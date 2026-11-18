// Tests des contrôles par langue de scripts/check-i18n-parity.mjs (registre des langues, lot 1 du ticket « Miff en 6 langues »).
// Chaque contrôle a un cas qui échoue et un cas qui réussit. Les dépôts de test sont fabriqués dans un dossier temporaire.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkSourcesConsistency, loadRegistry, markdownSummary, run, validateRegistry } from '../check-i18n-parity.mjs';
import {
  androidEscapeProblems, catalogLocalization, decodeAndroidValue, meaningfulNbspCount, parseAndroidStrings, parseCatalog, placeholderSignatures,
} from '../lib/i18n-resources.mjs';

const script = fileURLToPath(new URL('../check-i18n-parity.mjs', import.meta.url));
const repoRoot = fileURLToPath(new URL('../../', import.meta.url));
const NBSP = String.fromCodePoint(0xa0);
const NNBSP = String.fromCodePoint(0x202f);
const ZWSP = String.fromCodePoint(0x200b);
const RLM = String.fromCodePoint(0x200f);

// ----- Fabrique de dépôts de test -----

function language(code, status, extra = {}) {
  return {
    code,
    nativeName: code.toUpperCase(),
    plurals: { required: ['one', 'other'], allowed: ['one', 'many', 'other'], recommended: [] },
    style: { spaceBeforeHighPunctuation: 'none', forbidGenderMarks: false, formalForms: [], forbiddenTerms: [] },
    platforms: {
      android: { status, resDir: code === 'en' ? 'values' : `values-${code}` },
      ios: { status, code },
    },
    ...extra,
  };
}

function baseRegistry(overrides = {}) {
  return {
    version: 1,
    sourceLanguage: 'fr',
    fallbackLanguage: 'en',
    brandTerms: ['Miff+', 'Miff'],
    nbspRequiredKeyPatterns: ['^countdown_duration_'],
    shortLabelKeyPatterns: ['_button$'],
    lengthRatio: { maxVsSource: 1.5, minSourceLength: 10, shortLabelExtra: 0.4 },
    lengthBudgets: {},
    exceptions: [],
    consistency: { sources: [] },
    languages: [language('fr', 'required'), language('en', 'required'), language('es', 'planned', {
      style: { spaceBeforeHighPunctuation: 'none', forbidGenderMarks: true, formalForms: [{ pattern: '\\busted(es)?\\b', flags: 'iu' }], forbiddenTerms: ['pánico'] },
    })],
    ...overrides,
  };
}

const xml = (body) => `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n${body}\n</resources>\n`;
const str = (name, value, attributes = '') => `    <string name="${name}"${attributes}>${value}</string>`;
const plural = (name, forms) => `    <plurals name="${name}">${Object.entries(forms).map(([q, v]) => `<item quantity="${q}">${v}</item>`).join('')}</plurals>`;

function catalogEntry(values, extra = {}) {
  const localizations = {};
  for (const [lang, v] of Object.entries(values)) {
    localizations[lang] = typeof v === 'string' ? { stringUnit: { state: 'translated', value: v } } : v;
  }
  return { extractionState: 'manual', localizations, ...extra };
}
const iosPlural = (forms) => ({ variations: { plural: Object.fromEntries(Object.entries(forms).map(([c, v]) => [c, { stringUnit: { state: 'translated', value: v } }])) } });

/**
 * @param {{registry?: object|null, android?: Record<string, string>, ios?: Record<string, object>, infoPlist?: Record<string, object>, files?: Record<string, string>}} spec
 * `android` : dossier de ressources -> contenu de strings.xml ; `ios` : clé -> entrée de catalogue.
 */
function makeRepo({ registry = baseRegistry(), android = {}, ios = {}, infoPlist, files = {} }) {
  const root = mkdtempSync(join(tmpdir(), 'miff-i18n-lang-'));
  const put = (relativePath, content) => {
    const file = join(root, relativePath);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, content);
  };
  if (registry) put('docs/contracts/i18n-languages.json', JSON.stringify(registry));
  for (const [dir, content] of Object.entries(android)) put(`android/app/src/main/res/${dir}/strings.xml`, content);
  if (Object.keys(ios).length > 0) put('ios/Miff/Resources/Localizable.xcstrings', JSON.stringify({ sourceLanguage: 'fr', strings: ios, version: '1.0' }));
  if (infoPlist) put('ios/Miff/Resources/InfoPlist.xcstrings', JSON.stringify({ sourceLanguage: 'fr', strings: infoPlist, version: '1.0' }));
  for (const [path, content] of Object.entries(files)) put(path, content);
  return { root, cleanup: () => rmSync(root, { recursive: true, force: true }) };
}

function withRepo(spec, fn) {
  const repo = makeRepo(spec);
  try {
    return fn(run({ root: repo.root, verbose: true }), repo);
  } finally {
    repo.cleanup();
  }
}

