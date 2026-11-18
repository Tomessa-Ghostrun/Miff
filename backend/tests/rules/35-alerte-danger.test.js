// Plan de test, section 29 : ALERTE « Je suis en danger » (AD-B1, ticket docs/tickets/alerte-danger.md §6, §10, §15 AD25, AD37, AD40 à AD42 ; décisions du PM du 2026-11-18).
// Document miffs/{miffId}/alerts/{uid} : clé = chemin (Miff d'origine + auteur), 7 clés exactes, aucune position, durée de 7 jours au plus imposée par la règle,
// création / ré-armement (>= 60 s) / fin manuelle par l'auteur SEUL, lecture par tout membre sans réciprocité (D16), suppression exigée dans les lots d'arrêt du
// partage, de départ et de retrait. NON DÉPLOYÉ.
// Comptes (jeu de base) : alice (créatrice de M1, partage actif), bob (membre, partage actif), carol (membre, partage ARRÊTÉ), eve (étrangère), zoe (créatrice de M2),
// dave (Miff+, créateur de M3 dans les cas multi-Miffs).
import {
  ID, CODE, T0, DAY, HOUR, suite, assertFails, assertSucceeds, commit, update, set, del, serverTs, Timestamp,
  statusDoc, positionSeed, sharingOnNow, leaveOps, removeMemberOps,
  DEVICE_A, DEVICE_B, sessionSeed, SESSION_PATH,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const MIN = 60_000;
const alertPath = (uid, miffId = ID.M1) => `miffs/${miffId}/alerts/${uid}`;
const ts = (offsetMs) => Timestamp.fromMillis(Date.now() + offsetMs);

// --- Documents --------------------------------------------------------------------------------------------------------------------
/** Début tel qu'un client l'écrit : heure du serveur, expiresAt = maintenant + 7 j - 2 h (contrat du client), purgeAt = expiresAt + 2 j. */
const startDoc = (over = {}, expiresInMs = 7 * DAY - 2 * HOUR) => {
  const exp = Date.now() + expiresInMs;
  return {
    status: 'active', startedAt: serverTs(), expiresAt: Timestamp.fromMillis(exp), endedAt: null, seq: 1, deviceId: DEVICE_A,
    purgeAt: Timestamp.fromMillis(exp + 2 * DAY), ...over,
  };
};
/** Fin manuelle : active -> ended, heure de fin du serveur, suppression automatique dans 2 jours, appareil de la session. */
const endChange = (over = {}) => ({ status: 'ended', endedAt: serverTs(), purgeAt: ts(2 * DAY), deviceId: DEVICE_A, ...over });

// --- État injecté (règles désactivées) ----------------------------------------------------------------------------------------------
const sess = (h, ...uids) => h.admin(async (f) => { for (const uid of uids) await f.doc(SESSION_PATH(uid)).set(sessionSeed(DEVICE_A)); });
const activeSeed = (over = {}) => ({
  status: 'active', startedAt: ts(-2 * HOUR), expiresAt: ts(7 * DAY - 4 * HOUR), endedAt: null, seq: 1, deviceId: DEVICE_A, purgeAt: ts(9 * DAY - 4 * HOUR), ...over,
});
const endedSeed = (over = {}) => ({
  status: 'ended', startedAt: ts(-2 * HOUR), expiresAt: ts(7 * DAY - 4 * HOUR), endedAt: ts(-HOUR), seq: 1, deviceId: DEVICE_A, purgeAt: ts(2 * DAY), ...over,
});
const expiredSeed = (over = {}) => ({
  status: 'active', startedAt: ts(-8 * DAY), expiresAt: ts(-DAY), endedAt: null, seq: 1, deviceId: DEVICE_A, purgeAt: ts(DAY), ...over,
});
const seed = (h, uid, doc, miffId = ID.M1) => h.admin((f) => f.doc(alertPath(uid, miffId)).set(doc));
const stored = async (h, uid, miffId = ID.M1) => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(alertPath(uid, miffId)).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};
const ref = (h, as, of = as, miffId = ID.M1) => h.db(as).doc(alertPath(of, miffId));

const KEYS = ['deviceId', 'endedAt', 'expiresAt', 'purgeAt', 'seq', 'startedAt', 'status'];

