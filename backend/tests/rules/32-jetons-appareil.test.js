// Plan de test, section 26 : JETONS D'APPAREIL pour les notifications push (SRV-2R, ticket docs/tickets/notification-update-membres.md §8, étude
// docs/dev/etude-serveur-notifications-premium.md §8), cas DV1 à DV12. Règles NON DÉPLOYÉES (le fichier déployé porte encore l'ancien gabarit).
// Chemin : users/{uid}/devices/{deviceId}, où deviceId = identifiant de la SESSION active (users/{uid}/session/active.deviceId). Plus la collection
// serveur notifState/{miffId} (AUCUNE règle client : refus par défaut, prouvé ici).
// Comptes du jeu de départ : alice (créatrice de M1), bob et carol (membres), eve (étrangère), zoe (créatrice de M2), dave (Miff+). Les deux
// identifiants de session de test sont DEVICE_A et DEVICE_B (fixtures).
import {
  ID, T0, DAY, suite, assertFails, assertSucceeds, commit, set, del, serverTs, inMs, FieldValue, Timestamp,
  DEVICE_A, DEVICE_B, sessionDoc, sessionSeed, SESSION_PATH, deviceDoc, deviceSeed, DEVICE_DOC_PATH,
} from '../helpers/fixtures.js';

const s = suite();
const seedSession = (h, uid, deviceId = DEVICE_A) => h.admin((f) => f.doc(SESSION_PATH(uid)).set(sessionSeed(deviceId)));
const seedDevice = (h, uid, deviceId = DEVICE_A, over = {}) => h.admin((f) => f.doc(DEVICE_DOC_PATH(uid, deviceId)).set(deviceSeed(over)));
const ref = (h, who, uid, deviceId = DEVICE_A) => h.db(who).doc(DEVICE_DOC_PATH(uid, deviceId));
const put = (h, uid, over = {}, deviceId = DEVICE_A) => ref(h, uid, uid, deviceId).set(deviceDoc(over));
const read = async (h, path) => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};
const count = async (h, uid) => {
  let n = 0;
  await h.admin(async (f) => {
    n = (await f.collection(`users/${uid}/devices`).get()).size;
  });
  return n;
};
const KEYS = ['token', 'platform', 'appVersion', 'locale', 'notificationsEnabled', 'tz', 'notify', 'updatedAt', 'createdAt'];

// ---------------------------------------------------------------------------------------------------------------------------
// Création
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('DV1', "bob (session DEVICE_A) crée le jeton de son appareil : 9 clés, heures du serveur ; il le relit (get et liste) ; le document est celui de la session", async (h) => {
  await seedSession(h, 'bob');
  await assertSucceeds(put(h, 'bob'));
  const d = await read(h, DEVICE_DOC_PATH('bob'));
  if (Object.keys(d).sort().join() !== [...KEYS].sort().join()) throw new Error('les 9 clés exactement');
  if (!(d.updatedAt instanceof Timestamp) || d.updatedAt.toMillis() <= T0.toMillis() || d.createdAt.toMillis() <= T0.toMillis()) throw new Error('heures du serveur attendues');
  if (d.notify.updates !== true || d.platform !== 'android' || d.locale !== 'fr' || d.tz !== 'Europe/Paris' || d.notificationsEnabled !== true) throw new Error('valeurs mal relues');
  await assertSucceeds(ref(h, 'bob', 'bob').get());
  const list = await h.db('bob').collection('users/bob/devices').get();
  if (list.size !== 1 || list.docs[0].id !== DEVICE_A) throw new Error('la liste devrait contenir le seul appareil');
  // Bornes acceptées : jeton de 4096 caractères, version de 32, fuseaux usuels, iOS, anglais, notifications coupées (un `set` sur un document absent = création).
  const fresh = async (over) => {
    await h.admin((f) => f.doc(DEVICE_DOC_PATH('bob')).delete());
    await assertSucceeds(put(h, 'bob', over));
  };
  await fresh({ token: 'x'.repeat(4096), appVersion: 'v'.repeat(32), tz: 'America/Argentina/Buenos_Aires', platform: 'ios', locale: 'en' });
  for (const tz of ['Etc/GMT+5', 'GMT+01:00', 'UTC', 'Europe/Paris', 'A', 'a'.repeat(64)]) await fresh({ tz });
  await fresh({ notificationsEnabled: false, notify: { updates: false } });
});

