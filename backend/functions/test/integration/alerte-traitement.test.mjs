// AD-B2 : traitement d'une alerte « Je suis en danger », À L'ÉMULATEUR Firestore (lancé par `npm run test:functions` depuis backend/), avec un FAUX client FCM injecté.
// Aucun compte, aucun secret, aucun envoi réel. Critères du ticket docs/tickets/alerte-danger.md §15 : AD32 à AD39 (notifications), AD37 (idempotence et plafond silencieux).
//
// Ces tests appellent `traiterAlerte` directement (Firestore, FCM, horloge et attente injectés) et N'ÉCRIVENT JAMAIS dans miffs/{id}/alerts/ :
// l'émulateur de fonctions tourne en même temps et déclencherait la vraie fonction (voir alerte-declencheur.test.mjs, qui teste le déclencheur).
import { test, before, beforeEach, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { traiterAlerte } = require('../../lib/alerteTraitement.js');

const projet = process.env.GCLOUD_PROJECT;
const hoteFirestore = process.env.FIRESTORE_EMULATOR_HOST;
let db;

before(() => {
  assert.match(projet ?? '', /^demo-/, "ce test ne tourne que sur un projet de démonstration (lancer via 'npm run test:functions' depuis backend/)");
  assert.ok(hoteFirestore, 'FIRESTORE_EMULATOR_HOST absent : lancer via emulators:exec avec firestore ET functions');
  db = getFirestore(initializeApp({ projectId: projet }, 'alerte-traitement-tests'));
});

async function viderEmulateur() {
  const r = await fetch(`http://${hoteFirestore}/emulator/v1/projects/${projet}/databases/(default)/documents`, { method: 'DELETE' });
  assert.equal(r.status, 200, "vidage de l'émulateur Firestore");
}
beforeEach(viderEmulateur);
after(viderEmulateur);

// --- Aides --------------------------------------------------------------------------------------------------------------------------------

const MIFF = 'MiffAlerte1';
/** 03:00:00 à Paris (hiver) : en pleine heure calme d'un destinataire, ce qui ne doit rien changer à une alerte. */
const T_DEBUT = new Date('2026-12-10T02:00:00Z');
const ts = (date) => Timestamp.fromDate(date);
const plus = (date, ms) => new Date(date.getTime() + ms);
const MIN = 60_000;
const H = 3_600_000;

async function creerMiff(miffId, membres) {
  await db.doc(`miffs/${miffId}`).set({ ownerUid: Object.keys(membres)[0], name: 'Famille Dupont', joinCode: 'SECRETCODE12' });
  for (const [uid, m] of Object.entries(membres)) {
    await db.doc(`miffs/${miffId}/members/${uid}`).set({
      role: 'member',
      firstName: m.prenom ?? uid,
      lastName: 'Dupont',
      phone: '+33612345678',
      avatar: 'avatar-secret',
      sharing: { enabled: m.partage ?? true, consentedAt: ts(T_DEBUT), consentVersion: '2026-11-v3', revokedAt: null },
    });
    for (const [i, appareil] of (m.appareils ?? [{}]).entries()) {
      if (appareil === null) continue;
      await db.doc(`users/${uid}/devices/dev${i}`).set({
        token: appareil.token ?? `jeton-${uid}-${i}`,
        platform: appareil.platform ?? 'android',
        tz: appareil.tz ?? 'Europe/Paris',
        appVersion: '1.0.0 (12)',
        createdAt: ts(T_DEBUT),
        notify: 'notify' in appareil ? appareil.notify : { updates: true },
        updatedAt: appareil.updatedAt ?? ts(T_DEBUT),
      });
    }
    const aUnAppareil = (m.appareils ?? [{}]).some((a) => a !== null);
    const session = m.session === undefined ? (aUnAppareil ? 'dev0' : null) : m.session;
    if (session) {
      const s = typeof session === 'string' ? { deviceId: session, startedAt: ts(plus(T_DEBUT, -60 * MIN)) } : session;
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

function contexte({ fcm = fauxFcm(), maintenant = plus(T_DEBUT, 5000) } = {}) {
  const horloge = { maintenant };
  const attentes = [];
  return { horloge, attentes, fcm, dep: { db, fcm, maintenant: () => horloge.maintenant, attendre: async (ms) => void attentes.push(ms) } };
}

const debut = (c, { miffId = MIFF, auteurUid = 'A', quand = T_DEBUT } = {}) => traiterAlerte(c.dep, { miffId, auteurUid, phase: 'debut', startedAt: ts(quand) });
const fin = (c, { miffId = MIFF, auteurUid = 'A', debutDe = T_DEBUT, quand = plus(T_DEBUT, 2 * H) } = {}) =>
  traiterAlerte(c.dep, { miffId, auteurUid, phase: 'fin', startedAt: ts(debutDe), endedAt: ts(quand) });

const jetonsEnvoyes = (c) => c.fcm.messages.map((m) => m.token).sort();
const ETAT = (miffId = MIFF) => `notifState/${miffId}_alertes`;
const etat = async (miffId = MIFF) => (await db.doc(ETAT(miffId)).get()).data();
const existe = async (chemin) => (await db.doc(chemin).get()).exists;

const quatre = () => ({
  A: { prenom: 'Tomessa' },
  B: { prenom: 'Amandine', appareils: [{ platform: 'ios' }] },
  C: { prenom: 'Camille' },
  D: { prenom: 'Denis' },
});

// --- Début : qui reçoit, forme exacte, coût --------------------------------------------------------------------------------------------

test('AD32 : A déclare une alerte à 03:00 (heure calme à Paris) -> B, C, D notifiés, A non ; messages de forme exacte, priorité haute, Time Sensitive ; 13 lectures, 4 écritures (état + 3 lignes de journal)', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  const bilan = await debut(c);

  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0', 'jeton-C-0', 'jeton-D-0']);
  assert.equal(bilan.envoyes, 3);
  assert.equal(bilan.motif, undefined);
  assert.equal(bilan.lectures, 13, '1 pré-contrôle + 1 Miff + 4 fiches + 3 destinataires x (session + appareil) + 1 état (transaction)');
  assert.equal(bilan.ecritures, 4, "4 écritures : l'état de la fonction + 3 lignes de journal (une par destinataire, CNC-B)");
  assert.equal(bilan.journalisees, 3);

  const parJeton = Object.fromEntries(c.fcm.messages.map((m) => [m.token, m]));
  assert.deepEqual(parJeton['jeton-C-0'], {
    token: 'jeton-C-0',
    data: { type: 'circle_alert', event: 'start', miffId: MIFF, authorUid: 'A', firstName: 'Tomessa', entryId: `as-${MIFF}-A-${T_DEBUT.getTime()}` },
    android: { priority: 'high', ttl: 7_200_000 },
  });
  assert.deepEqual(parJeton['jeton-B-0'], {
    token: 'jeton-B-0',
    data: { type: 'circle_alert', event: 'start', miffId: MIFF, authorUid: 'A', entryId: `as-${MIFF}-A-${T_DEBUT.getTime()}` },
    apns: {
      headers: { 'apns-push-type': 'alert', 'apns-priority': '10', 'apns-expiration': String(Math.floor(plus(T_DEBUT, 5000 + 2 * H).getTime() / 1000)), 'apns-collapse-id': 'alert-A' },
      payload: {
        aps: {
          alert: { titleLocKey: 'notif_alert_start_title', titleLocArgs: ['Tomessa'], locKey: 'notif_alert_start_body', locArgs: ['Tomessa'] },
          threadId: `alert-${MIFF}`,
          sound: 'default',
          'interruption-level': 'time-sensitive',
        },
      },
    },
  });

  // L'état serveur ne contient que des horodatages, rien de personnel ; sa durée de vie couvre 7 jours d'alerte + 2 jours.
  const e = await etat();
  assert.deepEqual(Object.keys(e).sort(), ['alerts', 'expireAt']);
  assert.deepEqual(Object.keys(e.alerts), ['A']);
  assert.deepEqual(Object.keys(e.alerts.A).sort(), ['lastSeenStartedAt']);
  assert.ok(e.alerts.A.lastSeenStartedAt.isEqual(ts(T_DEBUT)));
  assert.ok(e.expireAt.isEqual(ts(plus(T_DEBUT, 5000 + 9 * 24 * H))), 'suppression automatique (TTL) 9 jours après la réservation du début');
  assert.equal(await existe(`notifState/${MIFF}`), false, "l'état des updates (notifState/{miffId}) n'est jamais touché");
});

test('D16 : un membre qui a ARRÊTÉ son partage de position est quand même prévenu (s\'il a un appareil) ; l\'auteur sans partage aussi peut être l\'origine ; la position n\'est jamais lue', async () => {
  const m = quatre();
  m.C.partage = false;
  m.A.partage = false;
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await debut(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0', 'jeton-C-0', 'jeton-D-0']);
  assert.equal(bilan.ecartes.sans_partage, undefined, "aucun destinataire n'est écarté faute de partage");
  assert.equal(bilan.lectures, 13);
});

test("§11.1 : l'interrupteur « Quand un proche donne des nouvelles » (notify.updates) n'agit PAS sur une alerte ; notify absent, mal formé : notifié aussi", async () => {
  const m = quatre();
  m.B.appareils = [{ notify: { updates: false } }];
  m.C.appareils = [{ notify: null }];
  m.D.appareils = [{ notify: { updates: 'non' } }];
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await debut(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0', 'jeton-C-0', 'jeton-D-0']);
  assert.equal(bilan.ecartes.notification_coupee, undefined);
});

test("AD38 : sans jeton (interrupteur général coupé ou appli désinstallée) ou sans session : aucun envoi pour cette personne ; membre retiré avant l'alerte (fiche supprimée) : rien", async () => {
  const m = quatre();
  m.B.appareils = [null]; // pas de jeton
  m.C.session = null; // pas de session
  await creerMiff(MIFF, m);
  await db.doc(`miffs/${MIFF}/members/D`).delete(); // D a été retiré : son jeton et sa session subsistent
  const c = contexte();
  const bilan = await debut(c);
  assert.deepEqual(c.fcm.messages, []);
  assert.equal(bilan.motif, 'aucun_destinataire');
  assert.equal(bilan.ecritures, 0, 'aucun destinataire : rien n\'est réservé, le plafond n\'est pas consommé');
  assert.equal(await existe(ETAT()), false);
});

test("I1 : plusieurs jetons chez un même compte -> seul l'appareil de la SESSION active est notifié ; un jeton d'une autre session n'est jamais utilisé", async () => {
  const m = quatre();
  m.C.appareils = [{}, { token: 'jeton-C-autre' }];
  m.C.session = 'dev1';
  m.D.appareils = [{}];
  m.D.session = 'devAutre'; // document inexistant
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await debut(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-B-0', 'jeton-C-autre']);
  assert.equal(bilan.ecartes.sans_appareil, 1);
});

test('jeton illisible (vide) ou plateforme inconnue : écartés sans erreur', async () => {
  const m = quatre();
  m.B.appareils = [{ token: '' }];
  m.C.appareils = [{ platform: 'windows' }];
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await debut(c);
  assert.deepEqual(jetonsEnvoyes(c), ['jeton-D-0']);
  assert.equal(bilan.ecartes.jeton_illisible, 2);
});

test('AD32 : l\'heure du fuseau du destinataire ne change RIEN (alerte à 03:00 à Paris ET à 21:00 à New York : même message prioritaire, aucun indicateur d\'heures calmes)', async () => {
  const m = quatre();
  m.B.appareils = [{ platform: 'ios', tz: 'Europe/Paris' }];
  m.C.appareils = [{ tz: 'America/New_York' }];
  m.D.appareils = [{ tz: 'Inconnu/Nulle-Part' }];
  await creerMiff(MIFF, m);
  const c = contexte();
  await debut(c);
  for (const message of c.fcm.messages) {
    assert.doesNotMatch(JSON.stringify(message), /quiet|passive|"apns-priority":"5"/, message.token);
  }
  assert.equal(c.fcm.messages.find((x) => x.token === 'jeton-B-0').apns.payload.aps['interruption-level'], 'time-sensitive');
});

// --- Portée : la Miff d'origine seulement ------------------------------------------------------------------------------------------------

test("AD47 : B est dans deux Miffs avec A ; l'alerte de A dans M1 ne notifie B QU'UNE fois (miffId de M1), n'écrit rien pour M2 et ne lit pas les membres de M2", async () => {
  await creerMiff('Miff1', { A: { prenom: 'Tomessa' }, B: { prenom: 'Amandine' } });
  await creerMiff('Miff2', { A: { prenom: 'Tomessa' }, B: { prenom: 'Amandine' }, E: { prenom: 'Eve' } });
  const c = contexte();
  await debut(c, { miffId: 'Miff1' });
  assert.equal(c.fcm.messages.length, 1);
  assert.equal(c.fcm.messages[0].data.miffId, 'Miff1');
  assert.equal(await existe(ETAT('Miff2')), false, "aucun état écrit pour l'autre Miff");
  assert.ok(await existe(ETAT('Miff1')));
  // E (seulement dans M2) ne reçoit rien.
  assert.ok(!jetonsEnvoyes(c).includes('jeton-E-0'));
});

// --- Idempotence et plafond silencieux --------------------------------------------------------------------------------------------------

test('AD37 : le même début livré deux fois (et un début plus ancien arrivé après) ne notifie qu\'une fois ; le rejeu coûte 1 lecture et 0 écriture', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  assert.equal((await debut(c)).envoyes, 3);
  const rejeu = await debut(c);
  assert.equal(rejeu.motif, 'deja_vu');
  assert.equal(rejeu.ecritures, 0);
  assert.equal(rejeu.lectures, 1, 'pré-contrôle seul : ni fiche, ni appareil, ni session lus');
  const ancien = await debut(c, { quand: plus(T_DEBUT, -1000) });
  assert.equal(ancien.motif, 'deja_vu');
  assert.equal(c.fcm.messages.length, 3);
});

test('AD37 : deux exécutions simultanées du même début -> un seul jeu de messages (la transaction départage)', async () => {
  await creerMiff(MIFF, quatre());
  const c1 = contexte();
  const c2 = contexte();
  c2.dep.fcm = c1.fcm;
  const [b1, b2] = await Promise.all([debut(c1), debut(c2)]);
  assert.equal(c1.fcm.messages.length, 3, `3 messages au total, pas 6 (bilans : ${b1.motif ?? 'envoyé'} / ${b2.motif ?? 'envoyé'})`);
  assert.equal([b1.motif, b2.motif].filter(Boolean).length, 1);
});

test('AD37 / D20 : 1 début notifié par minute au plus : un second début 30 s plus tard est ÉCARTÉ SANS ERREUR, un troisième à 61 s est notifié', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  assert.equal((await debut(c)).envoyes, 3);
  c.horloge.maintenant = plus(T_DEBUT, 35_000);
  const trop = await debut(c, { quand: plus(T_DEBUT, 30_000) });
  assert.equal(trop.motif, 'plafond');
  assert.equal(trop.erreur, undefined, 'aucune erreur : le plafond est silencieux');
  assert.equal(trop.ecritures, 0);
  assert.equal(c.fcm.messages.length, 3);
  c.horloge.maintenant = plus(T_DEBUT, 66_000);
  assert.equal((await debut(c, { quand: plus(T_DEBUT, 61_000) })).envoyes, 3);
  assert.equal(c.fcm.messages.length, 6);
});

test("D20 (décision du PM du 2026-11-18) : la fonction n'a PLUS de plafond journalier (le quota de 3 débuts par 24 h est dans les règles, qui refusent le 4e) : tous les débuts acceptés par les règles, espacés d'au moins 1 minute, sont notifiés ; l'état ne garde qu'un horodatage par auteur", async () => {
  await creerMiff(MIFF, quatre());
  await creerMiff('Miff2', quatre());
  const c = contexte();
  const quand = (i) => plus(T_DEBUT, i * 2 * MIN);
  for (let i = 0; i < 4; i++) {
    c.horloge.maintenant = plus(quand(i), 3000);
    assert.equal((await debut(c, { quand: quand(i) })).envoyes, 3, `début ${i + 1}`);
  }
  assert.equal(c.fcm.messages.length, 12);
  // Une autre personne de la même Miff et la même personne dans une autre Miff : indépendantes.
  c.horloge.maintenant = plus(quand(4), 4000);
  assert.equal((await debut(c, { auteurUid: 'B', quand: quand(4) })).envoyes, 3);
  assert.equal((await debut(c, { miffId: 'Miff2', quand: quand(4) })).envoyes, 3);
  assert.deepEqual(Object.keys((await etat()).alerts.A), ['lastSeenStartedAt']);
  // Le 1 par minute reste une garde silencieuse de la fonction.
  c.horloge.maintenant = plus(quand(4), 20_000);
  // Le dernier début réservé de A est quand(3) ; un début à +30 s est trop rapproché.
  const rapide = await debut(c, { quand: plus(quand(3), 30_000) });
  assert.equal(rapide.motif, 'plafond', 'à moins de 60 s du dernier début réservé : écarté sans erreur');
  assert.equal(rapide.erreur, undefined);
});

// --- Fin manuelle ---------------------------------------------------------------------------------------------------------------------

test('AD36 : fin manuelle APRÈS un début notifié -> « a mis fin à son alerte » (clés de fin) à tous les destinataires ; un rejeu de la fin ne renvoie rien', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  await debut(c);
  c.horloge.maintenant = plus(T_DEBUT, 2 * H + 5000);
  const bilan = await fin(c);
  assert.equal(bilan.envoyes, 3);
  assert.equal(bilan.motif, undefined);
  assert.equal(bilan.dureeAlerteMin, 120, "durée de l'alerte en minutes (signal « fausses alertes » : nombre seul, aucun identifiant)");
  const message = c.fcm.messages.filter((m) => m.data.event === 'end');
  assert.equal(message.length, 3);
  assert.deepEqual(message.find((m) => m.token === 'jeton-C-0'), {
    token: 'jeton-C-0',
    data: { type: 'circle_alert', event: 'end', miffId: MIFF, authorUid: 'A', firstName: 'Tomessa', entryId: `ae-${MIFF}-A-${T_DEBUT.getTime()}` },
    android: { priority: 'high', ttl: 7_200_000 },
  });
  const ios = message.find((m) => m.token === 'jeton-B-0');
  assert.equal(ios.apns.payload.aps.alert.titleLocKey, 'notif_alert_end_title');
  assert.equal(ios.apns.payload.aps.alert.locKey, 'notif_alert_end_body');
  assert.equal(ios.apns.headers['apns-collapse-id'], 'alert-A', 'la fin remplace la notification de début du même auteur');
  const rejeu = await fin(c);
  assert.equal(rejeu.motif, 'deja_vu');
  assert.equal(c.fcm.messages.length, 6);
  // La fin ne rallonge pas la durée de vie de l'état.
  const e = await etat();
  assert.ok(e.expireAt.isEqual(ts(plus(T_DEBUT, 5000 + 9 * 24 * H))));
  assert.ok(e.alerts.A.lastEndedStartedAt.isEqual(ts(T_DEBUT)));
});

test("AD36 : une fin sans début notifié (plafond silencieux, état absent, autre alerte) n'envoie RIEN : « a mis fin » n'a pas de sens sans début ; une fin pour un AUTRE début non plus", async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte({ maintenant: plus(T_DEBUT, 2 * H) });
  const sansEtat = await fin(c);
  assert.equal(sansEtat.motif, 'debut_non_notifie');
  assert.equal(sansEtat.ecritures, 0);
  assert.equal(sansEtat.lectures, 1);
  // Un début notifié, puis la fin d'un autre début (startedAt différent) : ignorée.
  c.horloge.maintenant = plus(T_DEBUT, 5000);
  await debut(c);
  c.horloge.maintenant = plus(T_DEBUT, 2 * H);
  const autre = await fin(c, { debutDe: plus(T_DEBUT, -H) });
  assert.equal(autre.motif, 'debut_non_notifie');
  assert.equal(c.fcm.messages.length, 3, 'seul le début a été envoyé');
});

test("D20 : un début ÉCARTÉ par le plafond n'a pas de notification de fin (l'alerte reste visible dans le Feed, sans push, ni au début ni à la fin)", async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte({ maintenant: plus(T_DEBUT, 5000) });
  await debut(c);
  c.horloge.maintenant = plus(T_DEBUT, 40_000);
  const rapide = plus(T_DEBUT, 30_000);
  assert.equal((await debut(c, { quand: rapide })).motif, 'plafond');
  c.horloge.maintenant = plus(T_DEBUT, 3 * MIN);
  const bilan = await fin(c, { debutDe: rapide, quand: plus(T_DEBUT, 2 * MIN) });
  assert.equal(bilan.motif, 'debut_non_notifie');
  assert.equal(c.fcm.messages.length, 3);
});

test("AD36 : une alerte longue (6 jours) : la fin retrouve son début ; l'état d'une alerte de 7 jours n'a pas expiré (9 jours de vie)", async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte({ maintenant: plus(T_DEBUT, 5000) });
  await debut(c);
  c.horloge.maintenant = plus(T_DEBUT, 6 * 24 * H + 5000);
  const bilan = await fin(c, { quand: plus(T_DEBUT, 6 * 24 * H) });
  assert.equal(bilan.envoyes, 3);
});