// =====================================================================================================================
// Créer, lire, terminer, expirer SA propre alerte
// =====================================================================================================================
s.tc('AL1', 'créer sa propre alerte : bob et alice (créatrice) ; 7 clés exactes, seq 1, active, fin nulle, durée de 7 jours au plus, purgeAt = expiresAt + 2 j, aucune position', async (h) => {
  await sess(h, 'bob', 'alice');
  await assertSucceeds(ref(h, 'bob').set(startDoc()));
  await assertSucceeds(ref(h, 'alice').set(startDoc()));
  const d = await stored(h, 'bob');
  if (JSON.stringify(Object.keys(d).sort()) !== JSON.stringify(KEYS)) throw new Error(`clés inattendues : ${Object.keys(d)}`);
  if (d.status !== 'active' || d.seq !== 1 || d.endedAt !== null || d.deviceId !== DEVICE_A) throw new Error('état de départ inattendu');
  const dureeMs = d.expiresAt.toMillis() - d.startedAt.toMillis();
  if (dureeMs > 7 * DAY || dureeMs <= 6 * DAY) throw new Error(`durée hors de la fenêtre (6 j, 7 j] : ${dureeMs}`);
  if (d.purgeAt.toMillis() - d.expiresAt.toMillis() !== 2 * DAY) throw new Error('purgeAt doit valoir expiresAt + 2 jours');
  if (d.startedAt.toMillis() < T0.toMillis() + DAY) throw new Error("startedAt doit être l'heure du serveur");
  for (const clef of ['lat', 'lng', 'measuredAt', 'position']) if (clef in d) throw new Error(`aucune position dans l'alerte : ${clef}`);
  await assertSucceeds(ref(h, 'bob').get()); // l'auteur relit la sienne
});

s.tc('AL2', 'terminer SA propre alerte (fin manuelle) : active -> ended, heure de fin du serveur, suppression automatique dans 2 jours ; les autres clés ne bougent pas', async (h) => {
  await sess(h, 'bob');
  await seed(h, 'bob', activeSeed());
  const avant = await stored(h, 'bob');
  await assertSucceeds(ref(h, 'bob').update(endChange()));
  const d = await stored(h, 'bob');
  if (d.status !== 'ended' || !d.endedAt || d.endedAt.toMillis() < T0.toMillis() + DAY) throw new Error("la fin doit porter l'heure du serveur");
  for (const k of ['seq', 'startedAt', 'expiresAt', 'deviceId']) if (JSON.stringify(d[k]) !== JSON.stringify(avant[k])) throw new Error(`${k} ne doit pas changer à la fin`);
  await assertFails(ref(h, 'bob').update(endChange())); // déjà terminée : refusé (ended -> ended)
});

s.tc('AL3', 'EXPIRER : une alerte active dont expiresAt est passé peut être terminée (sans effet de notification) et ré-armée tout de suite ; la règle ne la supprime pas', async (h) => {
  await sess(h, 'bob');
  await seed(h, 'bob', expiredSeed());
  await assertSucceeds(ref(h, 'bob').update(endChange())); // terminer une alerte expirée : permis
  await seed(h, 'bob', expiredSeed());
  await assertSucceeds(ref(h, 'bob').set(startDoc({ seq: 2 }))); // ré-armer une alerte expirée sans fin manuelle : permis (le document existe encore, 7 j + 2 j de purge)
  const d = await stored(h, 'bob');
  if (d.seq !== 2 || d.status !== 'active') throw new Error('le ré-armement devrait avoir remplacé le document');
});

// =====================================================================================================================
// Écrire pour autrui, lire, appartenance
// =====================================================================================================================
s.tc('AL4', 'interdiction d\'écrire pour autrui : créatrice, membre, membre sans partage, étrangère, autre Miff, visiteur ne créent pas l\'alerte de bob ; bob n\'écrit pas dans celle d\'alice', async (h) => {
  await sess(h, 'alice', 'bob', 'carol', 'eve', 'zoe');
  await assertSucceeds(ref(h, 'bob').set(startDoc())); // contrôle : bob peut créer la sienne
  await h.admin((f) => f.doc(alertPath('bob')).delete());
  for (const acteur of ['alice', 'carol', 'eve', 'zoe']) await assertFails(ref(h, acteur, 'bob').set(startDoc()));
  await assertFails(h.anon().doc(alertPath('bob')).set(startDoc()));
  await assertFails(ref(h, 'bob', 'alice').set(startDoc()));
  await assertFails(ref(h, 'alice', 'bob').set(startDoc({ deviceId: DEVICE_A })));
});