s.tc('DV2', "lot de connexion d'un nouvel appareil : [session B + jeton B + suppression du jeton A] en UN lot est accepté (getAfter) ; le jeton A sous la session B est refusé", async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await seedDevice(h, 'bob', DEVICE_A);
  const ops = (deviceId) => [
    set(SESSION_PATH('bob'), sessionDoc(DEVICE_B)),
    set(DEVICE_DOC_PATH('bob', deviceId), deviceDoc()),
    del(DEVICE_DOC_PATH('bob', DEVICE_A)),
  ];
  // Un jeton qui ne porte pas l'identifiant de la NOUVELLE session est refusé (identifiant d'une session qui n'existe pas, ou ancien identifiant sans le jeton A déjà là).
  await assertFails(commit(h.db('bob'), [set(SESSION_PATH('bob'), sessionDoc(DEVICE_B)), set(DEVICE_DOC_PATH('bob', 'DeviceCcccccccccccccccccc'), deviceDoc())]));
  await assertFails(commit(h.db('bob'), [set(SESSION_PATH('bob'), sessionDoc(DEVICE_B)), set(DEVICE_DOC_PATH('bob', DEVICE_A), deviceDoc({ createdAt: T0 }))])); // l'ancien jeton ne se met pas à jour sous la nouvelle session
  await assertSucceeds(commit(h.db('bob'), ops(DEVICE_B)));
  if ((await read(h, SESSION_PATH('bob'))).deviceId !== DEVICE_B) throw new Error('session B attendue');
  if ((await read(h, DEVICE_DOC_PATH('bob', DEVICE_A))) !== null) throw new Error('le jeton A devrait être supprimé');
  if ((await read(h, DEVICE_DOC_PATH('bob', DEVICE_B))) === null) throw new Error('le jeton B devrait exister');
  // Le jeton peut aussi être écrit dans un lot SANS la session, si elle existe déjà (cas : permission accordée plus tard).
  await h.admin((f) => f.doc(DEVICE_DOC_PATH('bob', DEVICE_B)).delete());
  await assertSucceeds(commit(h.db('bob'), [set(DEVICE_DOC_PATH('bob', DEVICE_B), deviceDoc())]));
});

