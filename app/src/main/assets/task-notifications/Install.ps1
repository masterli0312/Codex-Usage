param([string]$Endpoint, [string]$CodexDirectory, [switch]$Uninstall, [switch]$NoTest)
$ErrorActionPreference = 'Stop'
if (-not $CodexDirectory) {
    $CodexDirectory = if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $env:USERPROFILE '.codex' }
}
$runtime = Join-Path $CodexDirectory 'codex-usage-notifications'
$configFile = Join-Path $CodexDirectory 'config.toml'
$connectionFile = Join-Path $runtime 'connection.json'
$scriptPath = Join-Path $runtime 'notify.cjs'
$taskName = 'Codex Usage notification retry'

function Read-Notify([string]$Text) {
    $match = [regex]::Match($Text, '(?m)^notify\s*=\s*(\[[^\r\n]+\])\s*$')
    if ($match.Success) { return ,($match.Groups[1].Value | ConvertFrom-Json) }
    if ($Text -match '(?m)^notify\s*=') { throw 'Existing notify syntax needs manual review; config has not been changed.' }
    return ,@()
}

function Set-Notify([string]$Text, [array]$Command) {
    $line = if ($Command.Count) { 'notify = ' + (ConvertTo-Json -InputObject $Command -Compress) } else { '' }
    if ($Text -match '(?m)^notify\s*=') { return [regex]::Replace($Text, '(?m)^notify\s*=\s*\[[^\r\n]+\]\s*$', [Text.RegularExpressions.MatchEvaluator]{ param($m) $line }) }
    if (-not $line) { return $Text }
    return $line + "`r`n" + $Text
}

$oldText = if (Test-Path -LiteralPath $configFile) { [IO.File]::ReadAllText($configFile) } else { '' }
$oldNotify = Read-Notify $oldText
if ($Uninstall) {
    if ((Test-Path -LiteralPath $connectionFile) -and ($oldNotify -contains $scriptPath)) {
        $saved = Get-Content -LiteralPath $connectionFile -Raw | ConvertFrom-Json
        [IO.File]::WriteAllText($configFile, (Set-Notify $oldText @($saved.previousNotify)), [Text.UTF8Encoding]::new($false))
    }
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host 'Notifications disabled. Original Codex callback restored. Restart Codex.'
    exit
}

if (-not $Endpoint) {
    $pairingFile = Join-Path $PSScriptRoot 'pairing.json'
    if (-not (Test-Path -LiteralPath $pairingFile)) { throw 'Extract the setup ZIP shared by Codex Usage first.' }
    $Endpoint = (Get-Content -LiteralPath $pairingFile -Raw | ConvertFrom-Json).endpoint
}
$uri = [Uri]$Endpoint
if ($uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -notmatch '^/[A-Za-z0-9_-]{1,128}$') { throw 'Invalid pairing address.' }
$node = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
if (-not $node) {
    $bundledRuntime = Join-Path $env:LOCALAPPDATA 'OpenAI/Codex/runtimes/cua_node'
    if (Test-Path -LiteralPath $bundledRuntime) {
        $node = Get-ChildItem -LiteralPath $bundledRuntime -Directory | Sort-Object LastWriteTime -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin/node.exe' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
}
if (-not $node) { throw 'Install Node.js 18 or newer from nodejs.org, then run setup.cmd again.' }
& $node -e 'if (Number(process.versions.node.split(".")[0]) < 18) process.exit(1)'
if ($LASTEXITCODE -ne 0) { throw 'Node.js 18 or newer is required.' }
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$previousNotify = $oldNotify
if ($oldNotify -contains $scriptPath) {
    if (-not (Test-Path -LiteralPath $connectionFile)) { throw 'Existing notification setup is incomplete; config has not been changed.' }
    $previousNotify = @((Get-Content -LiteralPath $connectionFile -Raw | ConvertFrom-Json).previousNotify)
}
if (Test-Path -LiteralPath $configFile) {
    Copy-Item -LiteralPath $configFile -Destination ($configFile + '.before-task-notifications-' + [DateTime]::Now.ToString('yyyyMMddHHmmssfff'))
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'notify.cjs') -Destination $scriptPath -Force
$settings = @{ endpoint = $Endpoint; previousNotify = @($previousNotify) }
[IO.File]::WriteAllText($connectionFile, ($settings | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
$newText = Set-Notify $oldText @($node, $scriptPath)
[IO.File]::WriteAllText($configFile, $newText, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $runtime 'retry.ps1'), @'
param([string]$NodePath, [string]$NotifyPath)
Start-Process -FilePath $NodePath -ArgumentList ('"' + $NotifyPath + '" --flush') -WindowStyle Hidden -Wait
'@, [Text.UTF8Encoding]::new($false))

# Flush the metadata-only outbox after transient network failures, without an open terminal.
try {
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + (Join-Path $runtime 'retry.ps1') + '" -NodePath "' + $node + '" -NotifyPath "' + $scriptPath + '"')
    $trigger = New-ScheduledTaskTrigger -Once -At ([DateTime]::Now.AddMinutes(1)) -RepetitionInterval (New-TimeSpan -Minutes 1)
    $principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
    $taskSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $taskSettings -Force | Out-Null
} catch { Write-Host 'Automatic retry registration failed. Pending messages will retry at the next Codex completion.' }
Write-Host 'Installed. Your original Codex callback is preserved. Restart Codex to activate task notifications.'
if (-not $NoTest) {
    & $node $scriptPath --test
    if ($LASTEXITCODE -eq 0) { Write-Host 'Pairing test sent. Check Codex Usage on your phone.' }
    else { Write-Host 'Pairing test could not be sent. Check your proxy/network; queued messages are kept for retry.' }
}