/** Dépôt à une seule clé `auth_title`, avec la valeur donnée en français, en anglais et en espagnol. */
function oneKey({ fr, en = fr, es, key = 'auth_title', registry, status = 'planned', extraAndroidEs = '' }) {
  const reg = registry ?? baseRegistry();
  if (status === 'required') reg.languages.find((l) => l.code === 'es').platforms = { android: { status: 'required', resDir: 'values-es' }, ios: { status: 'required', code: 'es' } };
  return {
    registry: reg,
    android: {
      'values-fr': xml(str(key, fr)),
      values: xml(str(key, en)),
      ...(es === undefined ? {} : { 'values-es': xml(str(key, es) + extraAndroidEs) }),
    },
    // Côté iOS, les entités XML n'existent pas : les espaces insécables sont écrites telles quelles.
    ios: { [key]: catalogEntry(Object.fromEntries(Object.entries({ fr, en, es }).filter(([, v]) => v !== undefined).map(([l, v]) => [l, v.replaceAll('&#160;', NBSP).replaceAll('&#8239;', NNBSP)]))) },
  };
}

const output = (result) => result.lines.join('\n');
const finds = (result, check, lang = 'es') => result.report.findings.filter((f) => f.check === check && f.lang === lang);

// ----- Lecture des ressources -----

test('placeholderSignatures : %1$s = %1$@, %2$02d = %2$02lld, %% ignoré, positions implicites numérotées', () => {
  assert.deepEqual(placeholderSignatures('Bonjour %1$s, %2$02d'), placeholderSignatures('Bonjour %1$@, %2$02lld'));
  assert.deepEqual(placeholderSignatures('%d %s'), ['1d', '2s']);
  assert.deepEqual(placeholderSignatures('%d %@'), ['1d', '2s']);
  assert.deepEqual(placeholderSignatures('85 %% de %1$lld'), ['1d']);
  assert.deepEqual(placeholderSignatures('%#@heures@'), ['@heures']);
  assert.notDeepEqual(placeholderSignatures('%1$d'), placeholderSignatures('%1$s'));
  assert.notDeepEqual(placeholderSignatures('%1$d'), placeholderSignatures('%2$d'));
});

test('decodeAndroidValue : apostrophe, entités, espace insécable, guillemets englobants', () => {
  assert.equal(decodeAndroidValue("Tu n\\'as rien &amp; tout"), "Tu n'as rien & tout");
  assert.equal(decodeAndroidValue('%1$d&#160;h'), `%1$d${NBSP}h`);
  assert.equal(decodeAndroidValue('"  garde   les espaces  "'), '  garde   les espaces  ');
  assert.equal(decodeAndroidValue('a\\nb'), 'a\nb');
});

test('androidEscapeProblems : apostrophe, &, guillemet, @ initial, % dans un texte à valeurs insérées', () => {
  assert.deepEqual(androidEscapeProblems("Tu n\\'as rien"), []);
  assert.deepEqual(androidEscapeProblems('"Tu n\'as rien"'), []);
  assert.match(androidEscapeProblems("Tu n'as rien")[0], /apostrophe/);
  assert.match(androidEscapeProblems('Pain & lait')[0], /&/);
  assert.deepEqual(androidEscapeProblems('Pain &amp; lait &#160; &#x41;'), []);
  assert.match(androidEscapeProblems('Il dit "oui"')[0], /guillemet/);
  assert.match(androidEscapeProblems('@maison')[0], /@/);
  assert.deepEqual(androidEscapeProblems('\\@maison'), []);
  assert.match(androidEscapeProblems('%1$d % de batterie')[0], /%%/);
  assert.deepEqual(androidEscapeProblems('%1$d %% de batterie'), []);
  assert.deepEqual(androidEscapeProblems("presque 30 % d\\'économie"), [], 'un % seul est toléré dans un texte sans valeur insérée');
});

test('meaningfulNbspCount : ignore l\'espace avant ? ! : ; » et après «', () => {
  assert.equal(meaningfulNbspCount(`Vraiment${NBSP}? Oui${NNBSP}!`), 0);
  assert.equal(meaningfulNbspCount(`«${NBSP}ok${NBSP}»`), 0);
  assert.equal(meaningfulNbspCount(`1${NBSP}h 12${NBSP}min`), 2);
});

test('parseAndroidStrings et parseCatalog : chaînes, pluriels, translatable et shouldTranslate', () => {
  const entries = parseAndroidStrings(xml(`${str('a_b', 'x')}\n${str('brand_x', 'Miff', ' translatable="false"')}\n${plural('c_d', { one: '1', other: '%d' })}\n<!-- ${str('ghost_x', 'g')} -->`));
  assert.deepEqual([...entries.keys()], ['a_b', 'brand_x', 'c_d']);
  assert.equal(entries.get('brand_x').translatable, false);
  assert.deepEqual(entries.get('c_d').texts.map((t) => t.id), ['one', 'other']);
  const catalog = parseCatalog(JSON.stringify({ sourceLanguage: 'fr', strings: { a_b: catalogEntry({ fr: 'x' }), c_d: catalogEntry({ fr: iosPlural({ one: '1', other: '%lld' }) }), e_f: { shouldTranslate: false } } }));
  assert.equal(catalogLocalization(catalog.entries.get('a_b'), 'fr').kind, 'string');
  assert.deepEqual(catalogLocalization(catalog.entries.get('c_d'), 'fr').texts.map((t) => t.id), ['one', 'other']);
  assert.equal(catalogLocalization(catalog.entries.get('a_b'), 'es'), null);
  assert.equal(catalog.entries.get('e_f').shouldTranslate, false);
  const substituted = { localizations: { fr: { stringUnit: { state: 'translated', value: '%#@n@' }, substitutions: { n: { argNum: 1, formatSpecifier: 'lld', variations: { plural: { one: { stringUnit: { value: '%arg membre' } }, other: { stringUnit: { value: '%arg membres' } } } } } } } } };
  const loc = catalogLocalization(substituted, 'fr');
  assert.equal(loc.kind, 'substituted');
  assert.deepEqual(loc.texts.map((t) => t.id), ['', 'n:one', 'n:other']);
});