s.tc('DV3', 'forme : chacune des 9 clés manquante, clé en trop, ancien gabarit, types et bornes (jeton, plateforme, version, langue, fuseau, notify, booléen, heures) refusés ; contrôle valide', async (h) => {
  await seedSession(h, 'bob');
  for (const key of KEYS) {
    const d = deviceDoc();
    delete d[key];
    await assertFails(ref(h, 'bob', 'bob').set(d));
  }
  const bad = [
    // clés en trop (aucune donnée de plus : ni position, ni nom, ni identifiant publicitaire)
    { lat: 48.85 }, { lng: 2.35 }, { name: 'Bob' }, { userAgent: 'x' }, { adid: 'x' }, { deviceId: DEVICE_A }, { installId: 'x' }, { lastSeenAt: serverTs() }, { notifyAll: true },
    // jeton
    { token: '' }, { token: 'x'.repeat(4097) }, { token: 42 }, { token: null }, { token: ['a'] }, { token: true },
    // plateforme
    { platform: 'web' }, { platform: 'iOS' }, { platform: 'ANDROID' }, { platform: '' }, { platform: null }, { platform: 1 }, { platform: ['ios'] }, { platform: 'ios ' },
    // version de l'application
    { appVersion: '' }, { appVersion: 'v'.repeat(33) }, { appVersion: 12 }, { appVersion: null },
    // langue
    { locale: 'de' }, { locale: 'FR' }, { locale: 'fr-FR' }, { locale: '' }, { locale: null }, { locale: 1 }, { locale: ['fr'] },
    // permission / interrupteur général
    { notificationsEnabled: 'true' }, { notificationsEnabled: 1 }, { notificationsEnabled: null },
    // fuseau
    { tz: '' }, { tz: 'a'.repeat(65) }, { tz: '/Europe/Paris' }, { tz: '1Europe' }, { tz: 'Europe Paris' }, { tz: 'Europe/Paris\n' }, { tz: 'Europe/é' }, { tz: 'Europe\\Paris' }, { tz: 7 }, { tz: null },
    // préférences
    { notify: true }, { notify: null }, { notify: 'oui' }, { notify: [] }, { notify: {} }, { notify: { updates: 'true' } }, { notify: { updates: 1 } }, { notify: { updates: null } },
    { notify: { updates: true, arrival: true } }, { notify: { arrival: true } }, { notify: { updates: true, extra: {} } },
    // heures : jamais celles du client
    { updatedAt: T0 }, { updatedAt: new Date() }, { updatedAt: inMs(60_000) }, { updatedAt: null }, { updatedAt: '2026-11-12' },
    { updatedAt: T0, createdAt: T0 }, { updatedAt: inMs(-DAY), createdAt: inMs(-DAY) }, // les DEUX heures du client, égales entre elles : la création les compare entre elles, pas à l'heure du serveur
    { createdAt: T0 }, { createdAt: new Date() }, { createdAt: inMs(-DAY) }, { createdAt: null }, { createdAt: '2026-11-12' },
  ];
  for (const over of bad) await assertFails(put(h, 'bob', over));
  // Ancien gabarit (token, platform, updatedAt) : refusé, aucune application publiée ne l'écrit.
  await assertFails(ref(h, 'bob', 'bob').set({ token: 'jeton-de-test', platform: 'ios', updatedAt: serverTs() }));
  await assertSucceeds(put(h, 'bob')); // contrôle : le document valide passe
});

s.tc('DV4', "identifiant : il doit être celui de la session ET bien formé (16 à 64 caractères [A-Za-z0-9_-]) ; d1..d5, autre identifiant, identifiant d'une autre session refusés", async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  for (const id of ['d1', 'd2', 'd3', 'd4', 'd5', 'abc', DEVICE_B, `${DEVICE_A}x`, DEVICE_A.toLowerCase(), 'A'.repeat(16)]) await assertFails(put(h, 'bob', {}, id));
  await assertSucceeds(put(h, 'bob', {}, DEVICE_A));
  // Même quand la session porte un identifiant mal formé (injecté sans les règles), le jeton refuse le sien : la forme est contrôlée à part.
  for (const bad of ['d1', 'court', 'A'.repeat(15), 'A'.repeat(65), 'a.b', 'a b', 'é'.repeat(20)]) {
    await seedSession(h, 'carol', bad);
    await assertFails(put(h, 'carol', {}, bad));
  }
  for (const ok of ['A'.repeat(16), 'a-Z_9'.repeat(12) + 'abcd']) {
    await seedSession(h, 'carol', ok);
    await assertSucceeds(put(h, 'carol', {}, ok));
  }
});

s.tc('DV5', "sans session, session supprimée, session d'un autre appareil, session d'un autre compte : le jeton est refusé", async (h) => {
  await assertFails(put(h, 'bob')); // aucune session
  await seedSession(h, 'bob', DEVICE_B);
  await assertFails(put(h, 'bob', {}, DEVICE_A)); // la session est celle d'un autre appareil
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(put(h, 'bob'));
  await h.admin((f) => f.doc(SESSION_PATH('bob')).delete());
  await assertFails(put(h, 'bob')); // session supprimée (déconnexion) : on ne s'enregistre plus
  // La session de carol (même identifiant) ne vaut pas pour bob : on lit la session du compte, jamais celle d'un autre.
  await seedSession(h, 'carol', DEVICE_A);
  await assertFails(put(h, 'bob'));
  await assertFails(put(h, 'bob', {}, DEVICE_B));
});

