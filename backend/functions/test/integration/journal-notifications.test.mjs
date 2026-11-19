// CNC-B : JOURNAL de notifications users/{uid}/notifications/{entryId} écrit par notifierUpdate et notifierAlerte DANS leur transaction de réservation,
// À L'ÉMULATEUR Firestore (lancé par `npm run test:functions` depuis backend/), avec un FAUX client FCM injecté. Aucun compte, aucun secret, aucun envoi réel.
// Critères du ticket docs/tickets/centre-notifications-complet.md §10 : CNC-5 (identifiant partagé avec le message), CNC-6 (mêmes destinataires que l'envoi),
// CNC-13 (forme exacte), CNC-22 (atomicité avec la réservation, idempotence), CNC-9 quinquies (aucune rétroactivité : rien n'est écrit pour le passé).
//
// Ces tests appellent `traiterPhotoPubliee` et `traiterAlerte` directement et N'ÉCRIVENT JAMAIS dans photos/ ni alerts/ : l'émulateur de fonctions tourne en même temps
// et déclencherait les vraies fonctions.
import { test, before, beforeEach, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { traiterPhotoPubliee } = require('../../lib/notifTraitement.js');
const { traiterAlerte } = require('../../lib/alerteTraitement.js');

const projet = process.env.GCLOUD_PROJECT;
const hoteFirestore = process.env.FIRESTORE_EMULATOR_HOST;
let db;

before(() => {
  assert.match(projet ?? '', /^demo-/, "ce test ne tourne que sur un projet de démonstration (lancer via 'npm run test:functions' depuis backend/)");
  assert.ok(hoteFirestore, 'FIRESTORE_EMULATOR_HOST absent : lancer via emulators:exec avec firestore ET functions');
  db = getFirestore(initializeApp({ projectId: projet }, 'journal-notifications-tests'));
});

async function viderEmulateur() {
  const r = await fetch(`http://${hoteFirestore}/emulator/v1/projects/${projet}/databases/(default)/documents`, { method: 'DELETE' });
  assert.equal(r.status, 200, "vidage de l'émulateur Firestore");
}
beforeEach(viderEmulateur);
after(viderEmulateur);

// --- Aides --------------------------------------------------------------------------------------------------------------------------------

const MIFF = 'MiffJournal1';
const T0 = new Date('2026-12-10T13:00:00Z');
const MIN = 60_000;
const H = 3_600_000;
const JOUR = 24 * H;
const DAYS = (n) => n * JOUR;
const ts = (date) => Timestamp.fromDate(date);
const plus = (date, ms) => new Date(date.getTime() + ms);

async function creerMiff(miffId, membres) {
  await db.doc(`miffs/${miffId}`).set({ ownerUid: Object.keys(membres)[0], name: 'Famille Dupont', joinCode: 'SECRETCODE12' });
  for (const [uid, m] of Object.entries(membres)) {
    await db.doc(`miffs/${miffId}/members/${uid}`).set({
      role: 'member',
      firstName: m.prenom ?? uid,
      lastName: 'Dupont',
      phone: '+33612345678',
      avatar: 'avatar-secret',
      sharing: { enabled: m.partage ?? true, consentedAt: ts(T0), consentVersion: '2026-11-v3', revokedAt: null },
    });
    for (const [i, appareil] of (m.appareils ?? [{}]).entries()) {
      if (appareil === null) continue;
      await db.doc(`users/${uid}/devices/dev${i}`).set({
        token: appareil.token ?? `jeton-${uid}-${i}`,
        platform: appareil.platform ?? 'android',
        tz: 'Europe/Paris',
        appVersion: '1.0.0 (12)',
        createdAt: ts(T0),
        notify: 'notify' in appareil ? appareil.notify : { updates: true },
        updatedAt: ts(T0),
      });
    }
    const aUnAppareil = (m.appareils ?? [{}]).some((a) => a !== null);
    const session = m.session === undefined ? (aUnAppareil ? 'dev0' : null) : m.session;
    if (session) await db.doc(`users/${uid}/session/active`).set({ deviceId: session, startedAt: ts(plus(T0, -H)) });
  }
}

function fauxFcm(erreurPourTous = null) {
  const messages = [];
  return {
    messages,
    async envoyer(message) {
      messages.push(structuredClone(message));
      if (erreurPourTous) throw Object.assign(new Error('erreur simulée'), { code: erreurPourTous });
    },
  };
}

function contexte({ fcm = fauxFcm(), maintenant = plus(T0, 5000), base = db } = {}) {
  const horloge = { maintenant };
  return { horloge, fcm, dep: { db: base, fcm, maintenant: () => horloge.maintenant, attendre: async () => {} } };
}

const publier = (c, { miffId = MIFF, auteurUid = 'A', quand = T0 } = {}) => traiterPhotoPubliee(c.dep, { miffId, auteurUid, createdAt: ts(quand) });
const debut = (c, { miffId = MIFF, auteurUid = 'A', quand = T0 } = {}) => traiterAlerte(c.dep, { miffId, auteurUid, phase: 'debut', startedAt: ts(quand) });
const fin = (c, { miffId = MIFF, auteurUid = 'A', debutDe = T0, quand = plus(T0, 2 * H) } = {}) =>
  traiterAlerte(c.dep, { miffId, auteurUid, phase: 'fin', startedAt: ts(debutDe), endedAt: ts(quand) });

/** Toutes les lignes de journal de la base : { 'users/B/notifications/<id>': données }. */
async function journal() {
  const instantane = await db.collectionGroup('notifications').get();
  return Object.fromEntries(instantane.docs.map((d) => [d.ref.path, d.data()]));
}
const destinatairesDuJournal = async () => Object.keys(await journal()).map((p) => p.split('/')[1]).sort();
const jetons = (c) => c.fcm.messages.map((m) => m.token).sort();
const etatExiste = async (chemin) => (await db.doc(chemin).get()).exists;

const quatre = () => ({
  A: { prenom: 'Tomessa' },
  B: { prenom: 'Amandine', appareils: [{ platform: 'ios' }] },
  C: { prenom: 'Camille' },
  D: { prenom: 'Denis' },
});

// --- Update : forme exacte, identifiant partagé avec le message ----------------------------------------------------------------------------

test('CNC-13 / CNC-5 : une update de A écrit UNE ligne par destinataire (B, C, D ; jamais A), de forme exacte (6 clés), dont l\'identifiant est celui du message FCM', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  const bilan = await publier(c);
  const id = `u-${MIFF}-A-${T0.getTime()}`;

  assert.equal(bilan.journalisees, 3);
  assert.deepEqual(await destinatairesDuJournal(), ['B', 'C', 'D']);
  const lignes = await journal();
  for (const uid of ['B', 'C', 'D']) {
    const l = lignes[`users/${uid}/notifications/${id}`];
    assert.ok(l, `ligne de ${uid}`);
    assert.deepEqual(Object.keys(l).sort(), ['actorUid', 'createdAt', 'expireAt', 'kind', 'miffId', 'v']);
    assert.equal(l.v, 1);
    assert.equal(l.kind, 'update');
    assert.equal(l.miffId, MIFF);
    assert.equal(l.actorUid, 'A');
    assert.ok(l.createdAt.isEqual(ts(plus(T0, 5000))), "createdAt = heure injectée dans la fonction (sert de curseur de synchronisation)");
    assert.ok(l.expireAt.isEqual(ts(plus(T0, 5000 + 30 * JOUR))), 'expireAt = createdAt + 30 jours exactement (règle TTL)');
  }
  // Même identifiant dans CHAQUE message envoyé (Android et iOS) : le téléphone qui reçoit le push et celui qui relit le journal retrouvent la même clé.
  assert.equal(c.fcm.messages.length, 3);
  for (const m of c.fcm.messages) assert.equal(m.data.entryId, id, m.token);
});

