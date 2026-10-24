// Plan de test, section 7 : premium et attaques de privilèges (A1 à A10)
import {
  ID, CODE, DAY, T0, suite, assertFails, commit, createMiffOps, placeInput, serverTs, inMs, userDoc,
  profile, sharingOff,
} from '../helpers/fixtures.js';

const s = suite({ extraProfiles: false });

s.tc('A1', 'on ne s\'attribue pas le premium (écriture de entitlements)', async ({ db }) => {
  await assertFails(db('eve').doc('entitlements/eve').set({ active: true, expiresAt: inMs(365 * DAY) }));
});

s.tc('A2', 'on ne modifie ni ne supprime le premium d\'un autre', async ({ db }) => {
  const ref = db('eve').doc('entitlements/dave');
  await assertFails(ref.update({ expiresAt: inMs(365 * DAY) }));
  await assertFails(ref.delete());
});

s.tc('A3', 'ajouter un champ premium à son profil : refusé (clé inconnue)', async ({ db, admin }) => {
  await admin((f) => f.doc('users/eve').set(userDoc()));
  await assertFails(db('eve').doc('users/eve').update({ premium: true }));
});

s.tc('A4', 'créer son profil avec un champ isPremium : refusé', async ({ db }) => {
  await assertFails(db('eve').doc('users/eve').set(userDoc({ isPremium: true })));
});

s.tc('A5', 'écrire dans le profil d\'un autre utilisateur : refusé', async ({ db }) => {
  await assertFails(db('eve').doc('users/alice').update({ firstName: 'Piraté' }));
});

s.tc('A6', 'index d\'appartenance avec clé premium en étant gratuite : refusé', async ({ db }) => {
  await assertFails(db('eve').doc(`users/eve/memberships/${ID.M1}`).set({ miffId: ID.M1, joinedAt: serverTs() }));
});

s.tc('A7', 'réaffecter un slot d\'appartenance existant : refusé', async ({ db, admin }) => {
  // Préparation : eve est déjà membre de M1 avec son slot gratuit.
  await admin(async (f) => {
    const batch = f.batch();
    batch.set(f.doc(`miffs/${ID.M1}/members/eve`), {
      role: 'member', ...profile('eve'), joinedAt: T0, joinCode: CODE.M1, sharing: sharingOff(),
    });
    batch.set(f.doc('users/eve/memberships/free'), { miffId: ID.M1, joinedAt: T0 });
    await batch.commit();
  });
  const ref = db('eve').doc('users/eve/memberships/free');
  await assertFails(ref.update({ miffId: ID.M2 }));
  await assertFails(ref.set({ miffId: ID.M2, joinedAt: serverTs() }));
});

s.tc('A8', 'documents arbitraires hors schéma : refusé', async ({ db }) => {
  await assertFails(db('eve').doc('secret/x').set({ a: 1 }));
});

s.skipped(
  'A9',
  'contournement par deux comptes',
  'Non automatisable : deux comptes distincts sont deux utilisateurs légitimes pour les règles. Hors périmètre des règles (documenté dans data-model.md, section 6).',
);

s.tc('A10', 'premium expiré : plus de seconde Miff ni de lieu supplémentaire', async ({ db, admin, seedMiff }) => {
  await seedMiff({
    miffId: ID.M3,
    name: 'Premium',
    code: CODE.M3,
    ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: true }],
  });
  // L'abonnement de dave arrive à échéance (date dans le passé). Le comportement tant qu'il est
  // actif est couvert par C10 et P5.
  await admin((f) => f.doc('entitlements/dave').update({ expiresAt: inMs(-DAY) }));
  const dave = db('dave');
  await assertFails(commit(dave, Object.values(createMiffOps({ uid: 'dave', key: ID.NEW }))));
  await assertFails(dave.collection(`miffs/${ID.M3}/places`).doc().set(placeInput('dave')));
});
