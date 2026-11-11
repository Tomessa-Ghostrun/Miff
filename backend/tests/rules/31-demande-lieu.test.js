// Plan de test, section 25 : DEMANDE D'AJOUT D'UN LIEU (LIEU-REQ-A, ticket docs/tickets/demande-ajout-lieu.md, décisions du PM D1 à D9 du 2026-11-11), cas RQ1 à RQ24.
// Règles NON DÉPLOYÉES. Chemins : miffs/{id}/placeRequests/{uid}_{1..3} et le compteur miffs/{id}/meta/placeRequests (plafond de 10 par Miff).
// Comptes du jeu de départ : alice (créatrice gratuite de M1), bob et carol (membres gratuits), dave (Miff+ actif, ajouté comme MEMBRE de M1 quand il faut),
// frank (Miff+ EXPIRÉ), eve (étrangère), zoe (créatrice de M2). Les lots sont ceux d'un client (tests/helpers/requests.js) : `set` avec fusion et
// `increment(±1)` pour le compteur.
import {
  ID, CODE, T0, DAY, suite, assertFails, assertSucceeds, commit, set, del, setMerge, Timestamp, FieldValue, serverTs, inMs, profile, sharingOff,
  leaveOps, removeMemberOps, placeInput, placeSeed,
} from '../helpers/fixtures.js';
import {
  reqPath, counterPath, reqId, requestInput, requestSeed, counterMove, counterOwner, counterSeed, createRequestOps, withdrawRequestOps, declineRequestOps,
  acceptRequestOps, seedRequests, addPremiumMember,
} from '../helpers/requests.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const go = (h, uid, ops) => commit(h.db(uid), Object.values(ops));
const create = (h, uid, k = 1, over = {}) => go(h, uid, createRequestOps({ uid, k, data: requestInput(uid, over) }));
const read = async (h, path) => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};
const pending = async (h, miffId = ID.M1) => (await read(h, counterPath(miffId)))?.pending ?? null;
const addMember = (h, uid) =>
  h.admin((f) => f.doc(`${M1}/members/${uid}`).set({ role: 'member', ...profile(uid), joinedAt: T0, joinCode: CODE.M1, sharing: sharingOff() }));
const ids = (snap) => snap.docs.map((d) => d.id).sort();
const own = (h, uid) => h.db(uid).collection(`${M1}/placeRequests`).where('requestedBy', '==', uid).get();


s.tc('RQ1', 'S1 : un membre non créateur crée ses 3 emplacements {uid}_1 à _3, le compteur passe à 1, 2, 3 (création puis incréments) ; un second membre : 4', async (h) => {
  await assertSucceeds(create(h, 'bob', 1)); // le compteur n'existe pas : il est créé à 1
  if ((await pending(h)) !== 1) throw new Error('compteur attendu à 1');
  await assertSucceeds(create(h, 'bob', 2));
  await assertSucceeds(create(h, 'bob', 3));
  await assertSucceeds(create(h, 'carol', 1));
  if ((await pending(h)) !== 4) throw new Error('compteur attendu à 4');
  const d = await read(h, reqPath(ID.M1, 'bob', 1));
  if (d.requestedBy !== 'bob' || d.type !== 'trusted' || !(d.createdAt instanceof Timestamp) || Object.keys(d).length !== 9 || d.category !== 'relative') throw new Error('demande mal relue');
  const c = await read(h, counterPath());
  if (c.lastReqId !== 'carol_1' || !(c.updatedAt instanceof Timestamp)) throw new Error('compteur : lastReqId / updatedAt mal écrits');
});

s.tc('RQ2', 'S1 : bornes ACCEPTÉES (adresse nulle, nom de 60 caractères, rayon 50 et 2000, lat ±90, lng ±180, 5 décimales) ; les mêmes documents valides sur les 3 emplacements', async (h) => {
  await assertSucceeds(create(h, 'bob', 1, { address: null, name: 'N'.repeat(60), radiusM: 50 }));
  await assertSucceeds(create(h, 'bob', 2, { lat: 90, lng: 180, radiusM: 2000, address: 'A'.repeat(200) }));
  await assertSucceeds(create(h, 'bob', 3, { lat: -90, lng: -180, name: 'N', radiusM: 100 }));
  await assertSucceeds(create(h, 'carol', 1, { lat: 48.85661, lng: 2.35222 }));
});

s.tc('RQ3', 'S1 : forme refusée (clé en trop dont `status`, clé manquante, nom, type, adresse, coordonnées NaN / infinies / texte, rayon, createdAt du client ou passé ou futur) ; contrôle valide', async (h) => {
  const missing = (key) => {
    const d = requestInput('bob');
    delete d[key];
    return go(h, 'bob', createRequestOps({ uid: 'bob', data: d }));
  };
  for (const key of ['requestedBy', 'name', 'type', 'category', 'address', 'lat', 'lng', 'radiusM', 'createdAt']) await assertFails(missing(key));
  const bad = [
    { status: 'pending' }, { message: 'coucou' }, { requestedByName: 'Bob' }, { priority: null }, { createdBy: 'bob' }, { updatedAt: serverTs() },
    { name: '' }, { name: 'N'.repeat(61) }, { name: 42 }, { name: null }, { name: ['a'] },
    { category: 'inconnue' }, { category: 'Home' }, { category: 'HOME' }, { category: '' }, { category: null }, { category: 1 }, { category: ['home'] }, { category: 'home ' },
    { type: 'danger' }, { type: 'Trusted' }, { type: null }, { type: 1 }, { type: ['trusted'] },
    { address: 'A'.repeat(201) }, { address: 42 }, { address: true },
    { lat: 90.0001 }, { lat: -90.1 }, { lat: NaN }, { lat: Infinity }, { lat: '45.7' }, { lat: null },
    { lng: 180.0001 }, { lng: -181 }, { lng: NaN }, { lng: -Infinity }, { lng: '4.8' }, { lng: null },
    { radiusM: 49 }, { radiusM: 2001 }, { radiusM: 100.5 }, { radiusM: '100' }, { radiusM: null }, { radiusM: -150 },
    { createdAt: Timestamp.now() }, { createdAt: T0 }, { createdAt: inMs(60_000) }, { createdAt: null }, { createdAt: '2026-11-11' },
    { requestedBy: 'carol' }, { requestedBy: null }, { requestedBy: '' },
  ];
  for (const over of bad) await assertFails(create(h, 'bob', 1, over));
  await assertSucceeds(create(h, 'bob', 1)); // contrôle
});