// ---------------------------------------------------------------------------------------------------------------------------
// Mise à jour
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('DV6', "mise à jour : jeton, fuseau, version, langue, notify.updates, notificationsEnabled (mises à jour PARTIELLES) acceptés ; createdAt et platform figés ; clés supprimées ou ajoutées refusées", async (h) => {
  await seedSession(h, 'bob');
  await seedDevice(h, 'bob');
  const r = ref(h, 'bob', 'bob');
  await assertSucceeds(r.update({ token: 'nouveau-jeton', updatedAt: serverTs() }));
  await assertSucceeds(r.update({ tz: 'America/New_York', appVersion: '1.1.0 (13)', locale: 'en', updatedAt: serverTs() }));
  await assertSucceeds(r.update({ 'notify.updates': false, updatedAt: serverTs() }));
  await assertSucceeds(r.update({ notificationsEnabled: false, updatedAt: serverTs() }));
  await assertSucceeds(r.update({ notify: { updates: true }, notificationsEnabled: true, updatedAt: serverTs() }));
  const d = await read(h, DEVICE_DOC_PATH('bob'));
  if (d.token !== 'nouveau-jeton' || d.tz !== 'America/New_York' || d.locale !== 'en' || d.notify.updates !== true || !d.createdAt.isEqual(T0)) throw new Error('mise à jour mal relue (createdAt doit rester celui de la création)');
  // Refusés : updatedAt absent ou du client, createdAt modifié, plateforme changée, clé supprimée, clé ajoutée, valeurs invalides.
  await assertFails(r.update({ token: 'autre' })); // updatedAt non rafraîchi
  await assertFails(r.update({ token: 'autre', updatedAt: T0 }));
  await assertFails(r.update({ token: 'autre', updatedAt: new Date() }));
  await assertFails(r.update({ createdAt: serverTs(), updatedAt: serverTs() }));
  await assertFails(r.update({ createdAt: inMs(-DAY), updatedAt: serverTs() }));
  await assertFails(r.update({ platform: 'ios', updatedAt: serverTs() }));
  await assertFails(r.update({ tz: FieldValue.delete(), updatedAt: serverTs() }));
  await assertFails(r.update({ notify: FieldValue.delete(), updatedAt: serverTs() }));
  await assertFails(r.update({ createdAt: FieldValue.delete(), updatedAt: serverTs() }));
  await assertFails(r.update({ lat: 48.85, updatedAt: serverTs() }));
  await assertFails(r.update({ token: 'x'.repeat(4097), updatedAt: serverTs() }));
  await assertFails(r.update({ token: '', updatedAt: serverTs() }));
  await assertFails(r.update({ locale: 'de', updatedAt: serverTs() }));
  await assertFails(r.update({ tz: 'pas un fuseau', updatedAt: serverTs() }));
  await assertFails(r.update({ 'notify.updates': 'oui', updatedAt: serverTs() }));
  await assertFails(r.update({ 'notify.arrival': true, updatedAt: serverTs() }));
  await assertFails(r.update({ notificationsEnabled: 'non', updatedAt: serverTs() }));
  // Un `set` complet sur un document EXISTANT est une mise à jour : createdAt (heure serveur de maintenant) n'est plus celui de la création → refusé.
  // Le client lit d'abord (transaction) : il crée si absent, met à jour si présent (docs/dev/jetons-appareil-clients.md).
  await assertFails(put(h, 'bob'));
  await assertSucceeds(r.set({ ...deviceDoc({ createdAt: T0 }), updatedAt: serverTs() })); // set complet qui REPREND le createdAt lu : accepté
});

