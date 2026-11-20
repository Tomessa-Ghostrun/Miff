#!/usr/bin/env node
// Outillage d'échange de traductions Miff (ticket « Miff en 6 langues », lot 4A) : extraire les textes communs à Android et iOS dans un
// fichier d'échange unique, contrôler la forme d'un fichier de traductions, le réinjecter dans les deux apps, préparer les lots des agents.
// Marche à suivre complète : docs/i18n/procedure-lot4.md. Aucune dépendance, aucun service externe (sauf `gel-prs`, qui appelle `gh` et `git fetch`).
//
// Commandes (options communes : --racine <dossier du dépôt>, défaut : le dépôt de ce script)
//   extraire        [--sortie <dossier>] [--verifier] [--sans-usages]    écrit source-fr-en.json, propres-android.json, propres-ios.json
//   gel-prs         [--base origin/main] [--branche <ref>]...            clés touchées par les PR ouvertes -> gel-prs.json
//   controler       --langue <code> [--fichier <f>] [--complet] [--json] [--tout]   contrôle de forme d'un fichier de traductions
//   injecter        --langue <code> [--fichier <f>] [--plateforme android|ios|tout] [--dry-run] [--json]   réinjection dans les apps
//   lots            --langue <code|toutes> [--mode traduction|aveugle|relecture|retro] [--taille 100] [--sortie <dossier>]   lots pour les agents
//                   [--restant : exclut les clés déjà présentes dans traduction-<langue>.json] [--agents 4 : traducteurs par langue]
//   fusionner       --langue <code> (--dossier <d> | <fichiers...>) [--sortie <f>]   assemble les sorties des lots en traduction-<langue>.json
//
// Codes de sortie : 0 = OK, 1 = erreur(s) de traduction ou fichier d'échange périmé, 2 = usage incorrect ou fichier illisible.

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { loadRegistry } from './check-i18n-parity.mjs';
import { extractLanguageKit } from './lib/i18n-docs.mjs';
import {
  ANDROID_RES, CONFIG_FILE, EXCHANGE_DIR, GEL_SNAPSHOT_FILE, IOS_INFOPLIST, IOS_LOCALIZABLE, SOURCE_FILE,
  applyIosCatalog, buildAndroidStrings, buildExchange, buildUsageIndex, checkTranslations, computeOpenPrKeyChanges, exchangeFiles, indexExchange, longestVersusEnglish,
  normalizeConfig, prepareModels,
} from './lib/i18n-exchange.mjs';
import { DEFAULT_BATCH_SIZE, DEFAULT_TRANSLATORS, buildLots } from './lib/i18n-lots.mjs';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

class UsageError extends Error {}

const json = (value) => `${JSON.stringify(value, null, 2)}\n`;
const readText = (path) => readFileSync(path, 'utf8');
const readJson = (path) => {
  try {
    return JSON.parse(readText(path));
  } catch (error) {
    throw new UsageError(`${path} illisible : ${error.message}`);
  }
};

function writeFile(path, content) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
}

/** Arguments : `--nom valeur`, `--drapeau`, positionnels. Les options répétables (--branche) sont toujours des listes. */
export function parseArgs(argv) {
  const flags = new Set(['verifier', 'complet', 'json', 'tout', 'dry-run', 'sans-usages', 'restant', 'aide']);
  const repeatable = new Set(['branche']);
  const options = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith('--')) {
      options._.push(arg);
      continue;
    }
    const name = arg.slice(2);
    if (flags.has(name)) {
      options[name] = true;
      continue;
    }
    const value = argv[i + 1];
    if (value === undefined || value.startsWith('--')) throw new UsageError(`l'option --${name} attend une valeur`);
    i += 1;
    if (repeatable.has(name)) (options[name] ??= []).push(value);
    else options[name] = value;
  }
  return options;
}

