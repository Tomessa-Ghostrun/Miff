// SRV-4B : traitement d'une update publiée, À L'ÉMULATEUR Firestore (lancé par `npm run test:functions` depuis backend/), avec un FAUX client FCM injecté.
// Aucun compte, aucun secret, aucun envoi réel. Les vecteurs V1 à V13 sont ceux du ticket docs/tickets/notification-update-membres.md §19.
//
// Ces tests appellent `traiterPhotoPubliee` directement (Firestore, FCM, horloge et attente injectés) et N'ÉCRIVENT JAMAIS dans photos/ :
// l'émulateur de fonctions tourne en même temps et déclencherait la vraie fonction (voir notif-update-declencheur.test.mjs, qui teste le déclencheur).
import { test, before, beforeEach, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { traiterPhotoPubliee } = require('../../lib/notifTraitement.js');

const projet = process.env.GCLOUD_PROJECT;
const hoteFirestore = process.env.FIRESTORE_EMULATOR_HOST;
let db;

before(() => {
  assert.match(projet ?? '', /^demo-/, "ce test ne tourne que sur un projet de démonstration (lancer via 'npm run test:functions' depuis backend/)");
  assert.ok(hoteFirestore, 'FIRESTORE_EMULATOR_HOST absent : lancer via emulators:exec avec firestore ET functions');
  db = getFirestore(initializeApp({ projectId: projet }, 'notif-update-tests'));
});

async function viderEmulateur() {
  const r = await fetch(`http://${hoteFirestore}/emulator/v1/projects/${projet}/databases/(default)/documents`, { method: 'DELETE' });
  assert.equal(r.status, 200, 'vidage de l\'émulateur Firestore');
}
beforeEach(viderEmulateur);
after(viderEmulateur);

// --- Aides --------------------------------------------------------------------------------------------------------------------------------

const MIFF = 'MiffTest1';
/** 14:00:00 à Paris (hiver) = 13:00:00 UTC. */
const T_PUBLICATION = new Date('2026-12-10T13:00:00Z');
const ts = (date) => Timestamp.fromDate(date);
const plus = (date, ms) => new Date(date.getTime() + ms);
const MIN = 60_000;

async function creerMiff(miffId, membres) {
  await db.doc(`miffs/${miffId}`).set({ ownerUid: Object.keys(membres)[0], name: 'Famille Dupont', joinCode: 'SECRETCODE12' });
  for (const [uid, m] of Object.entries(membres)) {
    await db.doc(`miffs/${miffId}/members/${uid}`).set({
      role: 'member',
      firstName: m.prenom ?? uid,
      lastName: 'Dupont',
      phone: '+33612345678',
      avatar: 'avatar-secret',
      sharing: { enabled: m.partage ?? true, consentedAt: ts(T_PUBLICATION), consentVersion: '2026-11-v3', revokedAt: null },
    });
    for (const [i, appareil] of (m.appareils ?? [{}]).entries()) {
      if (appareil === null) continue;
      await db.doc(`users/${uid}/devices/dev${i}`).set({
        token: appareil.token ?? `jeton-${uid}-${i}`,
        platform: appareil.platform ?? 'android',
        tz: appareil.tz ?? 'Europe/Paris',
        appVersion: '1.0.0 (12)',
        createdAt: ts(T_PUBLICATION),
        notify: 'notify' in appareil ? appareil.notify : { updates: true },
        ...(appareil.extra ?? {}),
        updatedAt: appareil.updatedAt ?? ts(T_PUBLICATION),
      });
    }
    // Session active du compte (SESS) : `session: 'dev1'` ou `{ deviceId, startedAt }`.
    const aUnAppareil = (m.appareils ?? [{}]).some((a) => a !== null);
    const session = m.session === undefined ? (aUnAppareil ? 'dev0' : null) : m.session;
    if (session) {
      const s = typeof session === 'string' ? { deviceId: session, startedAt: ts(plus(T_PUBLICATION, -60 * 60_000)) } : session;
      await db.doc(`users/${uid}/session/active`).set(s);
    }
  }
}

/** Faux client FCM : enregistre chaque message ; `scenarios[token]` = liste de codes d'erreur à renvoyer, un par tentative. */
function fauxFcm(scenarios = {}, avantErreur = async () => {}) {
  const messages = [];
  const restants = Object.fromEntries(Object.entries(scenarios).map(([k, v]) => [k, [...v]]));
  return {
    messages,
    async envoyer(message) {
      messages.push(structuredClone(message));
      const file = restants[message.token];
      if (file?.length) {
        const code = file.shift();
        if (code) {
          await avantErreur(message.token);
          throw Object.assign(new Error('erreur simulée'), { code });
        }
      }
    },
  };
}

function contexte({ fcm = fauxFcm(), maintenant = plus(T_PUBLICATION, 5000) } = {}) {
  const horloge = { maintenant };
  const attentes = [];
  return {
    horloge,
    attentes,
    fcm,
    dep: { db, fcm, maintenant: () => horloge.maintenant, attendre: async (ms) => void attentes.push(ms) },
  };
}

const publier = (c, { miffId = MIFF, auteurUid = 'A', quand = T_PUBLICATION } = {}) =>
  traiterPhotoPubliee(c.dep, { miffId, auteurUid, createdAt: ts(quand) });

const jetonsEnvoyes = (c) => c.fcm.messages.map((m) => m.token).sort();
const etat = async (miffId = MIFF) => (await db.doc(`notifState/${miffId}`).get()).data();
const existe = async (chemin) => (await db.doc(chemin).get()).exists;

const quatre = () => ({
  A: { prenom: 'Tomessa' },
  B: { prenom: 'Amandine', appareils: [{ platform: 'ios' }] },
  C: { prenom: 'Camille' },
  D: { prenom: 'Denis' },
});

// --- V1 : destinataires, auteur exclu, forme exacte, budget de lectures ----------------------------------------------------------------

test('V1 : A publie à 14:00 -> B, C, D notifiés, A non ; message de forme exacte ; 13 lectures et 1 écriture (Miff de 4)', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  const bilan = await publier(c);

  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0', 'jeton-C-0', 'jeton-D-0']);
  assert.equal(bilan.envoyes, 3);
  assert.equal(bilan.motif, undefined);
  assert.equal(bilan.lectures, 13, '1 pré-contrôle + 1 Miff + 4 fiches + 3 destinataires x (session + appareil) + 1 état (transaction)');
  assert.equal(bilan.ecritures, 1, "1 écriture : l'état de la Miff");

  const parJeton = Object.fromEntries(c.fcm.messages.map((m) => [m.token, m]));
  assert.deepEqual(parJeton['jeton-C-0'], {
    token: 'jeton-C-0',
    data: { type: 'circle_update', miffId: MIFF, authorUid: 'A', firstName: 'Tomessa', quiet: '0' },
    android: { priority: 'high', ttl: 7_200_000 },
  });
  assert.deepEqual(parJeton['jeton-B-0'], {
    token: 'jeton-B-0',
    data: { type: 'circle_update', miffId: MIFF, authorUid: 'A' },
    apns: {
      headers: { 'apns-push-type': 'alert', 'apns-priority': '10', 'apns-expiration': String(Math.floor(plus(T_PUBLICATION, 5000 + 2 * 60 * MIN).getTime() / 1000)), 'apns-collapse-id': 'A' },
      payload: { aps: { alert: { titleLocKey: 'notif_circle_update_title', titleLocArgs: ['Tomessa'], locKey: 'notif_circle_update_body' }, threadId: 'A', sound: 'default' } },
    },
  });

  // L'état serveur ne contient que des horodatages, rien de personnel.
  const e = await etat();
  assert.deepEqual(Object.keys(e).sort(), ['authors', 'expireAt']);
  assert.deepEqual(Object.keys(e.authors), ['A']);
  assert.deepEqual(Object.keys(e.authors.A).sort(), ['lastNotifiedAt', 'lastSeenCreatedAt']);
  assert.ok(e.authors.A.lastSeenCreatedAt.isEqual(ts(T_PUBLICATION)));
  assert.ok(e.expireAt.isEqual(ts(plus(T_PUBLICATION, 5000 + 2 * 24 * 60 * MIN))), 'suppression automatique (TTL) 2 jours après la dernière écriture');
});

