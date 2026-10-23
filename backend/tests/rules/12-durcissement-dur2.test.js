// Plan de test, section 11.2 : durcissement DUR-2 (ticket docs/tickets/durcissement-regles-firestore.md).
//   H1e : miffAlive (une Miff supprimée n'est plus lisible par ses survivants)
//   H3  : ATK3 (réciprocité = avoir publié son propre statut ; pas d'extension aux photos, décision PM)
//   H4  : ATK4 (retrait d'un membre = code renouvelé dans le même lot)
//   H5  : photos (illisibles après expiresAt, 24 h + 1 h tolérée, identiques premium ou non, nettoyage par tout membre)
// Même jeu de données qu'à la section 0 du plan. Chaque cas isole UNE protection (voir tests/tools/mutations.mjs).
import {
  ID, CODE, T0, HOUR, DAY, suite, assertFails, assertSucceeds, commit, createMiffOps, joinOps, leaveOps, removeMemberOps, set, update,
  del, serverTs, statusDoc, photoDoc, inMs, sharingOnNow, sharingOnSeed,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const M3 = `miffs/${ID.M3}`;
const all = (ops) => Object.values(ops);
const expiredPhoto = () => photoDoc({ createdAt: T0, expiresAt: inMs(-HOUR) });
const validPhoto = () => photoDoc({ createdAt: T0, expiresAt: inMs(DAY) });

// Lot final de suppression de la Miff par alice (DUR-1) : alice et ses données, la Miff, la pierre tombale, le code.
const deleteMiffLot = () => [
  del(M1),
  set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
  del(`joinCodes/${CODE.M1}`),
  del(`${M1}/members/alice`),
  del(`${M1}/status/alice`),
  del('users/alice/memberships/free'),
];

const bobSharingOff = () => ({ enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: serverTs() });
const revokeBob = (db) =>
  commit(db('bob'), [update(`${M1}/members/bob`, { sharing: bobSharingOff() }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]);

async function assertMissing(promise) {
  const snap = await assertSucceeds(promise);
  if (snap.exists) throw new Error('le document ne devrait pas exister');
}

// ---------------------------------------------------------------------------------------------------------
// H1e : miffAlive
// ---------------------------------------------------------------------------------------------------------
s.tc('H1e', 'miffAlive : après suppression de la Miff, un survivant ne lit plus lieux, membres, statuts ni photos', async ({ db, admin }) => {
  await admin((f) => f.doc(`${M1}/photos/carol`).set(validPhoto()));
  const bob = db('bob');
  // Contrôle AVANT suppression : les mêmes lectures sont autorisées (bob partage, son statut est publié).
  await assertSucceeds(bob.collection(`${M1}/places`).get());
  await assertSucceeds(bob.doc(`${M1}/places/p1`).get());
  await assertSucceeds(bob.collection(`${M1}/members`).get());
  await assertSucceeds(bob.doc(`${M1}/members/bob`).get());
  await assertSucceeds(bob.collection(`${M1}/status`).get());
  await assertSucceeds(bob.doc(`${M1}/status/bob`).get());
  await assertSucceeds(bob.doc(`${M1}/photos/carol`).get());

  await assertSucceeds(commit(db('alice'), deleteMiffLot()));

  // La fiche de bob existe toujours (survivant) : seule l'existence de la Miff les rend illisibles.
  await assertFails(bob.collection(`${M1}/places`).get());
  await assertFails(bob.doc(`${M1}/places/p1`).get());
  await assertFails(bob.collection(`${M1}/members`).get());
  await assertFails(bob.doc(`${M1}/members/bob`).get());
  await assertFails(bob.collection(`${M1}/status`).get());
  await assertFails(bob.doc(`${M1}/status/bob`).get());
  await assertFails(bob.doc(`${M1}/photos/carol`).get());
  // Le document Miff lui-même : « introuvable », pas une erreur (l'app détecte la suppression et se nettoie).
  await assertMissing(bob.doc(M1).get());
  // Le nettoyage reste possible : bob quitte (fiche, statut, photo, index).
  await assertSucceeds(commit(bob, all(leaveOps('bob'))));
});

// ---------------------------------------------------------------------------------------------------------
// H3 : ATK3, réciprocité par statut publié
// ---------------------------------------------------------------------------------------------------------
s.tc('H3a', 'ATK3 : partage activé mais statut non publié : statuts refusés, photos des autres encore lisibles (décision PM)', async ({ db, admin }) => {
  await admin((f) => f.doc(`${M1}/photos/alice`).set(validPhoto()));
  const carol = db('carol');
  await assertSucceeds(carol.doc(`${M1}/members/carol`).update({ sharing: sharingOnNow() }));
  await assertFails(carol.doc(`${M1}/status/alice`).get());
  await assertFails(carol.doc(`${M1}/status/bob`).get());
  await assertFails(carol.collection(`${M1}/status`).get());
  // Photos : réciprocité inchangée (partage activé suffit). Les membres et les lieux restent lisibles.
  await assertSucceeds(carol.doc(`${M1}/photos/alice`).get());
  await assertSucceeds(carol.collection(`${M1}/members`).get());
  await assertSucceeds(carol.collection(`${M1}/places`).get());
});

s.tc('H3b', 'ATK3 : partage activé et statut créé dans le même lot : statuts lisibles (get et liste)', async ({ db }) => {
  const carol = db('carol');
  await assertSucceeds(
    commit(carol, [update(`${M1}/members/carol`, { sharing: sharingOnNow() }), set(`${M1}/status/carol`, statusDoc())]),
  );
  await assertSucceeds(carol.doc(`${M1}/status/alice`).get());
  await assertSucceeds(carol.collection(`${M1}/status`).get());
});

s.tc('H3c', 'ATK3 : après révocation (statut supprimé), les statuts des autres ne sont plus lisibles', async ({ db }) => {
  const bob = db('bob');
  await assertSucceeds(bob.doc(`${M1}/status/alice`).get());
  await assertSucceeds(bob.collection(`${M1}/status`).get());
  await assertSucceeds(revokeBob(db));
  await assertFails(bob.doc(`${M1}/status/alice`).get());
  await assertFails(bob.collection(`${M1}/status`).get());
});

s.tc('H3e', 'ATK3 : défense en profondeur, un statut présent ne suffit pas si le partage est désactivé (état injecté)', async ({ db, admin }) => {
  // État incohérent que les règles d'écriture rendent impossible aujourd'hui : carol a un statut mais son partage est désactivé.
  await admin((f) => f.doc(`${M1}/status/carol`).set(statusDoc({ updatedAt: T0 })));
  const carol = db('carol');
  await assertFails(carol.doc(`${M1}/status/alice`).get());
  await assertFails(carol.collection(`${M1}/status`).get());
  // Contrôle : même état mais partage activé par l'administrateur de test : lecture autorisée.
  await admin((f) => f.doc(`${M1}/members/carol`).update({ sharing: sharingOnSeed() }));
  await assertSucceeds(carol.doc(`${M1}/status/alice`).get());
});

s.tc('H3d', 'photos : ni un étranger, ni un membre dont le partage est désactivé ne les lit', async ({ db, admin }) => {
  await admin((f) => f.doc(`${M1}/photos/alice`).set(validPhoto()));
  await assertSucceeds(db('bob').doc(`${M1}/photos/alice`).get());
  await assertFails(db('eve').doc(`${M1}/photos/alice`).get());
  await assertFails(db('carol').doc(`${M1}/photos/alice`).get());
  // bob révoque son partage : il perd aussi la lecture des photos.
  await assertSucceeds(revokeBob(db));
  await assertFails(db('bob').doc(`${M1}/photos/alice`).get());
});

// ---------------------------------------------------------------------------------------------------------
// H4 : ATK4, retrait d'un membre = code renouvelé
// ---------------------------------------------------------------------------------------------------------
s.tc('H4a', 'ATK4 : retrait sans nouveau code, ou nouveau code sans suppression de l\'ancien : refusé', async ({ db }) => {
  const alice = db('alice');
  const ops = removeMemberOps({ target: 'bob' });
  // (1) Retrait seul (membre, statut, photo) : le code ne change pas.
  await assertFails(commit(alice, [ops.member, ops.status, ops.photo]));
  // (2) Retrait + mise à jour du nom de la Miff seulement : le code ne change pas non plus.
  await assertFails(commit(alice, [ops.member, ops.status, ops.photo, update(M1, { name: 'Famille 2' })]));
  // (3) Nouveau code créé mais ancien code laissé valable : refusé (l'ex-membre garderait un code valide).
  await assertFails(commit(alice, [ops.member, ops.status, ops.photo, ops.miff, ops.newCode]));
  // Contrôle : le lot complet est accepté (seul le renouvellement manquait).
  await assertSucceeds(commit(alice, all(ops)));
});

s.tc('H4b', 'ATK4 : la créatrice retire bob avec renouvellement du code dans le même lot', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), all(removeMemberOps({ target: 'bob' }))));
  await admin(async (f) => {
    const [miff, bob, oldCode, newCode] = await Promise.all([
      f.doc(M1).get(), f.doc(`${M1}/members/bob`).get(), f.doc(`joinCodes/${CODE.M1}`).get(), f.doc(`joinCodes/${CODE.REGEN}`).get(),
    ]);
    if (miff.data().joinCode !== CODE.REGEN) throw new Error('le code de la Miff devrait être le nouveau');
    if (bob.exists) throw new Error('bob devrait être retiré');
    if (oldCode.exists) throw new Error('l\'ancien code devrait être supprimé');
    if (!newCode.exists || newCode.data().miffId !== ID.M1) throw new Error('le nouveau code devrait pointer vers la Miff');
  });
});