/** Charge registre, configuration, instantané de gel et ressources du dépôt, et construit le fichier d'échange. */
export function loadWorld(root, { usages = true } = {}) {
  const registry = loadRegistry(root);
  if (!registry) throw new UsageError('registre docs/contracts/i18n-languages.json introuvable');
  const configPath = join(root, CONFIG_FILE);
  const config = normalizeConfig(existsSync(configPath) ? readJson(configPath) : {});
  const snapshotPath = join(root, GEL_SNAPSHOT_FILE);
  const snapshot = existsSync(snapshotPath) ? readJson(snapshotPath) : null;
  const read = (rel) => {
    const file = join(root, rel);
    if (!existsSync(file)) throw new UsageError(`fichier introuvable : ${rel}`);
    return readText(file);
  };
  const androidFrXml = read(`${ANDROID_RES}/values-fr/strings.xml`);
  const androidEnXml = read(`${ANDROID_RES}/values/strings.xml`);
  const iosCatalogText = read(IOS_LOCALIZABLE);
  const iosInfoText = existsSync(join(root, IOS_INFOPLIST)) ? read(IOS_INFOPLIST) : undefined;
  let usageIndex;
  if (usages) {
    const keys = [...androidFrXml.matchAll(/<(?:string|plurals)\s+name="([^"]+)"/g)].map((m) => m[1]);
    usageIndex = buildUsageIndex(root, keys);
  }
  const result = buildExchange({ androidFrXml, androidEnXml, iosCatalogText, iosInfoText, registry, config, snapshot, usages: usageIndex });
  return { root, registry, config, snapshot, result, byKey: indexExchange(result), androidFrXml, iosCatalogText, iosInfoText };
}

function targetLanguage(world, code) {
  if (!code) throw new UsageError('--langue <code> est obligatoire (es, it, pt, de)');
  const language = world.registry.languages.find((l) => l.code === code);
  if (!language) throw new UsageError(`langue « ${code} » absente du registre`);
  if (code === world.registry.sourceLanguage || code === world.registry.fallbackLanguage) throw new UsageError(`« ${code} » est la langue source ou de repli : rien à traduire ni à réinjecter`);
  return language;
}

function readTranslations(world, language, file) {
  const path = resolve(file ?? join(world.root, EXCHANGE_DIR, `traduction-${language.code}.json`));
  if (!existsSync(path)) throw new UsageError(`fichier de traductions introuvable : ${path}`);
  const content = readJson(path);
  if (content?.langue !== language.code) throw new UsageError(`${path} : « langue » vaut « ${content?.langue} », « ${language.code} » attendu`);
  if (content.traductions === null || typeof content.traductions !== 'object' || Array.isArray(content.traductions)) throw new UsageError(`${path} : objet « traductions » attendu`);
  return { path, translations: content.traductions };
}

// ---------------------------------------------------------------------------------------------------------------------
// Commandes
// ---------------------------------------------------------------------------------------------------------------------

export function commandExtract(options, log = console.log) {
  const root = resolve(options.racine ?? REPO_ROOT);
  const world = loadWorld(root, { usages: !options['sans-usages'] });
  const outDir = options.sortie ? resolve(options.sortie) : null;
  const files = exchangeFiles(world.result, { registry: world.registry });
  let stale = 0;
  for (const [rel, content] of Object.entries(files)) {
    const target = outDir ? join(outDir, rel.slice(`${EXCHANGE_DIR}/`.length)) : join(root, rel);
    const text = json(content);
    if (options.verifier) {
      const current = existsSync(target) ? readText(target) : null;
      if (current !== text) {
        stale += 1;
        log(`périmé : ${rel}`);
      }
    } else {
      writeFile(target, text);
      log(`écrit : ${rel} (${text.length} octets)`);
    }
  }
  const s = world.result.stats;
  log(`Textes par langue : ${s.parLangue} = ${s.communs} communs + ${s.propresAndroid} propres Android + ${s.propresIos} propres iOS + ${s.infoPlist} InfoPlist`);
  log(`Gelés : ${s.gelees} (dont ${s.gelees_communs} communs) ; divergents entre plateformes : ${s.divergentes} ; pluriels : ${s.pluriels} ; sensibles : ${s.sensibles}`);
  log(`Mots (français) : ${s.mots.fr} ; (anglais) : ${s.mots.en}`);
  for (const w of world.result.warnings) log(`avertissement : ${w}`);
  return options.verifier && stale > 0 ? 1 : 0;
}

export function commandGelPrs(options, log = console.log) {
  const root = resolve(options.racine ?? REPO_ROOT);
  const base = options.base ?? 'origin/main';
  const snapshot = computeOpenPrKeyChanges(root, { base, extraRefs: options.branche ?? [] });
  const full = {
    genereLe: new Date().toISOString().slice(0, 10),
    methode: 'gh pr list --state open ; pour chaque PR qui touche aux fichiers de chaînes : git fetch origin refs/pull/N/head, puis comparaison des clés entre le point de départ de la PR et sa tête (values-fr, values, Localizable, InfoPlist). Voir docs/i18n/procedure-lot4.md §3.',
    ...snapshot,
  };
  const target = join(root, GEL_SNAPSHOT_FILE);
  writeFile(target, json(full));
  log(`écrit : ${GEL_SNAPSHOT_FILE} (${full.prs.length} PR avec des changements de textes)`);
  for (const pr of full.prs) log(`  #${pr.numero ?? '-'} ${pr.branche} : +${pr.ajoutees.length} -${pr.supprimees.length} ~${pr.modifiees.length}`);
  return 0;
}

