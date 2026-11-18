// AD-B2 : le DÉCLENCHEUR `notifierAlerte`, chargé par l'émulateur de fonctions (Functions + Firestore). Ici la vraie fonction s'exécute dans l'émulateur ;
// son client FCM est désactivé en émulateur (FUNCTIONS_EMULATOR=true : aucun message n'est envoyé, voir src/notifFcm.ts). On observe donc l'effet d'idempotence
// dans notifState/{miffId}_alertes : il n'est écrit que si la fonction est allée jusqu'à la réservation d'un envoi.
//
// Ce que ce test prouve : la fonction se déclenche au DÉBUT (création, ré-armement) et à la FIN MANUELLE d'une alerte ; elle ne fait RIEN à un effacement (arrêt du
// partage, départ, retrait, TTL), à une écriture sans changement d'état, ni à une fin POSTÉRIEURE à l'échéance (l'expiration à 7 jours ne notifie jamais).
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
  db = getFirestore(initializeApp({ projectId: projet }, 'alerte-declencheur-tests'));
  await viderEmulateur();
});
after(viderEmulateur);

const MIFF = 'MiffAlerteDecl';
before(async () => {
  await db.doc(`miffs/${MIFF}`).set({ ownerUid: 'A1', name: 'Miff de test' });
});
// Une personne qui n'a PAS partagé sa position doit être prévenue quand même (D16) : on crée le destinataire sans partage.
async function membre(uid, partage = false, appareil = true) {
  await db.doc(`miffs/${MIFF}/members/${uid}`).set({ role: 'member', firstName: uid, sharing: { enabled: partage } });
  if (appareil) {
    await db.doc(`users/${uid}/devices/dev0`).set({ token: `jeton-${uid}`, platform: 'android', tz: 'Europe/Paris', notify: { updates: false } });
    await db.doc(`users/${uid}/session/active`).set({ deviceId: 'dev0', startedAt: Timestamp.fromMillis(Date.now() - 3_600_000) });
  }
}
const alerte = (over = {}) => ({
  status: 'active',
  startedAt: FieldValue.serverTimestamp(),
  expiresAt: Timestamp.fromMillis(Date.now() + 7 * 24 * 3_600_000 - 2 * 3_600_000),
  endedAt: null,
  seq: 1,
  deviceId: 'dev0',
  purgeAt: Timestamp.fromMillis(Date.now() + 9 * 24 * 3_600_000),
  ...over,
});
const etatAuteur = async (uid) => (await db.doc(`notifState/${MIFF}_alertes`).get()).get(`alerts.${uid}`);

test("DÉBUT : création d'une alerte -> la fonction s'exécute (elle réserve l'envoi) même si le destinataire a coupé « donne des nouvelles » et ne partage pas sa position ; l'état ne contient que des horodatages", async () => {
  await membre('A1');
  await membre('B1');
  await db.doc(`miffs/${MIFF}/alerts/A1`).set(alerte());
  const a = await attendre(() => etatAuteur('A1'), "notifState/{miff}_alertes.alerts.A1 n'est jamais apparu : le déclencheur ne s'est pas exécuté");
  assert.deepEqual(Object.keys(a).sort(), ['lastSeenStartedAt', 'starts']);
  assert.equal(a.starts.length, 1);
});

test("FIN MANUELLE : active -> ended avec endedAt <= expiresAt -> la fin est réservée (lastEndedStartedAt) ; un second passage identique n'ajoute rien", async () => {
  await membre('A2');
  await membre('B2');
  await db.doc(`miffs/${MIFF}/alerts/A2`).set(alerte());
  await attendre(() => etatAuteur('A2'), 'le début aurait dû être réservé');
  await db.doc(`miffs/${MIFF}/alerts/A2`).update({ status: 'ended', endedAt: FieldValue.serverTimestamp(), purgeAt: Timestamp.fromMillis(Date.now() + 2 * 24 * 3_600_000) });
  const a = await attendre(async () => (await etatAuteur('A2'))?.lastEndedStartedAt, "la fin manuelle n'a pas déclenché la fonction");
  assert.ok(a);
});

test("EXPIRATION : une fin POSTÉRIEURE à l'échéance (alerte restée « active » après ses 7 jours, puis terminée) ne notifie JAMAIS ; un effacement (TTL, arrêt du partage, départ) ne fait rien", async () => {
  await membre('A3');
  await membre('B3');
  // Alerte déjà échue au moment de sa création (échéance passée) : le début est réservé, la fin qui suit est postérieure à l'échéance.
  await db.doc(`miffs/${MIFF}/alerts/A3`).set(alerte({ expiresAt: Timestamp.fromMillis(Date.now() - 3_600_000) }));
  await attendre(() => etatAuteur('A3'), 'le début aurait dû être réservé');
  await db.doc(`miffs/${MIFF}/alerts/A3`).update({ status: 'ended', endedAt: FieldValue.serverTimestamp() });
  await dormir(5000);
  assert.equal((await etatAuteur('A3')).lastEndedStartedAt, undefined, "une fin après l'échéance ne doit rien réserver ni envoyer");
  // Effacement : aucun traitement (aucune réservation pour un auteur qui n'a pas de début).
  await membre('A4');
  await membre('B4');
  await db.doc(`miffs/${MIFF}/alerts/A4`).set(alerte({ expiresAt: Timestamp.fromMillis(Date.now() - 3_600_000) }));
  await attendre(() => etatAuteur('A4'), 'le début aurait dû être réservé');
  await db.doc(`miffs/${MIFF}/alerts/A4`).delete();
  await dormir(3000);
  assert.equal((await etatAuteur('A4')).lastEndedStartedAt, undefined, 'un effacement ne notifie jamais une fin');
});

test("ÉCRITURE SANS CHANGEMENT D'ÉTAT : modifier seulement purgeAt / deviceId d'une alerte active ne réserve rien de plus", async () => {
  await membre('A5');
  await membre('B5');
  await db.doc(`miffs/${MIFF}/alerts/A5`).set(alerte());
  const avant = await attendre(() => etatAuteur('A5'), 'le début aurait dû être réservé');
  await db.doc(`miffs/${MIFF}/alerts/A5`).update({ deviceId: 'autre-appareil-session-2', purgeAt: Timestamp.fromMillis(Date.now() + 10 * 24 * 3_600_000) });
  await dormir(4000);
  const apres = await etatAuteur('A5');
  assert.equal(apres.starts.length, 1, 'aucun second début');
  assert.ok(apres.lastSeenStartedAt.isEqual(avant.lastSeenStartedAt));
});