s.tc('RQ4', 'S1 : identifiant d\'emplacement : _4, _0, _01, sans suffixe, suffixe collé, identifiant automatique, emplacement d\'un autre (usurpation de l\'emplacement ET de requestedBy) refusés', async (h) => {
  const at = (uid, id, data, counterId = id) =>
    commit(h.db(uid), [set(`${M1}/placeRequests/${id}`, data), setMerge(counterPath(), counterMove(counterId, 1))]);
  for (const id of ['bob_4', 'bob_0', 'bob_01', 'bob_', 'bob', 'bob_1x', 'xbob_1', ' bob_1', 'bobb_1', 'bob_1_1', 'BOB_1', 'bob_a']) {
    await assertFails(at('bob', id, requestInput('bob')));
  }
  await assertFails(at('bob', 'carol_1', requestInput('carol'))); // emplacement de carol, requestedBy de carol
  await assertFails(at('bob', 'carol_1', requestInput('bob'))); // emplacement de carol, requestedBy de bob
  await assertFails(at('bob', 'bob_1', requestInput('bob', { requestedBy: 'carol' }))); // mon emplacement, requestedBy usurpé
  await assertFails(at('bob', 'alice_1', requestInput('alice'))); // emplacement du créateur
  await assertFails(commit(h.db('bob'), [set(`${M1}/placeRequests/${h.db('bob').collection(`${M1}/placeRequests`).doc().id}`, requestInput('bob')), setMerge(counterPath(), counterMove('bob_1', 1))]));
  await assertSucceeds(at('bob', 'bob_3', requestInput('bob'))); // contrôle
});

s.tc('RQ5', 'S2 : ne peuvent PAS créer : le créateur (alice, même sur son emplacement), un non-membre (eve, zoe créatrice d\'une autre Miff, dave), un ex-membre, un visiteur', async (h) => {
  await assertFails(create(h, 'alice', 1));
  await assertFails(create(h, 'eve', 1));
  await assertFails(create(h, 'zoe', 1));
  await assertFails(create(h, 'dave', 1));
  await assertFails(commit(h.anon(), Object.values(createRequestOps({ uid: 'bob' }))));
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete()); // bob n'est plus membre
  await assertFails(create(h, 'bob', 1));
  if ((await pending(h)) !== null) throw new Error('aucun compteur ne devait être écrit');
  await assertSucceeds(create(h, 'carol', 1)); // contrôle : une membre
});

s.tc('RQ6', 'S2 : Miff supprimée ou inexistante : aucune demande ne peut être créée', async (h) => {
  await h.admin((f) => f.doc(`miffs/${ID.M1}`).delete()); // les fiches de membres survivent (sous-collections non effacées)
  await assertFails(create(h, 'bob', 1));
  await assertFails(commit(h.db('bob'), Object.values(createRequestOps({ uid: 'bob', miffId: ID.M3 })))); // identifiant jamais créé
});

s.tc('RQ7', 'S3 : plafond de 10 par Miff : compteur à 10 : la 11e est refusée ; à 9 : acceptée ; puis refusée ; après un retrait : acceptée', async (h) => {
  await h.admin((f) => f.doc(counterPath()).set(counterSeed(10)));
  await assertFails(create(h, 'bob', 1));
  await h.admin((f) => f.doc(counterPath()).set(counterSeed(9)));
  await assertSucceeds(create(h, 'bob', 1));
  if ((await pending(h)) !== 10) throw new Error('compteur attendu à 10');
  await assertFails(create(h, 'carol', 1));
  await assertFails(create(h, 'bob', 2));
  await assertSucceeds(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 1 })));
  if ((await pending(h)) !== 9) throw new Error('compteur attendu à 9');
  await assertSucceeds(create(h, 'carol', 1));
  if ((await pending(h)) !== 10) throw new Error('compteur attendu à 10');
});