export function commandCheck(options, log = console.log) {
  const root = resolve(options.racine ?? REPO_ROOT);
  const world = loadWorld(root, { usages: false });
  const language = targetLanguage(world, options.langue);
  const { path, translations } = readTranslations(world, language, options.fichier);
  const report = checkTranslations({ registry: world.registry, config: world.config, byKey: world.byKey, lang: language.code, translations, complete: !!options.complet });
  if (options.json) {
    log(JSON.stringify(report, null, 2));
  } else {
    log(`Contrôle de ${path}`);
    const shown = options.tout ? report.findings : report.findings.slice(0, 80);
    for (const f of shown) log(`  ${f.level === 'error' ? 'ERREUR' : 'avert.'} [${f.check}] ${f.key} (${f.platform}) : ${f.message}`);
    if (shown.length < report.findings.length) log(`  … ${report.findings.length - shown.length} constat(s) de plus (--tout pour tout voir)`);
    log(`${report.translated} texte(s) contrôlé(s), ${report.errors} erreur(s), ${report.warnings} avertissement(s) ; ${report.missing.length} clé(s) attendue(s) absente(s) ; ${report.unknown.length} clé(s) inconnue(s).`);
    const ratio = report.lengthRatioVsFallback;
    log(`Longueur par rapport à l'anglais : ${Object.entries(ratio).map(([p, r]) => `${p} ×${r}`).join(', ') || 'n/a'}`);
    for (const row of longestVersusEnglish(world.byKey, translations, 5)) log(`  plus long vs en : ${row.cle} ×${row.rapport} (${row.longueur} / ${row.longueurEn})`);
  }
  return report.errors > 0 ? 1 : 0;
}

export function commandInject(options, log = console.log) {
  const root = resolve(options.racine ?? REPO_ROOT);
  const world = loadWorld(root, { usages: false });
  const language = targetLanguage(world, options.langue);
  const platform = options.plateforme ?? 'tout';
  if (!['android', 'ios', 'tout'].includes(platform)) throw new UsageError('--plateforme : android, ios ou tout');
  const { translations } = readTranslations(world, language, options.fichier);
  const report = checkTranslations({ registry: world.registry, config: world.config, byKey: world.byKey, lang: language.code, translations, complete: false });
  if (report.errors > 0) {
    for (const f of report.findings.filter((x) => x.level === 'error').slice(0, 40)) log(`ERREUR [${f.check}] ${f.key} (${f.platform}) : ${f.message}`);
    log(`${report.errors} erreur(s) de forme : rien n'a été écrit. Corrige le fichier puis relance (voir « controler »).`);
    return 1;
  }
  const models = prepareModels(world.byKey, translations);
  const outcome = { langue: language.code, plateformes: {}, ignorees: [...models.skipped] };
  const writes = [];

  if (platform === 'android' || platform === 'tout') {
    const dirName = language.platforms?.android?.resDir;
    if (!dirName) throw new UsageError(`${language.code} : pas de dossier Android dans le registre`);
    const target = join(root, ANDROID_RES, dirName, 'strings.xml');
    const existing = existsSync(target) ? readText(target) : null;
    const built = buildAndroidStrings({ frXml: world.androidFrXml, existingXml: existing, lang: language.code, nativeName: language.nativeName, models: models.android });
    const androidKeys = new Set([...world.byKey.values()].filter((e) => e.plateformes.includes('android')).map((e) => e.cle));
    outcome.plateformes.android = {
      fichier: `${ANDROID_RES}/${dirName}/strings.xml`, ecrites: built.written, inchangees: built.unchanged, conservees: built.kept,
      manquantes: [...androidKeys].filter((k) => !models.android.has(k) && !(existing ?? '').includes(`name="${k}"`)).length,
    };
    writes.push({ path: target, content: built.xml, changed: existing !== built.xml });
  }
  if (platform === 'ios' || platform === 'tout') {
    const code = language.platforms?.ios?.code;
    if (!code) throw new UsageError(`${language.code} : pas de code iOS dans le registre`);
    const apply = (rel, map, missingFilter) => {
      const path = join(root, rel);
      if (!existsSync(path)) return null;
      const current = readText(path);
      const applied = applyIosCatalog(current, code, map);
      const expected = [...world.byKey.values()].filter(missingFilter).map((e) => e.cle);
      outcome.plateformes[rel.endsWith('InfoPlist.xcstrings') ? 'iosInfoPlist' : 'ios'] = {
        fichier: rel, ecrites: applied.written, inchangees: applied.unchanged, manquantes: expected.filter((k) => !map.has(k)).length,
      };
      outcome.ignorees.push(...applied.ignored);
      writes.push({ path, content: applied.text, changed: applied.text !== current });
      return applied;
    };
    apply(IOS_LOCALIZABLE, models.ios, (e) => e.plateformes.includes('ios') && !e.table);
    if (models.infoPlist.size > 0 || [...world.byKey.values()].some((e) => e.table)) apply(IOS_INFOPLIST, models.infoPlist, (e) => !!e.table);
  }

  if (!options['dry-run']) for (const w of writes) if (w.changed) writeFile(w.path, w.content);
  outcome.fichiersModifies = writes.filter((w) => w.changed).length;
  outcome.dryRun = !!options['dry-run'];
  outcome.avertissements = report.warnings;
  if (options.json) log(JSON.stringify(outcome, null, 2));
  else {
    log(`Réinjection ${language.code}${options['dry-run'] ? ' (simulation, rien n\'est écrit)' : ''} :`);
    for (const [name, p] of Object.entries(outcome.plateformes)) log(`  ${name} : ${p.fichier} : ${p.ecrites} écrit(s) (${p.inchangees} déjà identiques), ${p.manquantes} clé(s) attendue(s) sans traduction`);
    for (const skipped of outcome.ignorees) log(`  ignoré : ${skipped.cle} : ${skipped.raison}`);
    log(`${outcome.fichiersModifies} fichier(s) modifié(s) ; ${report.warnings} avertissement(s) de forme (voir « controler »).`);
  }
  return 0;
}

