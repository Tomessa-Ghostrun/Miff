#!/usr/bin/env node
// Parité des clés de traduction entre Android et iOS (critère T8, ticket « Initialisation technique »), puis contrôles par langue.
//
// 1. Clés françaises (langue source) : compare
//   - android/app/src/main/res/values-fr/strings.xml (le français ; values/ = anglais de repli)
//   - tout fichier ios/**/Localizable.xcstrings (String Catalog)
//   en ignorant les clés propres à une plateforme : préfixe `ios_` (côté iOS) et `android_` (côté Android).
// 2. Pour CHAQUE langue du registre docs/contracts/i18n-languages.json (ticket « Miff en 6 langues », lot 1) : mêmes clés que le
//   français, valeurs insérées, pluriels, échappements, espaces insécables, marques de genre, vouvoiement, longueurs... sur Android ET
//   iOS (Localizable et InfoPlist). Langue `required` : écart = erreur (bloquant). Langue `planned` : écart = avertissement.
// 3. Cohérence des sources : le registre, les énumérations / filtres de langues / CFBundleLocalizations et les dossiers de ressources
//   listent les mêmes langues.
// Convention de clés : docs/contracts/i18n-keys.md. Registre et marche à suivre : docs/contracts/i18n-languages.md.
//
// Sans registre dans le dossier cible, seul le point 1 est exécuté (comportement historique).
// Aucune dépendance. Codes de sortie : 0 = OK (ou rien à comparer), 1 = écart de parité ou clé invalide,
// 2 = fichier illisible, registre invalide ou usage incorrect.
//
// Usage : node scripts/check-i18n-parity.mjs [--root <dossier du dépôt>] [--json] [--verbose] [--lang <code>]
//                                            [--registry <fichier>] [--summary <fichier.md>]

import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { androidLocalization, catalogLocalization, checkLanguageTable, CHECKS, termRegex } from './lib/i18n-checks.mjs';
import { parseAndroidStrings, parseCatalog } from './lib/i18n-resources.mjs';

// Le français (langue source) est dans values-fr ; values/ contient l'anglais de repli (décision du 2026-10-21).
export const ANDROID_STRINGS = 'android/app/src/main/res/values-fr/strings.xml';
export const IOS_CATALOG_NAME = 'Localizable.xcstrings';
export const IOS_ONLY_PREFIX = 'ios_';
export const ANDROID_ONLY_PREFIX = 'android_';
// Préfixes de fonctionnalité connus (voir docs/contracts/i18n-keys.md) : un préfixe inconnu est signalé
// en avertissement, sans faire échouer le contrôle (la liste s'étend par une PR sur le contrat).
export const KNOWN_PREFIXES = ['auth', 'onboarding', 'profile', 'miff', 'place', 'common', 'home', 'diag', 'tabbar', 'feed', 'settings', 'permission', 'update', 'review', 'notif', 'voyage', 'paywall', 'countdown', 'about', 'ios', 'android'];
export const KEY_FORMAT = /^[a-z][a-z0-9]*(_[a-z0-9]+)+$/;

const SKIPPED_DIRS = new Set(['node_modules', 'Pods', 'build', 'DerivedData', '.build', '.git', '.gradle']);

/** Clés d'un strings.xml Android (hors ressources `translatable="false"`). */
export function extractAndroidKeys(xml) {
  const withoutComments = xml.replace(/<!--[\s\S]*?-->/g, '');
  const keys = [];
  for (const match of withoutComments.matchAll(/<(string|plurals|string-array)\s+([^>]*?)\/?>/g)) {
    const attributes = match[2];
    const name = /\bname\s*=\s*"([^"]+)"/.exec(attributes);
    if (!name) continue;
    if (/\btranslatable\s*=\s*"false"/.test(attributes)) continue;
    keys.push(name[1]);
  }
  return keys;
}

/** Clés d'un String Catalog iOS (hors entrées `shouldTranslate: false`). */
export function extractIosKeys(catalogText) {
  const catalog = JSON.parse(catalogText);
  if (catalog === null || typeof catalog !== 'object' || typeof catalog.strings !== 'object') {
    throw new Error('format de String Catalog inattendu : objet "strings" absent');
  }
  return Object.entries(catalog.strings)
    .filter(([, entry]) => entry?.shouldTranslate !== false)
    .map(([key]) => key);
}