s.tc('RQ8', 'S3 : compteur incohérent (écriture par un membre) : +1 sans création, -1 sans suppression, valeur absolue fausse, remise à zéro, +2, 0, -1 à la création, demande sans compteur, lastReqId d\'une autre demande ou nul', async (h) => {
  await seedRequests(h, [['bob', 1], ['carol', 1]]); // compteur à 2
  const alone = (uid, data) => commit(h.db(uid), [setMerge(counterPath(), data)]);
  await assertFails(alone('bob', counterMove('bob_2', 1))); // +1 pour une demande qui n'existe pas
  await assertFails(alone('bob', counterMove('bob_1', 1))); // +1 pour une demande déjà là (aucune création)
  await assertFails(alone('bob', counterMove('bob_1', -1))); // -1 sans suppression de la demande
  await assertFails(alone('bob', counterMove('bob_1', 1, { pending: 0 }))); // remise à zéro
  await assertFails(alone('bob', { pending: 1, lastReqId: 'bob_1', updatedAt: serverTs() })); // valeur absolue sans rien créer
  await assertFails(alone('bob', { pending: 10, lastReqId: 'bob_1', updatedAt: serverTs() })); // gonfler le compteur : bloquer les autres
  await assertFails(alone('bob', counterOwner(0))); // lastReqId nul : réservé au créateur
  await assertFails(alone('bob', counterMove('carol_1', -1))); // la demande d'une autre
  // Création avec un mauvais compteur
  const two = (data) => commit(h.db('bob'), [set(reqPath(ID.M1, 'bob', 2), requestInput('bob')), setMerge(counterPath(), data)]);
  await assertFails(two(counterMove('bob_2', 2)));
  await assertFails(two(counterMove('bob_2', 0)));
  await assertFails(two(counterMove('bob_2', -1)));
  await assertFails(two(counterMove('bob_1', 1))); // lastReqId d'une autre demande (la mienne, déjà là)
  await assertFails(two(counterMove('carol_1', 1)));
  await assertFails(two(counterOwner(FieldValue.increment(1)))); // lastReqId nul
  await assertFails(two({ pending: FieldValue.increment(1), lastReqId: 'bob_2' })); // updatedAt manquant
  await assertFails(two(counterMove('bob_2', 1, { updatedAt: T0 }))); // heure du client
  await assertFails(two(counterMove('bob_2', 1, { extra: 1 })));
  await assertFails(two(counterMove('bob_2', 1, { pending: 1.5 })));
  await assertFails(two(counterMove('bob_2', 1, { pending: '3' })));
  await assertFails(commit(h.db('bob'), [set(reqPath(ID.M1, 'bob', 2), requestInput('bob'))])); // demande SANS compteur
  if ((await pending(h)) !== 2) throw new Error('le compteur ne devait pas avoir bougé');
});

s.tc('RQ9', 'S3 : le compteur est une SOMME vérifiée, pas un increment : une valeur absolue égale à ancienne valeur + 1 est acceptée avec la création ; deux demandes pour un seul +1 : refusé ; deux retraits pour un seul -1 : refusé', async (h) => {
  await seedRequests(h, [['bob', 1], ['carol', 1]]); // compteur à 2
  await assertFails(commit(h.db('bob'), [
    set(reqPath(ID.M1, 'bob', 2), requestInput('bob')), set(reqPath(ID.M1, 'bob', 3), requestInput('bob')), setMerge(counterPath(), counterMove('bob_2', 1)),
  ]));
  await assertFails(commit(h.db('bob'), [
    set(reqPath(ID.M1, 'bob', 2), requestInput('bob')), set(reqPath(ID.M1, 'bob', 3), requestInput('bob')), setMerge(counterPath(), counterMove('bob_3', 2)),
  ]));
  await assertSucceeds(commit(h.db('bob'), [
    set(reqPath(ID.M1, 'bob', 2), requestInput('bob')), setMerge(counterPath(), { pending: 3, lastReqId: 'bob_2', updatedAt: serverTs() }),
  ]));
  await seedRequests(h, [['bob', 3]], { counter: null });
  await assertFails(commit(h.db('bob'), [ // deux retraits, un seul -1
    del(reqPath(ID.M1, 'bob', 2)), del(reqPath(ID.M1, 'bob', 3)), setMerge(counterPath(), counterMove('bob_2', -1)),
  ]));
  if ((await pending(h)) !== 3) throw new Error('compteur attendu à 3');
});

s.tc('RQ10', 'S3 : « périmé » : un lastReqId resté d\'un lot précédent ne vaut rien (updatedAt doit être l\'heure du lot) ; un compteur écrit puis une demande créée dans un AUTRE lot : refusé', async (h) => {
  await h.admin((f) => f.doc(counterPath()).set(counterSeed(1, 'bob_1'))); // le dernier lot était pour bob_1
  await assertFails(commit(h.db('bob'), [set(reqPath(ID.M1, 'bob', 1), requestInput('bob'))])); // demande sans toucher le compteur
  await assertSucceeds(commit(h.db('bob'), Object.values(createRequestOps({ uid: 'bob', k: 1 })))); // contrôle avec le compteur dans le lot
  await assertFails(commit(h.db('bob'), [del(reqPath(ID.M1, 'bob', 1))])); // retrait sans compteur : lastReqId = bob_1 depuis le lot précédent
  await h.admin((f) => f.doc(counterPath()).set(counterSeed(2, 'bob_1')));
  await assertFails(commit(h.db('bob'), [del(reqPath(ID.M1, 'bob', 1))]));
});

