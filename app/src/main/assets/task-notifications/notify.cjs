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

function readJson(file, fallback) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '')); }
  catch { return fallback; }
}

function writeJson(file, value) {
  const temporary = file + '.' + process.pid + '.tmp';
  fs.writeFileSync(temporary, JSON.stringify(value), { mode: 0o600 });
  fs.renameSync(temporary, file);
}

function enqueue(outbox, message, delivered) {
  if (!message || delivered[message.turn_id]) return;
  writeJson(path.join(outbox, message.turn_id + '.json'), message);
}

// The notify hook is not emitted by every desktop session. Check explicit rollout events,
// never infer completion from file inactivity, assistant text, or an ended process.
function scanCompletions(config, directory, outbox, delivered) {
  const enabledAt = Date.parse(config.monitorEnabledAt);
  if (!config.codexHome || !Number.isFinite(enabledAt)) return;
  const root = path.join(config.codexHome, 'sessions');
  const checkpointFile = path.join(directory, 'monitor-state.json');
  const state = readJson(checkpointFile, { files: {} });
  if (!state.files || typeof state.files !== 'object') state.files = {};
  function walk(folder) {
    if (!fs.existsSync(folder)) return;
    for (const entry of fs.readdirSync(folder, { withFileTypes: true })) {
      if (entry.isSymbolicLink()) continue;
      const file = path.join(folder, entry.name);
      if (entry.isDirectory()) { walk(file); continue; }
      const match = entry.name.match(/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.jsonl$/i);
      if (!entry.isFile() || !match) continue;
      const stats = fs.statSync(file);
      if (stats.mtimeMs < enabledAt) continue;
      const key = crypto.createHash('sha256').update(path.relative(root, file)).digest('hex');
      const previous = state.files[key];
      if (previous?.ignored) continue;
      if (previous && previous.offset === stats.size && previous.modified === stats.mtimeMs) continue;
      let offset = previous && previous.offset <= stats.size ? previous.offset : 0;
      let position = offset;
      let pending = Buffer.alloc(0);
      let skipLongLine = false;
      let ignored = false;
      const fd = fs.openSync(file, 'r');
      try {
        const buffer = Buffer.alloc(64 * 1024);
        while (position < stats.size) {
          const count = fs.readSync(fd, buffer, 0, Math.min(buffer.length, stats.size - position), position);
          if (!count) break;
          position += count;
          const chunk = Buffer.concat([pending, buffer.subarray(0, count)]);
          let from = 0;
          let newline;
          while ((newline = chunk.indexOf(10, from)) >= 0) {
            const line = skipLongLine ? '' : chunk.subarray(from, newline).toString('utf8');
            skipLongLine = false;
            if (line.includes('"session_meta"')) {
              try {
                const meta = JSON.parse(line);
                if (meta.type === 'session_meta' && meta.payload?.source?.subagent) { ignored = true; break; }
              } catch { }
            }
            if (line.includes('"event_msg"') && line.includes('task_complete')) {
              let event;
              try { event = JSON.parse(line); } catch { event = null; }
              const timestamp = Date.parse(event?.timestamp);
              const turn = event?.payload?.turn_id;
              if (event?.type === 'event_msg' && event.payload?.type === 'task_complete' &&
                  timestamp >= enabledAt && typeof turn === 'string' && turn.length > 0 && turn.length <= 128) {
                enqueue(outbox, completionMetadata({ type: 'agent-turn-complete', 'thread-id': match[1], 'turn-id': turn }), delivered);
              }
            }
            from = newline + 1;
            offset = position - (chunk.length - from);
          }
          if (ignored) { offset = stats.size; break; }
          pending = chunk.subarray(from);
          // Bound memory for unrelated huge lines. No raw line is persisted in the checkpoint.
          if (pending.length > 1024 * 1024) { pending = Buffer.alloc(0); skipLongLine = true; }
        }
      } finally { fs.closeSync(fd); }
      state.files[key] = { offset, modified: stats.mtimeMs, ignored };
    }
  }
  walk(root);
  writeJson(checkpointFile, state);
  const statusFile = path.join(directory, 'status.json');
  writeJson(statusFile, { ...readJson(statusFile, {}), lastScanAt: new Date().toISOString() });
}