// --- Anciennetés -----------------------------------------------------------------------------------------------------------------------

test("V11 : un début ou une fin de plus de 5 min au traitement n'envoie rien (et ne lit rien) ; à 5 min pile : permis", async () => {
  await creerMiff(MIFF, quatre());
  let c = contexte({ maintenant: plus(T_DEBUT, 5 * MIN + 1) });
  const vieux = await debut(c);
  assert.equal(vieux.motif, 'trop_ancien');
  assert.equal(vieux.lectures, 0);
  assert.equal(vieux.ecritures, 0);
  c = contexte({ maintenant: plus(T_DEBUT, 5 * MIN) });
  assert.equal((await debut(c)).envoyes, 3);
  const finTardive = contexte({ maintenant: plus(T_DEBUT, 3 * H + 5 * MIN + 1) });
  assert.equal((await fin(finTardive, { quand: plus(T_DEBUT, 3 * H) })).motif, 'trop_ancien');
});

// --- Jetons invalides, erreurs, reprises --------------------------------------------------------------------------------------------------

test('F4/V9 : FCM répond « jeton inconnu » pour B -> son document est supprimé (nettoyage) ; C et D sont notifiés ; jamais retenté', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte({ fcm: fauxFcm({ 'jeton-B-0': ['messaging/registration-token-not-registered'] }) });
  const bilan = await debut(c);
  assert.equal(bilan.purges, 1);
  assert.equal(bilan.envoyes, 2);
  assert.equal(await existe('users/B/devices/dev0'), false, 'document de B supprimé');
  assert.equal(await existe('users/C/devices/dev0'), true);
  assert.deepEqual(bilan.erreursFcm, { 1: 1 });
  assert.deepEqual(c.attentes, []);
  assert.equal(bilan.ecritures, 5, "état + 3 lignes de journal (le journal garde la ligne de B : le téléphone la relira) + suppression d'un jeton");
});