s.tc('RQ11', 'S3 : compteur recalé trop bas (0) alors que des demandes existent : le retrait par increment(-1) est refusé (le résultat serait -1, hors 0..10) ; le repli à la valeur absolue 0 est accepté (jamais négatif) ; le créateur recale par une valeur absolue', async (h) => {
  await seedRequests(h, [['bob', 1], ['carol', 1]], { counter: 0 });
  await assertFails(go(h, 'bob', withdrawRequestOps({ uid: 'bob' }))); // increment(-1) depuis 0 : -1
  await assertFails(go(h, 'bob', withdrawRequestOps({ uid: 'bob', counter: { pending: -1, lastReqId: 'bob_1', updatedAt: serverTs() } })));
  await assertFails(go(h, 'bob', withdrawRequestOps({ uid: 'bob', counter: { pending: 1, lastReqId: 'bob_1', updatedAt: serverTs() } }))); // monter en retirant
  await assertSucceeds(go(h, 'bob', withdrawRequestOps({ uid: 'bob', counter: { pending: 0, lastReqId: 'bob_1', updatedAt: serverTs() } }))); // repli du client
  if ((await pending(h)) !== 0) throw new Error('compteur attendu à 0');
  if ((await read(h, reqPath(ID.M1, 'bob', 1))) !== null) throw new Error('la demande devait être supprimée');
  // Le créateur : increment(-1) depuis 0 refusé, valeur absolue (nombre de demandes restantes lu dans sa liste) acceptée.
  await assertFails(go(h, 'alice', declineRequestOps({ from: 'carol', k: 1 })));
  await assertSucceeds(go(h, 'alice', declineRequestOps({ from: 'carol', k: 1, left: 0 })));
});

s.tc('RQ12', 'S3 : lecture et écriture directe du compteur par un membre : get refusé, suppression refusée, création sur une Miff étrangère refusée ; le créateur le lit', async (h) => {
  await seedRequests(h, [['bob', 1]]);
  await assertFails(h.db('bob').doc(counterPath()).get());
  await assertFails(h.db('carol').doc(counterPath()).get());
  await assertFails(h.db('eve').doc(counterPath()).get());
  await assertFails(h.anon().doc(counterPath()).get());
  await assertSucceeds(h.db('alice').doc(counterPath()).get());
  await assertFails(h.db('bob').doc(counterPath()).delete());
  await assertFails(h.db('bob').collection(`${M1}/meta`).get()); // aucune liste
  await assertFails(h.db('alice').collection(`${M1}/meta`).get());
  await assertFails(h.db('bob').doc(`${M1}/meta/autre`).set({ pending: 0, lastReqId: null, updatedAt: serverTs() })); // aucun autre document `meta`
  await assertFails(h.db('alice').doc(`${M1}/meta/autre`).set({ pending: 0, lastReqId: null, updatedAt: serverTs() }));
  // Miff étrangère : eve n'est membre de rien, zoe est créatrice de M2 seulement.
  await assertFails(commit(h.db('eve'), [setMerge(counterPath(ID.M2), counterMove('eve_1', 1))]));
  await assertFails(commit(h.db('zoe'), [setMerge(counterPath(), counterOwner(0))]));
});

s.tc('RQ13', 'S4 : `risk` : un membre gratuit est refusé, un Miff+ actif est accepté, un Miff+ EXPIRÉ ou désactivé est refusé ; `trusted` accepté pour tous', async (h) => {
  await addMember(h, 'dave');
  await addMember(h, 'frank');
  await assertFails(create(h, 'bob', 1, { type: 'risk' }));
  await assertFails(create(h, 'carol', 1, { type: 'risk' }));
  await assertFails(create(h, 'frank', 1, { type: 'risk' })); // expiré hier
  await assertSucceeds(create(h, 'dave', 1, { type: 'risk' }));
  for (const type of ['danger', 'Risk', 'RISK', null, 1, ['risk']]) await assertFails(create(h, 'dave', 2, { type })); // un Miff+ ne contourne pas la liste des types
  await assertSucceeds(create(h, 'frank', 1)); // trusted : aucun premium requis
  await assertSucceeds(create(h, 'bob', 1)); // trusted
  await h.admin((f) => f.doc('entitlements/dave').update({ active: false }));
  await assertFails(create(h, 'dave', 2, { type: 'risk' }));
  await assertSucceeds(create(h, 'dave', 2)); // trusted
  await h.admin((f) => f.doc('entitlements/dave').delete());
  await assertFails(create(h, 'dave', 3, { type: 'risk' }));
  if ((await pending(h)) !== 4) throw new Error('compteur attendu à 4');
});

