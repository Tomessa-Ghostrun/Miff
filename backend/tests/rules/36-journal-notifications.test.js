// Plan de test, section 30 : JOURNAL DE NOTIFICATIONS users/{uid}/notifications/{entryId} (CNC-B, ticket docs/tickets/centre-notifications-complet.md §8.3, §10 CNC-13 à CNC-16,
// décisions du PM du 2026-11-19), cas CN1 à CN10. Règles NON DÉPLOYÉES.
// Écrit par le SERVEUR SEUL (Admin SDK : les règles ne sont pas consultées) ; ici, le serveur est simulé par `h.admin`. Le client lit (propriétaire seul) et supprime.
// Comptes du jeu de départ : alice (créatrice de M1), bob et carol (membres de M1), eve (étrangère), zoe (créatrice de M2), dave.
import { T0, DAY, ID, suite, assertFails, assertSucceeds, commit, set, del, serverTs, inMs, Timestamp } from '../helpers/fixtures.js';

const s = suite();
const PATH = (uid, entryId) => `users/${uid}/notifications/${entryId}`;
const LINE = (over = {}) => ({ v: 1, kind: 'update', miffId: ID.M1, actorUid: 'alice', createdAt: T0, expireAt: Timestamp.fromMillis(T0.toMillis() + 30 * DAY), ...over });
const KEYS = ['v', 'kind', 'miffId', 'actorUid', 'createdAt', 'expireAt'];
const seed = (h, uid, entryId, over = {}) => h.admin((f) => f.doc(PATH(uid, entryId)).set(LINE(over)));
const seedMany = (h, uid, n) =>
  h.admin(async (f) => {
    const batch = f.batch();
    for (let i = 0; i < n; i++) batch.set(f.doc(PATH(uid, `u-${ID.M1}-alice-${1000 + i}`)), LINE({ createdAt: Timestamp.fromMillis(T0.toMillis() + i * 1000) }));
    await batch.commit();
  });
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
    n = (await f.collection(`users/${uid}/notifications`).get()).size;
  });
  return n;
};
const ID1 = `u-${ID.M1}-alice-1765360000123`;
const ID2 = `as-${ID.M1}-alice-1765360000999`;

// ---------------------------------------------------------------------------------------------------------------------------
// Lecture : le propriétaire seul
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('CN1', 'bob (propriétaire) lit ses lignes : get, liste à 100 au plus, requête incrémentale (createdAt > curseur, tri, limite) ; les 3 sortes (update, alert_start, alert_end) et la forme exacte', async (h) => {
  await seed(h, 'bob', ID1);
  await seed(h, 'bob', ID2, { kind: 'alert_start', createdAt: Timestamp.fromMillis(T0.toMillis() + 1000) });
  await seed(h, 'bob', `ae-${ID.M1}-alice-1765360000999`, { kind: 'alert_end', createdAt: Timestamp.fromMillis(T0.toMillis() + 2000) });
  const db = h.db('bob');
  const one = await assertSucceeds(db.doc(PATH('bob', ID1)).get());
  if (Object.keys(one.data()).sort().join() !== [...KEYS].sort().join()) throw new Error('les 6 clés exactement');
  const list = await assertSucceeds(db.collection('users/bob/notifications').limit(100).get());
  if (list.size !== 3) throw new Error('3 lignes attendues');
  for (const limit of [1, 50, 99, 100]) await assertSucceeds(db.collection('users/bob/notifications').limit(limit).get());
  // Requête incrémentale recommandée aux clients : curseur - 2 min, plus ancien d'abord, 100 au plus (index simple sur createdAt, aucun index composite).
  const since = Timestamp.fromMillis(T0.toMillis() - 120_000);
  const inc = await assertSucceeds(db.collection('users/bob/notifications').where('createdAt', '>', since).orderBy('createdAt').limit(100).get());
  if (inc.size !== 3 || inc.docs[0].id !== ID1) throw new Error('tri par date croissante attendu');
  const apres = await assertSucceeds(db.collection('users/bob/notifications').where('createdAt', '>', Timestamp.fromMillis(T0.toMillis() + 500)).orderBy('createdAt').limit(100).get());
  if (apres.size !== 2) throw new Error('le curseur filtre');
  // Un utilisateur sans aucune ligne : liste vide, sans erreur (CNC-9 quinquies : aucun message d'erreur sans journal).
  const vide = await assertSucceeds(h.db('carol').collection('users/carol/notifications').limit(100).get());
  if (vide.size !== 0) throw new Error('liste vide attendue');
});