s.tc('AL5', 'membre seulement lit : la créatrice, un membre, un autre membre ne peuvent NI terminer, NI ré-armer, NI modifier l\'alerte de bob ; la créatrice ne peut pas la terminer (D10)', async (h) => {
  await sess(h, 'alice', 'bob', 'carol', 'eve');
  await seed(h, 'bob', activeSeed());
  for (const acteur of ['alice', 'carol', 'eve']) {
    await assertFails(ref(h, acteur, 'bob').update(endChange()));
    await assertFails(ref(h, acteur, 'bob').update({ status: 'ended', endedAt: serverTs() }));
    await assertFails(ref(h, acteur, 'bob').update({ expiresAt: ts(30 * DAY) }));
    await assertFails(ref(h, acteur, 'bob').set(startDoc({ seq: 2 })));
  }
  await assertFails(h.anon().doc(alertPath('bob')).update(endChange()));
  const d = await stored(h, 'bob');
  if (d.status !== 'active' || d.seq !== 1) throw new Error("l'alerte de bob ne doit pas avoir changé");
});

s.tc('AL6', 'lecture : tout membre (créatrice, membre, MEMBRE SANS PARTAGE : D16) lit get et liste ; étrangère, autre Miff, visiteur, ex-membre, Miff supprimée : refusés', async (h) => {
  await seed(h, 'bob', activeSeed());
  for (const lecteur of ['alice', 'bob', 'carol']) {
    const snap = await assertSucceeds(ref(h, lecteur, 'bob').get());
    if (snap.data().status !== 'active') throw new Error('le document devrait être relu tel que stocké');
    const liste = await assertSucceeds(h.db(lecteur).collection(`${M1}/alerts`).get());
    if (liste.size !== 1) throw new Error('la liste devrait contenir une alerte');
  }
  await assertFails(ref(h, 'eve', 'bob').get());
  await assertFails(ref(h, 'zoe', 'bob').get());
  await assertFails(h.anon().doc(alertPath('bob')).get());
  await assertFails(h.db('eve').collection(`${M1}/alerts`).get());
  await assertFails(h.db('zoe').collection(`${M1}/alerts`).get());
  await assertFails(h.anon().collection(`${M1}/alerts`).get());
  // Ex-membre : sa fiche disparaît (carol quitte), plus aucun accès.
  await h.admin((f) => f.doc(`${M1}/members/carol`).delete());
  await assertFails(ref(h, 'carol', 'bob').get());
  await assertFails(h.db('carol').collection(`${M1}/alerts`).get());
  // Miff supprimée : les sous-collections subsistent mais ne sont plus lisibles par un membre survivant.
  await h.admin((f) => f.doc(`miffs/${ID.M1}`).delete());
  await assertFails(ref(h, 'alice', 'bob').get());
  await assertFails(h.db('alice').collection(`${M1}/alerts`).get());
});

s.tc('AL7', 'conditions de création : étrangère, autre Miff, partage ARRÊTÉ, ex-membre, Miff supprimée : refusés ; activer le partage ET créer l\'alerte dans le même lot : accepté', async (h) => {
  await sess(h, 'eve', 'zoe', 'carol', 'bob', 'alice');
  await assertFails(ref(h, 'eve').set(startDoc()));
  await assertFails(ref(h, 'zoe', 'zoe').set(startDoc())); // zoe dans M1 : elle n'en est pas membre
  await assertFails(ref(h, 'carol').set(startDoc())); // partage arrêté
  // Lot : activer le partage de carol (heure du serveur) + alerte : le partage est jugé APRÈS le lot.
  await assertSucceeds(commit(h.db('carol'), [
    update(`${M1}/members/carol`, { sharing: sharingOnNow() }),
    set(alertPath('carol'), startDoc()),
  ]));
  // Ex-membre.
  await h.admin(async (f) => { await f.doc(`${M1}/members/bob`).delete(); });
  await assertFails(ref(h, 'bob').set(startDoc()));
  // Miff supprimée : la fiche de la créatrice survit mais la Miff n'existe plus.
  await h.admin(async (f) => { await f.doc(`miffs/${ID.M1}`).delete(); });
  await assertFails(ref(h, 'alice').set(startDoc()));
});