// ----- Registre -----

test('registre : valide ; erreurs de structure signalées (code 2 dans run)', () => {
  assert.deepEqual(validateRegistry(baseRegistry()), []);
  assert.match(validateRegistry({ ...baseRegistry(), languages: [language('fr', 'required'), language('fr', 'required'), language('en', 'required')] }).join('\n'), /en double/);
  const badStatus = baseRegistry();
  badStatus.languages[2].platforms.ios.status = 'bientot';
  assert.match(validateRegistry(badStatus).join('\n'), /status/);
  const noSource = baseRegistry();
  noSource.languages[0].platforms.android.status = 'planned';
  assert.match(validateRegistry(noSource).join('\n'), /langue source doit être required/);
  const badRegex = baseRegistry({ shortLabelKeyPatterns: ['('] });
  assert.match(validateRegistry(badRegex).join('\n'), /expression invalide/);
  const badDir = baseRegistry();
  badDir.languages[2].platforms.android.resDir = 'values';
  assert.match(validateRegistry(badDir).join('\n'), /utilisé deux fois/);
  withRepo({ registry: badRegex, android: { 'values-fr': xml(str('auth_a', 'x')) } }, (result) => {
    assert.equal(result.code, 2);
    assert.match(output(result), /registre/i);
  });
});

test('registre : le registre du dépôt est valide ; fr et en sont toujours required ; les statuts de es it pt de sont planned ou required', () => {
  const registry = loadRegistry(repoRoot);
  assert.deepEqual(registry.languages.map((l) => l.code), ['fr', 'en', 'es', 'it', 'pt', 'de']);
  for (const l of registry.languages) {
    for (const platform of ['android', 'ios']) {
      const status = l.platforms[platform].status;
      if (['fr', 'en'].includes(l.code)) assert.equal(status, 'required', `${l.code} ${platform}`);
      else assert.ok(['planned', 'required'].includes(status), `${l.code} ${platform}`);
    }
  }
  const pluralsByLang = Object.fromEntries(registry.languages.map((l) => [l.code, l.plurals.allowed.join(',')]));
  assert.deepEqual(pluralsByLang, { fr: 'one,many,other', en: 'one,other', es: 'one,many,other', it: 'one,many,other', pt: 'one,many,other', de: 'one,other' });
});

test('le vrai dépôt : contrôles par langue verts, aucune erreur, les six langues du registre sont contrôlées', () => {
  const result = run({ root: repoRoot });
  assert.equal(result.code, 0, output(result));
  assert.equal(result.report.summary.errors, 0);
  const codes = result.report.languages.filter((l) => l.platform === 'android').map((l) => l.code);
  assert.deepEqual(codes, ['fr', 'en', 'es', 'it', 'pt', 'de']);
});

// ----- Statut : required bloque, planned avertit -----

test('clé manquante : erreur bloquante si required, avertissement si planned', () => {
  const spec = oneKey({ fr: 'Salut', es: undefined });
  withRepo(spec, (result) => {
    assert.equal(result.code, 0, output(result));
    const missing = finds(result, 'missing-key');
    assert.equal(missing.length, 2, 'Android et iOS');
    assert.ok(missing.every((f) => f.effectiveLevel === 'warning' && f.level === 'error'));
    assert.match(output(result), /\[es · Android · planned\] 0\/1 clés/);
  });
  const required = oneKey({ fr: 'Salut', es: undefined, status: 'required' });
  withRepo(required, (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /ERREUR \[es · Android\] auth_title : clé absente/);
    assert.match(output(result), /ERREUR \[es · iOS\] auth_title : clé absente/);
  });
});

test('langue complète : sans écart, code 0 et aucune alerte pour es', () => {
  withRepo(oneKey({ fr: 'Salut', en: 'Hi', es: 'Hola', status: 'required' }), (result) => {
    assert.equal(result.code, 0, output(result));
    assert.equal(result.report.findings.filter((f) => f.lang === 'es').length, 0);
    assert.match(output(result), /\[es · Android · required\] 1\/1 clés, 0 erreur/);
  });
});

test('clé en trop côté Android -> erreur ; côté iOS une clé absente du français est signalée par la parité française', () => {
  const spec = oneKey({ fr: 'Salut', en: 'Hi', es: 'Hola', status: 'required', extraAndroidEs: str('auth_extra', 'de más') });
  withRepo(spec, (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /ERREUR \[es · Android\] auth_extra : clé absente du fr/);
  });
});

// ----- Contrôles de contenu -----

const failing = (fr, es, check, { en = fr, key = 'auth_title', esAndroid, extra } = {}) => {
  const spec = oneKey({ fr, en, es, key, status: 'required', ...extra });
  if (esAndroid !== undefined) spec.android['values-es'] = xml(str(key, esAndroid));
  return withRepo(spec, (result) => ({ code: result.code, count: finds(result, check).length, out: output(result), result }));
};

