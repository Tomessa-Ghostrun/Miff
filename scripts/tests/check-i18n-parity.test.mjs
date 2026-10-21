import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  checkKeyNames, compareKeys, extractAndroidKeys, extractIosKeys, run, ANDROID_STRINGS,
} from '../check-i18n-parity.mjs';

const fixture = (name) => fileURLToPath(new URL(`./fixtures/${name}/`, import.meta.url));
const script = fileURLToPath(new URL('../check-i18n-parity.mjs', import.meta.url));

test('extractAndroidKeys : string, plurals, ignore commentaires et translatable="false"', () => {
  const xml = `<resources>
    <!-- <string name="fantome">x</string> -->
    <string name="auth_title">A</string>
    <string name="brand" translatable="false">Miff</string>
    <plurals name="miff_count"><item quantity="one">1</item></plurals>
    <string-array name="common_days"><item>L</item></string-array>
  </resources>`;
  assert.deepEqual(extractAndroidKeys(xml), ['auth_title', 'miff_count', 'common_days']);
});

test('extractIosKeys : clés du catalogue, ignore shouldTranslate=false', () => {
  const json = JSON.stringify({ sourceLanguage: 'fr', strings: { auth_title: {}, brand: { shouldTranslate: false } } });
  assert.deepEqual(extractIosKeys(json), ['auth_title']);
  assert.throws(() => extractIosKeys('{"pas":"un catalogue"}'), /strings/);
});

test('compareKeys : ignore les préfixes propres à chaque plateforme', () => {
  const result = compareKeys(['auth_a', 'android_x'], ['auth_a', 'ios_y']);
  assert.deepEqual(result, { onlyAndroid: [], onlyIos: [] });
  assert.deepEqual(compareKeys(['auth_a', 'auth_b'], ['auth_a', 'auth_c']), { onlyAndroid: ['auth_b'], onlyIos: ['auth_c'] });
});

test('checkKeyNames : snake_case obligatoire, préfixe inconnu en avertissement', () => {
  const { errors, warnings } = checkKeyNames(['auth_ok', 'BadName', 'sans_prefixe_connu_ok', 'x'], 'Android');
  assert.equal(errors.length, 2);
  assert.match(errors.join('\n'), /BadName/);
  assert.equal(warnings.length, 1);
  assert.match(warnings[0], /sans_/);
  assert.match(checkKeyNames(['ios_a'], 'Android').errors[0], /réservé à iOS/);
  assert.match(checkKeyNames(['android_a'], 'iOS').errors[0], /réservé à Android/);
  assert.match(checkKeyNames(['auth_a', 'auth_a'], 'iOS').errors[0], /double/);
});

test('run : jeu de clés identique -> succès (clés propres aux plateformes ignorées)', () => {
  const { code, lines } = run({ root: fixture('ok') });
  assert.equal(code, 0, lines.join('\n'));
  assert.match(lines.join('\n'), /Parité des traductions OK : 4 clé\(s\)/);
});

test('run : clé ajoutée seulement côté Android -> échec explicite', () => {
  const { code, lines } = run({ root: fixture('android-extra') });
  assert.equal(code, 1);
  assert.match(lines.join('\n'), /Android seulement : place_name/);
});

test('run : clé ajoutée seulement côté iOS -> échec explicite', () => {
  const { code, lines } = run({ root: fixture('ios-extra') });
  assert.equal(code, 1);
  assert.match(lines.join('\n'), /iOS seulement : miff_create/);
});

test('run : noms de clés invalides -> échec', () => {
  const { code, lines } = run({ root: fixture('bad-names') });
  assert.equal(code, 1);
  assert.match(lines.join('\n'), /BadName/);
});

test('run : aucun fichier de traduction -> succès avec message explicite', () => {
  const root = mkdtempSync(join(tmpdir(), 'miff-i18n-'));
  try {
    const { code, lines } = run({ root });
    assert.equal(code, 0);
    assert.match(lines[0], /ignorée : aucun fichier de traduction/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('run : une seule plateforme présente -> succès, parité non vérifiable (mais noms contrôlés)', () => {
  const root = mkdtempSync(join(tmpdir(), 'miff-i18n-'));
  try {
    const file = join(root, ANDROID_STRINGS);
    mkdirSync(join(file, '..'), { recursive: true });
    writeFileSync(file, '<resources><string name="auth_title">A</string></resources>');
    const ok = run({ root });
    assert.equal(ok.code, 0);
    assert.match(ok.lines.join('\n'), /Parité non vérifiable pour l'instant/);
    writeFileSync(file, '<resources><string name="MauvaisNom">A</string></resources>');
    assert.equal(run({ root }).code, 1);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('run : catalogue iOS illisible -> code 2', () => {
  const root = mkdtempSync(join(tmpdir(), 'miff-i18n-'));
  try {
    mkdirSync(join(root, 'ios', 'Miff'), { recursive: true });
    writeFileSync(join(root, 'ios', 'Miff', 'Localizable.xcstrings'), '{ pas du json');
    const { code, lines } = run({ root });
    assert.equal(code, 2);
    assert.match(lines.join('\n'), /Localizable\.xcstrings/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('CLI : code de sortie 1 sur écart, 0 sur succès, 2 sur argument inconnu', () => {
  const ko = spawnSync(process.execPath, [script, '--root', fixture('android-extra')], { encoding: 'utf8' });
  assert.equal(ko.status, 1);
  assert.match(ko.stderr, /place_name/);
  const ok = spawnSync(process.execPath, [script, '--root', fixture('ok')], { encoding: 'utf8' });
  assert.equal(ok.status, 0);
  const bad = spawnSync(process.execPath, [script, '--nope'], { encoding: 'utf8' });
  assert.equal(bad.status, 2);
});
