// Vérifie, sans émulateur, la transformation en mémoire des règles utilisée pour mesurer les lectures.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { injectPads, _injectOneForTests, padExpression, PAD_COLLECTION } from '../helpers/pads.js';
import { H } from '../helpers/hosts.js';

const rulesPath = fileURLToPath(new URL('../../firestore.rules', import.meta.url));
const rules = readFileSync(rulesPath, 'utf8');

const stripComments = (text) => text.replace(/\/\/[^\n]*/g, '');
const balance = (text, open, close) => stripComments(text).split(open).length - stripComments(text).split(close).length;

test('PADS - k = 0 laisse les règles identiques', () => {
  assert.equal(injectPads(rules, [H.statusList], 0), rules);
});

test('PADS - chaque règle hôte est trouvée dans le bon bloc, sous la bonne instruction allow', () => {
  for (const [name, host] of Object.entries(H)) {
    const { rules: out, insertedAt } = _injectOneForTests(rules, host, padExpression([0]));
    const blockStart = rules.indexOf(host.match);
    assert.ok(blockStart >= 0, `${name} : en-tête introuvable`);
    assert.ok(insertedAt > blockStart, `${name} : insertion avant le bloc`);
    // Pas d'autre bloc "match" entre l'en-tête et l'insertion : on est bien dans le bon bloc.
    assert.ok(!rules.slice(blockStart + host.match.length, insertedAt).includes('match /'), `${name} : mauvais bloc`);
    // L'insertion suit immédiatement "allow <méthodes>: if ".
    assert.match(rules.slice(Math.max(0, insertedAt - 60), insertedAt), /allow\s+[a-z, ]+:\s*if\s+$/, name);
    assert.equal(out.split(PAD_COLLECTION).length - 1, 1, `${name} : une lecture factice attendue`);
  }
});

test('PADS - k lectures factices, réparties sur les hôtes, parenthèses équilibrées', () => {
  for (const k of [1, 2, 7, 20, 21]) {
    const out = injectPads(rules, [H.joinCodesCreate, H.membershipsCreate], k);
    assert.equal(out.split(PAD_COLLECTION).length - 1, k);
    assert.equal(balance(out, '(', ')'), balance(rules, '(', ')'), `parenthèses déséquilibrées (k=${k})`);
    assert.equal(balance(out, '{', '}'), balance(rules, '{', '}'), `accolades déséquilibrées (k=${k})`);
    assert.equal(out.split('allow ').length, rules.split('allow ').length, 'nombre de règles allow modifié');
  }
});

test('PADS - le fichier firestore.rules n\'est jamais modifié sur disque', () => {
  injectPads(rules, [H.statusDelete], 5);
  assert.equal(readFileSync(rulesPath, 'utf8'), rules);
});

test('PADS - un hôte inconnu fait échouer clairement', () => {
  assert.throws(() => injectPads(rules, [{ match: 'match /nope/{x} {', method: 'get' }], 1), /introuvable/);
  assert.throws(() => injectPads(rules, [{ match: 'match /places/{placeId} {', method: 'inconnue' }], 1), /aucune règle/);
});

// DUR-3 : lectures factices par hôte (mesure des lots sans dépasser la limite de 10 par écriture).
import { injectPadCounts } from '../helpers/pads.js';

test('PADS - injectPadCounts : nombre exact par hôte, indices tous distincts, règles équilibrées', () => {
  const hosts = [H.membersDelete, H.statusDelete, H.photosDelete];
  const out = injectPadCounts(rules, hosts, [3, 0, 5]);
  assert.equal(out.split(PAD_COLLECTION).length - 1, 8);
  const indices = [...out.matchAll(new RegExp(`${PAD_COLLECTION}/p([0-9]+)`, 'g'))].map((m) => m[1]);
  assert.equal(new Set(indices).size, 8, 'indices de lectures factices en double');
  assert.equal(balance(out, '(', ')'), balance(rules, '(', ')'));
  assert.equal(out.split('allow ').length, rules.split('allow ').length);
  assert.equal(injectPadCounts(rules, hosts, [0, 0, 0]), rules);
  assert.throws(() => injectPadCounts(rules, hosts, [1, 2]), /un nombre de lectures factices par hôte/);
});
