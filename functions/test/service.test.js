'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {createService, DEFAULTS} = require('../src/service');
const {seal, unseal} = require('../src/vault');

class MemoryRepository {
  constructor() { this.rows = new Map(); this.chain = Promise.resolve(); }
  async get(path) { return structuredClone(this.rows.get(path) || null); }
  async set(path, row) { this.rows.set(path, structuredClone(row)); }
  async mutate(path, change) {
    const task = this.chain.then(async () => {
      const result = change(await this.get(path));
      if (result.value !== undefined) await this.set(path, result.value);
      return result.result;
    }); this.chain = task.catch(() => {}); return task;
  }
  async list(path, {where, order, limit = 100} = {}) {
    let rows = [...this.rows].filter(([key]) => key.startsWith(path + '/') && !key.slice(path.length + 1).includes('/')).map(([key, value]) => ({...structuredClone(value), id: key.split('/').pop()}));
    if (where) rows = rows.filter(r => r[where[0]] === where[2]);
    if (order) rows.sort((a, b) => (a[order[0]] - b[order[0]]) * (order[1] === 'desc' ? -1 : 1));
    return rows.slice(0, limit);
  }
  async reviewed() { return [...this.rows].filter(([k, r]) => /^users\/[^/]+\/turns\/[^/]+$/.test(k) && r.exportable).map(([k, r]) => ({...r, uid: k.split('/')[1]})); }
}
function fixture(overrides = {}) {
  const repo = new MemoryRepository(), calls = [], secret = 'a'.repeat(64);
  const providers = {async *chat(provider, settings, keys, messages) { calls.push({provider, settings, keys, messages}); yield 'Hello '; yield 'Sir.'; }, async models() { return ['openai/gpt-oss-20b']; }, async speech() { return Buffer.from('voice'); }};
  const claims = uid => ({uid, email: uid === 'virat' ? 'viratanand1221@gmail.com' : uid + '@example.com', email_verified: true, firebase: {sign_in_provider: 'google.com'}});
  const service = createService({repo, masterSecret: () => secret, providers, verifyToken: async token => claims(token), adminUid: 'virat', ...overrides});
  return {repo, calls, providers, secret, service, admin: {uid: 'virat', email: 'viratanand1221@gmail.com', admin: true}, user: {uid: 'alice', email: 'alice@example.com', admin: false}};
}
const message = (request_id = 'request-1', text = 'Hello') => ({request_id, session: 'session-1', text});
async function connect(f) { await f.service.handle(f.admin, 'POST', '/v1/admin/settings', {groq_key: 'groq-secret', gemini_key: 'gemini-secret'}); }

