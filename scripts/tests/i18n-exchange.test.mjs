// Tests de scripts/i18n-exchange.mjs et de ses bibliothèques (fichier d'échange de traductions, lot 4A du ticket « Miff en 6 langues »).
// Les dépôts de test sont fabriqués dans un dossier temporaire à partir de petits fichiers (quelques textes, un pluriel, un texte qui diffère
// entre Android et iOS). Aucun test ne lit ni n'écrit les ressources réelles de l'app (sauf un test de lecture seule).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadWorld, main, parseArgs } from '../i18n-exchange.mjs';
import { checkLanguageTable } from '../lib/i18n-checks.mjs';
import { addressRow, extractLanguageKit, languageRows, parseSections, parseTables } from '../lib/i18n-docs.mjs';
import {
  applyIosCatalog, buildAndroidStrings, buildExchange, canonicalPlaceholders, checkTranslations, diffKeys, encodeAndroidValue, exchangeFiles, explicitPlaceholders,
  freezeReasons, indexExchange, iosTypeMap, modelFromValue, parseAndroidStructure, placeholderTokens, prepareModels, toIosPlaceholders, valueFromModel,
} from '../lib/i18n-exchange.mjs';
import { balancedChunks, buildLots, expectedPluralForms } from '../lib/i18n-lots.mjs';
import { catalogLocalization, decodeAndroidValue, parseAndroidStrings, parseCatalog, placeholderSignatures } from '../lib/i18n-resources.mjs';

const repoRoot = fileURLToPath(new URL('../../', import.meta.url));
const NBSP = ' ';
const registry = JSON.parse(readFileSync(join(repoRoot, 'docs/contracts/i18n-languages.json'), 'utf8'));

// ----- Fixtures -----

const FR_XML = `<?xml version="1.0" encoding="utf-8"?>
<!-- FRANÇAIS : source -->
<resources xmlns:tools="http://schemas.android.com/tools">
    <!-- Écran d'accueil -->
    <string name="home_title" tools:ignore="UnusedResources">Miff</string>
    <string name="home_tagline">Savoir que tes proches vont bien, sans les déranger.</string>
    <string name="home_apostrophe">Rien n\\'est perdu &amp; tout va bien</string>
    <string name="app_name" translatable="false">Miff</string>

    <!-- Fil -->
    <string name="feed_greeting">%1$s a donné des nouvelles à %2$s</string>
    <string name="feed_try_again_button">Réessayer</string>
    <string name="feed_age_label">Il y a 12&#160;min : %1$d&#160;%%</string>
    <plurals name="profile_tab_miff_members">
        <item quantity="one">%1$d membre</item>
        <item quantity="many">%1$d de membres</item>
        <item quantity="other">%1$d membres</item>
    </plurals>
    <string name="feed_place_noun_home">la maison</string>
    <string name="place_profile_title_named">Lieux de %1$s</string>
    <string name="android_battery_tip">Ouvre les réglages de batterie</string>
</resources>
`;
const EN_XML = `<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="home_title" tools:ignore="UnusedResources">Miff</string>
    <string name="home_tagline">Know your loved ones are fine, without bothering them.</string>
    <string name="home_apostrophe">Nothing is lost &amp; all is well</string>
    <string name="app_name" translatable="false">Miff</string>
    <string name="feed_greeting">%1$s gave news to %2$s</string>
    <string name="feed_try_again_button">Try again</string>
    <string name="feed_age_label">12&#160;min ago: %1$d&#160;%%</string>
    <plurals name="profile_tab_miff_members">
        <item quantity="one">%1$d member</item>
        <item quantity="other">%1$d members</item>
    </plurals>
    <string name="feed_place_noun_home">home</string>
    <string name="place_profile_title_named">Places of %1$s</string>
    <string name="android_battery_tip">Open the battery settings</string>
</resources>
`;

const unit = (value) => ({ stringUnit: { state: 'translated', value } });
const loc = (fr, en) => ({ extractionState: 'manual', localizations: { en: unit(en), fr: unit(fr) } });
const plural = (forms) => ({ variations: { plural: Object.fromEntries(Object.entries(forms).map(([k, v]) => [k, unit(v)])) } });

const IOS = {
  sourceLanguage: 'fr',
  strings: {
    home_title: loc('Miff', 'Miff'),
    home_tagline: loc('Savoir que tes proches vont bien, sans les déranger.', 'Know your loved ones are fine, without bothering them.'),
    home_apostrophe: loc('Rien n\'est perdu & tout va bien', 'Nothing is lost & all is well'),
    feed_greeting: loc('%1$@ a donné des nouvelles à %2$@', '%1$@ gave news to %2$@'),
    feed_try_again_button: loc('Réessayer', 'Try again'),
    feed_age_label: loc(`Il y a 12${NBSP}min : %1$lld${NBSP}%%`, `12${NBSP}min ago: %1$lld${NBSP}%%`),
    profile_tab_miff_members: { extractionState: 'manual', localizations: { en: plural({ one: '%lld member', other: '%lld members' }), fr: plural({ one: '%lld membre', many: '%lld de membres', other: '%lld membres' }) } },
    feed_place_noun_home: loc('la maison', 'home'),
    place_profile_title_named: loc('Lieux de %@ (iPhone)', 'Places of %@ (iPhone)'),
    ios_permission_hint: { comment: 'Écran des permissions', extractionState: 'manual', localizations: { en: unit('Tap %@'), fr: unit('Touche %@') } },
    ios_brand: { extractionState: 'manual', shouldTranslate: false, localizations: { fr: unit('Miff') } },
  },
  version: '1.0',
};
const INFO = {
  sourceLanguage: 'fr',
  strings: {
    NSCameraUsageDescription: loc('Miff utilise la caméra seulement quand tu publies une photo.', 'Miff uses the camera only when you post a photo.'),
  },
  version: '1.0',
};
const catalogText = (value) => `${JSON.stringify(value, null, 2)}\n`;

const GLOSSARY = `# Glossaire
## 2. Termes figés
### 2.1 Marque
| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G01 | **Miff** | Miff | Miff | Miff | Miff | Miff | Jamais traduit |
| G07 | proches | loved ones | seres queridos | cari | entes queridos | Liebsten | Terme le plus fréquent |
## 3. Formes d'adresse (tutoiement) par langue
| Langue | Pronom | Interdits |
|---|---|---|
| **es** (neutre) | tú | usted |
| **pt-PT** | tu | você |
| **de** | du | Sie |
## 4. Termes proscrits pour le ton
| Famille | es | it | pt-PT | de |
|---|---|---|---|---|
| Urgence | urgente, alarma | urgente | urgente | dringend |
## 5. Phrases assemblées
| Clé | fr | es | it | pt-PT | de |
|---|---|---|---|---|---|
| \`feed_label_left\` | A quitté {lieu} | Ha salido {lieu} | Ha lasciato {lieu} | Saiu {lieu} | Hat {lieu} verlassen |
## 6. Noms des réglages
| Libellé (fr) | iOS es | iOS it | iOS pt-PT | iOS de |
|---|---|---|---|---|
| Réglages | Ajustes | Impostazioni | Definições | Einstellungen |
## 7. Pièges
| Piège | es | it | pt-PT | de |
|---|---|---|---|---|
| Participe | rare | fréquent | rare | aucun |
`;
const GUIDE = `# Guide
## 1. Principes
Tutoiement.
## 2. Fiche de style par langue
### 2.1 Tableau de synthèse
| Sujet | fr (source) | **es** (neutre) | **it** | **pt-PT** (AO 1990) | **de** |
|---|---|---|---|---|---|
| Adresse | tu | **tú** | **tu** | **tu** | **du** |
### 2.2 Espagnol (\`es\`)
- Une seule langue.
### 2.3 Italien
- Impératif.
### 2.4 Portugais
- Portugal.
### 2.5 Allemand
- du.
## 3. Pluriels
Règle.
## 4. Longueurs
Budgets.
## 5. Ton
Principes.
`;

