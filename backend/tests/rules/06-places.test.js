// Plan de test, section 6 : lieux et limites freemium (P1 à P13, puis P14 à P23 : 2 lieux gratuits, catégorie, type modifiable,
// lieu dans plusieurs Miffs ; décisions du PM du 2026-11-01)
import assert from 'node:assert/strict';
import {
  ID, CODE, DAY, inMs, suite, assertFails, assertSucceeds, placeInput, placeSeed, serverTs, Timestamp,
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

s.tc('P2', 'gratuite : lieu sous un identifiant automatique (hors p1 et p2) : refusé', async ({ db }) => {
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

s.tc('P7', 'un simple membre ne crée, ne modifie ni ne supprime un lieu', async ({ db, admin }) => {
  const bob = db('bob');
  // Création : p1 est libre et de type "trusted", donc valide pour une gratuite. Seul le contrôle
  // "propriétaire de la Miff" peut alors refuser (et non la limite freemium).
  await removePlace(admin);
  await assertFails(bob.doc(P1).set(placeInput('bob')));
  await assertFails(bob.collection(placesOf(ID.M1)).doc().set(placeInput('bob')));
  // Modification / suppression d'un lieu existant.
  await admin((f) => f.doc(P1).set(placeSeed()));
  await assertFails(bob.doc(P1).update({ name: 'Piraté', updatedAt: serverTs() }));
  await assertFails(bob.doc(P1).delete());
});

s.tc('P8', 'gratuite : le type ne passe pas à "risk" (zone de danger = premium) ; un lieu "risk" hérité redevient "trusted"', async ({ db, admin }) => {
  await assertFails(db('alice').doc(P1).update({ type: 'risk', updatedAt: serverTs() }));
  // Un lieu "risk" hérité d'un ancien abonnement peut toujours redevenir "trusted" (jamais bloqué).
  await admin((f) => f.doc(P1).update({ type: 'risk' }));
  await assertSucceeds(db('alice').doc(P1).update({ type: 'trusted', updatedAt: serverTs() }));
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

// --- R-LIEU-5, R-LIEU-2, R-LIEU-6 et lieu dans plusieurs Miffs (décisions du PM du 2026-11-01) --------------------------------

const P2 = `${M1}/places/p2`;
const CATEGORIES = ['home', 'work', 'school', 'sport', 'relative', 'leisure', 'other'];
const seedDave = (seedMiff) =>
  seedMiff({ miffId: ID.M3, name: 'Premium', code: CODE.M3, ownerUid: 'dave', members: [{ uid: 'dave', role: 'owner', sharing: true }] });

// Cinq Miffs de dave (premium) : le maximum d'un lot de création (budget de 20 lectures de règles, data-model.md §12).
const DAVE_MIFFS = ['A', 'B', 'C', 'D', 'E'].map((c, i) => ({
  id: `MULTI${c}`.padEnd(20, 'x'),
  code: `BCDFGHK2345${i + 2}`,
}));
const seedDaveMiffs = async (seedMiff) => {
  for (const m of DAVE_MIFFS) {
    await seedMiff({
      miffId: m.id,
      name: `Cercle ${m.id.slice(5, 6)}`,
      code: m.code,
      ownerUid: 'dave',
      members: [{ uid: 'dave', role: 'owner', sharing: true, key: m.id }],
    });
  }
};
const lieuDans = (miffId, placeId) => `miffs/${miffId}/places/${placeId}`;

s.tc('P14', 'gratuite : deux lieux (p1 et p2, type trusted) acceptés ; un troisième (identifiant quelconque) refusé', async ({ db }) => {
  const alice = db('alice');
  // p1 existe déjà (jeu de données) : p2 est le second emplacement gratuit.
  await assertSucceeds(alice.doc(P2).set(placeInput('alice', { name: 'Travail', category: 'work' })));
  await assertFails(alice.doc(`${M1}/places/p3`).set(placeInput('alice')));
  await assertFails(alice.collection(placesOf(ID.M1)).doc().set(placeInput('alice')));
  await assertFails(alice.doc(`${M1}/places/P2`).set(placeInput('alice')));
});

s.tc('P14b', 'gratuite : p2 en zone de danger (risk) refusé ; supprimer un lieu libère son emplacement', async ({ db }) => {
  const alice = db('alice');
  await assertFails(alice.doc(P2).set(placeInput('alice', { type: 'risk' })));
  await assertSucceeds(alice.doc(P2).set(placeInput('alice')));
  await assertSucceeds(alice.doc(P1).delete());
  await assertSucceeds(alice.doc(P1).set(placeInput('alice', { name: 'Nouvelle maison', category: 'home' })));
});

s.tc('P15', 'catégorie obligatoire à la création : absente, vide, inconnue, mal écrite ou non textuelle : refusé', async ({ db, admin }) => {
  await admin((f) => f.doc(P1).delete());
  const ref = db('alice').doc(P1);
  const sansCategorie = placeInput('alice');
  delete sansCategorie.category;
  await assertFails(ref.set(sansCategorie));
  for (const category of ['', 'maison', 'Home', 'HOME', ' home', 'home ', 'danger', null, 3, ['home'], { v: 'home' }]) {
    await assertFails(ref.set(placeInput('alice', { category })));
  }
  await assertSucceeds(ref.set(placeInput('alice')));
});

s.tc('P15b', 'catégorie : les sept valeurs fermées sont acceptées à la création', async ({ db, seedMiff }) => {
  await seedDave(seedMiff);
  const places = db('dave').collection(placesOf(ID.M3));
  for (const category of CATEGORIES) {
    await assertSucceeds(places.doc().set(placeInput('dave', { category })));
  }
});

s.tc('P16', 'catégorie modifiable, jamais invalide ; un lieu hérité sans catégorie ne se modifie qu\'en la renseignant', async ({ db, admin }) => {
  const ref = db('alice').doc(P1);
  await assertSucceeds(ref.update({ category: 'work', updatedAt: serverTs() }));
  for (const category of ['maison', '', null, 7]) {
    await assertFails(ref.update({ category, updatedAt: serverTs() }));
  }
  // Lieu écrit avant R-LIEU-2 (sans catégorie) : le nom seul ne passe plus, nom + catégorie passe.
  const herite = placeSeed();
  delete herite.category;
  await admin((f) => f.doc(P1).set(herite));
  await assertFails(ref.update({ name: 'Autre nom', updatedAt: serverTs() }));
  await assertSucceeds(ref.update({ name: 'Autre nom', category: 'home', updatedAt: serverTs() }));
});

s.tc('P17', 'premium : le type change dans les deux sens (trusted vers risk, puis risk vers trusted), avec la catégorie', async ({ db, seedMiff }) => {
  await seedDave(seedMiff);
  const ref = db('dave').doc(lieuDans(ID.M3, 'abc'));
  await assertSucceeds(ref.set(placeInput('dave')));
  await assertSucceeds(ref.update({ type: 'risk', category: 'other', updatedAt: serverTs() }));
  await assertSucceeds(ref.update({ type: 'trusted', updatedAt: serverTs() }));
  await assertFails(ref.update({ type: 'banane', updatedAt: serverTs() }));
});

s.tc('P18', 'gratuite : passer un lieu de trusted à risk refusé (nom, rayon et catégorie restent modifiables)', async ({ db }) => {
  const ref = db('alice').doc(P1);
  await assertFails(ref.update({ type: 'risk', updatedAt: serverTs() }));
  await assertFails(ref.update({ type: 'risk', category: 'other', name: 'Zone', updatedAt: serverTs() }));
  await assertSucceeds(ref.update({ name: 'Maison (bis)', radiusM: 300, category: 'relative', updatedAt: serverTs() }));
});

s.tc('P19', 'abonnement expiré, inactif ou absent : ni risk à la création, ni passage à risk, ni lieu hors p1 et p2', async ({ db, admin, seedMiff }) => {
  // frank : abonnement expiré (jeu de données). Sa Miff M4 a déjà p1.
  await seedMiff({
    miffId: ID.M4, name: 'Expire', code: CODE.M4, ownerUid: 'frank', members: [{ uid: 'frank', role: 'owner', sharing: true }], place: true,
  });
  const frank = db('frank');
  await assertFails(frank.doc(lieuDans(ID.M4, 'p2')).set(placeInput('frank', { type: 'risk' })));
  await assertFails(frank.doc(lieuDans(ID.M4, 'p1')).update({ type: 'risk', updatedAt: serverTs() }));
  await assertFails(frank.doc(lieuDans(ID.M4, 'zzz')).set(placeInput('frank')));
  await assertSucceeds(frank.doc(lieuDans(ID.M4, 'p2')).set(placeInput('frank')));
  // Abonnement non actif (active=false) alors que la date est dans le futur : même résultat que l'expiration.
  await admin((f) => f.doc('entitlements/frank').update({ active: false, expiresAt: inMs(30 * DAY) }));
  await assertFails(frank.doc(lieuDans(ID.M4, 'p1')).update({ type: 'risk', updatedAt: serverTs() }));
  // Abonnement actif : accepté (contrôle que le refus venait bien de l'abonnement).
  await admin((f) => f.doc('entitlements/frank').update({ active: true }));
  await assertSucceeds(frank.doc(lieuDans(ID.M4, 'p1')).update({ type: 'risk', updatedAt: serverTs() }));
  // Aucun document d'abonnement (alice) : refus.
  await assertFails(db('alice').doc(P2).set(placeInput('alice', { type: 'risk' })));
});

s.tc('P20', 'plusieurs Miffs : un lot de 5 lieux de même identifiant, un par Miff du créateur premium, est accepté', async ({ db, seedMiff }) => {
  await seedDaveMiffs(seedMiff);
  const dave = db('dave');
  const batch = dave.batch();
  for (const m of DAVE_MIFFS) {
    batch.set(dave.doc(lieuDans(m.id, 'lieuUnique1')), placeInput('dave', { name: 'Maison de Nathan', category: 'relative' }));
  }
  await assertSucceeds(batch.commit());
  for (const m of DAVE_MIFFS) await assertSucceeds(dave.doc(lieuDans(m.id, 'lieuUnique1')).get());
});

s.tc('P20b', 'plusieurs Miffs : le lot entier est refusé si l\'une des Miffs n\'est pas celle du créateur ; rien n\'est écrit', async ({ db, seedMiff, admin }) => {
  await seedDaveMiffs(seedMiff);
  const dave = db('dave');
  const batch = dave.batch();
  for (const m of DAVE_MIFFS.slice(0, 4)) batch.set(dave.doc(lieuDans(m.id, 'lieuUnique2')), placeInput('dave'));
  batch.set(dave.doc(lieuDans(ID.M1, 'lieuUnique2')), placeInput('dave')); // M1 est la Miff d'alice
  await assertFails(batch.commit());
  await admin(async (f) => {
    for (const m of DAVE_MIFFS) assert.equal((await f.doc(lieuDans(m.id, 'lieuUnique2')).get()).exists, false);
  });
});

s.tc('P20c', 'plusieurs Miffs : une catégorie invalide ou un identifiant invalide dans une seule copie refuse tout le lot', async ({ db, seedMiff }) => {
  await seedDaveMiffs(seedMiff);
  const dave = db('dave');
  const mauvaiseCategorie = dave.batch();
  DAVE_MIFFS.forEach((m, i) => {
    mauvaiseCategorie.set(dave.doc(lieuDans(m.id, 'lieuUnique3')), placeInput('dave', i === 4 ? { category: 'maison' } : {}));
  });
  await assertFails(mauvaiseCategorie.commit());
  const mauvaisId = dave.batch();
  DAVE_MIFFS.forEach((m, i) => {
    mauvaisId.set(dave.doc(lieuDans(m.id, i === 2 ? 'id-invalide!' : 'lieuUnique3')), placeInput('dave'));
  });
  await assertFails(mauvaisId.commit());
});

s.tc('P21', 'plusieurs Miffs : modification (type compris) et suppression d\'un lieu dans 5 Miffs, un lot chacune, par le créateur premium', async ({ db, seedMiff }) => {
  await seedDaveMiffs(seedMiff);
  const dave = db('dave');
  const creation = dave.batch();
  for (const m of DAVE_MIFFS) creation.set(dave.doc(lieuDans(m.id, 'lieuUnique4')), placeInput('dave'));
  await assertSucceeds(creation.commit());
  const modification = dave.batch();
  for (const m of DAVE_MIFFS) {
    modification.update(dave.doc(lieuDans(m.id, 'lieuUnique4')), { type: 'risk', category: 'sport', updatedAt: serverTs() });
  }
  await assertSucceeds(modification.commit());
  const suppression = dave.batch();
  for (const m of DAVE_MIFFS) suppression.delete(dave.doc(lieuDans(m.id, 'lieuUnique4')));
  await assertSucceeds(suppression.commit());
});

s.tc('P22', 'un simple membre ne crée p2, ne modifie le type ou la catégorie, ni ne supprime', async ({ db }) => {
  const bob = db('bob');
  await assertFails(bob.doc(P2).set(placeInput('bob')));
  await assertFails(bob.doc(P1).update({ type: 'risk', updatedAt: serverTs() }));
  await assertFails(bob.doc(P1).update({ category: 'work', updatedAt: serverTs() }));
  await assertFails(bob.doc(P1).delete());
});

s.tc('P23', 'createdBy et createdAt restent immuables même avec type et catégorie modifiables', async ({ db }) => {
  const ref = db('alice').doc(P1);
  await assertFails(ref.update({ type: 'trusted', createdBy: 'bob', updatedAt: serverTs() }));
  await assertFails(ref.update({ category: 'work', createdAt: Timestamp.now(), updatedAt: serverTs() }));
});
