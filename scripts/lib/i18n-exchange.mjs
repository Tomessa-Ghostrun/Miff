// Fichier d'échange de traductions Miff (ticket « Miff en 6 langues », lot 4A) : extraction depuis Android et iOS, contrôle de forme des
// traductions, réinjection dans les deux apps. Aucune dépendance. Les fonctions de ce fichier ne lisent ni n'écrivent le disque (sauf
// `buildUsageIndex` et `computeOpenPrKeyChanges`, signalées) : la lecture et l'écriture sont dans scripts/i18n-exchange.mjs.
//
// Vocabulaire
//  - texte « commun » : même clé sur Android (strings.xml) et iOS (Localizable.xcstrings), traduit UNE fois ;
//  - texte « propre » : clé `android_*` (Android seul), `ios_*` (iOS seul) ou entrée d'InfoPlist.xcstrings (iOS seul) ;
//  - « modèle » : {kind: 'string'|'plural'|'substituted', texts: [{id, value}]} ; `id` = '' (texte simple), catégorie de pluriel, `arg:catégorie` ;
//  - notation des valeurs insérées dans le fichier d'échange pour un texte commun : celle d'Android (`%1$s`, `%2$d`, `%2$02d`, `%s`, `%d`).

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { androidLocalization, checkLanguageTable, isException } from './i18n-checks.mjs';
import {
  PLACEHOLDER, catalogLocalization, decodeAndroidValue, parseCatalog, placeholderSignatures, strayPercents, stripXmlComments, withoutPlaceholders,
} from './i18n-resources.mjs';

export const EXCHANGE_DIR = 'docs/i18n/echange';
export const CONFIG_FILE = `${EXCHANGE_DIR}/config.json`;
export const SOURCE_FILE = `${EXCHANGE_DIR}/source-fr-en.json`;
export const OWN_ANDROID_FILE = `${EXCHANGE_DIR}/propres-android.json`;
export const OWN_IOS_FILE = `${EXCHANGE_DIR}/propres-ios.json`;
export const GEL_SNAPSHOT_FILE = `${EXCHANGE_DIR}/gel-prs.json`;
export const ANDROID_RES = 'android/app/src/main/res';
export const IOS_RES = 'ios/Miff/Resources';
export const IOS_LOCALIZABLE = `${IOS_RES}/Localizable.xcstrings`;
export const IOS_INFOPLIST = `${IOS_RES}/InfoPlist.xcstrings`;
export const PLURAL_ORDER = ['zero', 'one', 'two', 'few', 'many', 'other'];
export const EXCHANGE_VERSION = 1;

const NBSP = ' ';
const NNBSP = ' ';

// ---------------------------------------------------------------------------------------------------------------------
// Valeurs insérées
// ---------------------------------------------------------------------------------------------------------------------

// %[n$][drapeaux][largeur][.précision][longueur]type, `%%`, `%#@nom@`, `%arg`.
const SCAN = /%%|%#@\w+@|%arg\b|%(\d+\$)?([-#+0,(]*)(\d+)?(\.\d+)?(hh|h|ll|l|q|L|z|t|j)?([a-zA-Z@])/g;

const normType = (type) => {
  if ('sS@'.includes(type)) return 's';
  if ('diu'.includes(type)) return 'd';
  return type;
};

/** Valeurs insérées d'un texte, dans l'ordre : [{raw, position, flags, width, precision, length, type, signature}] (positions implicites numérotées). */
export function scanPlaceholders(text) {
  const result = [];
  let implicit = 0;
  for (const m of text.matchAll(SCAN)) {
    if (m[0].startsWith('%%') || m[0].startsWith('%#@') || m[0] === '%arg') continue;
    const position = m[1] !== undefined ? Number(m[1].slice(0, -1)) : (implicit += 1);
    const pad = `${(m[2] ?? '').includes('0') ? '0' : ''}${m[3] ?? ''}`;
    result.push({
      raw: m[0], position, explicit: m[1] !== undefined, flags: m[2] ?? '', width: m[3] ?? '', precision: m[4] ?? '', length: m[5] ?? '', type: m[6],
      signature: `${position}${normType(m[6])}${pad}`,
    });
  }
  return result;
}

/** Notation canonique (celle d'Android) : `%@` devient `%s`, `%lld` / `%ld` / `%i` / `%u` deviennent `%d`. Les positions restent telles quelles. */
export function canonicalPlaceholders(text) {
  return text.replace(SCAN, (all, pos, flags, width, precision, length, type) => {
    if (all.startsWith('%%') || all.startsWith('%#@') || all === '%arg') return all;
    return `%${pos ?? ''}${flags ?? ''}${width ?? ''}${precision ?? ''}${normType(type)}`;
  });
}

/**
 * Table signature -> {type, explicit} du texte iOS français (`lld`, `d`, `@`...; position écrite ou non), pour reproduire le même type et le même style
 * (une seule valeur insérée écrite `%lld` en français iOS reste `%lld`, jamais `%1$lld`).
 */
export function iosTypeMap(texts) {
  const map = new Map();
  for (const text of texts) {
    for (const p of scanPlaceholders(text)) {
      if (!map.has(p.signature)) map.set(p.signature, { type: `${p.length}${p.type}`, explicit: p.explicit });
    }
  }
  return map;
}

/** Texte à écrire dans le catalogue iOS : types calqués sur le français iOS (sinon `s` -> `@`, `d` -> `lld`). */
export function toIosPlaceholders(text, typeMap) {
  let implicit = 0;
  const single = scanPlaceholders(text).length === 1;
  return text.replace(SCAN, (all, pos, flags, width, precision, length, type) => {
    if (all.startsWith('%%') || all.startsWith('%#@') || all === '%arg') return all;
    const position = pos !== undefined ? Number(pos.slice(0, -1)) : (implicit += 1);
    const pad = `${(flags ?? '').includes('0') ? '0' : ''}${width ?? ''}`;
    const signature = `${position}${normType(type)}${pad}`;
    const known = typeMap?.get(signature);
    const mapped = known?.type ?? (normType(type) === 's' ? '@' : normType(type) === 'd' ? 'lld' : `${length ?? ''}${type}`);
    // Une seule valeur insérée, écrite sans position en français iOS : même écriture.
    const written = single && position === 1 && known && !known.explicit ? '' : (pos ?? '');
    return `%${written}${flags ?? ''}${width ?? ''}${precision ?? ''}${mapped}`;
  });
}

/** Valeurs insérées (jetons canoniques, sans doublon, dans l'ordre d'apparition) d'une liste de textes. */
export function placeholderTokens(texts) {
  const seen = new Map();
  for (const text of texts) {
    for (const p of scanPlaceholders(canonicalPlaceholders(text))) if (!seen.has(p.signature)) seen.set(p.signature, p);
  }
  return [...seen.values()].sort((a, b) => a.position - b.position || a.signature.localeCompare(b.signature)).map((p) => p.raw);
}

// ---------------------------------------------------------------------------------------------------------------------
// Android : structure du strings.xml, encodage des valeurs
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Éléments d'un strings.xml, dans l'ordre : commentaires de section, `<string>`, `<plurals>`, `<string-array>`.
 * @returns {{items: object[], header: string}} `header` = commentaire d'en-tête (avant `<resources>`).
 */