s.tc('CN2', 'LIMITE OBLIGATOIRE : liste sans limite, limite 101, 500, 10 000 refusées (même avec un filtre et un tri) ; limite 0 ou négative refusée par le SDK ; get d\'un document toujours permis', async (h) => {
  await seedMany(h, 'bob', 5);
  const col = h.db('bob').collection('users/bob/notifications');
  await assertFails(col.get());
  await assertFails(col.orderBy('createdAt').get());
  await assertFails(col.where('createdAt', '>', T0).orderBy('createdAt').get());
  for (const limit of [101, 500, 10_000]) await assertFails(col.limit(limit).get());
  await assertFails(col.orderBy('createdAt').limit(101).get());
  await assertFails(col.where('createdAt', '>', T0).orderBy('createdAt').limit(101).get());
  await assertSucceeds(col.orderBy('createdAt').limit(100).get());
  await assertSucceeds(h.db('bob').doc(PATH('bob', `u-${ID.M1}-alice-1000`)).get());
});

s.tc('CN3', 'PERSONNE D\'AUTRE NE LIT : alice (créatrice de la Miff), carol (membre), eve (étrangère), zoe, visiteur : get et liste de la ligne de bob refusés ; groupe de collections refusé pour tous (même bob)', async (h) => {
  await seed(h, 'bob', ID1);
  for (const who of ['alice', 'carol', 'eve', 'zoe', 'dave']) {
    const db = h.db(who);
    await assertFails(db.doc(PATH('bob', ID1)).get());
    await assertFails(db.collection('users/bob/notifications').limit(100).get());
    await assertFails(db.collection('users/bob/notifications').where('createdAt', '>', T0).orderBy('createdAt').limit(100).get());
    await assertFails(db.collectionGroup('notifications').limit(100).get());
  }
  const anon = h.anon();
  await assertFails(anon.doc(PATH('bob', ID1)).get());
  await assertFails(anon.collection('users/bob/notifications').limit(100).get());
  await assertFails(anon.collectionGroup('notifications').limit(100).get());
  await assertFails(h.db('bob').collectionGroup('notifications').limit(100).get()); // aucune règle de groupe : jamais la lecture croisée de tous les journaux
  await assertFails(h.db('bob').collectionGroup('notifications').where('actorUid', '==', 'alice').limit(100).get());
  // Contrôle : bob lit toujours la sienne.
  await assertSucceeds(h.db('bob').doc(PATH('bob', ID1)).get());
});

s.tc('CN4', 'L\'AUTEUR de l\'événement et les membres de la même Miff ne voient ni la ligne du destinataire ni le fait qu\'elle existe (ligne dont actorUid = alice, Miff M1 : alice, bob, carol lisent chacun SEULEMENT la leur)', async (h) => {
  for (const uid of ['bob', 'carol']) await seed(h, uid, ID1);
  await assertFails(h.db('alice').doc(PATH('bob', ID1)).get());
  await assertFails(h.db('alice').doc(PATH('carol', ID1)).get());
  await assertFails(h.db('bob').doc(PATH('carol', ID1)).get());
  await assertFails(h.db('carol').doc(PATH('bob', ID1)).get());
  await assertFails(h.db('alice').collection('users/carol/notifications').limit(100).get());
  await assertSucceeds(h.db('bob').doc(PATH('bob', ID1)).get());
  await assertSucceeds(h.db('carol').doc(PATH('carol', ID1)).get());
  // alice (l'auteur) n'a aucune ligne : sa propre liste est vide, ce qui n'a rien à voir avec celles des autres.
  if ((await assertSucceeds(h.db('alice').collection('users/alice/notifications').limit(100).get())).size !== 0) throw new Error('vide');
});