s.tc('RQ14', 'S5 : lecture : le créateur lit toutes les demandes (liste et get) ; le demandeur les siennes seulement, par requête filtrée ; les autres membres, les non-membres, un visiteur rien', async (h) => {
  await seedRequests(h, [['bob', 1], ['bob', 2], ['carol', 1]]);
  const col = (uid) => h.db(uid).collection(`${M1}/placeRequests`);
  const all = await col('alice').get();
  if (ids(all).join() !== 'bob_1,bob_2,carol_1') throw new Error(`le créateur devait lire les 3 demandes : ${ids(all)}`);
  await assertSucceeds(h.db('alice').doc(reqPath(ID.M1, 'bob', 1)).get());
  if (ids(await own(h, 'bob')).join() !== 'bob_1,bob_2') throw new Error('bob devait lire ses 2 demandes');
  if (ids(await own(h, 'carol')).join() !== 'carol_1') throw new Error('carol devait lire sa demande');
  await assertSucceeds(h.db('bob').doc(reqPath(ID.M1, 'bob', 1)).get());
  await assertSucceeds(h.db('bob').doc(reqPath(ID.M1, 'bob', 3)).get()); // emplacement libre : introuvable, pas refusé
  await assertFails(h.db('bob').doc(reqPath(ID.M1, 'carol', 1)).get()); // la demande de carol
  await assertFails(h.db('bob').doc(reqPath(ID.M1, 'carol', 3)).get()); // l'emplacement (libre) de carol
  await assertFails(h.db('bob').doc(reqPath(ID.M1, 'alice', 1)).get());
  await assertFails(col('bob').get()); // liste sans filtre
  await assertFails(col('bob').where('requestedBy', '==', 'carol').get()); // filtre sur un autre
  await assertFails(col('bob').where('type', '==', 'trusted').get());
  await assertFails(col('bob').orderBy('createdAt').get());
  await assertFails(col('carol').where('requestedBy', 'in', ['carol', 'bob']).get());
  await assertFails(col('carol').get());
  for (const uid of ['eve', 'zoe', 'dave']) {
    await assertFails(col(uid).get());
    await assertFails(col(uid).where('requestedBy', '==', uid).get());
    await assertFails(h.db(uid).doc(reqPath(ID.M1, 'bob', 1)).get());
  }
  await assertFails(h.anon().collection(`${M1}/placeRequests`).get());
  await assertFails(h.anon().doc(reqPath(ID.M1, 'bob', 1)).get());
  await assertFails(h.db('bob').collectionGroup('placeRequests').get()); // pas de règle de groupe de collections : refus par défaut
  await assertFails(h.db('alice').collectionGroup('placeRequests').get());
});

s.tc('RQ15', 'S5 : membre retiré, Miff supprimée : plus aucune lecture (ni le demandeur, ni le créateur) ; une demande de 40 jours reste lisible (le masquage est côté client)', async (h) => {
  await seedRequests(h, [['bob', 1], ['carol', 1]]);
  await h.admin((f) => f.doc(reqPath(ID.M1, 'bob', 2)).set(requestSeed('bob', {}, 40)));
  await assertSucceeds(h.db('bob').doc(reqPath(ID.M1, 'bob', 2)).get()); // expirée : toujours lisible, aucune règle d'âge
  await assertSucceeds(h.db('alice').doc(reqPath(ID.M1, 'bob', 2)).get());
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete()); // bob n'est plus membre
  await assertFails(own(h, 'bob'));
  await assertFails(h.db('bob').doc(reqPath(ID.M1, 'bob', 1)).get());
  await assertSucceeds(own(h, 'carol')); // contrôle
  await h.admin((f) => f.doc(`miffs/${ID.M1}`).delete()); // Miff supprimée
  await assertFails(own(h, 'carol'));
  await assertFails(h.db('carol').doc(reqPath(ID.M1, 'carol', 1)).get());
  await assertFails(h.db('alice').collection(`${M1}/placeRequests`).get());
  await assertFails(h.db('alice').doc(reqPath(ID.M1, 'carol', 1)).get());
});

s.tc('RQ16', 'S6 : aucune modification d\'une demande (update, set sur l\'existante, avec ou sans compteur, par le demandeur, le créateur, un tiers)', async (h) => {
  await seedRequests(h, [['bob', 1]]);
  const path = reqPath(ID.M1, 'bob', 1);
  await assertFails(h.db('bob').doc(path).update({ name: 'Autre' }));
  await assertFails(h.db('bob').doc(path).update({ radiusM: 200 }));
  await assertFails(h.db('bob').doc(path).set(requestInput('bob'))); // « recréer » l'existante = modification
  await assertFails(commit(h.db('bob'), Object.values(createRequestOps({ uid: 'bob', k: 1 })))); // idem avec le compteur
  await assertFails(h.db('bob').doc(path).update({ type: 'risk' }));
  await assertFails(h.db('bob').doc(path).update({ requestedBy: 'carol' }));
  await assertFails(h.db('alice').doc(path).update({ name: 'Autre' })); // le créateur non plus
  await assertFails(h.db('alice').doc(path).set(requestInput('bob')));
  await assertFails(h.db('carol').doc(path).update({ name: 'Autre' }));
  await assertFails(h.db('eve').doc(path).update({ name: 'Autre' }));
  await assertFails(h.anon().doc(path).update({ name: 'Autre' }));
  const d = await read(h, path);
  if (d.name !== 'Chez Mamie' || d.radiusM !== 150) throw new Error('la demande ne devait pas avoir changé');
});