test("jeton renouvelé entre la lecture et la réponse de FCM : le nouveau document n'est PAS supprimé ; invalid-argument ne purge jamais ; erreur passagère -> 2 nouvelles tentatives au plus", async () => {
  await creerMiff(MIFF, quatre());
  const renouveler = async (token) => {
    if (token === 'jeton-B-0') await db.doc('users/B/devices/dev0').update({ token: 'jeton-B-nouveau' });
  };
  let c = contexte({ fcm: fauxFcm({ 'jeton-B-0': ['messaging/registration-token-not-registered'], 'jeton-C-0': ['messaging/invalid-argument'] }, renouveler) });
  const bilan = await debut(c);
  assert.equal(bilan.purges, 0);
  assert.equal((await db.doc('users/B/devices/dev0').get()).data().token, 'jeton-B-nouveau');
  assert.equal(await existe('users/C/devices/dev0'), true, 'invalid-argument ne supprime jamais un jeton');

  await viderEmulateur();
  await creerMiff(MIFF, quatre());
  c = contexte({ fcm: fauxFcm({ 'jeton-C-0': ['messaging/server-unavailable', 'messaging/internal-error'] }) });
  const reprise = await debut(c);
  assert.equal(reprise.envoyes, 3);
  assert.deepEqual(c.attentes, [500, 1500]);
  assert.equal(c.fcm.messages.filter((m) => m.token === 'jeton-C-0').length, 3);
});

