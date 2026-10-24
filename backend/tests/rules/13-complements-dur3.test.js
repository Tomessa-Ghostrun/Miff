// Plan de test, section 12 : compléments de couverture issus de l'extension des mutations (DUR-3, critère R14).
// Chaque cas ci-dessous a été écrit pour tuer un SURVIVANT du test par mutation : avant ce fichier, la protection visée
// pouvait être retirée de firestore.rules sans qu'aucun test ne passe au rouge (voir tests/tools/mutations-dur3.mjs et
// backend/rules-test-report.md, section DUR-3). Chaque test n'introduit qu'UN défaut à la fois : sinon une autre règle
// pourrait refuser à sa place et la protection visée resterait non testée.
import {
  ID, CODE, T0, HOUR, suite, assertFails, assertSucceeds, commit, createMiffOps, joinOps, update, del, serverTs,
  statusDoc, photoDoc, bytes, inMs, sharingOnNow, sharingOff, placeInput, userDoc,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const P1 = `${M1}/places/p1`;
const all = (ops) => Object.values(ops);
const without = (ops, ...names) =>
  Object.entries(ops)
    .filter(([name]) => !names.includes(name))
    .map(([, op]) => op);

// --- Index d'appartenance ----------------------------------------------------------------------------------------

s.tc('M19b', "l'index orphelin d'un membre retiré ne se libère que par lui-même", async ({ db, admin }) => {
  await admin(async (f) => {
    await f.doc(`${M1}/members/bob`).delete();
    await f.doc(`${M1}/status/bob`).delete();
  });
  // bob n'est plus membre : sa seule condition de libération est remplie, mais ce n'est pas à un autre de le faire.
  await assertFails(db('eve').doc('users/bob/memberships/free').delete());
  await assertFails(db('alice').doc('users/bob/memberships/free').delete());
  await assertSucceeds(db('bob').doc('users/bob/memberships/free').delete());
});

s.tc('R6b', "l'index des appartenances n'est lisible que par son propriétaire (get et liste)", async ({ db }) => {
  await assertFails(db('eve').doc('users/alice/memberships/free').get());
  await assertFails(db('bob').collection('users/alice/memberships').get());
  await assertSucceeds(db('alice').doc('users/alice/memberships/free').get());
  await assertSucceeds(db('alice').collection('users/alice/memberships').get());
});

// --- Consentement ------------------------------------------------------------------------------------------------

s.tc('S1b', 'création du membre : consentement avec revokedAt renseigné ou clé inconnue refusé', async ({ db }) => {
  const sharings = [
    { enabled: false, consentedAt: null, consentVersion: null, revokedAt: serverTs() },
    { enabled: true, consentedAt: serverTs(), consentVersion: 'v1', revokedAt: serverTs() },
    { ...sharingOff(), preuve: 'x' },
    { enabled: true, consentedAt: serverTs(), consentVersion: 'v1', revokedAt: null, extra: true },
  ];
  for (const sharing of sharings) await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve', sharing }))));
  // Contrôle positif : le même lot avec un consentement conforme est accepté.
  await assertSucceeds(commit(db('eve'), all(joinOps({ uid: 'eve', sharing: sharingOff() }))));
});

s.tc('S3b', "activation du partage : revokedAt non vidé ou clé inconnue refusé", async ({ db }) => {
  const ref = db('carol').doc(`${M1}/members/carol`);
  await assertFails(
    ref.update({ sharing: { enabled: true, consentedAt: serverTs(), consentVersion: 'v1', revokedAt: serverTs() } }),
  );
  await assertFails(ref.update({ sharing: { ...sharingOnNow(), extra: 'x' } }));
  await assertSucceeds(ref.update({ sharing: sharingOnNow() }));
});

s.tc('S6b', 'révocation : version effacée, date client ou revokedAt vide refusés (la preuve est conservée)', async ({ db }) => {
  const revoke = (sharing) =>
    commit(db('bob'), [update(`${M1}/members/bob`, { sharing }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]);
  await assertFails(revoke({ enabled: false, consentedAt: T0, consentVersion: null, revokedAt: serverTs() }));
  await assertFails(revoke({ enabled: false, consentedAt: T0, consentVersion: 'v2', revokedAt: serverTs() }));
  await assertFails(revoke({ enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: inMs(-HOUR) }));
  await assertFails(revoke({ enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: null }));
  await assertSucceeds(revoke({ enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: serverTs() }));
});

// --- Statut et photo ---------------------------------------------------------------------------------------------

s.tc('S12b', 'statut : batterie négative ou non entière, placeId vide ou trop long refusés ; bornes incluses acceptées', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/status/bob`);
  await assertFails(ref.set(statusDoc({ batteryPct: -5 })));
  await assertFails(ref.set(statusDoc({ batteryPct: 50.5 })));
  await assertFails(ref.set(statusDoc({ batteryPct: '80' })));
  await assertFails(ref.set(statusDoc({ placeId: '' })));
  await assertFails(ref.set(statusDoc({ placeId: 'x'.repeat(41) })));
  await assertSucceeds(ref.set(statusDoc({ batteryPct: 0 })));
  await assertSucceeds(ref.set(statusDoc({ batteryPct: 100, placeId: 'x'.repeat(40) })));
  await assertSucceeds(ref.set(statusDoc({ batteryPct: null, placeId: null })));
});

s.tc('S16b', 'photo : vide, clé inconnue, createdAt client ou déjà expirée refusées', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/photos/bob`);
  await assertFails(ref.set(photoDoc({ jpeg: bytes(0) })));
  await assertFails(ref.set(photoDoc({ legende: 'x' })));
  await assertFails(ref.set(photoDoc({ createdAt: inMs(-HOUR) })));
  await assertFails(ref.set(photoDoc({ expiresAt: inMs(-HOUR) })));
  await assertSucceeds(ref.set(photoDoc({ jpeg: bytes(1) })));
});