/**
 * Clés en double directement sous "strings" dans un String Catalog. `JSON.parse` garde silencieusement la dernière occurrence d'une clé
 * répétée : le doublon ne se voit donc qu'en lisant le texte brut. Un petit lecteur suit les objets ouverts et compte les clés de l'objet "strings".
 */
export function findDuplicateIosKeys(catalogText) {
  const duplicates = [];
  const stack = [];
  let pendingName = null;
  let i = 0;
  const text = catalogText;
  while (i < text.length) {
    const c = text[i];
    if (c === '"') {
      let j = i + 1;
      let value = '';
      while (j < text.length && text[j] !== '"') {
        if (text[j] === '\\') {
          value += text[j] + text[j + 1];
          j += 2;
        } else {
          value += text[j];
          j += 1;
        }
      }
      let k = j + 1;
      while (k < text.length && /\s/.test(text[k])) k += 1;
      if (text[k] === ':') {
        const top = stack[stack.length - 1];
        if (top?.keys) {
          if (top.name === 'strings' && stack.length === 2) {
            if (top.keys.has(value)) duplicates.push(value);
            top.keys.add(value);
          }
          pendingName = value;
        }
      }
      i = j + 1;
      continue;
    }
    if (c === '{') {
      stack.push({ keys: new Set(), name: pendingName });
      pendingName = null;
    } else if (c === '[') {
      stack.push({ keys: null, name: pendingName });
      pendingName = null;
    } else if (c === '}' || c === ']') {
      stack.pop();
    }
    i += 1;
  }
  return duplicates;
}

/** Compare deux listes de clés ; les préfixes propres à une plateforme sont ignorés. */
export function compareKeys(androidKeys, iosKeys) {
  const shared = (keys, ownPrefix) => new Set(keys.filter((k) => !k.startsWith(ownPrefix)));
  const android = shared(androidKeys, ANDROID_ONLY_PREFIX);
  const ios = shared(iosKeys, IOS_ONLY_PREFIX);
  // Une clé ios_* côté Android (ou android_* côté iOS) n'a pas de sens : on la traite comme une clé commune
  // pour qu'elle soit signalée comme manquante de l'autre côté.
  return {
    onlyAndroid: [...android].filter((k) => !ios.has(k)).sort(),
    onlyIos: [...ios].filter((k) => !android.has(k)).sort(),
  };
}

/** Problèmes de format (snake_case) et avertissements de préfixe. */
export function checkKeyNames(keys, platform) {
  const errors = [];
  const warnings = [];
  const seen = new Set();
  for (const key of keys) {
    if (seen.has(key)) errors.push(`${platform} : clé en double « ${key} »`);
    seen.add(key);
    if (!KEY_FORMAT.test(key)) {
      errors.push(`${platform} : « ${key} » n'est pas en snake_case avec un préfixe (ex. auth_titre)`);
      continue;
    }
    const prefix = key.slice(0, key.indexOf('_'));
    if (!KNOWN_PREFIXES.includes(prefix)) {
      warnings.push(`${platform} : préfixe « ${prefix}_ » absent de docs/contracts/i18n-keys.md (clé « ${key} »)`);
    }
    if (platform === 'Android' && key.startsWith(IOS_ONLY_PREFIX)) {
      errors.push(`Android : la clé « ${key} » utilise le préfixe réservé à iOS`);
    }
    if (platform === 'iOS' && key.startsWith(ANDROID_ONLY_PREFIX)) {
      errors.push(`iOS : la clé « ${key} » utilise le préfixe réservé à Android`);
    }
  }
  return { errors, warnings };
}

function findCatalogs(dir, catalogName = IOS_CATALOG_NAME, found = []) {
  if (!existsSync(dir)) return found;
  for (const name of readdirSync(dir)) {
    if (SKIPPED_DIRS.has(name)) continue;
    const full = join(dir, name);
    const info = statSync(full);
    if (info.isDirectory()) findCatalogs(full, catalogName, found);
    else if (name === catalogName) found.push(full);
  }
  return found.sort();
}


// ---------------------------------------------------------------------------------------------------------------------
// Registre des langues et contrôles par langue (ticket « Miff en 6 langues », lot 1)
// ---------------------------------------------------------------------------------------------------------------------