s.tc('H4c', 'ATK4 : après le retrait, bob (index libéré) ne rejoint plus avec l\'ANCIEN code', async ({ db }) => {
  await assertSucceeds(commit(db('alice'), all(removeMemberOps({ target: 'bob' }))));
  const bob = db('bob');
  // Nettoyage normal de l'ex-membre : il libère son ancien emplacement gratuit.
  await assertSucceeds(bob.doc('users/bob/memberships/free').delete());
  await assertFails(commit(bob, all(joinOps({ uid: 'bob', miffId: ID.M1, code: CODE.M1 }))));
});

s.tc('H4d', 'ATK4 : après le retrait, bob rejoint avec le NOUVEAU code (partagé par la créatrice)', async ({ db }) => {
  await assertSucceeds(commit(db('alice'), all(removeMemberOps({ target: 'bob' }))));
  const bob = db('bob');
  await assertSucceeds(bob.doc('users/bob/memberships/free').delete());
  await assertSucceeds(commit(bob, all(joinOps({ uid: 'bob', miffId: ID.M1, code: CODE.REGEN }))));
  // Les autres membres voient le nouveau code dans le document Miff.
  const snap = await assertSucceeds(db('carol').doc(M1).get());
  if (snap.data().joinCode !== CODE.REGEN) throw new Error('carol devrait voir le nouveau code');
});