test('texte vide ou marqueur TODO : erreur', () => {
  assert.equal(failing('Salut', '', 'empty').code, 1);
  assert.ok(failing('Salut', '', 'empty').count >= 1);
  assert.equal(failing('Salut', 'TODO Hola', 'todo').code, 1);
  assert.equal(failing('Salut', 'Hola ???', 'todo').count >= 1, true);
  // « todo » (tout, en espagnol) n'est pas un marqueur
  assert.equal(failing('Tout va bien', 'Todo va bien', 'todo').code, 0);
});

test('caractères de contrôle et de direction : erreur ; saut de ligne toléré', () => {
  assert.ok(failing('Salut', `Ho${ZWSP}la`, 'control-char').count >= 1);
  assert.ok(failing('Salut', `${RLM}Hola`, 'control-char').count >= 1);
  assert.equal(failing('Salut', 'Ho\nla', 'control-char').count, 0);
});

test('valeurs insérées : même ensemble que le français, ordre libre, %1$@ = %1$s, pluriel avec forme sans nombre', () => {
  assert.equal(failing('Bonjour %1$s', 'Hola %1$s', 'placeholders').code, 0);
  assert.equal(failing('%1$s a %2$d lieux', '%2$d lugares para %1$s', 'placeholders').code, 0, 'ordre libre');
  const missing = failing('Bonjour %1$s', 'Hola', 'placeholders');
  assert.equal(missing.code, 1);
  assert.match(missing.out, /valeurs insérées attendues « 1s »/);
  assert.equal(failing('%1$d min', '%1$s min', 'placeholders').code, 1, 'type différent');
  assert.equal(failing('%1$d min', '%2$d min', 'placeholders').code, 1, 'position différente');
  assert.equal(failing('%1$s', '%1$s %2$s', 'placeholders').code, 1, 'valeur en trop');
  assert.equal(failing('%2$02d min', '%2$d min', 'placeholders').code, 1, 'remplissage perdu (02)');
});

test('valeurs insérées : Android et iOS comparés chacun à leur français (%lld, %@)', () => {
  const spec = {
    registry: (() => {
      const r = baseRegistry();
      r.languages[2].platforms = { android: { status: 'required', resDir: 'values-es' }, ios: { status: 'required', code: 'es' } };
      return r;
    })(),
    android: {
      'values-fr': xml(str('auth_a', '%1$s : %2$02d')),
      values: xml(str('auth_a', '%1$s: %2$02d')),
      'values-es': xml(str('auth_a', '%1$s: %2$02d')),
    },
    ios: { auth_a: catalogEntry({ fr: '%1$@ : %2$02lld', en: '%1$@: %2$02lld', es: '%1$@: %2$02d' }) },
  };
  withRepo(spec, (result) => assert.equal(result.code, 0, output(result)));
  spec.ios.auth_a = catalogEntry({ fr: '%1$@ : %2$02lld', en: '%1$@: %2$02lld', es: '%1$@: %2$lld' });
  withRepo(spec, (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /\[es · iOS\] auth_a : valeurs insérées/);
  });
});

test('pluriels Android : one et other obligatoires, forme inconnue refusée, many toléré, forme sans nombre acceptée', () => {
  const build = (esForms, registry) => {
    const reg = registry ?? baseRegistry();
    reg.languages[2].platforms = { android: { status: 'required', resDir: 'values-es' }, ios: { status: 'planned', code: 'es' } };
    return {
      registry: reg,
      android: {
        'values-fr': xml(plural('profile_members', { one: '%d membre', many: '%d de membres', other: '%d membres' })),
        values: xml(plural('profile_members', { one: '%d member', other: '%d members' })),
        'values-es': xml(plural('profile_members', esForms)),
      },
      ios: {},
    };
  };
  withRepo(build({ one: '%d miembro', many: '%d de miembros', other: '%d miembros' }), (result) => assert.equal(result.code, 0, output(result)));
  withRepo(build({ one: 'Un miembro', other: '%d miembros' }), (result) => assert.equal(result.code, 0, output(result)));
  withRepo(build({ one: '%d miembro' }), (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /forme « other » obligatoire absente/);
  });
  withRepo(build({ one: '%d miembro', few: '%d miembros', other: '%d miembros' }), (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /forme « few » inconnue/);
  });
  withRepo(build({ zero: 'ninguno', one: '%d miembro', other: '%d miembros' }), (result) => assert.equal(result.code, 1));
  withRepo(build({ one: '%d miembro', other: 'miembros' }), (result) => {
    assert.equal(result.code, 1, 'le nombre disparaît de toutes les formes');
    assert.match(output(result), /valeurs insérées/);
  });
  // allemand : many n'existe pas
  const de = baseRegistry();
  de.languages.push(language('de', 'required'));
  de.languages[3].plurals = { required: ['one', 'other'], allowed: ['one', 'other'], recommended: [] };
  withRepo({
    registry: de,
    android: {
      'values-fr': xml(plural('profile_members', { one: '%d membre', other: '%d membres' })),
      values: xml(plural('profile_members', { one: '%d member', other: '%d members' })),
      'values-de': xml(plural('profile_members', { one: '%d Mitglied', many: '%d Mitglieder', other: '%d Mitglieder' })),
    },
    ios: {},
  }, (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /\[de · Android\].*forme « many » inconnue pour de/);
  });
});

