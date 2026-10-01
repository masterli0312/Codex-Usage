'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { completionMetadata, validEndpoint, forwardOriginal, publish, main } = require('../../app/src/main/assets/task-notifications/notify.cjs');

test('completion emits metadata only and has a stable identity for retries', () => {
  const raw = { type: 'agent-turn-complete', 'thread-id': 'thread', 'turn-id': 'turn',
    'input-messages': ['private prompt'], 'last-assistant-message': 'private reply', token: 'secret' };
  const result = completionMetadata(raw);
  assert.equal(result.status, 'turn_complete');
  assert.equal(JSON.stringify(result).includes('private'), false);
  assert.equal(JSON.stringify(result).includes('secret'), false);
  assert.deepEqual(result, completionMetadata(raw));
  assert.equal(completionMetadata({ type: 'approval-requested' }), null);
});
test('only a private HTTPS topic endpoint is accepted', () => {
  assert.equal(validEndpoint('https://ntfy.sh/codex-usage-test'), true);
  for (const value of ['http://ntfy.sh/topic', 'https://u:p@ntfy.sh/topic', 'https://ntfy.sh/topic?key=x', 'https://ntfy.sh/', 'https://ntfy.sh/a/b']) {
    assert.equal(validEndpoint(value), false);
  }
});
test('existing callback receives its original arguments and unmodified payload', () => {
  let captured;
  forwardOriginal(['existing.exe', 'turn-ended'], '{"type":"agent-turn-complete"}', (...args) => { captured = args; });
  assert.equal(captured[0], 'existing.exe');
  assert.deepEqual(captured[1], ['turn-ended', '{"type":"agent-turn-complete"}']);
  assert.equal(captured[2].windowsHide, true);
});
test('publisher checks errors and never adds OpenAI authorization', async () => {
  let options;
  await publish('https://ntfy.sh/example', { status: 'turn_complete' }, async (url, args) => {
    options = args;
    return { ok: true, arrayBuffer: async () => new ArrayBuffer(0) };
  });
  assert.equal(options.method, 'POST');
  assert.equal(options.headers.Authorization, undefined);
  await assert.rejects(publish('https://ntfy.sh/example', {}, async () => ({ ok: false })));
});
test('failed sends remain metadata-only and can be flushed after recovery', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'codex-usage-outbox-test-'));
  try {
    fs.writeFileSync(path.join(directory, 'connection.json'), JSON.stringify({ endpoint: 'https://ntfy.sh/example', previousNotify: [] }));
    const payload = { type: 'agent-turn-complete', 'thread-id': 'thread', 'turn-id': 'turn', 'last-assistant-message': 'private reply' };
    await main([JSON.stringify(payload)], directory, async () => { throw new Error('offline'); });
    const files = fs.readdirSync(path.join(directory, 'outbox'));
    assert.equal(files.length, 1);
    assert.equal(fs.readFileSync(path.join(directory, 'outbox', files[0]), 'utf8').includes('private'), false);
    let sent = 0;
    await main(['--flush'], directory, async () => { sent += 1; });
    assert.equal(sent, 1);
    assert.equal(fs.readdirSync(path.join(directory, 'outbox')).length, 0);
  } finally {
    assert.equal(path.dirname(directory), path.resolve(os.tmpdir()));
    assert.equal(path.basename(directory).startsWith('codex-usage-outbox-test-'), true);
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

function monitorFixture() {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'codex-usage-monitor-test-'));
  const home = path.join(directory, 'codex');
  const sessions = path.join(home, 'sessions', '2026', '10', '01');
  fs.mkdirSync(sessions, { recursive: true });
  fs.writeFileSync(path.join(directory, 'connection.json'), JSON.stringify({
    endpoint: 'https://ntfy.sh/example', previousNotify: [], codexHome: home,
    monitorEnabledAt: '2026-10-01T00:00:00Z',
  }));
  const thread = '01a00000-0000-0000-0000-000000000001';
  const file = path.join(sessions, 'rollout-2026-10-01T00-00-00-' + thread + '.jsonl');
  function event(type, turn, time = '2026-10-01T01:00:00Z') {
    return JSON.stringify({ timestamp: time, type: 'event_msg', payload: { type, turn_id: turn,
      last_agent_message: 'private reply must never leave the computer' } });
  }
  return { directory, home, file, thread, event, cleanup: () => {
    assert.equal(path.dirname(directory), path.resolve(os.tmpdir()));
    assert.equal(path.basename(directory).startsWith('codex-usage-monitor-test-'), true);
    fs.rmSync(directory, { recursive: true, force: true });
  } };
}

