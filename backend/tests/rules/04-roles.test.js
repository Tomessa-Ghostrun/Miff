// Plan de test, section 4 : rôles, modification, retrait, départ (M1 à M20)
import {
  ID, CODE, suite, assertFails, assertSucceeds, commit, leaveOps, set, update, del, serverTs, bytes,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;

// Départ / retrait : les écritures nécessaires à ne laisser aucune trace de position.
const removalOps = (uid) => [del(`${M1}/members/${uid}`), del(`${M1}/status/${uid}`), del(`${M1}/photos/${uid}`)];

s.tc('M1', 'un membre modifie son prénom et son avatar (20 Ko maximum)', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/members/bob`);
  await assertSucceeds(ref.update({ firstName: 'Bobby' }));
  await assertSucceeds(ref.update({ avatar: bytes(20000) }));
});

s.tc('M2', 'un membre ne se donne pas le rôle "owner"', async ({ db }) => {
  await assertFails(db('bob').doc(`${M1}/members/bob`).update({ role: 'owner' }));
});

s.tc('M3', 'joinedAt et joinCode sont immuables', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/members/bob`);
  await assertFails(ref.update({ joinedAt: serverTs() }));
  await assertFails(ref.update({ joinCode: CODE.M2 }));
});

s.tc('M4', 'un membre ne modifie pas le document d\'un autre membre', async ({ db }) => {
  await assertFails(db('bob').doc(`${M1}/members/carol`).update({ firstName: 'Intrus' }));
});

s.tc('M5', 'un membre ne devient pas propriétaire de la Miff', async ({ db }) => {
  await assertFails(db('bob').doc(M1).update({ ownerUid: 'bob' }));
});

s.tc('M6', 'la créatrice renomme la Miff (Miff + code dans le même batch)', async ({ db }) => {
  await assertSucceeds(
    commit(db('alice'), [
      update(M1, { name: 'Nouveau nom' }),
      update(`joinCodes/${CODE.M1}`, { miffName: 'Nouveau nom' }),
    ]),
  );
});

s.tc('M7', 'ownerUid et createdAt de la Miff sont immuables', async ({ db }) => {
  const ref = db('alice').doc(M1);
  await assertFails(ref.update({ ownerUid: 'bob' }));
  await assertFails(ref.update({ createdAt: serverTs() }));
});

s.tc('M8', 'un membre ne renomme ni ne supprime la Miff', async ({ db }) => {
  const ref = db('bob').doc(M1);
  await assertFails(ref.update({ name: 'Piraté' }));
  await assertFails(ref.delete());
});

s.tc('M9', 'régénérer le code : nouveau code créé et ancien supprimé dans le même batch', async ({ db }) => {
  await assertSucceeds(
    commit(db('alice'), [
      update(M1, { joinCode: CODE.REGEN }),
      set(`joinCodes/${CODE.REGEN}`, { miffId: ID.M1, miffName: 'Famille', createdAt: serverTs() }),
      del(`joinCodes/${CODE.M1}`),
    ]),
  );
});

s.tc('M10', 'changer le code sans supprimer l\'ancien : refusé', async ({ db }) => {
  await assertFails(
    commit(db('alice'), [
      update(M1, { joinCode: CODE.REGEN }),
      set(`joinCodes/${CODE.REGEN}`, { miffId: ID.M1, miffName: 'Famille', createdAt: serverTs() }),
    ]),
  );
});

s.tc('M11', 'un membre ne régénère pas le code', async ({ db }) => {
  await assertFails(
    commit(db('bob'), [
      update(M1, { joinCode: CODE.REGEN }),
      set(`joinCodes/${CODE.REGEN}`, { miffId: ID.M1, miffName: 'Famille', createdAt: serverTs() }),
      del(`joinCodes/${CODE.M1}`),
    ]),
  );
});

s.tc('M12', 'un membre quitte la Miff (membre, statut, photo, index supprimés ensemble)', async ({ db }) => {
  await assertSucceeds(commit(db('bob'), Object.values(leaveOps('bob'))));
});

s.tc('M13', 'quitter en laissant son statut : refusé (trace de position)', async ({ db }) => {
  const ops = leaveOps('bob');
  await assertFails(commit(db('bob'), [ops.member, ops.photo, ops.index]));
});

s.tc('M14', 'libérer l\'index sans quitter la Miff : refusé', async ({ db }) => {
  await assertFails(db('bob').doc('users/bob/memberships/free').delete());
});

s.tc('M15', 'la créatrice retire un membre (membre, statut, photo)', async ({ db }) => {
  await assertSucceeds(commit(db('alice'), removalOps('bob')));
});

s.tc('M16', 'retirer un membre sans supprimer son statut : refusé', async ({ db }) => {
  await assertFails(commit(db('alice'), [del(`${M1}/members/bob`), del(`${M1}/photos/bob`)]));
});

s.tc('M17', 'un membre ne retire pas un autre membre', async ({ db }) => {
  // Seule la suppression du document membre est tentée : c'est la règle memberDeleteOk (contrôle du
  // propriétaire) qui doit refuser, et non la règle des statuts / photos.
  await assertFails(db('bob').doc(`${M1}/members/carol`).delete());
  // Variante complète (membre, statut, photo) : refusée aussi.
  await assertFails(commit(db('bob'), removalOps('carol')));
});

s.tc('M18', 'la créatrice ne part qu\'en supprimant la Miff dans le même batch', async ({ db, admin }) => {
  // Sans suppression de la Miff : refusé.
  await assertFails(
    commit(db('alice'), [
      del(`${M1}/members/alice`),
      del(`${M1}/status/alice`),
      del(`${M1}/photos/alice`),
      del('users/alice/memberships/free'),
    ]),
  );
  // Les autres membres doivent déjà avoir été retirés (précondition du plan).
  await admin(async (f) => {
    const batch = f.batch();
    for (const uid of ['bob', 'carol']) {
      batch.delete(f.doc(`${M1}/members/${uid}`));
      batch.delete(f.doc(`${M1}/status/${uid}`));
    }
    await batch.commit();
  });
  await assertSucceeds(
    commit(db('alice'), [
      del(M1),
      del(`joinCodes/${CODE.M1}`),
      del(`${M1}/members/alice`),
      del(`${M1}/status/alice`),
      del('users/alice/memberships/free'),
    ]),
  );
});

s.tc('M19', 'un membre retiré supprime son index orphelin ; avant d\'être retiré : refusé', async ({ db, admin }) => {
  await assertFails(db('bob').doc('users/bob/memberships/free').delete());
  await admin(async (f) => {
    await f.doc(`${M1}/members/bob`).delete();
    await f.doc(`${M1}/status/bob`).delete();
  });
  await assertSucceeds(db('bob').doc('users/bob/memberships/free').delete());
});

s.tc('M20', 'un membre retiré ne relit plus rien de la Miff', async ({ db, admin }) => {
  await admin(async (f) => {
    await f.doc(`${M1}/members/bob`).delete();
    await f.doc(`${M1}/status/bob`).delete();
  });
  const bob = db('bob');
  await assertFails(bob.doc(M1).get());
  await assertFails(bob.collection(`${M1}/members`).get());
  await assertFails(bob.collection(`${M1}/status`).get());
  await assertFails(bob.collection(`${M1}/places`).get());
});