test('pluriels iOS : variations de pluriel et substitutions comparées au français', () => {
  const reg = baseRegistry();
  reg.languages[2].platforms = { android: { status: 'planned', resDir: 'values-es' }, ios: { status: 'required', code: 'es' } };
  const sub = (one, other) => ({ stringUnit: { state: 'translated', value: '%#@n@' }, substitutions: { n: { argNum: 1, formatSpecifier: 'lld', variations: { plural: { one: { stringUnit: { state: 'translated', value: one } }, other: { stringUnit: { state: 'translated', value: other } } } } } } });
  const spec = (esPlural, esSub) => ({
    registry: reg,
    android: {},
    ios: {
      profile_members: catalogEntry({ fr: iosPlural({ one: '%lld membre', many: '%lld membres', other: '%lld membres' }), en: iosPlural({ one: '%lld member', other: '%lld members' }), es: esPlural }),
      profile_places: catalogEntry({ fr: sub('%arg lieu', '%arg lieux'), en: sub('%arg place', '%arg places'), es: esSub }),
    },
  });
  withRepo(spec(iosPlural({ one: '%lld miembro', other: '%lld miembros' }), sub('%arg lugar', '%arg lugares')), (result) => assert.equal(result.code, 0, output(result)));
  withRepo(spec(iosPlural({ one: '%lld miembro' }), sub('%arg lugar', '%arg lugares')), (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /profile_members : forme « other » obligatoire absente/);
  });
  withRepo(spec(iosPlural({ one: '%lld miembro', other: '%lld miembros' }), sub('%arg lugar', '%arg lugares ')), (result) => assert.equal(result.code, 0));
  withRepo(spec('Miembros', sub('%arg lugar', '%arg lugares')), (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /profile_members : pluriel attendu/);
  });
  withRepo(spec(iosPlural({ one: '%lld miembro', other: '%lld miembros' }), 'Lugares'), (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /profile_places : texte à substitutions attendu/);
  });
  const noNumber = (n) => ({ stringUnit: { state: 'translated', value: '%#@n@' }, substitutions: { [n]: { argNum: 1, formatSpecifier: 'lld', variations: { plural: { one: { stringUnit: { value: 'uno' } }, other: { stringUnit: { value: 'varios' } } } } } } });
  withRepo(spec(iosPlural({ one: '%lld miembro', other: '%lld miembros' }), noNumber('autre')), (result) => {
    assert.equal(result.code, 1, 'nom de substitution différent');
    assert.match(output(result), /profile_places : valeurs insérées/);
  });
});

test('échappements Android : apostrophe non échappée -> erreur, échappée -> ok ; entités &amp; et &#160;', () => {
  assert.equal(failing('Tu n\\\'as rien', "No tienes nada", 'android-escape', { esAndroid: 'No tienes nada' }).code, 0);
  const bad = failing('Salut', "L'ami", 'android-escape', { esAndroid: "L'ami" });
  assert.equal(bad.code, 1);
  assert.match(bad.out, /\[es · Android\] auth_title : apostrophe non échappée/);
  assert.equal(failing('Salut', 'L\\\'ami', 'android-escape', { esAndroid: "L\\'ami &amp; yo" }).code, 0);
  assert.equal(failing('Salut', 'x', 'android-escape', { esAndroid: 'Pan & leche' }).code, 1);
  assert.equal(failing('Salut', 'x', 'android-escape', { esAndroid: '1&#160;h' }).code, 0);
});

test('espace insécable du français : durée perdue = erreur, autre texte = avertissement, remplacée par U+202F = ok', () => {
  const durations = (es) => oneKey({ fr: '%1$d&#160;h', en: '%1$d&#160;h', es, key: 'countdown_duration_hours', status: 'required' });
  withRepo(durations('%1$d h'), (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /countdown_duration_hours : le fr a 1 espace\(s\) insécable/);
    assert.match(output(result), /durée : à conserver/);
  });
  withRepo(durations('%1$d&#160;h'), (result) => assert.equal(result.code, 0, output(result)));
  withRepo(durations('%1$d&#8239;h'), (result) => assert.equal(result.code, 0, output(result)));
  const other = oneKey({ fr: 'Tu as 3&#160;lieux', en: 'You have 3 places', es: 'Tienes 3 lugares', status: 'required' });
  withRepo(other, (result) => {
    assert.equal(result.code, 0, 'hors durée : avertissement seulement');
    assert.equal(finds(result, 'nbsp-lost').length, 2);
  });
  // L'espace avant « ? » en français n'est pas à conserver
  const question = oneKey({ fr: 'Prêt&#160;?', en: 'Ready?', es: '¿Listo?', status: 'required' });
  withRepo(question, (result) => assert.equal(finds(result, 'nbsp-lost').length, 0));
});

test('espace insécable avant ? ! : ; dans une langue sans cette règle : avertissement', () => {
  const spec = oneKey({ fr: 'Prêt&#160;?', en: 'Ready?', es: `Listo${NBSP}?`, status: 'required' });
  spec.android['values-es'] = xml(str('auth_title', 'Listo&#160;?'));
  withRepo(spec, (result) => {
    assert.equal(result.code, 0);
    assert.equal(finds(result, 'high-punctuation').length, 2);
  });
  withRepo(oneKey({ fr: 'Prêt&#160;?', en: 'Ready?', es: 'Listo?', status: 'required' }), (result) => assert.equal(finds(result, 'high-punctuation').length, 0));
});

