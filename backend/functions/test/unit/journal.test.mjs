// Journalisation sans donnée personnelle : liste blanche de champs, valeurs fermées, aucune position/nom/jeton.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { assainir, journaliser } = require('../../lib/journal.js');

test('assainir : les champs autorisés passent', () => {
  assert.deepEqual(assainir({ resultat: 'ok', code: 403, nombre: 3, dureeMs: 12 }), { resultat: 'ok', code: 403, nombre: 3, dureeMs: 12 });
});

test('assainir : position, nom, jeton, uid, e-mail et tout champ inconnu sont retirés', () => {
  const sortie = assainir({
    latitude: 48.8566,
    longitude: 2.3522,
    nom: 'Thomas',
    token: 'eyJhbGciOi...',
    uid: 'abc123',
    email: 'a@b.fr',
    miffId: 'XYZ',
  });
  const texte = JSON.stringify(sortie);
  for (const interdit of ['48.8566', '2.3522', 'Thomas', 'eyJ', 'abc123', 'a@b.fr', 'XYZ', 'latitude', 'longitude', 'token', 'email', 'uid']) {
    assert.ok(!texte.includes(interdit), `${interdit} ne doit jamais apparaître : ${texte}`);
  }
});

test('assainir : un champ autorisé ne peut pas transporter du texte libre (nom, jeton) ni un nombre invalide', () => {
  assert.equal(assainir({ resultat: 'Thomas' }).resultat, '[omis]');
  assert.equal(assainir({ resultat: 'eyJhbGciOiJSUzI1NiJ9.x.y' }).resultat, '[omis]');
  assert.equal(assainir({ code: 'abc' }).code, '[omis]');
  assert.equal(assainir({ nombre: Number.NaN }).nombre, '[omis]');
  assert.equal(assainir({ dureeMs: { latitude: 1 } }).dureeMs, '[omis]');
});

test("journaliser : un nom d'événement qui ressemble à une donnée est remplacé, les champs interdits ne sont jamais écrits", () => {
  const lignes = [];
  const reelOut = process.stdout.write.bind(process.stdout);
  const reelErr = process.stderr.write.bind(process.stderr);
  const capter = (chunk) => {
    lignes.push(String(chunk));
    return true;
  };
  process.stdout.write = capter;
  process.stderr.write = capter; // les niveaux warn/error partent sur la sortie d'erreur
  try {
    journaliser('info', 'Thomas est à Paris 48.85', { resultat: 'ok', latitude: 48.85 });
    journaliser('warn', 'ping.test', { resultat: 'refuse' });
  } finally {
    process.stdout.write = reelOut;
    process.stderr.write = reelErr;
  }
  const tout = lignes.join('');
  assert.ok(tout.includes('evenement.invalide'), tout);
  assert.ok(tout.includes('ping.test'), tout);
  assert.ok(!tout.includes('Thomas') && !tout.includes('48.85') && !tout.includes('latitude'), tout);
});