export const REGISTRY_PATH = 'docs/contracts/i18n-languages.json';
export const ANDROID_RES_DIR = 'android/app/src/main/res';
export const IOS_INFOPLIST_NAME = 'InfoPlist.xcstrings';
const STATUSES = ['planned', 'required'];

/** Registre des langues du dépôt, ou null s'il n'existe pas (mode historique : français seul, Android / iOS). */
export function loadRegistry(root, registryPath) {
  const file = registryPath ? resolve(registryPath) : join(root, REGISTRY_PATH);
  if (!existsSync(file)) {
    // Un registre demandé explicitement et introuvable est une erreur (jamais un « tout va bien » silencieux).
    if (registryPath) throw new Error(`registre demandé introuvable : ${registryPath}`);
    return null;
  }
  let registry;
  try {
    registry = JSON.parse(readFileSync(file, 'utf8'));
  } catch (error) {
    throw new Error(`${REGISTRY_PATH} illisible : ${error.message}`);
  }
  const problems = validateRegistry(registry);
  if (problems.length > 0) throw new Error(`${REGISTRY_PATH} invalide :\n  - ${problems.join('\n  - ')}`);
  return registry;
}

/** Problèmes de structure du registre (liste vide = valide). */
export function validateRegistry(registry) {
  const problems = [];
  if (registry === null || typeof registry !== 'object' || !Array.isArray(registry.languages)) return ['« languages » doit être une liste'];
  const codes = new Set();
  const dirs = new Set();
  for (const language of registry.languages) {
    const code = language?.code;
    if (typeof code !== 'string' || !/^[a-z]{2,3}(-[A-Za-z]{2,4})?$/.test(code)) {
      problems.push(`code de langue invalide : ${JSON.stringify(code)}`);
      continue;
    }
    if (codes.has(code)) problems.push(`langue « ${code} » en double`);
    codes.add(code);
    if (typeof language.nativeName !== 'string' || language.nativeName.trim() === '') problems.push(`${code} : nativeName manquant`);
    const plurals = language.plurals;
    if (!plurals || !Array.isArray(plurals.required) || !Array.isArray(plurals.allowed)) problems.push(`${code} : plurals.required et plurals.allowed attendus`);
    else if (!plurals.required.every((c) => plurals.allowed.includes(c))) problems.push(`${code} : plurals.required doit être inclus dans plurals.allowed`);
    const platforms = language.platforms ?? {};
    if (Object.keys(platforms).length === 0) problems.push(`${code} : au moins une plateforme (android, ios) attendue`);
    for (const [platform, config] of Object.entries(platforms)) {
      if (!['android', 'ios'].includes(platform)) problems.push(`${code} : plateforme inconnue « ${platform} »`);
      if (!STATUSES.includes(config?.status)) problems.push(`${code}/${platform} : status doit être ${STATUSES.join(' ou ')}`);
      if (platform === 'android') {
        if (typeof config?.resDir !== 'string') problems.push(`${code}/android : resDir manquant`);
        else if (dirs.has(config.resDir)) problems.push(`dossier Android « ${config.resDir} » utilisé deux fois`);
        else dirs.add(config.resDir);
      }
      if (platform === 'ios' && typeof config?.code !== 'string') problems.push(`${code}/ios : code manquant`);
    }
  }
  const source = registry.languages.find((l) => l?.code === registry.sourceLanguage);
  if (!source) problems.push(`sourceLanguage « ${registry.sourceLanguage} » absente de la liste`);
  else {
    for (const platform of ['android', 'ios']) {
      if (source.platforms?.[platform]?.status !== 'required') problems.push(`la langue source doit être required sur ${platform}`);
    }
  }
  if (!codes.has(registry.fallbackLanguage)) problems.push(`fallbackLanguage « ${registry.fallbackLanguage} » absente de la liste`);
  for (const patterns of ['shortLabelKeyPatterns', 'nbspRequiredKeyPatterns']) {
    for (const pattern of registry[patterns] ?? []) {
      try {
        new RegExp(pattern, 'u');
      } catch {
        problems.push(`${patterns} : expression invalide « ${pattern} »`);
      }
    }
  }
  for (const language of registry.languages) {
    for (const term of [...(language?.style?.formalForms ?? []), ...(language?.style?.forbiddenTerms ?? [])]) {
      try {
        termRegex(term);
      } catch {
        problems.push(`${language.code} : expression invalide ${JSON.stringify(term)}`);
      }
    }
  }
  return problems;
}

