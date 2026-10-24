// Test par mutation des règles : affaiblit volontairement UNE protection de firestore.rules (copie temporaire
// en mémoire/dans le dossier temporaire, le fichier du dépôt n'est jamais modifié), relance les tests censés la
// protéger et exige qu'au moins un passe au rouge. Une mutation qui « survit » = un test trop faible.
//
// Usage (dans l'émulateur) : npm run test:mutation [-- --only <id-mutation>[,<id>...]] ; sans émulateur : --check
//   --check : vérifie seulement que chaque motif de mutation existe dans firestore.rules (aucun test lancé).
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { assertSafeEnvironment, testProjectId } from '../helpers/guard.js';
import { MUTATIONS } from './mutations.mjs';

const backendDir = fileURLToPath(new URL('../../', import.meta.url));
const rulesPath = join(backendDir, 'firestore.rules');
const rulesDir = join(backendDir, 'tests', 'rules');

export function applyMutation(rules, mutation) {
  let out = rules;
  for (const { find, replace, nth, of } of mutation.edits) {
    const positions = [];
    for (let i = out.indexOf(find); i !== -1; i = out.indexOf(find, i + find.length)) positions.push(i);
    if (positions.length !== of) {
      throw new Error(`${mutation.id} : motif trouvé ${positions.length} fois au lieu de ${of} : ${find}`);
    }
    const at = positions[nth - 1];
    out = out.slice(0, at) + replace + out.slice(at + find.length);
  }
  if (out === rules) throw new Error(`${mutation.id} : la mutation ne change rien`);
  return out;
}

/** Vrai si la sortie des tests montre que l'émulateur a refusé de charger les règles mutées (erreur de syntaxe). */
export const isInvalidMutation = (output) => /Error compiling rules/.test(output);

const readRulesText = () => readFileSync(rulesPath, 'utf8').replace(/\r\n/g, '\n');

function filesFor(ids) {
  const files = new Set();
  for (const file of readdirSync(rulesDir).filter((f) => f.endsWith('.test.js'))) {
    const source = readFileSync(join(rulesDir, file), 'utf8');
    if (ids.some((id) => new RegExp(`\\.(?:tc|skipped)\\(\\s*'${id}'`).test(source))) files.add(file);
  }
  return [...files].sort().map((f) => `tests/rules/${f}`);
}

function runTests(ids, rulesFile) {
  const pattern = `^(${ids.join('|')}) - `;
  const result = spawnSync(
    process.execPath,
    ['--test', '--test-concurrency=1', `--test-name-pattern=${pattern}`, ...filesFor(ids)],
    { cwd: backendDir, encoding: 'utf8', env: { ...process.env, ...(rulesFile ? { MIFF_RULES_FILE: rulesFile } : {}) } },
  );
  const count = (label) => Number(new RegExp(`ℹ ${label} (\\d+)`).exec(result.stdout)?.[1] ?? 0);
  return { status: result.status, pass: count('pass'), fail: count('fail'), skipped: count('skipped'), output: result.stdout };
}

function main(argv) {
  const only = argv.includes('--only') ? argv[argv.indexOf('--only') + 1].split(',') : null;
  const checkOnly = argv.includes('--check');
  const selected = MUTATIONS.filter((m) => !only || only.includes(m.id));
  if (only && selected.length !== only.length) {
    throw new Error(`--only : mutation(s) inconnue(s) : ${only.filter((id) => !MUTATIONS.some((m) => m.id === id)).join(', ')}`);
  }
  const rules = readRulesText();

  if (checkOnly) {
    for (const m of selected) applyMutation(rules, m);
    for (const m of selected) {
      const missing = m.tests.filter((id) => filesFor([id]).length === 0);
      if (missing.length > 0) throw new Error(`${m.id} : aucun test pour ${missing.join(', ')}`);
    }
    console.log(`Mutations : ${selected.length} motif(s) valides dans firestore.rules, tests cibles présents.`);
    return 0;
  }

  assertSafeEnvironment({ projectId: testProjectId() });
  const tmp = mkdtempSync(join(tmpdir(), 'miff-mutation-'));
  const problems = [];
  const tally = { killed: 0, equivalent: 0, survivors: 0 };
  try {
    const allIds = [...new Set(selected.flatMap((m) => m.tests))];
    const baseline = runTests(allIds);
    console.log(`Référence (règles intactes) : ${baseline.pass} réussi(s), ${baseline.fail} échoué(s)`);
    if (baseline.status !== 0 || baseline.pass === 0) {
      console.error(baseline.output.slice(-3000));
      throw new Error('les tests ciblés doivent d\'abord passer sur les règles intactes');
    }
    for (const m of selected) {
      const mutated = join(tmp, `${m.id}.rules`);
      writeFileSync(mutated, applyMutation(rules, m));
      const r = runTests(m.tests, mutated);
      if (isInvalidMutation(r.output)) {
        // Des règles qui ne compilent pas font échouer TOUS les tests : ce ne serait pas une détection mais une mutation cassée.
        tally.survivors += 1;
        problems.push(m.id);
        console.log(`KO  ${m.id} (${m.tests.join(', ')}) : MUTATION INVALIDE : les règles mutées ne compilent pas (corriger le motif)`);
        continue;
      }
      const killed = r.fail > 0;
      if (killed) tally.killed += 1;
      else if (m.equivalent) tally.equivalent += 1;
      else tally.survivors += 1;
      let verdict;
      if (killed) verdict = 'tué (test rouge)';
      else if (m.equivalent) verdict = `survit (mutant équivalent : ${m.equivalent})`;
      else {
        verdict = 'SURVIT : aucun test ne détecte cet affaiblissement';
        problems.push(m.id);
      }
      if (killed && m.equivalent) {
        verdict += ' [ATTENTION : marqué équivalent mais détecté, retirer la mention]';
        problems.push(m.id);
      }
      console.log(`${killed ? 'OK ' : m.equivalent ? 'EQ ' : 'KO '} ${m.id} (${m.tests.join(', ')}) : ${verdict}`);
    }
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
  console.log(
    `\nBilan : ${selected.length} mutation(s), ${tally.killed} détectée(s), ${tally.equivalent} équivalente(s) documentée(s), ${tally.survivors} survivante(s).`,
  );
  if (problems.length > 0) {
    console.error(`\nMutations à traiter : ${problems.join(', ')}`);
    return 1;
  }
  console.log('\nToutes les mutations sont détectées (ou documentées comme équivalentes).');
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 2;
  }
}