// =====================================================================================================================
// Durée maximale de 7 jours, imposée côté serveur
// =====================================================================================================================
s.tc('AL8', 'durée de 7 jours AU PLUS imposée par la règle : 7 j + 1 min, 8 j, 30 j, 1 an, passé, 6 j et moins, texte, nombre, nul : refusés ; 6 j + 1 h, 7 j - 2 h, 7 j - 1 min : acceptés', async (h) => {
  await sess(h, 'bob');
  const r = ref(h, 'bob');
  const avec = (ms) => startDoc({}, ms);
  for (const ms of [7 * DAY + MIN, 8 * DAY, 30 * DAY, 365 * DAY, -MIN, 0, 6 * DAY - MIN, 6 * DAY, 3 * DAY, DAY]) await assertFails(r.set(avec(ms)));
  for (const mauvais of ['demain', 7, null, true]) await assertFails(r.set(startDoc({ expiresAt: mauvais })));
  for (const ms of [6 * DAY + HOUR, 7 * DAY - 2 * HOUR, 7 * DAY - MIN]) {
    await assertSucceeds(r.set(avec(ms)));
    await h.admin((f) => f.doc(alertPath('bob')).delete());
  }
  // Ré-armement : la même borne.
  await seed(h, 'bob', endedSeed({ startedAt: ts(-HOUR) }));
  await assertFails(r.set(startDoc({ seq: 2 }, 7 * DAY + HOUR)));
  await assertFails(r.set(startDoc({ seq: 2 }, 30 * DAY)));
  await assertSucceeds(r.set(startDoc({ seq: 2 })));
});

s.tc('AL9', 'purgeAt : doit valoir expiresAt + 2 jours (début) ; +1 j, +3 j, = expiresAt, passé, texte, absent : refusés', async (h) => {
  await sess(h, 'bob');
  const r = ref(h, 'bob');
  const exp = Date.now() + 7 * DAY - 2 * HOUR;
  const doc = (purgeAt) => startDoc({ expiresAt: Timestamp.fromMillis(exp), purgeAt });
  for (const delta of [DAY, 3 * DAY, 0, -DAY, 365 * DAY]) await assertFails(r.set(doc(Timestamp.fromMillis(exp + delta))));
  await assertFails(r.set(doc('bientôt')));
  await assertFails(r.set(doc(null)));
  const sans = doc(null);
  delete sans.purgeAt;
  await assertFails(r.set(sans));
  await assertSucceeds(r.set(doc(Timestamp.fromMillis(exp + 2 * DAY))));
});

// =====================================================================================================================
// Champs fermés, aucune position
// =====================================================================================================================
s.tc('AL10', 'champs fermés (hasOnly) : toute clé en plus refusée ; chacune des 7 clés manquante : refusée', async (h) => {
  await sess(h, 'bob');
  const r = ref(h, 'bob');
  for (const extra of [{ text: 'au secours' }, { message: 'x' }, { miffId: ID.M1 }, { uid: 'bob' }, { photo: 'x' }, { name: 'Bob' }, { reason: 'x' }, { updatedAt: serverTs() }, { Status: 'active' }]) {
    await assertFails(r.set(startDoc(extra)));
  }
  for (const clef of KEYS) {
    const doc = startDoc();
    delete doc[clef];
    await assertFails(r.set(doc));
  }
  await assertSucceeds(r.set(startDoc())); // contrôle
});

