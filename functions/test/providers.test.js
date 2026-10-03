'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {Providers, sse} = require('../src/providers');
const {DEFAULTS} = require('../src/service');
const stream = text => new Response(text, {status: 200, headers: {'Content-Type': 'text/event-stream'}});
const collect = async iterator => {const rows = []; for await (const row of iterator) rows.push(row); return rows;};

test('SSE decoder preserves split UTF-8 and multiline frames', async () => {
  const bytes = Buffer.from('data: café\r\ndata: 猫\r\n\r\n');
  async function* chunks() {for (const byte of bytes) yield Buffer.from([byte]);}
  assert.deepEqual(await collect(sse(chunks())), ['café\n猫']);
});
test('Groq payload streams content with fixed host and hidden short reasoning', async () => {
  let request;
  const p = new Providers(async (url, options) => {request = {url, ...options}; return stream('data: {"choices":[{"delta":{"content":"Hi"}}]}\n\ndata: [DONE]\n\n');});
  assert.deepEqual(await collect(p.chat('groq', DEFAULTS, {groq: 'secret'}, [{role: 'system', content: 'KITTY'}], new AbortController().signal)), ['Hi']);
  const body = JSON.parse(request.body); assert.equal(body.reasoning_format, 'hidden'); assert.equal(body.reasoning_effort, 'low'); assert.equal(body.max_completion_tokens, 1536);
  assert.equal(request.url, 'https://api.groq.com/openai/v1/chat/completions'); assert.equal(request.redirect, 'error');
});
test('incomplete provider streams are failures instead of successful empty replies', async () => {
  const p = new Providers(async () => stream('data: {"choices":[{"delta":{"content":"Partial"}}]}\n\n'));
  await assert.rejects(collect(p.chat('groq', DEFAULTS, {groq: 'key'}, [], new AbortController().signal)));
});
test('Gemini uses Interactions API and ignores thought summaries', async () => {
  let request;
  const p = new Providers(async (url, options) => {request = {url, ...options}; return stream('data: {"event_type":"step.delta","delta":{"type":"thought_summary","text":"private"}}\n\ndata: {"event_type":"step.delta","delta":{"type":"text","text":"Hi"}}\n\ndata: {"event_type":"interaction.completed"}\n\ndata: [DONE]\n\n');});
  assert.deepEqual(await collect(p.chat('gemini', DEFAULTS, {gemini: 'key'}, [{role: 'system', content: 'Core'}, {role: 'user', content: 'Hello'}], new AbortController().signal)), ['Hi']);
  const body = JSON.parse(request.body); assert.equal(body.system_instruction, 'Core'); assert.equal(body.store, false); assert.equal(body.stream, true);
  assert.equal(request.url, 'https://generativelanguage.googleapis.com/v1beta/interactions');
});
test('provider errors never leak the upstream response body', async () => {
  const p = new Providers(async () => new Response('key=SUPER_SECRET', {status: 401}));
  await assert.rejects(collect(p.chat('groq', DEFAULTS, {groq: 'key'}, [], new AbortController().signal)), error => !error.message.includes('SUPER_SECRET'));
});
test('unknown provider cannot become an arbitrary URL', async () => {
  let called = false; const p = new Providers(async () => {called = true;});
  await assert.rejects(collect(p.chat('https://evil.example', DEFAULTS, {'https://evil.example': 'key'}, [], new AbortController().signal))); assert.equal(called, false);
});
test('Gemini speech accepts only a bounded WAV result from model output', async () => {
  const wave = Buffer.alloc(44); wave.write('RIFF'); wave.write('WAVE', 8);
  const p = new Providers(async () => new Response(JSON.stringify({steps: [{type: 'model_output', content: [{type: 'audio', data: wave.toString('base64'), mime_type: 'audio/wav'}]}]})));
  assert.deepEqual(await p.speech(DEFAULTS, {gemini: 'key'}, 'Hello', new AbortController().signal), wave);
  const bad = new Providers(async () => new Response(JSON.stringify({output_audio: {data: Buffer.from('not wav').toString('base64')}})));
  await assert.rejects(bad.speech(DEFAULTS, {gemini: 'key'}, 'Hello', new AbortController().signal));
});