s.tc('DV7', "appareil remplacé : une fois la session de bob passée à DEVICE_B, le jeton de DEVICE_A ne peut plus être mis à jour ni recréé ; il reste supprimable", async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await seedDevice(h, 'bob', DEVICE_A);
  await assertSucceeds(ref(h, 'bob', 'bob', DEVICE_A).update({ token: 'jeton-avant', updatedAt: serverTs() }));
  await h.admin((f) => f.doc(SESSION_PATH('bob')).set(sessionSeed(DEVICE_B)));
  await assertFails(ref(h, 'bob', 'bob', DEVICE_A).update({ token: 'jeton-apres', updatedAt: serverTs() }));
  await assertFails(ref(h, 'bob', 'bob', DEVICE_A).update({ 'notify.updates': true, updatedAt: serverTs() }));
  await assertSucceeds(ref(h, 'bob', 'bob', DEVICE_A).delete()); // le téléphone remplacé efface son jeton avant de se déconnecter
  await assertFails(put(h, 'bob', {}, DEVICE_A)); // ... et ne peut pas se réenregistrer
  await assertSucceeds(put(h, 'bob', {}, DEVICE_B));
});

// ---------------------------------------------------------------------------------------------------------------------------
// Confidentialité : le jeton est privé
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('DV8', "un jeton est PRIVÉ : ni un membre de la même Miff, ni la créatrice, ni une étrangère, ni un visiteur ne le lisent (get, liste, groupe de collections) ; le propriétaire le lit", async (h) => {
  await seedSession(h, 'bob');
  await seedDevice(h, 'bob');
  for (const who of ['alice', 'carol', 'dave', 'eve', 'zoe']) { // alice crée la Miff de bob, carol la partage, eve et zoe n'y sont pas
    await assertFails(ref(h, who, 'bob').get());
    await assertFails(h.db(who).collection('users/bob/devices').get());
    await assertFails(h.db(who).collection('users/bob/devices').where('platform', '==', 'android').get());
    await assertFails(h.db(who).collectionGroup('devices').get());
  }
  await assertFails(h.anon().doc(DEVICE_DOC_PATH('bob')).get());
  await assertFails(h.anon().collection('users/bob/devices').get());
  await assertFails(h.anon().collectionGroup('devices').get());
  await assertFails(h.db('bob').collectionGroup('devices').get()); // même pour le propriétaire : aucune règle de groupe de collections
  await assertSucceeds(ref(h, 'bob', 'bob').get());
  await assertSucceeds(h.db('bob').collection('users/bob/devices').get());
  // Le profil `users/bob` non plus n'est pas lisible par un autre : le jeton ne fuit pas par le document parent.
  await assertFails(h.db('carol').doc('users/bob').get());
});

s.tc('DV9', "usurpation : personne n'écrit, ne modifie ni ne supprime le jeton d'un autre compte, même avec la forme valide et la session de la cible", async (h) => {
  await seedSession(h, 'bob');
  await seedDevice(h, 'bob');
  await seedSession(h, 'eve', DEVICE_A); // eve a la MÊME identifiant de session : sans effet sur le chemin de bob
  for (const who of ['alice', 'carol', 'eve', 'zoe']) {
    await assertFails(ref(h, who, 'bob').set(deviceDoc()));
    await assertFails(ref(h, who, 'bob').update({ token: 'vole', updatedAt: serverTs() }));
    await assertFails(ref(h, who, 'bob').delete());
  }
  await assertFails(h.anon().doc(DEVICE_DOC_PATH('bob')).set(deviceDoc()));
  await assertFails(h.anon().doc(DEVICE_DOC_PATH('bob')).update({ token: 'vole', updatedAt: serverTs() }));
  await assertFails(h.anon().doc(DEVICE_DOC_PATH('bob')).delete());
  // Créer un jeton pour un compte qui n'a pas de jeton (la session de la cible existe) : refusé aussi.
  await seedSession(h, 'carol', DEVICE_B);
  await assertFails(ref(h, 'eve', 'carol', DEVICE_B).set(deviceDoc()));
  await assertFails(ref(h, 'alice', 'carol', DEVICE_B).set(deviceDoc()));
  if ((await read(h, DEVICE_DOC_PATH('bob'))).token !== 'jeton-de-test-factice') throw new Error('le jeton de bob ne devait pas changer');
  if ((await read(h, DEVICE_DOC_PATH('carol', DEVICE_B))) !== null) throw new Error('aucun jeton ne devait être créé pour carol');
  await assertSucceeds(ref(h, 'bob', 'bob').update({ token: 'le-sien', updatedAt: serverTs() })); // contrôle : le propriétaire le peut
});