s.tc('AL11', 'AUCUNE position : lat, lng, measuredAt, position, location, coords, geo, address refusés à la création, au ré-armement et à la fin', async (h) => {
  await sess(h, 'bob');
  const r = ref(h, 'bob');
  const positions = [{ lat: 48.85 }, { lng: 2.35 }, { lat: 48.85, lng: 2.35 }, { measuredAt: serverTs() }, { position: { lat: 48.85, lng: 2.35 } }, { location: 'Paris' }, { coords: [48.85, 2.35] }, { geo: 'x' }, { address: '1 rue X' }];
  for (const p of positions) await assertFails(r.set(startDoc(p)));
  await assertSucceeds(r.set(startDoc())); // contrôle
  for (const p of positions) await assertFails(r.update({ ...endChange(), ...p }));
  await seed(h, 'bob', endedSeed({ startedAt: ts(-HOUR) }));
  for (const p of positions) await assertFails(r.set(startDoc({ seq: 2, ...p })));
  // La dernière position (R-LIEU-POS) reste un document SÉPARÉ : écrire une alerte n'en crée ni n'en modifie.
  const apres = await h.admin(async (f) => (await f.doc(`${M1}/positions/bob`).get()).exists);
  if (apres) throw new Error("l'alerte ne doit écrire aucune position");
});

s.tc('AL12', 'état de départ : status active imposé (ended, autre texte, nul refusés), startedAt = heure du serveur (heure du client refusée), endedAt nul, seq = 1 (0, 2, "1", 1.5, nul refusés)', async (h) => {
  await sess(h, 'bob');
  const r = ref(h, 'bob');
  for (const status of ['ended', 'Active', 'pending', 'danger', null, 1]) await assertFails(r.set(startDoc({ status })));
  await assertFails(r.set(startDoc({ startedAt: ts(0) }))); // heure du client
  await assertFails(r.set(startDoc({ startedAt: ts(-DAY) })));
  await assertFails(r.set(startDoc({ startedAt: 'maintenant' })));
  await assertFails(r.set(startDoc({ endedAt: serverTs() })));
  await assertFails(r.set(startDoc({ endedAt: ts(DAY) })));
  await assertFails(r.set(startDoc({ status: 'ended', endedAt: serverTs() })));
  for (const seq of [0, 2, 5, -1, '1', 1.5, null]) await assertFails(r.set(startDoc({ seq })));
  await assertSucceeds(r.set(startDoc()));
});

// =====================================================================================================================
// Un compte, un seul appareil (session unique)
// =====================================================================================================================
s.tc('AL13', 'session unique : deviceId obligatoire, bien formé et égal à celui de la session (créer, ré-armer, terminer) ; sans session : refusé ; un appareil remplacé ne peut plus terminer', async (h) => {
  const r = ref(h, 'bob');
  await assertFails(r.set(startDoc())); // aucune session du tout
  await sess(h, 'bob'); // session = DEVICE_A
  await assertFails(r.set(startDoc({ deviceId: DEVICE_B }))); // pas celui de la session
  for (const mauvais of ['court', 'a b c d e f g h i j k l m n o p', 'x'.repeat(65), 12345678901234567, null]) await assertFails(r.set(startDoc({ deviceId: mauvais })));
  const sans = startDoc();
  delete sans.deviceId;
  await assertFails(r.set(sans));
  await assertSucceeds(r.set(startDoc()));
  // Un autre appareil se connecte : la session devient DEVICE_B. L'ancien appareil (DEVICE_A) ne peut plus terminer l'alerte.
  await h.admin((f) => f.doc(SESSION_PATH('bob')).set(sessionSeed(DEVICE_B)));
  await assertFails(r.update(endChange({ deviceId: DEVICE_A })));
  await assertFails(r.update({ status: 'ended', endedAt: serverTs(), purgeAt: ts(2 * DAY) })); // sans écrire son identifiant : le document garde celui de l'ancien appareil
  await assertFails(r.set(startDoc({ seq: 2, deviceId: DEVICE_A })));
  // Le nouvel appareil ADOPTE l'alerte : il la termine en écrivant son propre identifiant.
  await assertSucceeds(r.update(endChange({ deviceId: DEVICE_B })));
  const d = await stored(h, 'bob');
  if (d.deviceId !== DEVICE_B || d.status !== 'ended') throw new Error("l'alerte devrait être terminée par le nouvel appareil");
});

