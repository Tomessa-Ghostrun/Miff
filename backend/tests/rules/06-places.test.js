// Plan de test, section 6 : lieux et limites freemium (P1 à P13)
import {
  ID, CODE, suite, assertFails, assertSucceeds, placeInput, placeSeed, serverTs, Timestamp,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const P1 = `${M1}/places/p1`;
const placesOf = (miffId) => `miffs/${miffId}/places`;

// Le jeu de données de départ contient déjà places/p1 dans M1 : ce helper le retire (état "Miff sans lieu").
const removePlace = (admin) => admin((f) => f.doc(P1).delete());

s.tc('P1', 'gratuite : premier lieu "trusted" (identifiant p1) dans une Miff sans lieu : accepté', async ({ db, admin }) => {
  await removePlace(admin);
  await assertSucceeds(db('alice').doc(P1).set(placeInput('alice')));
});

s.tc('P2', 'gratuite : second lieu (identifiant automatique) : refusé', async ({ db }) => {
  const ref = db('alice').collection(placesOf(ID.M1)).doc();
  await assertFails(ref.set(placeInput('alice')));
});

s.tc('P3', 'gratuite : "recréer" p1 existant = mise à jour ; type/createdBy/createdAt verrouillés', async ({ db }) => {
  const ref = db('alice').doc(P1);
  // Champs verrouillés modifiés : refusé.
  await assertFails(ref.set(placeSeed({ type: 'risk', updatedAt: serverTs() })));
  await assertFails(ref.set(placeSeed({ createdBy: 'bob', updatedAt: serverTs() })));
  await assertFails(ref.set(placeSeed({ createdAt: Timestamp.now(), updatedAt: serverTs() })));
  // Nom, rayon, coordonnées modifiables (createdBy / createdAt / type inchangés).
  await assertSucceeds(
    ref.update({ name: 'Maison (nouvelle)', radiusM: 200, lat: 48.9, lng: 2.4, updatedAt: serverTs() }),
  );
});

s.tc('P4', 'gratuite : lieu de type "risk" (identifiant p1) : refusé', async ({ db, admin }) => {
  await removePlace(admin);
  await assertFails(db('alice').doc(P1).set(placeInput('alice', { type: 'risk' })));
});

s.tc('P5', 'premium : trois lieux (identifiants automatiques) dont un "risk" : accepté', async ({ db, seedMiff }) => {
  await seedMiff({
    miffId: ID.M3,
    name: 'Premium',
    code: CODE.M3,
    ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: true }],
  });
  const places = db('dave').collection(placesOf(ID.M3));
  await assertSucceeds(places.doc().set(placeInput('dave', { name: 'Maison' })));
  await assertSucceeds(places.doc().set(placeInput('dave', { name: 'Ecole' })));
  await assertSucceeds(places.doc().set(placeInput('dave', { name: 'Zone a eviter', type: 'risk' })));
});

s.tc('P6', 'premium expiré : second lieu refusé', async ({ db, seedMiff }) => {
  await seedMiff({
    miffId: ID.M4,
    name: 'Expire',
    code: CODE.M4,
    ownerUid: 'frank',
    members: [{ uid: 'frank', role: 'owner', sharing: true }],
    place: true,
  });
  await assertFails(db('frank').collection(placesOf(ID.M4)).doc().set(placeInput('frank')));
});

s.tc('P7', 'un simple membre ne crée, ne modifie ni ne supprime un lieu', async ({ db }) => {
  const bob = db('bob');
  await assertFails(bob.collection(placesOf(ID.M1)).doc().set(placeInput('bob')));
  await assertFails(bob.doc(P1).update({ name: 'Piraté', updatedAt: serverTs() }));
  await assertFails(bob.doc(P1).delete());
});

s.tc('P8', 'le type d\'un lieu existant ne change pas', async ({ db }) => {
  await assertFails(db('alice').doc(P1).update({ type: 'risk', updatedAt: serverTs() }));
});

s.tc('P9', 'rayon hors bornes, coordonnées invalides, nom vide : refusé', async ({ db }) => {
  const ref = db('alice').doc(P1);
  const bad = [{ radiusM: 10 }, { radiusM: 5000 }, { lat: 95 }, { lng: 200 }, { name: '' }];
  for (const change of bad) {
    await assertFails(ref.update({ ...change, updatedAt: serverTs() }));
  }
});

s.tc('P10', 'supprimer puis recréer p1 : accepté', async ({ db }) => {
  const ref = db('alice').doc(P1);
  await assertSucceeds(ref.delete());
  await assertSucceeds(ref.set(placeInput('alice')));
});

s.tc('P11', 'createdBy d\'un lieu non modifiable', async ({ db }) => {
  await assertFails(db('alice').doc(P1).update({ createdBy: 'bob', updatedAt: serverTs() }));
});

s.tc('P12', 'createdAt fourni par le client à la création : refusé', async ({ db, admin }) => {
  await removePlace(admin);
  await assertFails(db('alice').doc(P1).set(placeInput('alice', { createdAt: Timestamp.now() })));
});

s.tc('P13', 'non membre : ne lit pas les lieux', async ({ db }) => {
  await assertFails(db('eve').collection(placesOf(ID.M1)).get());
});