s.tc('RQ17', 'S7 : retrait : le demandeur retire SA demande avec le compteur -1 ; sans compteur, la demande d\'un autre, un tiers, un visiteur : refusés', async (h) => {
  await seedRequests(h, [['bob', 1], ['bob', 2], ['carol', 1]]); // compteur à 3
  await assertFails(commit(h.db('bob'), [del(reqPath(ID.M1, 'bob', 1))])); // sans compteur
  await assertFails(commit(h.db('bob'), Object.values(withdrawRequestOps({ uid: 'carol' })))); // la demande de carol, compteur de carol
  await assertFails(commit(h.db('bob'), [del(reqPath(ID.M1, 'carol', 1)), setMerge(counterPath(), counterMove('carol_1', -1))]));
  await assertFails(commit(h.db('eve'), [del(reqPath(ID.M1, 'bob', 1)), setMerge(counterPath(), counterMove('bob_1', -1))]));
  await assertFails(commit(h.db('dave'), [del(reqPath(ID.M1, 'bob', 1)), setMerge(counterPath(), counterMove('bob_1', -1))]));
  await assertFails(commit(h.anon(), [del(reqPath(ID.M1, 'bob', 1)), setMerge(counterPath(), counterMove('bob_1', -1))]));
  await assertFails(h.db('carol').doc(reqPath(ID.M1, 'bob', 1)).delete());
  await assertFails(h.db('bob').doc(reqPath(ID.M1, 'carol', 1)).delete());
  await assertFails(h.db('bob').doc(reqPath(ID.M1, 'bob', 1)).delete()); // seul
  await assertFails(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 1, counter: { pending: 0, lastReqId: 'bob_1', updatedAt: serverTs() } }))); // remise à zéro en retirant
  await assertFails(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 1, counter: { pending: 3, lastReqId: 'bob_1', updatedAt: serverTs() } }))); // retrait sans baisse
  if ((await pending(h)) !== 3) throw new Error('compteur attendu à 3');
  await assertSucceeds(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 1 })));
  if ((await pending(h)) !== 2) throw new Error('compteur attendu à 2');
  await assertSucceeds(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 2 })));
  await assertSucceeds(go(h, 'carol', withdrawRequestOps({ uid: 'carol', k: 1 })));
  if ((await pending(h)) !== 0) throw new Error('compteur attendu à 0');
  // L'emplacement libéré peut être reutilisé.
  await assertSucceeds(create(h, 'bob', 1));
});

s.tc('RQ18', 'S7 : suppression par le créateur : seul, avec compteur, en lot de plusieurs ; demande expirée (40 jours) ; demande d\'un ex-membre ; un autre membre ne peut pas supprimer', async (h) => {
  await seedRequests(h, [['bob', 1], ['bob', 2], ['carol', 1]]);
  await h.admin((f) => f.doc(reqPath(ID.M1, 'carol', 2)).set(requestSeed('carol', {}, 40)));
  await assertSucceeds(h.db('alice').doc(reqPath(ID.M1, 'bob', 1)).delete()); // seul : le créateur est maître de sa Miff
  await assertSucceeds(go(h, 'alice', declineRequestOps({ from: 'bob', k: 2 })));
  await assertSucceeds(commit(h.db('alice'), [del(reqPath(ID.M1, 'carol', 1)), del(reqPath(ID.M1, 'carol', 2)), setMerge(counterPath(), counterOwner(0))])); // incl. l'expirée
  if ((await pending(h)) !== 0) throw new Error('compteur attendu à 0');
  const left = await h.db('alice').collection(`${M1}/placeRequests`).get();
  if (!left.empty) throw new Error('plus aucune demande attendue');
  await assertFails(h.db('zoe').doc(reqPath(ID.M1, 'bob', 1)).delete()); // créatrice d'une autre Miff
});

s.tc('RQ19', 'S8 : accepter (créatrice gratuite) : lot lieu + suppression + compteur -1 accepté ; lieu relu tel que demandé, demande disparue, compteur recalé ; rien n\'est écrit pour le demandeur', async (h) => {
  await seedRequests(h, [['bob', 1], ['carol', 1]]); // alice a déjà p1
  await assertSucceeds(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId: 'p2' })));
  const place = await read(h, `${M1}/places/p2`);
  if (place.name !== 'Chez Mamie' || place.type !== 'trusted' || place.radiusM !== 150 || place.lat !== 45.764 || place.lng !== 4.8357 || place.createdBy !== 'alice' || place.category !== 'relative') {
    throw new Error('lieu mal relu');
  }
  if ((await read(h, reqPath(ID.M1, 'bob', 1))) !== null) throw new Error('la demande devait être supprimée');
  if ((await pending(h)) !== 1) throw new Error('compteur attendu à 1');
  if ((await own(h, 'bob')).size !== 0) throw new Error('rien ne doit rester pour le demandeur');
  await assertSucceeds(go(h, 'alice', acceptRequestOps({ from: 'carol', placeId: 'p3' })));
  if ((await pending(h)) !== 0) throw new Error('compteur attendu à 0');
});

s.tc('RQ20', 'S8 : accepter refusé en entier si le lieu est refusé par les règles EXISTANTES : 4e lieu gratuit (p3 déjà pris, identifiant automatique), zone de danger pour une créatrice gratuite ; la demande et le compteur restent', async (h) => {
  await seedRequests(h, [['bob', 1]]);
  await h.admin(async (f) => {
    await f.doc(`${M1}/places/p2`).set(placeSeed({ createdBy: 'alice' }));
    await f.doc(`${M1}/places/p3`).set(placeSeed({ createdBy: 'alice' }));
  });
  await assertFails(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId: 'p3' }))); // p3 existe : « recréer » = modification refusée
  const auto = h.db('alice').collection(`${M1}/places`).doc().id;
  await assertFails(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId: auto }))); // 4e lieu : premium
  await assertFails(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId: 'p4' })));
  await h.admin((f) => f.doc(`${M1}/places/p3`).delete());
  await assertFails(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId: 'p3', place: { type: 'risk' } }))); // zone de danger : premium
  await assertFails(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId: 'p3', place: { category: 'inconnue' } }))); // la règle de lieu s'applique entière
  if ((await read(h, reqPath(ID.M1, 'bob', 1))) === null) throw new Error('la demande devait rester');
  if ((await pending(h)) !== 1) throw new Error('le compteur devait rester à 1');
  await assertSucceeds(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId: 'p3' }))); // contrôle : p3 libre, trusted
});