const statusOf = (language, platform) => language.platforms?.[platform]?.status ?? null;

/** Codes des langues du registre ayant ce statut sur cette plateforme. */
export function languagesByStatus(registry, platform, status) {
  return registry.languages.filter((l) => statusOf(l, platform) === status).map((l) => l.code);
}

// ----- Cohérence des sources (critère 6) : le registre et les endroits où les langues sont déclarées doivent concorder -----

const ANDROID_LANGUAGE_DIR = /^values(?:-([a-z]{2,3}(?:-r[A-Z]{2})?))?$/;
const ANDROID_NON_LANGUAGE_QUALIFIERS = new Set(['car', 'tv']);

/**
 * Code de langue d'un tag Android ou iOS, région comprise : `pt-BR`, `pt-rBR` et `pt_BR` donnent `pt-BR`, jamais `pt`. Le portugais du Portugal est
 * le code `pt` du registre (défaut du PM) ; un `pt-BR` trouvé dans une source est donc « inconnu du registre » tant que le registre ne le liste pas.
 */
export function normalizeLanguageTag(tag) {
  const [language, region] = tag.replace(/_/g, '-').replace(/-r(?=[A-Z])/, '-').split('-');
  return region ? `${language.toLowerCase()}-${region.toUpperCase()}` : language.toLowerCase();
}

function extractSourceLanguages(source, root, registry) {
  const file = join(root, source.file);
  if (!existsSync(file)) return { missing: true, codes: new Set() };
  const byIos = new Map(registry.languages.filter((l) => l.platforms?.ios).map((l) => [l.platforms.ios.code, l.code]));
  switch (source.kind) {
    case 'kotlin-enum-tags': {
      const text = readFileSync(file, 'utf8');
      const codes = new Set();
      for (const m of text.matchAll(/^\s*[A-Z][A-Z0-9_]*\(\s*"([A-Za-z-]+)"\s*\)/gm)) codes.add(normalizeLanguageTag(m[1]));
      return { codes };
    }
    case 'swift-enum-raw-values': {
      // `case french = "fr"` dans l'énumération de Réglages > Langue ; `case system` n'a pas de valeur brute : ignoré.
      const text = readFileSync(file, 'utf8');
      const start = text.search(/\benum\s+AppLanguageChoice\b[^{]*\{/);
      const body = start >= 0 ? text.slice(start).split(/\n\}/)[0] : '';
      const codes = new Set();
      for (const m of body.matchAll(/^\s*case\s+\w+\s*=\s*"([A-Za-z_-]+)"/gm)) codes.add(byIos.get(normalizeLanguageTag(m[1])) ?? `? ${m[1]}`);
      return { codes };
    }
    case 'gradle-locale-filters': {
      const text = readFileSync(file, 'utf8');
      const codes = new Set();
      for (const m of text.matchAll(/localeFilters\s*\.\s*addAll\s*\(([^)]*)\)/g)) {
        for (const tag of m[1].matchAll(/"([A-Za-z-]+)"/g)) codes.add(normalizeLanguageTag(tag[1]));
      }
      return { codes };
    }
    case 'android-res-dirs': {
      const byDir = new Map(registry.languages.filter((l) => l.platforms?.android).map((l) => [l.platforms.android.resDir, l.code]));
      const codes = new Set();
      for (const name of readdirSync(file)) {
        if (!statSync(join(file, name)).isDirectory()) continue;
        const m = ANDROID_LANGUAGE_DIR.exec(name);
        if (!m) continue;
        if (m[1] && ANDROID_NON_LANGUAGE_QUALIFIERS.has(m[1])) continue;
        // Un dossier de langue ne compte que s'il contient un strings.xml (values/ contient aussi couleurs et thèmes).
        if (!existsSync(join(file, name, 'strings.xml'))) continue;
        codes.add(byDir.get(name) ?? `? ${name}`);
      }
      return { codes };
    }
    case 'project-yml-localizations': {
      const lines = readFileSync(file, 'utf8').split(/\r?\n/);
      const start = lines.findIndex((l) => /^\s*CFBundleLocalizations:\s*$/.test(l));
      const codes = new Set();
      if (start >= 0) {
        for (let i = start + 1; i < lines.length; i += 1) {
          const m = /^\s*-\s*"?([A-Za-z-]+)"?\s*$/.exec(lines[i]);
          if (!m) break;
          codes.add(byIos.get(m[1]) ?? `? ${m[1]}`);
        }
      }
      return { codes };
    }
    case 'ios-catalog-languages': {
      const { entries } = parseCatalog(readFileSync(file, 'utf8'));
      const codes = new Set();
      for (const entry of entries.values()) for (const lang of Object.keys(entry.localizations)) codes.add(byIos.get(lang) ?? `? ${lang}`);
      return { codes };
    }
    default:
      throw new Error(`source de cohérence inconnue : ${source.kind}`);
  }
}

