// Test d'intégration à l'ÉMULATEUR (Functions + Firestore), lancé par `npm run test:functions` depuis backend/
// (= firebase emulators:exec --only firestore,functions --project demo-miff-functions ...).
// Aucun compte, aucun secret, aucun accès à un vrai projet : tout reste sur la machine / dans le runner de la CI.
//
// Ce que ce test prouve, à travers une vraie requête HTTP vers la fonction chargée par l'émulateur :
//  1. App Check est EXIGÉ : sans jeton App Check, la fonction n'exécute pas notre code ;
//  2. la connexion est exigée en plus ;
//  3. avec les deux, la fonction répond { ok: true } ;
//  4. la fonction n'écrit rien dans Firestore (émulateur lancé en même temps, base vide avant et après).
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const exportees = require('../../lib/index.js');

const projet = process.env.GCLOUD_PROJECT;
const hoteFonctions = process.env.FUNCTIONS_EMULATOR_HOST ?? '127.0.0.1:5001';
const hoteFirestore = process.env.FIRESTORE_EMULATOR_HOST;

/** Jeton non signé : l'émulateur ne vérifie pas les signatures (skipTokenVerification) ; la production, si. */
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const jetonFactice = (charge) => `${b64({ alg: 'none', typ: 'JWT' })}.${b64(charge)}.`;
const maintenant = Math.floor(Date.now() / 1000);
const jetonConnexion = jetonFactice({ aud: projet, iss: `https://securetoken.google.com/${projet}`, sub: 'utilisateur-test', user_id: 'utilisateur-test', iat: maintenant, exp: maintenant + 3600, firebase: { sign_in_provider: 'password' } });
const jetonAppCheck = jetonFactice({ aud: [`projects/${projet}`], sub: '1:000000000000:android:0000000000000000', iat: maintenant, exp: maintenant + 3600 });

async function appeler(nom, entetes = {}, donnees = {}) {
  const url = `http://${hoteFonctions}/${projet}/europe-west9/${nom}`;
  const reponse = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...entetes },
    body: JSON.stringify({ data: donnees }),
  });
  const texte = await reponse.text();
  let json;
  try {
    json = JSON.parse(texte);
  } catch {
    json = undefined;
  }
  return { statut: reponse.status, json, texte };
}

const FIRESTORE_DOCS = () => `http://${hoteFirestore}/v1/projects/${projet}/databases/(default)/documents`;
async function lister(chemin) {
  const r = await fetch(`${FIRESTORE_DOCS()}/${chemin}`, { headers: { Authorization: 'Bearer owner' } });
  const texte = await r.text();
  assert.equal(r.status, 200, `lecture Firestore (émulateur) : ${texte}`);
  return JSON.parse(texte).documents ?? [];
}

before(() => {
  assert.match(projet ?? '', /^demo-/, "ce test ne tourne que sur un projet de démonstration (lancer via 'npm run test:functions' depuis backend/)");
  assert.ok(hoteFirestore, 'FIRESTORE_EMULATOR_HOST absent : lancer via emulators:exec avec firestore ET functions');
});

test('chaque fonction appelable exportée refuse une requête SANS jeton App Check (même connecté)', async () => {
  const appelables = Object.entries(exportees).filter(([, f]) => f.__endpoint?.callableTrigger);
  assert.ok(appelables.length >= 1);
  for (const [nom] of appelables) {
    const r = await appeler(nom, { Authorization: `Bearer ${jetonConnexion}` });
    assert.equal(r.statut, 401, `${nom} sans App Check devrait répondre 401, reçu ${r.statut} : ${r.texte}`);
    assert.equal(r.json?.error?.status, 'UNAUTHENTICATED');
  }
});

test('ping : refusé sans connexion même avec un jeton App Check', async () => {
  const r = await appeler('ping', { 'X-Firebase-AppCheck': jetonAppCheck });
  assert.equal(r.statut, 401, r.texte);
  assert.equal(r.json?.error?.status, 'UNAUTHENTICATED');
});

test('ping : répond { ok: true } avec App Check ET connexion, et ne renvoie rien de ce qu\'on lui envoie', async () => {
  const r = await appeler('ping', { 'X-Firebase-AppCheck': jetonAppCheck, Authorization: `Bearer ${jetonConnexion}` }, { latitude: 48.85, nom: 'x' });
  assert.equal(r.statut, 200, r.texte);
  assert.deepEqual(r.json, { result: { ok: true } });
});

test("ping : n'écrit rien dans Firestore (base de l'émulateur vide après les appels)", async () => {
  for (const collection of ['users', 'miffs', 'entitlements']) {
    assert.deepEqual(await lister(collection), [], `${collection} devrait être vide`);
  }
});
