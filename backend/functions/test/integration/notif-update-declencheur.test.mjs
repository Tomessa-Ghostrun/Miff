// SRV-4B : le DÉCLENCHEUR, chargé par l'émulateur de fonctions (Functions + Firestore). Ici la vraie fonction `notifierUpdate` s'exécute dans l'émulateur ;
// son client FCM est désactivé en émulateur (FUNCTIONS_EMULATOR=true : aucun message n'est envoyé, voir src/notifFcm.ts). On observe donc l'effet
// d'idempotence dans notifState/{miffId} : il n'est écrit que si la fonction est allée jusqu'à la réservation d'un envoi.
//
// Ce que ce test prouve : la fonction se déclenche à la CRÉATION et au REMPLACEMENT de miffs/{miffId}/photos/{uid}, et pas à un EFFACEMENT.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue, Timestamp } = require('firebase-admin/firestore');

const projet = process.env.GCLOUD_PROJECT;
const hoteFirestore = process.env.FIRESTORE_EMULATOR_HOST;
let db;

const dormir = (ms) => new Promise((r) => setTimeout(r, ms));
async function attendre(condition, message, delaiMs = 60_000) {
  const fin = Date.now() + delaiMs;
  for (;;) {
    const valeur = await condition();
    if (valeur) return valeur;
    if (Date.now() > fin) assert.fail(`délai dépassé : ${message}`);
    await dormir(250);
  }
}
async function viderEmulateur() {
  const r = await fetch(`http://${hoteFirestore}/emulator/v1/projects/${projet}/databases/(default)/documents`, { method: 'DELETE' });
  assert.equal(r.status, 200);
}

before(async () => {
  assert.match(projet ?? '', /^demo-/, "ce test ne tourne que sur un projet de démonstration (lancer via 'npm run test:functions' depuis backend/)");
  assert.ok(hoteFirestore);
  db = getFirestore(initializeApp({ projectId: projet }, 'notif-declencheur-tests'));
  await viderEmulateur();
});
after(viderEmulateur);

const MIFF = 'MiffDeclencheur';
// La fonction verifie que la Miff existe encore (miffs/{miffId}) avant tout envoi.
before(async () => {
  await db.doc(`miffs/${MIFF}`).set({ ownerUid: 'A1', name: 'Miff de test' });
});
async function membre(uid, partage, appareil = true) {
  await db.doc(`miffs/${MIFF}/members/${uid}`).set({ role: 'member', firstName: uid, sharing: { enabled: partage } });
  if (appareil) await db.doc(`users/${uid}/devices/dev0`).set({ token: `jeton-${uid}`, platform: 'android', tz: 'Europe/Paris', notify: { updates: true } });
  // Session active du compte : la fonction n'envoie qu'a l'appareil de la session (contrat des jetons §7).
  if (appareil) await db.doc(`users/${uid}/session/active`).set({ deviceId: 'dev0', startedAt: Timestamp.fromMillis(Date.now() - 3_600_000) });
}
const photo = () => ({ jpeg: Buffer.from([1, 2, 3]), createdAt: FieldValue.serverTimestamp(), expiresAt: Timestamp.fromMillis(Date.now() + 3_600_000), lat: 48.85661, lng: 2.35222, measuredAt: FieldValue.serverTimestamp() });
const etatAuteur = async (uid) => (await db.doc(`notifState/${MIFF}`).get()).get(`authors.${uid}`);

test("création d'une photo -> la fonction s'exécute (elle réserve l'envoi dans notifState) ; l'état ne contient que des horodatages", async () => {
  await membre('A1', true);
  await membre('B1', true);
  await db.doc(`miffs/${MIFF}/photos/A1`).set(photo());
  const a = await attendre(() => etatAuteur('A1'), "notifState/{miff}.authors.A1 n'est jamais apparu : le déclencheur ne s'est pas exécuté");
  assert.deepEqual(Object.keys(a).sort(), ['lastNotifiedAt', 'lastSeenCreatedAt']);
});

test('REMPLACEMENT d\'une photo existante -> la fonction s\'exécute aussi (ici l\'auteur ne partageait pas à la première écriture)', async () => {
  await membre('A2', false);
  await db.doc(`miffs/${MIFF}/photos/A2`).set(photo()); // création : auteur sans partage, rien à réserver
  await dormir(2500);
  assert.equal(await etatAuteur('A2'), undefined, "pas d'envoi pour un auteur qui ne partage pas");
  await db.doc(`miffs/${MIFF}/members/A2`).update({ 'sharing.enabled': true });
  await db.doc(`miffs/${MIFF}/photos/A2`).set(photo()); // remplacement
  await attendre(() => etatAuteur('A2'), "le remplacement d'une photo n'a pas déclenché la fonction");
});

test("EFFACEMENT d'une photo (expiration, arrêt du partage, départ) -> la fonction ne fait rien", async () => {
  await membre('A3', false);
  await db.doc(`miffs/${MIFF}/photos/A3`).set(photo());
  await dormir(2500);
  await db.doc(`miffs/${MIFF}/members/A3`).update({ 'sharing.enabled': true }); // toutes les conditions d'un envoi sont réunies...
  await db.doc(`miffs/${MIFF}/photos/A3`).delete(); // ... mais l'événement est un effacement
  await dormir(5000);
  assert.equal(await etatAuteur('A3'), undefined, 'un effacement ne doit rien réserver ni envoyer');
});