export function commandLots(options, log = console.log) {
  const root = resolve(options.racine ?? REPO_ROOT);
  const world = loadWorld(root, { usages: !options['sans-usages'] });
  const mode = options.mode ?? 'traduction';
  if (!['traduction', 'aveugle', 'relecture', 'retro'].includes(mode)) throw new UsageError('--mode : traduction, aveugle, relecture ou retro');
  const size = Number(options.taille ?? world.config.tailleLot ?? DEFAULT_BATCH_SIZE);
  if (!Number.isInteger(size) || size < 1) throw new UsageError('--taille : entier positif attendu');
  const codes = options.langue === 'toutes' || !options.langue
    ? world.registry.languages.filter((l) => l.code !== world.registry.sourceLanguage && l.code !== world.registry.fallbackLanguage).map((l) => l.code)
    : [options.langue];
  const outRoot = resolve(options.sortie ?? join(root, EXCHANGE_DIR, 'lots'));
  const glossaire = readText(join(root, 'docs/i18n/glossaire.md'));
  const guide = readText(join(root, 'docs/i18n/guide-de-style.md'));
  const entries = [...world.result.communs, ...world.result.propresAndroid, ...world.result.propresIos];
  const summary = [];
  for (const code of codes) {
    const language = targetLanguage(world, code);
    const kit = extractLanguageKit({ glossaire, guide }, language.code);
    let translations;
    if (mode === 'relecture' || mode === 'retro') translations = readTranslations(world, language, options.fichier).translations;
    let excludeKeys;
    if (options.restant) {
      const existing = join(root, EXCHANGE_DIR, `traduction-${language.code}.json`);
      excludeKeys = new Set(existsSync(existing) ? Object.keys(readJson(existing).traductions ?? {}) : []);
    }
    const agents = Number(options.agents ?? DEFAULT_TRANSLATORS);
    if (!Number.isInteger(agents) || agents < 1) throw new UsageError('--agents : entier positif attendu');
    const built = buildLots({ language, entries, config: world.config, kit, mode, size, translations, excludeKeys, agents });
    const dir = join(outRoot, language.code);
    for (const file of built.files) writeFile(join(dir, file.name), json(file.content));
    if (built.plan) writeFile(join(dir, 'plan-traducteurs.json'), json({ langue: language.code, note: 'Attribution des lots aux agents traducteurs, dans l’ordre : les lots sensibles sont en tête.', ...built.plan }));
    writeFile(join(dir, `gele-${mode}.json`), json({ langue: language.code, note: 'Clés gelées : à traiter après la fusion des PR concernées (docs/i18n/procedure-lot4.md §3).', cles: built.frozen }));
    for (const w of kit.avertissements) log(`avertissement (${language.code}) : ${w}`);
    summary.push(built.stats);
    log(`${language.code} : ${built.stats.lots} lot(s) de ${size} au plus (${built.stats.lotsSensibles} sensibles, ${built.stats.lotsCourants} courants), ${built.stats.textes} textes, ≈ ${built.stats.motsFr} mots (fr) ; ${built.stats.gelees} gelé(s) et ${built.stats.internes} interne(s) exclus -> ${dir}`);
    for (const a of built.plan?.agents ?? []) log(`    ${a.agent} : ${a.lots.join(', ')} (${a.textes} textes, ≈ ${a.mots} mots ; ${a.groupes.join(' + ')})`);
  }
  if (options.json) log(JSON.stringify(summary, null, 2));
  return 0;
}