test('marques de genre : (e), o/a, @ inclusif refusés dans les langues concernées, pas en français ni sur une adresse e-mail', () => {
  assert.equal(failing('Tu es connecté(e)', 'Estás conectado(a)', 'gender-mark').code, 1);
  assert.equal(failing('Salut', 'Querido/a amigo', 'gender-mark').code, 1);
  assert.equal(failing('Salut', 'Hola a tod@s', 'gender-mark').code, 1);
  assert.equal(failing('Salut', 'Bienvenid@', 'gender-mark').code, 1);
  assert.equal(failing('Salut', 'Escríbenos a hola@miff.app', 'gender-mark').code, 0);
  assert.equal(failing('Tu es connecté(e)', 'Tu sesión está iniciada', 'gender-mark').code, 0);
  const frenchOnly = oneKey({ fr: 'Tu es connecté(e)', en: 'You are signed in', es: 'Tu sesión está iniciada', status: 'required' });
  withRepo(frenchOnly, (result) => assert.equal(finds(result, 'gender-mark', 'fr').length, 0));
});

test('vouvoiement : « usted » refusé, tutoiement accepté ; exception documentée dans le registre', () => {
  const bad = failing('Salut', 'Si usted lo desea', 'formal-address');
  assert.equal(bad.code, 1);
  assert.match(bad.out, /forme de vouvoiement « usted »/);
  assert.equal(failing('Salut', 'Si lo deseas', 'formal-address').code, 0);
  const excepted = baseRegistry({ exceptions: [{ lang: 'es', key: 'auth_title', check: 'formal-address' }] });
  assert.equal(failing('Salut', 'Si usted lo desea', 'formal-address', { extra: { registry: excepted } }).code, 0);
  const keyPattern = baseRegistry({ exceptions: [{ lang: 'es', keyPattern: '^auth_', check: 'formal-address', platform: 'android' }] });
  const result = failing('Salut', 'Si usted lo desea', 'formal-address', { extra: { registry: keyPattern } });
  assert.equal(result.code, 1, 'l\'exception ne vaut que pour Android');
});

test('termes proscrits par langue (liste du registre) : refusés', () => {
  const bad = failing('Salut', 'Tranquilo, sin pánico', 'forbidden-term');
  assert.equal(bad.code, 1);
  assert.match(bad.out, /terme proscrit « pánico »/);
  assert.equal(failing('Salut', 'Tranquilo', 'forbidden-term').code, 0);
});

test('texte identique au français : avertissement (marque et valeurs seules exemptées)', () => {
  const same = oneKey({ fr: 'Bonjour', en: 'Hello', es: 'Bonjour', status: 'required' });
  withRepo(same, (result) => {
    assert.equal(result.code, 0);
    assert.equal(finds(result, 'identical-to-source').length, 2);
  });
  withRepo(oneKey({ fr: 'Miff+', en: 'Miff+', es: 'Miff+', status: 'required' }), (result) => assert.equal(finds(result, 'identical-to-source').length, 0));
  withRepo(oneKey({ fr: '%1$d', en: '%1$d', es: '%1$d', status: 'required' }), (result) => assert.equal(finds(result, 'identical-to-source').length, 0));
});

test('longueur : plus de 1,5 fois le français = avertissement ; étiquette courte au-delà du budget (clé ou registre)', () => {
  const long = oneKey({ fr: 'Ton compte est prêt', en: 'Your account is ready', es: 'Tu cuenta personal ya está lista y activada', status: 'required' });
  withRepo(long, (result) => {
    assert.equal(result.code, 0);
    assert.ok(finds(result, 'length').length >= 1);
  });
  withRepo(oneKey({ fr: 'Ton compte est prêt', en: 'Your account is ready', es: 'Tu cuenta ya está lista', status: 'required' }), (result) => assert.equal(finds(result, 'length').length, 0));
  const button = oneKey({ fr: 'Continuer', en: 'Continue', es: 'Continuar ahora mismo', key: 'auth_signup_button', status: 'required' });
  withRepo(button, (result) => assert.equal(finds(result, 'short-label-budget').length, 2));
  withRepo(oneKey({ fr: 'Continuer', en: 'Continue', es: 'Continuar', key: 'auth_signup_button', status: 'required' }), (result) => assert.equal(finds(result, 'short-label-budget').length, 0));
  const budget = baseRegistry({ lengthBudgets: { auth_signup_button: 40 } });
  withRepo(oneKey({ fr: 'Continuer', en: 'Continue', es: 'Continuar ahora mismo', key: 'auth_signup_button', status: 'required', registry: budget }), (result) => assert.equal(finds(result, 'short-label-budget').length, 0));
});

test('rapport : longueur relative à l\'anglais par langue et plateforme', () => {
  withRepo(oneKey({ fr: 'Bonjour à toi', en: 'Hello', es: 'Hola amigo', status: 'required' }), (result) => {
    const entry = result.report.languages.find((l) => l.code === 'es' && l.platform === 'android');
    assert.equal(entry.lengthRatioVsFallback, 2);
    assert.match(output(result), /longueur ≈ ×2 vs en/);
  });
});

test('états iOS : une entrée « needs_review » ou « new » est refusée', () => {
  const spec = oneKey({ fr: 'Salut', en: 'Hi', es: 'Hola', status: 'required' });
  spec.ios.auth_title.localizations.es.stringUnit.state = 'needs_review';
  withRepo(spec, (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /\[es · iOS\] auth_title : état « needs_review »/);
  });
});

