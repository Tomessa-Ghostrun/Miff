// Budget de lectures de règles FIGÉ (revue de sécurité des PR #54/#55, point I2 / M7).
//
// L'émulateur met en cache les lectures d'un même document et accepte des lots que Firestore refuserait (sonde X8 : 7 Miffs
// premium en un lot passent dans l'émulateur, 21 appels sans cache). Les budgets de data-model.md §12 sont donc des comptages SANS
// CACHE, et ce test (sans émulateur) les fige :
//   1. le coût de chaque fonction de règles et de chaque règle « allow » est calculé sur le TEXTE de firestore.rules (borne haute,
//      sans court-circuit) et comparé à une table figée : ajouter ou retirer un get / exists / getAfter / existsAfter fait échouer
//      le test, ce qui oblige à recalculer les lots (et data-model.md §12) avant de continuer ;
//   2. les chiffres documentés (comptage manuel avec court-circuit) sont figés ici avec leur marge sur la limite de 20 par lot,
//      et vérifiés <= à la borne haute calculée.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { makeCostModel } from '../helpers/rule-budget.js';

const LIMIT = 20;
const model = makeCostModel();
const allow = (header, method) => model.costOfAllow(header, method);

test('BUDGET - coût figé de chaque fonction de règles (direct, avec fonctions appelées) : tout changement impose de recalculer les lots', () => {
  const actual = {};
  for (const name of Object.keys(model.fns)) {
    const total = model.costOfFn(name);
    if (total > 0) actual[name] = [model.direct(name), total];
  }
  assert.deepEqual(actual, {
    isMember: [1, 1],
    isOwner: [2, 2],
    mySharingOn: [1, 1],
    miffAlive: [1, 1],
    statusPublished: [1, 1],
    codeRenewed: [2, 2],
    isPremium: [3, 3],
    phoneMatchesProfile: [1, 1],
    validSharingChange: [2, 2],
    indexAfterOk: [4, 4],
    ownerCreateOk: [2, 6],
    memberJoinOk: [2, 6],
    termsAcceptedAfter: [1, 1],
    validMemberCreate: [0, 14],
    validMemberUpdate: [0, 3],
    memberDeleteOk: [3, 7],
    validMiffCreate: [3, 3],
    validMiffUpdate: [2, 2],
    validJoinCodeCreate: [3, 3],
    isPremiumOneRead: [1, 1],
    validPlaceCreate: [0, 1],
    validPlaceUpdate: [0, 1],
    validStatus: [1, 1],
    validPhoto: [1, 1],
  });
});

test('BUDGET - coût figé de chaque règle « allow » (borne haute sans court-circuit)', () => {
  const cost = (header, methods) => Object.fromEntries(methods.map((m) => [m, allow(header, m)]));
  assert.deepEqual(cost('match /places/{placeId} {', ['get', 'create', 'update', 'delete']), { get: 2, create: 3, update: 3, delete: 2 });
  assert.deepEqual(cost('match /status/{uid} {', ['get', 'create', 'update', 'delete']), { get: 3, create: 1, update: 1, delete: 2 });
  assert.deepEqual(cost('match /members/{uid} {', ['get', 'create', 'update', 'delete']), { get: 2, create: 14, update: 3, delete: 7 });
  assert.deepEqual(cost('match /photos/{uid} {', ['get', 'create', 'delete']), { get: 2, create: 1, delete: 3 });
  assert.deepEqual(cost('match /memberships/{key} {', ['create', 'delete']), { create: 4, delete: 1 });
  assert.deepEqual(cost('match /miffs/{miffId} {', ['get', 'create', 'update', 'delete']), { get: 1, create: 3, update: 4, delete: 4 });
  assert.deepEqual(cost('match /joinCodes/{code} {', ['create', 'update', 'delete']), { create: 3, update: 2, delete: 1 });
});

// --- Lots de lieux : le coût par document est EXACT (aucun court-circuit possible : le créateur est vérifié d'abord) -------------
const PLACE = {
  createFree: allow('match /places/{placeId} {', 'create') - model.costOfFn('isPremiumOneRead'), // p1 / p2 : pas de lecture du premium
  createPremium: allow('match /places/{placeId} {', 'create'),
  updateToRisk: allow('match /places/{placeId} {', 'update'),
  remove: allow('match /places/{placeId} {', 'delete'),
};