// ---------------------------------------------------------------------------------------------------------------------------
// Suppression : déconnexion, remplacement, suppression de compte
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('DV10', "suppression par le propriétaire : jeton existant, jeton absent, session déjà supprimée, session d'un autre appareil ; séquence de la suppression de compte (session d'abord, puis liste + suppression de chaque jeton)", async (h) => {
  await seedSession(h, 'bob');
  await seedDevice(h, 'bob', DEVICE_A);
  await seedDevice(h, 'bob', DEVICE_B); // jeton resté d'un appareil précédent
  await assertSucceeds(ref(h, 'bob', 'bob', DEVICE_B).delete()); // la session est DEVICE_A : la suppression d'un autre jeton est permise
  await assertSucceeds(ref(h, 'bob', 'bob', 'Absent0000000000000000').delete()); // absent : sans effet
  // Déconnexion : le jeton d'abord (puis la session), ou la session d'abord : dans les deux ordres la suppression passe.
  await h.admin((f) => f.doc(SESSION_PATH('bob')).delete());
  await assertSucceeds(ref(h, 'bob', 'bob', DEVICE_A).delete());
  if ((await count(h, 'bob')) !== 0) throw new Error('plus aucun jeton attendu');
  // Séquence des applications actuelles (AccountDeletion Android et iOS) : session supprimée EN PREMIER, puis liste (serveur) et suppression un à un.
  await seedSession(h, 'carol', DEVICE_A);
  await seedDevice(h, 'carol', DEVICE_A);
  await seedDevice(h, 'carol', DEVICE_B);
  await assertSucceeds(h.db('carol').doc(SESSION_PATH('carol')).delete());
  const list = await h.db('carol').collection('users/carol/devices').get();
  if (list.size !== 2) throw new Error('deux jetons attendus dans la liste');
  for (const d of list.docs) await assertSucceeds(h.db('carol').doc(`users/carol/devices/${d.id}`).delete());
  if ((await count(h, 'carol')) !== 0) throw new Error('la suppression de compte doit vider devices');
  // Un lot de plusieurs suppressions (nouvel appareil) passe aussi.
  await seedDevice(h, 'carol', DEVICE_A);
  await seedDevice(h, 'carol', DEVICE_B);
  await assertSucceeds(commit(h.db('carol'), [del(DEVICE_DOC_PATH('carol', DEVICE_A)), del(DEVICE_DOC_PATH('carol', DEVICE_B))]));
});