export function parseAndroidStructure(xml) {
  const resourcesStart = xml.search(/<resources\b/);
  const header = resourcesStart > 0 ? (/<!--([\s\S]*?)-->/.exec(xml.slice(0, resourcesStart))?.[1] ?? '') : '';
  const body = resourcesStart >= 0 ? xml.slice(xml.indexOf('>', resourcesStart) + 1) : xml;
  const items = [];
  const re = /<!--([\s\S]*?)-->|<(string|plurals|string-array)\b([^>]*?)(\/>|>([\s\S]*?)<\/\2>)/g;
  for (const m of body.matchAll(re)) {
    if (m[1] !== undefined) {
      items.push({ type: 'comment', text: m[1].replace(/\s+/g, ' ').trim() });
      continue;
    }
    const tag = m[2];
    const attributes = m[3];
    const name = /\bname\s*=\s*"([^"]+)"/.exec(attributes);
    if (!name) continue;
    const translatable = !/\btranslatable\s*=\s*"false"/.test(attributes);
    const extra = [...attributes.matchAll(/([A-Za-z:]+)\s*=\s*"([^"]*)"/g)].filter((a) => a[1] !== 'name' && a[1] !== 'translatable').map((a) => [a[1], a[2]]);
    if (tag === 'string') {
      items.push({ type: 'string', name: name[1], translatable, extra, raw: m[5] ?? '' });
    } else {
      const entries = [...(m[5] ?? '').matchAll(/<item\b([^>]*?)>([\s\S]*?)<\/item>/g)].map((i, index) => ({
        quantity: tag === 'plurals' ? (/\bquantity\s*=\s*"([^"]+)"/.exec(i[1])?.[1] ?? '?') : String(index), raw: i[2],
      }));
      items.push({ type: tag === 'plurals' ? 'plurals' : 'array', name: name[1], translatable, extra, items: entries });
    }
  }
  return { header: header.replace(/\s+/g, ' ').trim(), items };
}

/** Texte à écrire dans un strings.xml pour un texte tel qu'affiché (inverse de `decodeAndroidValue`). */
export function encodeAndroidValue(text) {
  let out = '';
  for (const char of text) {
    switch (char) {
      case '\\': out += '\\\\'; break;
      case "'": out += "\\'"; break;
      case '"': out += '\\"'; break;
      case '&': out += '&amp;'; break;
      case '<': out += '&lt;'; break;
      case '\n': out += '\\n'; break;
      case '\t': out += '\\t'; break;
      case NBSP: out += '&#160;'; break;
      case NNBSP: out += '&#8239;'; break;
      default: out += char;
    }
  }
  if (/^[@?]/.test(out)) out = `\\${out}`;
  return out;
}

// ---------------------------------------------------------------------------------------------------------------------
// Modèles de texte
// ---------------------------------------------------------------------------------------------------------------------

/** Modèle d'une entrée Android (déjà décodée). */
function androidModel(item) {
  if (item.type === 'string') return { kind: 'string', texts: [{ id: '', value: decodeAndroidValue(item.raw) }] };
  return { kind: 'plural', texts: item.items.map((i) => ({ id: i.quantity, value: decodeAndroidValue(i.raw) })) };
}

/** Modèle d'une entrée de catalogue iOS pour une langue, ou null. Les variations non gérées ressortent en `unsupported`. */
function iosModel(entry, lang) {
  const loc = catalogLocalization(entry, lang);
  if (!loc) return null;
  return { kind: loc.kind, texts: loc.texts.map((t) => ({ id: t.id, value: t.value })) };
}

const canonicalModel = (model) => (model ? { kind: model.kind, texts: model.texts.map((t) => ({ id: t.id, value: canonicalPlaceholders(t.value) })) } : null);

const sortedTexts = (model) => [...model.texts].sort((a, b) => a.id.localeCompare(b.id));

/** Positions explicites (%s -> %1$s) : deux textes qui ne diffèrent que par cela sont le même texte. */
export function explicitPlaceholders(text) {
  let implicit = 0;
  return text.replace(SCAN, (all, pos, flags, width, precision, length, type) => {
    if (all.startsWith('%%') || all.startsWith('%#@') || all === '%arg') return all;
    const position = pos !== undefined ? pos : `${(implicit += 1)}$`;
    return `%${position}${flags ?? ''}${width ?? ''}${precision ?? ''}${normType(type)}`;
  });
}

/** Forme de comparaison entre plateformes : positions explicites, apostrophe droite, espaces insécables ordinaires. */
const comparableText = (text) => explicitPlaceholders(canonicalPlaceholders(text)).replace(/[’]/g, "'").replace(/[  ]/g, ' ');
const sameModel = (a, b) => !!a && !!b && a.kind === b.kind
  && JSON.stringify(sortedTexts(a).map((t) => [t.id, comparableText(t.value)])) === JSON.stringify(sortedTexts(b).map((t) => [t.id, comparableText(t.value)]));

/** Valeur JSON d'un modèle : texte, objet de formes de pluriel, ou {modele, formes} pour les substitutions iOS. */
export function valueFromModel(model) {
  if (!model) return null;
  if (model.kind === 'string') return model.texts[0].value;
  if (model.kind === 'plural') {
    const forms = {};
    for (const t of [...model.texts].sort((a, b) => PLURAL_ORDER.indexOf(a.id) - PLURAL_ORDER.indexOf(b.id))) forms[t.id] = t.value;
    return forms;
  }
  if (model.kind === 'substituted') {
    const formes = {};
    let modele = '';
    for (const t of model.texts) {
      if (t.id === '') {
        modele = t.value;
        continue;
      }
      const [arg, category] = t.id.split(':');
      (formes[arg] ??= {})[category] = t.value;
    }
    return { modele, formes };
  }
  return null;
}

/** Inverse de `valueFromModel`, avec validation de la forme. @returns {{model?: object, error?: string}} */
export function modelFromValue(kind, value) {
  const isObject = value !== null && typeof value === 'object' && !Array.isArray(value);
  if (kind === 'string') {
    if (typeof value !== 'string') return { error: `texte attendu (chaîne), trouvé : ${isObject ? 'objet' : typeof value}` };
    return { model: { kind, texts: [{ id: '', value }] } };
  }
  if (kind === 'plural') {
    if (!isObject) return { error: 'objet de formes de pluriel attendu ({"one": …, "other": …})' };
    const texts = [];
    for (const [category, text] of Object.entries(value)) {
      if (typeof text !== 'string') return { error: `forme « ${category} » : texte attendu` };
      texts.push({ id: category, value: text });
    }
    return { model: { kind, texts } };
  }
  if (kind === 'substituted') {
    if (!isObject || typeof value.modele !== 'string' || value.formes === null || typeof value.formes !== 'object') return { error: 'objet {"modele": …, "formes": {arg: {one, other}}} attendu' };
    const texts = [{ id: '', value: value.modele }];
    for (const [arg, forms] of Object.entries(value.formes)) {
      for (const [category, text] of Object.entries(forms ?? {})) {
        if (typeof text !== 'string') return { error: `forme « ${arg}:${category} » : texte attendu` };
        texts.push({ id: `${arg}:${category}`, value: text });
      }
    }
    return { model: { kind, texts } };
  }
  return { error: `type « ${kind} » non pris en charge` };
}

