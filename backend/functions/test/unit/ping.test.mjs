// Fonction témoin `ping` : refuse sans connexion, répond { ok: true } avec connexion, ne renvoie aucune donnée.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { ping } = require('../../lib/index.js');

test('ping : sans connexion -> unauthenticated', async () => {
  await assert.rejects(() => ping.run({ data: {} }), (e) => e.code === 'unauthenticated');
});

test("ping : avec connexion -> { ok: true } et rien d'autre (aucune donnée d'entrée n'est renvoyée)", async () => {
  const sortie = await ping.run({ data: { latitude: 1, nom: 'x' }, auth: { uid: 'u1', token: {} } });
  assert.deepEqual(sortie, { ok: true });
});

test("ping : connexion anonyme refusée (le projet n'active pas la connexion anonyme, on le vérifie aussi côté fonction)", async () => {
  await assert.rejects(
    () => ping.run({ data: {}, auth: { uid: 'u2', token: { firebase: { sign_in_provider: 'anonymous' } } } }),
    (e) => e.code === 'unauthenticated',
  );
  const ok = await ping.run({ data: {}, auth: { uid: 'u3', token: { firebase: { sign_in_provider: 'password' } } } });
  assert.deepEqual(ok, { ok: true });
});
