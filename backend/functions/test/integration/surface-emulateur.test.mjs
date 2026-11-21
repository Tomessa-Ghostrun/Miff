// Test d'intégration à l'ÉMULATEUR (Functions + Firestore), lancé par `npm run test:functions` depuis backend/
// (= firebase emulators:exec --only firestore,functions --project demo-miff-functions ...).
// Aucun compte, aucun secret, aucun accès à un vrai projet : tout reste sur la machine / dans le runner de la CI.
//
// Ce que ce test prouve : la « surface » exposée sur Internet par nos fonctions est EXACTEMENT celle décidée par les tickets.
//  1. Deux fonctions déclenchées par Firestore (`notifierUpdate`, `notifierAlerte`) et une par Pub/Sub (`recevoirNotificationsGooglePlay`) : aucun point d'entrée HTTP joignable ;
//  2. trois fonctions APPELABLES de facturation (SRV-7) : connexion exigée (sans jeton ou avec un jeton anonyme : refus), forme des entrées contrôlée ;
//  3. UNE seule fonction HTTP publique (`recevoirNotificationsApple`) : méthode, taille et forme contrôlées AVANT tout accès à Firestore ;
//  4. dans l'ÉMULATEUR, les clients des stores ne sont jamais réels (« non configuré » : aucun appel à Google Play ni à Apple depuis un poste de développement).
// App Check n'est pas appliqué par l'émulateur : il est prouvé par la configuration (garde-couts.test.mjs) et par la recette en conditions réelles.
import { test, before, beforeEach, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const exportees = require('../../lib/index.js');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

const projet = process.env.GCLOUD_PROJECT;
const hoteFonctions = process.env.FUNCTIONS_EMULATOR_HOST ?? '127.0.0.1:5001';
const hoteFirestore = process.env.FIRESTORE_EMULATOR_HOST;
let db;

const b64url = (v) => Buffer.from(JSON.stringify(v)).toString('base64url');
/** Jeton d'identité NON signé : l'émulateur le décode sans le vérifier (en production, un jeton non signé est refusé par Firebase). */
const jeton = (uid, fournisseur = 'password') => `${b64url({ alg: 'none', typ: 'JWT' })}.${b64url({ user_id: uid, sub: uid, aud: projet, iss: `https://securetoken.google.com/${projet}`, firebase: { sign_in_provider: fournisseur, identities: {} } })}.`;

/** Jeton App Check NON signé : l'émulateur le décode sans le vérifier (en production, Firebase refuse tout jeton App Check non signé). */
const appCheck = () => `${b64url({ alg: 'none', typ: 'JWT' })}.${b64url({ sub: '1:123456789:android:abcdef', aud: [`projects/${projet}`], iss: `https://firebaseappcheck.googleapis.com/${projet}` })}.`;

async function appeler(nom, { corps = { data: {} }, jeton: j, methode = 'POST', brut, sansAppCheck = false } = {}) {
  const reponse = await fetch(`http://${hoteFonctions}/${projet}/europe-west9/${nom}`, {
    method: methode,
    headers: { 'Content-Type': 'application/json', ...(j ? { Authorization: `Bearer ${j}` } : {}), ...(sansAppCheck ? {} : { 'X-Firebase-AppCheck': appCheck() }) },
    ...(methode === 'GET' ? {} : { body: brut ?? JSON.stringify(corps) }),
  });
  const texte = await reponse.text();
  let json = null;
  try {
    json = JSON.parse(texte);
  } catch {
    // corps non JSON
  }
  return { statut: reponse.status, texte, json };
}

async function viderEmulateur() {
  const r = await fetch(`http://${hoteFirestore}/emulator/v1/projects/${projet}/databases/(default)/documents`, { method: 'DELETE' });
  assert.equal(r.status, 200, "vidage de l'émulateur Firestore");
}

before(() => {
  assert.match(projet ?? '', /^demo-/, "ce test ne tourne que sur un projet de démonstration (lancer via 'npm run test:functions' depuis backend/)");
  assert.ok(hoteFirestore, 'FIRESTORE_EMULATOR_HOST absent : lancer via emulators:exec avec firestore ET functions');
  db = getFirestore(initializeApp({ projectId: projet }, 'surface-tests'));
});
beforeEach(viderEmulateur);
after(viderEmulateur);

test('surface : fonctions déclenchées par un événement (aucun point d\'entrée), trois appelables, UNE seule fonction HTTP publique', () => {
  const parType = (cle) => Object.entries(exportees).filter(([, f]) => f.__endpoint?.[cle]).map(([n]) => n).sort();
  assert.deepEqual(parType('eventTrigger'), ['notifierAlerte', 'notifierUpdate', 'recevoirNotificationsGooglePlay']);
  assert.deepEqual(parType('callableTrigger'), ['preparerAchat', 'supprimerDonneesAbonnement', 'synchroniserAchat']);
  assert.deepEqual(parType('httpsTrigger'), ['recevoirNotificationsApple']);
});

test('`ping` (retirée), `notifierUpdate`, `notifierAlerte` et `recevoirNotificationsGooglePlay` (déclencheurs Firestore / Pub/Sub) ne répondent à aucune requête HTTP', async () => {
  for (const nom of ['ping', 'notifierUpdate', 'notifierAlerte', 'recevoirNotificationsGooglePlay']) {
    const r = await appeler(nom);
    assert.notEqual(r.statut, 200, `${nom} : ne doit pas répondre 200 (${r.texte})`);
    assert.ok(r.statut >= 400, `${nom} : statut ${r.statut} (${r.texte})`);
    assert.doesNotMatch(r.texte, /"result"/, `${nom} : aucune réponse de fonction appelable`);
  }
});

test('appelables : SANS App Check, SANS connexion ou avec une connexion ANONYME, refus (401) ; rien n\'est écrit', async () => {
  for (const nom of ['preparerAchat', 'synchroniserAchat', 'supprimerDonneesAbonnement']) {
    // App Check est EXIGE (meme dans l'emulateur) : une connexion valide sans jeton App Check est refusee.
    const sansAppCheck = await appeler(nom, { jeton: jeton('alice'), sansAppCheck: true });
    assert.equal(sansAppCheck.statut, 401, `${nom} sans App Check : ${sansAppCheck.texte}`);
    assert.equal(sansAppCheck.json?.error?.message, 'Unauthenticated');
    const sans = await appeler(nom);
    assert.equal(sans.statut, 401, `${nom} sans connexion : ${sans.texte}`);
    assert.equal(sans.json?.error?.message, 'Connexion requise.', 'refus par appelable.ts (connexion exigee), pas par un autre controle');
    const anonyme = await appeler(nom, { jeton: jeton('anon1', 'anonymous') });
    assert.equal(anonyme.statut, 401, `${nom} anonyme : ${anonyme.texte}`);
    assert.equal(anonyme.json?.error?.message, 'Connexion requise.');
  }
  assert.equal((await db.collection('billingAccounts').get()).size, 0);
});

test('preparerAchat : connecté, renvoie un identifiant d\'achat UUID, le même à chaque appel ; le compte est créé côté serveur', async () => {
  const a = await appeler('preparerAchat', { jeton: jeton('alice') });
  assert.equal(a.statut, 200, a.texte);
  assert.match(a.json.result.purchaseId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  const b = await appeler('preparerAchat', { jeton: jeton('alice') });
  assert.equal(b.json.result.purchaseId, a.json.result.purchaseId);
  const autre = await appeler('preparerAchat', { jeton: jeton('bob') });
  assert.notEqual(autre.json.result.purchaseId, a.json.result.purchaseId);
  assert.equal((await db.doc('billingAccounts/alice').get()).get('purchaseId'), a.json.result.purchaseId);
  // L'identifiant d'achat ne vient JAMAIS de l'entrée : un champ « purchaseId » envoyé par l'appelant est ignoré.
  const triche = await appeler('preparerAchat', { jeton: jeton('alice'), corps: { data: { purchaseId: '99999999-9999-4999-8999-999999999999', uid: 'bob' } } });
  assert.equal(triche.json.result.purchaseId, a.json.result.purchaseId);
});

test('synchroniserAchat : forme des entrées contrôlée (400) ; entrée valide : l\'émulateur ne contacte JAMAIS les stores (indisponible, aucun droit accordé)', async () => {
  const t = jeton('alice');
  for (const data of [{}, { store: 'google_play' }, { store: 'google_play', purchaseToken: 'court' }, { store: 'google_play', purchaseToken: 'x'.repeat(40), extra: true }, { store: 'autre', purchaseToken: 'x'.repeat(40) }, { store: 'app_store', signedTransaction: 'pas-un-jws' }, 'texte']) {
    const r = await appeler('synchroniserAchat', { jeton: t, corps: { data } });
    assert.equal(r.statut, 400, `${JSON.stringify(data)} : ${r.texte}`);
    assert.equal(r.json?.error?.status, 'INVALID_ARGUMENT');
  }
  const google = await appeler('synchroniserAchat', { jeton: t, corps: { data: { store: 'google_play', purchaseToken: `kpdlbgmpjflhhldfpdgnhaii.${'A'.repeat(60)}` } } });
  assert.equal(google.statut, 503, google.texte);
  assert.equal(google.json.error.status, 'UNAVAILABLE');
  assert.equal(google.json.error.details?.raison, 'non_configure');
  assert.equal((await db.collection('entitlements').get()).size, 0, 'aucun droit accordé');
  assert.equal((await db.collection('billingPurchases').get()).size, 0);
  // Limite : une 2e synchronisation dans la minute est refusée avant tout appel au store.
  const limite = await appeler('synchroniserAchat', { jeton: t, corps: { data: { store: 'google_play', purchaseToken: `kpdlbgmpjflhhldfpdgnhaii.${'A'.repeat(60)}` } } });
  assert.equal(limite.statut, 429, limite.texte);
  assert.equal(limite.json.error.status, 'RESOURCE_EXHAUSTED');
});

test('supprimerDonneesAbonnement : supprime l\'identifiant d\'achat du compte de l\'APPELANT seulement', async () => {
  await appeler('preparerAchat', { jeton: jeton('alice') });
  await appeler('preparerAchat', { jeton: jeton('bob') });
  const r = await appeler('supprimerDonneesAbonnement', { jeton: jeton('alice'), corps: { data: { uid: 'bob' } } });
  assert.equal(r.statut, 200, r.texte);
  assert.equal((await db.doc('billingAccounts/alice').get()).exists, false);
  assert.equal((await db.doc('billingAccounts/bob').get()).exists, true, "l'entrée ne désigne jamais un autre compte");
});

test('recevoirNotificationsApple : méthode, taille et forme contrôlées ; AUCUNE écriture Firestore quoi qu\'on envoie', async () => {
  const get = await appeler('recevoirNotificationsApple', { methode: 'GET' });
  assert.equal(get.statut, 405);
  const pasJson = await appeler('recevoirNotificationsApple', { brut: 'ceci n\'est pas du json' });
  assert.equal(pasJson.statut, 400);
  const vide = await appeler('recevoirNotificationsApple', { brut: '{}' });
  assert.equal(vide.statut, 400);
  for (const mauvais of [{ signedPayload: 'pas-un-jws' }, { signedPayload: 42 }, { signedPayload: 'a.b' }, [1, 2]]) {
    assert.equal((await appeler('recevoirNotificationsApple', { corps: mauvais })).statut, 400, JSON.stringify(mauvais));
  }
  const gros = await appeler('recevoirNotificationsApple', { brut: JSON.stringify({ signedPayload: `${'a'.repeat(40_000)}.bbbb.cccc` }) });
  assert.equal(gros.statut, 413);
  // Forme valide mais contenu qui n'est pas d'Apple : refusé (l'émulateur n'a pas de client Apple réel : « non configuré » -> 503, jamais « accepté »).
  const forme = await appeler('recevoirNotificationsApple', { corps: { signedPayload: 'aaaa.bbbb.cccc' } });
  assert.ok([400, 503].includes(forme.statut), `jamais 200 : ${forme.statut}`);
  for (const col of ['entitlements', 'billingAccounts', 'billingPurchases', 'billingEvents']) assert.equal((await db.collection(col).get()).size, 0, col);
});