/**
 * Les endroits qui déclarent les langues (énumération, filtres de langues de la release, CFBundleLocalizations) listent EXACTEMENT les
 * langues `required` de leur plateforme ; les dossiers et catalogues de ressources contiennent au moins les `required` et rien d'inconnu.
 * Retourne des constats {level, source, message}.
 */
export function checkSourcesConsistency(registry, root) {
  const findings = [];
  for (const source of registry.consistency?.sources ?? []) {
    const required = new Set(languagesByStatus(registry, source.platform, 'required'));
    const planned = new Set(languagesByStatus(registry, source.platform, 'planned'));
    let result;
    try {
      result = extractSourceLanguages(source, root, registry);
    } catch (error) {
      findings.push({ level: 'error', source: source.id, message: `${source.file} illisible : ${error.message}` });
      continue;
    }
    if (result.missing) {
      findings.push({ level: 'error', source: source.id, message: `source déclarée dans le registre mais absente : ${source.file}` });
      continue;
    }
    const found = result.codes;
    if (found.size === 0 && source.kind !== 'android-res-dirs') {
      findings.push({ level: 'error', source: source.id, message: `aucune langue trouvée dans ${source.file} (le format a-t-il changé ? adapter le registre)` });
      continue;
    }
    for (const code of required) {
      if (!found.has(code)) findings.push({ level: 'error', source: source.id, message: `langue « ${code} » (required sur ${source.platform}) absente de ${source.file}` });
    }
    for (const code of found) {
      if (code.startsWith('? ')) findings.push({ level: 'error', source: source.id, message: `langue inconnue du registre « ${code.slice(2)} » dans ${source.file}` });
      else if (planned.has(code) && source.role === 'declaration') {
        findings.push({ level: 'warning', source: source.id, message: `langue « ${code} » (planned sur ${source.platform}) déjà déclarée dans ${source.file} : la passer en required avant de la proposer` });
      } else if (!required.has(code) && !planned.has(code)) {
        findings.push({ level: 'error', source: source.id, message: `langue « ${code} » de ${source.file} absente du registre pour ${source.platform}` });
      }
    }
  }
  return findings;
}

// ----- Chargement des ressources et contrôle de toutes les langues -----

function loadAndroid(root, registry) {
  const maps = new Map();
  let any = false;
  for (const language of registry.languages) {
    const config = language.platforms?.android;
    if (!config) continue;
    const file = join(root, ANDROID_RES_DIR, config.resDir, 'strings.xml');
    if (existsSync(file)) {
      maps.set(language.code, parseAndroidStrings(readFileSync(file, 'utf8')));
      any = true;
    } else {
      maps.set(language.code, null);
    }
  }
  return any ? maps : null;
}

function loadIosTable(root, catalogName) {
  const files = findCatalogs(join(root, 'ios'), catalogName);
  if (files.length === 0) return null;
  const entries = new Map();
  for (const file of files) {
    let parsed;
    try {
      parsed = parseCatalog(readFileSync(file, 'utf8'));
    } catch (error) {
      throw new Error(`${relative(root, file).split(sep).join('/')} : ${error.message}`);
    }
    for (const [key, entry] of parsed.entries) if (!entries.has(key)) entries.set(key, entry);
  }
  return entries;
}