// ---------------------------------------------------------------------------------------------------------------------
// Configuration, gel, familles sensibles
// ---------------------------------------------------------------------------------------------------------------------

export const DEFAULT_CONFIG = {
  version: 1,
  gel: { prefixes: [], cles: [] },
  sensibles: { motifs: [] },
  internes: { motifs: [] },
  sensiblesRenforces: { motifs: [] },
  ordreSensibles: [],
  motifs: {},
  invariables: { cles: [], parLangue: {} },
  tailleLot: 100,
};

export function normalizeConfig(config) {
  const c = { ...DEFAULT_CONFIG, ...(config ?? {}) };
  c.gel = { ...DEFAULT_CONFIG.gel, ...(c.gel ?? {}) };
  c.sensibles = { ...DEFAULT_CONFIG.sensibles, ...(c.sensibles ?? {}) };
  c.internes = { ...DEFAULT_CONFIG.internes, ...(c.internes ?? {}) };
  c.sensiblesRenforces = { ...DEFAULT_CONFIG.sensiblesRenforces, ...(c.sensiblesRenforces ?? {}) };
  c.invariables = { ...DEFAULT_CONFIG.invariables, ...(c.invariables ?? {}) };
  return c;
}

/** Raisons pour lesquelles une clé est « gelée » (liste vide = non gelée). */
export function freezeReasons(key, config, snapshot) {
  const reasons = [];
  for (const prefix of config.gel.prefixes) if (key.startsWith(prefix)) reasons.push(`préfixe ${prefix}*`);
  if (config.gel.cles.includes(key)) reasons.push('clé listée dans config.json');
  for (const pr of snapshot?.prs ?? []) {
    const touched = [...(pr.ajoutees ?? []), ...(pr.supprimees ?? []), ...(pr.modifiees ?? [])];
    if (touched.includes(key)) reasons.push(`PR #${pr.numero} ouverte (${pr.supprimees?.includes(key) ? 'supprime la clé' : 'modifie le texte'})`);
  }
  return reasons;
}

/** Budget de longueur (caractères) d'un texte français : même calcul que le contrôle `length` / `short-label-budget` du script de parité. */
export function lengthBudget(key, frText, registry, isShort) {
  const ratio = registry?.lengthRatio ?? { maxVsSource: 1.5, minSourceLength: 10, shortLabelExtra: 0.4 };
  const base = Math.max([...frText].length, ratio.minSourceLength);
  if (isShort) return registry?.lengthBudgets?.[key] ?? Math.ceil(base * (1 + ratio.shortLabelExtra));
  return Math.floor(base * ratio.maxVsSource);
}

// ---------------------------------------------------------------------------------------------------------------------
// Extraction : fichier d'échange
// ---------------------------------------------------------------------------------------------------------------------

/** Légende de section raccourcie à un mot entier. */
const shorten = (text, max) => {
  if (text.length <= max) return text;
  const cut = text.slice(0, max);
  return `${cut.slice(0, Math.max(cut.lastIndexOf(' '), 40))} …`;
};

const hasLetters = (text) => /\p{L}/u.test(text);

/**
 * Construit le fichier d'échange depuis le contenu des ressources.
 * @param {object} p
 * @param {string} p.androidFrXml  values-fr/strings.xml
 * @param {string} p.androidEnXml  values/strings.xml
 * @param {string} p.iosCatalogText  Localizable.xcstrings
 * @param {string} [p.iosInfoText]  InfoPlist.xcstrings
 * @param {object} [p.registry]  registre des langues (budgets de longueur, motifs d'étiquettes courtes)
 * @param {object} [p.config]  config.json
 * @param {object} [p.snapshot]  gel-prs.json (clés touchées par des PR ouvertes)
 * @param {Map<string, {android: string[], ios: string[]}>} [p.usages]  fichiers de code qui citent chaque clé
 */
