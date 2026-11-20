// Budget de lectures de règles du JOURNAL DE NOTIFICATIONS (CNC-B, ticket docs/tickets/centre-notifications-complet.md §8.3 : « 0 lecture de règles »). Sans émulateur.
// Comptages SANS CACHE calculés sur le texte de firestore.rules (borne haute). Limite : 20 par lot, 10 par lecture.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { makeCostModel, RULES_FILE, stripComments } from '../helpers/rule-budget.js';

const model = makeCostModel();
const allow = (header, method) => model.costOfAllow(header, method);
const NOTIFICATIONS = 'match /notifications/{entryId} {';
const text = stripComments(readFileSync(RULES_FILE, 'utf8'));
const squash = (s) => s.replace(/\s+/g, ' ').trim();
const SELF = 'request.auth != null && request.auth.uid == uid';

test('BUDGET JOURNAL - 0 lecture de règles pour lire, lister, supprimer (create et update sont refusés sans rien lire) : un lot de suppression de 100 lignes reste à 0 sur 20', () => {
  const cost = Object.fromEntries(['get', 'list', 'create', 'update', 'delete'].map((m) => [m, allow(NOTIFICATIONS, m)]));
  assert.deepEqual(cost, { get: 0, list: 0, create: 0, update: 0, delete: 0 });
  assert.equal(100 * allow(NOTIFICATIONS, 'delete'), 0);
});

test('BUDGET JOURNAL - confidentialité : lire = propriétaire seul ; liste = propriétaire seul ET limite explicite de 100 au plus ; supprimer = propriétaire seul ; AUCUNE écriture client', () => {
  const rules = model.allowRules(NOTIFICATIONS);
  assert.equal(squash(rules.get), SELF);
  assert.equal(squash(rules.list), `${SELF} && request.query.limit <= 100`);
  assert.equal(squash(rules.delete), SELF);
  assert.equal(squash(rules.create), 'false');
  assert.equal(squash(rules.update), 'false');
  // Aucune règle ne regarde une Miff, un profil, une session ou un abonnement : le journal n'ouvre aucune porte vers d'autres données.
  const bloc = text.slice(text.indexOf(NOTIFICATIONS), text.indexOf('}', text.indexOf('allow create, update: if false;')));
  assert.doesNotMatch(bloc, /\b(get|exists|getAfter|existsAfter|isMember|isOwner|isOwnerOneRead|isPremiumOneRead|miffAlive)\(/);
});

test('BUDGET JOURNAL - un seul bloc `notifications`, sous users/{uid}, aucune règle de groupe de collections (aucune lecture croisée de tous les journaux)', () => {
  assert.equal(text.split(NOTIFICATIONS).length, 2, 'un seul bloc');
  assert.ok(text.indexOf(NOTIFICATIONS) > text.indexOf('match /users/{uid} {') && text.indexOf(NOTIFICATIONS) < text.indexOf('match /entitlements/{uid} {'), 'sous users/{uid}');
  assert.doesNotMatch(text, /\{path=\*\*\}\/notifications|\/\{path=\*\*\}/, 'aucun joker récursif : jamais de lecture par groupe de collections');
});
