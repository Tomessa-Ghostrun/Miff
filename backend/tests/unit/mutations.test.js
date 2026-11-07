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

// DUR-3 : des règles mutées qui ne compilent pas feraient échouer tous les tests ; l'outil ne doit pas les compter comme « détectées ».
import { isInvalidMutation } from '../tools/mutation-check.mjs';

test('MUTATIONS - une erreur de compilation des règles est reconnue (mutation invalide, pas détection)', () => {
  const compileError = '{"error":{"code":400,"message":"Error compiling rules:\nL570:29 Missing conditional expression after \'if\'.","status":"INVALID_ARGUMENT"}}';
  assert.equal(isInvalidMutation(`✖ R1 - lecture\n  Error: ${compileError}`), true);
  assert.equal(isInvalidMutation('✖ R1 - lecture\n  AssertionError: Expected request to fail'), false);
});

test('MUTATIONS - DUR-3 : chaque protection du §7.3 a au moins une mutation (liste de couverture)', () => {
  const ids = new Set(MUTATIONS.map((m) => m.id));
  // Protections citées au §7.3 du ticket ; si une mutation est renommée ou supprimée, ce test le signale.
  const required = [
    'M10-ancien-code-conserve', 'M11-miff-modifiable-par-membre', 'M14-index-libere-sans-quitter', 'M18-createur-part-sans-supprimer-la-miff',
    'S1-creation-consentedAt-client', 'S2-activation-consentedAt-client', 'S3-activation-sans-version', 'J15-creation-sans-version',
    'S6-revocation-efface-consentedAt', 'S7-statut-sans-partage-actif', 'S9-statut-ecrit-par-autrui', 'S17-photo-ecrite-par-autrui',
    'S11-statut-cles-inconnues', 'S12-presence-libre', 'S13-presenceAt-passe', 'S14-updatedAt-client', 'S16-photo-taille-max',
    'S18-purge-statut-par-membre', 'P9-rayon-min', 'P9-lat-max', 'P8-type-modifiable', 'P11-createdBy-modifiable', 'P12-createdAt-client',
    'P2-lieu-libre-gratuit', 'P4-lieu-risque-gratuit', 'A10-premium-expire', 'A1-entitlements-ecriture', 'C9-index-miffId-sans-premium',
    'J5-rejoindre-code-autre-miff', 'J7-rejoindre-code-invente', 'J8-rejoindre-en-proprietaire', 'J9-rejoindre-sans-index',
    'C13-code-nom-different', 'C13-code-createdAt-client', 'R4-liste-users', 'R4-liste-codes', 'R12-liste-photos', 'R11-refus-par-defaut',
    'R6-profil-lisible-par-autrui', 'U1-termsAcceptedAt-creation-client', 'U2-profil-cles-creation', 'U5-appareil-plateforme',
  ];
  assert.deepEqual(required.filter((id) => !ids.has(id)), []);
  assert.ok(MUTATIONS.length >= 180, `la liste doit couvrir le §7.3 (${MUTATIONS.length} mutations)`);
});