// --- V2 à V4 et exclusions ---------------------------------------------------------------------------------------------------------------

test('V2 : un membre qui a arrêté son partage (sharing.enabled = false) ne reçoit rien', async () => {
  const m = quatre();
  m.C.partage = false;
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0', 'jeton-D-0']);
  assert.equal(bilan.ecartes.sans_partage, 1);
  assert.equal(bilan.lectures, 11, "1 + 1 Miff + 4 fiches + 2 destinataires x (session + appareil) (C n'est pas lu) + 1");
});

test('V3 : notify.updates = false -> pas de notification pour cet appareil (les autres sont notifiés)', async () => {
  const m = quatre();
  m.D.appareils = [{ notify: { updates: false } }];
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0', 'jeton-C-0']);
  assert.equal(bilan.ecartes.notification_coupee, 1);
});

test('V3b : notify absent, mal formé ou "updates" non booléen -> écarté (seul `true` strict notifie)', async () => {
  const m = quatre();
  m.B.appareils = [{ notify: null }];
  m.C.appareils = [{ notify: { updates: 'true' } }];
  m.D.appareils = [{ notify: { updates: 1 } }];
  await creerMiff(MIFF, m);
  const c = contexte();
  await publier(c);
  assert.deepEqual(c.fcm.messages, []);
});