function makeRepo(extra = {}) {
  const root = mkdtempSync(join(tmpdir(), 'miff-exchange-'));
  const put = (rel, content) => {
    mkdirSync(dirname(join(root, rel)), { recursive: true });
    writeFileSync(join(root, rel), content);
  };
  put('docs/contracts/i18n-languages.json', `${JSON.stringify(registry, null, 2)}\n`);
  put('android/app/src/main/res/values-fr/strings.xml', FR_XML);
  put('android/app/src/main/res/values/strings.xml', EN_XML);
  put('ios/Miff/Resources/Localizable.xcstrings', catalogText(IOS));
  put('ios/Miff/Resources/InfoPlist.xcstrings', catalogText(INFO));
  put('docs/i18n/glossaire.md', GLOSSARY);
  put('docs/i18n/guide-de-style.md', GUIDE);
  put('android/app/src/main/java/com/tomessa/miff/ui/home/HomeScreen.kt', 'Text(stringResource(R.string.home_tagline))\n');
  put('ios/Miff/Views/HomeView.swift', 'Text("home_tagline")\n');
  for (const [rel, content] of Object.entries(extra)) put(rel, content);
  return { root, put, read: (rel) => readFileSync(join(root, rel), 'utf8'), exists: (rel) => existsSync(join(root, rel)), cleanup: () => rmSync(root, { recursive: true, force: true }) };
}

const config = { gel: { prefixes: ['feed_place_'], cles: [] }, sensibles: { motifs: ['^feed_'] }, motifs: {} };
const build = (overrides = {}) => buildExchange({
  androidFrXml: FR_XML, androidEnXml: EN_XML, iosCatalogText: catalogText(IOS), iosInfoText: catalogText(INFO), registry, config, ...overrides,
});

/** Traductions correctes en espagnol pour la fixture. */
const ES = {
  home_title: 'Miff',
  home_tagline: 'Saber que tus seres queridos están bien, sin molestarles.',
  home_apostrophe: 'No se pierde nada & todo va bien',
  feed_greeting: '%1$s ha dado noticias a %2$s',
  feed_try_again_button: 'Reintentar',
  feed_age_label: `Hace 12${NBSP}min: %1$d${NBSP}%%`,
  profile_tab_miff_members: { one: '%1$d miembro', many: '%1$d miembros', other: '%1$d miembros' },
  feed_place_noun_home: 'casa',
  place_profile_title_named: { android: 'Lugares de %1$s', ios: 'Lugares de %1$s (iPhone)' },
  android_battery_tip: 'Abre los ajustes de batería',
  ios_permission_hint: 'Toca %@',
  NSCameraUsageDescription: 'Miff usa la cámara solo cuando publicas una foto.',
};
const check = (translations, lang = 'es', options = {}) => {
  const result = build();
  return checkTranslations({ registry, config, byKey: indexExchange(result), lang, translations, ...options });
};
const errorsOf = (report) => report.findings.filter((f) => f.level === 'error');
const checks = (report, level = 'error') => report.findings.filter((f) => f.level === level).map((f) => `${f.key}:${f.check}`);

// ----- Valeurs insérées -----

test('notation canonique : %@ -> %s, %lld -> %d, positions conservées', () => {
  assert.equal(canonicalPlaceholders('%1$@ a vu %2$lld fois %@ %lld %%'), '%1$s a vu %2$d fois %s %d %%');
  assert.equal(canonicalPlaceholders('%2$02lld h %1$.1f'), '%2$02d h %1$.1f');
  assert.equal(explicitPlaceholders('%s et %d, puis %3$s'), '%1$s et %2$d, puis %3$s');
  assert.deepEqual(placeholderTokens(['%1$@ et %2$lld', '%1$@']), ['%1$s', '%2$d']);
});

test('types iOS calqués sur le français iOS ; défauts %@ et %lld', () => {
  const map = iosTypeMap(['%1$@ %2$d %3$lld']);
  assert.equal(toIosPlaceholders('%1$s %2$d %3$d', map), '%1$@ %2$d %3$lld');
  assert.equal(toIosPlaceholders('%1$d membres', iosTypeMap(['%lld membres'])), '%lld membres', 'une seule valeur sans position en iOS : écriture conservée');
  assert.equal(toIosPlaceholders('%1$d membres', iosTypeMap(['%1$lld membres'])), '%1$lld membres');
  assert.equal(toIosPlaceholders('%s et %d, 100 %%', new Map()), '%@ et %lld, 100 %%');
  assert.equal(toIosPlaceholders('%2$02d', new Map()), '%2$02lld');
});

test('encodage Android : aller-retour avec decodeAndroidValue', () => {
  const samples = [
    "L'app d'un ami", 'Dis "bonjour" & à bientôt', '1 < 2', `12${NBSP}min`, `30${NBSP}%`, '@mention au début', '?question', 'ligne 1\nligne 2', 'antislash \\ ici',
    'Miff — tiret', 'n’est pas droit', '100 %% sûr %1$s',
  ];
  for (const text of samples) assert.equal(decodeAndroidValue(encodeAndroidValue(text)), text, text);
  assert.equal(encodeAndroidValue("l'app"), "l\\'app");
  assert.equal(encodeAndroidValue('A & B'), 'A &amp; B');
  assert.equal(encodeAndroidValue(`1${NBSP}h`), '1&#160;h');
  assert.equal(encodeAndroidValue('"x"'), '\\"x\\"');
  assert.equal(encodeAndroidValue('@a'), '\\@a');
});

// ----- Extraction -----

test('extraction : communs, propres, InfoPlist, ressources non traduisibles ignorées', () => {
  const result = build();
  assert.deepEqual(result.communs.map((e) => e.cle), [
    'home_title', 'home_tagline', 'home_apostrophe', 'feed_greeting', 'feed_try_again_button', 'feed_age_label', 'profile_tab_miff_members', 'feed_place_noun_home', 'place_profile_title_named',
  ]);
  assert.deepEqual(result.propresAndroid.map((e) => e.cle), ['android_battery_tip']);
  assert.deepEqual(result.propresIos.map((e) => e.cle), ['ios_permission_hint', 'NSCameraUsageDescription']);
  assert.equal(result.propresIos[1].table, 'InfoPlist.xcstrings');
  assert.equal(result.stats.communs, 9);
  assert.equal(result.stats.parLangue, 12);
  assert.equal(result.stats.infoPlist, 1);
  assert.equal(result.warnings.length, 0);
});

test('extraction : texte décodé, valeurs insérées en notation Android, contexte', () => {
  const byKey = indexExchange(build());
  const apos = byKey.get('home_apostrophe');
  assert.equal(apos.fr, "Rien n'est perdu & tout va bien");
  assert.equal(apos.en, 'Nothing is lost & all is well');
  const greeting = byKey.get('feed_greeting');
  assert.deepEqual(greeting.placeholders, ['%1$s', '%2$s']);
  assert.equal(greeting.fr, '%1$s a donné des nouvelles à %2$s');
  assert.equal(greeting.contexte.ecran, 'Fil');
  assert.equal(byKey.get('home_title').contexte.ecran, "Écran d'accueil");
  assert.equal(byKey.get('feed_age_label').fr, `Il y a 12${NBSP}min : %1$d${NBSP}%%`);
  assert.equal(byKey.get('ios_permission_hint').contexte.commentaire, 'Écran des permissions');
});

test('extraction : pluriels (formes du français et de l\'anglais), longueurs, étiquettes courtes', () => {
  const byKey = indexExchange(build());
  const members = byKey.get('profile_tab_miff_members');
  assert.equal(members.type, 'plural');
  assert.deepEqual(members.fr, { one: '%1$d membre', many: '%1$d de membres', other: '%1$d membres' });
  assert.deepEqual(members.en, { one: '%1$d member', other: '%1$d members' });
  assert.deepEqual(members.placeholders, ['%1$d']);
  const button = byKey.get('feed_try_again_button');
  assert.equal(button.courte, true);
  assert.equal(button.longueurMax, Math.ceil(10 * 1.4));
  const tagline = byKey.get('home_tagline');
  assert.equal(tagline.courte, false);
  assert.equal(tagline.longueurMax, Math.floor([...tagline.fr].length * 1.5));
});