test('CNC-13 : aucun prénom, photo, lieu, position, nom de Miff, nom de famille, téléphone ni jeton dans le journal (contenu complet relu)', async () => {
  await creerMiff(MIFF, quatre());
  await db.doc(`miffs/${MIFF}/status/A`).set({ presence: 'out', updateText: 'Légende secrète', lat: 48.85661, lng: 2.35222 });
  const c = contexte();
  await publier(c);
  await debut(contexte({ maintenant: plus(T0, 2 * H) }), { quand: plus(T0, 2 * H - 5000) });
  const texte = JSON.stringify(await journal());
  assert.ok(texte.length > 100);
  for (const interdit of ['Tomessa', 'Amandine', 'Camille', 'Denis', 'Dupont', 'Famille', 'SECRETCODE', '+336', 'avatar', 'Légende', '48.85', '2.352', 'jeton-', 'sharing', 'phone', 'firstName', 'lastName']) {
    assert.ok(!texte.includes(interdit), `« ${interdit} » ne doit jamais figurer dans le journal`);
  }
});

test('CNC-6 : seuls les destinataires dont un push PART ont une ligne : interrupteur « nouvelles » coupé, sans partage, sans session, sans appareil, jeton illisible : aucune ligne', async () => {
  const m = {
    A: { prenom: 'Tomessa' },
    B: { appareils: [{ notify: { updates: false } }] }, // interrupteur coupé
    C: { partage: false }, // sans partage de position
    D: { session: null }, // sans session
    E: { appareils: [null] }, // sans appareil
    F: { appareils: [{ token: '' }] }, // jeton illisible
    G: {}, // le seul destinataire valide
  };
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await publier(c);
  assert.deepEqual(jetons(c), ['jeton-G-0']);
  assert.deepEqual(await destinatairesDuJournal(), ['G']);
  assert.equal(bilan.journalisees, 1);
  assert.equal(bilan.ecritures, 2, "état + 1 ligne");
});