test("V4 : un membre sans aucun appareil enregistré n'est pas notifié, les autres le sont", async () => {
  const m = quatre();
  m.B.appareils = [null];
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-C-0', 'jeton-D-0']);
  assert.equal(bilan.ecartes.sans_session, 1, 'B : ni session ni appareil');
});

test("I1 : plusieurs jetons chez un même compte -> seul l'appareil de la SESSION active est notifié", async () => {
  const m = quatre();
  m.B.appareils = [{ platform: 'ios' }, { platform: 'android' }, { platform: 'android' }, { platform: 'android' }]; // 4 documents, 3 lus au plus
  m.B.session = 'dev1';
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-1', 'jeton-C-0', 'jeton-D-0']);
  assert.equal(c.fcm.messages.length, 3, 'les autres documents devices de B ne sont ni lus ni utilisés');
  assert.equal(bilan.lectures, 1 + 1 + 4 + 3 * 2 + 1, 'pré-contrôle + Miff + 4 fiches + 3 destinataires x (session + get direct de l appareil) + état : plus de liste, quel que soit le nombre de jetons');
});

test("I1 : un seul document mais d'une AUTRE session (déconnexion échouée puis autre compte sur le même téléphone) -> aucun envoi ; pas de session -> aucun envoi", async () => {
  const m = quatre();
  m.B.session = 'dev-d-un-autre-appareil'; // B s'est reconnecté ailleurs : son ancien jeton n'est plus celui de la session
  m.C.session = null; // C : session supprimée (déconnexion), jeton resté faute de réseau
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-D-0']);
  assert.equal(bilan.ecartes.sans_appareil, 1, "B : la session pointe un document devices qui n'existe pas");
  assert.equal(bilan.ecartes.sans_session, 1);
});

test("I1 : le jeton T d'un ancien compte A reste dans devices/ ; B ouvre une session sur le même téléphone (autre uid, même jeton T) -> A (sans session) ne reçoit rien, B ne reçoit que ce qui concerne SES Miffs", async () => {
  // A est membre de la Miff de l'auteur X ; sa déconnexion hors connexion a laissé devices/devA (jeton T) mais supprimé sa session.
  await creerMiff(MIFF, { X: { prenom: 'Xavier' }, A: { appareils: [{ token: 'T' }], session: null } });
  // B (autre compte, autre cercle) est connecté sur le même téléphone : même jeton T, sa propre session.
  await creerMiff('MiffDeB', { Y: { prenom: 'Yann' }, B: { appareils: [{ token: 'T' }] } });
  const c = contexte();
  await publier(c, { auteurUid: 'X' });
  assert.deepEqual(c.fcm.messages, [], "le prénom de X ne part pas vers T : A n'a plus de session");
  await publier(c, { miffId: 'MiffDeB', auteurUid: 'Y' });
  assert.equal(c.fcm.messages.length, 1);
  assert.equal(c.fcm.messages[0].data.miffId, 'MiffDeB', 'T ne reçoit que la Miff du compte connecté');
});

test('jeton illisible (vide) ou plateforme inconnue : écartés sans erreur', async () => {
  const m = quatre();
  m.C.appareils = [{ token: '' }];
  m.D.appareils = [{ platform: 'windows' }];
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0']);
  assert.equal(bilan.ecartes.jeton_illisible, 2);
});