test('extraction : texte qui diffère entre Android et iOS = entrée divergente ; différences d\'écriture (%s / %1$s, ’) ignorées', () => {
  const byKey = indexExchange(build());
  const named = byKey.get('place_profile_title_named');
  assert.equal(named.fr, 'Lieux de %1$s');
  assert.equal(named.divergent.ios.fr, 'Lieux de %s (iPhone)');
  assert.equal(byKey.get('feed_greeting').divergent, undefined);
  assert.equal(byKey.get('home_tagline').divergent, undefined);
  // Même texte, seule l'apostrophe typographique change : pas de divergence.
  const ios = structuredClone(IOS);
  ios.strings.home_apostrophe = loc('Rien n’est perdu & tout va bien', 'Nothing is lost & all is well');
  const other = indexExchange(build({ iosCatalogText: catalogText(ios) }));
  assert.equal(other.get('home_apostrophe').divergent, undefined);
});

test('extraction : texte identique en français et en anglais = invariable probable ; motifs et sensibles', () => {
  const byKey = indexExchange(build({ config: { ...config, motifs: { feed_try_again_button: { es: 'x' } } } }));
  assert.equal(byKey.get('home_title').invariableProbable, true);
  assert.equal(byKey.get('home_tagline').invariableProbable, undefined);
  assert.equal(byKey.get('feed_greeting').sensible, true);
  assert.equal(byKey.get('home_title').sensible, undefined);
  assert.equal(byKey.get('feed_try_again_button').motif, true);
});

test('extraction : substitutions iOS (type substituted) et variations non gérées', () => {
  const ios = structuredClone(IOS);
  ios.strings.feed_greeting = {
    extractionState: 'manual',
    localizations: {
      en: { stringUnit: { state: 'translated', value: '%#@n@ news' }, substitutions: { n: { argNum: 1, formatSpecifier: 'lld', variations: { plural: { one: unit('%arg item'), other: unit('%arg items') } } } } },
      fr: { stringUnit: { state: 'translated', value: '%#@n@ nouvelles' }, substitutions: { n: { argNum: 1, formatSpecifier: 'lld', variations: { plural: { one: unit('%arg élément'), other: unit('%arg éléments') } } } } },
    },
  };
  ios.strings.feed_try_again_button = { extractionState: 'manual', localizations: { fr: { variations: { device: { iphone: unit('Réessayer') } } }, en: unit('Try again') } };
  const result = build({ iosCatalogText: catalogText(ios) });
  assert.ok(result.unsupported.includes('feed_try_again_button'));
  assert.ok(result.warnings.some((w) => w.includes('feed_try_again_button')));
  assert.ok(!result.communs.some((e) => e.cle === 'feed_try_again_button'));
  // Le français Android (« %1$s a donné… ») diffère du français iOS (substitution) : divergent, type « substituted » côté iOS.
  const entry = indexExchange(result).get('feed_greeting');
  assert.equal(entry.divergent.ios.type, 'substituted');
  assert.deepEqual(entry.divergent.ios.fr.formes.n, { one: '%arg élément', other: '%arg éléments' });
});

test('extraction : déterministe (deux passes identiques) et fichiers d\'échange', () => {
  const a = JSON.stringify(exchangeFiles(build(), { registry }));
  const b = JSON.stringify(exchangeFiles(build(), { registry }));
  assert.equal(a, b);
  const files = exchangeFiles(build(), { registry });
  assert.deepEqual(Object.keys(files).sort(), [
    'docs/i18n/echange/propres-android.json', 'docs/i18n/echange/propres-ios.json', 'docs/i18n/echange/source-fr-en.json',
  ]);
});

test('extraction : usages dans le code (contexte « écran »)', () => {
  const usages = new Map([['home_tagline', { android: ['ui/home/HomeScreen.kt'], ios: ['Views/HomeView.swift'] }]]);
  const byKey = indexExchange(build({ usages }));
  assert.deepEqual(byKey.get('home_tagline').contexte.usages.android, ['ui/home/HomeScreen.kt']);
});

// ----- Gel -----

test('gel : préfixes, clés listées et clés touchées par une PR ouverte', () => {
  const snapshot = { prs: [{ numero: 282, ajoutees: ['feed_place_leave_home'], supprimees: ['feed_place_noun_home'], modifiees: ['feed_greeting'] }, { numero: 5, ajoutees: [], supprimees: [], modifiees: ['home_title'] }] };
  const cfg = { ...config, gel: { prefixes: ['feed_place_'], cles: ['home_apostrophe'] } };
  const byKey = indexExchange(build({ snapshot, config: cfg }));
  assert.equal(byKey.get('feed_place_noun_home').gele, true);
  assert.ok(byKey.get('feed_place_noun_home').geleRaisons.some((r) => r.includes('feed_place_')));
  assert.ok(byKey.get('feed_place_noun_home').geleRaisons.some((r) => r.includes('#282')));
  assert.deepEqual(byKey.get('feed_greeting').geleRaisons, ['PR #282 ouverte (modifie le texte)']);
  assert.equal(byKey.get('home_apostrophe').gele, true);
  assert.equal(byKey.get('home_title').gele, true);
  assert.equal(byKey.get('home_tagline').gele, false);
  assert.deepEqual(freezeReasons('x_y', { gel: { prefixes: [], cles: [] } }, null), []);
});

test('gel : diffKeys détecte clés ajoutées, supprimées et modifiées (Android et iOS)', () => {
  const beforeXml = '<resources><string name="a_one">Un</string><string name="a_two">Deux</string><string name="a_old">Vieux</string></resources>';
  const afterXml = '<resources><string name="a_one">Un</string><string name="a_two">Deux modifié</string><string name="a_new">Neuf</string></resources>';
  const before = { androidFr: beforeXml, iosLocalizable: catalogText({ strings: { b_one: loc('A', 'A'), b_two: loc('B', 'B') } }) };
  const after = { androidFr: afterXml, iosLocalizable: catalogText({ strings: { b_one: loc('A', 'A'), b_two: loc('B2', 'B'), b_new: loc('N', 'N') } }) };
  const diff = diffKeys(before, after);
  assert.deepEqual(diff.ajoutees, ['a_new', 'b_new']);
  assert.deepEqual(diff.supprimees, ['a_old']);
  assert.deepEqual(diff.modifiees, ['a_two', 'b_two']);
  assert.deepEqual(diffKeys(before, before), { ajoutees: [], supprimees: [], modifiees: [] });
});

// ----- Contrôle de forme -----

test('contrôle : une traduction correcte passe sans erreur', () => {
  const report = check(ES);
  assert.deepEqual(errorsOf(report), []);
  assert.equal(report.unknown.length, 0);
});

test('contrôle : valeurs insérées différentes, absentes ou en trop = erreur', () => {
  assert.ok(checks(check({ feed_greeting: '%1$s ha dado noticias' })).includes('feed_greeting:placeholders'));
  assert.ok(checks(check({ feed_greeting: '%1$s ha dado noticias a %3$s' })).includes('feed_greeting:placeholders'));
  assert.deepEqual(checks(check({ feed_greeting: '%2$s recibe noticias de %1$s' })), []);
});

test('contrôle : formes de pluriel (es : one et other, many recommandée ; de : jamais many)', () => {
  assert.ok(checks(check({ profile_tab_miff_members: { one: '%1$d miembro' } })).includes('profile_tab_miff_members:plural-forms'));
  assert.ok(checks(check({ profile_tab_miff_members: { one: '%1$d miembro', other: '%1$d miembros' } })).includes('profile_tab_miff_members:plural-recommended'));
  assert.ok(checks(check({ profile_tab_miff_members: { one: '%1$d miembro', many: 'x %1$d', other: '%1$d miembros', few: '%1$d' } })).includes('profile_tab_miff_members:plural-forms'));
  assert.ok(checks(check({ profile_tab_miff_members: '%1$d miembros' })).includes('profile_tab_miff_members:forme'));
  const de = check({ profile_tab_miff_members: { one: '%1$d Mitglied', other: '%1$d Mitglieder' } }, 'de');
  assert.deepEqual(checks(de), []);
  assert.ok(checks(check({ profile_tab_miff_members: { one: '%1$d Mitglied', many: '%1$d Mitglieder', other: '%1$d Mitglieder' } }, 'de')).includes('profile_tab_miff_members:plural-forms'));
});

