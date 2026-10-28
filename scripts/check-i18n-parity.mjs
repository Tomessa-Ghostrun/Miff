#!/usr/bin/env node
// Parité des clés de traduction entre Android et iOS (critère T8, ticket « Initialisation technique »).
//
// Compare les clés françaises (langue source) de :
//   - android/app/src/main/res/values-fr/strings.xml (le français ; values/ = anglais de repli, non lu ici)
//   - tout fichier ios/**/Localizable.xcstrings (String Catalog)
// en ignorant les clés propres à une plateforme : préfixe `ios_` (côté iOS) et `android_` (côté Android).
// Convention de clés : docs/contracts/i18n-keys.md.
//
// Aucune dépendance. Codes de sortie : 0 = OK (ou rien à comparer), 1 = écart de parité ou clé invalide,
// 2 = fichier illisible ou usage incorrect.
//
// Usage : node scripts/check-i18n-parity.mjs [--root <dossier du dépôt>]

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Le français (langue source) est dans values-fr ; values/ contient l'anglais de repli (décision du 2026-10-21).
export const ANDROID_STRINGS = 'android/app/src/main/res/values-fr/strings.xml';
export const IOS_CATALOG_NAME = 'Localizable.xcstrings';
export const IOS_ONLY_PREFIX = 'ios_';
export const ANDROID_ONLY_PREFIX = 'android_';
// Préfixes de fonctionnalité connus (voir docs/contracts/i18n-keys.md) : un préfixe inconnu est signalé
// en avertissement, sans faire échouer le contrôle (la liste s'étend par une PR sur le contrat).
export const KNOWN_PREFIXES = ['auth', 'onboarding', 'miff', 'place', 'common', 'home', 'diag', 'ios', 'android'];
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

function findCatalogs(dir, found = []) {
  if (!existsSync(dir)) return found;
  for (const name of readdirSync(dir)) {
    if (SKIPPED_DIRS.has(name)) continue;
    const full = join(dir, name);
    const info = statSync(full);
    if (info.isDirectory()) findCatalogs(full, found);
    else if (name === IOS_CATALOG_NAME) found.push(full);
  }
  return found.sort();
}

/**
 * @param {{root: string}} options
 * @returns {{code: 0 | 1 | 2, lines: string[]}}
 */
export function run({ root }) {
  const lines = [];
  const androidFile = join(root, ANDROID_STRINGS);
  const iosFiles = findCatalogs(join(root, 'ios'));
  const hasAndroid = existsSync(androidFile);
  const hasIos = iosFiles.length > 0;

  if (!hasAndroid && !hasIos) {
    lines.push(
      `Parité des traductions ignorée : aucun fichier de traduction pour l'instant (${ANDROID_STRINGS} et ios/**/${IOS_CATALOG_NAME} absents).`,
    );
    return { code: 0, lines };
  }

  let androidKeys = [];
  let iosKeys = [];
  try {
    if (hasAndroid) androidKeys = extractAndroidKeys(readFileSync(androidFile, 'utf8'));
    for (const file of iosFiles) {
      try {
        iosKeys.push(...extractIosKeys(readFileSync(file, 'utf8')));
      } catch (error) {
        throw new Error(`${relative(root, file).split(sep).join('/')} : ${error.message}`);
      }
    }
  } catch (error) {
    lines.push(`Erreur de lecture : ${error.message}`);
    return { code: 2, lines };
  }
  iosKeys = [...new Set(iosKeys)];

  let failed = false;
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

  if (!hasAndroid || !hasIos) {
    const missing = hasAndroid ? `ios/**/${IOS_CATALOG_NAME}` : ANDROID_STRINGS;
    lines.push(`Parité non vérifiable pour l'instant : ${missing} n'existe pas encore (noms de clés contrôlés quand même).`);
    return { code: failed ? 1 : 0, lines };
  }

  const { onlyAndroid, onlyIos } = compareKeys(androidKeys, iosKeys);
  for (const key of onlyAndroid) lines.push(`ERREUR Clé présente sur Android seulement : ${key}`);
  for (const key of onlyIos) lines.push(`ERREUR Clé présente sur iOS seulement : ${key}`);
  if (onlyAndroid.length > 0 || onlyIos.length > 0) failed = true;

  if (!failed) {
    const count = [...new Set(androidKeys.filter((k) => !k.startsWith(ANDROID_ONLY_PREFIX)))].length;
    lines.push(`Parité des traductions OK : ${count} clé(s) commune(s) entre Android et iOS.`);
  } else {
    lines.push('Parité des traductions : ÉCHEC (voir docs/contracts/i18n-keys.md).');
  }
  return { code: failed ? 1 : 0, lines };
}

function main(argv) {
  let root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--root' && argv[i + 1]) {
      root = resolve(argv[i + 1]);
      i += 1;
    } else {
      console.error(`Argument inconnu : ${argv[i]}\nUsage : node scripts/check-i18n-parity.mjs [--root <dossier>]`);
      return 2;
    }
  }
  const { code, lines } = run({ root });
  for (const line of lines) (code === 0 ? console.log : console.error)(line);
  return code;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exitCode = main(process.argv.slice(2));
}
