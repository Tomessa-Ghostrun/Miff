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
  // android.yml : la configuration Firebase + le keystore de debug stable (SHA-1 fixe pour la connexion Google).
  'android.yml': ['DEBUG_KEYSTORE_B64', 'GOOGLE_SERVICES_JSON_B64'],
  // ios.yml : seulement la configuration Firebase (recréée pour la compilation, supprimée en fin de job).
  'ios.yml': ['GOOGLE_SERVICE_INFO_PLIST_B64'],
  // ios-testflight.yml : les 4 secrets de la clé d'API + les 3 de la signature manuelle (certificat Apple Distribution en .p12, son
  // mot de passe, profil App Store : docs/dev/apple-certificats-ci.md) + la configuration Firebase (mêmes règles que ios.yml).
  'ios-testflight.yml': [
    'APPLE_TEAM_ID',
    'APPSTORE_PROFILE_B64',
    'ASC_ISSUER_ID',
    'ASC_KEY_ID',
    'ASC_KEY_P8_B64',
    'DIST_CERT_P12_B64',
    'DIST_CERT_P12_PASSWORD',
    'GOOGLE_SERVICE_INFO_PLIST_B64',
  ],
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

// --- ios-testflight : signature manuelle (docs/dev/apple-certificats-ci.md) -------------------------------------------------
const testflight = workflows.find((w) => w.file === 'ios-testflight.yml');
const testflightSteps = testflight.doc.jobs['archive-and-upload'].steps;
const stepNamed = (start) => {
  const step = testflightSteps.find((s) => String(s.name).startsWith(start));
  assert.ok(step, `étape « ${start} » introuvable`);
  return step;
};
const code = (run) => stripComments(String(run ?? ''));
const PROFILE_NAME = 'Miff CI App Store';

test('TESTFLIGHT - déclenché à la main seulement, dans l\'environnement protégé « testflight »', () => {
  assert.deepEqual(Object.keys(testflight.doc.on), ['workflow_dispatch']);
  assert.equal(testflight.doc.jobs['archive-and-upload'].environment, 'testflight');
});

test('TESTFLIGHT - aucun secret hors des blocs env: des étapes', () => {
  for (const step of testflightSteps) {
    for (const field of ['run', 'if', 'with', 'name', 'working-directory']) {
      assert.doesNotMatch(JSON.stringify(step[field] ?? ''), /secrets[.][A-Za-z_]/, `${step.name} : référence à un secret dans ${field}`);
    }
  }
  // Pas au niveau du job ni du workflow non plus (sauf dans les env: d'étape, vérifié ci-dessus par exclusion).
  const job = testflight.doc.jobs['archive-and-upload'];
  assert.ok(!JSON.stringify(job.env ?? {}).includes('secrets.'), 'secret dans job.env');
  assert.ok(!JSON.stringify(testflight.doc.env ?? {}).includes('secrets.'), 'secret dans env global');
});

test('TESTFLIGHT - jamais de set -x (la ligne de commande contient des identifiants)', () => {
  for (const step of testflightSteps) {
    assert.doesNotMatch(code(step.run), /^\s*set\s+(?:-[a-zA-Z]*x|.*\s-[a-zA-Z]*x)/m, `${step.name} : set -x interdit`);
  }
});

test('TESTFLIGHT - l\'archive manuelle n\'utilise ni -allowProvisioningUpdates ni clé d\'API', () => {
  const run = code(stepNamed('Archiver (signature manuelle').run);
  assert.match(run, /xcodebuild archive/);
  assert.doesNotMatch(run, /allowProvisioningUpdates/);
  assert.doesNotMatch(run, /authenticationKey/);
  assert.doesNotMatch(run, /ASC_KEY/);
});

test('TESTFLIGHT - l\'export manuel n\'utilise la clé d\'API que pour l\'envoi, sans -allowProvisioningUpdates', () => {
  const step = stepNamed('Exporter et envoyer à TestFlight (signature manuelle');
  const run = code(step.run);
  assert.doesNotMatch(run, /allowProvisioningUpdates/);
  assert.match(run, /-authenticationKeyPath/);
  assert.match(run, /ExportOptions\.manual\.plist\.template/);
  assert.match(String(step.if), /MANUAL_SIGNING == 'yes'/);
});

test('TESTFLIGHT - le nettoyage tourne toujours et efface trousseau, fichiers déchiffrés et profil', () => {
  const step = stepNamed('Nettoyer');
  assert.equal(step.if, 'always()');
  const run = code(step.run);
  assert.match(run, /security delete-keychain/);
  assert.match(run, /rm -rf "\$MANUAL_SIGNING_DIR"/);
  assert.match(run, /MANUAL_PROFILE_UUID/);
  assert.match(run, /AuthKey_\*\.p8/);
});

test('TESTFLIGHT - le .p12 déchiffré est supprimé juste après l\'import', () => {
  const run = code(stepNamed('Installer le certificat de distribution').run);
  const importAt = run.indexOf('security import');
  const removeAt = run.indexOf('rm -f "$work/dist.p12"');
  assert.ok(importAt > 0 && removeAt > importAt, 'suppression du .p12 après l\'import');
  assert.ok(removeAt < run.indexOf('security cms -D'), 'suppression avant le décodage du profil');
});

test('TESTFLIGHT - le nom du profil est identique partout', () => {
  const places = {
    workflow: testflight.text,
    superposition: readFileSync(`${root}ios/project-signing-manual.yml`, 'utf8'),
    exportOptions: readFileSync(`${root}ios/ci/ExportOptions.manual.plist.template`, 'utf8'),
    ios: workflows.find((w) => w.file === 'ios.yml').text,
    guide: readFileSync(`${root}docs/dev/apple-certificats-ci.md`, 'utf8'),
  };
  for (const [name, text] of Object.entries(places)) {
    assert.ok(text.includes(PROFILE_NAME), `${name} : nom du profil « ${PROFILE_NAME} » absent`);
    // Aucune variante proche (autre casse, autre libellé) : toute mention « Miff CI ... » est le nom exact.
    for (const match of text.matchAll(/Miff CI [A-Za-z ]+Store/g)) {
      assert.equal(match[0], PROFILE_NAME, `${name} : variante du nom du profil « ${match[0]} »`);
    }
  }
});

test('TESTFLIGHT - les trois secrets de la signature manuelle sont décrits dans le guide et vérifiés ensemble', () => {
  const secretsStep = testflightSteps.find((s) => String(s.name).startsWith('Vérifier que les secrets'));
  for (const name of ['DIST_CERT_P12_B64', 'DIST_CERT_P12_PASSWORD', 'APPSTORE_PROFILE_B64']) {
    assert.ok(secretsStep.env?.[name], `${name} absent de l'étape de contrôle des secrets`);
  }
  assert.match(secretsStep.run, /PARTIELLE/);
});