// --- Lieux -------------------------------------------------------------------------------------------------------

const seedDave = (seedMiff) =>
  seedMiff({ miffId: ID.M3, name: 'Premium', code: CODE.M3, ownerUid: 'dave', members: [{ uid: 'dave', role: 'owner', sharing: true }] });

s.tc('P9b', 'lieu : rayon non entier, latitude/longitude trop basses, adresse longue, priorité invalide, type ou clé inconnus refusés', async ({ db, seedMiff }) => {
  const alice = db('alice').doc(P1);
  await assertFails(alice.update({ radiusM: 100.5, updatedAt: serverTs() }));
  await assertFails(alice.update({ lat: -95, updatedAt: serverTs() }));
  await assertFails(alice.update({ lng: -200, updatedAt: serverTs() }));
  await assertFails(alice.update({ address: 'x'.repeat(201), updatedAt: serverTs() }));
  for (const priority of [-1, 1000, 1.5, 'haute']) await assertFails(alice.update({ priority, updatedAt: serverTs() }));
  // Bornes incluses : acceptées.
  await assertSucceeds(
    alice.update({ lat: -90, lng: -180, radiusM: 2000, priority: 999, address: 'x'.repeat(200), updatedAt: serverTs() }),
  );
  await assertSucceeds(alice.update({ lat: 90, lng: 180, radiusM: 50, priority: 0, updatedAt: serverTs() }));
  // Type et clés à la création : une gratuite est déjà limitée à p1/trusted par la règle freemium, donc test avec la premium.
  await seedDave(seedMiff);
  const places = db('dave').collection(`miffs/${ID.M3}/places`);
  await assertFails(places.doc().set(placeInput('dave', { type: 'banane' })));
  await assertFails(places.doc().set(placeInput('dave', { coordonnees: [1, 2] })));
  await assertSucceeds(places.doc().set(placeInput('dave', { type: 'risk' })));
});

s.tc('P11b', "lieu : createdBy d'un autre utilisateur à la création refusé", async ({ db, admin }) => {
  await admin((f) => f.doc(P1).delete());
  await assertFails(db('alice').doc(P1).set(placeInput('alice', { createdBy: 'bob' })));
  await assertSucceeds(db('alice').doc(P1).set(placeInput('alice')));
});

s.tc('P12b', 'lieu : createdAt et updatedAt fournis par le client refusés (création de p1 et mise à jour)', async ({ db, admin }) => {
  await admin((f) => f.doc(P1).delete());
  const ref = db('alice').doc(P1);
  await assertFails(ref.set(placeInput('alice', { createdAt: inMs(-HOUR) })));
  await assertFails(ref.set(placeInput('alice', { updatedAt: inMs(-HOUR) })));
  await assertSucceeds(ref.set(placeInput('alice')));
  await assertFails(ref.update({ name: 'Autre', updatedAt: inMs(-HOUR) }));
  await assertSucceeds(ref.update({ name: 'Autre', updatedAt: serverTs() }));
});

s.tc('P2b', "lieu premium : identifiant hors format (tiret, 21 caractères) refusé", async ({ db, seedMiff }) => {
  await seedDave(seedMiff);
  const places = `miffs/${ID.M3}/places`;
  await assertFails(db('dave').doc(`${places}/mauvais-id`).set(placeInput('dave')));
  await assertFails(db('dave').doc(`${places}/${'x'.repeat(21)}`).set(placeInput('dave')));
  await assertSucceeds(db('dave').doc(`${places}/${'x'.repeat(20)}`).set(placeInput('dave')));
});

// --- Création et code --------------------------------------------------------------------------------------------

s.tc('C3b', "création d'une Miff sans l'index memberships du créateur : refusé", async ({ db }) => {
  await assertFails(commit(db('eve'), without(createMiffOps({ uid: 'eve' }), 'index')));
  await assertSucceeds(commit(db('eve'), all(createMiffOps({ uid: 'eve' }))));
});