// --- Garde-fous de l'événement ---------------------------------------------------------------------------------------------------------

test('identifiants suspects (chemin, séparateur) : rejetés avant toute lecture ; date absente : rien', async () => {
  const c = contexte();
  for (const [miffId, auteurUid] of [['a/b', 'A'], ['..', 'A'], ['M', 'a/b'], ['', 'A'], ['M', ''], ['M'.repeat(129), 'A']]) {
    const bilan = await debut(c, { miffId, auteurUid });
    assert.equal(bilan.motif, 'identifiant_invalide', `${miffId} / ${auteurUid}`);
    assert.equal(bilan.lectures, 0);
  }
  for (const startedAt of [undefined, null, 'hier', 42, {}]) {
    const bilan = await traiterAlerte(c.dep, { miffId: MIFF, auteurUid: 'A', phase: 'debut', startedAt });
    assert.equal(bilan.motif, 'date_absente');
  }
  const sansFin = await traiterAlerte(c.dep, { miffId: MIFF, auteurUid: 'A', phase: 'fin', startedAt: ts(T_DEBUT) });
  assert.equal(sansFin.motif, 'date_absente');
  assert.deepEqual(c.fcm.messages, []);
});

test("la Miff n'existe plus (sous-collections restées) -> aucun envoi, miff_absente, 2 lectures, 0 écriture ; auteur absent ou prénom vide : rien", async () => {
  await creerMiff(MIFF, quatre());
  await db.doc(`miffs/${MIFF}`).delete();
  let c = contexte();
  let bilan = await debut(c);
  assert.equal(bilan.motif, 'miff_absente');
  assert.equal(bilan.lectures, 2);
  assert.equal(bilan.ecritures, 0);
  await viderEmulateur();
  const m = quatre();
  delete m.A;
  await creerMiff(MIFF, m);
  c = contexte();
  bilan = await debut(c);
  assert.equal(bilan.motif, 'auteur_absent');
  await viderEmulateur();
  const v = quatre();
  v.A.prenom = '   ';
  await creerMiff(MIFF, v);
  c = contexte();
  bilan = await debut(c);
  assert.equal(bilan.motif, 'prenom_vide');
  assert.deepEqual(c.fcm.messages, []);
});

