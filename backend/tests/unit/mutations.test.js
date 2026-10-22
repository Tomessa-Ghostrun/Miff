// Sans émulateur : chaque mutation de tests/tools/mutations.mjs s'applique bien à firestore.rules
// (si les règles changent et qu'un motif disparaît, ce test le signale au lieu d'une mutation sans effet).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { MUTATIONS } from '../tools/mutations.mjs';
import { applyMutation } from '../tools/mutation-check.mjs';

const rules = readFileSync(fileURLToPath(new URL('../../firestore.rules', import.meta.url)), 'utf8').replace(/\r\n/g, '\n');
const rulesDir = fileURLToPath(new URL('../rules/', import.meta.url));
const testSources = readdirSync(rulesDir).map((f) => readFileSync(`${rulesDir}${f}`, 'utf8')).join('\n');

test('MUTATIONS - identifiants uniques, chaque mutation modifie les règles', () => {
  const ids = MUTATIONS.map((m) => m.id);
  assert.equal(new Set(ids).size, ids.length, 'identifiant de mutation en double');
  for (const m of MUTATIONS) {
    assert.notEqual(applyMutation(rules, m), rules, m.id);
  }
});

test('MUTATIONS - les tests visés existent', () => {
  for (const m of MUTATIONS) {
    for (const id of m.tests) {
      assert.match(testSources, new RegExp(`\\.(?:tc|skipped)\\(\\s*'${id}'`), `${m.id} : test ${id} introuvable`);
    }
  }
});