// ---------------------------------------------------------------------------------------------------------------------------
// Écriture : serveur seul
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('CN5', 'AUCUNE CRÉATION par un client : ni bob chez lui (ligne VALIDE de 6 clés, heures du serveur ou du client), ni alice chez bob, ni visiteur ; ni `set` fusionné, ni lot, ni identifiant quelconque', async (h) => {
  const db = h.db('bob');
  const formes = [
    LINE(),
    LINE({ createdAt: serverTs(), expireAt: inMs(30 * DAY) }),
    LINE({ kind: 'alert_start' }),
    LINE({ kind: 'alert_end' }),
    LINE({ actorUid: 'bob' }),
    LINE({ miffId: ID.M2 }),
    { v: 1, kind: 'update' },
    {},
  ];
  for (const data of formes) {
    await assertFails(db.doc(PATH('bob', ID1)).set(data));
    await assertFails(db.doc(PATH('bob', ID1)).set(data, { merge: true }));
    await assertFails(h.db('alice').doc(PATH('bob', ID1)).set(data));
    await assertFails(h.anon().doc(PATH('bob', ID1)).set(data));
  }
  await assertFails(commit(db, [set(PATH('bob', ID1), LINE()), set(PATH('bob', ID2), LINE({ kind: 'alert_start' }))]));
  await assertFails(db.collection('users/bob/notifications').add(LINE()));
  // Rien n'a été écrit.
  if ((await count(h, 'bob')) !== 0) throw new Error('aucune ligne ne devait être créée');
});

s.tc('CN6', 'AUCUNE MODIFICATION par un client : le propriétaire ne peut changer aucune clé d\'une ligne existante (kind, miffId, actorUid, heures, version), ni la remplacer, ni ajouter une clé (prénom, photo) ; autrui non plus', async (h) => {
  await seed(h, 'bob', ID1);
  const avant = await read(h, PATH('bob', ID1));
  const db = h.db('bob');
  const ref = () => db.doc(PATH('bob', ID1));
  for (const patch of [
    { kind: 'alert_start' }, { miffId: ID.M2 }, { actorUid: 'eve' }, { v: 2 }, { createdAt: serverTs() }, { createdAt: inMs(DAY) }, { expireAt: inMs(365 * DAY) }, { expireAt: null },
    { firstName: 'Alice' }, { avatar: 'xx' }, { lat: 48.85 }, { readAt: serverTs() },
  ]) {
    await assertFails(ref().update(patch));
    await assertFails(h.db('alice').doc(PATH('bob', ID1)).update(patch));
    await assertFails(h.anon().doc(PATH('bob', ID1)).update(patch));
  }
  await assertFails(ref().set(LINE({ kind: 'alert_end' })));
  await assertFails(ref().set({ ...LINE(), firstName: 'Alice' }));
  const apres = await read(h, PATH('bob', ID1));
  if (JSON.stringify(apres) !== JSON.stringify(avant)) throw new Error('la ligne ne devait pas changer');
});

// ---------------------------------------------------------------------------------------------------------------------------
// Suppression : le propriétaire seul, à tout moment
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('CN7', 'SUPPRESSION par le propriétaire : une ligne, un lot de 100 (page de liste puis suppression), « Tout effacer » en plusieurs pages ; la ligne supprimée ne revient pas ; supprimer une ligne absente est sans erreur', async (h) => {
  await seed(h, 'bob', ID1);
  const db = h.db('bob');
  await assertSucceeds(db.doc(PATH('bob', ID1)).delete());
  if ((await count(h, 'bob')) !== 0) throw new Error('supprimée');
  await assertSucceeds(db.doc(PATH('bob', ID1)).delete()); // déjà absente : idempotent
  // « Tout effacer » : 130 lignes = 2 pages de 100 au plus (la liste exige une limite).
  await seedMany(h, 'bob', 130);
  const page1 = await assertSucceeds(db.collection('users/bob/notifications').limit(100).get());
  if (page1.size !== 100) throw new Error('100 par page');
  const batch1 = db.batch();
  page1.docs.forEach((d) => batch1.delete(d.ref));
  await assertSucceeds(batch1.commit());
  const page2 = await assertSucceeds(db.collection('users/bob/notifications').limit(100).get());
  if (page2.size !== 30) throw new Error('30 restantes');
  const batch2 = db.batch();
  page2.docs.forEach((d) => batch2.delete(d.ref));
  await assertSucceeds(batch2.commit());
  if ((await count(h, 'bob')) !== 0) throw new Error('journal vidé');
});

