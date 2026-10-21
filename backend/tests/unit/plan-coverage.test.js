// Critère T3 : un test (ou un test ignoré avec sa raison) pour chaque identifiant des sections 1 à 8
// de backend/rules-test-plan.md, et aucun identifiant orphelin.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const plan = readFileSync(fileURLToPath(new URL('../../rules-test-plan.md', import.meta.url)), 'utf8');
const rulesDir = fileURLToPath(new URL('../rules/', import.meta.url));

function planIds() {
  const section9 = plan.indexOf('## 9.');
  const body = section9 > 0 ? plan.slice(0, section9) : plan;
  return [...body.matchAll(/^\| ([A-Z]+\d+) \|/gm)].map((m) => m[1]);
}

function testedIds() {
  const ids = [];
  for (const file of readdirSync(rulesDir).filter((f) => f.endsWith('.test.js'))) {
    const source = readFileSync(`${rulesDir}${file}`, 'utf8');
    for (const m of source.matchAll(/\.(?:tc|skipped)\(\s*'([A-Z]+\d+)'/g)) ids.push(m[1]);
    for (const m of source.matchAll(/^\s*id: '([A-Z]+\d+)',/gm)) ids.push(m[1]);
  }
  return ids;
}

test('PLAN - le plan contient bien les 8 sections attendues (garde-fou du parseur)', () => {
  const ids = planIds();
  assert.ok(ids.length >= 110, `trop peu d'identifiants lus dans le plan (${ids.length})`);
  for (const prefix of ['R', 'C', 'J', 'M', 'S', 'P', 'A', 'U']) {
    assert.ok(ids.some((id) => id.startsWith(prefix)), `aucun identifiant ${prefix}* dans le plan`);
  }
});

test('PLAN - chaque identifiant du plan a un test', () => {
  const tested = new Set(testedIds());
  const missing = planIds().filter((id) => !tested.has(id));
  assert.deepEqual(missing, [], `identifiants sans test : ${missing.join(', ')}`);
});

test('PLAN - aucun identifiant testé n\'est absent du plan, aucun doublon', () => {
  const ids = testedIds();
  const inPlan = new Set(planIds());
  assert.deepEqual(ids.filter((id) => !inPlan.has(id)), [], 'identifiants testés mais absents du plan');
  const duplicates = ids.filter((id, i) => ids.indexOf(id) !== i);
  assert.deepEqual(duplicates, [], 'identifiants testés deux fois');
});
