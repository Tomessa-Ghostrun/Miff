// Critère T4 : le garde-fou refuse tout ce qui pourrait atteindre un vrai projet.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { assertSafeEnvironment, emulatorProjectFromEnv, testProjectId, DEFAULT_TEST_PROJECT_ID } from '../helpers/guard.js';

const okEnv = {
  GCLOUD_PROJECT: 'demo-miff-rules',
  FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080',
  FIREBASE_CONFIG: '{"projectId":"demo-miff-rules"}',
  PATH: '/usr/bin',
};

const refuses = (projectId, env, pattern) =>
  assert.throws(() => assertSafeEnvironment({ projectId, env }), pattern);

test('GUARD - accepte un projet demo-* avec un émulateur local', () => {
  assert.doesNotThrow(() => assertSafeEnvironment({ projectId: 'demo-miff-rules', env: okEnv }));
  assert.doesNotThrow(() =>
    assertSafeEnvironment({ projectId: 'demo-miff-rules', env: { ...okEnv, FIRESTORE_EMULATOR_HOST: '[::1]:8080' } }),
  );
  assert.doesNotThrow(() =>
    assertSafeEnvironment({ projectId: 'demo-miff-rules', env: { ...okEnv, FIRESTORE_EMULATOR_HOST: 'localhost:9000' } }),
  );
});

test('GUARD - refuse les vrais projets passés en paramètre', () => {
  refuses('miff-3d283', okEnv, /demo-/);
  refuses('ghostrun-app', okEnv, /demo-/);
  refuses('demo', okEnv, /demo-/);
  refuses(undefined, okEnv, /demo-/);
});

test('GUARD - refuse une variable d\'environnement qui désigne un vrai projet', () => {
  refuses('demo-miff-rules', { ...okEnv, GCLOUD_PROJECT: 'miff-3d283' }, /GCLOUD_PROJECT/);
  refuses('demo-miff-rules', { ...okEnv, GOOGLE_CLOUD_PROJECT: 'ghostrun-app' }, /GOOGLE_CLOUD_PROJECT/);
  refuses('demo-miff-rules', { ...okEnv, FIREBASE_PROJECT: 'autre-projet' }, /FIREBASE_PROJECT/);
  refuses('demo-miff-rules', { ...okEnv, FIREBASE_CONFIG: '{"projectId":"miff-3d283"}' }, /FIREBASE_CONFIG/);
  refuses('demo-miff-rules', { ...okEnv, FIREBASE_CONFIG: 'pas du json' }, /FIREBASE_CONFIG/);
});

test('GUARD - refuse la mention d\'un projet interdit dans une variable liée à Firebase', () => {
  refuses('demo-miff-rules', { ...okEnv, FIREBASE_TOKEN_FOR: 'ghostrun-app' }, /ghostrun-app/);
  refuses('demo-miff-rules', { ...okEnv, MY_FIREBASE_TARGET: 'cible=MIFF-3D283' }, /miff-3d283/);
});

test('GUARD - ne bloque pas un dossier de travail qui contient un nom de projet (PATH, PWD...)', () => {
  assert.doesNotThrow(() =>
    assertSafeEnvironment({ projectId: 'demo-miff-rules', env: { ...okEnv, PWD: 'C:/dev/ghostrun-app/outils' } }),
  );
});

test('GUARD - refuse des identifiants Google réels', () => {
  refuses('demo-miff-rules', { ...okEnv, GOOGLE_APPLICATION_CREDENTIALS: 'C:/cle.json' }, /GOOGLE_APPLICATION_CREDENTIALS/);
});

test('GUARD - exige un émulateur local', () => {
  const { FIRESTORE_EMULATOR_HOST: _omit, ...withoutHost } = okEnv;
  refuses('demo-miff-rules', withoutHost, /FIRESTORE_EMULATOR_HOST/);
  refuses('demo-miff-rules', { ...okEnv, FIRESTORE_EMULATOR_HOST: 'firestore.googleapis.com:443' }, /machine locale/);
  refuses('demo-miff-rules', { ...okEnv, FIRESTORE_EMULATOR_HOST: '10.0.0.5:8080' }, /machine locale/);
  refuses('demo-miff-rules', { ...okEnv, FIREBASE_EMULATOR_HUB: 'example.com:4400' }, /FIREBASE_EMULATOR_HUB/);
});