test('InfoPlist : clé manquante dans une langue required -> erreur ; les clés techniques ne passent pas par le format snake_case', () => {
  const reg = baseRegistry();
  reg.languages[2].platforms = { android: { status: 'planned', resDir: 'values-es' }, ios: { status: 'required', code: 'es' } };
  const spec = (esValue) => ({
    registry: reg,
    android: {},
    ios: { auth_title: catalogEntry({ fr: 'Salut', en: 'Hi', es: 'Hola' }) },
    infoPlist: { NSCameraUsageDescription: catalogEntry({ fr: 'Photo', en: 'Photo', ...(esValue ? { es: esValue } : {}) }) },
  });
  withRepo(spec('Foto'), (result) => assert.equal(result.code, 0, output(result)));
  withRepo(spec(null), (result) => {
    assert.equal(result.code, 1);
    assert.match(output(result), /\[es · iOS InfoPlist\] NSCameraUsageDescription : clé absente/);
  });
});

test('registre absent : comportement historique (français seul, aucun contrôle par langue)', () => {
  const spec = oneKey({ fr: 'Salut', en: 'Hi' });
  spec.registry = null;
  withRepo(spec, (result) => {
    assert.equal(result.code, 0, output(result));
    assert.equal(result.report, null);
    assert.match(output(result), /Parité des traductions OK : 1 clé/);
  });
});

// ----- Cohérence des sources (critère 6) -----

const enumKt = (...tags) => `enum class AppLanguage(val tag: String?) {\n${tags.map((t) => `    ${t.toUpperCase()}("${t}"),`).join('\n')}\n    SYSTEM(null),\n    ;\n}\n`;
const gradle = (...tags) => `androidComponents {\n    variant.androidResources.localeFilters.addAll(${tags.map((t) => `"${t}"`).join(', ')})\n}\n`;
const projectYml = (...tags) => `targets:\n  Miff:\n    info:\n      properties:\n        CFBundleDevelopmentRegion: en\n        CFBundleLocalizations:\n${tags.map((t) => `          - ${t}`).join('\n')}\n        CFBundleShortVersionString: "1"\n`;

function consistencyRegistry() {
  return baseRegistry({
    consistency: {
      sources: [
        { id: 'android-enum', platform: 'android', role: 'declaration', kind: 'kotlin-enum-tags', file: 'app/AppLanguage.kt' },
        { id: 'android-locale-filters', platform: 'android', role: 'declaration', kind: 'gradle-locale-filters', file: 'app/build.gradle.kts' },
        { id: 'android-res-dirs', platform: 'android', role: 'resources', kind: 'android-res-dirs', file: 'android/app/src/main/res' },
        { id: 'ios-bundle-localizations', platform: 'ios', role: 'declaration', kind: 'project-yml-localizations', file: 'ios/project.yml' },
        { id: 'ios-catalog-languages', platform: 'ios', role: 'resources', kind: 'ios-catalog-languages', file: 'ios/Miff/Resources/Localizable.xcstrings' },
      ],
    },
  });
}

function consistencyRepo({ enumTags = ['fr', 'en'], filters = ['en', 'fr'], bundle = ['fr', 'en'], dirs = ['values-fr', 'values'], iosLangs = ['fr', 'en'] } = {}) {
  const android = Object.fromEntries(dirs.map((d) => [d, xml(str('auth_a', 'x'))]));
  const localizations = Object.fromEntries(iosLangs.map((l) => [l, { stringUnit: { state: 'translated', value: 'x' } }]));
  return makeRepo({
    registry: consistencyRegistry(),
    android,
    ios: { auth_a: { localizations } },
    files: { 'app/AppLanguage.kt': enumKt(...enumTags), 'app/build.gradle.kts': gradle(...filters), 'ios/project.yml': projectYml(...bundle) },
  });
}

const consistency = (spec) => {
  const repo = consistencyRepo(spec);
  try {
    return checkSourcesConsistency(loadRegistry(repo.root), repo.root);
  } finally {
    repo.cleanup();
  }
};

test('cohérence des sources : mêmes langues partout -> aucun constat', () => {
  assert.deepEqual(consistency({}), []);
});

test('cohérence des sources : une langue required absente d\'une source déclarative -> erreur (une source à la fois)', () => {
  const cases = {
    enumTags: ['android-enum', 'langue « en »'],
    filters: ['android-locale-filters', 'langue « fr »'],
    bundle: ['ios-bundle-localizations', 'langue « en »'],
    dirs: ['android-res-dirs', 'langue « fr »'],
    iosLangs: ['ios-catalog-languages', 'langue « en »'],
  };
  const reduced = { enumTags: ['fr'], filters: ['en'], bundle: ['fr'], dirs: ['values'], iosLangs: ['fr'] };
  for (const [option, [source, text]] of Object.entries(cases)) {
    const findings = consistency({ [option]: reduced[option] });
    assert.equal(findings.length, 1, option);
    assert.equal(findings[0].level, 'error');
    assert.equal(findings[0].source, source);
    assert.ok(findings[0].message.includes(text), `${option} : ${findings[0].message}`);
  }
});