s.tc('RQ21', 'S8 : créateur Miff+ : accepte une zone de danger et un lieu au-delà de 3 (identifiant automatique) ; un créateur Miff+ EXPIRÉ est refusé', async (h) => {
  await h.seedMiff({
    miffId: ID.M3, name: 'Voyageurs', code: CODE.M3, ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: false, key: ID.M3 }, { uid: 'bob', role: 'member', sharing: false, key: 'zz' }],
  });
  await h.seedMiff({
    miffId: ID.M4, name: 'Anciens', code: CODE.M4, ownerUid: 'frank',
    members: [{ uid: 'frank', role: 'owner', sharing: false, key: ID.M4 }, { uid: 'carol', role: 'member', sharing: false, key: 'yy' }],
  });
  await seedRequests(h, [['bob', 1], ['bob', 2]], { miffId: ID.M3, over: { type: 'risk' } });
  await h.admin(async (f) => {
    for (const id of ['p1', 'p2', 'p3']) await f.doc(`miffs/${ID.M3}/places/${id}`).set(placeSeed({ createdBy: 'dave' }));
  });
  const auto = h.db('dave').collection(`miffs/${ID.M3}/places`).doc().id;
  await assertSucceeds(go(h, 'dave', acceptRequestOps({ owner: 'dave', from: 'bob', miffId: ID.M3, k: 1, placeId: auto, place: { type: 'risk' } })));
  if ((await read(h, `miffs/${ID.M3}/places/${auto}`)).type !== 'risk') throw new Error('zone de danger attendue');
  await assertSucceeds(go(h, 'dave', acceptRequestOps({ owner: 'dave', from: 'bob', miffId: ID.M3, k: 2, placeId: h.db('dave').collection(`miffs/${ID.M3}/places`).doc().id, place: { type: 'trusted' } })));
  if ((await pending(h, ID.M3)) !== 0) throw new Error('compteur attendu à 0');
  await seedRequests(h, [['carol', 1]], { miffId: ID.M4 });
  await assertFails(go(h, 'frank', acceptRequestOps({ owner: 'frank', from: 'carol', miffId: ID.M4, placeId: h.db('frank').collection(`miffs/${ID.M4}/places`).doc().id })));
  await assertSucceeds(go(h, 'frank', acceptRequestOps({ owner: 'frank', from: 'carol', miffId: ID.M4, placeId: 'p1' }))); // un lieu gratuit reste permis
});

s.tc('RQ22', 'S8 : refuser : lot suppression + compteur -1 ; le créateur peut aussi accepter / refuser SANS toucher le compteur (maître de sa Miff, dérive possible, recalage) ; valeur du compteur recalée : 11, négative, lastReqId renseigné refusés', async (h) => {
  await seedRequests(h, [['bob', 1], ['bob', 2], ['carol', 1]]);
  await assertSucceeds(go(h, 'alice', declineRequestOps({ from: 'bob', k: 1 })));
  if ((await pending(h)) !== 2) throw new Error('compteur attendu à 2');
  await assertSucceeds(commit(h.db('alice'), [del(reqPath(ID.M1, 'bob', 2))])); // sans compteur : accepté (dérive du compteur : limite connue)
  await assertSucceeds(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(1))])); // recalage : le créateur lit 1 demande
  await assertSucceeds(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(10))]));
  await assertFails(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(11))]));
  await assertFails(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(-1))]));
  await assertFails(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(1, { lastReqId: 'carol_1' }))]));
  await assertFails(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(1, { updatedAt: T0 }))]));
  await assertFails(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(1, { extra: true }))]));
  await assertFails(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(1.5))]));
  // Compteur absent : le créateur le crée ; il peut le supprimer (nettoyage avant la suppression de la Miff) ; pas un membre.
  await h.admin((f) => f.doc(counterPath()).delete());
  await assertSucceeds(commit(h.db('alice'), [setMerge(counterPath(), counterOwner(0))]));
  await assertFails(h.db('bob').doc(counterPath()).delete());
  await assertSucceeds(h.db('alice').doc(counterPath()).delete());
});

s.tc('RQ23', 'S10 : un membre n\'écrit JAMAIS un lieu (création, avec ou sans demande dans le lot, modification, suppression) ; le créateur seul le fait', async (h) => {
  await seedRequests(h, [['bob', 1]]);
  await assertFails(h.db('bob').doc(`${M1}/places/p2`).set(placeInput('bob')));
  await assertFails(commit(h.db('bob'), [set(`${M1}/places/p2`, placeInput('bob')), ...Object.values(withdrawRequestOps({ uid: 'bob', k: 1 }))])); // « accepter » par son propre auteur
  await assertFails(commit(h.db('bob'), [set(`${M1}/places/p2`, placeInput('bob')), del(reqPath(ID.M1, 'bob', 1)), setMerge(counterPath(), counterMove('bob_1', -1))]));
  await assertFails(h.db('bob').doc(`${M1}/places/p1`).update({ name: 'X', updatedAt: serverTs() }));
  await assertFails(h.db('bob').doc(`${M1}/places/p1`).delete());
  await assertFails(commit(h.db('bob'), [set(`${M1}/places/p2`, placeInput('alice', { createdBy: 'alice' })), ...Object.values(withdrawRequestOps({ uid: 'bob', k: 1 }))]));
  if ((await read(h, reqPath(ID.M1, 'bob', 1))) === null) throw new Error('la demande devait rester');
});

