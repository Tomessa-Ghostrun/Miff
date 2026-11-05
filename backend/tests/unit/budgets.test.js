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
import { readFileSync } from 'node:fs';
import { makeCostModel, RULES_FILE, stripComments } from '../helpers/rule-budget.js';

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
    isOwnerOneRead: [1, 1],
    mySharingOn: [1, 1],
    miffAlive: [1, 1],
    statusPublished: [1, 1],
    positionPublished: [1, 1],
    targetSharingOn: [1, 1],
    codeRenewed: [2, 2],
    phoneMatchesProfile: [1, 1],
    validSharingChange: [3, 3],
    indexAfterOk: [4, 4],
    ownerCreateOk: [2, 6],
    memberJoinOk: [2, 6],
    termsAcceptedAfter: [1, 1],
    validMemberCreate: [0, 14],
    validMemberUpdate: [0, 4],
    memberDeleteOk: [4, 8],
    validMiffCreate: [3, 3],
    validMiffUpdate: [2, 2],
    validJoinCodeCreate: [3, 3],
    isPremiumOneRead: [1, 1],
    validPlaceCreate: [0, 1],
    validPlaceUpdate: [0, 1],
    // SESS-1 : fromActiveDevice = la lecture de la session (users/{uid}/session/active), évaluée seulement quand la clé deviceId est présente.
    fromActiveDevice: [1, 1],
    validStatus: [1, 2],
    validPosition: [1, 2],
    validPhoto: [1, 1],
  });
});