test("V8 : un ancien membre (fiche supprimée) n'est pas notifié, même s'il a encore un appareil enregistré", async () => {
  await creerMiff(MIFF, quatre());
  await db.doc(`miffs/${MIFF}/members/D`).delete();
  const c = contexte();
  await publier(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0', 'jeton-C-0']);
});

test("l'auteur doit lui-même partager ; l'auteur absent de la Miff ou seul dans la Miff : rien n'est envoyé", async () => {
  const m = quatre();
  m.A.partage = false;
  await creerMiff(MIFF, m);
  let c = contexte();
  let bilan = await publier(c);
  assert.equal(bilan.motif, 'auteur_sans_partage');
  assert.equal(bilan.lectures, 1 + 1 + 4);
  assert.deepEqual(c.fcm.messages, []);
  assert.equal(await existe(`notifState/${MIFF}`), false);

  c = contexte();
  bilan = await publier(c, { auteurUid: 'Z' });
  assert.equal(bilan.motif, 'auteur_absent');

  await creerMiff('Seul', { A: {} });
  c = contexte();
  bilan = await publier(c, { miffId: 'Seul' });
  assert.equal(bilan.motif, 'aucun_destinataire');
  assert.equal(bilan.lectures, 3, 'le pré-contrôle, la Miff et la liste des membres, rien d\'autre');
  assert.equal(bilan.ecritures, 0);
});

test('aucun appareil chez personne : rien ne consomme le plafond (aucune écriture)', async () => {
  const m = quatre();
  for (const k of ['B', 'C', 'D']) m[k].appareils = [null];
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.equal(bilan.motif, 'aucun_destinataire');
  assert.equal(await existe(`notifState/${MIFF}`), false);
});

// --- V5, V6 : plafond horaire et idempotence ---------------------------------------------------------------------------------------------

test("V5 : A publie à 14:00 puis 14:40 -> une seule notification (14:00) ; à 15:05 une nouvelle publication notifie ; l'état ne bouge pas pour 14:40", async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  assert.equal((await publier(c)).envoyes, 3);

  const quarante = plus(T_PUBLICATION, 40 * MIN);
  c.horloge.maintenant = plus(quarante, 5000);
  const avant = await etat();
  const b2 = await publier(c, { quand: quarante });
  assert.equal(b2.motif, 'plafond');
  assert.equal(b2.ecritures, 0, 'aucune écriture pour une update plafonnée');
  assert.equal(c.fcm.messages.length, 3, 'toujours les 3 messages de 14:00');
  assert.deepEqual(await etat(), avant);

  const quinzeCinq = plus(T_PUBLICATION, 65 * MIN);
  c.horloge.maintenant = plus(quinzeCinq, 5000);
  const b3 = await publier(c, { quand: quinzeCinq });
  assert.equal(b3.envoyes, 3);
  assert.equal(c.fcm.messages.length, 6);
});

test('plafond : la fenêtre est de 1 h pile (59 min 59 s plafonné, 60 min notifié) ; par auteur et par Miff', async () => {
  await creerMiff(MIFF, quatre());
  await creerMiff('Autre', { A: {}, B: {} });
  const c = contexte();
  await publier(c);

  let quand = plus(T_PUBLICATION, 60 * MIN - 1000);
  c.horloge.maintenant = plus(quand, 1000);
  assert.equal((await publier(c, { quand })).motif, 'plafond');

  // Un autre auteur de la même Miff : son propre plafond.
  quand = plus(T_PUBLICATION, 10 * MIN);
  c.horloge.maintenant = plus(quand, 1000);
  assert.equal((await publier(c, { auteurUid: 'B', quand })).envoyes, 3);

  // La même personne dans une autre Miff : son propre plafond.
  assert.equal((await publier(c, { miffId: 'Autre', auteurUid: 'A', quand })).envoyes, 1);

  // Exactement 1 h après la dernière notification envoyée : permis.
  quand = plus(T_PUBLICATION, 60 * MIN);
  c.horloge.maintenant = plus(quand, 1000);
  assert.equal((await publier(c, { quand })).envoyes, 3);
});

test('V6 : le même événement livré deux fois (et une publication plus ancienne arrivée après) ne notifie qu\'une fois', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  assert.equal((await publier(c)).envoyes, 3);
  const rejeu = await publier(c);
  assert.equal(rejeu.motif, 'deja_vu');
  assert.equal(rejeu.ecritures, 0);
  const ancien = await publier(c, { quand: plus(T_PUBLICATION, -1000) });
  assert.equal(ancien.motif, 'deja_vu');
  assert.equal(c.fcm.messages.length, 3);
});

test('V6b : deux exécutions simultanées du même événement -> un seul jeu de messages (la transaction départage)', async () => {
  await creerMiff(MIFF, quatre());
  const c1 = contexte();
  const c2 = contexte();
  c2.dep.fcm = c1.fcm; // même faux client : on compte le total
  const [b1, b2] = await Promise.all([publier(c1), publier(c2)]);
  assert.equal(c1.fcm.messages.length, 3, `3 messages au total, pas 6 (bilans : ${b1.motif ?? 'envoyé'} / ${b2.motif ?? 'envoyé'})`);
  assert.deepEqual([b1.motif, b2.motif].filter(Boolean).length, 1);
});

// --- V9 : jetons invalides, erreurs, reprises ----------------------------------------------------------------------------------------------