// =====================================================================================================================
// Fin manuelle : ce qui peut changer
// =====================================================================================================================
s.tc('AL14', 'fin manuelle : seuls status, endedAt, purgeAt (et deviceId) changent ; startedAt, expiresAt, seq modifiés en même temps : refusés ; heure de fin du client, purgeAt hors de 1 à 3 jours, état autre que ended : refusés', async (h) => {
  await sess(h, 'bob');
  await seed(h, 'bob', activeSeed());
  const r = ref(h, 'bob');
  await assertFails(r.update(endChange({ expiresAt: ts(30 * DAY) }))); // allonge la durée en terminant
  await assertFails(r.update(endChange({ expiresAt: ts(DAY) })));
  await assertFails(r.update(endChange({ startedAt: serverTs() })));
  await assertFails(r.update(endChange({ seq: 9 })));
  await assertFails(r.update(endChange({ endedAt: ts(0) }))); // heure du client
  await assertFails(r.update(endChange({ endedAt: null })));
  await assertFails(r.update(endChange({ purgeAt: ts(10 * DAY) })));
  await assertFails(r.update(endChange({ purgeAt: ts(4 * DAY) })));
  await assertFails(r.update(endChange({ purgeAt: ts(MIN) })));
  await assertFails(r.update(endChange({ purgeAt: ts(-DAY) })));
  await assertFails(r.update(endChange({ purgeAt: 'jamais' })));
  await assertFails(r.update(endChange({ status: 'active' })));
  await assertFails(r.update(endChange({ status: 'cancelled' })));
  await assertFails(r.update({ ...endChange(), text: 'tout va bien' })); // clé en plus
  await assertFails(r.update({ status: 'active', purgeAt: ts(2 * DAY) })); // modification sans fin
  await assertFails(r.update({ expiresAt: ts(DAY) })); // raccourcir seul : aucune route autre que la fin
  await assertSucceeds(r.update(endChange({ purgeAt: ts(DAY + HOUR) })));
  const d = await stored(h, 'bob');
  if (d.status !== 'ended') throw new Error('la fin valide devrait passer');
});

// =====================================================================================================================
// Ré-armement : 60 s, seq + 1, jamais pendant une alerte active
// =====================================================================================================================
s.tc('AL15', 'ré-armement : après une fin et >= 60 s depuis l\'ancien début, seq + 1 ; moins de 60 s refusé ; seq identique, +2, retour à 1 refusés ; alerte ACTIVE non expirée refusée', async (h) => {
  await sess(h, 'bob');
  const r = ref(h, 'bob');
  // Moins de 60 s depuis l'ancien début (fausse alerte immédiatement terminée) : refusé, silencieux (la fonction reste la barrière).
  await seed(h, 'bob', endedSeed({ startedAt: ts(-30_000), endedAt: ts(-10_000) }));
  await assertFails(r.set(startDoc({ seq: 2 })));
  // Juste au-delà de 60 s : accepté.
  await seed(h, 'bob', endedSeed({ startedAt: ts(-61_000), endedAt: ts(-50_000) }));
  for (const seq of [1, 3, 4, 0, 1.5, '2']) await assertFails(r.set(startDoc({ seq })));
  await assertSucceeds(r.set(startDoc({ seq: 2 })));
  const d = await stored(h, 'bob');
  if (d.seq !== 2 || d.status !== 'active' || d.endedAt !== null) throw new Error('le ré-armement devrait remettre une alerte active, sans fin');
  // Alerte active non expirée : refusé (idempotence), quel que soit l'âge.
  await assertFails(r.set(startDoc({ seq: 3 })));
  await seed(h, 'bob', activeSeed({ startedAt: ts(-3 * DAY), expiresAt: ts(4 * DAY), purgeAt: ts(6 * DAY) }));
  await assertFails(r.set(startDoc({ seq: 2 })));
  // Un ré-armement reste soumis aux mêmes conditions qu'un début.
  await seed(h, 'bob', endedSeed());
  await assertFails(r.set(startDoc({ seq: 2, status: 'ended' })));
  await assertFails(r.set(startDoc({ seq: 2, startedAt: ts(0) })));
  await assertFails(r.set(startDoc({ seq: 2, deviceId: DEVICE_B })));
  await assertFails(r.set(startDoc({ seq: 2, lat: 48.85 })));
});

