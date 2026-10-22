import { test } from 'node:test';
import assert from 'node:assert/strict';
import reporter, { summarize, renderMarkdown } from '../tools/id-reporter.mjs';

const pass = (name, extra = {}) => ({ type: 'test:pass', data: { name, nesting: 0, details: { duration_ms: 12.4 }, ...extra } });
const fail = (name, message) => ({
  type: 'test:fail',
  data: { name, nesting: 0, details: { duration_ms: 3, error: { cause: { message } } } },
});

test('REPORTER - une ligne par identifiant, avec résultat et remarque', async () => {
  async function* source() {
    yield pass('R1 - non connecté : aucune lecture possible');
    yield fail('C8 - seconde Miff', 'attendu : refus\nobtenu : succès');
    yield pass('A9 - contournement par deux comptes', { skip: 'Non automatisable : raison' });
    yield { type: 'test:pass', data: { name: 'sous-test', nesting: 1, details: { duration_ms: 1 } } };
  }
  let out = '';
  for await (const chunk of reporter(source())) out += chunk;
  assert.match(out, /1 réussi\(s\), 1 échoué\(s\), 1 ignoré\(s\), 3 au total/);
  assert.match(out, /\| R1 \| non connecté : aucune lecture possible \| réussi \| 12 \| +\|/);
  assert.match(out, /\| C8 \| seconde Miff \| ÉCHOUÉ \| 3 \| attendu : refus \|/);
  assert.match(out, /\| A9 \| contournement par deux comptes \| ignoré \| 12 \| Non automatisable : raison \|/);
  assert.ok(!out.includes('sous-test'));
});

test('REPORTER - un test sans identifiant reste visible', () => {
  const rows = summarize([pass('quelque chose')]);
  assert.equal(rows[0].id, '-');
  assert.match(renderMarkdown(rows), /quelque chose/);
});
