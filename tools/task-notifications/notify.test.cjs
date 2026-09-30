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
