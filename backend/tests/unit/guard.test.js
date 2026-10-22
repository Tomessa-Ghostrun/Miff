// Critère T4 : le garde-fou refuse tout ce qui pourrait atteindre un vrai projet.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assertSafeEnvironment } from '../helpers/guard.js';

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