function acquireLock(directory) {
  const lock = path.resolve(directory, 'flush.lock');
  if (path.dirname(lock) !== path.resolve(directory) || path.basename(lock) !== 'flush.lock')
    throw new Error('Invalid relay lock path');
  try { fs.mkdirSync(lock); }
  catch {
    const owner = readJson(path.join(lock, 'owner.json'), {});
    if (Number.isInteger(owner.pid)) {
      try { process.kill(owner.pid, 0); return null; }
      catch (error) { if (error.code !== 'ESRCH') return null; }
    } else if (Date.now() - fs.statSync(lock).mtimeMs < 120_000) return null;
    // Only this known relay lock directory is removed, never a user-supplied path.
    fs.rmSync(lock, { recursive: true, force: true });
    try { fs.mkdirSync(lock); } catch { return null; }
  }
  writeJson(path.join(lock, 'owner.json'), { pid: process.pid });
  return () => fs.rmSync(lock, { recursive: true, force: true });
}

async function main(argv = process.argv.slice(2), directory = __dirname, publisher = publish) {
  const configPath = process.env.CODEX_USAGE_NOTIFY_CONFIG || path.join(directory, 'connection.json');
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8').replace(/^\uFEFF/, ''));
  const raw = argv[argv.length - 1];
  const flushOnly = argv[0] === '--flush';
  const test = argv[0] === '--test';
  if (!validEndpoint(config.endpoint)) throw new Error('Invalid notification endpoint');
  const metadata = test ? {
    schema_version: '1.0', agent_source: 'codex', status: 'test', session_id: 'test', turn_id: crypto.randomUUID(),
  } : flushOnly ? null : completionMetadata(JSON.parse(raw));
  const outbox = path.join(directory, 'outbox');
  fs.mkdirSync(outbox, { recursive: true });
  const deliveredFile = path.join(directory, 'delivered.json');
  let delivered = readJson(deliveredFile, {});
  enqueue(outbox, metadata, delivered);
  if (!flushOnly && !test) {
    writeJson(path.join(directory, 'status.json'), { ...readJson(path.join(directory, 'status.json'), {}), lastCallbackAt: new Date().toISOString() });
    forwardOriginal(config.previousNotify, raw);
  }
  const release = acquireLock(directory);
  if (!release) return; // Enqueued events will be drained by the current owner or next minute's run.
  delivered = readJson(deliveredFile, {});
  let failed = false;
  try {
    // A scan error must not prevent already queued notifications from being delivered.
    if (flushOnly) {
      try { scanCompletions(config, directory, outbox, delivered); }
      catch { writeJson(path.join(directory, 'status.json'), { ...readJson(path.join(directory, 'status.json'), {}), scanStatus: 'retry_needed' }); }
    }
    for (const name of fs.readdirSync(outbox).filter(name => /^[a-f0-9-]+\.json$/.test(name)).sort()) {
      const file = path.join(outbox, name);
      try {
        const message = JSON.parse(fs.readFileSync(file, 'utf8'));
        if (!delivered[message.turn_id]) {
          await publisher(config.endpoint, message);
          delivered[message.turn_id] = Date.now();
          const recent = Object.entries(delivered).filter(([, time]) => time >= Date.now() - 7 * 86400_000)
            .sort((a, b) => b[1] - a[1]).slice(0, 10_000);
          writeJson(deliveredFile, Object.fromEntries(recent));
        }
        fs.unlinkSync(file);
        // Diagnostics contain no endpoint, thread ID, prompts, or replies.
        writeJson(path.join(directory, 'status.json'), { ...readJson(path.join(directory, 'status.json'), {}),
          lastPublishedAt: new Date().toISOString(), status: message.status });
      } catch { failed = true; break; }
    }
  } finally { release(); }
  if (failed && test) process.exitCode = 1;
}

module.exports = { completionMetadata, validEndpoint, forwardOriginal, publish, main };
if (require.main === module) main().catch(() => {
  // No raw payload/config/error output: retryable messages stay in the outbox.
  if (process.argv.includes('--test')) process.exitCode = 1;
});
