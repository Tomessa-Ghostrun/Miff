// Plan de test, section 28 : ZONE DE DANGER DEMANDÉE SANS MIFF+ (LIEU-DANGER-REQ, ticket docs/tickets/lieu-danger-demande-gratuite.md, décision du PM du 2026-11-17), cas LD1 à LD8.
// Règles NON DÉPLOYÉES. Matrice de bout en bout : DEMANDER `risk` (tout membre non créateur, gratuit ou Miff+), ACCEPTER (Miff+ du CRÉATEUR) et DÉCLARER
// directement (Miff+ du CRÉATEUR). Comptes du jeu de départ : alice (créatrice gratuite de M1, sans document d'abonnement), bob et carol (membres gratuits, sans
// document d'abonnement), dave (Miff+ actif, créateur de M3 dans ces tests), frank (Miff+ EXPIRÉ, créateur de M4), eve et zoe (étrangères à M1).
import {
  ID, CODE, T0, suite, assertFails, assertSucceeds, commit, del, setMerge, serverTs, profile, sharingOff, placeInput, placeSeed,
} from '../helpers/fixtures.js';
import {
  reqPath, counterPath, requestInput, counterSeed, createRequestOps, withdrawRequestOps, acceptRequestOps, seedRequests,
} from '../helpers/requests.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const go = (h, uid, ops) => commit(h.db(uid), Object.values(ops));
const ask = (h, uid, k = 1, over = {}) => go(h, uid, createRequestOps({ uid, k, data: requestInput(uid, { type: 'risk', ...over }) }));
const read = async (h, path) => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};
const pending = async (h, miffId = ID.M1) => (await read(h, counterPath(miffId)))?.pending ?? null;
const addMember = (h, uid, miffId = ID.M1, code = CODE.M1) =>
  h.admin((f) => f.doc(`miffs/${miffId}/members/${uid}`).set({ role: 'member', ...profile(uid), joinedAt: T0, joinCode: code, sharing: sharingOff() }));
// Miffs dont le créateur est Miff+ (dave, M3) ou Miff+ EXPIRÉ (frank, M4) ; bob et carol y sont membres gratuits.
const seedPremiumMiffs = async (h) => {
  await h.seedMiff({
    miffId: ID.M3, name: 'Voyageurs', code: CODE.M3, ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: false, key: ID.M3 }, { uid: 'bob', role: 'member', sharing: false, key: 'zz' }],
  });
  await h.seedMiff({
    miffId: ID.M4, name: 'Anciens', code: CODE.M4, ownerUid: 'frank',
    members: [{ uid: 'frank', role: 'owner', sharing: false, key: ID.M4 }, { uid: 'carol', role: 'member', sharing: false, key: 'yy' }],
  });
};

s.tc('LD1', 'DEMANDER : un membre GRATUIT (sans document d\'abonnement) demande une zone de danger : accepté, demande relue en `risk`, compteur à 1 puis 2 ; un Miff+ et un Miff+ EXPIRÉ membres aussi', async (h) => {
  await assertSucceeds(ask(h, 'bob', 1));
  if ((await pending(h)) !== 1) throw new Error('compteur attendu à 1');
  const d = await read(h, reqPath(ID.M1, 'bob', 1));
  if (d.type !== 'risk' || d.requestedBy !== 'bob' || Object.keys(d).length !== 9) throw new Error('demande `risk` mal relue');
  await assertSucceeds(ask(h, 'carol', 1));
  if ((await pending(h)) !== 2) throw new Error('compteur attendu à 2');
  await addMember(h, 'dave');
  await addMember(h, 'frank');
  await assertSucceeds(ask(h, 'dave', 1)); // inchangé : un Miff+ demande toujours
  await assertSucceeds(ask(h, 'frank', 1)); // abonnement expiré : sans importance pour demander
  if ((await pending(h)) !== 4) throw new Error('compteur attendu à 4');
});