test('contrôle : vide, identique au français, texte non traduit', () => {
  assert.ok(checks(check({ home_tagline: '' })).includes('home_tagline:empty'));
  assert.ok(checks(check({ home_tagline: '   ' })).includes('home_tagline:empty'));
  assert.ok(checks(check({ home_tagline: 'Savoir que tes proches vont bien, sans les déranger.' })).includes('home_tagline:identical-to-source'));
  // Marque seule ou texte français = anglais (invariable probable) : pas d'erreur.
  assert.deepEqual(checks(check({ home_title: 'Miff' })), []);
  assert.deepEqual(checks(check({ home_title: 'Miff' }), 'warning').filter((x) => x.includes('home_title')), []);
});

test('contrôle : genre, vouvoiement, termes proscrits (registre), tiret long, marque', () => {
  assert.ok(checks(check({ home_tagline: 'Bienvenido/a, sabe que están bien' })).includes('home_tagline:gender-mark'));
  assert.ok(checks(check({ home_tagline: 'Usted puede estar tranquilo' })).includes('home_tagline:formal-address'));
  assert.ok(checks(check({ home_tagline: 'Saber que están bien — sin molestar' })).includes('home_tagline:tiret-long'));
  assert.ok(checks(check({ home_tagline: 'Descubre miff hoy' })).includes('home_tagline:marque'));
  assert.ok(checks(check({ home_tagline: 'Descubre Miff Plus hoy' })).includes('home_tagline:marque'));
  assert.ok(checks(check({ home_tagline: 'Ruf an: Sie können anrufen' }, 'de')).includes('home_tagline:formal-address'));
});

test('contrôle : espaces insécables, ponctuation haute, pourcentage selon la langue', () => {
  assert.ok(checks(check({ home_tagline: `¿Todo bien${NBSP}?` })).includes('home_tagline:high-punctuation'));
  assert.ok(checks(check({ feed_age_label: 'Hace 12 min: %1$d %%' }), 'warning').includes('feed_age_label:nbsp-lost'));
  // « 30 % » : NBSP en es et de, collé en it et pt.
  assert.ok(checks(check({ home_tagline: 'Casi un 30 % de ahorro' })).includes('home_tagline:pourcentage'));
  assert.deepEqual(checks(check({ home_tagline: `Casi un 30${NBSP}% de ahorro` })), []);
  assert.ok(checks(check({ home_tagline: `Quasi il 30${NBSP}% di risparmio` }, 'it')).includes('home_tagline:pourcentage'));
  assert.deepEqual(checks(check({ home_tagline: 'Quasi il 30% di risparmio' }, 'it')), []);
  assert.ok(checks(check({ home_tagline: 'Fast 30% Ersparnis' }, 'de')).includes('home_tagline:pourcentage'));
  assert.ok(checks(check({ home_tagline: 'Dura 5 min en total' }), 'warning').includes('home_tagline:unite-espace'));
});

test('contrôle : entités XML, barres obliques, espaces de bord, pourcent isolé', () => {
  assert.ok(checks(check({ home_tagline: 'Todo &amp; nada' })).includes('home_tagline:entite-xml'));
  assert.ok(checks(check({ home_tagline: 'No se pierde l\\\'app' })).includes('home_tagline:barre-oblique'));
  assert.ok(checks(check({ home_tagline: ' Hola ' })).includes('home_tagline:espaces'));
  assert.ok(checks(check({ feed_greeting: '%1$s ha dado el 100 % a %2$s' })).includes('feed_greeting:pourcent-isole'));
});

test('contrôle : clé divergente = objet {android, ios} ; texte unique accepté pour les deux', () => {
  assert.deepEqual(errorsOf(check({ place_profile_title_named: 'Lugares de %1$s' })), []);
  assert.deepEqual(errorsOf(check({ place_profile_title_named: { android: 'Lugares de %1$s', ios: 'Lugares de %1$s (iPhone)' } })), []);
  const bad = check({ place_profile_title_named: { android: 'Lugares de %1$s', ios: 'Lugares de' } });
  assert.ok(bad.findings.some((f) => f.platform === 'ios' && f.check === 'placeholders'));
});

test('contrôle : clés inconnues signalées ; clés absentes = erreur seulement en mode complet', () => {
  const report = check({ cle_inconnue: 'x' });
  assert.deepEqual(report.unknown, ['cle_inconnue']);
  assert.equal(report.errors, 0);
  assert.ok(report.missing.includes('home_title'));
  const complete = check(ES, 'es', { complete: true });
  assert.equal(complete.errors, 0);
  const partial = check({ home_title: 'Miff' }, 'es', { complete: true });
  assert.ok(partial.findings.some((f) => f.check === 'missing-key' && f.key === 'home_tagline'));
});

test('contrôle : motifs de format imposés', () => {
  const cfg = { ...config, motifs: { feed_try_again_button: { es: 'HH:mm' } } };
  const result = build({ config: cfg });
  const report = checkTranslations({ registry, config: cfg, byKey: indexExchange(result), lang: 'es', translations: { feed_try_again_button: 'Reintentar' } });
  assert.ok(report.findings.some((f) => f.check === 'motif' && f.level === 'error'));
  const ok = checkTranslations({ registry, config: cfg, byKey: indexExchange(result), lang: 'es', translations: { feed_try_again_button: 'HH:mm' } });
  assert.ok(!ok.findings.some((f) => f.check === 'motif'));
});

test('contrôle : refuse la langue source et la langue de repli', () => {
  assert.throws(() => check({}, 'fr'), /source ou de repli/);
  assert.throws(() => check({}, 'en'), /source ou de repli/);
  assert.throws(() => check({}, 'zz'), /absente du registre/);
});

test('contrôle : budget des étiquettes courtes et longueur (avertissements, jamais bloquants)', () => {
  const report = check({ feed_try_again_button: 'Intentarlo de nuevo otra vez ahora', home_tagline: 'Saber que tus seres queridos están bien, sin molestarles nunca jamás, de ninguna manera posible' });
  assert.ok(checks(report, 'warning').includes('feed_try_again_button:short-label-budget'));
  assert.ok(checks(report, 'warning').includes('home_tagline:length'));
  assert.ok(!checks(report).includes('feed_try_again_button:short-label-budget'));
});

// ----- Réinjection Android -----

