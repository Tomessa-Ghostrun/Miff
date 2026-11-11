// Budget de lectures de règles du MODE VOYAGE (VOY-B, ticket docs/tickets/mode-voyage.md §4.2, critère VB4). Sans émulateur.
// Décision du PM du 2026-11-10 : le mode voyage est DISPONIBLE POUR TOUS (plus de barrière Miff+) : un statut de voyage ne lit rien de plus
// qu'un statut ordinaire (consentement + session = 2). Ce fichier fige cette propriété pour qu'une barrière premium ne revienne pas
// (ni par oubli, ni par copie de la première version du ticket) sans faire échouer un test.
//
// Fichier à part (et non dans budgets.test.js) pour ne pas entrer en collision avec les autres évolutions de ce fichier. L'émulateur met en cache
// les lectures d'un même document (sonde X8) : les chiffres sont les comptages SANS CACHE calculés sur le texte de firestore.rules.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { makeCostModel, RULES_FILE, stripComments } from '../helpers/rule-budget.js';

const LIMIT = 20;
const model = makeCostModel();
const allow = (header, method) => model.costOfAllow(header, method);
const STATUS = 'match /status/{uid} {';
const POSITIONS = 'match /positions/{uid} {';

test('BUDGET VOY-B - un statut de voyage coûte 2 lectures de règles (consentement + session), comme un statut ordinaire : validTravel ne lit rien', () => {
  assert.equal(model.costOfFn('validTravel'), 0);
  assert.equal(model.direct('validTravel'), 0);
  assert.equal(model.costOfFn('validStatus'), 2);
  assert.equal(allow(STATUS, 'create'), 2);
  assert.equal(allow(STATUS, 'update'), 2);
});

test('BUDGET VOY-B - aucune barrière Miff+ : ni validTravel ni validStatus ne lisent le premium ni le document entitlements', () => {
  const text = readFileSync(RULES_FILE, 'utf8');
  const code = stripComments(text.slice(text.indexOf('function validStatus('), text.indexOf('function isCoordinate(')));
  assert.ok(code.includes('function validTravel('), 'validTravel doit se trouver dans la zone examinée');
  assert.ok(!code.includes('isPremium'), 'aucun appel au premium dans le statut');
  assert.ok(!code.includes('entitlements'), 'aucune lecture de entitlements dans le statut');
});

test('BUDGET VOY-B - lot de statuts de voyage : 8 Miffs au plus (2 x 8 = 16 sur 20, marge 4) ; 10 = 20 sans marge ; 11 dépasse', () => {
  const perStatus = allow(STATUS, 'create');
  assert.equal(8 * perStatus, 16);
  assert.ok(LIMIT - 8 * perStatus >= 2);
  assert.equal(10 * perStatus, 20); // aucune marge : le client ne dépasse pas 8
  assert.ok(11 * perStatus > LIMIT);
});

test('BUDGET VOY-B - variante de borne « voyage + suppression de MA dernière position » (non utilisée par l\'activation, la position restant partagée) : la suppression ne lit rien pour soi (isSelf avant isOwnerOneRead) ; 8 Miffs = 16', () => {
  const deleteRule = model.allowRules(POSITIONS).delete.replace(/\s+/g, ' ').trim();
  assert.match(deleteRule, /^isSelf\(uid\) \|\| isOwnerOneRead\(miffId\)$/);
  assert.equal(model.costOfFn('isSelf'), 0);
  assert.equal(model.costOfFn('isOwnerOneRead'), 1); // borne haute statique de la règle de suppression (non évaluée quand c'est moi)
  assert.equal(8 * allow(STATUS, 'create'), 16);
  // Borne haute statique SANS court-circuit : 8 x (2 + 1) = 24 : ce n'est PAS le coût réel, mais ce test interdit d'inverser les deux termes de la règle.
  assert.equal(8 * (allow(STATUS, 'create') + allow(POSITIONS, 'delete')), 24);
});