test('BUDGET - coût figé de chaque règle « allow » (borne haute sans court-circuit)', () => {
  const cost = (header, methods) => Object.fromEntries(methods.map((m) => [m, allow(header, m)]));
  assert.deepEqual(cost('match /places/{placeId} {', ['get', 'create', 'update', 'delete']), { get: 2, create: 3, update: 3, delete: 2 });
  assert.deepEqual(cost('match /status/{uid} {', ['get', 'create', 'update', 'delete']), { get: 3, create: 2, update: 2, delete: 1 }); // SESS-1 : +1 (session), seulement quand la clé deviceId est présente
  assert.deepEqual(cost('match /members/{uid} {', ['get', 'create', 'update', 'delete']), { get: 2, create: 14, update: 4, delete: 8 });
  assert.deepEqual(cost('match /photos/{uid} {', ['get', 'create', 'delete']), { get: 2, create: 1, delete: 2 });
  // Dernière position (R-LIEU-POS) : écriture = Miff existante + fiche du membre ; lecture = Miff, mon partage, mon statut, ma position, partage de la cible.
  assert.deepEqual(cost('match /positions/{uid} {', ['get', 'create', 'update', 'delete']), { get: 5, create: 3, update: 3, delete: 1 }); // SESS-1 : écriture 2 -> 3 (+1 session)
  // SESS-1 : l'index premium lit le droit en UNE lecture (isPremiumOneRead) : 4 -> 2.
  assert.deepEqual(cost('match /memberships/{key} {', ['create', 'delete']), { create: 2, delete: 1 });
  // SESS-1 : le document de session ne lit rien (propriétaire seul, schéma fermé).
  assert.deepEqual(cost('match /session/active {', ['get', 'create', 'update', 'delete']), { get: 0, create: 0, update: 0, delete: 0 });
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
const sum = (...parts) => parts.reduce((n, p) => n + p, 0);
const A = (header, method) => allow(header, method);
const upper = {
  retrait: sum(A('match /members/{uid} {', 'delete'), A('match /status/{uid} {', 'delete'), A('match /photos/{uid} {', 'delete'),
    A('match /positions/{uid} {', 'delete'), A('match /miffs/{miffId} {', 'update'), A('match /joinCodes/{code} {', 'create'),
    A('match /joinCodes/{code} {', 'delete')),
  creerPremium: sum(A('match /miffs/{miffId} {', 'create'), A('match /joinCodes/{code} {', 'create'), A('match /members/{uid} {', 'create'),
    A('match /memberships/{key} {', 'create'), A('match /status/{uid} {', 'create')),
  arretUneMiff: A('match /members/{uid} {', 'update'),
  quitterUneMiff: sum(A('match /members/{uid} {', 'delete'), A('match /memberships/{key} {', 'delete')),
  activer: sum(A('match /members/{uid} {', 'update'), A('match /status/{uid} {', 'create'), A('match /positions/{uid} {', 'create')),
  supprimerMiff: sum(A('match /miffs/{miffId} {', 'delete'), A('match /members/{uid} {', 'delete'), A('match /memberships/{key} {', 'delete')),
};
const DOCUMENTED = {
  retraitUnMembre: 18, // fiche 7 (3 existsAfter + propriétaire 2 + code renouvelé 2) + statut 1 + photo 1 + position 1 + Miff 4 + code créé 3 + code supprimé 1
  // SESS-1 : 19 -> 18. Fiche 8 (téléphone 1 + CGU 1 + !exists de la Miff 1 + getAfter de la Miff 1 + indexAfterOk 4) + Miff 3 + code 3 + index premium 2
  // (isPremiumOneRead au lieu d'isPremium : 4 -> 2) + statut 2 (consentement 1 + session 1) = 18, marge 2. Avec une application qui n'écrit pas encore le
  // deviceId (temps 1) : 17. La position n'y entre jamais.
  creerEtPartagerPremium: 18,
  arretDuPartageUneMiff: 3, // 3 existsAfter (statut, photo, position)
  quitterUneMiff: 4, // fiche 3 existsAfter + index 1
  activerPartageStatutPosition: 5, // getAfter du statut 1 + session 1 (statut) + Miff 1 + getAfter de la fiche 1 + session 1 (position) ; 3 sans deviceId
  supprimerLaMiff: 13, // Miff 4 + pierre tombale 3 + code 1 + fiche 4 + index 1
};

test('BUDGET - lots documentés : sous la limite de 20 et sous la borne haute calculée sur le texte des règles', () => {
  for (const [name, value] of Object.entries(DOCUMENTED)) assert.ok(value <= LIMIT, name);
  assert.ok(DOCUMENTED.retraitUnMembre <= upper.retrait, `retrait : ${DOCUMENTED.retraitUnMembre} > ${upper.retrait}`);
  assert.ok(DOCUMENTED.creerEtPartagerPremium <= upper.creerPremium, 'création premium');
  assert.ok(DOCUMENTED.arretDuPartageUneMiff <= upper.arretUneMiff, 'arrêt du partage');
  assert.ok(DOCUMENTED.quitterUneMiff <= upper.quitterUneMiff, 'quitter');
  assert.ok(DOCUMENTED.activerPartageStatutPosition <= upper.activer, 'activation');
  assert.ok(DOCUMENTED.supprimerLaMiff <= upper.supprimerMiff + A('match /miffsDeleted/{miffId} {', 'create'), 'suppression de la Miff');
  assert.equal(LIMIT - DOCUMENTED.creerEtPartagerPremium, 2, 'marge de 2 (SESS-1 : isPremiumOneRead libère 2 appels, la session en prend 1)');
  assert.equal(LIMIT - DOCUMENTED.retraitUnMembre, 2, 'retrait : marge de 2, inchangée par la position (isOwnerOneRead)');
});

// --- Dernière position (R-LIEU-POS) et contrat client des lots multi-Miffs (revue de sécurité, I2 / M1) -----------------------------
test('BUDGET - position : écriture isolée = 3 (Miff + fiche + session) ; au plus 6 Miffs par lot (18 sur 20, marge 2) ; 7 = 21 dépasse (SESS-1 : 8 Miffs avant)', () => {
  const write = A('match /positions/{uid} {', 'create');
  assert.equal(write, 3);
  assert.equal(6 * write, 18);
  assert.ok(LIMIT - 6 * write >= 2);
  assert.ok(7 * write > LIMIT); // 21 : le client ne dépasse pas 6 (avant SESS-1 : 8 Miffs, 2 appels chacune)
  assert.equal(write - 1, 2); // sans deviceId (temps 1, application actuelle) : 2 par Miff, comme avant
  assert.equal(A('match /positions/{uid} {', 'get'), 5); // 5 sur 10 : marge 5
});

test('BUDGET - contrat client : arrêt du partage 6 Miffs au plus par lot (3 × 6 = 18), départ 4 au plus (4 × 4 = 16 ; 5 = 20 sans marge)', () => {
  assert.equal(DOCUMENTED.arretDuPartageUneMiff * 6, 18);
  assert.ok(LIMIT - DOCUMENTED.arretDuPartageUneMiff * 6 >= 2);
  assert.ok(DOCUMENTED.arretDuPartageUneMiff * 7 > LIMIT);
  assert.equal(DOCUMENTED.quitterUneMiff * 4, 16);
  assert.ok(LIMIT - DOCUMENTED.quitterUneMiff * 4 >= 2);
  assert.equal(DOCUMENTED.quitterUneMiff * 5, 20);
  // Avant la position (PR #55) : arrêt 2 par Miff (10 Miffs), départ 3 par Miff (6 Miffs) : le contrat recule.
});

test('BUDGET - la position n\'entre dans aucun lot de lieux ni dans la création de Miff : budgets de ces lots inchangés par la position', () => {
  assert.equal(5 * PLACE.createPremium, 15);
  assert.equal(DOCUMENTED.creerEtPartagerPremium, 18);
  // Aucune règle de lieu ni de création n'appelle de fonction de position.
  const positionFns = ['validPosition', 'positionPublished', 'targetSharingOn', 'isPositionConsentVersion'];
  for (const header of ['match /places/{placeId} {', 'match /miffs/{miffId} {', 'match /joinCodes/{code} {', 'match /memberships/{key} {']) {
    for (const [method, expr] of Object.entries(model.allowRules(header))) {
      for (const fn of positionFns) assert.ok(!expr.includes(fn), `${header} ${method} appelle ${fn}`);
    }
  }
});

// --- SESS-1 : un compte, un seul appareil -----------------------------------------------------------------------------------------
// La session ajoute UNE lecture de règles par statut et par position écrits avec la clé deviceId (rien quand la clé est absente, temps 1).
// Les lieux, les fiches, les photos seules, les suppressions et le renouvellement du consentement ne la lisent pas : leurs budgets sont inchangés.
test('BUDGET - SESS-1 : la session coûte 1 par statut / position écrits (borne haute) ; les autres écritures ne la lisent pas', () => {
  const status = A('match /status/{uid} {', 'create');
  const position = A('match /positions/{uid} {', 'create');
  assert.equal(status, 2);
  assert.equal(position, 3);
  assert.equal(model.costOfFn('fromActiveDevice'), 1);
  assert.equal(model.costOfFn('validStatus') - 1, 1);
  assert.equal(model.costOfFn('validPosition') - 1, 1);
  // Aucune autre règle n'appelle la fonction de session ni ne lit un document de session.
  for (const header of ['match /places/{placeId} {', 'match /members/{uid} {', 'match /photos/{uid} {', 'match /miffs/{miffId} {', 'match /joinCodes/{code} {', 'match /memberships/{key} {', 'match /miffsDeleted/{miffId} {']) {
    for (const [method, expr] of Object.entries(model.allowRules(header))) {
      assert.ok(!expr.includes('fromActiveDevice') && !expr.includes('validStatus') && !expr.includes('validPosition'), `${header} ${method}`);
    }
  }
});

test('BUDGET - SESS-1 : pire cas de CHAQUE lot des applications avec la session (comptage manuel sans cache) : aucun ne dépasse 20, marge de 2 au moins', () => {
  const worst = {
    // [pire cas SESS-1, pire cas avant SESS-1 / application sans deviceId]
    rejoindreEtPartager: [9, 8], // fiche 6 + index 1 + statut 1 + session 1
    profilEtRejoindre: [9, 8],
    creerEtPartagerGratuit: [15, 14],
    creerEtPartagerPremium: [DOCUMENTED.creerEtPartagerPremium, 17], // 19 avant SESS-1 (isPremium)
    profilEtCreerGratuit: [15, 14],
    activerPartage: [2, 1], // fiche 0 + statut 2 (avec la position : 5)
    activerPartageAvecPosition: [DOCUMENTED.activerPartageStatutPosition, 3],
    statutSeul: [2, 1], // événement de géofence (LOC-2b) : un document par lot
    majManuelle: [3, 2], // photo 1 + statut 2
    positionSeule: [3, 2], // LOC-2b : un document par lot
    lotPositions6Miffs: [18, 12],
    lotStatuts8Miffs: [16, 8],
    lieuxPremium5Miffs: [15, 15], // inchangé
    retraitUnMembre: [DOCUMENTED.retraitUnMembre, 18], // inchangé (aucune écriture de statut ni de position)
    arretDuPartage: [DOCUMENTED.arretDuPartageUneMiff, 3],
    quitter: [DOCUMENTED.quitterUneMiff, 4],
    supprimerLaMiff: [DOCUMENTED.supprimerLaMiff, 13],
    renouvelerConsentement5Miffs: [0, 0],
  };
  for (const [name, [now, before]] of Object.entries(worst)) {
    const limit = name === 'statutSeul' || name === 'positionSeule' || name === 'activerPartage' ? 10 : LIMIT;
    assert.ok(now <= limit - 2, `${name} : ${now} (marge ${limit - now} sur ${limit})`);
    assert.ok(now >= before, name);
  }
  // Rien n'a augmenté pour les lots qui ne portent ni statut ni position.
  for (const name of ['lieuxPremium5Miffs', 'retraitUnMembre', 'arretDuPartage', 'quitter', 'supprimerLaMiff', 'renouvelerConsentement5Miffs']) {
    assert.equal(worst[name][0], worst[name][1], name);
  }
});

test('BUDGET - SESS-1 : l index premium (isPremiumOneRead) coûte 2 ; l ancienne fonction isPremium (3 appels) n existe plus', () => {
  assert.equal(A('match /memberships/{key} {', 'create'), 2);
  assert.equal(model.fns.isPremium, undefined);
  assert.equal(model.costOfFn('isPremiumOneRead'), 1);
});

test('BUDGET - R-LIEU-1 : la conservation des horodatages du statut ne coûte aucune lecture de règles (resource n\'est pas une lecture)', () => {
  assert.equal(model.costOfFn('statusInstant'), 0);
  assert.equal(model.costOfFn('validStatus'), 2); // getAfter du consentement + lecture de la session (SESS-1)
  const text = readFileSync(RULES_FILE, 'utf8');
  const body = stripComments(text.slice(text.indexOf('function statusInstant('), text.indexOf('function validStatus(')));
  assert.match(body, /resource != null && resource\.data\.get\(key, null\) == v/);
  assert.doesNotMatch(body.replace('resource.data.get(', ''), /\b(get|getAfter|exists|existsAfter)\(/);
});
