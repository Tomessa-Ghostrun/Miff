// Critère T6 (vérifié sans exécuter la CI) : déclencheurs, permissions, épinglage des actions, durées.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const workflowsDir = `${root}.github/workflows/`;
const stripComments = (text) =>
  text
    .split('\n')
    .filter((line) => !line.trim().startsWith('#'))
    .join('\n');
const load = (path) => parse(readFileSync(path, 'utf8'));
const workflows = readdirSync(workflowsDir)
  .filter((f) => f.endsWith('.yml'))
  .map((f) => ({ file: f, text: readFileSync(`${workflowsDir}${f}`, 'utf8'), doc: load(`${workflowsDir}${f}`) }));

test('WORKFLOWS - hygiene.yml et firestore-rules.yml existent', () => {
  const names = workflows.map((w) => w.file);
  assert.ok(names.includes('hygiene.yml'));
  assert.ok(names.includes('firestore-rules.yml'));
});

// Un envoi signé vers TestFlight ne doit pas être interrompu à mi-course par une exécution suivante :
// ce workflow (manuel) garde cancel-in-progress: false, et lui seul.
const KEEP_RUNNING = new Set(['ios-testflight.yml']);

// Seuls ces workflows ont le droit de lire des secrets, et seulement ceux-ci (liste fermée, noms exacts).
// Tout autre workflow, ou tout autre secret, fait échouer le test : l'ajout d'un secret doit être une décision visible en revue.
const SECRETS_ALLOWED = {
  'android.yml': ['GOOGLE_SERVICES_JSON_B64'],
  'ios-testflight.yml': ['APPLE_TEAM_ID', 'ASC_ISSUER_ID', 'ASC_KEY_ID', 'ASC_KEY_P8_B64'],
};

test('WORKFLOWS - permissions minimales, annulation des exécutions obsolètes, jamais pull_request_target', () => {
  for (const { file, doc, text } of workflows) {
    assert.deepEqual(doc.permissions, { contents: 'read' }, `${file} : permissions`);
    assert.equal(doc.concurrency?.['cancel-in-progress'], !KEEP_RUNNING.has(file), `${file} : concurrency`);
    assert.ok(doc.concurrency?.group, `${file} : groupe de concurrency`);
    assert.ok(!('pull_request_target' in doc.on), `${file} : pull_request_target interdit`);
    assert.ok(!stripComments(text).includes('pull_request_target'), `${file} : pull_request_target interdit (texte)`);
    for (const [name, job] of Object.entries(doc.jobs)) {
      assert.ok(job['timeout-minutes'], `${file}/${name} : timeout-minutes manquant`);
    }
  }
});

test('WORKFLOWS - toute action tierce est épinglée par empreinte de commit complète', () => {
  for (const { file, doc } of workflows) {
    for (const [jobName, job] of Object.entries(doc.jobs)) {
      for (const step of job.steps) {
        if (!step.uses) continue;
        if (step.uses.startsWith('./')) continue;
        assert.match(step.uses, /^[\w.-]+\/[\w./-]+@[0-9a-f]{40}$/, `${file}/${jobName} : ${step.uses} n'est pas épinglée`);
      }
    }
  }
});

test('WORKFLOWS - aucun secret, sauf liste fermée (hygiene et firestore-rules n\'en ont jamais besoin)', () => {
  for (const { file, text } of workflows) {
    const used = [...new Set([...stripComments(text).matchAll(/secrets\.([A-Za-z0-9_]+)/g)].map((m) => m[1]))].sort();
    assert.deepEqual(used, SECRETS_ALLOWED[file] ?? [], `${file} : secrets utilisés différents de la liste autorisée`);
  }
});

test('WORKFLOWS - firestore-rules : filtre de chemins backend/**, Java 21 Temurin, tests lancés', () => {
  const { doc, text } = workflows.find((w) => w.file === 'firestore-rules.yml');
  assert.ok(doc.on.pull_request.paths.includes('backend/**'));
  assert.match(text, /distribution: temurin/);
  assert.match(text, /java-version: '21'/);
  assert.match(text, /npm ci/);
  assert.match(text, /npm run test:emulator/);
  assert.match(text, /upload-artifact/);
  assert.match(text, /GITHUB_STEP_SUMMARY/);
});

test('WORKFLOWS - hygiene : sur toute PR (aucun filtre de chemins), gitleaks épinglé avec somme de contrôle', () => {
  const { doc, text } = workflows.find((w) => w.file === 'hygiene.yml');
  assert.ok(doc.on.pull_request !== undefined);
  assert.ok(!doc.on.pull_request?.paths, 'hygiene doit couvrir toute PR');
  assert.match(text, /GITLEAKS_SHA256: [0-9a-f]{64}/);
  assert.match(text, /sha256sum --check/);
  assert.ok(!text.includes('gitleaks-action'), 'gitleaks-action interdit (licence payante pour les organisations)');
  assert.match(text, /check-forbidden-files\.mjs/);
  assert.match(text, /check-i18n-parity\.mjs/);
});

test('WORKFLOWS - web : filtre web/** et docs/legal/**, tests node:test, aucun déploiement ni commande Firebase', () => {
  const { doc, text } = workflows.find((w) => w.file === 'web.yml');
  const paths = doc.on.pull_request.paths;
  assert.ok(paths.includes('web/**') && paths.includes('docs/legal/**'));
  const code = stripComments(text);
  assert.match(code, /node scripts\/build-web\.mjs/);
  assert.match(code, /node --test "web\/tests\/\*\.test\.mjs"/);
  assert.ok(!/firebase|deploy/i.test(code.replace(/web\/tests|web-predeploy-check\.mjs|déploiement/gi, '')), 'web.yml ne déploie jamais et n\'appelle pas Firebase');
  assert.ok(!text.includes('MIFF_WEB_API_KEY'), 'la clé web n\'est jamais fournie à la CI');
});

test('WORKFLOWS - la durée de chaque job est consignée dans le résumé', () => {
  for (const { file, doc } of workflows) {
    for (const [jobName, job] of Object.entries(doc.jobs)) {
      const logsDuration = job.steps.some((s) => /JOB_START/.test(s.run ?? '') && /GITHUB_STEP_SUMMARY/.test(s.run ?? ''));
      assert.ok(logsDuration, `${file}/${jobName} : durée non consignée`);
    }
  }
});

test('DEPENDABOT - github-actions et npm (backend) déclarés ; gradle / swift dès qu\'un manifeste existe', () => {
  const doc = load(`${root}.github/dependabot.yml`);
  const ecosystems = Object.fromEntries(doc.updates.map((u) => [u['package-ecosystem'], u.directory]));
  assert.equal(ecosystems['github-actions'], '/');
  assert.equal(ecosystems.npm, '/backend');
  // Une entrée vers un dossier sans manifeste fait échouer Dependabot ; à l'inverse, un manifeste sans entrée
  // n'est pas surveillé. Ce test oblige à ajouter l'entrée au moment où android/ ou ios/ reçoivent leur manifeste.
  const has = (dir, names) => existsSync(`${root}${dir}`) && readdirSync(`${root}${dir}`).some((f) => names.includes(f));
  const androidHasManifest = has('android', ['settings.gradle.kts', 'settings.gradle', 'build.gradle.kts', 'build.gradle']);
  const iosHasManifest = has('ios', ['Package.swift', 'Package.resolved']);
  assert.equal(ecosystems.gradle !== undefined, androidHasManifest, 'gradle : entrée Dependabot à synchroniser avec android/');
  assert.equal(ecosystems.swift !== undefined, iosHasManifest, 'swift : entrée Dependabot à synchroniser avec ios/');
});
