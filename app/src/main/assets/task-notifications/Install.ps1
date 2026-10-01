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

function Get-WrappedNotify([array]$Command) {
    if ($Command.Count -lt 4 -or [IO.Path]::GetFileName([string]$Command[0]) -ne 'codex-computer-use.exe' -or $Command[1] -ne 'turn-ended') { return ,@() }
    $index = [Array]::IndexOf($Command, '--previous-notify')
    if ($index -lt 0 -or $index + 1 -ge $Command.Count) { return ,@() }
    try { return ,@($Command[$index + 1] | ConvertFrom-Json) } catch { return ,@() }
}

function Test-OwnNotify([array]$Command, [string]$NotifyScript, [int]$Depth = 0) {
    $expected = [IO.Path]::GetFullPath($NotifyScript)
    foreach ($argument in $Command) {
        try {
            if ([string]::Equals([IO.Path]::GetFullPath([string]$argument), $expected, [StringComparison]::OrdinalIgnoreCase)) { return $true }
        } catch { }
    }
    if ($Depth -lt 4) {
        $wrapped = Get-WrappedNotify $Command
        if ($wrapped.Count) { return (Test-OwnNotify $wrapped $NotifyScript ($Depth + 1)) }
    }
    return $false
}

function Get-PreservedNotify([array]$Command, [string]$NotifyScript, [array]$SavedOriginal) {
    $wrapped = Get-WrappedNotify $Command
    if ($wrapped.Count -and (Test-OwnNotify $wrapped $NotifyScript)) {
        # Keep the current computer-use handler, removing only its old relay chain.
        $index = [Array]::IndexOf($Command, '--previous-notify')
        return ,@($Command | Select-Object -Index @(0..($Command.Count - 1) | Where-Object { $_ -ne $index -and $_ -ne ($index + 1) }))
    }
    return ,@($SavedOriginal)
}

$oldText = if (Test-Path -LiteralPath $configFile) { [IO.File]::ReadAllText($configFile) } else { '' }
$oldNotify = Read-Notify $oldText
if ($Uninstall) {
    if ((Test-Path -LiteralPath $connectionFile) -and (Test-OwnNotify $oldNotify $scriptPath)) {
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
$launcherSource = Join-Path $PSScriptRoot 'NotificationLauncher.cs'
if (-not (Test-Path -LiteralPath $launcherSource)) { throw 'Extract the latest setup ZIP shared by Codex Usage first.' }
$sourceHash = (Get-FileHash -LiteralPath $launcherSource -Algorithm SHA256).Hash.Substring(0,16)
$launcherPath = Join-Path $runtime ('NotificationLauncher-' + $sourceHash + '.exe')
if (-not (Test-Path -LiteralPath $launcherPath)) {
    $compiler = @('Microsoft.NET/Framework64/v4.0.30319/csc.exe', 'Microsoft.NET/Framework/v4.0.30319/csc.exe') |
        ForEach-Object { Join-Path $env:WINDIR $_ } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $compiler) { throw 'The Windows .NET Framework compiler is unavailable. Existing Codex settings have not been changed.' }
    & $compiler /nologo /target:winexe ('/out:' + $launcherPath) $launcherSource
    if ($LASTEXITCODE -ne 0) { throw 'Could not build the windowless launcher. Existing Codex settings have not been changed.' }
}
$previousNotify = $oldNotify
if (Test-OwnNotify $oldNotify $scriptPath) {
    if (-not (Test-Path -LiteralPath $connectionFile)) { throw 'Existing notification setup is incomplete; config has not been changed.' }
    $savedConnection = Get-Content -LiteralPath $connectionFile -Raw | ConvertFrom-Json
    $previousNotify = Get-PreservedNotify $oldNotify $scriptPath @($savedConnection.previousNotify)
}
if (Test-Path -LiteralPath $configFile) {
    Copy-Item -LiteralPath $configFile -Destination ($configFile + '.before-task-notifications-' + [DateTime]::Now.ToString('yyyyMMddHHmmssfff'))
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'notify.cjs') -Destination $scriptPath -Force
$monitorEnabledAt = [DateTime]::UtcNow.ToString('o')
if (Test-Path -LiteralPath $connectionFile) {
    $savedConnection = Get-Content -LiteralPath $connectionFile -Raw | ConvertFrom-Json
    if ($savedConnection.endpoint -eq $Endpoint -and $savedConnection.monitorEnabledAt) { $monitorEnabledAt = $savedConnection.monitorEnabledAt }
}
$settings = @{ endpoint = $Endpoint; previousNotify = @($previousNotify); codexHome = [IO.Path]::GetFullPath($CodexDirectory); monitorEnabledAt = $monitorEnabledAt }
[IO.File]::WriteAllText($connectionFile, ($settings | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
$newText = Set-Notify $oldText @($launcherPath, $node, $scriptPath)
[IO.File]::WriteAllText($configFile, $newText, [Text.UTF8Encoding]::new($false))

# Flush the metadata-only outbox after transient network failures, without an open terminal.
try {
    $action = New-ScheduledTaskAction -Execute $launcherPath -Argument ('"' + $node + '" "' + $scriptPath + '" --flush')
    $trigger = New-ScheduledTaskTrigger -Once -At ([DateTime]::Now.AddMinutes(1)) -RepetitionInterval (New-TimeSpan -Minutes 1)
    $principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
    $taskSettings = New-ScheduledTaskSettingsSet -Hidden -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $taskSettings -Force | Out-Null
} catch { Write-Host 'Automatic retry registration failed. Pending messages will retry at the next Codex completion.' }
Write-Host 'Installed. Your original Codex callback is preserved. Restart Codex to activate task notifications.'
if (-not $NoTest) {
    & $launcherPath $node $scriptPath --test
    if ($LASTEXITCODE -eq 0) { Write-Host 'Pairing test sent. Check Codex Usage on your phone.' }
    else { Write-Host 'Pairing test could not be sent. Check your proxy/network; queued messages are kept for retry.' }
}