s.tc('C13b', 'createdAt du code secret fourni par le client : refusé', async ({ db }) => {
  const ops = createMiffOps({ uid: 'eve' });
  ops.code.data.createdAt = inMs(-HOUR);
  await assertFails(commit(db('eve'), all(ops)));
});

s.tc('C14b', 'un code existant ne se « retarge » pas vers une autre Miff (seul miffName est modifiable)', async ({ db }) => {
  // Si miffId pouvait changer, la créatrice de M1 ferait ouvrir la Miff d'une autre (M2) par son propre code.
  const code = db('alice').doc(`joinCodes/${CODE.M1}`);
  await assertFails(code.update({ miffId: ID.M2 }));
  await assertFails(code.update({ createdAt: serverTs() }));
  await assertFails(code.update({ proprietaire: 'alice' }));
  await assertSucceeds(
    commit(db('alice'), [update(M1, { name: 'Nouveau nom' }), update(`joinCodes/${CODE.M1}`, { miffName: 'Nouveau nom' })]),
  );
});

s.tc('M6b', 'renommer la Miff avec un nom vide ou de plus de 40 caractères : refusé', async ({ db }) => {
  const ref = db('alice').doc(M1);
  await assertFails(ref.update({ name: '' }));
  await assertFails(ref.update({ name: 'x'.repeat(41) }));
  await assertSucceeds(ref.update({ name: 'x'.repeat(40) }));
});

// --- Profil et appareils -----------------------------------------------------------------------------------------

s.tc('U1b', 'profil : createdAt et termsAcceptedAt fournis par le client refusés (création et mise à jour) ; termsAcceptedAt figé ensuite', async ({ db, admin }) => {
  await admin((f) => f.doc('users/eve').delete()); // eve a un profil dans le jeu de départ (AUTH-B) : on teste la création
  const eve = db('eve').doc('users/eve');
  await assertFails(eve.set(userDoc({ termsAcceptedAt: inMs(-HOUR) })));
  await assertFails(eve.set(userDoc({ createdAt: inMs(-HOUR) })));
  await assertSucceeds(eve.set(userDoc()));
  await assertFails(eve.update({ termsAcceptedAt: inMs(-HOUR) }));
  // AUTH-B (décision PM du 2026-10-23) : (a) est figé après la création, même avec l'heure serveur.
  await assertFails(eve.update({ termsAcceptedAt: serverTs() }));
});

s.tc('U2b', 'profil : nom vide, version des conditions vide ou trop longue refusés (création et mise à jour)', async ({ db, admin }) => {
  await admin((f) => f.doc('users/eve').delete()); // eve a un profil dans le jeu de départ (AUTH-B) : on teste la création
  const eve = db('eve').doc('users/eve');
  await assertFails(eve.set(userDoc({ lastName: '' })));
  await assertFails(eve.set(userDoc({ termsVersion: '' })));
  await assertFails(eve.set(userDoc({ termsVersion: 'x'.repeat(21) })));
  await assertSucceeds(eve.set(userDoc()));
  await assertFails(eve.update({ lastName: '' }));
  await assertFails(eve.update({ termsVersion: '' }));
  await assertFails(eve.update({ termsVersion: 'x'.repeat(21) }));
  // AUTH-B : termsVersion est figé après la création (décision PM), même avec une valeur valide.
  await assertFails(eve.update({ termsVersion: 'v2' }));
});

s.tc('A5b', "créer le profil d'un autre utilisateur : refusé", async ({ db }) => {
  await assertFails(db('eve').doc('users/mallory').set(userDoc()));
});

s.tc('U5b', "appareil : clé inconnue, jeton vide ou trop long, date client, identifiant hors format refusés", async ({ db }) => {
  const device = (over = {}) => ({ token: 'jeton-de-test', platform: 'ios', updatedAt: serverTs(), ...over });
  const eve = (id) => db('eve').doc(`users/eve/devices/${id}`);
  await assertFails(eve('d1').set(device({ langue: 'fr' })));
  await assertFails(eve('d1').set(device({ token: '' })));
  await assertFails(eve('d1').set(device({ token: 'x'.repeat(513) })));
  await assertFails(eve('d1').set(device({ updatedAt: inMs(-HOUR) })));
  await assertFails(eve('a.b').set(device()));
  await assertFails(eve('x'.repeat(65)).set(device()));
  await assertSucceeds(eve('d1').set(device({ token: 'x'.repeat(512) })));
  await assertSucceeds(eve('x'.repeat(64)).set(device()));
});

s.tc('U6b', "écrire un jeton d'appareil pour un autre utilisateur : refusé", async ({ db }) => {
  await assertFails(db('eve').doc('users/alice/devices/d1').set({ token: 'jeton-de-test', platform: 'ios', updatedAt: serverTs() }));
  await assertSucceeds(db('alice').doc('users/alice/devices/d1').set({ token: 'jeton-de-test', platform: 'ios', updatedAt: serverTs() }));
});