test('CNC-6 : un destinataire qui n\'a PLUS de partage est écarté, un membre d\'une AUTRE Miff n\'a jamais de ligne ; l\'auteur n\'en a jamais', async () => {
  await creerMiff(MIFF, { A: { prenom: 'Tomessa' }, B: {} });
  await creerMiff('AutreMiff', { A: { prenom: 'Tomessa' }, Z: {} });
  const c = contexte();
  await publier(c);
  assert.deepEqual(await destinatairesDuJournal(), ['B']);
});

test('aucun destinataire joignable : ni push, ni réservation, ni ligne', async () => {
  await creerMiff(MIFF, { A: { prenom: 'Tomessa' }, B: { appareils: [null] } });
  const c = contexte();
  const bilan = await publier(c);
  assert.equal(bilan.motif, 'aucun_destinataire');
  assert.deepEqual(await journal(), {});
  assert.equal(bilan.ecritures, 0);
});

// --- Update : idempotence, plafond, atomicité -----------------------------------------------------------------------------------------------

test('CNC-22 : événement rejoué = aucune ligne de plus (ni identifiant nouveau, ni écriture) ; 2e publication de l\'heure (plafond) = ni push ni ligne', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  await publier(c);
  const avant = await journal();
  assert.equal(Object.keys(avant).length, 3);

  const rejeu = await publier(c);
  assert.equal(rejeu.motif, 'deja_vu');
  assert.equal(rejeu.ecritures, 0);
  assert.deepEqual(await journal(), avant, 'le journal n\'a pas bougé (mêmes lignes, mêmes heures)');

  c.horloge.maintenant = plus(T0, 27 * MIN);
  const plafonnee = await publier(c, { quand: plus(T0, 25 * MIN) });
  assert.equal(plafonnee.motif, 'plafond');
  assert.equal(Object.keys(await journal()).length, 3, 'une publication plafonnée n\'écrit AUCUNE ligne (conçu ainsi : elle reste dans le Feed)');
  assert.equal(c.fcm.messages.length, 3);

  // Au-delà de l'heure : nouvelle ligne, nouvel identifiant (un identifiant par publication notifiée).
  c.horloge.maintenant = plus(T0, 65 * MIN + 5000);
  await publier(c, { quand: plus(T0, 65 * MIN) });
  const apres = await journal();
  assert.equal(Object.keys(apres).length, 6);
  assert.ok(apres[`users/B/notifications/u-${MIFF}-A-${plus(T0, 65 * MIN).getTime()}`]);
});

test('CNC-22 : deux exécutions simultanées du même événement -> exactement UNE ligne par destinataire (la transaction départage)', async () => {
  await creerMiff(MIFF, quatre());
  const c1 = contexte();
  const c2 = contexte();
  c2.dep.fcm = c1.fcm;
  await Promise.all([publier(c1), publier(c2)]);
  assert.equal(Object.keys(await journal()).length, 3);
  assert.equal(c1.fcm.messages.length, 3);
});

