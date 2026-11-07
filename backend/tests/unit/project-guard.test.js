// Cas H6 / critère R16 : aucune commande firebase du dépôt ne peut viser un autre projet qu'un projet de démonstration,
// et la CLI n'a plus de projet par défaut. Tests rapides, sans émulateur, sans réseau.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';
import { findFirebaseCommands, violationsIn } from '../helpers/firebase-commands.js';

const backend = fileURLToPath(new URL('../../', import.meta.url));
const root = fileURLToPath(new URL('../../../', import.meta.url));
const REAL_PROJECTS = ['miff-3d283', 'ghostrun-app'];
const SKIPPED_DIRS = ['node_modules', '.git', 'build', '.gradle', 'Pods', 'DerivedData'];

// --- Le détecteur lui-même : il doit voir les erreurs (sinon les tests suivants ne prouvent rien) ---------------

test('H6 - le détecteur refuse un déploiement, une absence de --project et un vrai projet', () => {
  const bad = [
    'firebase deploy --only firestore:rules --project miff-3d283',
    'firebase deploy --project demo-miff-rules',
    'firebase emulators:exec --only firestore "npm run test:rules"',
    'firebase emulators:exec --only firestore --project miff-3d283 "npm run test:rules"',
    'firebase emulators:exec --only firestore --project ghostrun-app "x"',
    'npx firebase emulators:exec --only firestore "x"',
    'npx --yes firebase-tools deploy',
    'npm ci && firebase use miff-3d283',
    'cd backend; firebase firestore:delete --all-collections',
    'firebase emulators:exec --project demo-miff-rules --token abc "x"',
    'firebase emulators:exec \\\n  --only firestore \\\n  "npm test"',
    'echo ok | firebase login',
    'FOO=1 $(firebase projects:list)',
  ];
  for (const line of bad) assert.notDeepEqual(violationsIn(line), [], `devrait être refusé : ${line}`);
});

test("H6 - le détecteur accepte les commandes d'émulateur sur un projet demo-* et ignore les simples mentions", () => {
  const good = [
    'firebase emulators:exec --only firestore --project demo-miff-rules "npm run test:rules"',
    'npx firebase emulators:start --only firestore --project=demo-x',
    'firebase emulators:exec --only firestore -P demo-miff-rules "x"',
    'firebase emulators:exec --only firestore \\\n  --project demo-miff-rules "npm test"',
    'echo "Le PM doit lancer : firebase apps:sdkconfig ANDROID ID --project miff-3d283"',
    '# firebase deploy --project miff-3d283 (commentaire)',
    'npm run test:emulator',
  ];
  for (const line of good) assert.deepEqual(violationsIn(line), [], `devrait être accepté : ${line}`);
  assert.equal(findFirebaseCommands('firebase emulators:exec --project demo-a "x"').length, 1);
});

// --- Le dépôt -------------------------------------------------------------------------------------------------

test('H6 - .firebaserc ne désigne aucun projet par défaut, ni même un projet unique (qui deviendrait implicite)', () => {
  const path = join(backend, '.firebaserc');
  if (!existsSync(path)) return; // l'absence du fichier est aussi sûre
  const text = readFileSync(path, 'utf8');
  const doc = JSON.parse(text);
  assert.ok(!JSON.stringify(doc).includes('"default"'), '.firebaserc ne doit pas contenir de projet "default"');
  // Constaté le 2026-10-23 (firebase-tools 15.34.0) : avec UN SEUL alias déclaré, la CLI le prend comme projet implicite
  // (emulators:exec sans --project a utilisé miff-3d283). Aucun alias n'est donc déclaré ; le projet se passe toujours avec --project.
  assert.deepEqual(Object.keys(doc.projects ?? {}), [], '.firebaserc ne doit déclarer aucun projet (un alias unique devient implicite)');
  assert.equal(doc.targets, undefined, '.firebaserc : pas de "targets"');
  for (const id of REAL_PROJECTS) assert.ok(!text.includes(id), `.firebaserc ne doit pas mentionner ${id}`);
});