test("AD33 : contenu STRICT : prénom tronqué à 20 caractères ; ni nom de famille, ni nom de Miff, ni code, ni téléphone, ni avatar, ni position dans aucun message", async () => {
  const m = quatre();
  m.A.prenom = 'Prenomtreslongpourunenotification';
  await creerMiff(MIFF, m);
  const c = contexte();
  await debut(c);
  for (const message of c.fcm.messages) {
    const texte = JSON.stringify(message);
    for (const interdit of ['Dupont', 'Famille', 'SECRETCODE12', '+33612345678', 'avatar-secret', 'photo', 'lat', 'lng', 'position', 'location', '2026-11-v3']) {
      assert.ok(!texte.includes(interdit), `« ${interdit} » ne doit apparaître dans aucun message : ${texte}`);
    }
  }
  const android = c.fcm.messages.find((x) => x.token === 'jeton-C-0');
  assert.equal(Array.from(android.data.firstName).length, 20);
  assert.ok(android.data.firstName.endsWith('…'));
});

// --- Coût ------------------------------------------------------------------------------------------------------------------------------

test('AD-coût : Miff de 15 membres = 46 lectures et 15 écritures (état + 14 lignes de journal) ; Miff de 40 membres = 40 fiches, 39 destinataires, 121 lectures (122 si l\'auteur est hors des 40 premières fiches), 40 écritures', async () => {
  const membres = (n) => Object.fromEntries(Array.from({ length: n }, (_, i) => [`M${String(i).padStart(2, '0')}`, { prenom: `Prenom${i}` }]));
  await creerMiff('Miff15', membres(15));
  let c = contexte();
  let bilan = await debut(c, { miffId: 'Miff15', auteurUid: 'M00' });
  assert.equal(bilan.lectures, 1 + 1 + 15 + 14 * 2 + 1, '46');
  assert.equal(bilan.ecritures, 1 + 14);
  assert.equal(c.fcm.messages.length, 14);

  await viderEmulateur();
  await creerMiff('Miff40', membres(40));
  c = contexte();
  bilan = await debut(c, { miffId: 'Miff40', auteurUid: 'M00' });
  assert.equal(bilan.lectures, 1 + 1 + 40 + 39 * 2 + 1, '121');
  assert.equal(bilan.envoyes, 39);
  assert.equal(bilan.ecritures, 1 + 39, 'une seule transaction de 40 écritures, très loin de la limite de 500');

  await viderEmulateur();
  await creerMiff('Miff45', membres(45));
  c = contexte();
  bilan = await debut(c, { miffId: 'Miff45', auteurUid: 'M44' });
  assert.equal(bilan.lectures, 1 + 1 + 40 + 1 + 39 * 2 + 1, '122 : lecture bornée, auteur lu à part (hors des 40 premières fiches)');
  assert.equal(c.fcm.messages.length, 39, 'jamais plus de 39 destinataires');
  assert.equal(bilan.ecartes.au_dela_limite, 1, 'un destinataire lu au-delà de la limite est compté, pas notifié ; les membres au-delà des 40 fiches ne sont pas lus (limite assumée pour une Miff hors norme)');
});

