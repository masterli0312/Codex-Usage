'use strict';
// Windows-only integration tests; use the system .NET compiler, without downloads.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
if (process.platform !== 'win32') throw new Error('Run launcher tests on Windows');
const output = path.resolve(__dirname, '../../../../output/task-launcher-tests');
fs.mkdirSync(output, { recursive: true });
const compiler = ['Framework64', 'Framework'].map(p => path.join(process.env.WINDIR, 'Microsoft.NET', p, 'v4.0.30319', 'csc.exe')).find(fs.existsSync);
assert.ok(compiler, 'Windows .NET compiler is required');
const launcher = path.join(output, 'NotificationLauncher.exe');
const fixture = path.join(output, 'console fixture.exe');
const fixtureSource = path.join(output, 'console-fixture.cs');
fs.writeFileSync(fixtureSource, `using System; using System.IO; using System.Text; using System.Collections.Generic; using System.Runtime.InteropServices;
class Fixture {
  [DllImport("kernel32.dll")] static extern IntPtr GetConsoleWindow();
  static int Main(string[] args) {
    var lines = new List<string>(); lines.Add(GetConsoleWindow().ToInt64().ToString());
    for (int i = 1; i < args.Length; i++) lines.Add(Convert.ToBase64String(Encoding.UTF8.GetBytes(args[i])));
    File.WriteAllLines(args[0], lines);
    Console.WriteLine("output must not escape the launcher"); Console.Error.WriteLine("error must not escape the launcher");
    return 23;
  }
}`);
for (const [target, source, type] of [[launcher, path.resolve(__dirname, '../../app/src/main/assets/task-notifications/NotificationLauncher.cs'), 'winexe'], [fixture, fixtureSource, 'exe']]) {
  const compile = spawnSync(compiler, ['/nologo', '/target:' + type, '/out:' + target, source], { windowsHide: true, encoding: 'utf8' });
  assert.equal(compile.status, 0, compile.stdout + compile.stderr);
}

test('scheduled launcher is a GUI executable and its child has no console', () => {
  const image = fs.readFileSync(launcher);
  assert.equal(image.readUInt16LE(image.readUInt32LE(0x3c) + 24 + 68), 2);
  const report = path.join(output, 'console-report.txt');
  const result = spawnSync(launcher, [fixture, report, '--flush'], { encoding: 'utf8', windowsHide: true });
  assert.equal(result.status, 23);
  assert.equal(fs.readFileSync(report, 'utf8').trim().split(/\r?\n/)[0], '0');
  assert.equal(result.stdout, '');
  assert.equal(result.stderr, '');
});

test('Codex JSON, Unicode, empty arguments and trailing backslashes are preserved', () => {
  const report = path.join(output, 'argument-report.txt');
  const args = ['', '中文 task', '{"type":"agent-turn-complete","text":"quote \\\" and newline\\n"}', 'C:\\folder with spaces\\', '\\"'];
  const result = spawnSync(launcher, [fixture, report, ...args], { encoding: 'utf8', windowsHide: true });
  assert.equal(result.status, 23);
  const lines = fs.readFileSync(report, 'utf8').replace(/\r?\n$/, '').split(/\r?\n/);
  assert.deepEqual(lines.slice(1).map(line => Buffer.from(line, 'base64').toString('utf8')), args);
});

test('launch failures return an error without opening a dialog or exposing arguments', () => {
  const result = spawnSync(launcher, [path.join(output, 'missing.exe'), 'private event'], { encoding: 'utf8', windowsHide: true });
  assert.equal(result.status, 1);
  assert.equal(result.stdout, '');
  assert.equal(result.stderr, '');
});

test('reinstallation recognizes an existing callback with different Windows path spelling', () => {
  const install = path.resolve(__dirname, '../../app/src/main/assets/task-notifications/Install.ps1').replace(/'/g, "''");
  const script = `$ast=[System.Management.Automation.Language.Parser]::ParseFile('${install}',[ref]$null,[ref]$null); $fn=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Test-OwnNotify'},$true); Invoke-Expression $fn.Extent.Text;
if (-not (Test-OwnNotify @('node.exe','C:/EXAMPLE/notify.cjs') 'c:\\example\\notify.cjs')) { exit 1 };
if (Test-OwnNotify @('other.exe','C:/other/notify.cjs') 'c:\\example\\notify.cjs') { exit 2 }`;
  const result = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], { windowsHide: true, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
});