test('H6 - firebase.json et .firebaserc : backend/ (émulateur seulement) et web/ (Hosting, verrouillé sur miff-3d283), nulle part ailleurs', () => {
  const found = [];
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      if (SKIPPED_DIRS.includes(name)) continue;
      const full = join(dir, name);
      if (statSync(full).isDirectory()) walk(full);
      else if (name === '.firebaserc' || name === 'firebase.json') found.push(relative(root, full).replaceAll('\\', '/'));
    }
  };
  walk(root);
  assert.deepEqual(found.sort(), ['backend/.firebaserc', 'backend/firebase.json', 'web/.firebaserc', 'web/firebase.json']);
  const config = readFileSync(join(backend, 'firebase.json'), 'utf8');
  for (const id of REAL_PROJECTS) assert.ok(!config.includes(id), `firebase.json ne doit pas mentionner ${id}`);
  // Site web (AUTH-W) : seule exception, Hosting uniquement, projet miff-3d283 uniquement (jamais ghostrun-app),
  // et déploiement protégé par le garde-fou « predeploy » (aucun champ à compléter, jamais en CI, accord du PM).
  const webConfig = JSON.parse(readFileSync(join(root, 'web', 'firebase.json'), 'utf8'));
  assert.deepEqual(Object.keys(webConfig), ['hosting'], 'web/firebase.json ne configure que Hosting');
  assert.deepEqual(webConfig.hosting.predeploy, ['node ../scripts/web-predeploy-check.mjs']);
  assert.deepEqual(JSON.parse(readFileSync(join(root, 'web', '.firebaserc'), 'utf8')), { projects: { default: 'miff-3d283' } });
  assert.ok(!/ghostrun/i.test(readFileSync(join(root, 'web', 'firebase.json'), 'utf8')));
});

test("H6 - package.json : tout script qui appelle firebase est une commande d'émulateur sur un projet demo-*", () => {
  const scripts = JSON.parse(readFileSync(join(backend, 'package.json'), 'utf8')).scripts;
  let count = 0;
  for (const [name, command] of Object.entries(scripts)) {
    assert.deepEqual(violationsIn(command, `npm run ${name}`), []);
    count += findFirebaseCommands(command).length;
    assert.ok(!/deploy/.test(command), `npm run ${name} : un script npm ne déploie jamais`);
  }
  assert.ok(count >= 2, 'les scripts test:emulator et test:mutation appellent firebase : le détecteur doit les voir');
});

test('H6 - workflows : jamais de déploiement, de jeton ni de compte de service ; toute commande firebase vise demo-*', () => {
  const dir = join(root, '.github', 'workflows');
  for (const file of readdirSync(dir).filter((f) => f.endsWith('.yml'))) {
    const source = readFileSync(join(dir, file), 'utf8');
    const doc = parse(source);
    for (const [jobName, job] of Object.entries(doc.jobs)) {
      for (const step of job.steps) {
        if (step.run) assert.deepEqual(violationsIn(step.run, `${file}/${jobName}`), []);
      }
    }
    const live = source
      .split('\n')
      .filter((l) => !l.trim().startsWith('#'))
      .join('\n');
    assert.ok(
      !/firebase\s+deploy|FIREBASE_TOKEN|GOOGLE_APPLICATION_CREDENTIALS|FIREBASE_SERVICE_ACCOUNT|google-github-actions\/auth|w9jds\/firebase-action|FirebaseExtended\/action-hosting-deploy/i.test(live),
      `${file} : aucun déploiement ni identifiant de déploiement dans un workflow`,
    );
  }
});

test('H6 - le workflow des règles lance les tests via le script npm contrôlé (donc via --project demo-*)', () => {
  const text = readFileSync(join(root, '.github', 'workflows', 'firestore-rules.yml'), 'utf8');
  assert.match(text, /npm run test:emulator/);
  assert.deepEqual(findFirebaseCommands(text.replace(/^\s*#.*$/gm, '')), [], 'pas d\'appel direct à firebase : tout passe par npm run');
});

test('H6 - scripts shell / PowerShell du dépôt : aucune commande firebase hors émulateur', () => {
  const offenders = [];
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      if ([...SKIPPED_DIRS, 'docs'].includes(name)) continue;
      const full = join(dir, name);
      if (statSync(full).isDirectory()) walk(full);
      else if (/\.(sh|bash|ps1|cmd|bat)$|^Makefile$/.test(name)) offenders.push(...violationsIn(readFileSync(full, 'utf8'), relative(root, full)));
    }
  };
  walk(root);
  assert.deepEqual(offenders, []);
});