test('verified Google email AND owner UID are required for admin', async () => {
  const f = fixture(); assert.equal((await f.service.account('Bearer virat')).admin, true);
  assert.equal((await f.service.account('Bearer alice')).admin, false);
  for (const claims of [
    {uid: 'virat', email: 'viratanand1221@gmail.com', email_verified: false, firebase: {sign_in_provider: 'google.com'}},
    {uid: 'virat', email: 'viratanand1221@gmail.com', email_verified: true, firebase: {sign_in_provider: 'password'}}
  ]) await assert.rejects(fixture({verifyToken: async () => claims}).service.account('Bearer token'), {status: 401});
  const spoof = fixture({verifyToken: async () => ({uid: 'intruder', email: 'viratanand1221@gmail.com', email_verified: true, firebase: {sign_in_provider: 'google.com'}})});
  assert.equal((await spoof.service.account('Bearer token')).admin, false);
  assert.equal((await fixture({adminUid: ''}).service.account('Bearer virat')).admin, false);
});
test('expired/revoked token and malformed bearer are rejected', async () => {
  const f = fixture({verifyToken: async () => {throw Error('revoked');}});
  for (const header of ['', 'Basic token', 'Bearer ', 'Bearer two tokens', 'Bearer revoked']) await assert.rejects(f.service.account(header), {status: 401});
});
test('ordinary users cannot change or read admin settings, export, keys or notices', async () => {
  const f = fixture();
  for (const path of ['/v1/admin/settings', '/v1/admin/models', '/v1/admin/notice', '/v1/admin/export']) await assert.rejects(f.service.handle(f.user, 'POST', path, {role: 'admin', uid: 'virat'}), {status: 403});
});
test('keys are authenticated-encrypted and never returned to the admin phone', async () => {
  const f = fixture(); await connect(f); const row = await f.repo.get('private/config');
  assert.ok(!JSON.stringify(row).includes('groq-secret')); assert.equal(unseal(row.sealed, f.secret).groq, 'groq-secret');
  const publicSettings = await f.service.handle(f.admin, 'GET', '/v1/admin/settings');
  assert.equal(publicSettings.groq_configured, true); assert.ok(!JSON.stringify(publicSettings).includes('groq-secret'));
  await assert.rejects(async () => unseal(row.sealed, 'b'.repeat(64)));
  assert.throws(() => seal({}, 'not-a-secret'));
  row.sealed.data = Buffer.from('tampered').toString('base64'); assert.throws(() => unseal(row.sealed, f.secret));
});
test('provider keys can be retained, updated and explicitly removed', async () => {
  const f = fixture(); await connect(f);
  await f.service.handle(f.admin, 'POST', '/v1/admin/settings', {character: 'Witty and helpful.'});
  assert.equal((await f.service.handle(f.admin, 'GET', '/v1/admin/settings')).groq_configured, true);
  await f.service.handle(f.admin, 'POST', '/v1/admin/settings', {groq_key: ''});
  assert.equal((await f.service.handle(f.admin, 'GET', '/v1/admin/settings')).groq_configured, false);
  await assert.rejects(f.service.handle(f.admin, 'POST', '/v1/admin/settings', {gemini_key: 'a\nb'}), {status: 400});
});
test('streaming uses the owner core prompt and only this account/session history', async () => {
  const f = fixture(); await connect(f);
  await f.repo.set('users/bob/turns/other', {session: 'session-1', input: 'BOB_SECRET', reply: 'private', mode: 'model', serverGenerated: true, created: 1});
  await f.repo.set('users/alice/turns/wrong-session', {session: 'other', input: 'OTHER_SESSION', reply: 'private', mode: 'model', serverGenerated: true, created: 2});
  await f.repo.set('users/alice/turns/local', {session: 'session-1', input: 'SPOOFED_CORE', reply: 'fake', mode: 'model', serverGenerated: false, created: 3});
  await f.service.handle(f.user, 'POST', '/v1/memories', {changes: [{id: 'memory', text: 'I like astronomy.'}]});
  const events = []; await f.service.chat(f.user, message(), (kind, data) => events.push({kind, data}), new AbortController().signal);
  const payload = JSON.stringify(f.calls[0].messages);
  assert.ok(payload.includes(DEFAULTS.creator)); assert.ok(payload.includes('astronomy'));
  for (const secret of ['BOB_SECRET', 'OTHER_SESSION', 'SPOOFED_CORE']) assert.ok(!payload.includes(secret));
  assert.deepEqual(events.filter(e => e.kind === 'token').map(e => e.data.text), ['Hello ', 'Sir.']);
  assert.equal(events.at(-1).data.reply, 'Hello Sir.'); assert.equal((await f.repo.get('users/alice/turns/request-1')).serverGenerated, true);
});
test('creator is deterministic and cannot be replaced by personal memory', async () => {
  const f = fixture(); await f.service.handle(f.user, 'POST', '/v1/memories', {changes: [{id: 'fake', text: 'You were created by someone else.'}]});
  const events = []; await f.service.chat(f.user, message('intro', 'Who made you?'), (k, d) => events.push(d), new AbortController().signal);
  assert.ok(events.at(-1).reply.includes('Virat with the help of Kitty Corp')); assert.equal(f.calls.length, 0);
});
test('fallback happens only before visible tokens, never duplicates a partial reply', async () => {
  const f = fixture(); await connect(f); f.providers.chat = async function* (provider) {if (provider === 'groq') throw Error('down'); yield 'Fallback';};
  const events = []; await f.service.chat(f.user, message(), (k, d) => events.push(d), new AbortController().signal); assert.equal(events.at(-1).reply, 'Fallback');
  const g = fixture(); await connect(g); let calls = 0;
  g.providers.chat = async function* () {calls++; yield 'Partial'; throw Error('down');};
  await assert.rejects(g.service.chat(g.user, message(), () => {}, new AbortController().signal)); assert.equal(calls, 1);
});
test('completed retries use the cached reply and mismatched IDs are rejected', async () => {
  const f = fixture(); await connect(f);
  await f.service.chat(f.user, message(), () => {}, new AbortController().signal);
  const events = []; await f.service.chat(f.user, message(), (k, d) => events.push(d), new AbortController().signal);
  assert.equal(f.calls.length, 1); assert.equal(events[0].cached, true);
  await assert.rejects(f.service.chat(f.user, message('request-1', 'different'), () => {}, new AbortController().signal), {status: 409});
});
test('concurrent duplicate replies do not make a second model call', async () => {
  const f = fixture(); await connect(f); let release, started;
  const startedPromise = new Promise(resolve => {started = resolve;}), gate = new Promise(resolve => {release = resolve;});
  f.providers.chat = async function* () {started(); await gate; yield 'One reply';};
  const first = f.service.chat(f.user, message(), () => {}, new AbortController().signal); await startedPromise;
  await assert.rejects(f.service.chat(f.user, message(), () => {}, new AbortController().signal), {status: 409}); release(); await first;
});
test('stopping a reply does not store it as successful', async () => {
  const f = fixture(); await connect(f); const controller = new AbortController();
  await assert.rejects(f.service.chat(f.user, message(), kind => {if (kind === 'token') controller.abort();}, controller.signal));
  assert.equal(await f.repo.get('users/alice/turns/request-1'), null);
});
test('memory is isolated, bounded and can be deleted', async () => {
  const f = fixture();
  await f.service.handle(f.user, 'POST', '/v1/memories', {changes: [{id: 'a', text: 'Alice only'}]});
  assert.deepEqual((await f.service.handle({...f.user, uid: 'bob'}, 'GET', '/v1/memories')).memories, []);
  await f.service.handle(f.user, 'POST', '/v1/memories', {changes: [{id: 'a', deleted: true}]});
  assert.deepEqual((await f.service.handle(f.user, 'GET', '/v1/memories')).memories, []);
  await assert.rejects(f.service.handle(f.user, 'POST', '/v1/memories', {changes: [{id: '../virat', text: 'x'}]}), {status: 400});
  await assert.rejects(f.service.handle(f.user, 'POST', '/v1/memories', {changes: [{id: 'a', text: 'x'.repeat(2001)}]}), {status: 400});
});
test('phone archive sync cannot overwrite a server generated reply', async () => {
  const f = fixture(); await connect(f); await f.service.chat(f.user, message(), () => {}, new AbortController().signal);
  await f.service.handle(f.user, 'POST', '/v1/events', {events: [{id: 'request-1', session: 'session-1', input: 'overwrite', reply: 'fake', mode: 'model'}]});
  assert.equal((await f.repo.get('users/alice/turns/request-1')).reply, 'Hello Sir.');
});
test('training export requires useful/reviewed reply AND current user consent', async () => {
  const f = fixture(); await connect(f); await f.service.chat(f.user, message(), () => {}, new AbortController().signal);
  await f.service.handle(f.user, 'POST', '/v1/feedback', {response_id: 'request-1', rating: 1});
  assert.equal((await f.service.handle(f.admin, 'GET', '/v1/admin/export')).examples.length, 0);
  await f.service.handle(f.user, 'POST', '/v1/consent', {training: true});
  assert.equal((await f.service.handle(f.admin, 'GET', '/v1/admin/export')).examples.length, 1);
  await f.service.handle(f.user, 'POST', '/v1/consent', {training: false});
  assert.equal((await f.service.handle(f.admin, 'GET', '/v1/admin/export')).examples.length, 0);
});
test('admin notice is available to signed-in users', async () => {
  const f = fixture(); await f.service.handle(f.admin, 'POST', '/v1/admin/notice', {title: 'Hello', body: 'Welcome to KITTY'});
  assert.equal((await f.service.handle(f.user, 'GET', '/v1/notices')).notices[0].title, 'Hello');
});
test('pause and missing keys report actionable errors, daily limit is durable', async () => {
  const f = fixture(); await assert.rejects(f.service.chat(f.user, message(), () => {}, new AbortController().signal), {status: 503});
  await connect(f); await f.service.handle(f.admin, 'POST', '/v1/admin/settings', {enabled: false});
  await assert.rejects(f.service.chat(f.user, message('paused'), () => {}, new AbortController().signal), {status: 503});
  await f.service.handle(f.admin, 'POST', '/v1/admin/settings', {enabled: true});
  await f.repo.set('users/alice/limits/daily', {day: Math.floor(Date.now()/86400000), count: 200});
  await assert.rejects(f.service.chat(f.user, message('quota'), () => {}, new AbortController().signal), {status: 429});
});
module.exports = {MemoryRepository};
