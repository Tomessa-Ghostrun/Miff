// SRV-1 : configuration des Cloud Functions vérifiée sans réseau ni émulateur.
// - firebase.json : la section "firestore" (règles, index) n'a pas bougé ; la section "functions" est conforme ;
// - dépendances minimales, versions épinglées, lockfile présent, runtime cohérent ;
// - le workflow des fonctions ne déploie jamais, n'a aucun secret ;
// - la sortie de compilation n'est pas versionnée.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const backend = fileURLToPath(new URL('../../', import.meta.url));
const root = fileURLToPath(new URL('../../../', import.meta.url));
const read = (path) => readFileSync(path, 'utf8');
const firebaseJson = JSON.parse(read(`${backend}firebase.json`));
const functionsPackage = JSON.parse(read(`${backend}functions/package.json`));
const EXACT = /^\d+\.\d+\.\d+$/;

test('SRV-1 - firebase.json : la section firestore (règles et index) est inchangée', () => {
  assert.deepEqual(firebaseJson.firestore, { rules: 'firestore.rules', indexes: 'firestore.indexes.json' });
  assert.deepEqual(Object.keys(firebaseJson).sort(), ['firestore', 'functions'], 'aucune autre section (hosting, emulators avec ports...) sans décision');
});

test('SRV-1 - firebase.json : une seule base de code "default", source functions, Node 24, build avant envoi, rien de superflu envoyé', () => {
  assert.ok(Array.isArray(firebaseJson.functions) && firebaseJson.functions.length === 1);
  const [f] = firebaseJson.functions;
  assert.equal(f.source, 'functions');
  assert.equal(f.codebase, 'default');
  assert.equal(f.runtime, 'nodejs24');
  assert.deepEqual(f.predeploy, ['npm --prefix "$RESOURCE_DIR" ci --ignore-scripts', 'npm --prefix "$RESOURCE_DIR" run build']);
  for (const motif of ['node_modules', '.git', 'src', 'test']) assert.ok(f.ignore.includes(motif), `ignore doit contenir ${motif}`);
  assert.ok(existsSync(`${backend}functions/package.json`));
  assert.equal(functionsPackage.main, 'lib/index.js');
  assert.ok(!f.ignore.includes('lib'), 'lib/ (code compilé) doit être envoyé');
});

test('SRV-1 - functions/package.json : runtime Node 24 cohérent avec firebase.json, aucun script de déploiement', () => {
  assert.equal(functionsPackage.engines?.node, '24');
  assert.equal(functionsPackage.private, true);
  for (const [nom, commande] of Object.entries(functionsPackage.scripts)) {
    assert.ok(!/deploy|firebase\s/.test(commande), `functions : npm run ${nom} ne déploie pas et n'appelle pas la CLI Firebase`);
  }
});

test("SRV-1 - dépendances minimales et versions EXACTES (pas de ^ ni ~) : en ajouter une est une décision visible en revue", () => {
  assert.deepEqual(Object.keys(functionsPackage.dependencies).sort(), ['firebase-admin', 'firebase-functions']);
  assert.deepEqual(Object.keys(functionsPackage.devDependencies).sort(), ['@types/node', 'typescript']);
  for (const [nom, version] of Object.entries({ ...functionsPackage.dependencies, ...functionsPackage.devDependencies })) {
    assert.match(version, EXACT, `${nom} : version épinglée attendue (reçu ${version})`);
  }
  assert.match(functionsPackage.devDependencies['@types/node'], /^24\./, '@types/node doit suivre le runtime (Node 24)');
});

test('SRV-1 - package-lock.json présent et aligné sur package.json', () => {
  const lock = JSON.parse(read(`${backend}functions/package-lock.json`));
  assert.ok(lock.lockfileVersion >= 3);
  const racine = lock.packages[''];
  assert.deepEqual(racine.dependencies, functionsPackage.dependencies);
  assert.deepEqual(racine.devDependencies, functionsPackage.devDependencies);
});

test('SRV-1 - lib/ (sortie de compilation) et node_modules ne sont pas versionnés', () => {
  const ignore = read(`${root}.gitignore`);
  assert.match(ignore, /^backend\/functions\/lib\/$/m);
  assert.match(ignore, /^node_modules\/$/m);
});

test('SRV-1 - workflow functions.yml : déclencheur par chemins, jamais de secret, jamais de déploiement, tests lancés', () => {
  const texte = read(`${root}.github/workflows/functions.yml`);
  const code = texte.split('\n').filter((l) => !l.trim().startsWith('#')).join('\n');
  const doc = parse(texte);
  assert.ok(doc.on.pull_request.paths.includes('backend/functions/**'));
  assert.ok(!/secrets\./.test(code), 'aucun secret');
  assert.ok(!/deploy/i.test(code), 'aucune mention de déploiement hors commentaires');
  assert.match(code, /npm run test:functions:unit/);
  assert.match(code, /npm run test:functions\b/);
  assert.match(code, /npm ci --ignore-scripts/);
});

test('SRV-1 - le script test:functions vise un projet de démonstration et construit avant de tester', () => {
  const scripts = JSON.parse(read(`${backend}package.json`)).scripts;
  assert.match(scripts['test:functions'], /--project demo-miff-functions/);
  assert.match(scripts['test:functions'], /^npm --prefix functions run build &&/);
  assert.match(scripts['test:functions'], /--only firestore,functions/);
});