test('cohérence des sources : langue inconnue du registre -> erreur ; langue planned déclarée trop tôt -> avertissement', () => {
  const unknown = consistency({ enumTags: ['fr', 'en', 'nl'], dirs: ['values-fr', 'values', 'values-nl'] });
  assert.ok(unknown.some((f) => f.level === 'error' && f.source === 'android-res-dirs' && /inconnue du registre « values-nl »/.test(f.message)));
  const early = consistency({ enumTags: ['fr', 'en', 'es'], filters: ['en', 'fr', 'es'], bundle: ['fr', 'en', 'es'] });
  assert.equal(early.length, 3);
  assert.ok(early.every((f) => f.level === 'warning' && /planned/.test(f.message)));
  // Ressources d'une langue planned (traduction en cours) : normal, aucun constat
  assert.deepEqual(consistency({ dirs: ['values-fr', 'values', 'values-es'], iosLangs: ['fr', 'en', 'es'] }), []);
});

test('cohérence des sources : source déclarée mais fichier absent -> erreur ; dossiers de qualificatifs (night, v29) ignorés', () => {
  const repo = consistencyRepo({ dirs: ['values-fr', 'values', 'values-night', 'values-v29', 'values-w600dp'] });
  try {
    assert.deepEqual(checkSourcesConsistency(loadRegistry(repo.root), repo.root), []);
    rmSync(join(repo.root, 'ios/project.yml'));
    const findings = checkSourcesConsistency(loadRegistry(repo.root), repo.root);
    assert.equal(findings.length, 1);
    assert.match(findings[0].message, /absente : ios\/project\.yml/);
  } finally {
    repo.cleanup();
  }
});

test('cohérence des sources : un échec bloque run (code 1) même si toutes les traductions sont complètes', () => {
  const repo = consistencyRepo({ filters: ['en'] });
  try {
    const result = run({ root: repo.root });
    assert.equal(result.code, 1);
    assert.match(output(result), /ERREUR Cohérence des sources \(android-locale-filters\)/);
  } finally {
    repo.cleanup();
  }
});

test('cohérence des sources du vrai dépôt : enum, localeFilters, CFBundleLocalizations, dossiers et catalogue = langues required', () => {
  const registry = loadRegistry(repoRoot);
  assert.deepEqual(checkSourcesConsistency(registry, repoRoot), []);
});

// ----- Ligne de commande -----

test('CLI : --json produit un rapport lisible par machine ; code de sortie conforme', () => {
  const repo = makeRepo(oneKey({ fr: 'Salut', en: 'Hi', es: 'Hola', status: 'required' }));
  try {
    const ok = spawnSync(process.execPath, [script, '--root', repo.root, '--json'], { encoding: 'utf8' });
    assert.equal(ok.status, 0, ok.stderr);
    const parsed = JSON.parse(ok.stdout);
    assert.equal(parsed.exitCode, 0);
    assert.equal(parsed.report.summary.errors, 0);
    assert.deepEqual(parsed.report.languages.filter((l) => l.platform === 'android').map((l) => l.code), ['fr', 'en', 'es']);
    assert.ok(parsed.report.languages.every((l) => 'expected' in l && 'present' in l && 'missing' in l));
    // clé manquante en es required -> code 1 et constat dans le JSON
    const ko = makeRepo(oneKey({ fr: 'Salut', en: 'Hi', es: undefined, status: 'required' }));
    try {
      const failed = spawnSync(process.execPath, [script, '--root', ko.root, '--json'], { encoding: 'utf8' });
      assert.equal(failed.status, 1);
      const report = JSON.parse(failed.stdout).report;
      assert.ok(report.findings.some((f) => f.check === 'missing-key' && f.lang === 'es' && f.effectiveLevel === 'error'));
    } finally {
      ko.cleanup();
    }
  } finally {
    repo.cleanup();
  }
});

test('CLI : --lang limite à une langue, langue inconnue -> code 2, --summary écrit un tableau Markdown', () => {
  const repo = makeRepo(oneKey({ fr: 'Salut', en: 'Hi', es: undefined }));
  try {
    const only = spawnSync(process.execPath, [script, '--root', repo.root, '--lang', 'es', '--json'], { encoding: 'utf8' });
    assert.equal(only.status, 0, only.stderr);
    assert.deepEqual([...new Set(JSON.parse(only.stdout).report.languages.map((l) => l.code))], ['es']);
    const unknown = spawnSync(process.execPath, [script, '--root', repo.root, '--lang', 'zz'], { encoding: 'utf8' });
    assert.equal(unknown.status, 2);
    const summary = join(repo.root, 'summary.md');
    const withSummary = spawnSync(process.execPath, [script, '--root', repo.root, '--summary', summary], { encoding: 'utf8' });
    assert.equal(withSummary.status, 0);
    const markdown = readFileSync(summary, 'utf8');
    assert.match(markdown, /\| es \(ES\) \| Android \| planned \| 0\/1 \| 1 \|/);
    assert.equal(markdown, markdownSummary(run({ root: repo.root }).report));
  } finally {
    repo.cleanup();
  }
});

test('CLI : registre explicite (--registry) et registre invalide -> code 2', () => {
  const repo = makeRepo({ registry: null, android: { 'values-fr': xml(str('auth_a', 'x')) } });
  try {
    const bad = join(repo.root, 'bad.json');
    writeFileSync(bad, '{ pas du json');
    const result = spawnSync(process.execPath, [script, '--root', repo.root, '--registry', bad], { encoding: 'utf8' });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /registre/i);
  } finally {
    repo.cleanup();
  }
});