test('V9 : FCM répond « jeton inconnu » pour B -> son document est supprimé ; C et D sont notifiés', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte({ fcm: fauxFcm({ 'jeton-B-0': ['messaging/registration-token-not-registered'] }) });
  const bilan = await publier(c);
  assert.equal(bilan.purges, 1);
  assert.equal(bilan.envoyes, 2);
  assert.equal(await existe('users/B/devices/dev0'), false, 'document de B supprimé');
  assert.equal(await existe('users/C/devices/dev0'), true);
  assert.equal(await existe('users/D/devices/dev0'), true);
  assert.deepEqual(bilan.erreursFcm, { 1: 1 });
  assert.deepEqual(c.attentes, [], "un jeton inconnu n'est jamais retenté");
  assert.equal(bilan.ecritures, 2, "état + suppression d'un jeton");
});

test("jeton renouvelé entre la lecture et la réponse de FCM : le nouveau document n'est PAS supprimé (précondition)", async () => {
  await creerMiff(MIFF, quatre());
  const renouveler = async (token) => {
    if (token === 'jeton-B-0') await db.doc('users/B/devices/dev0').update({ token: 'jeton-B-nouveau' });
  };
  const c = contexte({ fcm: fauxFcm({ 'jeton-B-0': ['messaging/registration-token-not-registered'] }, renouveler) });
  const bilan = await publier(c);
  assert.equal(bilan.purges, 0);
  assert.equal((await db.doc('users/B/devices/dev0').get()).data().token, 'jeton-B-nouveau');
});

test('invalid-argument et third-party-auth-error (clé APNs absente) : ni nouvelle tentative, ni suppression de jeton', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte({ fcm: fauxFcm({ 'jeton-B-0': ['messaging/third-party-auth-error'], 'jeton-C-0': ['messaging/invalid-argument'] }) });
  const bilan = await publier(c);
  assert.equal(bilan.purges, 0);
  assert.equal(bilan.envoyes, 1);
  assert.deepEqual(bilan.erreursFcm, { 8: 1, 9: 1 });
  assert.deepEqual(c.attentes, []);
  assert.equal(c.fcm.messages.length, 3, 'un seul essai par appareil');
  for (const uid of ['B', 'C', 'D']) assert.equal(await existe(`users/${uid}/devices/dev0`), true);
});

test('F5 : erreur passagère -> au plus 2 nouvelles tentatives (délais croissants) puis succès ; 3 échecs -> abandon sans autre relance', async () => {
  await creerMiff(MIFF, quatre());
  let c = contexte({ fcm: fauxFcm({ 'jeton-C-0': ['messaging/server-unavailable', 'messaging/internal-error'] }) });
  let bilan = await publier(c);
  assert.equal(bilan.envoyes, 3, 'C est notifié à la 3e tentative');
  assert.deepEqual(c.attentes, [500, 1500]);
  assert.equal(c.fcm.messages.filter((m) => m.token === 'jeton-C-0').length, 3);

  await viderEmulateur();
  await creerMiff(MIFF, quatre());
  c = contexte({ fcm: fauxFcm({ 'jeton-C-0': ['messaging/server-unavailable', 'messaging/server-unavailable', 'messaging/server-unavailable', 'messaging/server-unavailable'] }) });
  bilan = await publier(c);
  assert.equal(c.fcm.messages.filter((m) => m.token === 'jeton-C-0').length, 3, '1 envoi + 2 nouvelles tentatives, pas une de plus');
  assert.equal(bilan.envoyes, 2);
  assert.deepEqual(bilan.erreursFcm, { 4: 1 });
  assert.equal(bilan.purges, 0, 'une panne passagère ne supprime jamais un jeton');
  // « Au plus une fois » : la réservation précède l'envoi ; un rejeu ne renvoie rien.
  assert.equal((await publier(c)).motif, 'deja_vu');
});

test("un échec d'envoi chez un appareil n'empêche pas les autres (tous les appareils sont tentés)", async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte({ fcm: fauxFcm({ 'jeton-B-0': ['messaging/invalid-argument'], 'jeton-C-0': ['messaging/invalid-argument'] }) });
  const bilan = await publier(c);
  assert.equal(bilan.envoyes, 1);
  assert.equal(c.fcm.messages.length, 3);
});

// --- V10 : nuit ------------------------------------------------------------------------------------------------------------------------