export function buildExchange({ androidFrXml, androidEnXml, iosCatalogText, iosInfoText, registry, config: rawConfig, snapshot, usages }) {
  const config = normalizeConfig(rawConfig);
  const shortPatterns = (registry?.shortLabelKeyPatterns ?? []).map((p) => new RegExp(p, 'u'));
  const sensiblePatterns = (config.sensibles.motifs ?? []).map((p) => new RegExp(p, 'u'));
  const reinforcedPatterns = (config.sensiblesRenforces.motifs ?? []).map((p) => new RegExp(p, 'u'));
  const internalPatterns = (config.internes.motifs ?? []).map((p) => new RegExp(p, 'u'));
  const warnings = [];

  const frStructure = parseAndroidStructure(androidFrXml);
  const enStructure = parseAndroidStructure(androidEnXml);
  const enByKey = new Map(enStructure.items.filter((i) => i.type !== 'comment').map((i) => [i.name, i]));
  const catalog = parseCatalog(iosCatalogText);
  const info = iosInfoText ? parseCatalog(iosInfoText) : null;

  // Android : clés traduisibles dans l'ordre du fichier, avec la dernière légende de section.
  const android = new Map();
  let section = '';
  for (const item of frStructure.items) {
    if (item.type === 'comment') {
      section = item.text;
      continue;
    }
    if (item.type === 'array') {
      warnings.push(`Android : tableau « ${item.name} » ignoré (les tableaux de chaînes ne sont pas pris en charge par l'échange)`);
      continue;
    }
    if (!item.translatable) continue;
    android.set(item.name, { item, section, en: enByKey.get(item.name) ?? null });
  }
  const ios = new Map();
  for (const [key, entry] of catalog.entries) if (entry.shouldTranslate) ios.set(key, entry);
  const rawCatalog = JSON.parse(iosCatalogText);

  const makeEntry = (key, platforms, variants, extras = {}) => {
    const primary = variants.primary;
    const fr = primary.fr;
    const frTexts = fr.texts.map((t) => t.value);
    const isShort = fr.kind === 'string' && shortPatterns.some((r) => r.test(key));
    const budget = fr.kind === 'string'
      ? lengthBudget(key, fr.texts[0].value, registry, isShort)
      : Object.fromEntries(fr.texts.map((t) => [t.id, lengthBudget(key, t.value, registry, false)]));
    const prefix = key.includes('_') ? key.slice(0, key.indexOf('_')) : null;
    const entry = {
      cle: key,
      plateformes: platforms,
      type: fr.kind,
      fr: valueFromModel(fr),
      en: valueFromModel(primary.en),
      placeholders: placeholderTokens(frTexts),
      contexte: {
        ...(extras.section ? { ecran: shorten(extras.section, 110) } : {}),
        ...(prefix ? { prefixe: prefix } : {}),
        ...(usages?.get(key) ? { usages: usages.get(key) } : {}),
        ...(extras.comment ? { commentaire: extras.comment } : {}),
      },
      longueurMax: budget,
      courte: isShort,
    };
    if (extras.table) entry.table = extras.table;
    const reasons = freezeReasons(key, config, snapshot);
    entry.gele = reasons.length > 0;
    if (reasons.length > 0) entry.geleRaisons = reasons;
    if (sensiblePatterns.some((r) => r.test(key)) || extras.table) entry.sensible = true;
    if (reinforcedPatterns.some((r) => r.test(key))) {
      entry.sensible = true;
      entry.sensibleRenforce = true;
    }
    if (internalPatterns.some((r) => r.test(key))) entry.interne = true;
    if (config.motifs[key]) entry.motif = true;
    if (fr.kind === 'string' && primary.en?.kind === 'string' && fr.texts[0].value === primary.en.texts[0].value && hasLetters(withoutPlaceholders(fr.texts[0].value))) entry.invariableProbable = true;
    if (variants.iosVariant) {
      entry.divergent = { ios: { type: variants.iosVariant.fr.kind, fr: valueFromModel(variants.iosVariant.fr), en: valueFromModel(variants.iosVariant.en), placeholders: placeholderTokens(variants.iosVariant.fr.texts.map((t) => t.value)) } };
    }
    return entry;
  };

  const communs = [];
  const propresAndroid = [];
  const propresIos = [];
  const infoPlist = [];
  const unsupported = [];

  for (const [key, a] of android) {
    const aFr = androidModel(a.item);
    const aEnModel = a.en && a.en.type !== 'comment' ? androidModel(a.en) : null;
    if (key.startsWith('android_')) {
      propresAndroid.push(makeEntry(key, ['android'], { primary: { fr: aFr, en: aEnModel } }, { section: a.section }));
      continue;
    }
    const iEntry = ios.get(key);
    if (!iEntry) {
      warnings.push(`Android : la clé « ${key} » n'existe pas sur iOS (exclue de l'échange ; le script de parité la signale)`);
      continue;
    }
    const iFr = iosModel(iEntry, 'fr');
    const iEn = iosModel(iEntry, 'en');
    if (!iFr || iFr.kind === 'unsupported') {
      unsupported.push(key);
      warnings.push(`iOS : la clé « ${key} » a une variation non prise en charge (hors pluriels et substitutions) : exclue de l'échange`);
      continue;
    }
    const iFrCanon = canonicalModel(iFr);
    const iEnCanon = canonicalModel(iEn);
    const variants = { primary: { fr: aFr, en: aEnModel } };
    if (!sameModel(aFr, iFrCanon)) variants.iosVariant = { fr: iFrCanon, en: iEnCanon };
    else if (aEnModel && iEnCanon && !sameModel(aEnModel, iEnCanon)) {
      warnings.push(`« ${key} » : même français sur les deux plateformes, anglais différent (l'anglais d'Android est affiché ; celui d'iOS est dans contexte.anglaisIos)`);
    }
    const entry = makeEntry(key, ['android', 'ios'], variants, { section: a.section, comment: rawCatalog.strings?.[key]?.comment });
    if (!variants.iosVariant && aEnModel && iEnCanon && !sameModel(aEnModel, iEnCanon)) entry.contexte.anglaisIos = valueFromModel(iEnCanon);
    communs.push(entry);
  }

  const knownAndroid = new Set(android.keys());
  for (const [key, iEntry] of ios) {
    if (key.startsWith('ios_')) {
      const iFr = iosModel(iEntry, 'fr');
      if (!iFr || iFr.kind === 'unsupported') {
        unsupported.push(key);
        continue;
      }
      propresIos.push(makeEntry(key, ['ios'], { primary: { fr: iFr, en: iosModel(iEntry, 'en') } }, { comment: rawCatalog.strings?.[key]?.comment }));
    } else if (!knownAndroid.has(key)) {
      warnings.push(`iOS : la clé « ${key} » n'existe pas sur Android (exclue de l'échange ; le script de parité la signale)`);
    }
  }
  if (info) {
    const rawInfo = JSON.parse(iosInfoText);
    for (const [key, iEntry] of info.entries) {
      if (!iEntry.shouldTranslate) continue;
      const iFr = iosModel(iEntry, 'fr');
      if (!iFr || iFr.kind === 'unsupported') continue;
      infoPlist.push(makeEntry(key, ['ios'], { primary: { fr: iFr, en: iosModel(iEntry, 'en') } }, { table: 'InfoPlist.xcstrings', comment: rawInfo.strings?.[key]?.comment }));
    }
  }

  const all = [...communs, ...propresAndroid, ...propresIos, ...infoPlist];
  const stats = {
    communs: communs.length,
    propresAndroid: propresAndroid.length,
    propresIos: propresIos.length,
    infoPlist: infoPlist.length,
    parLangue: all.length,
    gelees: all.filter((e) => e.gele).length,
    gelees_communs: communs.filter((e) => e.gele).length,
    divergentes: communs.filter((e) => e.divergent).length,
    pluriels: all.filter((e) => e.type === 'plural').length,
    sensibles: all.filter((e) => e.sensible).length,
    internes: all.filter((e) => e.interne).length,
    sensiblesRenforces: all.filter((e) => e.sensibleRenforce).length,
    motifs: all.filter((e) => e.motif).length,
    invariablesProbables: all.filter((e) => e.invariableProbable).length,
    mots: { fr: countWords(all, 'fr'), en: countWords(all, 'en') },
  };
  return { communs, propresAndroid, propresIos: [...propresIos, ...infoPlist], stats, warnings, unsupported };
}

/** Nombre de mots (séparés par des espaces) de tous les textes d'une langue source, valeurs insérées exclues. Les formes de pluriel comptent toutes. */
export function countWords(entries, lang) {
  let total = 0;
  const count = (text) => withoutPlaceholders(text).split(/\s+/).filter((w) => /[\p{L}\p{N}]/u.test(w)).length;
  for (const entry of entries) {
    const variants = [entry, ...(entry.divergent ? [{ ...entry.divergent.ios }] : [])];
    for (const v of variants) {
      const value = v[lang];
      if (value === null || value === undefined) continue;
      if (typeof value === 'string') total += count(value);
      else if (typeof value.modele === 'string') total += count(value.modele) + Object.values(value.formes ?? {}).flatMap((f) => Object.values(f)).reduce((n, t) => n + count(t), 0);
      else total += Object.values(value).reduce((n, t) => n + count(t), 0);
    }
  }
  return total;
}

/** Contenu des trois fichiers d'échange (objets prêts à écrire). */
export function exchangeFiles(result, { registry } = {}) {
  const meta = {
    version: EXCHANGE_VERSION,
    langueSource: registry?.sourceLanguage ?? 'fr',
    langueRepli: registry?.fallbackLanguage ?? 'en',
    notation: 'Valeurs insérées des textes communs en notation Android (%1$s, %2$d, %2$02d, %s, %d). iOS les reçoit converties (%@, %lld) par le script.',
    comptes: result.stats,
  };
  return {
    [SOURCE_FILE]: { meta, textes: result.communs },
    [OWN_ANDROID_FILE]: { meta: { ...meta, comptes: undefined, notation: 'Notation Android native.' }, textes: result.propresAndroid },
    [OWN_IOS_FILE]: { meta: { ...meta, comptes: undefined, notation: 'Notation iOS native (%@, %lld). Les entrées « table: InfoPlist.xcstrings » vont dans InfoPlist.xcstrings.' }, textes: result.propresIos },
  };
}

