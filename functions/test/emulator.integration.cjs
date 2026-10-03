'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {initializeTestEnvironment, assertFails} = require('@firebase/rules-unit-testing');
const {initializeApp: clientApp, deleteApp: deleteClient} = require('firebase/app');
const {getAuth: clientAuth, connectAuthEmulator, signInWithCredential, GoogleAuthProvider} = require('firebase/auth');
const {doc, getDoc, setDoc} = require('firebase/firestore');
const {initializeApp: adminApp, deleteApp: deleteAdmin} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');
const {FirestoreRepository} = require('../src/repository');
const {createService} = require('../src/service');

test('real Firebase Auth/Firestore emulators enforce owner, rules, transactions and isolated chat history', {timeout: 60_000}, async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Run only with Firebase emulators.');
  assert.ok(process.env.FIREBASE_AUTH_EMULATOR_HOST, 'Auth emulator required.');
  const projectId = 'demo-kitty-ci', admin = adminApp({projectId}, 'integration-admin');
  const clients = [], env = await initializeTestEnvironment({projectId, firestore: {rules: fs.readFileSync('firestore.rules', 'utf8')}});
  try {
    await env.clearFirestore();
    async function google(name, email) {
      const app = clientApp({projectId, apiKey: 'emulator-only', authDomain: projectId + '.firebaseapp.com'}, name); clients.push(app);
      const auth = clientAuth(app); connectAuthEmulator(auth, 'http://' + process.env.FIREBASE_AUTH_EMULATOR_HOST, {disableWarnings: true});
      const result = await signInWithCredential(auth, GoogleAuthProvider.credential(JSON.stringify({sub: name, email, email_verified: true})));
      return {uid: result.user.uid, token: await result.user.getIdToken()};
    }
    const virat = await google('owner-test', 'viratanand1221@gmail.com'), alice = await google('alice-test', 'alice@example.com');
    const repo = new FirestoreRepository(getFirestore(admin)), sdkAuth = getAuth(admin);
    const service = createService({repo, masterSecret: () => 'c'.repeat(64), adminUid: virat.uid,
      verifyToken: token => sdkAuth.verifyIdToken(token, true),
      providers: {async *chat() {yield 'Firebase reply';}, async models() {return [];}}});
    const owner = await service.account('Bearer ' + virat.token), user = await service.account('Bearer ' + alice.token);
    assert.equal(owner.admin, true); assert.equal(user.admin, false);
    await service.handle(owner, 'POST', '/v1/admin/settings', {groq_key: 'test-only-key', fallback_provider: 'none'});
    await assert.rejects(service.handle(user, 'POST', '/v1/admin/settings', {creator: 'Spoofed'}), {status: 403});
    const events = [];
    await service.chat(user, {text: 'Hello', session: 's1', request_id: 'r1'}, (kind, data) => events.push({kind, data}), new AbortController().signal);
    assert.equal(events.at(-1).data.reply, 'Firebase reply');
    assert.equal((await service.handle(user, 'GET', '/v1/history')).turns.length, 1);
    assert.equal((await service.handle(owner, 'GET', '/v1/history')).turns.length, 0);
    const written = await repo.get('private/config'); assert.ok(written.sealed); assert.ok(!JSON.stringify(written).includes('test-only-key'));
    // Even a client with a forged admin claim cannot bypass deny-all rules.
    for (const db of [env.unauthenticatedContext().firestore(), env.authenticatedContext(alice.uid).firestore(), env.authenticatedContext(virat.uid, {admin: true}).firestore()]) {
      await assertFails(getDoc(doc(db, 'private/config')));
      await assertFails(setDoc(doc(db, 'private/config'), {creator: 'Forged'}));
      await assertFails(getDoc(doc(db, `users/${alice.uid}/turns/r1`)));
    }
    // Exercise the real exported HTTPS handler with emulator-issued Google
    // Firebase tokens. No production project or paid provider call is involved.
    process.env.GCLOUD_PROJECT = projectId;
    process.env.FUNCTIONS_EMULATOR = 'true';
    process.env.KITTY_ADMIN_UID = virat.uid;
    process.env.KITTY_VAULT_KEY = 'c'.repeat(64);
    const express = require('express');
    const app = express(); app.use(express.json()); app.use(require('../src/index').kittyApi);
    const server = app.listen(0, '127.0.0.1');
    await new Promise(resolve => server.once('listening', resolve));
    const url = 'http://127.0.0.1:' + server.address().port;
    try {
      assert.equal((await fetch(url + '/health')).status, 200);
      assert.equal((await fetch(url + '/v1/me')).status, 401);
      const me = await (await fetch(url + '/v1/me', {headers: {Authorization: 'Bearer ' + virat.token}})).json();
      assert.equal(me.role, 'admin');
      assert.equal((await fetch(url + '/v1/admin/settings', {headers: {Authorization: 'Bearer ' + alice.token}})).status, 403);
      const settings = await (await fetch(url + '/v1/admin/settings', {headers: {Authorization: 'Bearer ' + virat.token}})).json();
      assert.equal(settings.groq_configured, true); assert.ok(!JSON.stringify(settings).includes('test-only-key'));
      const response = await fetch(url + '/v1/chat/stream', {method: 'POST', headers: {Authorization: 'Bearer ' + virat.token, 'Content-Type': 'application/json'}, body: JSON.stringify({text: 'Who made you?', session: 'http-session', request_id: 'http-intro'})});
      assert.ok(response.headers.get('content-type').startsWith('text/event-stream'));
      const events = await response.text(); assert.ok(events.includes('event: done')); assert.ok(events.includes('Virat with the help of Kitty Corp'));
    } finally {server.closeAllConnections(); await new Promise(resolve => server.close(resolve));}
    await sdkAuth.updateUser(alice.uid, {disabled: true});
    await assert.rejects(service.account('Bearer ' + alice.token), {status: 401});
  } finally { await env.cleanup(); await Promise.all(clients.map(deleteClient)); await deleteAdmin(admin); }
});
