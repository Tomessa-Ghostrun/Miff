// Budget de lectures de règles de la DEMANDE D'AJOUT D'UN LIEU (LIEU-REQ-A, ticket docs/tickets/demande-ajout-lieu.md §6.3). Sans émulateur.
// Comptages SANS CACHE calculés sur le texte de firestore.rules (borne haute, sans court-circuit), plus le comptage avec court-circuit documenté
// (data-model.md) ; les mesures de l'émulateur (09-rule-reads.test.js, scénarios READS-req-*) les recoupent. Limite : 20 par lot, 10 par lecture.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { makeCostModel, RULES_FILE, stripComments } from '../helpers/rule-budget.js';

const LIMIT = 20;
const model = makeCostModel();
const allow = (header, method) => model.costOfAllow(header, method);
const REQ = 'match /placeRequests/{reqId} {';
const COUNTER = 'match /meta/placeRequests {';
const PLACES = 'match /places/{placeId} {';

test('BUDGET LIEU-REQ - coût de chaque règle (borne haute sans court-circuit) : tout changement impose de recalculer les lots et data-model.md', () => {
  const cost = (header, methods) => Object.fromEntries(methods.map((m) => [m, allow(header, m)]));
  // get / list : créateur 1 + (membre 1 + Miff 1) ; create : premium 1 + fiche 1 + Miff 1 + compteur 1 ; delete : créateur 1 + compteur 1.
  assert.deepEqual(cost(REQ, ['get', 'list', 'create', 'update', 'delete']), { get: 3, list: 3, create: 4, update: 0, delete: 2 });
  // Compteur : lecture réservée au créateur ; écriture = créateur 1 OU membre 2 (exists + existsAfter de la demande) : 3 en borne haute.
  assert.deepEqual(cost(COUNTER, ['get', 'create', 'update', 'delete']), { get: 1, create: 3, update: 3, delete: 1 });
});

test('BUDGET LIEU-REQ - lots (comptage sans cache AVEC court-circuit, tel que documenté) : chaque lot reste à 20 - marge >= 2, et sous la borne haute du texte', () => {
  const upper = (...parts) => parts.reduce((n, p) => n + p, 0);
  // Court-circuit : un membre n'évalue pas la branche « créateur » du compteur (lastReqId non nul) : 2 au lieu de 3 ; `type == 'trusted'` évite le premium.
  const documented = {
    creerTrusted: 3 + 2, //  fiche 1 + Miff 1 + compteur après 1 | compteur : exists + existsAfter de la demande
    creerRisk: 4 + 2, //  + droit Miff+ du demandeur (isPremiumOneRead)
    retirer: 2 + 2, //  créateur 1 (faux) + compteur après 1 | exists + existsAfter
    refuser: 1 + 1, //  créateur 1 | créateur 1
    accepterGratuit: 2 + 1 + 1, //  lieu p1..p3 : isOwner 2 | demande : créateur 1 | compteur : créateur 1
    accepterMiffPlus: 3 + 1 + 1, //  lieu libre ou zone de danger : isOwner 2 + premium 1
  };
  const bounds = {
    creerTrusted: upper(allow(REQ, 'create') - model.costOfFn('isPremiumOneRead'), allow(COUNTER, 'create')),
    creerRisk: upper(allow(REQ, 'create'), allow(COUNTER, 'create')),
    retirer: upper(allow(REQ, 'delete'), allow(COUNTER, 'update')),
    refuser: upper(allow(REQ, 'delete'), allow(COUNTER, 'update')),
    accepterGratuit: upper(allow(PLACES, 'create') - model.costOfFn('isPremiumOneRead'), allow(REQ, 'delete'), allow(COUNTER, 'update')),
    accepterMiffPlus: upper(allow(PLACES, 'create'), allow(REQ, 'delete'), allow(COUNTER, 'update')),
  };
  for (const [name, value] of Object.entries(documented)) {
    assert.ok(value <= bounds[name], `${name} : ${value} documenté > borne haute ${bounds[name]}`);
    assert.ok(LIMIT - bounds[name] >= 2, `${name} : borne haute ${bounds[name]}, marge ${LIMIT - bounds[name]} < 2`);
  }
  assert.deepEqual(documented, { creerTrusted: 5, creerRisk: 6, retirer: 4, refuser: 2, accepterGratuit: 4, accepterMiffPlus: 5 });
  // Borne haute statique : le pire est 8 sur 20 (accepter avec un lieu Miff+), marge 12.
  assert.equal(Math.max(...Object.values(bounds)), 8);
});