/** Contrôles de toutes les langues du registre. @returns {{findings: object[], languages: object[]}} */
export function checkAllLanguages({ root, registry, only }) {
  const findings = [];
  const languages = [];
  const android = loadAndroid(root, registry);
  const iosMain = loadIosTable(root, IOS_CATALOG_NAME);
  const iosPlist = loadIosTable(root, IOS_INFOPLIST_NAME);
  const source = registry.sourceLanguage;
  const tables = [];
  if (android?.get(source)) {
    const referenceKeys = new Set([...android.get(source)].filter(([, e]) => e.translatable).map(([k]) => k));
    tables.push({
      platform: 'android',
      table: 'android',
      referenceKeys,
      localization: (lang, key) => androidLocalization(android.get(lang), key),
      extraKeys: (lang) => [...(android.get(lang) ?? [])].filter(([k, e]) => e.translatable && !referenceKeys.has(k)).map(([k]) => k).sort(),
    });
  }
  const iosTable = (entries, id) => {
    const referenceKeys = new Set([...entries].filter(([, e]) => e.shouldTranslate).map(([k]) => k));
    return {
      platform: 'ios',
      table: id,
      referenceKeys,
      localization: (lang, key) => {
        const entry = entries.get(key);
        if (!entry || !entry.shouldTranslate) return null;
        const code = registry.languages.find((l) => l.code === lang)?.platforms?.ios?.code ?? lang;
        return catalogLocalization(entry, code);
      },
      extraKeys: () => [],
    };
  };
  if (iosMain) tables.push(iosTable(iosMain, 'ios'));
  if (iosPlist) tables.push(iosTable(iosPlist, 'ios-infoplist'));

  for (const language of registry.languages) {
    if (only && language.code !== only) continue;
    for (const t of tables) {
      const status = statusOf(language, t.platform);
      if (!status) continue;
      const result = checkLanguageTable({
        registry,
        language,
        platform: t.platform,
        table: t.table,
        referenceKeys: t.referenceKeys,
        localization: t.localization,
        extraKeys: () => t.extraKeys(language.code),
      });
      for (const f of result.findings) findings.push({ ...f, status, effectiveLevel: status === 'required' ? f.level : 'warning' });
      languages.push({ code: language.code, nativeName: language.nativeName, platform: t.platform, table: t.table, status, ...result.stats });
    }
  }
  return { findings, languages };
}

// ----- Présentation -----

const PLATFORM_LABEL = { android: 'Android', ios: 'iOS' };
const tableLabel = (f) => (f.table === 'ios-infoplist' ? 'iOS InfoPlist' : PLATFORM_LABEL[f.platform]);

function groupFindings(findings) {
  const groups = new Map();
  for (const f of findings) {
    const id = `${f.effectiveLevel}|${f.lang}|${f.table}|${f.check}`;
    if (!groups.has(id)) groups.set(id, { level: f.effectiveLevel, lang: f.lang, platform: f.platform, table: f.table, check: f.check, status: f.status, items: [] });
    groups.get(id).items.push({ key: f.key, message: f.message });
  }
  return [...groups.values()];
}

function renderLines(report, { verbose }) {
  const lines = [];
  for (const entry of report.languages) {
    const own = report.findings.filter((f) => f.lang === entry.code && f.table === entry.table);
    const errors = own.filter((f) => f.effectiveLevel === 'error').length;
    const warnings = own.filter((f) => f.effectiveLevel === 'warning').length;
    const ratio = entry.lengthRatioVsFallback != null && entry.code !== report.fallbackLanguage ? `, longueur ≈ ×${entry.lengthRatioVsFallback} vs ${report.fallbackLanguage}` : '';
    lines.push(`[${entry.code} · ${tableLabel(entry)} · ${entry.status}] ${entry.present}/${entry.expected} clés${entry.extra ? `, ${entry.extra} en trop` : ''}, ${errors} erreur(s), ${warnings} avertissement(s)${ratio}`);
  }
  const groups = groupFindings(report.findings);
  for (const g of groups.sort((a, b) => (a.level === b.level ? 0 : a.level === 'error' ? -1 : 1))) {
    const prefix = g.level === 'error' ? 'ERREUR' : 'Avertissement';
    const where = `[${g.lang} · ${tableLabel(g)}${g.status === 'planned' ? ' · planned' : ''}]`;
    const limit = verbose ? Infinity : g.level === 'error' ? 30 : 3;
    if (g.level === 'warning' && !verbose && g.items.length > 3) {
      const examples = g.items.slice(0, limit).map((i) => i.key).join(', ');
      lines.push(`${prefix} ${where} ${CHECKS[g.check] ?? g.check} : ${g.items.length} texte(s) (ex. ${examples}, ...)`);
      continue;
    }
    for (const item of g.items.slice(0, limit)) lines.push(`${prefix} ${where} ${item.key} : ${item.message}`);
    if (g.items.length > limit) lines.push(`${prefix} ${where} ${CHECKS[g.check] ?? g.check} : ... et ${g.items.length - limit} autre(s) (--verbose pour tout voir)`);
  }
  for (const f of report.consistency) lines.push(`${f.level === 'error' ? 'ERREUR' : 'Avertissement'} Cohérence des sources (${f.source}) : ${f.message}`);
  return lines;
}