s.tc('DV11', "pas de compteur de plafond : un seul identifiant est écrivable à la fois (celui de la session) ; des jetons périmés peuvent subsister (limite connue) mais restent inertes ; 6 connexions avec nettoyage = 1 jeton", async (h) => {
  const ids = ['LoginAaaaaaaaaaaaaaaaaaaa', 'LoginBbbbbbbbbbbbbbbbbbbb', 'LoginCcccccccccccccccccc', 'LoginDdddddddddddddddddd', 'LoginEeeeeeeeeeeeeeeeeeee', 'LoginFfffffffffffffffffff'];
  // Connexions SANS nettoyage (client fautif) : chaque session permet un jeton ; les anciens restent mais ne sont plus modifiables.
  await assertSucceeds(commit(h.db('bob'), [set(SESSION_PATH('bob'), sessionDoc(ids[0])), set(DEVICE_DOC_PATH('bob', ids[0]), deviceDoc())]));
  await assertSucceeds(commit(h.db('bob'), [set(SESSION_PATH('bob'), sessionDoc(ids[1])), set(DEVICE_DOC_PATH('bob', ids[1]), deviceDoc())]));
  await assertSucceeds(commit(h.db('bob'), [set(SESSION_PATH('bob'), sessionDoc(ids[2])), set(DEVICE_DOC_PATH('bob', ids[2]), deviceDoc())]));
  if ((await count(h, 'bob')) !== 3) throw new Error('trois jetons périmés/actif attendus (le plafond n est pas compté par les règles)');
  await assertFails(ref(h, 'bob', 'bob', ids[0]).update({ token: 'zombie', updatedAt: serverTs() }));
  await assertFails(ref(h, 'bob', 'bob', ids[1]).update({ token: 'zombie', updatedAt: serverTs() }));
  await assertSucceeds(ref(h, 'bob', 'bob', ids[2]).update({ token: 'actif', updatedAt: serverTs() }));
  await assertFails(put(h, 'bob', {}, ids[3])); // l'identifiant d'une session pas encore ouverte
  // Connexions AVEC nettoyage (contrat) : 6 connexions de suite, un seul jeton à la fin.
  for (let i = 0; i < ids.length; i += 1) {
    const prev = ids.filter((_, j) => j !== i);
    await assertSucceeds(commit(h.db('carol'), [
      set(SESSION_PATH('carol'), sessionDoc(ids[i])),
      set(DEVICE_DOC_PATH('carol', ids[i]), deviceDoc()),
      ...prev.map((p) => del(DEVICE_DOC_PATH('carol', p))), // 5 suppressions, dont des jetons absents : sans effet
    ]));
    if ((await count(h, 'carol')) !== 1) throw new Error(`un seul jeton attendu après la connexion ${i + 1}`);
  }
});

// ---------------------------------------------------------------------------------------------------------------------------
// notifState : collection réservée au serveur
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('DV12', "notifState/{miffId} : AUCUNE règle client, lecture et écriture refusées à tous (créatrice, membre, étranger, visiteur), y compris sous-documents et groupe de collections ; le serveur (Admin) y écrit", async (h) => {
  const state = { authors: { bob: { lastSeenCreatedAt: T0, lastNotifiedAt: T0 } }, expireAt: inMs(2 * DAY) };
  await h.admin((f) => f.doc(`notifState/${ID.M1}`).set(state)); // le serveur contourne les règles (Admin SDK)
  for (const who of ['alice', 'bob', 'carol', 'dave', 'eve', 'zoe']) {
    const db = h.db(who);
    await assertFails(db.doc(`notifState/${ID.M1}`).get());
    await assertFails(db.collection('notifState').get());
    await assertFails(db.collection('notifState').where('expireAt', '>', T0).get());
    await assertFails(db.collectionGroup('notifState').get());
    await assertFails(db.doc(`notifState/${ID.M1}`).set(state));
    await assertFails(db.doc(`notifState/${ID.M1}`).update({ expireAt: inMs(DAY) }));
    await assertFails(db.doc(`notifState/${ID.M1}`).delete());
    await assertFails(db.doc(`notifState/${ID.M1}/authors/bob`).get());
    await assertFails(db.doc(`notifState/${ID.M1}/authors/bob`).set({ a: 1 }));
    await assertFails(db.doc(`notifState/${ID.M2}`).set(state)); // et la Miff d'un autre non plus
  }
  await assertFails(h.anon().doc(`notifState/${ID.M1}`).get());
  await assertFails(h.anon().doc(`notifState/${ID.M1}`).set(state));
  await assertFails(h.anon().doc(`notifState/${ID.M1}`).delete());
  // L'état du serveur n'a pas été touché.
  if (!(await read(h, `notifState/${ID.M1}`)).authors.bob) throw new Error('notifState ne devait pas changer');
  // Pas de passerelle par la Miff : un document `notifState` sous miffs/{id} n'existe pas non plus.
  await assertFails(h.db('alice').doc(`miffs/${ID.M1}/notifState/x`).set({ a: 1 }));
  await assertFails(h.db('alice').doc(`miffs/${ID.M1}/notifState/x`).get());
});