s.tc('LD2', 'DEMANDER : refusé pour le créateur (même Miff+), un non-membre, un ex-membre, un visiteur, un membre d\'une AUTRE Miff ; la liste fermée des types reste fermée', async (h) => {
  await assertFails(ask(h, 'alice', 1)); // créatrice gratuite
  await assertFails(ask(h, 'eve', 1)); // étrangère
  await assertFails(ask(h, 'zoe', 1)); // créatrice d'une autre Miff
  await assertFails(ask(h, 'dave', 1)); // Miff+ mais pas membre de M1
  await assertFails(commit(h.anon(), Object.values(createRequestOps({ uid: 'bob', data: requestInput('bob', { type: 'risk' }) }))));
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete());
  await assertFails(ask(h, 'bob', 1)); // ex-membre
  for (const type of ['danger', 'Risk', 'RISK', null, 1, ['risk'], '']) await assertFails(ask(h, 'carol', 1, { type }));
  if ((await pending(h)) !== null) throw new Error('aucun compteur ne devait être écrit');
  await assertSucceeds(ask(h, 'carol', 1)); // contrôle
});

s.tc('LD3', 'DEMANDER : quotas inchangés pour une zone de danger : 3 emplacements par membre (4e refusé), plafond de 10 par Miff (11e refusée), retrait puis nouvelle demande', async (h) => {
  await assertSucceeds(ask(h, 'bob', 1));
  await assertSucceeds(ask(h, 'bob', 2));
  await assertSucceeds(ask(h, 'bob', 3));
  await assertFails(ask(h, 'bob', 4)); // pas de 4e emplacement
  await h.admin((f) => f.doc(counterPath()).set(counterSeed(9)));
  await assertSucceeds(ask(h, 'carol', 1)); // la 10e
  if ((await pending(h)) !== 10) throw new Error('compteur attendu à 10');
  await assertFails(ask(h, 'carol', 2)); // la 11e
  await assertSucceeds(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 1 })));
  await assertSucceeds(ask(h, 'carol', 2)); // une place s'est libérée
  if ((await pending(h)) !== 10) throw new Error('compteur attendu à 10');
});

s.tc('LD4', 'ACCEPTER : un créateur GRATUIT ne peut pas accepter une demande `risk` (lot entier refusé : lieu non créé, demande et compteur inchangés) ; il peut refuser ; il accepte une demande trusted', async (h) => {
  await seedRequests(h, [['bob', 1], ['carol', 1]], { over: { type: 'risk' } });
  for (const placeId of ['p2', 'p3', h.db('alice').collection(`${M1}/places`).doc().id]) {
    await assertFails(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId, place: { type: 'risk' } })));
  }
  if ((await read(h, `${M1}/places/p2`)) !== null) throw new Error('aucun lieu ne devait être créé');
  if ((await read(h, reqPath(ID.M1, 'bob', 1))) === null) throw new Error('la demande devait rester');
  if ((await pending(h)) !== 2) throw new Error('le compteur devait rester à 2');
  // Refuser reste possible (suppression de la demande + compteur).
  await assertSucceeds(commit(h.db('alice'), [
    del(`${M1}/placeRequests/carol_1`),
    setMerge(counterPath(), { pending: 1, lastReqId: null, updatedAt: serverTs() }),
  ]));
  if ((await read(h, reqPath(ID.M1, 'carol', 1))) !== null) throw new Error('la demande refusée devait disparaître');
  // Une demande trusted s'accepte (p2 libre) : seule la zone de danger est réservée.
  await seedRequests(h, [['carol', 2]], { counter: 2 });
  await assertSucceeds(go(h, 'alice', acceptRequestOps({ from: 'carol', k: 2, placeId: 'p2', place: { type: 'trusted' }, left: 1 })));
});