s.tc('H4e', 'ATK4 : un non-propriétaire ne retire personne, même avec un lot complet', async ({ db }) => {
  // bob essaie de retirer carol avec le lot complet de renouvellement : refusé (seule la créatrice retire).
  const ops = removeMemberOps({ target: 'carol' });
  await assertFails(commit(db('bob'), all(ops)));
  // Seule la suppression de la fiche (sans rien d'autre) : refusée aussi.
  await assertFails(db('bob').doc(`${M1}/members/carol`).delete());
});

s.tc('H4f', 'ATK4 : le départ volontaire reste possible, sans changement de code imposé (décision PM Q1)', async ({ db, admin }) => {
  await assertSucceeds(commit(db('bob'), all(leaveOps('bob'))));
  await admin(async (f) => {
    const [miff, code] = await Promise.all([f.doc(M1).get(), f.doc(`joinCodes/${CODE.M1}`).get()]);
    if (miff.data().joinCode !== CODE.M1 || !code.exists) throw new Error('le code ne devait pas changer');
  });
});

// ---------------------------------------------------------------------------------------------------------
// H5 : photos
// ---------------------------------------------------------------------------------------------------------
s.tc('H5a', 'photos : illisible après expiresAt ; photo absente = introuvable (pas une erreur)', async ({ db, admin }) => {
  const bob = db('bob');
  await admin((f) => f.doc(`${M1}/photos/alice`).set(validPhoto()));
  await assertSucceeds(bob.doc(`${M1}/photos/alice`).get());
  await admin((f) => f.doc(`${M1}/photos/alice`).set(expiredPhoto()));
  await assertFails(bob.doc(`${M1}/photos/alice`).get());
  await assertMissing(bob.doc(`${M1}/photos/carol`).get());
});

