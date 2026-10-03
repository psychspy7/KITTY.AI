'use strict';

class ProviderError extends Error {
  constructor(status = 502) { super('Model provider unavailable. Check its key, model and quota in Admin console.'); this.status = status === 429 ? 429 : 502; }
}
const ENDPOINTS = Object.freeze({groq: 'https://api.groq.com/openai/v1', openai: 'https://api.openai.com/v1'});
const GEMINI = 'https://generativelanguage.googleapis.com/v1beta/interactions';

async function jsonResponse(response, max = 8_000_000) {
  if (!response.ok) { await response.body?.cancel(); throw new ProviderError(response.status); }
  const bytes = [];
  let count = 0;
  for await (const chunk of response.body) { count += chunk.length; if (count > max) throw new ProviderError(); bytes.push(chunk); }
  return JSON.parse(Buffer.concat(bytes).toString('utf8'));
}

// Correctly handles UTF-8 split across chunks, CRLF, comments and multiline data.
async function* sse(body) {
  const decoder = new TextDecoder(); let buffer = '', data = [], size = 0;
  const line = value => {
    if (!value) { const result = data.join('\n'); data = []; return result; }
    if (value.startsWith('data:')) data.push(value.slice(5).trimStart());
    return null;
  };
  for await (const chunk of body) {
    size += chunk.length; if (size > 2_000_000) throw new ProviderError();
    buffer += decoder.decode(chunk, {stream: true});
    if (buffer.length > 200_000) throw new ProviderError();
    let end;
    while ((end = buffer.indexOf('\n')) >= 0) {
      const result = line(buffer.slice(0, end).replace(/\r$/, '')); buffer = buffer.slice(end + 1);
      if (result) yield result;
    }
  }
  buffer += decoder.decode();
  if (buffer) { const result = line(buffer.replace(/\r$/, '')); if (result) yield result; }
  if (data.length) yield data.join('\n');
}

class Providers {
  constructor(fetchImpl = fetch) { this.fetch = fetchImpl; }
  async *chat(provider, settings, keys, messages, signal) {
    const key = keys[provider]; if (!key) throw new ProviderError();
    let url, headers, body;
    if (provider === 'gemini') {
      url = GEMINI; headers = {'x-goog-api-key': key};
      // Stateless request. Bounded Firestore history is explicitly supplied;
      // no cross-user provider conversation ID or local PC session is reused.
      body = {model: settings.gemini_chat_model, system_instruction: messages[0].content,
        input: JSON.stringify({conversation: messages.slice(1)}), stream: true, store: false,
        generation_config: {max_output_tokens: 1536, thinking_level: 'low'}};
    } else {
      if (!ENDPOINTS[provider]) throw new ProviderError();
      url = ENDPOINTS[provider] + '/chat/completions'; headers = {Authorization: 'Bearer ' + key};
      const model = provider === 'groq' ? settings.groq_model : settings.openai_model;
      body = {model, messages, stream: true, max_completion_tokens: 1536};
      if (provider === 'groq' && model.startsWith('openai/gpt-oss')) Object.assign(body, {reasoning_effort: 'low', reasoning_format: 'hidden'});
      if (provider === 'openai' && /^(gpt-5|gpt-6|o[134])/.test(model)) body.reasoning_effort = 'low';
    }
    const response = await this.fetch(url, {method: 'POST', headers: {...headers, 'Content-Type': 'application/json', Accept: 'text/event-stream'}, body: JSON.stringify(body), signal, redirect: 'error'});
    if (!response.ok || !response.body) { await response.body?.cancel(); throw new ProviderError(response.status); }
    let completed = false;
    for await (const raw of sse(response.body)) {
      if (raw === '[DONE]') { if (provider !== 'gemini') completed = true; break; }
      const event = JSON.parse(raw);
      if (event.error || event.event_type === 'error' || event.event_type === 'interaction.failed') throw new ProviderError();
      if (provider === 'gemini') {
        if (event.event_type === 'step.delta' && event.delta?.type === 'text' && typeof event.delta.text === 'string') yield event.delta.text;
        if (event.event_type === 'interaction.completed') completed = true;
      } else {
        const text = event.choices?.[0]?.delta?.content; if (typeof text === 'string') yield text;
      }
    }
    if (!completed) throw new ProviderError();
  }
  async models(keys, signal) {
    if (!keys.groq) return [];
    const data = await jsonResponse(await this.fetch(ENDPOINTS.groq + '/models', {headers: {Authorization: 'Bearer ' + keys.groq}, signal, redirect: 'error'}), 200_000);
    return (data.data || []).map(m => m.id).filter(id => typeof id === 'string').slice(0, 100);
  }
  async speech(settings, keys, text, signal) {
    if (!keys.gemini) throw new ProviderError();
    const data = await jsonResponse(await this.fetch(GEMINI, {
      method: 'POST', headers: {'x-goog-api-key': keys.gemini, 'Content-Type': 'application/json'}, signal, redirect: 'error',
      body: JSON.stringify({model: settings.gemini_model, store: false,
        input: [{type: 'user_input', content: [{type: 'text', text, annotations: [{type: 'speech_metadata', style: 'Warm, witty and conversational. Read the supplied words verbatim.'}]}]}],
        response_format: {type: 'audio'}, generation_config: {speech_config: [{voice: settings.voice}]}})
    }));
    // REST Interaction outputs are model_output steps, not SDK helper properties.
    const contents = (data.steps || []).filter(s => s.type === 'model_output').flatMap(s => s.content || []);
    const audio = data.output_audio || contents.find(c => c.type === 'audio');
    if (!audio || typeof audio.data !== 'string') throw new ProviderError();
    const bytes = Buffer.from(audio.data, 'base64');
    if (bytes.length < 44 || bytes.length > 5_000_000 || bytes.toString('ascii', 0, 4) !== 'RIFF' || bytes.toString('ascii', 8, 12) !== 'WAVE') throw new ProviderError();
    return bytes;
  }
}
module.exports = {Providers, ProviderError, sse};