/** Tableau Markdown pour le résumé du job CI. */
export function markdownSummary(report) {
  const rows = ['| Langue | Plateforme | Statut | Clés | Manquantes | Erreurs | Avertissements |', '|---|---|---|---|---|---|---|'];
  for (const entry of report.languages) {
    const own = report.findings.filter((f) => f.lang === entry.code && f.table === entry.table);
    rows.push(`| ${entry.code} (${entry.nativeName}) | ${tableLabel(entry)} | ${entry.status} | ${entry.present}/${entry.expected} | ${entry.missing} | ${own.filter((f) => f.effectiveLevel === 'error').length} | ${own.filter((f) => f.effectiveLevel === 'warning').length} |`);
  }
  return `### Parité des traductions\n\n${rows.join('\n')}\n`;
}

/**
 * @param {{root: string, registryPath?: string, lang?: string, verbose?: boolean}} options
 * @returns {{code: 0 | 1 | 2, lines: string[], report: object | null}}
 */
export function run({ root, registryPath, lang, verbose = false }) {
  const lines = [];
  let registry;
  try {
    registry = loadRegistry(root, registryPath);
  } catch (error) {
    return { code: 2, lines: [`Erreur de registre : ${error.message}`], report: null };
  }
  if (lang && !registry) {
    return { code: 2, lines: [`--lang ${lang} demande un registre des langues (${REGISTRY_PATH}), introuvable dans ce dossier.`], report: null };
  }
  if (lang && !registry.languages.some((l) => l.code === lang)) {
    return { code: 2, lines: [`Langue inconnue du registre : ${lang}`], report: null };
  }

  const androidFile = join(root, ANDROID_STRINGS);
  const iosFiles = findCatalogs(join(root, 'ios'));
  const hasAndroid = existsSync(androidFile);
  const hasIos = iosFiles.length > 0;

  if (!hasAndroid && !hasIos) {
    lines.push(
      `Parité des traductions ignorée : aucun fichier de traduction pour l'instant (${ANDROID_STRINGS} et ios/**/${IOS_CATALOG_NAME} absents).`,
    );
    return { code: 0, lines, report: null };
  }

  let androidKeys = [];
  let iosKeys = [];
  const duplicateIosKeys = [];
  try {
    if (hasAndroid) androidKeys = extractAndroidKeys(readFileSync(androidFile, 'utf8'));
    for (const file of iosFiles) {
      try {
        const catalogText = readFileSync(file, 'utf8');
        iosKeys.push(...extractIosKeys(catalogText));
        for (const key of new Set(findDuplicateIosKeys(catalogText))) {
          duplicateIosKeys.push(`${relative(root, file).split(sep).join('/')} : clé en double « ${key} » (une seule occurrence par clé)`);
        }
      } catch (error) {
        throw new Error(`${relative(root, file).split(sep).join('/')} : ${error.message}`);
      }
    }
  } catch (error) {
    lines.push(`Erreur de lecture : ${error.message}`);
    return { code: 2, lines, report: null };
  }
  iosKeys = [...new Set(iosKeys)];

  let failed = false;
  for (const message of duplicateIosKeys) {
    lines.push(`ERREUR iOS : ${message}`);
    failed = true;
  }
  const names = [
    ...(hasAndroid ? [checkKeyNames(androidKeys, 'Android')] : []),
    ...(hasIos ? [checkKeyNames(iosKeys, 'iOS')] : []),
  ];
  for (const { errors, warnings } of names) {
    for (const message of errors) {
      lines.push(`ERREUR ${message}`);
      failed = true;
    }
    for (const message of warnings) lines.push(`Avertissement ${message}`);
  }

  let sharedMessage = null;
  if (!hasAndroid || !hasIos) {
    const missing = hasAndroid ? `ios/**/${IOS_CATALOG_NAME}` : ANDROID_STRINGS;
    lines.push(`Parité non vérifiable pour l'instant : ${missing} n'existe pas encore (noms de clés contrôlés quand même).`);
  } else {
    const { onlyAndroid, onlyIos } = compareKeys(androidKeys, iosKeys);
    for (const key of onlyAndroid) lines.push(`ERREUR Clé présente sur Android seulement : ${key}`);
    for (const key of onlyIos) lines.push(`ERREUR Clé présente sur iOS seulement : ${key}`);
    if (onlyAndroid.length > 0 || onlyIos.length > 0) failed = true;
    if (!failed) {
      const count = [...new Set(androidKeys.filter((k) => !k.startsWith(ANDROID_ONLY_PREFIX)))].length;
      sharedMessage = `Parité des traductions OK : ${count} clé(s) commune(s) entre Android et iOS.`;
    }
  }

  let report = null;
  if (registry) {
    let checked;
    try {
      checked = checkAllLanguages({ root, registry, only: lang });
    } catch (error) {
      lines.push(`Erreur de lecture : ${error.message}`);
      return { code: 2, lines, report: null };
    }
    const consistency = checkSourcesConsistency(registry, root);
    const errors = checked.findings.filter((f) => f.effectiveLevel === 'error').length + consistency.filter((f) => f.level === 'error').length;
    const warnings = checked.findings.filter((f) => f.effectiveLevel === 'warning').length + consistency.filter((f) => f.level === 'warning').length;
    report = {
      registry: { file: REGISTRY_PATH, sourceLanguage: registry.sourceLanguage, fallbackLanguage: registry.fallbackLanguage },
      fallbackLanguage: registry.fallbackLanguage,
      languages: checked.languages,
      findings: checked.findings,
      consistency,
      summary: { errors, warnings },
    };
    lines.push(...renderLines(report, { verbose }));
    if (errors > 0) failed = true;
  }

  if (!failed) {
    if (sharedMessage) lines.push(sharedMessage);
    if (report) lines.push(`Contrôles par langue OK (${report.summary.warnings} avertissement(s) non bloquant(s)).`);
  } else {
    lines.push('Parité des traductions : ÉCHEC (voir docs/contracts/i18n-keys.md et docs/contracts/i18n-languages.md).');
  }
  const code = failed ? 1 : 0;
  if (report) report.exitCode = code;
  return { code, lines, report };
}

