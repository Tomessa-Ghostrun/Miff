import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { forbiddenReason, findForbidden } from '../check-forbidden-files.mjs';

const script = fileURLToPath(new URL('../check-forbidden-files.mjs', import.meta.url));

test('forbiddenReason : les fichiers listés au critère T1 sont interdits', () => {
  const forbidden = [
    'android/app/google-services.json',
    'ios/Miff/GoogleService-Info.plist',
    'release.jks',
    'android/upload.keystore',
    'AuthKey_ABC123DEFG.p8',
    'cert.p12',
    'cle.pem',
    'Miff_AppStore.mobileprovision',
    '.env',
    '.env.local',
    'config/prod.env',
    'android/key.properties',
    'miff-3d283-firebase-adminsdk-abcde-0123456789.json',
    'serviceAccountKey.json',
    'appcheck-debug-token.txt',
    'build/app-debug.apk',
    'app.ipa',
    'GOOGLE-SERVICES.JSON',
    // Variantes de téléchargement en double
    'GoogleService-Info (1).plist',
    'ios/Miff/GoogleService-Info (2).plist',
    'google-services (1).json',
    'android/app/google-services (3).json',
    'google-services-2.json',
    'google-services.prod.json',
    'GoogleService-Info copy.plist',
    // Autres formats de clés
    'certificat.pfx',
    'CERT.PFX',
    'server.key',
    'AuthKey_ABC (1).p8',
  ];
  for (const path of forbidden) assert.ok(forbiddenReason(path), `${path} devrait être interdit`);
});

test('forbiddenReason : fichiers ordinaires et modèles sans valeur acceptés', () => {
  const allowed = [
    'README.md',
    'backend/firestore.rules',
    'backend/package.json',
    '.env.example',
    'config/prod.env.example',
    'android/app/src/main/res/values/strings.xml',
    'ios/Miff/Localizable.xcstrings',
    'docs/dev/secrets.md',
    'docs/google-services-notes.md',
    '.github/workflows/hygiene.yml',
    'design/figma-design-system-state.json',
  ];
  for (const path of allowed) assert.equal(forbiddenReason(path), null, `${path} ne devrait pas être interdit`);
});

function makeRepo() {
  const root = mkdtempSync(join(tmpdir(), 'miff-forbidden-'));
  const git = (...args) =>
    execFileSync('git', ['-C', root, '-c', 'user.name=test', '-c', 'user.email=test@example.com', '-c', 'commit.gpgsign=false', ...args], {
      encoding: 'utf8',
    });
  git('init', '-q');
  const write = (path, content = 'x') => {
    mkdirSync(dirname(join(root, path)), { recursive: true });
    writeFileSync(join(root, path), content);
  };
  return { root, git, write, cleanup: () => rmSync(root, { recursive: true, force: true }) };
}

test('dépôt propre : aucun fichier interdit, code 0', () => {
  const repo = makeRepo();
  try {
    repo.write('README.md');
    repo.write('.env.example', 'CLE=');
    repo.git('add', '.');
    repo.git('commit', '-q', '-m', 'propre');
    assert.deepEqual(findForbidden(repo.root), []);
    assert.equal(spawnSync(process.execPath, [script, '--root', repo.root]).status, 0);
  } finally {
    repo.cleanup();
  }
});

test('faux fichier ajouté (branche jetable) : code 1 et fichier nommé', () => {
  const repo = makeRepo();
  try {
    repo.write('README.md');
    repo.git('add', '.');
    repo.git('commit', '-q', '-m', 'base');
    repo.git('checkout', '-q', '-b', 'jetable');
    repo.write('android/app/google-services.json', '{"fake": true}');
    repo.git('add', '-f', '.');
    repo.git('commit', '-q', '-m', 'ajoute un faux fichier');
    const result = spawnSync(process.execPath, [script, '--root', repo.root], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /android\/app\/google-services\.json/);
  } finally {
    repo.cleanup();
  }
});

test('fichier ajouté puis supprimé : toujours signalé via l\'historique', () => {
  const repo = makeRepo();
  try {
    repo.write('README.md');
    repo.write('AuthKey_TEST.p8');
    repo.git('add', '-f', '.');
    repo.git('commit', '-q', '-m', 'ajoute');
    repo.git('rm', '-q', 'AuthKey_TEST.p8');
    repo.git('commit', '-q', '-m', 'retire');
    const found = findForbidden(repo.root);
    assert.equal(found.length, 1);
    assert.equal(found[0].where, 'historique');
    assert.equal(spawnSync(process.execPath, [script, '--root', repo.root]).status, 1);
  } finally {
    repo.cleanup();
  }
});

test('argument inconnu : code 2', () => {
  assert.equal(spawnSync(process.execPath, [script, '--nope']).status, 2);
});

test('variante de téléchargement en double commitée : code 1', () => {
  const repo = makeRepo();
  try {
    repo.write('README.md');
    repo.write('ios/Miff/GoogleService-Info (1).plist', '<plist/>');
    repo.git('add', '-f', '.');
    repo.git('commit', '-q', '-m', 'ajoute une copie');
    const result = spawnSync(process.execPath, [script, '--root', repo.root], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /GoogleService-Info \(1\)\.plist/);
  } finally {
    repo.cleanup();
  }
});

test('.gitignore du dépôt : variantes en double, .pfx et .key ignorés', () => {
  const root = fileURLToPath(new URL('../../', import.meta.url));
  const ignored = [
    'GoogleService-Info (1).plist',
    'ios/Miff/GoogleService-Info (2).plist',
    'google-services (1).json',
    'android/app/google-services (2).json',
    'certificat.pfx',
    'server.key',
  ];
  for (const path of ignored) {
    const result = spawnSync('git', ['-C', root, 'check-ignore', '-q', '--', path]);
    assert.equal(result.status, 0, `${path} devrait être ignoré par .gitignore`);
  }
});