test('BUDGET LIEU-REQ - lecture de la liste : 1 pour le créateur, 3 pour le demandeur (sur 10) ; get : 1 / 3', () => {
  assert.equal(model.costOfFn('isOwnerOneRead'), 1);
  assert.equal(allow(REQ, 'list'), 3);
  assert.ok(10 - allow(REQ, 'list') >= 7);
  assert.ok(10 - allow(REQ, 'get') >= 7);
});

test('BUDGET LIEU-REQ - aucune règle EXISTANTE n\'appelle les fonctions de la demande de lieu : aucun lot existant ne change (créer, rejoindre, quitter, retirer, supprimer, lieux)', () => {
  const requestFns = ['isOwnRequestId', 'validRequestShape', 'isPlainMember', 'requestCounterMoved', 'memberCounterOk', 'validRequestCounter'];
  for (const header of [PLACES, 'match /members/{uid} {', 'match /status/{uid} {', 'match /photos/{uid} {', 'match /positions/{uid} {', 'match /miffs/{miffId} {',
    'match /joinCodes/{code} {', 'match /memberships/{key} {', 'match /miffsDeleted/{miffId} {', 'match /entitlements/{uid} {', 'match /users/{uid} {']) {
    for (const [method, expr] of Object.entries(model.allowRules(header))) {
      for (const fn of requestFns) assert.ok(!expr.includes(fn), `${header} ${method} appelle ${fn}`);
    }
  }
  // Et aucune fonction existante (hors celles de la demande) ne les appelle non plus.
  for (const [name, body] of Object.entries(model.fns)) {
    if (requestFns.includes(name)) continue;
    for (const fn of requestFns) assert.ok(!new RegExp(`\\b${fn}\\(`).test(body), `${name} appelle ${fn}`);
  }
});

test('BUDGET LIEU-REQ - les règles de lieu sont INCHANGÉES : un membre ne peut jamais écrire un lieu, créer et modifier restent réservés au créateur', () => {
  const places = model.allowRules(PLACES);
  assert.equal(places.create.replace(/\s+/g, ' ').trim(), 'isOwner(miffId) && validPlaceCreate(placeId)');
  assert.equal(places.update.replace(/\s+/g, ' ').trim(), 'isOwner(miffId) && validPlaceUpdate()');
  assert.equal(places.delete.replace(/\s+/g, ' ').trim(), 'isOwner(miffId)');
  const text = stripComments(readFileSync(RULES_FILE, 'utf8'));
  assert.ok(!/placeRequests/.test(text.slice(text.indexOf('function validPlaceCreate('), text.indexOf('function validPlaceUpdate('))), 'validPlaceCreate ne connaît pas les demandes');
});

test('BUDGET LIEU-REQ - plafonds : 3 emplacements par membre (liste fermée {uid}_1..3), 10 par Miff (borne du compteur) ; aucune expression régulière construite avec un uid', () => {
  const text = stripComments(readFileSync(RULES_FILE, 'utf8'));
  const body = text.slice(text.indexOf('function isOwnRequestId('), text.indexOf('function validRequestShape('));
  assert.match(body.replace(/\s+/g, ' '), /reqId in \[request\.auth\.uid \+ '_1', request\.auth\.uid \+ '_2', request\.auth\.uid \+ '_3'\]/);
  assert.ok(!body.includes('matches('), 'un uid ne doit jamais entrer dans une expression régulière');
  const counter = text.slice(text.indexOf('function validRequestCounter('), text.indexOf('match /users/{uid}'));
  assert.match(counter.replace(/\s+/g, ' '), /d\.pending >= 0 && d\.pending <= 10/);
});