const USAGE = 'Usage : node scripts/check-i18n-parity.mjs [--root <dossier>] [--json] [--verbose] [--lang <code>] [--registry <fichier>] [--summary <fichier.md>]';

function main(argv) {
  let root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const options = { json: false, verbose: false, lang: undefined, registryPath: undefined, summary: undefined };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--root' && argv[i + 1]) {
      root = resolve(argv[i + 1]);
      i += 1;
    } else if (arg === '--json') options.json = true;
    else if (arg === '--verbose') options.verbose = true;
    else if (arg === '--lang' && argv[i + 1]) {
      options.lang = argv[i + 1];
      i += 1;
    } else if (arg === '--registry' && argv[i + 1]) {
      options.registryPath = argv[i + 1];
      i += 1;
    } else if (arg === '--summary' && argv[i + 1]) {
      options.summary = argv[i + 1];
      i += 1;
    } else {
      console.error(`Argument inconnu : ${arg}\n${USAGE}`);
      return 2;
    }
  }
  const { code, lines, report } = run({ root, registryPath: options.registryPath, lang: options.lang, verbose: options.verbose });
  if (options.summary && report) {
    try {
      writeFileSync(resolve(options.summary), markdownSummary(report), 'utf8');
    } catch (error) {
      console.error(`Impossible d'écrire le résumé ${options.summary} : ${error.code === 'ENOENT' ? 'le dossier n\'existe pas' : error.message}`);
      return 2;
    }
  }
  if (options.json) {
    console.log(JSON.stringify({ exitCode: code, messages: lines, report }, null, 2));
    return code;
  }
  for (const line of lines) (code === 0 ? console.log : console.error)(line);
  return code;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exitCode = main(process.argv.slice(2));
}
