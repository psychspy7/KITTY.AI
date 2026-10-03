'use strict';
const {onRequest} = require('firebase-functions/v2/https');
const {defineSecret, defineString} = require('firebase-functions/params');
const {initializeApp} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');
const {FirestoreRepository} = require('./repository');
const {Providers} = require('./providers');
const {createService} = require('./service');

if (process.env.FIREBASE_AUTH_EMULATOR_HOST && process.env.FUNCTIONS_EMULATOR !== 'true') throw new Error('Auth emulator settings are forbidden in the deployed function.');
initializeApp();
const vaultKey = defineSecret('KITTY_VAULT_KEY');
const region = defineString('KITTY_REGION', {default: 'asia-south1'});
const adminEmail = defineString('KITTY_ADMIN_EMAIL', {default: 'viratanand1221@gmail.com'});
const adminUid = defineString('KITTY_ADMIN_UID', {default: ''});
let service;
function getService() {
  if (!service) service = createService({repo: new FirestoreRepository(getFirestore()),
    verifyToken: token => getAuth().verifyIdToken(token, true), masterSecret: () => vaultKey.value(), providers: new Providers(),
    adminEmail: adminEmail.value() || 'viratanand1221@gmail.com', adminUid: adminUid.value()});
  return service;
}

exports.kittyApi = onRequest({region, secrets: [vaultKey], timeoutSeconds: 120, memory: '256MiB',
  minInstances: 0, maxInstances: 3, concurrency: 8, invoker: 'public'}, async (req, res) => {
  res.set({'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff'});
  // Browser CORS is intentionally absent: this is the native Android API.
  if (req.method !== 'GET' && req.method !== 'POST') { res.status(405).json({error: 'Use GET or POST.'}); return; }
  const path = req.path.replace(/^\/kittyApi(?=\/)/, '');
  if (req.method === 'GET' && path === '/health') { res.json({ok: true, version: '0.6.0', backend: 'firebase'}); return; }
  let controller, timer;
  try {
    if (Number(req.get('content-length') || 0) > 256000 || Buffer.byteLength(JSON.stringify(req.body || {})) > 256000) { res.status(413).json({error: 'Request too large.'}); return; }
    const app = getService(), user = await app.account(req.get('authorization'));
    controller = new AbortController(); timer = setTimeout(() => controller.abort(), 55_000);
    res.on('close', () => controller.abort());
    if (req.method === 'POST' && path === '/v1/chat/stream') {
      res.set({'Content-Type': 'text/event-stream; charset=utf-8', 'X-Accel-Buffering': 'no'}); res.flushHeaders();
      const emit = (event, data) => { if (!res.destroyed) res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`); };
      const heartbeat = setInterval(() => { if (!res.destroyed) res.write(': keep-alive\n\n'); }, 10_000);
      try { await app.chat(user, req.body, emit, controller.signal); }
      finally { clearInterval(heartbeat); }
      res.end();
    } else if (req.method === 'POST' && path === '/v1/speech') {
      const audio = await app.speech(user, req.body, controller.signal); res.type('audio/wav').send(audio);
    } else res.json(await app.handle(user, req.method, path, req.body || {}));
  } catch (error) {
    if (error.code === 7) {error.status = 503; error.message = 'Firebase data access needs attention from Virat. Check the function service account permissions.';}
    // Never send provider response bodies, bearer tokens or key material to logs or phones.
    const message = error.status ? error.message : 'KITTY could not finish that request. Please retry.';
    if (res.headersSent) { if (!res.destroyed) res.write(`event: error\ndata: ${JSON.stringify({error: message})}\n\n`); res.end(); }
    else res.status(error.status && error.status >= 400 && error.status <= 599 ? error.status : 500).json({error: message});
  } finally { if (timer) clearTimeout(timer); }
});
