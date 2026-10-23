// Plan de test, section 11 : durcissement des règles (ticket docs/tickets/durcissement-regles-firestore.md).
// DUR-1 : H1 (ATK1, pierre tombale miffsDeleted) et H2 (ATK2, code secret orphelin).
// Même jeu de données qu'à la section 0 du plan. Chaque cas isole UNE protection : l'outil de mutation
// (tests/tools/mutations.mjs) vérifie que le test devient rouge quand cette protection est retirée.
import {
  ID, CODE, T0, suite, assertFails, assertSucceeds, commit, createMiffOps, joinOps, leaveOps, set, del, serverTs,
  Timestamp,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const TOMB = `miffsDeleted/${ID.M1}`;
const all = (ops) => Object.values(ops);

// Lot final de suppression de la Miff par alice (ticket §4.1.a) : Miff, pierre tombale, code, sa fiche
// membre, son statut, son index. Chaque variante retire UNE pièce.
function deleteMiffLot({ tombstone = true, code = true, tombData = { deletedAt: serverTs() } } = {}) {
  return [
    del(M1),
    ...(tombstone ? [set(TOMB, tombData)] : []),
    ...(code ? [del(`joinCodes/${CODE.M1}`)] : []),
    del(`${M1}/members/alice`),
    del(`${M1}/status/alice`),
    del('users/alice/memberships/free'),
  ];
}

s.tc('H1a', 'ATK1 : la créatrice supprime la Miff avec la pierre tombale et le code, alors que bob et carol restent', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), deleteMiffLot()));
  await admin(async (f) => {
    const [miff, tomb, code, bob] = await Promise.all([
      f.doc(M1).get(), f.doc(TOMB).get(), f.doc(`joinCodes/${CODE.M1}`).get(), f.doc(`${M1}/members/bob`).get(),
    ]);
    if (miff.exists) throw new Error('la Miff devrait être supprimée');
    if (!tomb.exists) throw new Error('la pierre tombale devrait exister');
    if (code.exists) throw new Error('le code devrait être supprimé');
    if (!bob.exists) throw new Error('la fiche de bob devrait toujours exister (survivant)');
    if (Object.keys(tomb.data()).join() !== 'deletedAt') throw new Error('la pierre tombale ne contient que deletedAt');
  });
});

s.tc('H1b', 'ATK1 : suppression de la Miff sans pierre tombale : refusée', async ({ db }) => {
  await assertFails(commit(db('alice'), deleteMiffLot({ tombstone: false })));
  // Contrôle : le même lot avec la pierre tombale est accepté (seule la pierre tombale manquait).
  await assertSucceeds(commit(db('alice'), deleteMiffLot()));
});

s.tc('H1c', 'ATK1 : un survivant ne recrée pas la Miff supprimée sous le même identifiant', async ({ db }) => {
  await assertSucceeds(commit(db('alice'), deleteMiffLot()));
  const bob = db('bob');
  // bob (survivant) libère sa fiche, son statut et son index : autorisé, la Miff n'existant plus.
  await assertSucceeds(commit(bob, Object.values(leaveOps('bob'))));
  // L'attaque : se déclarer créateur de la Miff M1 (mêmes 4 écritures qu'une création normale).
  await assertFails(commit(bob, all(createMiffOps({ uid: 'bob', miffId: ID.M1, code: CODE.NEW }))));
  // Contrôle : exactement le même lot sous un identifiant neuf est accepté.
  await assertSucceeds(commit(bob, all(createMiffOps({ uid: 'bob', miffId: ID.NEW, code: CODE.NEW }))));
});

s.tc('H1f', 'ATK1 : la créatrice qui a gardé sa fiche membre ne recrée pas la Miff supprimée', async ({ db, admin }) => {
  // Suppression de la Miff SANS supprimer la fiche membre d'alice (les règles ne l'exigent pas).
  await assertSucceeds(
    commit(db('alice'), [del(M1), set(TOMB, { deletedAt: serverTs() }), del(`joinCodes/${CODE.M1}`)]),
  );
  const recreate = (miffId, code) => [
    set(`miffs/${miffId}`, { ownerUid: 'alice', name: 'Cercle', joinCode: code, createdAt: serverTs() }),
    set(`joinCodes/${code}`, { miffId, miffName: 'Cercle', createdAt: serverTs() }),
  ];
  // Aucune écriture de fiche membre dans ce lot : la fiche d'alice existe déjà, la création de la Miff
  // elle-même doit donc refuser l'identifiant à la pierre tombale.
  await assertFails(commit(db('alice'), recreate(ID.M1, CODE.NEW)));
  // Contrôle : sous un identifiant sans pierre tombale, avec une fiche membre déjà présente, le lot passe.
  await admin((f) =>
    f.doc(`miffs/${ID.NEW}/members/alice`).set({
      role: 'owner', firstName: 'alice', lastName: 'Test', joinedAt: T0,
      sharing: { enabled: false, consentedAt: null, consentVersion: null, revokedAt: null },
    }),
  );
  await assertSucceeds(commit(db('alice'), recreate(ID.NEW, CODE.NEW)));
});