test('V10 : publication à 23:30 à Paris -> B (Paris) en mode calme, D (New York, 17:30) non', async () => {
  const m = quatre();
  m.B.appareils = [{ platform: 'ios', tz: 'Europe/Paris' }];
  m.C.appareils = [{ platform: 'android', tz: 'Europe/Paris' }];
  m.D.appareils = [{ platform: 'android', tz: 'America/New_York' }];
  await creerMiff(MIFF, m);
  const nuit = new Date('2026-12-10T22:30:00Z');
  const c = contexte({ maintenant: plus(nuit, 5000) });
  await publier(c, { quand: nuit });
  const par = Object.fromEntries(c.fcm.messages.map((x) => [x.token, x]));
  assert.equal(par['jeton-B-0'].apns.payload.aps['interruption-level'], 'passive');
  assert.equal(par['jeton-B-0'].apns.payload.aps.sound, undefined);
  assert.equal(par['jeton-C-0'].data.quiet, '1');
  assert.equal(par['jeton-D-0'].data.quiet, '0', 'New York : 17:30, de jour');
});

// --- V11, V12 : âge, grande Miff -------------------------------------------------------------------------------------------------------

test('V11 : un événement dont la photo a plus de 5 min au traitement n\'envoie rien (et ne lit rien) ; à 5 min pile : permis', async () => {
  await creerMiff(MIFF, quatre());
  let c = contexte({ maintenant: plus(T_PUBLICATION, 5 * MIN + 1) });
  let bilan = await publier(c);
  assert.equal(bilan.motif, 'trop_ancien');
  assert.equal(bilan.lectures, 0);
  assert.deepEqual(c.fcm.messages, []);
  c = contexte({ maintenant: plus(T_PUBLICATION, 5 * MIN) });
  bilan = await publier(c);
  assert.equal(bilan.envoyes, 3);
});

test('F3/V12 : Miff de 15 membres = 46 lectures et 1 écriture ; Miff de 40 membres = 30 fiches, 20 destinataires, 73 lectures (74 si l\'auteur est hors des 30 premières fiches)', async () => {
  const membres = (n) => Object.fromEntries(Array.from({ length: n }, (_, i) => [`u${String(i).padStart(2, '0')}`, {}]));
  await creerMiff('Quinze', membres(15));
  let c = contexte();
  let bilan = await publier(c, { miffId: 'Quinze', auteurUid: 'u00' });
  assert.equal(c.fcm.messages.length, 14);
  assert.equal(bilan.lectures, 1 + 1 + 15 + 14 + 14 + 1);
  assert.equal(bilan.ecritures, 1);

  await creerMiff('Quarante', membres(40));
  c = contexte();
  bilan = await publier(c, { miffId: 'Quarante', auteurUid: 'u00' });
  assert.equal(c.fcm.messages.length, 20, '20 destinataires au plus');
  assert.equal(bilan.ecartes.au_dela_limite, 9, '29 autres fiches lues, 20 retenues');
  assert.equal(bilan.lectures, 1 + 1 + 30 + 20 + 20 + 1);

  // Auteur hors des 30 premières fiches : une lecture de plus pour le retrouver, et la notification part quand même.
  c = contexte();
  bilan = await publier(c, { miffId: 'Quarante', auteurUid: 'u39', quand: plus(T_PUBLICATION, 1) });
  assert.equal(c.fcm.messages.length, 20);
  assert.equal(bilan.lectures, 1 + 1 + 30 + 1 + 20 + 20 + 1);
});

// --- V13 : deux Miffs en commun ------------------------------------------------------------------------------------------------------

test("V13 : A dans deux Miffs avec B, publie une fois -> deux messages, même identifiant de remplacement (auteur), Miffs distinctes", async () => {
  await creerMiff('M1', { A: {}, B: { appareils: [{ platform: 'ios' }] } });
  await creerMiff('M2', { A: {}, B: { appareils: [{ platform: 'ios' }] } });
  const c = contexte();
  await publier(c, { miffId: 'M1' });
  await publier(c, { miffId: 'M2' });
  assert.equal(c.fcm.messages.length, 2);
  const [x, y] = c.fcm.messages;
  assert.equal(x.apns.headers['apns-collapse-id'], y.apns.headers['apns-collapse-id']);
  assert.equal(x.apns.payload.aps.threadId, y.apns.payload.aps.threadId);
  assert.deepEqual([x.data.miffId, y.data.miffId].sort(), ['M1', 'M2']);
});

// --- Contenu strict ---------------------------------------------------------------------------------------------------------------------