s.tc('AL16', 'ré-armement : partage arrêté, ex-membre, Miff supprimée : refusés (comme une création) ; le créateur ne ré-arme pas pour autrui', async (h) => {
  await sess(h, 'carol', 'bob', 'alice');
  await seed(h, 'carol', endedSeed({ startedAt: ts(-HOUR) }));
  await assertFails(ref(h, 'carol').set(startDoc({ seq: 2 }))); // partage arrêté
  await seed(h, 'bob', endedSeed({ startedAt: ts(-HOUR) }));
  await assertFails(ref(h, 'alice', 'bob').set(startDoc({ seq: 2 })));
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete());
  await assertFails(ref(h, 'bob').set(startDoc({ seq: 2 }))); // ex-membre
  await seed(h, 'alice', endedSeed({ startedAt: ts(-HOUR) }));
  await h.admin((f) => f.doc(`miffs/${ID.M1}`).delete());
  await assertFails(ref(h, 'alice').set(startDoc({ seq: 2 }))); // Miff supprimée
});

// =====================================================================================================================
// Suppression
// =====================================================================================================================
s.tc('AL17', 'suppression : l\'auteur (active ou terminée) et la créatrice peuvent supprimer ; un autre membre, une étrangère, un visiteur : refusés ; un ex-membre efface la sienne', async (h) => {
  await seed(h, 'bob', activeSeed());
  for (const acteur of ['carol', 'eve', 'zoe']) await assertFails(ref(h, acteur, 'bob').delete());
  await assertFails(h.anon().doc(alertPath('bob')).delete());
  await assertFails(ref(h, 'bob', 'alice').delete()); // bob supprime celle d'alice (qui n'existe pas encore : même refus)
  await assertSucceeds(ref(h, 'bob').delete());
  await seed(h, 'bob', endedSeed());
  await assertSucceeds(ref(h, 'bob').delete()); // terminée : l'auteur nettoie
  await seed(h, 'bob', activeSeed());
  await assertSucceeds(ref(h, 'alice', 'bob').delete()); // la créatrice
  await seed(h, 'bob', activeSeed());
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete());
  await assertSucceeds(ref(h, 'bob').delete()); // ex-membre : droit de suppression de sa propre trace (comme le statut)
});

// =====================================================================================================================
// Lots : arrêt du partage, départ, retrait, suppression de la Miff (AD41)
// =====================================================================================================================
const bobOff = () => ({ enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: serverTs() });

