// Sans émulateur : la recherche par dichotomie de la mesure de lectures (DUR-3, R15).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { bisectMax } from '../helpers/measure.js';

// Une opération qui utilise `base` lectures sous une limite : elle réussit tant que base + k <= limite.
const operation = (base, limit) => async (k) => base + k <= limit;

test('MEASURE - bisectMax trouve la marge exacte pour toutes les bases possibles', async () => {
  for (const limit of [10, 20]) {
    for (let base = 0; base <= limit; base += 1) {
      assert.equal(await bisectMax(operation(base, limit), limit + 1), limit - base, `limite ${limit}, base ${base}`);
    }
  }
});

test('MEASURE - bisectMax : opération de référence en échec = -1 ; lectures factices jamais comptées = borne atteinte', async () => {
  assert.equal(await bisectMax(async () => false, 11), -1);
  assert.equal(await bisectMax(async () => true, 11), 11, 'toutes les valeurs passent : mesure non concluante, la borne est rendue');
});

test('MEASURE - bisectMax n\'appelle pas l\'opération plus que nécessaire (dichotomie)', async () => {
  let calls = 0;
  await bisectMax(async (k) => { calls += 1; return 3 + k <= 20; }, 21);
  assert.ok(calls <= 8, `${calls} essais : la dichotomie doit en faire au plus 8 pour 21 valeurs`);
});
