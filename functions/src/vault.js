'use strict';
const crypto = require('node:crypto');

function keyFrom(secret) {
  if (!/^[a-f0-9]{64}$/i.test(secret || '')) throw new Error('Vault secret must be 32 random bytes in hex.');
  return Buffer.from(secret, 'hex');
}
function seal(value, secret) {
  const iv = crypto.randomBytes(12), cipher = crypto.createCipheriv('aes-256-gcm', keyFrom(secret), iv);
  cipher.setAAD(Buffer.from('kitty-provider-keys-v1'));
  const ciphertext = Buffer.concat([cipher.update(JSON.stringify(value), 'utf8'), cipher.final()]);
  return {v: 1, iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64'), data: ciphertext.toString('base64')};
}
function unseal(value, secret) {
  if (!value) return {};
  if (value.v !== 1) throw new Error('Unknown vault format.');
  const decipher = crypto.createDecipheriv('aes-256-gcm', keyFrom(secret), Buffer.from(value.iv, 'base64'));
  decipher.setAAD(Buffer.from('kitty-provider-keys-v1'));
  decipher.setAuthTag(Buffer.from(value.tag, 'base64'));
  return JSON.parse(Buffer.concat([decipher.update(Buffer.from(value.data, 'base64')), decipher.final()]).toString('utf8'));
}
module.exports = {seal, unseal};