/** Index de toutes les entrées d'échange par clé (communs, propres Android, propres iOS, InfoPlist). */
export function indexExchange(result) {
  const byKey = new Map();
  for (const entry of [...result.communs, ...result.propresAndroid, ...result.propresIos]) byKey.set(entry.cle, entry);
  return byKey;
}

// ---------------------------------------------------------------------------------------------------------------------
// Usages des clés dans le code (contexte « écran »)
// ---------------------------------------------------------------------------------------------------------------------

const SKIPPED = new Set(['node_modules', 'build', 'DerivedData', '.build', '.git', '.gradle', 'Pods']);
const TEST_DIR = /(^|\/)(test|androidTest|Tests|UITests|.*Tests)(\/|$)/;

/** Fichiers de code (Kotlin, Swift) qui citent chaque clé : lit le disque. Au plus 3 fichiers par plateforme, ordre alphabétique. */
export function buildUsageIndex(root, keys) {
  const wanted = new Set(keys);
  const found = new Map();
  const walk = (dir, platform) => {
    if (!existsSync(dir)) return;
    for (const name of readdirSync(dir).sort()) {
      if (SKIPPED.has(name)) continue;
      const full = join(dir, name);
      const rel = relative(root, full).split(sep).join('/');
      const info = statSync(full);
      if (info.isDirectory()) {
        if (TEST_DIR.test(rel)) continue;
        walk(full, platform);
      } else if (name.endsWith(platform === 'android' ? '.kt' : '.swift')) {
        const text = readFileSync(full, 'utf8');
        for (const m of text.matchAll(/[a-z][a-z0-9]*(?:_[a-z0-9]+)+/g)) {
          if (!wanted.has(m[0])) continue;
          const entry = found.get(m[0]) ?? { android: new Set(), ios: new Set() };
          entry[platform].add(rel);
          found.set(m[0], entry);
        }
      }
    }
  };
  walk(join(root, 'android/app/src/main'), 'android');
  walk(join(root, 'ios/Miff'), 'ios');
  const result = new Map();
  for (const [key, sets] of found) {
    const usage = {};
    for (const platform of ['android', 'ios']) if (sets[platform].size > 0) usage[platform] = [...sets[platform]].sort().slice(0, 3).map((p) => p.replace(/^android\/app\/src\/main\/java\/com\/tomessa\/miff\//, '').replace(/^ios\/Miff\//, ''));
    result.set(key, usage);
  }
  return result;
}

// ---------------------------------------------------------------------------------------------------------------------
// Clés touchées par les PR ouvertes (gel)
// ---------------------------------------------------------------------------------------------------------------------

const GEL_FILES = {
  androidFr: `${ANDROID_RES}/values-fr/strings.xml`,
  androidEn: `${ANDROID_RES}/values/strings.xml`,
  iosLocalizable: IOS_LOCALIZABLE,
  iosInfoPlist: IOS_INFOPLIST,
};

function git(root, args, options = {}) {
  return execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024, ...options });
}

function gitShow(root, ref, path) {
  try {
    return git(root, ['show', `${ref}:${path}`], { stdio: ['ignore', 'pipe', 'ignore'] });
  } catch {
    return null;
  }
}

/** Valeurs par clé d'un fichier de ressources (texte brut Android, JSON des langues fr et en pour iOS). */
function valuesByKey(kind, text) {
  const map = new Map();
  if (text === null) return map;
  if (kind.startsWith('android')) {
    for (const item of parseAndroidStructure(text).items) {
      if (item.type === 'comment' || !item.translatable) continue;
      map.set(item.name, JSON.stringify(item.type === 'string' ? item.raw : item.items));
    }
  } else {
    const catalog = JSON.parse(text);
    for (const [key, entry] of Object.entries(catalog.strings ?? {})) {
      map.set(key, JSON.stringify({ fr: entry.localizations?.fr, en: entry.localizations?.en, t: entry.shouldTranslate }));
    }
  }
  return map;
}

/** Clés ajoutées, supprimées et modifiées entre deux versions d'un même ensemble de fichiers de ressources. */
export function diffKeys(before, after) {
  const added = new Set();
  const removed = new Set();
  const changed = new Set();
  for (const kind of Object.keys(GEL_FILES)) {
    const a = valuesByKey(kind, before[kind] ?? null);
    const b = valuesByKey(kind, after[kind] ?? null);
    for (const [key, value] of b) {
      if (!a.has(key)) added.add(key);
      else if (a.get(key) !== value) changed.add(key);
    }
    for (const key of a.keys()) if (!b.has(key)) removed.add(key);
  }
  return { ajoutees: [...added].sort(), supprimees: [...removed].sort(), modifiees: [...changed].filter((k) => !added.has(k) && !removed.has(k)).sort() };
}

/**
 * PR ouvertes qui touchent aux fichiers de chaînes, et clés qu'elles ajoutent, suppriment ou modifient (par rapport à leur point de départ).
 * Lit git et appelle `gh pr list` + `git fetch origin refs/pull/N/head` (réseau). Méthode : voir docs/i18n/procedure-lot4.md §3.
 * @param {string} root  copie de travail git
 * @param {{base?: string, extraRefs?: string[], prs?: object[]}} options  `prs` : liste déjà obtenue (tests) ; `extraRefs` : branches sans PR à inclure
 */
export function computeOpenPrKeyChanges(root, { base = 'origin/main', extraRefs = [], prs } = {}) {
  const watched = Object.values(GEL_FILES);
  const list = prs ?? JSON.parse(execFileSync('gh', ['pr', 'list', '--state', 'open', '--limit', '200', '--json', 'number,title,headRefName,files'], { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }));
  const result = [];
  const baseSha = git(root, ['rev-parse', base]).trim();
  const analyse = (meta, sha) => {
    const mergeBase = git(root, ['merge-base', base, sha]).trim();
    const before = {};
    const after = {};
    for (const [kind, path] of Object.entries(GEL_FILES)) {
      before[kind] = gitShow(root, mergeBase, path);
      after[kind] = gitShow(root, sha, path);
    }
    const diff = diffKeys(before, after);
    if (diff.ajoutees.length + diff.supprimees.length + diff.modifiees.length === 0) return;
    result.push({ ...meta, sha: sha.slice(0, 10), pointDeDepart: mergeBase.slice(0, 10), ...diff });
  };
  for (const pr of list) {
    const touches = (pr.files ?? []).some((f) => watched.includes(f.path));
    if (!touches) continue;
    git(root, ['fetch', '--quiet', 'origin', `refs/pull/${pr.number}/head`]);
    const sha = git(root, ['rev-parse', 'FETCH_HEAD']).trim();
    analyse({ numero: pr.number, titre: pr.title, branche: pr.headRefName }, sha);
  }
  for (const ref of extraRefs) {
    const sha = git(root, ['rev-parse', ref]).trim();
    analyse({ numero: null, titre: `branche ${ref} (sans PR)`, branche: ref }, sha);
  }
  result.sort((a, b) => (a.numero ?? 1e9) - (b.numero ?? 1e9) || a.branche.localeCompare(b.branche));
  return { base, baseSha: baseSha.slice(0, 10), prs: result };
}

