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
