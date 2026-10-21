// Plan de test, section 1 : accès par défaut et lecture (R1 à R12)
import { ID, CODE, suite, assertFails, assertSucceeds, photoDoc } from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;

s.tc('R1', 'non connecté : aucune lecture possible', async ({ anon }) => {
  const db = anon();
  await assertFails(db.doc(M1).get());
  await assertFails(db.collection(`${M1}/members`).get());
  await assertFails(db.collection(`${M1}/status`).get());
  await assertFails(db.collection(`${M1}/places`).get());
  await assertFails(db.doc('users/alice').get());
  await assertFails(db.doc(`joinCodes/${CODE.M1}`).get());
});

s.tc('R2', 'connectée mais non membre : ne lit pas la Miff', async ({ db }) => {
  await assertFails(db('eve').doc(M1).get());
});

s.tc('R3', 'non membre : ni membres, ni lieux, ni statuts, ni photos', async ({ db }) => {
  const eve = db('eve');
  await assertFails(eve.collection(`${M1}/members`).get());
  await assertFails(eve.doc(`${M1}/members/alice`).get());
  await assertFails(eve.collection(`${M1}/places`).get());
  await assertFails(eve.collection(`${M1}/status`).get());
  await assertFails(eve.doc(`${M1}/photos/alice`).get());
});

s.tc('R4', 'non membre : aucune liste de collection ni de groupe de collections', async ({ db }) => {
  const eve = db('eve');
  await assertFails(eve.collection('miffs').get());
  await assertFails(eve.collection('users').get());
  await assertFails(eve.collection('joinCodes').get());
  await assertFails(eve.collection('entitlements').get());
  await assertFails(eve.collectionGroup('members').get());
  await assertFails(eve.collectionGroup('status').get());
});

s.tc('R5', 'membre : lit sa Miff, ses membres et ses lieux', async ({ db }) => {
  const bob = db('bob');
  await assertSucceeds(bob.doc(M1).get());
  await assertSucceeds(bob.collection(`${M1}/members`).get());
  await assertSucceeds(bob.collection(`${M1}/places`).get());
});

s.tc('R6', 'membre : ne lit pas le profil privé d\'un autre', async ({ db }) => {
  await assertFails(db('bob').doc('users/alice').get());
});

s.tc('R7', 'on lit son propre profil', async ({ db }) => {
  await assertSucceeds(db('alice').doc('users/alice').get());
});

s.tc('R8', 'membre qui partage : lit le statut des autres (get et list)', async ({ db }) => {
  const bob = db('bob');
  await assertSucceeds(bob.doc(`${M1}/status/alice`).get());
  await assertSucceeds(bob.collection(`${M1}/status`).get());
});

s.tc('R9', 'réciprocité : partage désactivé = pas de statut des autres, mais membres et lieux lisibles', async ({ db }) => {
  const carol = db('carol');
  await assertFails(carol.doc(`${M1}/status/alice`).get());
  await assertFails(carol.collection(`${M1}/status`).get());
  await assertSucceeds(carol.collection(`${M1}/members`).get());
  await assertSucceeds(carol.collection(`${M1}/places`).get());
});

s.tc('R10', 'abonnement premium : lisible par son propriétaire seulement', async ({ db }) => {
  await assertFails(db('eve').doc('entitlements/dave').get());
  await assertSucceeds(db('dave').doc('entitlements/dave').get());
});

s.tc('R11', 'chemins inconnus : lecture et écriture refusées', async ({ db }) => {
  const bob = db('bob');
  await assertFails(bob.doc('foo/bar').get());
  await assertFails(bob.doc('foo/bar').set({ a: 1 }));
  await assertFails(bob.doc(`${M1}/autre/x`).get());
  await assertFails(db('alice').doc(`${M1}/autre/x`).set({ a: 1 }));
});

s.tc('R12', 'photo : get autorisé à un membre qui partage, list refusé', async ({ db, admin }) => {
  await admin(async (f) => {
    await f.doc(`${M1}/photos/alice`).set(photoDoc());
    await f.doc(`${M1}/status/alice`).update({ hasPhoto: true });
  });
  const bob = db('bob');
  await assertSucceeds(bob.doc(`${M1}/photos/alice`).get());
  await assertFails(bob.collection(`${M1}/photos`).get());
});