test('background scan delivers a missed completion and ignores old turns and running tasks', async () => {
  const f = monitorFixture();
  try {
    fs.writeFileSync(f.file, [f.event('task_complete', 'old', '2026-09-30T01:00:00Z'),
      f.event('task_started', 'running'), f.event('task_complete', 'finished')].join('\n') + '\n');
    const sent = [];
    await main(['--flush'], f.directory, async (_, message) => sent.push(message));
    assert.equal(sent.length, 1);
    assert.deepEqual(sent[0], completionMetadata({ type: 'agent-turn-complete', 'thread-id': f.thread, 'turn-id': 'finished' }));
    const checkpoint = fs.readFileSync(path.join(f.directory, 'monitor-state.json'), 'utf8');
    assert.equal(checkpoint.includes('private reply'), false);
    await main(['--flush'], f.directory, async (_, message) => sent.push(message));
    assert.equal(sent.length, 1);
  } finally { f.cleanup(); }
});

test('callback and fallback share one delivery identity', async () => {
  const f = monitorFixture();
  try {
    fs.writeFileSync(f.file, f.event('task_complete', 'done') + '\n');
    let sent = 0;
    const publisher = async () => { sent++; };
    const raw = JSON.stringify({ type: 'agent-turn-complete', 'thread-id': f.thread, 'turn-id': 'done' });
    await main([raw], f.directory, publisher);
    await main(['--flush'], f.directory, publisher);
    await main([raw], f.directory, publisher);
    assert.equal(sent, 1);
  } finally { f.cleanup(); }
});

test('partial lines and failures recover without losing or repeating completions', async () => {
  const f = monitorFixture();
  try {
    const line = f.event('task_complete', 'done');
    fs.writeFileSync(f.file, line.slice(0, 80));
    let sent = 0;
    await main(['--flush'], f.directory, async () => { sent++; });
    assert.equal(sent, 0);
    fs.appendFileSync(f.file, line.slice(80) + '\n');
    await main(['--flush'], f.directory, async () => { throw new Error('offline'); });
    assert.equal(fs.readdirSync(path.join(f.directory, 'outbox')).filter(n => n.endsWith('.json')).length, 1);
    await main(['--flush'], f.directory, async () => { sent++; });
    await main(['--flush'], f.directory, async () => { sent++; });
    assert.equal(sent, 1);
  } finally { f.cleanup(); }
});

test('all sessions are checked independently and an aborted turn is not completed', async () => {
  const f = monitorFixture();
  try {
    fs.writeFileSync(f.file, f.event('turn_aborted', 'abort') + '\n' + f.event('task_complete', 'first') + '\n');
    const second = f.file.replace(f.thread, '01a00000-0000-0000-0000-000000000002');
    fs.writeFileSync(second, f.event('task_complete', 'second') + '\n');
    let sent = 0;
    await main(['--flush'], f.directory, async () => { sent++; });
    assert.equal(sent, 2);
  } finally { f.cleanup(); }
});

test('subagent completions do not masquerade as a user conversation ending', async () => {
  const f = monitorFixture();
  try {
    fs.writeFileSync(f.file, JSON.stringify({type: 'session_meta', payload: { source: { subagent: 'thread_spawn' } }}) + '\n' + f.event('task_complete', 'child') + '\n');
    let sent = 0;
    await main(['--flush'], f.directory, async () => { sent++; });
    fs.appendFileSync(f.file, f.event('task_complete', 'child-again') + '\n');
    await main(['--flush'], f.directory, async () => { sent++; });
    assert.equal(sent, 0);
  } finally { f.cleanup(); }
});