s.tc('LD5', 'ACCEPTER : un créateur Miff+ actif accepte une demande `risk` émise par un membre GRATUIT (lieu `risk`, demande supprimée, compteur recalé) ; un créateur Miff+ EXPIRÉ, désactivé ou sans document est refusé', async (h) => {
  await seedPremiumMiffs(h);
  await seedRequests(h, [['bob', 1]], { miffId: ID.M3, over: { type: 'risk' } });
  await seedRequests(h, [['carol', 1]], { miffId: ID.M4, over: { type: 'risk' } });
  const auto = h.db('dave').collection(`miffs/${ID.M3}/places`).doc().id;
  await assertSucceeds(go(h, 'dave', acceptRequestOps({ owner: 'dave', from: 'bob', miffId: ID.M3, placeId: auto, place: { type: 'risk' }, left: 0 })));
  if ((await read(h, `miffs/${ID.M3}/places/${auto}`)).type !== 'risk') throw new Error('zone de danger attendue');
  if ((await read(h, reqPath(ID.M3, 'bob', 1))) !== null) throw new Error('la demande devait disparaître');
  if ((await pending(h, ID.M3)) !== 0) throw new Error('compteur attendu à 0');
  // Créateur Miff+ expiré : refusé, y compris sur un emplacement gratuit (p1) : la zone de danger reste réservée.
  for (const placeId of ['p1', h.db('frank').collection(`miffs/${ID.M4}/places`).doc().id]) {
    await assertFails(go(h, 'frank', acceptRequestOps({ owner: 'frank', from: 'carol', miffId: ID.M4, placeId, place: { type: 'risk' }, left: 0 })));
  }
  // Créateur désactivé (active=false), puis sans document d'abonnement.
  await seedRequests(h, [['bob', 2]], { miffId: ID.M3, counter: 1, over: { type: 'risk' } });
  await h.admin((f) => f.doc('entitlements/dave').update({ active: false }));
  await assertFails(go(h, 'dave', acceptRequestOps({ owner: 'dave', from: 'bob', miffId: ID.M3, k: 2, placeId: h.db('dave').collection(`miffs/${ID.M3}/places`).doc().id, place: { type: 'risk' }, left: 0 })));
  await h.admin((f) => f.doc('entitlements/dave').delete());
  await assertFails(go(h, 'dave', acceptRequestOps({ owner: 'dave', from: 'bob', miffId: ID.M3, k: 2, placeId: h.db('dave').collection(`miffs/${ID.M3}/places`).doc().id, place: { type: 'risk' }, left: 0 })));
  if ((await read(h, reqPath(ID.M3, 'bob', 2))) === null) throw new Error('la demande devait rester');
});

s.tc('LD6', 'DÉCLARER directement : un créateur GRATUIT ne crée pas de lieu `risk` (p1..p3, identifiant libre, set complet) ni ne passe un lieu à `risk` ; un créateur Miff+ le fait ; un membre jamais', async (h) => {
  const alice = h.db('alice');
  await assertFails(alice.doc(`${M1}/places/p2`).set(placeInput('alice', { type: 'risk' })));
  await assertFails(alice.doc(`${M1}/places/p3`).set(placeInput('alice', { type: 'risk' })));
  await assertFails(alice.doc(`${M1}/places/${alice.collection(`${M1}/places`).doc().id}`).set(placeInput('alice', { type: 'risk' })));
  await assertFails(alice.doc(`${M1}/places/p1`).update({ type: 'risk', updatedAt: serverTs() })); // trusted -> risk
  await assertFails(alice.doc(`${M1}/places/p1`).set({ ...placeSeed(), type: 'risk', createdAt: serverTs(), updatedAt: serverTs() }));
  await assertSucceeds(alice.doc(`${M1}/places/p2`).set(placeInput('alice'))); // contrôle : trusted gratuit
  await seedPremiumMiffs(h);
  const dave = h.db('dave');
  const auto = dave.collection(`miffs/${ID.M3}/places`).doc().id;
  await assertSucceeds(dave.doc(`miffs/${ID.M3}/places/${auto}`).set(placeInput('dave', { type: 'risk' })));
  await assertSucceeds(dave.doc(`miffs/${ID.M3}/places/${auto}`).update({ type: 'trusted', updatedAt: serverTs() }));
  await assertSucceeds(dave.doc(`miffs/${ID.M3}/places/${auto}`).update({ type: 'risk', updatedAt: serverTs() })); // passage à risk : Miff+
  await assertFails(h.db('bob').doc(`miffs/${ID.M3}/places/zzzBob`).set(placeInput('bob', { type: 'risk' }))); // un membre n'écrit jamais un lieu
  await assertFails(h.db('bob').doc(`miffs/${ID.M3}/places/${auto}`).update({ name: 'X', updatedAt: serverTs() }));
});