// ---------------------------------------------------------------------------------------------------------------------
// Contrôle de forme des traductions
// ---------------------------------------------------------------------------------------------------------------------

/** Valeur de traduction pour une variante de plateforme : un texte de texte commun divergent peut être donné par plateforme. */
export function translationForVariant(entry, platform, value) {
  if (entry.divergent && value !== null && typeof value === 'object' && !Array.isArray(value)) {
    const keys = Object.keys(value);
    if (keys.length > 0 && keys.every((k) => k === 'android' || k === 'ios')) return value[platform];
  }
  return value;
}

const toModel = (kind, value) => (value === null || value === undefined ? null : modelFromValue(kind, value).model ?? null);

/** Variante d'une entrée pour une plateforme : {platform, kind, fr, en} (fr et en = modèles). */
export function platformVariant(entry, platform) {
  if (platform === 'ios' && entry.divergent?.ios) {
    const d = entry.divergent.ios;
    return { platform, kind: d.type, fr: toModel(d.type, d.fr), en: toModel(d.type, d.en) };
  }
  return { platform, kind: entry.type, fr: toModel(entry.type, entry.fr), en: toModel(entry.type, entry.en) };
}

/** Variantes à CONTRÔLER : une seule quand le texte est le même sur les deux plateformes (communs sans divergence). */
export function variantsOf(entry) {
  const variants = [];
  if (entry.plateformes.includes('android')) variants.push(platformVariant(entry, 'android'));
  if (entry.plateformes.includes('ios') && (entry.divergent?.ios || !entry.plateformes.includes('android'))) variants.push(platformVariant(entry, 'ios'));
  return variants;
}

const WORD_BRAND = /\bmiff\b/giu;

function exchangeExtraChecks({ lang, key, model, registry, add, invariable }) {
  const language = registry.languages.find((l) => l.code === lang);
  for (const text of model.texts) {
    const value = text.value;
    const where = text.id ? ` [${text.id}]` : '';
    if (/&(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);/.test(value)) add('entite-xml', 'error', key, `entité XML dans le texte${where} : écrire le caractère lui-même (le script encode pour Android)`);
    if (value.includes('\\')) add('barre-oblique', 'error', key, `barre oblique inverse dans le texte${where} : écrire le caractère lui-même (apostrophe, guillemet, retour à la ligne)`);
    if (/^\s|\s$| {2,}/.test(value)) add('espaces', 'error', key, `espace en début ou fin de texte, ou deux espaces de suite${where}`);
    if (value.includes('—')) add('tiret-long', 'error', key, `tiret long « — » interdit par le guide de style${where}`);
    if (value.includes('"')) add('guillemet-droit', 'warning', key, `guillemet droit « " »${where} : utiliser les guillemets de la langue (guide §2.1)`);
    if (!invariable && lang === 'it' && value.includes("'")) add('apostrophe', 'warning', key, `apostrophe droite en italien${where} : écrire ’ (U+2019)`);
    if (!invariable && ['es', 'pt', 'de'].includes(lang) && /['’]/u.test(value) && !/^[\p{L}]+['’]s\b/u.test(value)) add('apostrophe', 'warning', key, `apostrophe${where} : cette langue n'en utilise pas (guide §2.1)`);
    if (placeholderSignatures(value).length > 0 && strayPercents(value) > 0) add('pourcent-isole', 'error', key, `« % » isolé dans un texte à valeurs insérées${where} : écrire %%`);
    // Pourcentage et unités : les valeurs insérées sont remplacées par 0 pour que « %1$d % » se lise comme « 0 % ».
    const probe = value.replace(SCAN, (all) => (all.startsWith('%%') ? '%' : '0'));
    if (language && ['es', 'de'].includes(lang) && /\d(?: | )?%/u.test(probe) && !/\d %/u.test(probe) && !/\d %/u.test(probe)) add('pourcentage', 'error', key, `pourcentage${where} : espace insécable (U+00A0) entre le nombre et « % » en ${lang}`);
    if (['it', 'pt'].includes(lang) && /\d[   ]%/u.test(probe)) add('pourcentage', 'error', key, `pourcentage${where} : pas d'espace entre le nombre et « % » en ${lang} (« 30% »)`);
    if (!invariable && /\d (?:h|min|m|km|s|Min\.|Std\.)(?![\p{L}])/u.test(probe)) add('unite-espace', 'warning', key, `espace ordinaire entre un nombre et son unité${where} : utiliser une espace insécable (U+00A0)`);
    if (lang === 'es') {
      const count = (c) => [...value].filter((x) => x === c).length;
      if (count('¿') !== count('?') || count('¡') !== count('!')) add('es-ponctuation', 'warning', key, `« ¿ ? » ou « ¡ ! » non appariés${where}`);
      if (/[¿¡] /u.test(value)) add('es-ponctuation', 'warning', key, `espace après « ¿ » ou « ¡ »${where}`);
    }
    for (const m of value.matchAll(WORD_BRAND)) {
      if (m[0] !== 'Miff') add('marque', 'error', key, `la marque s'écrit « Miff » (trouvé « ${m[0]} »)${where}`);
    }
    if (/\bMiff (?:Plus|Premium)\b/u.test(value)) add('marque', 'error', key, `« Miff+ » s'écrit avec le signe plus (jamais « Miff Plus » ni « Miff Premium »)${where}`);
  }
}

/**
 * Contrôle de forme d'un fichier de traductions.
 * @param {object} p
 * @param {object} p.registry
 * @param {object} p.config
 * @param {Map<string, object>} p.byKey  entrées d'échange par clé
 * @param {string} p.lang
 * @param {Record<string, unknown>} p.translations  clé -> valeur
 * @param {boolean} [p.complete]  vrai : une clé attendue mais absente est une erreur
 * @param {string[]} [p.expected]  clés attendues (défaut : toutes les clés d'échange)
 */
/** Texte déclaré invariable (config.json -> invariables) pour cette langue. */
export function isDeclaredInvariable(config, key, lang) {
  return config.invariables.cles.includes(key) || (config.invariables.parLangue?.[lang] ?? []).includes(key);
}