s.tc('CN8', 'SUPPRESSION par autrui refusée : alice (créatrice, auteur de l\'événement), carol (membre), eve, zoe, visiteur ; un lot qui mélange sa propre ligne et celle d\'autrui est refusé en entier', async (h) => {
  await seed(h, 'bob', ID1);
  await seed(h, 'carol', ID1);
  for (const who of ['alice', 'carol', 'eve', 'zoe', 'dave']) await assertFails(h.db(who).doc(PATH('bob', ID1)).delete());
  await assertFails(h.anon().doc(PATH('bob', ID1)).delete());
  await assertFails(commit(h.db('bob'), [del(PATH('bob', ID1)), del(PATH('carol', ID1))]));
  if ((await count(h, 'bob')) !== 1 || (await count(h, 'carol')) !== 1) throw new Error('rien ne devait être supprimé');
});

s.tc('CN9', 'SUPPRESSION DE COMPTE (CNC-16) : le lot [lignes du journal + profil] est accepté (la suppression d\'une ligne ne dépend NI du profil, NI d\'une Miff, NI d\'une session) ; le journal est effaçable avant ET après la suppression du profil tant que le compte Auth existe', async (h) => {
  await seedMany(h, 'bob', 3);
  const db = h.db('bob');
  const list = await assertSucceeds(db.collection('users/bob/notifications').limit(100).get());
  await assertSucceeds(commit(db, [...list.docs.map((d) => del(d.ref.path)), del('users/bob')]));
  if ((await count(h, 'bob')) !== 0) throw new Error('journal supprimé avec le profil');
  // Après la suppression du profil (cas d'une reprise après échec) : le propriétaire peut encore lister et supprimer ce qui reste.
  await seedMany(h, 'bob', 2);
  await assertSucceeds(db.collection('users/bob/notifications').limit(100).get());
  await assertSucceeds(db.doc(PATH('bob', `u-${ID.M1}-alice-1000`)).delete());
  // Un compte qui n'est plus connecté (visiteur) ne lit ni ne supprime rien.
  await assertFails(h.anon().collection('users/bob/notifications').limit(100).get());
  await assertFails(h.anon().doc(PATH('bob', `u-${ID.M1}-alice-1001`)).delete());
});

s.tc('CN10', 'AUCUNE PASSERELLE : chemins voisins ou imbriqués refusés (notifications racine, users/{uid}/notification, notifs, sous-collection d\'une ligne, ligne sous une Miff) ; les règles voisines (devices, session, memberships, quota) sont inchangées', async (h) => {
  const db = h.db('bob');
  for (const path of ['notifications/x', 'users/bob/notification/x', 'users/bob/notifs/x', `users/bob/notifications/${ID1}/sub/y`, `miffs/${ID.M1}/notifications/x`, `miffs/${ID.M1}/members/bob/notifications/x`]) {
    await assertFails(db.doc(path).set(LINE()));
    await assertFails(db.doc(path).get());
    await assertFails(db.doc(path).delete());
  }
  await assertFails(db.collection('notifications').limit(100).get());
  await assertFails(db.collection('users/bob/notification').limit(100).get());
  // Ce que bob peut toujours faire chez lui (non régression des règles voisines).
  await assertSucceeds(db.doc('users/bob').get());
  await assertSucceeds(db.collection('users/bob/memberships').get());
  await assertFails(db.collection('users/bob/session').get());
});