s.tc('H1d', 'ATK1 : la pierre tombale n\'est créée que par le propriétaire, quand la Miff disparaît, avec le seul champ deletedAt serveur', async ({ db, admin }) => {
  const alice = db('alice');
  // (1) Créée alors que la Miff existe encore : refusé.
  await assertFails(alice.doc(TOMB).set({ deletedAt: serverTs() }));
  // (2) Champ en trop : refusé (le reste du lot est valide).
  await assertFails(commit(alice, deleteMiffLot({ tombData: { deletedAt: serverTs(), note: 'x' } })));
  // (3) Date fournie par le client : refusé.
  await assertFails(commit(alice, deleteMiffLot({ tombData: { deletedAt: Timestamp.now() } })));
  // (4) Champ manquant : refusé.
  await assertFails(commit(alice, deleteMiffLot({ tombData: {} })));
  // Contrôle : le lot valide est accepté, rien n'avait donc été écrit par les tentatives refusées.
  await assertSucceeds(commit(alice, deleteMiffLot()));

  // (5) Miff déjà absente (état injecté) : ni la propriétaire d'avant, ni une étrangère ne crée une pierre tombale
  // (l'appartenance se vérifie sur l'état d'avant le lot).
  await admin(async (f) => {
    await f.doc(TOMB).delete();
    await f.doc(`miffs/${ID.M2}`).delete();
  });
  await assertFails(db('alice').doc(TOMB).set({ deletedAt: serverTs() }));
  await assertFails(db('eve').doc(TOMB).set({ deletedAt: serverTs() }));
  await assertFails(db('zoe').doc(`miffsDeleted/${ID.M2}`).set({ deletedAt: serverTs() }));
});

s.tc('H1g', 'ATK1 : la pierre tombale n\'est jamais lisible, modifiable ni supprimable par un client', async ({ db, anon, admin }) => {
  await admin((f) => f.doc(TOMB).set({ deletedAt: T0 }));
  for (const d of [db('alice'), db('bob'), db('eve'), anon()]) {
    await assertFails(d.doc(TOMB).get());
    await assertFails(d.collection('miffsDeleted').get());
  }
  for (const uid of ['alice', 'bob', 'eve']) {
    await assertFails(db(uid).doc(TOMB).update({ deletedAt: serverTs() }));
    await assertFails(db(uid).doc(TOMB).delete());
  }
});

s.tc('H2a', 'ATK2 : suppression de la Miff en laissant son code secret : refusée', async ({ db }) => {
  await assertFails(commit(db('alice'), deleteMiffLot({ code: false })));
  // Le code supprimé AVANT (lot séparé, autorisé tant que la Miff existe) : le lot final passe sans l'inclure.
  await assertSucceeds(db('alice').doc(`joinCodes/${CODE.M1}`).delete());
  await assertSucceeds(commit(db('alice'), deleteMiffLot({ code: false })));
});

s.tc('H2b', 'ATK2 : un code orphelin (sans Miff) ne permet pas de rejoindre', async ({ db, admin }) => {
  // État injecté : la Miff M2 et sa fiche membre n'existent plus, son code oui.
  await admin(async (f) => {
    await f.doc(`miffs/${ID.M2}`).delete();
    await f.doc(`miffs/${ID.M2}/members/zoe`).delete();
  });
  await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve', miffId: ID.M2, code: CODE.M2 }))));
  // Contrôle : rejoindre M1 avec son code valide est accepté.
  await assertSucceeds(commit(db('eve'), all(joinOps({ uid: 'eve', miffId: ID.M1, code: CODE.M1 }))));
});