export function checkTranslations({ registry, config: rawConfig, byKey, lang, translations, complete = false, expected }) {
  const config = normalizeConfig(rawConfig);
  const language = registry.languages.find((l) => l.code === lang);
  if (!language) throw new Error(`langue « ${lang} » absente du registre`);
  if (lang === registry.sourceLanguage || lang === registry.fallbackLanguage) throw new Error(`« ${lang} » est la langue source ou de repli : rien à traduire`);
  const findings = [];
  const unknown = [];
  const records = { android: new Map(), ios: new Map() };
  const addFinding = (platform) => (check, level, key, message) => {
    if (isException(registry, { lang, platform, key, check })) return;
    findings.push({ lang, platform, key, check, level, message });
  };

  for (const [key, value] of Object.entries(translations)) {
    const entry = byKey.get(key);
    if (!entry) {
      unknown.push(key);
      continue;
    }
    for (const variant of variantsOf(entry)) {
      const add = addFinding(variant.platform);
      const raw = translationForVariant(entry, variant.platform, value);
      if (raw === undefined) {
        add('forme', 'error', key, `texte divergent entre Android et iOS : fournir {"android": …, "ios": …} (ou un seul texte pour les deux)`);
        continue;
      }
      const { model, error } = modelFromValue(variant.kind, raw);
      if (error) {
        add('forme', 'error', key, `forme du texte incorrecte : ${error}`);
        continue;
      }
      if (!variant.fr) {
        add('forme', 'error', key, 'français de référence illisible (fichier d\'échange à régénérer)');
        continue;
      }
      records[variant.platform].set(key, { entry, variant, model });
    }
  }

  const stats = {};
  for (const platform of ['android', 'ios']) {
    const table = records[platform];
    if (table.size === 0) continue;
    const reference = new Set(table.keys());
    const localization = (code, key) => {
      const record = table.get(key);
      if (!record) return null;
      if (code === lang) return record.model;
      if (code === registry.sourceLanguage) return record.variant.fr;
      if (code === registry.fallbackLanguage) return record.variant.en;
      return null;
    };
    const { findings: raw, stats: tableStats } = checkLanguageTable({
      registry, language, platform, table: `echange-${platform}`, referenceKeys: reference, localization, extraKeys: () => [],
    });
    stats[platform] = tableStats;
    for (const f of raw) {
      const { entry } = table.get(f.key);
      let { level } = f;
      if (f.check === 'identical-to-source') {
        // Texte déclaré invariable (config.json -> invariables) ou motif de format : aucun constat.
        if (entry.motif || entry.interne || isDeclaredInvariable(config, f.key, lang)) continue;
        level = entry.invariableProbable ? 'warning' : 'error';
      } else if (f.check === 'high-punctuation' || f.check === 'plural-recommended') level = 'error';
      else if (f.check === 'length' || f.check === 'short-label-budget') level = 'warning';
      findings.push({ lang, platform, key: f.key, check: f.check, level, message: f.message });
    }
    for (const [key, { model }] of table) {
      exchangeExtraChecks({ lang, key, model, registry, add: addFinding(platform), invariable: isDeclaredInvariable(config, key, lang) });
    }
  }

  // Motifs de format imposés (heures et dates du Feed).
  for (const [key, forced] of Object.entries(config.motifs)) {
    if (!(key in translations) || forced?.[lang] === undefined) continue;
    const value = translations[key];
    if (typeof value === 'string' && value !== forced[lang]) {
      findings.push({ lang, platform: 'android', key, check: 'motif', level: 'error', message: `motif de format : « ${forced[lang]} » attendu (guide de style §3), trouvé « ${value} »` });
    }
  }

  // Les textes « internes » (écran de diagnostic) restent en français et en anglais : leurs clés ne sont pas attendues.
  const expectedKeys = expected ?? [...byKey.values()].filter((e) => !e.interne).map((e) => e.cle);
  const missing = expectedKeys.filter((key) => !(key in translations)).sort();
  if (complete) for (const key of missing) findings.push({ lang, platform: byKey.get(key)?.plateformes[0] ?? 'android', key, check: 'missing-key', level: 'error', message: 'clé absente du fichier de traductions' });
  for (const key of unknown) findings.push({ lang, platform: 'android', key, check: 'unknown-key', level: 'warning', message: 'clé inconnue du fichier d\'échange (ignorée à la réinjection)' });

  findings.sort((a, b) => a.key.localeCompare(b.key) || a.check.localeCompare(b.check) || a.platform.localeCompare(b.platform));
  const errors = findings.filter((f) => f.level === 'error').length;
  const ratios = Object.fromEntries(Object.entries(stats).map(([platform, s]) => [platform, s.lengthRatioVsFallback]));
  return { findings, errors, warnings: findings.length - errors, missing, unknown, translated: Object.keys(translations).length - unknown.length, lengthRatioVsFallback: ratios };
}

/** Rapport du plus long au plus court par rapport à l'anglais : les `count` textes les plus longs (rapport > 1,8). */
export function longestVersusEnglish(byKey, translations, count = 10) {
  const rows = [];
  for (const [key, value] of Object.entries(translations)) {
    const entry = byKey.get(key);
    if (!entry || typeof value !== 'string' || typeof entry.en !== 'string' || entry.en.length < 10) continue;
    rows.push({ cle: key, rapport: Math.round((value.length / entry.en.length) * 100) / 100, longueur: value.length, longueurEn: entry.en.length });
  }
  return rows.sort((a, b) => b.rapport - a.rapport || a.cle.localeCompare(b.cle)).slice(0, count);
}

// ---------------------------------------------------------------------------------------------------------------------
// Réinjection dans Android
// ---------------------------------------------------------------------------------------------------------------------

const ANDROID_HEADER = (lang, nativeName) => `\n    ${nativeName.toUpperCase()} (${lang}) : traductions fusionnées depuis le fichier d'échange docs/i18n/echange/traduction-${lang}.json par\n    scripts/i18n-exchange.mjs (jamais à la main : voir docs/i18n/procedure-lot4.md). Mêmes clés que values-fr/strings.xml.\n`;

/**
 * Texte du `values-<langue>/strings.xml` : ordre, légendes de section et attributs du français ; valeurs = traductions fournies, sinon valeurs déjà présentes.
 * Les textes français et anglais ne sont jamais lus en écriture. Un fichier existant garde les clés que les traductions ne mentionnent pas.
 * @param {object} p
 * @param {string} p.frXml  values-fr/strings.xml
 * @param {string|null} p.existingXml  values-<langue>/strings.xml actuel (ou null)
 * @param {string} p.lang
 * @param {string} p.nativeName
 * @param {Map<string, object>} p.models  clé -> modèle de la traduction pour Android (valeurs déjà en notation Android)
 * @returns {{xml: string, written: number, unchanged: number, kept: number, extra: string[]}}
 */