s.tc('RQ24', 'L1 / L2 : départ et retrait : le membre retire ses demandes puis quitte (lot de départ INCHANGÉ) ; le lot de départ et le lot de retrait passent même avec des demandes restantes (elles deviennent orphelines) ; le créateur les nettoie ; un ex-membre ne crée ni ne lit mais peut effacer la sienne', async (h) => {
  await seedRequests(h, [['bob', 1], ['bob', 2], ['carol', 1]]);
  // Départ : bob retire d'abord ses deux demandes (un lot de 2 par demande), puis quitte.
  await assertSucceeds(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 1 })));
  await assertSucceeds(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 2 })));
  await assertSucceeds(commit(h.db('bob'), Object.values(leaveOps('bob'))));
  if ((await pending(h)) !== 1) throw new Error('compteur attendu à 1');
  // Retrait de carol par alice alors que carol a encore une demande : le lot existant (6 écritures) passe, la demande devient orpheline.
  await assertSucceeds(commit(h.db('alice'), Object.values(removeMemberOps({ target: 'carol' }))));
  if ((await read(h, reqPath(ID.M1, 'carol', 1))) === null) throw new Error('la demande de carol devait rester (orpheline)');
  await assertFails(create(h, 'carol', 2)); // plus membre
  await assertFails(own(h, 'carol'));
  await assertFails(h.db('carol').doc(reqPath(ID.M1, 'carol', 1)).get());
  // L'ex-membre peut effacer SA demande (effacement de ses données), avec le compteur.
  await assertSucceeds(go(h, 'carol', withdrawRequestOps({ uid: 'carol', k: 1 })));
  // Variante : le créateur nettoie une demande orpheline.
  await h.admin(async (f) => {
    await f.doc(reqPath(ID.M1, 'bob', 3)).set(requestSeed('bob'));
    await f.doc(counterPath()).set(counterSeed(1));
  });
  await assertSucceeds(go(h, 'alice', declineRequestOps({ from: 'bob', k: 3 })));
  if ((await pending(h)) !== 0) throw new Error('compteur attendu à 0');
});

s.tc('RQ25', 'L3 : suppression de la Miff : le créateur supprime d\'abord les demandes (lot de 5 au plus) et le compteur ; ensuite aucune lecture ; le lot existant de la Miff n\'est pas modifié', async (h) => {
  await seedRequests(h, [['bob', 1], ['bob', 2], ['bob', 3], ['carol', 1], ['carol', 2]]);
  await assertSucceeds(commit(h.db('alice'), [
    ...[['bob', 1], ['bob', 2], ['bob', 3], ['carol', 1], ['carol', 2]].map(([uid, k]) => del(reqPath(ID.M1, uid, k))),
  ]));
  await assertSucceeds(h.db('alice').doc(counterPath()).delete());
  const left = await h.db('alice').collection(`${M1}/placeRequests`).get();
  if (!left.empty || (await read(h, counterPath())) !== null) throw new Error('plus aucune demande ni compteur attendus');
});

s.tc('RQ26', 'Cas particuliers : emplacements d\'un autre uid ressemblant (`bob` vs `bobby`) ; Miff d\'un autre créateur : le créateur de M2 ne lit ni n\'écrit les demandes de M1 ; demande identique redéposée après un retrait', async (h) => {
  await h.admin((f) => f.doc(`${M1}/members/bobby`).set({ role: 'member', ...profile('bobby'), joinedAt: T0, joinCode: CODE.M1, sharing: sharingOff() }));
  await assertSucceeds(create(h, 'bobby', 1));
  await assertFails(commit(h.db('bob'), [del(reqPath(ID.M1, 'bobby', 1)), setMerge(counterPath(), counterMove('bobby_1', -1))])); // bob n'est pas bobby
  await assertFails(h.db('bob').doc(reqPath(ID.M1, 'bobby', 1)).get());
  await assertFails(h.db('zoe').collection(`${M1}/placeRequests`).get());
  await assertSucceeds(create(h, 'bob', 1));
  await assertSucceeds(go(h, 'bob', withdrawRequestOps({ uid: 'bob', k: 1 })));
  await assertSucceeds(create(h, 'bob', 1)); // même lieu, même emplacement : redemander est permis (D3)
  if ((await pending(h)) !== 2) throw new Error('compteur attendu à 2');
});

s.tc('RQ27', 'catégorie : les 7 valeurs de R-LIEU-2 acceptées ; absente, inconnue, casse, espace, vide, nulle, nombre, tableau refusées ; l\'acceptation la copie dans le lieu', async (h) => {
  for (const category of ['home', 'work', 'school', 'sport', 'relative', 'leisure', 'other']) {
    await assertSucceeds(create(h, 'bob', 1, { category }));
    await h.admin((f) => f.doc(reqPath(ID.M1, 'bob', 1)).delete());
  }
  for (const category of ['inconnue', 'Home', 'HOME', 'home ', ' home', '', null, 1, ['home'], { a: 1 }, true]) await assertFails(create(h, 'bob', 1, { category }));
  const d = requestInput('bob');
  delete d.category;
  await assertFails(go(h, 'bob', createRequestOps({ uid: 'bob', data: d })));
  await h.admin((f) => f.doc(counterPath()).set(counterSeed(0)));
  await assertSucceeds(create(h, 'bob', 1, { category: 'school' }));
  await assertSucceeds(go(h, 'alice', acceptRequestOps({ from: 'bob', placeId: 'p2', place: { category: 'school' }, left: 0 })));
  if ((await read(h, `${M1}/places/p2`)).category !== 'school') throw new Error('la catégorie devait être copiée dans le lieu');
});