test('contenu STRICT : prénom tronqué à 20 caractères ; ni nom de famille, ni nom de Miff, ni code, ni téléphone, ni avatar, ni photo, ni position dans aucun message', async () => {
  const m = quatre();
  m.A.prenom = 'Maximilienne-Alexandrine-Béatrice';
  await creerMiff(MIFF, m);
  await db.doc(`miffs/${MIFF}/status/A`).set({ presence: 'out', placeId: 'p1', updateText: 'Légende secrète', batteryPct: 12, lat: 48.85661, lng: 2.35222 });
  await db.doc(`miffs/${MIFF}/places/p1`).set({ name: 'Chez Mamie', address: '12 rue Secrète' });
  const c = contexte();
  await publier(c);
  assert.equal(c.fcm.messages.length, 3);
  const texte = JSON.stringify(c.fcm.messages);
  assert.ok(texte.includes('Maximilienne-Alexan…'), texte);
  assert.equal(Array.from('Maximilienne-Alexan…').length, 20);
  for (const interdit of ['Dupont', 'Famille', 'SECRETCODE', '+336', 'avatar', 'Légende', 'Chez Mamie', 'Secrète', 'jpeg', '48.85', '2.352', 'Béatrice', 'sharing', 'phone']) {
    assert.ok(!texte.includes(interdit), `« ${interdit} » ne doit jamais figurer dans un message : ${texte}`);
  }
  // Seules clés possibles dans `data`.
  for (const message of c.fcm.messages) assert.ok(Object.keys(message.data).every((k) => ['type', 'miffId', 'authorUid', 'firstName', 'quiet'].includes(k)));
});

test('prénom vide ou non affichable : rien n\'est envoyé (jamais un message cassé)', async () => {
  const m = quatre();
  m.A.prenom = ' \n\t ';
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.equal(bilan.motif, 'prenom_vide');
  assert.deepEqual(c.fcm.messages, []);
});

test('identifiants suspects (chemin, séparateur) : rejetés avant toute lecture', async () => {
  const c = contexte();
  for (const mauvais of ['a/b', '', '..', 'x'.repeat(129), 'é']) {
    assert.equal((await publier(c, { miffId: mauvais })).motif, 'identifiant_invalide');
    assert.equal((await publier(c, { auteurUid: mauvais })).motif, 'identifiant_invalide');
  }
  assert.equal((await traiterPhotoPubliee(c.dep, { miffId: MIFF, auteurUid: 'A', createdAt: undefined })).motif, 'date_absente');
  assert.equal((await traiterPhotoPubliee(c.dep, { miffId: MIFF, auteurUid: 'A', createdAt: 'hier' })).motif, 'date_absente');
});

// --- F4 : journaux ------------------------------------------------------------------------------------------------------------------------

test("F4 : les journaux d'une exécution complète (envoi, écarts, erreurs, purge) ne contiennent aucun identifiant, jeton, prénom, coordonnée", async () => {
  const m = {
    UidAuteurSecret1: { prenom: 'Zorglub' },
    UidDestinataireSecret2: { prenom: 'Prenom2', appareils: [{ token: 'JetonSecretAAA' }] },
    UidDestinataireSecret3: { prenom: 'Prenom3', appareils: [{ token: 'JetonSecretBBB', platform: 'ios' }] },
    UidDestinataireSecret4: { prenom: 'Prenom4', appareils: [{ token: 'JetonSecretCCC', notify: { updates: false } }] },
    UidDestinataireSecret5: { prenom: 'Prenom5', partage: false },
  };
  await creerMiff('MiffSecrete9', m);
  const lignes = [];
  const sortieOrig = process.stdout.write.bind(process.stdout);
  const erreurOrig = process.stderr.write.bind(process.stderr);
  const capter = (morceau) => {
    lignes.push(String(morceau));
    return true;
  };
  process.stdout.write = capter;
  process.stderr.write = capter;
  try {
    const c = contexte({ fcm: fauxFcm({ JetonSecretBBB: ['messaging/registration-token-not-registered'], JetonSecretAAA: ['messaging/server-unavailable', 'messaging/internal-error', 'messaging/server-unavailable'] }) });
    await traiterPhotoPubliee(c.dep, { miffId: 'MiffSecrete9', auteurUid: 'UidAuteurSecret1', createdAt: ts(T_PUBLICATION) });
    await traiterPhotoPubliee(c.dep, { miffId: 'MiffSecrete9', auteurUid: 'UidAuteurSecret1', createdAt: ts(T_PUBLICATION) }); // rejeu
  } finally {
    process.stdout.write = sortieOrig;
    process.stderr.write = erreurOrig;
  }
  const tout = lignes.join('');
  assert.ok(tout.includes('notif.update.bilan'), `le bilan doit être journalisé : ${tout}`);
  assert.ok(tout.includes('notif.update.ignore'), 'le rejeu aussi');
  for (const interdit of ['UidAuteurSecret1', 'UidDestinataire', 'MiffSecrete9', 'JetonSecret', 'Zorglub', 'Prenom', 'Dupont', 'Famille', 'lat', 'lng', 'photo', 'miffs/', 'users/']) {
    assert.ok(!tout.includes(interdit), `« ${interdit} » ne doit jamais apparaître dans les journaux : ${tout}`);
  }
});