s.tc('AL18', 'ARRÊT DU PARTAGE avec une alerte : refusé sans la suppression de l\'alerte dans le même lot ; accepté avec ; sans alerte, le lot habituel des applications actuelles passe toujours', async (h) => {
  await seed(h, 'bob', activeSeed());
  await assertFails(commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: bobOff() }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]));
  await assertSucceeds(commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: bobOff() }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`), del(alertPath('bob'))]));
  if (await stored(h, 'bob')) throw new Error("l'alerte devrait avoir disparu avec l'arrêt du partage");
  // Sans alerte : exactement le lot actuel (statut + photo), accepté.
  await h.admin(async (f) => { await f.doc(`${M1}/members/bob`).update({ sharing: { enabled: true, consentedAt: T0, consentVersion: 'v1', revokedAt: null } }); await f.doc(`${M1}/status/bob`).set(statusDoc({ updatedAt: T0 })); });
  await assertSucceeds(commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: bobOff() }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]));
});

s.tc('AL19', 'DÉPART avec une alerte : refusé sans la suppression de l\'alerte dans le lot ; accepté avec', async (h) => {
  await seed(h, 'bob', activeSeed());
  const quitter = leaveOps('bob');
  await assertFails(commit(h.db('bob'), Object.values(quitter)));
  await assertSucceeds(commit(h.db('bob'), [...Object.values(quitter), del(alertPath('bob'))]));
  if (await stored(h, 'bob')) throw new Error("l'alerte devrait avoir disparu avec le départ");
});

s.tc('AL20', 'RETRAIT par la créatrice avec une alerte du membre retiré : refusé sans suppression de l\'alerte ; accepté avec (code renouvelé) ; la créatrice supprime l\'alerte du membre', async (h) => {
  await seed(h, 'bob', activeSeed());
  const ops = removeMemberOps({ target: 'bob' });
  await assertFails(commit(h.db('alice'), Object.values(ops)));
  await assertSucceeds(commit(h.db('alice'), [...Object.values(ops), del(alertPath('bob'))]));
  if (await stored(h, 'bob')) throw new Error("l'alerte du membre retiré devrait avoir disparu");
});

s.tc('AL21', 'SUPPRESSION de la Miff par la créatrice : la fiche de la créatrice ne part pas tant que SON alerte existe ; avec la suppression de son alerte, le lot habituel passe', async (h) => {
  await seed(h, 'alice', activeSeed());
  // Retirer les autres membres d'abord n'est pas l'objet ici : on isole la règle sur la fiche de la créatrice (départ en supprimant la Miff dans le même lot).
  const lot = [
    del(`${M1}/members/alice`), del(`${M1}/status/alice`), del(`${M1}/photos/alice`), del(`users/alice/memberships/free`),
    del(`miffs/${ID.M1}`), del(`joinCodes/${CODE.M1}`), set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
  ];
  await assertFails(commit(h.db('alice'), lot));
  await assertSucceeds(commit(h.db('alice'), [...lot, del(alertPath('alice'))]));
});

// =====================================================================================================================
// Portée : une seule Miff (D9) ; les autres Miffs ne sont pas touchées (AD41, AD47)
// =====================================================================================================================
s.tc('AL22', 'portée : bob dans deux Miffs ; son alerte dans M1 ne crée rien dans M3 ; arrêter le partage dans M3 (sans alerte) ne touche pas l\'alerte de M1 ; dans M3 il faut retirer l\'alerte de M3 seulement', async (h) => {
  await h.seedMiff({
    miffId: ID.M3, name: 'Amis', code: CODE.M3, ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: true }, { uid: 'bob', role: 'member', sharing: true, key: ID.M3 }],
    status: ['dave', 'bob'],
  });
  await sess(h, 'bob', 'dave');
  await assertSucceeds(ref(h, 'bob', 'bob', ID.M1).set(startDoc())); // alerte dans la seule Miff M1
  if (await stored(h, 'bob', ID.M3)) throw new Error('aucune alerte ne doit être créée dans M3');
  // Les membres de M3 ne voient pas l'alerte de M1 : leur lecture se fait dans M3 (collection vide) et leur lecture de M1 est refusée.
  const liste = await assertSucceeds(h.db('dave').collection(`miffs/${ID.M3}/alerts`).get());
  if (liste.size !== 0) throw new Error("M3 ne doit contenir aucune alerte");
  await assertFails(h.db('dave').collection(`${M1}/alerts`).get());
  // Arrêt du partage dans M3 : n'a pas à toucher l'alerte de M1.
  await assertSucceeds(commit(h.db('bob'), [update(`miffs/${ID.M3}/members/bob`, { sharing: bobOff() }), del(`miffs/${ID.M3}/status/bob`), del(`miffs/${ID.M3}/photos/bob`)]));
  if (!(await stored(h, 'bob', ID.M1))) throw new Error("l'alerte de M1 devrait être intacte");
  // Départ de M1 : l'alerte de M1 doit partir ; l'état de M3 ne compte pas.
  await assertFails(commit(h.db('bob'), Object.values(leaveOps('bob'))));
});

// =====================================================================================================================
// Aucune règle de position, de statut ni de photo modifiée par l'alerte : un statut écrit pendant une alerte ne la touche pas
// =====================================================================================================================
s.tc('AL23', 'indépendance : écrire ou supprimer le statut, la photo, la position d\'un membre en alerte n\'affecte pas l\'alerte ; l\'alerte n\'ouvre aucun droit de lecture sur la position', async (h) => {
  await sess(h, 'bob', 'alice');
  await assertSucceeds(ref(h, 'bob').set(startDoc()));
  await assertSucceeds(h.db('bob').doc(`${M1}/status/bob`).set(statusDoc({ deviceId: DEVICE_A })));
  const apres = await stored(h, 'bob');
  if (!apres || apres.status !== 'active') throw new Error("l'écriture d'un statut ne doit pas effacer l'alerte");
  // alice ne publie pas sa position : elle lit l'alerte de bob mais pas sa position (réciprocité de la position inchangée).
  await h.admin((f) => f.doc(`${M1}/positions/bob`).set(positionSeed()));
  await assertSucceeds(ref(h, 'alice', 'bob').get());
  await assertFails(h.db('alice').doc(`${M1}/positions/bob`).get());
});