test('CNC-22 : une réservation qui ÉCHOUE n\'écrit ni ligne, ni état, et n\'envoie aucun push (atomicité)', async () => {
  await creerMiff(MIFF, quatre());
  const cassee = new Proxy(db, {
    get(cible, propriete) {
      if (propriete === 'runTransaction') return async () => { throw new Error('transaction refusée (simulation)'); };
      const v = cible[propriete];
      return typeof v === 'function' ? v.bind(cible) : v;
    },
  });
  const c = contexte({ base: cassee });
  const bilan = await publier(c);
  assert.equal(bilan.erreur, true);
  assert.deepEqual(c.fcm.messages, []);
  assert.deepEqual(await journal(), {});
  assert.equal(await etatExiste(`notifState/${MIFF}`), false);
  // Idem pour l'alerte.
  const d = contexte({ base: cassee });
  assert.equal((await debut(d)).erreur, true);
  assert.deepEqual(d.fcm.messages, []);
  assert.deepEqual(await journal(), {});
  assert.equal(await etatExiste(`notifState/${MIFF}_alertes`), false);
});

test('une ligne écrite a TOUJOURS son envoi tenté : FCM en panne définitive -> les lignes existent quand même (le téléphone les relira) ; jeton inconnu -> jeton purgé, ligne conservée', async () => {
  await creerMiff(MIFF, quatre());
  const panne = contexte({ fcm: fauxFcm('messaging/third-party-auth-error') });
  const bilan = await publier(panne);
  assert.equal(bilan.envoyes, 0);
  assert.equal(panne.fcm.messages.length, 3, 'envoi tenté pour chaque destinataire');
  assert.deepEqual(await destinatairesDuJournal(), ['B', 'C', 'D']);

  await viderEmulateur();
  await creerMiff(MIFF, quatre());
  const inconnu = contexte({ fcm: fauxFcm('messaging/registration-token-not-registered') });
  const b2 = await publier(inconnu);
  assert.equal(b2.purges, 3);
  assert.deepEqual(await destinatairesDuJournal(), ['B', 'C', 'D'], 'la ligne reste : seul le jeton est supprimé');
});

test('`set` et non `create` : une ligne de même identifiant déjà présente ne bloque JAMAIS le push (elle est simplement réécrite)', async () => {
  await creerMiff(MIFF, quatre());
  const id = `u-${MIFF}-A-${T0.getTime()}`;
  await db.doc(`users/B/notifications/${id}`).set({ v: 1, kind: 'update', miffId: MIFF, actorUid: 'A', createdAt: ts(plus(T0, -DAYS(1))), expireAt: ts(plus(T0, DAYS(29))) });
  const c = contexte();
  const bilan = await publier(c);
  assert.equal(bilan.envoyes, 3);
  assert.equal(Object.keys(await journal()).length, 3);
});

test('CNC-9 quinquies : rien n\'est écrit pour le passé (événement trop ancien) ; aucun rétro-remplissage', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte({ maintenant: plus(T0, 10 * MIN) });
  assert.equal((await publier(c)).motif, 'trop_ancien');
  assert.equal((await debut(c)).motif, 'trop_ancien');
  assert.deepEqual(await journal(), {});
});

// --- Alerte : début et fin ---------------------------------------------------------------------------------------------------------------------

test('alerte, début : une ligne `alert_start` par destinataire AVEC un appareil de session, MÊME sans partage et MÊME interrupteur « nouvelles » coupé (D16) ; identifiant « as- » = celui du message', async () => {
  const m = quatre();
  m.B.partage = false;
  m.C.appareils = [{ notify: { updates: false } }];
  m.D.session = null; // pas de session : pas de push, pas de ligne
  await creerMiff(MIFF, m);
  const c = contexte();
  const bilan = await debut(c);
  const id = `as-${MIFF}-A-${T0.getTime()}`;

  assert.deepEqual(jetons(c), ['jeton-B-0', 'jeton-C-0']);
  assert.equal(bilan.journalisees, 2);
  assert.deepEqual(await destinatairesDuJournal(), ['B', 'C']);
  const lignes = await journal();
  for (const uid of ['B', 'C']) {
    const l = lignes[`users/${uid}/notifications/${id}`];
    assert.deepEqual(Object.keys(l).sort(), ['actorUid', 'createdAt', 'expireAt', 'kind', 'miffId', 'v']);
    assert.equal(l.kind, 'alert_start');
    assert.equal(l.actorUid, 'A');
    assert.equal(l.miffId, MIFF);
    assert.ok(l.expireAt.isEqual(ts(plus(T0, 5000 + 30 * JOUR))));
  }
  for (const msg of c.fcm.messages) assert.equal(msg.data.entryId, id);
});