// --- I2 : pré-contrôle (amplification de coût) ---------------------------------------------------------------------------------------

test("I2 : update plafonnée ou rejouée = 1 seule lecture (notifState) et 0 écriture : ni fiches, ni appareils, ni sessions ne sont lus", async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  const premiere = await publier(c);
  assert.equal(premiere.lectures, 13);

  const rejeu = await publier(c);
  assert.equal(rejeu.motif, 'deja_vu');
  assert.equal(rejeu.lectures, 1);
  assert.equal(rejeu.ecritures, 0);

  const quand = plus(T_PUBLICATION, 20 * MIN);
  c.horloge.maintenant = plus(quand, 1000);
  const plafonnee = await publier(c, { quand });
  assert.equal(plafonnee.motif, 'plafond');
  assert.equal(plafonnee.lectures, 1);
  assert.equal(plafonnee.ecritures, 0);
  assert.equal(c.fcm.messages.length, 3);
});

test("I2 : une Miff de 40 membres dont l'update est plafonnée coûte 1 lecture, pas 72", async () => {
  const membres = Object.fromEntries(Array.from({ length: 40 }, (_, i) => [`u${String(i).padStart(2, '0')}`, {}]));
  await creerMiff('Quarante', membres);
  const c = contexte();
  await publier(c, { miffId: 'Quarante', auteurUid: 'u00' });
  const quand = plus(T_PUBLICATION, 5 * MIN);
  c.horloge.maintenant = plus(quand, 1000);
  const bilan = await publier(c, { miffId: 'Quarante', auteurUid: 'u00', quand });
  assert.equal(bilan.motif, 'plafond');
  assert.equal(bilan.lectures, 1);
});

// --- M4 : exception non prévue ---------------------------------------------------------------------------------------------------------

test("M4 : une exception non prévue (Firestore en panne) ne remonte pas, et n'écrit dans les journaux qu'un code fixe : aucun message, chemin, identifiant ni pile", async () => {
  const dbCasse = {
    doc: () => ({
      get: async () => {
        throw new Error('Zorglub: panne sur users/UidSecretXYZ/devices (jeton JetonSecretQQQ)');
      },
    }),
    collection: () => {
      throw new Error('Zorglub collection');
    },
  };
  const lignes = [];
  const sortieOrig = process.stdout.write.bind(process.stdout);
  const erreurOrig = process.stderr.write.bind(process.stderr);
  const capter = (morceau) => {
    lignes.push(String(morceau));
    return true;
  };
  process.stdout.write = capter;
  process.stderr.write = capter;
  let bilan;
  try {
    bilan = await traiterPhotoPubliee({ db: dbCasse, fcm: fauxFcm(), maintenant: () => plus(T_PUBLICATION, 5000), attendre: async () => {} }, { miffId: 'MiffSecrete', auteurUid: 'UidSecretXYZ', createdAt: ts(T_PUBLICATION) });
  } finally {
    process.stdout.write = sortieOrig;
    process.stderr.write = erreurOrig;
  }
  assert.equal(bilan.erreur, true);
  const tout = lignes.join('');
  assert.ok(tout.includes('notif.update.erreur'), tout);
  assert.ok(tout.includes('"resultat":"erreur"'), tout);
  for (const interdit of ['Zorglub', 'UidSecretXYZ', 'JetonSecretQQQ', 'MiffSecrete', 'devices', 'at ', 'Error:', 'stack']) {
    assert.ok(!tout.includes(interdit), `« ${interdit} » ne doit pas apparaître dans les journaux : ${tout}`);
  }
});

// --- I-A : Miff supprimée --------------------------------------------------------------------------------------------------------------

test("I-A : la Miff n'existe plus (document supprimé, sous-collections restées) -> aucun envoi, motif miff_absente, 2 lectures, 0 écriture", async () => {
  await creerMiff(MIFF, quatre());
  await db.doc(`miffs/${MIFF}`).delete(); // suppression non récursive : fiches, sessions et appareils subsistent
  const c = contexte();
  const bilan = await publier(c);
  assert.equal(bilan.motif, 'miff_absente');
  assert.deepEqual(c.fcm.messages, []);
  assert.equal(bilan.lectures, 2, 'pré-contrôle + Miff : aucune fiche lue');
  assert.equal(bilan.ecritures, 0);
  assert.equal(await existe(`notifState/${MIFF}`), false);
});