// --- Journaux --------------------------------------------------------------------------------------------------------------------------

test("AD39 : les journaux d'une exécution complète (début, fin, rejeu, plafond, écarts, erreurs, purge) ne contiennent aucun identifiant, jeton, prénom, coordonnée", async () => {
  const m = {
    UidAuteurSecret1: { prenom: 'Zorglub' },
    UidDestinataireSecret2: { prenom: 'Prenom2', appareils: [{ token: 'JetonSecretAAA' }] },
    UidDestinataireSecret3: { prenom: 'Prenom3', appareils: [{ token: 'JetonSecretBBB', platform: 'ios' }] },
    UidDestinataireSecret4: { prenom: 'Prenom4', appareils: [null] },
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
    const base = { miffId: 'MiffSecrete9', auteurUid: 'UidAuteurSecret1' };
    await traiterAlerte(c.dep, { ...base, phase: 'debut', startedAt: ts(T_DEBUT) });
    await traiterAlerte(c.dep, { ...base, phase: 'debut', startedAt: ts(T_DEBUT) }); // rejeu
    c.horloge.maintenant = plus(T_DEBUT, 35_000);
    await traiterAlerte(c.dep, { ...base, phase: 'debut', startedAt: ts(plus(T_DEBUT, 30_000)) }); // plafond
    c.horloge.maintenant = plus(T_DEBUT, 2 * H);
    await traiterAlerte(c.dep, { ...base, phase: 'fin', startedAt: ts(T_DEBUT), endedAt: ts(plus(T_DEBUT, 2 * H - 1000)) });
  } finally {
    process.stdout.write = sortieOrig;
    process.stderr.write = erreurOrig;
  }
  const tout = lignes.join('');
  assert.ok(tout.includes('notif.alerte.debut.bilan'), `le bilan du début doit être journalisé : ${tout}`);
  assert.ok(tout.includes('notif.alerte.fin.bilan'), 'le bilan de la fin aussi');
  assert.ok(tout.includes('notif.alerte.fin.duree'), "la durée de l'alerte (en minutes) est journalisée à la fin");
  assert.ok(tout.includes('notif.alerte.debut.ignore'), 'le rejeu et le plafond aussi');
  assert.ok(tout.includes('"motif":"plafond"') && tout.includes('"motif":"deja_vu"'), tout);
  for (const interdit of ['UidAuteurSecret1', 'UidDestinataire', 'MiffSecrete9', 'JetonSecret', 'Zorglub', 'Prenom', 'Dupont', 'Famille', 'lat', 'lng', 'photo', 'miffs/', 'users/']) {
    assert.ok(!tout.includes(interdit), `« ${interdit} » ne doit jamais apparaître dans les journaux : ${tout}`);
  }
});