export function commandMerge(options, log = console.log) {
  const root = resolve(options.racine ?? REPO_ROOT);
  const registry = loadRegistry(root);
  const code = options.langue;
  if (!code || !registry?.languages.some((l) => l.code === code)) throw new UsageError('--langue <code> du registre attendu');
  let files = options._.slice(1);
  if (options.dossier) {
    files = readdirSync(resolve(options.dossier)).filter((f) => /^batch-\d+\.json$/.test(f)).sort().map((f) => join(resolve(options.dossier), f));
  }
  if (files.length === 0) throw new UsageError('aucun fichier de sortie de lot à fusionner (--dossier <d> ou liste de fichiers)');
  const target = resolve(options.sortie ?? join(root, EXCHANGE_DIR, `traduction-${code}.json`));
  const merged = existsSync(target) ? (readJson(target).traductions ?? {}) : {};
  const conflicts = [];
  const lots = [];
  const notesPath = target.replace(/.json$/, '.notes.json');
  const previous = existsSync(notesPath) ? readJson(notesPath) : {};
  const questions = { ...(previous.questions ?? {}) };
  const toFix = { ...(previous.a_corriger_source ?? {}) };
  for (const file of files) {
    const content = readJson(file);
    if (content.langue !== code) throw new UsageError(`${file} : langue « ${content.langue} », « ${code} » attendu`);
    if (!content.traductions || typeof content.traductions !== 'object') throw new UsageError(`${file} : objet « traductions » attendu`);
    for (const [key, value] of Object.entries(content.traductions)) {
      if (key in merged && JSON.stringify(merged[key]) !== JSON.stringify(value)) conflicts.push(`${key} (déjà présente, valeur différente dans ${file})`);
      merged[key] = value;
    }
    lots.push(content.lot ?? file);
    for (const [field, target] of [['questions', questions], ['a_corriger_source', toFix]]) {
      for (const [key, note] of Object.entries(content[field] ?? {})) if (typeof note === 'string' && !key.startsWith('<')) target[key] = note;
    }
  }
  if (conflicts.length > 0) {
    for (const c of conflicts) log(`conflit : ${c}`);
    log('Rien n\'a été écrit : une clé ne doit apparaître qu\'une fois (ou avec la même valeur).');
    return 1;
  }
  const sorted = Object.fromEntries(Object.keys(merged).sort().map((k) => [k, merged[k]]));
  writeFile(target, json({ langue: code, traductions: sorted }));
  log(`écrit : ${target} (${Object.keys(sorted).length} clés, ${lots.length} lot(s) : ${lots.join(', ')})`);
  if (Object.keys(questions).length + Object.keys(toFix).length > 0) {
    writeFile(notesPath, json({ langue: code, questions, a_corriger_source: toFix }));
    log(`notes des agents : ${notesPath} (${Object.keys(questions).length} question(s), ${Object.keys(toFix).length} texte(s) source à corriger)`);
  }
  return 0;
}

const COMMANDS = {
  extraire: commandExtract,
  'gel-prs': commandGelPrs,
  controler: commandCheck,
  injecter: commandInject,
  lots: commandLots,
  fusionner: commandMerge,
};

export function main(argv, log = console.log) {
  let options;
  try {
    options = parseArgs(argv);
    const name = options._[0];
    if (!name || options.aide || !COMMANDS[name]) {
      const lines = readText(fileURLToPath(import.meta.url)).split('\n');
      const end = lines.findIndex((l) => l.startsWith('import '));
      log(lines.slice(0, end).filter((l) => l.startsWith('//')).map((l) => l.slice(3)).join('\n'));
      return name && !COMMANDS[name] ? 2 : 0;
    }
    return COMMANDS[name](options, log);
  } catch (error) {
    if (error instanceof UsageError) {
      log(`erreur : ${error.message}`);
      return 2;
    }
    log(`erreur : ${error.message}`);
    return 2;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) process.exit(main(process.argv.slice(2)));