test('alerte, fin manuelle : une ligne `alert_end` « ae- » par destinataire, en plus de la ligne de début ; rejeu de la fin = aucune ligne de plus', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  await debut(c);
  c.horloge.maintenant = plus(T0, 2 * H + 5000);
  const bilan = await fin(c);
  assert.equal(bilan.journalisees, 3);
  const idDebut = `as-${MIFF}-A-${T0.getTime()}`;
  const idFin = `ae-${MIFF}-A-${T0.getTime()}`;
  const lignes = await journal();
  assert.equal(Object.keys(lignes).length, 6);
  for (const uid of ['B', 'C', 'D']) {
    assert.equal(lignes[`users/${uid}/notifications/${idDebut}`].kind, 'alert_start');
    assert.equal(lignes[`users/${uid}/notifications/${idFin}`].kind, 'alert_end');
  }
  assert.deepEqual(c.fcm.messages.filter((x) => x.data.event === 'end').map((x) => x.data.entryId), [idFin, idFin, idFin]);

  const rejeu = await fin(c);
  assert.equal(rejeu.motif, 'deja_vu');
  assert.equal(Object.keys(await journal()).length, 6);
});

test('alerte : début rejoué, début plafonné (1 par minute), fin sans début notifié = aucune ligne, aucun push', async () => {
  await creerMiff(MIFF, quatre());
  const c = contexte();
  await debut(c);
  assert.equal(Object.keys(await journal()).length, 3);
  assert.equal((await debut(c)).motif, 'deja_vu');
  c.horloge.maintenant = plus(T0, 35_000);
  assert.equal((await debut(c, { quand: plus(T0, 30_000) })).motif, 'plafond');
  assert.equal(Object.keys(await journal()).length, 3, 'le plafond silencieux n\'écrit aucune ligne');
  // Fin d'une alerte dont le début n'a pas été notifié (celle de 30 s) : rien.
  c.horloge.maintenant = plus(T0, 2 * H);
  const orpheline = await fin(c, { debutDe: plus(T0, 30_000), quand: plus(T0, 2 * H - 5000) });
  assert.equal(orpheline.motif, 'debut_non_notifie');
  assert.equal(Object.keys(await journal()).length, 3);
  assert.equal(c.fcm.messages.length, 3);
});

test('alerte : l\'auteur n\'a jamais de ligne de sa propre alerte ; un membre d\'une autre Miff non plus ; deux alertes successives = deux lignes distinctes', async () => {
  await creerMiff(MIFF, quatre());
  await creerMiff('Autre', { A: { prenom: 'Tomessa' }, Z: {} });
  const c = contexte();
  await debut(c);
  c.horloge.maintenant = plus(T0, 10 * MIN + 5000);
  await debut(c, { quand: plus(T0, 10 * MIN) });
  const noms = Object.keys(await journal());
  assert.equal(noms.length, 6);
  assert.ok(!noms.some((p) => p.startsWith('users/A/') || p.startsWith('users/Z/')));
  assert.equal(new Set(noms.map((p) => p.split('/')[3])).size, 2);
});

// --- Coût -----------------------------------------------------------------------------------------------------------------------------------------

test('coût (CNC-B) : +1 écriture par destinataire, 0 lecture de plus : Miff de 4 = 13 lectures / 4 écritures (update et alerte) ; Miff de 40 en alerte = 39 lignes dans UNE transaction', async () => {
  await creerMiff(MIFF, quatre());
  const u = await publier(contexte());
  assert.equal(u.lectures, 13);
  assert.equal(u.ecritures, 4);
  await viderEmulateur();
  await creerMiff(MIFF, quatre());
  const a = await debut(contexte());
  assert.equal(a.lectures, 13);
  assert.equal(a.ecritures, 4);

  await viderEmulateur();
  const membres = Object.fromEntries(Array.from({ length: 40 }, (_, i) => [`M${String(i).padStart(2, '0')}`, {}]));
  await creerMiff('Quarante', membres);
  const grosse = await debut(contexte(), { miffId: 'Quarante', auteurUid: 'M00' });
  assert.equal(grosse.journalisees, 39);
  assert.equal(grosse.ecritures, 40);
  assert.equal((await destinatairesDuJournal()).length, 39);
});