test("M4 : une exception non prévue (Firestore en panne) ne remonte pas, et n'écrit dans les journaux qu'un code fixe : aucun message, chemin, identifiant ni pile", async () => {
  const dbCasse = {
    doc: () => ({ get: async () => { throw new Error('Zorglub: panne sur users/UidSecretXYZ/devices (jeton JetonSecretQQQ)'); } }),
    collection: () => { throw new Error('Zorglub collection'); },
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
    bilan = await traiterAlerte({ db: dbCasse, fcm: fauxFcm(), maintenant: () => plus(T_DEBUT, 5000), attendre: async () => {} }, { miffId: 'MiffSecrete', auteurUid: 'UidSecretXYZ', phase: 'debut', startedAt: ts(T_DEBUT) });
  } finally {
    process.stdout.write = sortieOrig;
    process.stderr.write = erreurOrig;
  }
  assert.equal(bilan.erreur, true);
  const tout = lignes.join('');
  assert.ok(tout.includes('notif.alerte.debut.erreur'), tout);
  for (const interdit of ['Zorglub', 'UidSecretXYZ', 'JetonSecretQQQ', 'MiffSecrete', 'devices', 'at ', 'Error:', 'stack']) {
    assert.ok(!tout.includes(interdit), `« ${interdit} » ne doit pas apparaître dans les journaux : ${tout}`);
  }
});