s.tc('LD7', 'PERTE DU MIFF+ du créateur : ses lieux `risk` existants restent modifiables (nom, rayon), supprimables et repassent à trusted, mais ne repassent jamais à `risk` ; ses demandes `risk` en attente restent lisibles et refusables', async (h) => {
  await seedPremiumMiffs(h);
  const place = `miffs/${ID.M4}/places/zoneHeritee`;
  await h.admin((f) => f.doc(place).set(placeSeed({ createdBy: 'frank', type: 'risk' }))); // posée du temps de son Miff+
  await seedRequests(h, [['carol', 1]], { miffId: ID.M4, over: { type: 'risk' } });
  const frank = h.db('frank');
  await assertSucceeds(frank.doc(place).update({ name: 'Renomme', radiusM: 200, updatedAt: serverTs() })); // le type ne change pas : aucun droit requis
  await assertSucceeds(frank.doc(place).update({ type: 'trusted', updatedAt: serverTs() }));
  await assertFails(frank.doc(place).update({ type: 'risk', updatedAt: serverTs() })); // plus de retour à risk sans Miff+
  await assertSucceeds(frank.doc(place).delete());
  await assertSucceeds(frank.collection(`miffs/${ID.M4}/placeRequests`).get()); // il lit encore ses demandes
  await assertSucceeds(commit(frank, [del(reqPath(ID.M4, 'carol', 1)), setMerge(counterPath(ID.M4), { pending: 0, lastReqId: null, updatedAt: serverTs() })]));
});

s.tc('LD8', 'AUCUN AUTRE CHAMP : une demande `risk` ne se modifie jamais (type, nom, coordonnées, requestedBy, createdAt), même par son auteur gratuit ou le créateur ; clés en trop refusées ; lecture inchangée (créateur : toutes ; demandeur : les siennes ; autres membres : rien)', async (h) => {
  await assertSucceeds(ask(h, 'bob', 1));
  const path = reqPath(ID.M1, 'bob', 1);
  for (const patch of [{ type: 'trusted' }, { type: 'risk' }, { name: 'Autre' }, { lat: 1 }, { radiusM: 300 }, { requestedBy: 'carol' }, { createdAt: serverTs() }, { status: 'accepted' }]) {
    await assertFails(h.db('bob').doc(path).update(patch));
    await assertFails(h.db('alice').doc(path).update(patch));
  }
  await assertFails(h.db('bob').doc(path).set(requestInput('bob', { type: 'risk', name: 'Autre' }))); // recréation = modification
  for (const extra of [{ status: 'pending' }, { dangerLevel: 3 }, { reason: 'x' }, { requestedByName: 'Bob' }]) await assertFails(ask(h, 'bob', 2, extra));
  const d = await read(h, path);
  if (d.type !== 'risk' || d.name !== 'Chez Mamie') throw new Error('la demande ne devait pas avoir changé');
  // Lecture : inchangée.
  await assertSucceeds(h.db('alice').doc(path).get());
  await assertSucceeds(h.db('bob').doc(path).get());
  await assertSucceeds(h.db('bob').collection(`${M1}/placeRequests`).where('requestedBy', '==', 'bob').get());
  await assertFails(h.db('carol').doc(path).get()); // un autre membre ne voit rien
  await assertFails(h.db('carol').collection(`${M1}/placeRequests`).where('requestedBy', '==', 'bob').get());
  await assertFails(h.db('eve').doc(path).get());
  await assertFails(h.anon().doc(path).get());
});