test('réinjection Android : structure du français, échappements, pluriels, attributs', () => {
  const byKey = indexExchange(build());
  const models = prepareModels(byKey, ES);
  const { xml, written } = buildAndroidStrings({ frXml: FR_XML, existingXml: null, lang: 'es', nativeName: 'Español', models: models.android });
  assert.equal(written, 10);
  assert.match(xml, /<resources xmlns:tools="http:\/\/schemas.android.com\/tools">/);
  assert.match(xml, /<!-- Écran d'accueil -->/);
  assert.match(xml, /<string name="home_title" tools:ignore="UnusedResources">Miff<\/string>/);
  assert.match(xml, /<string name="home_apostrophe">No se pierde nada &amp; todo va bien<\/string>/);
  assert.match(xml, /Hace 12&#160;min: %1\$d&#160;%%/);
  assert.match(xml, /<plurals name="profile_tab_miff_members">\s*<item quantity="one">%1\$d miembro<\/item>\s*<item quantity="many">%1\$d miembros<\/item>\s*<item quantity="other">/);
  assert.ok(!xml.includes('app_name'), 'ressource non traduisible non recopiée');
  assert.ok(!xml.includes('ios_permission_hint'));
  // Le texte relu par les lecteurs du dépôt est celui de la traduction.
  const parsed = parseAndroidStrings(xml);
  assert.equal(decodeAndroidValue(parsed.get('home_apostrophe').texts[0].raw), ES.home_apostrophe);
  assert.deepEqual(parsed.get('profile_tab_miff_members').texts.map((t) => t.id), ['one', 'many', 'other']);
});

test('réinjection Android : apostrophes et guillemets échappés, valeurs insérées canoniques', () => {
  const byKey = indexExchange(build());
  const translations = { home_apostrophe: "Nada se pierde y 'todo' \"va\" bien: l'app", feed_greeting: '%1$@ ha dado noticias a %2$@' };
  const models = prepareModels(byKey, translations);
  const { xml } = buildAndroidStrings({ frXml: FR_XML, existingXml: null, lang: 'es', nativeName: 'Español', models: models.android });
  assert.match(xml, /Nada se pierde y \\'todo\\' \\"va\\" bien: l\\'app/);
  assert.match(xml, /<string name="feed_greeting">%1\$s ha dado noticias a %2\$s<\/string>/);
});

test('réinjection Android : idempotente, conserve les textes déjà présents et les clés absentes du français', () => {
  const byKey = indexExchange(build());
  const first = buildAndroidStrings({ frXml: FR_XML, existingXml: null, lang: 'es', nativeName: 'Español', models: prepareModels(byKey, ES).android });
  const second = buildAndroidStrings({ frXml: FR_XML, existingXml: first.xml, lang: 'es', nativeName: 'Español', models: prepareModels(byKey, ES).android });
  assert.equal(second.xml, first.xml);
  assert.equal(second.unchanged, second.written);
  // Une traduction partielle ne retire rien de ce qui existe déjà.
  const partial = buildAndroidStrings({ frXml: FR_XML, existingXml: first.xml, lang: 'es', nativeName: 'Español', models: prepareModels(byKey, { home_title: 'Miff' }).android });
  assert.equal(partial.kept, 9);
  assert.match(partial.xml, /Reintentar/);
  // Clé inconnue du français déjà présente : conservée à la fin (le script de parité la signale).
  const withExtra = first.xml.replace('</resources>', '    <string name="orpheline">Hola</string>\n</resources>');
  const kept = buildAndroidStrings({ frXml: FR_XML, existingXml: withExtra, lang: 'es', nativeName: 'Español', models: prepareModels(byKey, ES).android });
  assert.deepEqual(kept.extra, ['orpheline']);
  assert.match(kept.xml, /name="orpheline">Hola/);
});

// ----- Réinjection iOS -----

test('réinjection iOS : langue ajoutée, fr et en inchangés, état translated, types iOS', () => {
  const byKey = indexExchange(build());
  const models = prepareModels(byKey, ES);
  const before = JSON.parse(catalogText(IOS));
  const applied = applyIosCatalog(catalogText(IOS), 'es', models.ios);
  const after = JSON.parse(applied.text);
  assert.equal(applied.written, 10);
  assert.deepEqual(applied.ignored, []);
  for (const [key, entry] of Object.entries(before.strings)) {
    assert.deepEqual(after.strings[key].localizations.fr, entry.localizations.fr, `fr ${key}`);
    assert.deepEqual(after.strings[key].localizations.en, entry.localizations.en, `en ${key}`);
  }
  assert.deepEqual(Object.keys(after.strings), Object.keys(before.strings), 'ordre des clés conservé');
  assert.deepEqual(Object.keys(after.strings.home_title.localizations), ['en', 'es', 'fr']);
  assert.equal(after.strings.feed_greeting.localizations.es.stringUnit.value, '%1$@ ha dado noticias a %2$@');
  assert.equal(after.strings.feed_age_label.localizations.es.stringUnit.value, `Hace 12${NBSP}min: %1$lld${NBSP}%%`);
  assert.equal(after.strings.feed_greeting.localizations.es.stringUnit.state, 'translated');
  assert.deepEqual(Object.keys(after.strings.profile_tab_miff_members.localizations.es.variations.plural), ['one', 'many', 'other']);
  assert.equal(after.strings.profile_tab_miff_members.localizations.es.variations.plural.one.stringUnit.value, '%lld miembro');
  assert.equal(after.strings.place_profile_title_named.localizations.es.stringUnit.value, 'Lugares de %@ (iPhone)');
  assert.equal(after.strings.ios_permission_hint.localizations.es.stringUnit.value, 'Toca %@');
  assert.deepEqual(after.strings.ios_brand, before.strings.ios_brand);
  assert.equal(applied.text.endsWith('}\n'), true);
});

test('réinjection iOS : idempotente et relisible par le script de parité', () => {
  const byKey = indexExchange(build());
  const models = prepareModels(byKey, ES);
  const first = applyIosCatalog(catalogText(IOS), 'es', models.ios);
  const second = applyIosCatalog(first.text, 'es', models.ios);
  assert.equal(second.text, first.text);
  assert.equal(second.unchanged, second.written);
  const { entries } = parseCatalog(first.text);
  const es = catalogLocalization(entries.get('profile_tab_miff_members'), 'es');
  assert.equal(es.kind, 'plural');
  assert.deepEqual(es.texts.map((t) => t.id), ['one', 'many', 'other']);
  for (const [key, entry] of entries) {
    if (!entry.shouldTranslate) continue;
    const fr = catalogLocalization(entry, 'fr');
    const loc2 = catalogLocalization(entry, 'es');
    assert.ok(loc2, `es présent pour ${key}`);
    const union = (texts) => [...new Set(texts.flatMap((t) => placeholderSignatures(t.value)))].sort();
    assert.deepEqual(union(loc2.texts), union(fr.texts), `valeurs insérées ${key}`);
  }
});

test('réinjection iOS : clé absente du catalogue ignorée avec raison ; ressource non traduisible intouchée ; format non canonique refusé', () => {
  const models = new Map([['absente', { kind: 'string', texts: [{ id: '', value: 'x' }] }], ['ios_brand', { kind: 'string', texts: [{ id: '', value: 'Miff' }] }]]);
  const applied = applyIosCatalog(catalogText(IOS), 'es', models);
  assert.equal(applied.written, 0);
  assert.deepEqual(applied.ignored.map((i) => i.cle), ['absente', 'ios_brand']);
  assert.deepEqual(JSON.parse(applied.text), JSON.parse(catalogText(IOS)));
  assert.throws(() => applyIosCatalog(JSON.stringify(IOS), 'es', models), /format Xcode/);
});

test('réinjection iOS : substitutions recopiées du français (argNum, formatSpecifier)', () => {
  const ios = structuredClone(IOS);
  const sub = (a, b) => ({ stringUnit: { state: 'translated', value: '%#@n@ news' }, substitutions: { n: { argNum: 1, formatSpecifier: 'lld', variations: { plural: { one: unit(a), other: unit(b) } } } } });
  ios.strings.feed_greeting = { extractionState: 'manual', localizations: { en: sub('%arg item', '%arg items'), fr: sub('%arg élément', '%arg éléments') } };
  const text = catalogText(ios);
  const { model } = modelFromValue('substituted', { modele: '%#@n@ noticias', formes: { n: { one: '%arg elemento', other: '%arg elementos' } } });
  const applied = applyIosCatalog(text, 'es', new Map([['feed_greeting', model]]));
  const es = JSON.parse(applied.text).strings.feed_greeting.localizations.es;
  assert.equal(es.stringUnit.value, '%#@n@ noticias');
  assert.equal(es.substitutions.n.argNum, 1);
  assert.equal(es.substitutions.n.formatSpecifier, 'lld');
  assert.equal(es.substitutions.n.variations.plural.other.stringUnit.value, '%arg elementos');
  assert.deepEqual(valueFromModel(model), { modele: '%#@n@ noticias', formes: { n: { one: '%arg elemento', other: '%arg elementos' } } });
});

// ----- Aller-retour complet -----

test('aller-retour : extraction -> traduction -> réinjection -> relecture = texte de la traduction, fr et en inchangés', () => {
  const repo = makeRepo();
  try {
    const logs = [];
    const log = (m) => logs.push(m);
    assert.equal(main(['extraire', '--racine', repo.root], log), 0);
    assert.ok(repo.exists('docs/i18n/echange/source-fr-en.json'));
    const source = JSON.parse(repo.read('docs/i18n/echange/source-fr-en.json'));
    assert.equal(source.textes.length, 9);
    repo.put('docs/i18n/echange/traduction-es.json', JSON.stringify({ langue: 'es', traductions: ES }));
    const frBefore = repo.read('android/app/src/main/res/values-fr/strings.xml');
    const enBefore = repo.read('android/app/src/main/res/values/strings.xml');
    const catalogBefore = JSON.parse(repo.read('ios/Miff/Resources/Localizable.xcstrings'));

    assert.equal(main(['controler', '--langue', 'es', '--racine', repo.root, '--complet'], log), 0);
    assert.equal(main(['injecter', '--langue', 'es', '--racine', repo.root, '--dry-run'], log), 0);
    assert.equal(repo.exists('android/app/src/main/res/values-es/strings.xml'), false, 'dry-run n\'écrit rien');
    assert.equal(main(['injecter', '--langue', 'es', '--racine', repo.root], log), 0);

    assert.equal(repo.read('android/app/src/main/res/values-fr/strings.xml'), frBefore);
    assert.equal(repo.read('android/app/src/main/res/values/strings.xml'), enBefore);
    const catalogAfter = JSON.parse(repo.read('ios/Miff/Resources/Localizable.xcstrings'));
    for (const [key, entry] of Object.entries(catalogBefore.strings)) {
      assert.deepEqual(catalogAfter.strings[key].localizations.fr, entry.localizations.fr);
      assert.deepEqual(catalogAfter.strings[key].localizations.en, entry.localizations.en);
    }

    // Relecture : les valeurs réinjectées, relues par les lecteurs du dépôt, sont celles de la traduction.
    const android = parseAndroidStrings(repo.read('android/app/src/main/res/values-es/strings.xml'));
    assert.equal(decodeAndroidValue(android.get('home_tagline').texts[0].raw), ES.home_tagline);
    assert.equal(decodeAndroidValue(android.get('place_profile_title_named').texts[0].raw), ES.place_profile_title_named.android);
    assert.equal(decodeAndroidValue(android.get('android_battery_tip').texts[0].raw), ES.android_battery_tip);
    const catalog = parseCatalog(repo.read('ios/Miff/Resources/Localizable.xcstrings'));
    assert.equal(catalogLocalization(catalog.entries.get('home_tagline'), 'es').texts[0].value, ES.home_tagline);
    assert.equal(catalogLocalization(catalog.entries.get('place_profile_title_named'), 'es').texts[0].value, 'Lugares de %@ (iPhone)');
    const info = parseCatalog(repo.read('ios/Miff/Resources/InfoPlist.xcstrings'));
    assert.equal(catalogLocalization(info.entries.get('NSCameraUsageDescription'), 'es').texts[0].value, ES.NSCameraUsageDescription);
    assert.equal(catalogLocalization(info.entries.get('NSCameraUsageDescription'), 'fr').texts[0].value, 'Miff utilise la caméra seulement quand tu publies une photo.');

    // Deuxième passe : rien ne change.
    const snapshot = ['android/app/src/main/res/values-es/strings.xml', 'ios/Miff/Resources/Localizable.xcstrings', 'ios/Miff/Resources/InfoPlist.xcstrings'].map((p) => repo.read(p));
    assert.equal(main(['injecter', '--langue', 'es', '--racine', repo.root, '--json'], log), 0);
    assert.deepEqual(['android/app/src/main/res/values-es/strings.xml', 'ios/Miff/Resources/Localizable.xcstrings', 'ios/Miff/Resources/InfoPlist.xcstrings'].map((p) => repo.read(p)), snapshot);
    const json = JSON.parse(logs.at(-1));
    assert.equal(json.fichiersModifies, 0);
  } finally {
    repo.cleanup();
  }
});

test('injecter : une erreur de forme bloque tout (rien n\'est écrit), exit 1', () => {
  const repo = makeRepo();
  try {
    const logs = [];
    repo.put('docs/i18n/echange/traduction-es.json', JSON.stringify({ langue: 'es', traductions: { ...ES, feed_greeting: '%1$s ha dado noticias' } }));
    assert.equal(main(['injecter', '--langue', 'es', '--racine', repo.root], (m) => logs.push(m)), 1);
    assert.equal(repo.exists('android/app/src/main/res/values-es/strings.xml'), false);
    assert.ok(logs.some((l) => l.includes('feed_greeting') && l.includes('placeholders')));
    assert.equal(parseCatalog(repo.read('ios/Miff/Resources/Localizable.xcstrings')).entries.get('home_title').localizations.es, undefined);
  } finally {
    repo.cleanup();
  }
});

test('injecter : plateforme choisie, langue inconnue, fichier de langue différent = usage incorrect (exit 2)', () => {
  const repo = makeRepo();
  try {
    const log = () => {};
    repo.put('docs/i18n/echange/traduction-es.json', JSON.stringify({ langue: 'it', traductions: {} }));
    assert.equal(main(['injecter', '--langue', 'es', '--racine', repo.root], log), 2);
    assert.equal(main(['injecter', '--langue', 'zz', '--racine', repo.root], log), 2);
    assert.equal(main(['injecter', '--langue', 'fr', '--racine', repo.root], log), 2);
    assert.equal(main(['injecter', '--racine', repo.root], log), 2);
    assert.equal(main(['commande-inconnue'], log), 2);
    repo.put('docs/i18n/echange/traduction-es.json', JSON.stringify({ langue: 'es', traductions: ES }));
    assert.equal(main(['injecter', '--langue', 'es', '--plateforme', 'windows', '--racine', repo.root], log), 2);
    assert.equal(main(['injecter', '--langue', 'es', '--plateforme', 'android', '--racine', repo.root], log), 0);
    assert.equal(repo.exists('android/app/src/main/res/values-es/strings.xml'), true);
    assert.equal(parseCatalog(repo.read('ios/Miff/Resources/Localizable.xcstrings')).entries.get('home_title').localizations.es, undefined, 'iOS non touché');
  } finally {
    repo.cleanup();
  }
});

test('extraire --verifier : exit 1 quand le fichier d\'échange est périmé', () => {
  const repo = makeRepo();
  try {
    const log = () => {};
    assert.equal(main(['extraire', '--racine', repo.root, '--verifier'], log), 1, 'rien d\'écrit encore');
    assert.equal(main(['extraire', '--racine', repo.root], log), 0);
    assert.equal(main(['extraire', '--racine', repo.root, '--verifier'], log), 0);
    repo.put('android/app/src/main/res/values-fr/strings.xml', FR_XML.replace('Réessayer', 'Recommencer'));
    assert.equal(main(['extraire', '--racine', repo.root, '--verifier'], log), 1);
  } finally {
    repo.cleanup();
  }
});

// ----- Lots -----

test('lots : taille équilibrée, gelés exclus, contexte, glossaire de la langue', () => {
  const repo = makeRepo();
  try {
    const log = () => {};
    repo.put('docs/i18n/echange/config.json', JSON.stringify(config));
    assert.equal(main(['lots', '--langue', 'de', '--taille', '4', '--racine', repo.root], log), 0);
    const dir = 'docs/i18n/echange/lots/de';
    const files = readdirSync(join(repo.root, dir)).sort();
    assert.deepEqual(files, ['batch-001.json', 'batch-002.json', 'batch-003.json', 'gele-traduction.json', 'plan-traducteurs.json']);
    const lots = ['batch-001.json', 'batch-002.json', 'batch-003.json'].map((f) => JSON.parse(repo.read(`${dir}/${f}`)));
    assert.deepEqual(lots.map((l) => l.nombreTextes), [4, 4, 3]);
    const keys = lots.flatMap((l) => l.textes.map((t) => t.cle));
    assert.ok(!keys.includes('feed_place_noun_home'), 'clé gelée exclue');
    assert.equal(new Set(keys).size, keys.length);
    assert.deepEqual(JSON.parse(repo.read(`${dir}/gele-traduction.json`)).cles, ['feed_place_noun_home']);
    const first = lots[0];
    assert.equal(first.langue, 'de');
    assert.equal(first.glossaire.termesFiges[1].cible, 'Liebsten');
    assert.equal(first.glossaire.adresse.Pronom, 'du');
    assert.deepEqual(first.glossaire.termesProscrits[0].cible, 'dringend');
    assert.equal(first.glossaire.phrasesAssemblees[0].cible, 'Hat {lieu} verlassen');
    assert.match(first.glossaire.guide.langue, /du/);
    assert.ok(first.consignes.length > 5);
    assert.deepEqual(Object.keys(first.formatSortie), ['langue', 'lot', 'traductions', 'questions', 'a_corriger_source']);
    const tagline = lots.flatMap((l) => l.textes).find((t) => t.cle === 'home_tagline');
    assert.deepEqual(tagline.contexte.usages, ['ui/home/HomeScreen.kt', 'Views/HomeView.swift']);
    const members = lots.flatMap((l) => l.textes).find((t) => t.cle === 'profile_tab_miff_members');
    assert.deepEqual(members.formesAttendues, ['one', 'other'], 'de : jamais many');
  } finally {
    repo.cleanup();
  }
});

test('lots : formes de pluriel attendues par langue, étiquettes courtes, relecture avec la traduction', () => {
  const es = registry.languages.find((l) => l.code === 'es');
  const de = registry.languages.find((l) => l.code === 'de');
  assert.deepEqual(expectedPluralForms(es, ['one', 'many', 'other']), ['one', 'many', 'other']);
  assert.deepEqual(expectedPluralForms(es, ['one', 'other']), ['one', 'other']);
  assert.deepEqual(expectedPluralForms(de, ['one', 'many', 'other']), ['one', 'other']);
  const result = build();
  const entries = [...result.communs, ...result.propresAndroid, ...result.propresIos];
  const kit = extractLanguageKit({ glossaire: GLOSSARY, guide: GUIDE }, 'es');
  const traduction = buildLots({ language: es, entries, config, kit, size: 100 });
  const button = traduction.files[0].content.textes.find((t) => t.cle === 'feed_try_again_button');
  assert.equal(button.courte, true);
  assert.equal(button.longueurCible, Math.ceil(10 * 1.25));
  const relecture = buildLots({ language: es, entries, config, kit, mode: 'relecture', translations: ES });
  const relectureTextes = relecture.files.flatMap((f) => f.content.textes);
  assert.ok(relectureTextes.every((t) => 'traduction' in t));
  assert.equal(relectureTextes.find((t) => t.cle === 'home_tagline').traduction, ES.home_tagline);
  const aveugle = buildLots({ language: es, entries, config, kit, mode: 'aveugle' });
  assert.ok(aveugle.files.flatMap((f) => f.content.textes).every((t) => !('traduction' in t)), 'la lecture aveugle ne montre aucune traduction');
  const retro = buildLots({ language: es, entries, config, kit, mode: 'retro', translations: ES });
  const retroKeys = retro.files.flatMap((f) => f.content.textes.map((t) => t.cle));
  assert.deepEqual(retroKeys.sort(), ['NSCameraUsageDescription', 'feed_age_label', 'feed_greeting', 'feed_try_again_button']);
  assert.ok(retro.files[0].content.textes.every((t) => !('fr' in t) && !('en' in t) && 'traduction' in t), 'la rétro-traduction ne montre ni le français ni l’anglais');
  assert.equal(retro.files[0].content.glossaire, undefined);
  assert.deepEqual(balancedChunks([1, 2, 3, 4, 5, 6, 7], 3).map((c) => c.length), [3, 2, 2]);
  assert.deepEqual(balancedChunks([], 3), []);
});

test('lots : textes sensibles en tête, jamais mélangés aux courants ; internes et gelés exclus ; plan des traducteurs', () => {
  const es = registry.languages.find((l) => l.code === 'es');
  const cfg = {
    ...config,
    sensiblesRenforces: { motifs: ['^feed_try_'] },
    internes: { motifs: ['^home_apostrophe$'] },
    ordreSensibles: ['^NS', '^feed_age', '^feed_'],
  };
  const result = build({ config: cfg });
  const entries = [...result.communs, ...result.propresAndroid, ...result.propresIos];
  const kit = extractLanguageKit({ glossaire: GLOSSARY, guide: GUIDE }, 'es');
  const built = buildLots({ language: es, entries, config: cfg, kit, size: 2, agents: 3 });
  const contents = built.files.map((f) => f.content);
  assert.deepEqual(contents.map((c) => c.groupe), ['sensible', 'sensible', 'courant', 'courant', 'courant']);
  for (const c of contents) {
    const flags = new Set(c.textes.map((t) => !!t.sensible));
    assert.equal(flags.size, 1, 'un lot est entièrement sensible ou entièrement courant');
  }
  assert.deepEqual(contents[0].textes.map((t) => t.cle), ['NSCameraUsageDescription', 'feed_age_label'], 'ordre des familles sensibles');
  const all = contents.flatMap((c) => c.textes.map((t) => t.cle));
  assert.ok(!all.includes('home_apostrophe'), 'interne exclu');
  assert.ok(!all.includes('feed_place_noun_home'), 'gelé exclu');
  assert.deepEqual(built.internal, ['home_apostrophe']);
  assert.equal(contents.flatMap((c) => c.textes).find((t) => t.cle === 'feed_try_again_button').sensibleRenforce, true);
  // 5 lots pour 3 traducteurs : blocs contigus de 1 à 2 lots, dans l'ordre, tous les lots attribués une fois.
  const planned = built.plan.agents.map((a) => a.lots);
  assert.deepEqual(planned.flat(), ['batch-001', 'batch-002', 'batch-003', 'batch-004', 'batch-005']);
  assert.ok(planned.every((lots) => lots.length >= 1 && lots.length <= 2));
  assert.deepEqual(built.plan.agents.map((a) => a.agent), ['T1', 'T2', 'T3']);
  assert.deepEqual(built.plan.agents[0].groupes, ['sensible']);
  // Rétro-traduction : seuls les sensibles, même regroupement, pas de plan de traducteurs.
  const retro = buildLots({ language: es, entries, config: cfg, kit, mode: 'retro', translations: ES, size: 2 });
  assert.equal(retro.plan, null);
  assert.ok(retro.files.every((f) => f.content.groupe === 'sensible'));
});

test('lots : consignes (variantesIos, pas de calque, tutoiement, termes proscrits) dans chaque langue', () => {
  const result = build();
  const entries = [...result.communs, ...result.propresAndroid, ...result.propresIos];
  for (const code of ['es', 'it', 'pt', 'de']) {
    const language = registry.languages.find((l) => l.code === code);
    const kit = extractLanguageKit({ glossaire: GLOSSARY, guide: GUIDE }, code);
    const lot = buildLots({ language, entries, config, kit }).files[0].content;
    const text = lot.consignes.join('\n');
    assert.match(text, /variantesIos[\s\S]*TOUJOURS[\s\S]*\{"android": …, "ios": …\}/, code);
    assert.match(text, /même quand les deux variantes ne diffèrent que par peu/, code);
    assert.match(text, /Pas de calque/, code);
    assert.match(text, /a_corriger_source/, code);
    assert.match(text, /Tutoiement : toujours, même si le texte français vouvoie/, code);
    assert.match(text, /Attention : … » en français ne devient pas/, code);
    assert.match(text, /obligatoire » ne devient pas/, code);
    assert.ok('a_corriger_source' in lot.formatSortie);
  }
  const de = registry.languages.find((l) => l.code === 'de');
  const deText = buildLots({ language: de, entries, config, kit: extractLanguageKit({ glossaire: GLOSSARY, guide: GUIDE }, 'de') }).files[0].content.consignes.join('\n');
  assert.match(deText, /Achtung/);
  assert.match(deText, /Beachte/);
});

test('invariables : aucun constat pour un texte déclaré invariable ; internes non attendus', () => {
  const cfg = { ...config, invariables: { cles: ['home_title'], parLangue: { de: ['home_tagline'] } }, internes: { motifs: ['^feed_greeting$'] } };
  const result = build({ config: cfg });
  const byKey = indexExchange(result);
  const same = checkTranslations({ registry, config: cfg, byKey, lang: 'de', translations: { home_title: 'Miff', home_tagline: 'Savoir que tes proches vont bien, sans les déranger.' } });
  assert.deepEqual(same.findings.filter((f) => f.check === 'identical-to-source'), []);
  const other = checkTranslations({ registry, config: cfg, byKey, lang: 'es', translations: { home_tagline: 'Savoir que tes proches vont bien, sans les déranger.' } });
  assert.ok(other.findings.some((f) => f.check === 'identical-to-source' && f.level === 'error'), 'seul de est exempté pour home_tagline');
  assert.ok(!same.missing.includes('feed_greeting'), 'texte interne non attendu');
  assert.ok(same.missing.includes('home_apostrophe'));
});

test('registre : les clés de l\'écran de diagnostic ne sont pas attendues en es, it, pt, de (repli anglais) ; le reste l\'est', () => {
  const keys = ['diag_title', 'ios_diag_detection_empty', 'android_diag_appcheck_off_pending', 'home_tagline', 'settings_diag_note'];
  const frOrEn = (lang, key) => (lang === 'fr' || lang === 'en' ? { kind: 'string', texts: [{ id: '', value: `texte ${key}` }] } : null);
  for (const code of ['es', 'it', 'pt', 'de']) {
    const language = registry.languages.find((l) => l.code === code);
    for (const platform of ['android', 'ios']) {
      const { findings } = checkLanguageTable({ registry, language, platform, table: platform, referenceKeys: new Set(keys), localization: frOrEn, extraKeys: () => [] });
      assert.deepEqual(findings.filter((f) => f.check === 'missing-key').map((f) => f.key), ['home_tagline', 'settings_diag_note'], `${code}/${platform}`);
    }
  }
  // Le français et l'anglais ne sont pas concernés : une clé diag_ manquante y reste une erreur.
  const en = registry.languages.find((l) => l.code === 'en');
  const { findings } = checkLanguageTable({
    registry, language: en, platform: 'android', table: 'android', referenceKeys: new Set(['diag_title']),
    localization: (lang) => (lang === 'fr' ? { kind: 'string', texts: [{ id: '', value: 'x' }] } : null), extraKeys: () => [],
  });
  assert.deepEqual(findings.map((f) => f.check), ['missing-key']);
});

test('fusionner : questions et textes source à corriger sont conservés à part', () => {
  const repo = makeRepo();
  try {
    const log = () => {};
    repo.put('lots/batch-001.json', JSON.stringify({ langue: 'es', lot: 'batch-001', traductions: { home_tagline: 'Uno' }, questions: { home_tagline: 'ambigu ?', '<cle>': 'modèle' }, a_corriger_source: { home_title: 'vouvoie : « déconnectez-vous »' } }));
    assert.equal(main(['fusionner', '--langue', 'es', '--dossier', join(repo.root, 'lots'), '--racine', repo.root], log), 0);
    const notes = JSON.parse(repo.read('docs/i18n/echange/traduction-es.notes.json'));
    assert.deepEqual(notes.questions, { home_tagline: 'ambigu ?' });
    assert.deepEqual(notes.a_corriger_source, { home_title: 'vouvoie : « déconnectez-vous »' });
  } finally {
    repo.cleanup();
  }
});

test('lots --restant : exclut les clés déjà traduites', () => {
  const repo = makeRepo();
  try {
    const log = () => {};
    repo.put('docs/i18n/echange/config.json', JSON.stringify(config));
    repo.put('docs/i18n/echange/traduction-it.json', JSON.stringify({ langue: 'it', traductions: { home_title: 'Miff', home_tagline: 'x' } }));
    assert.equal(main(['lots', '--langue', 'it', '--restant', '--racine', repo.root], log), 0);
    const keys = JSON.parse(repo.read('docs/i18n/echange/lots/it/batch-001.json')).textes.map((t) => t.cle);
    assert.ok(!keys.includes('home_title') && !keys.includes('home_tagline'));
    assert.ok(keys.includes('feed_greeting'));
  } finally {
    repo.cleanup();
  }
});

test('fusionner : assemble les sorties de lots, refuse les conflits', () => {
  const repo = makeRepo();
  try {
    const log = () => {};
    repo.put('lots/batch-001.json', JSON.stringify({ langue: 'es', lot: 'batch-001', traductions: { home_tagline: 'Uno', feed_try_again_button: 'Reintentar' }, questions: { x: '?' } }));
    repo.put('lots/batch-002.json', JSON.stringify({ langue: 'es', lot: 'batch-002', traductions: { home_title: 'Miff' } }));
    assert.equal(main(['fusionner', '--langue', 'es', '--dossier', join(repo.root, 'lots'), '--racine', repo.root], log), 0);
    const merged = JSON.parse(repo.read('docs/i18n/echange/traduction-es.json'));
    assert.deepEqual(Object.keys(merged.traductions), ['feed_try_again_button', 'home_tagline', 'home_title']);
    repo.put('lots/batch-003.json', JSON.stringify({ langue: 'es', lot: 'batch-003', traductions: { home_title: 'Otro' } }));
    assert.equal(main(['fusionner', '--langue', 'es', '--dossier', join(repo.root, 'lots'), '--racine', repo.root], log), 1);
    repo.put('lots/batch-003.json', JSON.stringify({ langue: 'it', lot: 'batch-003', traductions: {} }));
    assert.equal(main(['fusionner', '--langue', 'es', '--dossier', join(repo.root, 'lots'), '--racine', repo.root], log), 2);
  } finally {
    repo.cleanup();
  }
});

// ----- Documents -----

test('glossaire : colonne de la langue, adresse, lignes G, sections', () => {
  const kit = extractLanguageKit({ glossaire: GLOSSARY, guide: GUIDE }, 'pt');
  assert.deepEqual(kit.termesFiges.map((t) => t.cible), ['Miff', 'entes queridos']);
  assert.equal(kit.termesFiges[0].fr, 'Miff');
  assert.equal(kit.termesFiges[1].notes, 'Terme le plus fréquent');
  assert.equal(kit.reglagesSysteme[0].cible, 'Definições');
  assert.equal(kit.pieges[0].cible, 'rare');
  assert.equal(kit.guide.fiche[0].cible, 'tu');
  assert.match(kit.guide.langue, /Portugal/);
  assert.deepEqual(kit.avertissements, []);
  assert.equal(addressRow(GLOSSARY, 'es').Pronom, 'tú');
  const tables = parseTables(GLOSSARY);
  assert.ok(tables.length >= 6);
  const sections = parseSections(GUIDE);
  assert.ok(sections.some((s) => s.title.startsWith('2.5')));
  assert.deepEqual(languageRows('| a | es | de |\n|---|---|---|\n| x | uno | eins |', 'de'), [{ etiquette: 'x', cible: 'eins' }]);
  const empty = extractLanguageKit({ glossaire: '# vide', guide: '# vide' }, 'es');
  assert.ok(empty.avertissements.length > 0);
});

test('arguments : options à valeur, drapeaux, options répétables', () => {
  assert.deepEqual(parseArgs(['controler', '--langue', 'es', '--complet', '--branche', 'a', '--branche', 'b']), { _: ['controler'], langue: 'es', complet: true, branche: ['a', 'b'] });
  assert.throws(() => parseArgs(['x', '--langue']), /attend une valeur/);
});

// ----- Dépôt réel (lecture seule) -----

test('dépôt réel : chaque clé commune a français, anglais, valeurs insérées cohérentes ; pluriels complets', () => {
  const world = loadWorld(repoRoot, { usages: false });
  const { communs, propresAndroid, propresIos, stats } = world.result;
  assert.ok(communs.length > 1000);
  assert.ok(propresAndroid.every((e) => e.cle.startsWith('android_')));
  assert.ok(propresIos.every((e) => e.cle.startsWith('ios_') || e.table === 'InfoPlist.xcstrings'));
  assert.equal(stats.infoPlist, 4);
  for (const e of [...communs, ...propresAndroid, ...propresIos]) {
    assert.ok(e.fr !== null && e.fr !== undefined, `fr ${e.cle}`);
    assert.ok(e.en !== null && e.en !== undefined, `en ${e.cle}`);
    if (e.type === 'plural') {
      assert.ok('one' in e.fr && 'other' in e.fr, `pluriel fr ${e.cle}`);
      assert.ok('one' in e.en && 'other' in e.en, `pluriel en ${e.cle}`);
    }
  }
  // Tout texte commun, tel qu'extrait, passe le contrôle de forme de l'anglais lu comme une « traduction » : mêmes valeurs insérées.
  const translations = Object.fromEntries(communs.filter((e) => !e.divergent && e.en !== null).map((e) => [e.cle, e.en]));
  const report = checkTranslations({ registry, config: world.config, byKey: world.byKey, lang: 'de', translations });
  assert.deepEqual(report.findings.filter((f) => f.check === 'placeholders'), []);
});