test('BUDGET - lots de lieux : 5 Miffs au plus (15 sur 20, marge 5) ; 6 = 18 (marge 2) ; 7 = 21 dépasse la limite', () => {
  assert.deepEqual(PLACE, { createFree: 2, createPremium: 3, updateToRisk: 3, remove: 2 });
  assert.equal(5 * PLACE.createPremium, 15);
  assert.equal(5 * PLACE.updateToRisk, 15);
  assert.equal(5 * PLACE.createFree, 10);
  assert.equal(5 * PLACE.remove, 10);
  assert.ok(LIMIT - 5 * PLACE.createPremium >= 5);
  assert.equal(6 * PLACE.createPremium, 18); // marge de 2, minimum visé
  // 7 Miffs : 21 appels sans cache. NON VÉRIFIABLE dans l'émulateur (il accepte ce lot grâce à son cache : sonde X8) ; la borne retenue
  // est le comptage manuel sans cache.
  assert.equal(7 * PLACE.createPremium, 21);
  assert.ok(7 * PLACE.createPremium > LIMIT);
});

test('BUDGET - changer les Miffs d\'un lieu rattaché à 5 Miffs (retirer de 5 et ajouter à 5) coûte 25 : deux lots NON atomiques', () => {
  assert.equal(5 * PLACE.remove + 5 * PLACE.createPremium, 25);
  assert.ok(5 * PLACE.remove + 5 * PLACE.createPremium > LIMIT);
  // Un seul lot reste possible tant que la somme tient : 2 retraits + 3 ajouts = 13.
  assert.equal(2 * PLACE.remove + 3 * PLACE.createPremium, 13);
  // Au plus 4 retraits + 4 ajouts en un lot (20, marge 0) : le client sépare dès 4 + 4 ; 3 + 3 = 15 (marge 5).
  assert.equal(3 * PLACE.remove + 3 * PLACE.createPremium, 15);
  assert.equal(4 * PLACE.remove + 4 * PLACE.createPremium, 20);
});

// --- Lots documentés (comptage manuel avec court-circuit, data-model.md §12) -------------------------------------------------------
const writes = (...parts) => parts.reduce((n, p) => n + p, 0);
const upper = {
  retrait: writes(allow('match /members/{uid} {', 'delete'), allow('match /status/{uid} {', 'delete'), allow('match /photos/{uid} {', 'delete'),
    allow('match /miffs/{miffId} {', 'update'), allow('match /joinCodes/{code} {', 'create'), allow('match /joinCodes/{code} {', 'delete')),
  creerPremium: writes(allow('match /miffs/{miffId} {', 'create'), allow('match /joinCodes/{code} {', 'create'), allow('match /members/{uid} {', 'create'),
    allow('match /memberships/{key} {', 'create'), allow('match /status/{uid} {', 'create')),
};
const DOCUMENTED = {
  // [lectures documentées, lot concerné]
  retraitUnMembre: 18, // fiche 6 + statut 2 + photo 2 + Miff 4 + code créé 3 + code supprimé 1
  creerEtPartagerPremium: 19, // marge de 1 : AUCUN appel de plus dans ce lot (data-model.md §12)
};

test('BUDGET - lots documentés : sous la limite de 20 et sous la borne haute calculée sur le texte des règles', () => {
  for (const [name, value] of Object.entries(DOCUMENTED)) assert.ok(value <= LIMIT, name);
  assert.ok(DOCUMENTED.retraitUnMembre <= upper.retrait, `retrait : ${DOCUMENTED.retraitUnMembre} > ${upper.retrait}`);
  assert.ok(DOCUMENTED.creerEtPartagerPremium <= upper.creerPremium, `création : ${DOCUMENTED.creerEtPartagerPremium} > ${upper.creerPremium}`);
  assert.equal(LIMIT - DOCUMENTED.creerEtPartagerPremium, 1, 'marge de 1 documentée : tout ajout de lecture à ce lot le fait dépasser 20');
  assert.equal(LIMIT - DOCUMENTED.retraitUnMembre, 2);
});