export function buildAndroidStrings({ frXml, existingXml, lang, nativeName, models }) {
  const fr = parseAndroidStructure(frXml);
  const existing = existingXml ? parseAndroidStructure(existingXml) : { items: [] };
  const existingByKey = new Map(existing.items.filter((i) => i.type !== 'comment').map((i) => [i.name, i]));
  const frKeys = new Set(fr.items.filter((i) => i.type !== 'comment').map((i) => i.name));
  const lines = [];
  let pendingComments = [];
  let written = 0;
  let unchanged = 0;
  let kept = 0;
  const attrs = (item) => item.extra.map(([k, v]) => ` ${k}="${v}"`).join('');
  const flush = () => {
    if (pendingComments.length === 0) return;
    if (lines.length > 0) lines.push('');
    for (const c of pendingComments) lines.push(`    <!-- ${c} -->`);
    pendingComments = [];
  };

  for (const item of fr.items) {
    if (item.type === 'comment') {
      pendingComments.push(item.text);
      continue;
    }
    if (item.type === 'array' || !item.translatable) continue;
    const model = models.get(item.name);
    const old = existingByKey.get(item.name);
    if (!model && !old) continue;
    flush();
    if (item.type === 'string') {
      const raw = model ? encodeAndroidValue(model.texts[0].value) : old.raw;
      if (model) {
        written += 1;
        if (old && old.raw === raw) unchanged += 1;
      } else kept += 1;
      lines.push(`    <string name="${item.name}"${attrs(item)}>${raw}</string>`);
    } else {
      const forms = model
        ? PLURAL_ORDER.map((c) => model.texts.find((t) => t.id === c)).filter(Boolean).map((t) => ({ quantity: t.id, raw: encodeAndroidValue(t.value) }))
        : old.items;
      if (model) {
        written += 1;
        if (old?.items && JSON.stringify(old.items.map((i) => [i.quantity, i.raw])) === JSON.stringify(forms.map((i) => [i.quantity, i.raw]))) unchanged += 1;
      } else kept += 1;
      lines.push(`    <plurals name="${item.name}"${attrs(item)}>`);
      for (const f of forms) lines.push(`        <item quantity="${f.quantity}">${f.raw}</item>`);
      lines.push('    </plurals>');
    }
  }
  // Clés déjà présentes dans le fichier de la langue mais inconnues du français : conservées à la fin (le script de parité les signale).
  const extra = [...existingByKey.keys()].filter((k) => !frKeys.has(k));
  if (extra.length > 0) {
    lines.push('', '    <!-- Clés absentes du français (à supprimer) -->');
    for (const key of extra) {
      const old = existingByKey.get(key);
      if (old.type === 'string') lines.push(`    <string name="${key}"${attrs(old)}>${old.raw}</string>`);
      else {
        lines.push(`    <plurals name="${key}"${attrs(old)}>`);
        for (const f of old.items) lines.push(`        <item quantity="${f.quantity}">${f.raw}</item>`);
        lines.push('    </plurals>');
      }
    }
  }
  const xml = `<?xml version="1.0" encoding="utf-8"?>\n<!--${ANDROID_HEADER(lang, nativeName)}-->\n<resources xmlns:tools="http://schemas.android.com/tools">\n${lines.join('\n')}\n</resources>\n`;
  return { xml, written, unchanged, kept, extra };
}

// ---------------------------------------------------------------------------------------------------------------------
// Réinjection dans iOS
// ---------------------------------------------------------------------------------------------------------------------

/** Texte JSON canonique d'un String Catalog (celui qu'écrit Xcode : indentation de 2, saut de ligne final). */
export const stringifyCatalog = (catalog) => `${JSON.stringify(catalog, null, 2)}\n`;

const unit = (value) => ({ stringUnit: { state: 'translated', value } });

/** Localisation iOS d'une traduction : texte simple, variations de pluriel ou substitutions (arguments recopiés du français). */
export function iosLocalization(model, frLocalization, typeMap) {
  const text = (value) => toIosPlaceholders(value, typeMap);
  if (model.kind === 'string') return unit(text(model.texts[0].value));
  if (model.kind === 'plural') {
    const plural = {};
    for (const c of PLURAL_ORDER) {
      const t = model.texts.find((x) => x.id === c);
      if (t) plural[c] = unit(text(t.value));
    }
    return { variations: { plural } };
  }
  const result = { ...unit(text(model.texts.find((t) => t.id === '').value)), substitutions: {} };
  for (const [arg, frSub] of Object.entries(frLocalization?.substitutions ?? {})) {
    const plural = {};
    for (const c of PLURAL_ORDER) {
      const t = model.texts.find((x) => x.id === `${arg}:${c}`);
      if (t) plural[c] = unit(text(t.value));
    }
    result.substitutions[arg] = { argNum: frSub.argNum, formatSpecifier: frSub.formatSpecifier, variations: { plural } };
  }
  return result;
}

/**
 * Ajoute (ou remplace) la langue `iosCode` dans un String Catalog, sans toucher aux autres langues. Les clés absentes des traductions restent telles quelles.
 * @param {string} catalogText  contenu actuel du catalogue
 * @param {string} iosCode
 * @param {Map<string, object>} models  clé -> modèle de la traduction
 * @returns {{text: string, written: number, unchanged: number, ignored: {cle: string, raison: string}[]}}
 */
export function applyIosCatalog(catalogText, iosCode, models) {
  const catalog = JSON.parse(catalogText);
  if (stringifyCatalog(catalog) !== catalogText) {
    throw new Error('le catalogue iOS n\'est pas au format Xcode attendu (JSON indenté de 2, saut de ligne final) : le script refuse de le réécrire');
  }
  let written = 0;
  let unchanged = 0;
  const ignored = [];
  for (const [key, model] of models) {
    const entry = catalog.strings?.[key];
    if (!entry) {
      ignored.push({ cle: key, raison: 'clé absente du catalogue iOS' });
      continue;
    }
    if (entry.shouldTranslate === false) {
      ignored.push({ cle: key, raison: 'ressource non traduisible (shouldTranslate: false)' });
      continue;
    }
    const frLoc = entry.localizations?.fr;
    const typeMap = iosTypeMap((catalogLocalization(entry, 'fr')?.texts ?? []).map((t) => t.value));
    const localization = iosLocalization(model, frLoc, typeMap);
    const before = JSON.stringify(entry.localizations?.[iosCode] ?? null);
    entry.localizations = { ...(entry.localizations ?? {}), [iosCode]: localization };
    // Ordre alphabétique des langues, comme Xcode (en, fr, puis es, it...).
    entry.localizations = Object.fromEntries(Object.keys(entry.localizations).sort().map((k) => [k, entry.localizations[k]]));
    written += 1;
    if (before === JSON.stringify(localization)) unchanged += 1;
  }
  return { text: stringifyCatalog(catalog), written, unchanged, ignored };
}

// ---------------------------------------------------------------------------------------------------------------------
// Réinjection : préparation des modèles par plateforme
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Modèles à écrire par plateforme à partir des traductions.
 * Textes communs : notation canonique en entrée, convertie pour chaque plateforme. Textes propres : notation native, laissée telle quelle.
 * @returns {{android: Map<string, object>, ios: Map<string, object>, infoPlist: Map<string, object>, skipped: {cle: string, raison: string}[]}}
 */
export function prepareModels(byKey, translations) {
  const android = new Map();
  const ios = new Map();
  const infoPlist = new Map();
  const skipped = [];
  const mapTexts = (model, fn) => ({ kind: model.kind, texts: model.texts.map((t) => ({ id: t.id, value: fn(t.value) })) });
  for (const [key, value] of Object.entries(translations)) {
    const entry = byKey.get(key);
    if (!entry) {
      skipped.push({ cle: key, raison: 'clé inconnue du fichier d\'échange' });
      continue;
    }
    for (const platform of entry.plateformes) {
      const variant = platformVariant(entry, platform);
      const raw = translationForVariant(entry, variant.platform, value);
      const { model, error } = modelFromValue(variant.kind, raw);
      if (error) {
        skipped.push({ cle: key, raison: error });
        continue;
      }
      const common = entry.plateformes.length === 2;
      if (variant.platform === 'android') android.set(key, common ? mapTexts(model, canonicalPlaceholders) : model);
      else if (entry.table) infoPlist.set(key, model);
      else ios.set(key, model);
    }
  }
  return { android, ios, infoPlist, skipped };
}

export { androidLocalization, stripXmlComments };
