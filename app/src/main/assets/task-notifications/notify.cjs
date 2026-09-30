'use strict';
// Transport is ntfy's public protocol. Never publish the raw Codex hook payload.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');

function completionMetadata(payload) {
  if (payload?.type !== 'agent-turn-complete') return null;
  const identity = String(payload['thread-id'] || '') + ':' + String(payload['turn-id'] || '') + ':' +
    (payload['turn-id'] ? '' : JSON.stringify(payload));
  return {
    schema_version: '1.0', agent_source: 'codex', status: 'turn_complete',
    session_id: 'codex', turn_id: crypto.createHash('sha256').update(identity).digest('hex'),
  };
}

function validEndpoint(endpoint) {
  try {
    const url = new URL(endpoint);
    return url.protocol === 'https:' && !url.username && !url.password && !url.search && !url.hash &&
      /^\/[A-Za-z0-9_-]{1,128}$/.test(url.pathname);
  } catch { return false; }
}

function forwardOriginal(command, raw, run = spawnSync) {
  if (!Array.isArray(command) || !command.length) return;
  // Preserve the existing callback and pass exactly the argument Codex would have appended.
  run(command[0], [...command.slice(1), raw], { stdio: 'ignore', timeout: 10_000, windowsHide: true });
}

async function publish(endpoint, message, fetchImpl = fetch) {
  if (!validEndpoint(endpoint)) throw new Error('Invalid notification endpoint');
  const response = await fetchImpl(endpoint, {
    method: 'POST', headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Title': 'Codex Usage' },
    body: JSON.stringify(message), signal: AbortSignal.timeout(7_000),
  });
  if (!response.ok) throw new Error('Notification publish failed');
  await response.arrayBuffer();
}

async function main(argv = process.argv.slice(2), directory = __dirname, publisher = publish) {
  const configPath = process.env.CODEX_USAGE_NOTIFY_CONFIG || path.join(directory, 'connection.json');
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8').replace(/^\uFEFF/, ''));
  const raw = argv[argv.length - 1];
  const flushOnly = argv[0] === '--flush';
  const test = argv[0] === '--test';
  if (!flushOnly && !test) forwardOriginal(config.previousNotify, raw);
  if (!validEndpoint(config.endpoint)) throw new Error('Invalid notification endpoint');
  const metadata = test ? {
    schema_version: '1.0', agent_source: 'codex', status: 'test', session_id: 'test', turn_id: crypto.randomUUID(),
  } : flushOnly ? null : completionMetadata(JSON.parse(raw));
  const outbox = path.join(directory, 'outbox');
  fs.mkdirSync(outbox, { recursive: true });
  if (metadata) {
    const target = path.join(outbox, metadata.turn_id + '.json');
    const temporary = target + '.' + process.pid + '.tmp';
    fs.writeFileSync(temporary, JSON.stringify(metadata), { mode: 0o600 });
    fs.renameSync(temporary, target);
  }
  let failed = false;
  for (const name of fs.readdirSync(outbox).filter(name => /^[a-f0-9-]+\.json$/.test(name)).sort()) {
    const file = path.join(outbox, name);
    try {
      const message = JSON.parse(fs.readFileSync(file, 'utf8'));
      await publisher(config.endpoint, message);
      fs.unlinkSync(file);
      // Diagnostics contain no endpoint, thread ID, prompts, or replies.
      fs.writeFileSync(path.join(directory, 'status.json'), JSON.stringify({ lastPublishedAt: new Date().toISOString(), status: message.status }), { mode: 0o600 });
    } catch { failed = true; break; }
  }
  if (failed && test) process.exitCode = 1;
}

module.exports = { completionMetadata, validEndpoint, forwardOriginal, publish, main };
if (require.main === module) main().catch(() => {
  // No raw payload/config/error output: retryable messages stay in the outbox.
  if (process.argv.includes('--test')) process.exitCode = 1;
});