test('GUARD - l\'émulateur peut être ignoré uniquement sur demande explicite (tests unitaires)', () => {
  const { FIRESTORE_EMULATOR_HOST: _omit, ...withoutHost } = okEnv;
  assert.doesNotThrow(() => assertSafeEnvironment({ projectId: 'demo-x', env: withoutHost, requireEmulator: false }));
  // ... mais un vrai projet reste refusé même sans émulateur exigé.
  assert.throws(() => assertSafeEnvironment({ projectId: 'miff-3d283', env: withoutHost, requireEmulator: false }), /demo-/);
});

// --- DUR-3 (R16) : le garde-fou juge le projet RÉELLEMENT utilisé par l'émulateur ----------------

test('GUARD - exige que l\'émulateur annonce son projet (GCLOUD_PROJECT posé par emulators:exec)', () => {
  const { GCLOUD_PROJECT: _omit, ...withoutGcloud } = okEnv;
  refuses('demo-miff-rules', withoutGcloud, /GCLOUD_PROJECT absent/);
});

test('GUARD - refuse un projet de tests différent de celui de l\'émulateur', () => {
  refuses('demo-miff-rules', { ...okEnv, GCLOUD_PROJECT: 'demo-autre', FIREBASE_CONFIG: '{"projectId":"demo-autre"}' }, /n'est pas celui de l'émulateur/);
  refuses('demo-miff-rules', { ...okEnv, FIREBASE_CONFIG: '{"projectId":"demo-autre"}' }, /deux projets différents/);
});

test('GUARD - testProjectId lit le projet de l\'émulateur, jamais une valeur inventée quand l\'environnement en annonce une', () => {
  assert.equal(testProjectId(okEnv), 'demo-miff-rules');
  assert.equal(testProjectId({ GCLOUD_PROJECT: 'miff-3d283' }), 'miff-3d283', 'un vrai projet annoncé doit être transmis tel quel pour être refusé');
  assert.equal(testProjectId({ FIREBASE_CONFIG: '{"projectId":"ghostrun-app"}' }), 'ghostrun-app');
  assert.equal(testProjectId({}), DEFAULT_TEST_PROJECT_ID);
  assert.deepEqual(emulatorProjectFromEnv({ FIREBASE_CONFIG: 'pas du json', GCLOUD_PROJECT: 'demo-x' }), { gcloud: 'demo-x', config: undefined });
});

// Critère R16 de bout en bout : les VRAIS points d'entrée (lanceur des tests de règles et fichier de test) s'arrêtent
// avant toute requête quand l'environnement désigne le vrai projet. Rien n'écoute sur le port : si le garde-fou échouait,
// l'échec serait une erreur de connexion, pas le message « GARDE-FOU » attendu.
const backendDir = fileURLToPath(new URL('../../', import.meta.url));
const baseEnv = Object.fromEntries(Object.entries(process.env).filter(([k]) => !/FIREBASE|FIRESTORE|GCLOUD|GOOGLE|GCP|CLOUDSDK|NODE_TEST_CONTEXT/i.test(k)));
const realProjectEnv = (project) => ({
  ...baseEnv,
  GCLOUD_PROJECT: project,
  FIREBASE_CONFIG: JSON.stringify({ projectId: project }),
  FIRESTORE_EMULATOR_HOST: '127.0.0.1:1', // port inutilisé : aucun service ne répond
});

for (const project of ['miff-3d283', 'ghostrun-app']) {
  test(`GUARD - le lanceur des tests de règles refuse GCLOUD_PROJECT=${project} (code 2, aucune requête)`, () => {
    const r = spawnSync(process.execPath, ['tests/tools/run-rules.mjs'], { cwd: backendDir, env: realProjectEnv(project), encoding: 'utf8', timeout: 30000 });
    assert.equal(r.status, 2, r.stdout + r.stderr);
    assert.match(r.stderr, /GARDE-FOU/);
    assert.match(r.stderr, new RegExp(project));
  });
}

test('GUARD - un fichier de test lancé directement avec GCLOUD_PROJECT=miff-3d283 échoue sur le garde-fou, pas sur le réseau', () => {
  const r = spawnSync(process.execPath, ['--test', 'tests/rules/01-read.test.js'], {
    cwd: backendDir, env: realProjectEnv('miff-3d283'), encoding: 'utf8', timeout: 60000,
  });
  assert.notEqual(r.status, 0);
  assert.match(r.stdout + r.stderr, /GARDE-FOU/);
  assert.doesNotMatch(r.stdout + r.stderr, /ECONNREFUSED|UNAVAILABLE/);
});