s.tc('H5b', 'photos : expiresAt au plus 24 h + 1 h de marge à la création (+26 h et +7 jours refusés)', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/photos/bob`);
  await assertFails(ref.set(photoDoc({ expiresAt: inMs(26 * HOUR) })));
  await assertFails(ref.set(photoDoc({ expiresAt: inMs(7 * DAY) })));
  await assertSucceeds(ref.set(photoDoc({ expiresAt: inMs(25 * HOUR - 5 * 60_000) })));
  await assertSucceeds(ref.set(photoDoc({ expiresAt: inMs(24 * HOUR) })));
});

s.tc('H5c', 'photos : tout membre supprime une photo EXPIRÉE ; une photo valide ou un étranger : refusé', async ({ db, admin }) => {
  const photo = `${M1}/photos/alice`;
  // Photo valide : bob (autre membre) ne la supprime pas ; son auteure oui.
  await admin((f) => f.doc(photo).set(validPhoto()));
  await assertFails(db('bob').doc(photo).delete());
  await assertFails(db('carol').doc(photo).delete());
  // Photo expirée : un étranger non, tout membre oui (même carol, partage désactivé).
  await admin((f) => f.doc(photo).set(expiredPhoto()));
  await assertFails(db('eve').doc(photo).delete());
  await assertSucceeds(db('bob').doc(photo).delete());
  await admin((f) => f.doc(photo).set(expiredPhoto()));
  await assertSucceeds(db('carol').doc(photo).delete());
});

s.tc('H5d', 'photos : mêmes règles de durée et d\'expiration pour un compte premium (dave) et gratuit (bob)', async ({ db, admin, seedMiff }) => {
  await seedMiff({
    miffId: ID.M3, name: 'Premiere', code: CODE.M3, ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: true }],
  });
  for (const [uid, base] of [['bob', M1], ['dave', M3]]) {
    const ref = db(uid).doc(`${base}/photos/${uid}`);
    await assertFails(ref.set(photoDoc({ expiresAt: inMs(26 * HOUR) })));
    await assertFails(ref.set(photoDoc({ expiresAt: inMs(7 * DAY) })));
    await assertSucceeds(ref.set(photoDoc({ expiresAt: inMs(24 * HOUR) })));
    // Expirée : illisible pour son propre auteur, quel que soit l'abonnement.
    await admin((f) => f.doc(`${base}/photos/${uid}`).set(expiredPhoto()));
    await assertFails(ref.get());
  }
});

// ---------------------------------------------------------------------------------------------------------
// H7 : le code d'une Miff pointe vers CETTE Miff (revue de sécurité indépendante de DUR-1 / DUR-2)
// ---------------------------------------------------------------------------------------------------------
s.tc('H7a', 'le code d\x27une Miff pointe vers cette Miff : création avec le code d\x27une autre Miff refusée', async ({ db }) => {
  const eve = db('eve');
  // Le code de M2 existe déjà et pointe vers M2 : eve ne crée pas de joinCodes (il existe), elle essaie de le « réutiliser ».
  const ops = createMiffOps({ uid: 'eve', code: CODE.M2 });
  await assertFails(commit(eve, [ops.miff, ops.member, ops.index]));
  // Contrôle : la création normale, avec son propre code créé dans le lot, est acceptée.
  await assertSucceeds(commit(eve, all(createMiffOps({ uid: 'eve', code: CODE.NEW }))));
});

s.tc('H7b', 'le code d\x27une Miff pointe vers cette Miff : renouvellement vers le code d\x27une autre Miff refusé', async ({ db }) => {
  const alice = db('alice');
  // Le code de M2 (Miff de zoe) existe déjà : alice le prend pour M1 et supprime l\x27ancien.
  await assertFails(commit(alice, [update(M1, { joinCode: CODE.M2 }), del(`joinCodes/${CODE.M1}`)]));
  // Contrôle : le renouvellement normal (nouveau code créé pour M1) est accepté.
  await assertSucceeds(
    commit(alice, [
      update(M1, { joinCode: CODE.REGEN }),
      set(`joinCodes/${CODE.REGEN}`, { miffId: ID.M1, miffName: 'Famille', createdAt: serverTs() }),
      del(`joinCodes/${CODE.M1}`),
    ]),
  );
});

s.tc('H7c', 'renouvellement vers un code qui n\x27existe pas (non créé dans le lot) : refusé', async ({ db }) => {
  await assertFails(commit(db('alice'), [update(M1, { joinCode: CODE.REGEN }), del(`joinCodes/${CODE.M1}`)]));
});
