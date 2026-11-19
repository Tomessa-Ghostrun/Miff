// Contrôles de traduction par langue (Android et iOS) pour scripts/check-i18n-parity.mjs.
// Chaque contrôle produit des « constats » {lang, platform, table, check, key, level, message}. `level` est la gravité naturelle du
// constat (`error` ou `warning`) ; c'est le STATUT de la langue dans le registre (docs/contracts/i18n-languages.json) qui décide
// si une erreur bloque : `required` = bloquant, `planned` = tout devient avertissement.

import {
  androidEscapeProblems, catalogLocalization, decodeAndroidValue, describeControlChar, meaningfulNbspCount, placeholderSignatures, withoutPlaceholders,
} from './i18n-resources.mjs';

export const CHECKS = {
  'missing-key': 'clé manquante',
  'extra-key': 'clé en trop',
  structure: 'structure différente du français (chaîne / pluriel)',
  empty: 'texte vide',
  todo: 'marqueur à traduire',
  state: 'état de traduction iOS non « translated »',
  'control-char': 'caractère de contrôle ou de direction',
  placeholders: 'valeurs insérées différentes du français',
  'plural-forms': 'formes de pluriel inconnues, manquantes ou en double',
  'plural-recommended': 'forme de pluriel recommandée absente',
  'android-escape': 'échappement Android incorrect',
  'nbsp-lost': 'espace insécable du français perdue',
  'high-punctuation': 'espace insécable avant ? ! : ; (inutile dans cette langue)',
  'gender-mark': 'marque de genre',
  'formal-address': 'vouvoiement',
  'forbidden-term': 'terme proscrit',
  'identical-to-source': 'identique au français',
  length: 'plus long que prévu par rapport au français',
  'short-label-budget': 'étiquette courte au-dessus du budget',
};

const TODO_MARKER = /\b(TODO|TBD|FIXME|XXX|TRANSLATE_ME|TO_TRANSLATE)\b|\?\?\?/;
// Marques de genre : (e) (a) (o) (x) (in), o/a, -a/-o, @ inclusif, formes allemandes avec * : _ / ou « Binnen-I ».
const GENDER_MARKS = [
  /\((?:e|a|o|x|in|innen)\)/iu,
  /\b\p{L}+o\/a\b/iu,
  /(?<![\p{L}\d])o\/a\b/iu,
  // « y/o » (espagnol), « e/o » (italien) = « et/ou » : une lettre seule avant la barre n'est pas une marque de genre.
  /(?!(?<![\p{L}\d])[yYeE]\/)\p{L}\/[ao]s?\b/u,
  /\p{L}@(?![\p{L}\d.-]+\.\p{L}{2,})/u,
  /\p{L}[*:_]in(?:nen)?\b/u,
  /\p{L}\/in(?:nen)?\b/u,
  /\p{Ll}Inn?en\b/u,
];

const hasLetters = (text) => /\p{L}/u.test(text);
const codePoints = (text) => [...text].length;

/** Compile les motifs de clé du registre. */
export function compilePatterns(patterns) {
  return (patterns ?? []).map((p) => new RegExp(p, 'u'));
}

/** Regex d'un terme du registre : objet `{pattern, flags}` ou mot simple (mot entier, sans casse). */
export function termRegex(term) {
  if (typeof term === 'string') return new RegExp(`(?<![\\p{L}\\d])${term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![\\p{L}\\d])`, 'iu');
  return new RegExp(term.pattern, term.flags ?? 'u');
}

function isException(registry, { lang, platform, key, check, match }) {
  return (registry.exceptions ?? []).some((e) => e.lang === lang && e.check === check
    && (e.platform === undefined || e.platform === platform)
    && (e.match === undefined || e.match === match)
    && ((e.key !== undefined && e.key === key) || (e.keyPattern !== undefined && new RegExp(e.keyPattern, 'u').test(key))));
}

const unionOf = (texts, sign) => new Set(texts.flatMap((t) => sign(t.value)));
const sameSet = (a, b) => a.size === b.size && [...a].every((x) => b.has(x));
const planCategory = (id) => (id.includes(':') ? id.slice(id.indexOf(':') + 1) : id);

function describeSignatures(set) {
  return set.size === 0 ? 'aucune' : [...set].sort().join(', ');
}

/**
 * Contrôles d'une table (Android, iOS ou InfoPlist) pour une langue.
 * @param {object} p
 * @param {object} p.registry
 * @param {object} p.language  entrée du registre
 * @param {'android'|'ios'} p.platform
 * @param {string} p.table  identifiant de table (`android`, `ios`, `ios-infoplist`)
 * @param {Set<string>} p.referenceKeys  clés attendues
 * @param {(lang: string, key: string) => ({kind: string, texts: {id: string, value: string, raw?: string, state?: string|null}[]} | null)} p.localization
 * @param {() => string[]} p.extraKeys  clés présentes dans la langue mais pas dans la référence
 */
export function checkLanguageTable({ registry, language, platform, table, referenceKeys, localization, extraKeys }) {
  const lang = language.code;
  const source = registry.sourceLanguage;
  const isSource = lang === source;
  const findings = [];
  const present = new Set();
  const add = (check, level, key, message, match) => {
    if (isException(registry, { lang, platform, key, check, match })) return;
    findings.push({ lang, platform, table, check, key, level, message, ...(match === undefined ? {} : { match }) });
  };

  const shortLabelPatterns = compilePatterns(registry.shortLabelKeyPatterns);
  const nbspRequired = compilePatterns(registry.nbspRequiredKeyPatterns);
  const ratio = registry.lengthRatio ?? { maxVsSource: 1.5, minSourceLength: 10, shortLabelExtra: 0.4 };
  const brand = (registry.brandTerms ?? []).map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
  const brandRegex = brand ? new RegExp(brand, 'gu') : null;
  const formal = (language.style?.formalForms ?? []).map(termRegex);
  const forbidden = (language.style?.forbiddenTerms ?? []).map(termRegex);
  const plurals = language.plurals ?? { required: ['one', 'other'], allowed: ['one', 'other'], recommended: [] };
  let ratioChars = 0;
  let ratioEnChars = 0;
  const fallback = registry.fallbackLanguage;

  for (const key of [...referenceKeys].sort()) {
    const loc = localization(lang, key);
    if (!loc) {
      add('missing-key', 'error', key, 'clé absente');
      continue;
    }
    present.add(key);
    const ref = isSource ? loc : localization(source, key);
    if (loc.kind === 'unsupported') {
      add('structure', 'warning', key, 'variation iOS non analysée par le script (hors plurals et substitutions)');
      continue;
    }

    if (ref && !isSource && ref.kind !== 'unsupported') {
      if (ref.kind !== loc.kind) {
        const label = { string: 'texte simple', plural: 'pluriel', substituted: 'texte à substitutions', array: 'tableau' };
        add('structure', 'error', key, `${label[ref.kind] ?? ref.kind} attendu comme en ${source}, trouvé : ${label[loc.kind] ?? loc.kind}`);
      }
    }

    // --- Texte par texte
    for (const text of loc.texts) {
      const where = text.id ? ` [${text.id}]` : '';
      const value = text.value;
      if (value.trim() === '') add('empty', 'error', key, `texte vide${where}`);
      if (TODO_MARKER.test(value)) add('todo', 'error', key, `marqueur « à traduire » dans le texte${where}`);
      if (text.state != null && text.state !== 'translated') add('state', 'error', key, `état « ${text.state} »${where}`);
      const control = describeControlChar(value);
      if (control) add('control-char', 'error', key, `caractère ${control}${where}`);
      if (platform === 'android' && text.raw !== undefined) {
        for (const problem of androidEscapeProblems(text.raw)) add('android-escape', 'error', key, `${problem}${where}`);
      }
      if (language.style?.spaceBeforeHighPunctuation === 'none' && /[\u00A0\u202F](?=[?!:;])/u.test(value)) {
        add('high-punctuation', 'warning', key, `espace insécable avant la ponctuation${where}`);
      }
      const styled = withoutPlaceholders(value);
      if (language.style?.forbidGenderMarks) {
        const mark = GENDER_MARKS.map((r) => r.exec(styled)).find(Boolean);
        if (mark) add('gender-mark', 'error', key, `marque de genre « ${mark[0]} »${where}`, mark[0]);
      }
      for (const regex of formal) {
        const hit = regex.exec(styled);
        if (hit) add('formal-address', 'error', key, `forme de vouvoiement « ${hit[0]} »${where} (tutoiement attendu)`, hit[0]);
      }
      for (const regex of forbidden) {
        const hit = regex.exec(styled);
        if (hit) add('forbidden-term', 'error', key, `terme proscrit « ${hit[0]} »${where}`, hit[0]);
      }
    }

    // --- Pluriels
    if (loc.kind === 'plural') {
      const categories = loc.texts.map((t) => planCategory(t.id));
      const problems = [];
      const seen = new Set();
      for (const c of categories) {
        if (seen.has(c)) problems.push(`forme « ${c} » en double`);
        seen.add(c);
        if (!plurals.allowed.includes(c)) problems.push(`forme « ${c} » inconnue pour ${lang} (autorisées : ${plurals.allowed.join(', ')})`);
      }
      for (const c of plurals.required) if (!seen.has(c)) problems.push(`forme « ${c} » obligatoire absente`);
      if (problems.length > 0) add('plural-forms', 'error', key, problems.join(' ; '));
      const missingRecommended = (plurals.recommended ?? []).filter((c) => !seen.has(c) && !plurals.required.includes(c));
      if (missingRecommended.length > 0) add('plural-recommended', 'warning', key, `forme(s) recommandée(s) absente(s) : ${missingRecommended.join(', ')} (non exigée)`);
    }

    if (!ref || isSource || ref.kind === 'unsupported') continue;

    // --- Valeurs insérées
    const sign = (t) => placeholderSignatures(t);
    if (loc.kind === 'string' && ref.kind === 'string') {
      const a = sign(ref.texts[0].value).join(' ');
      const b = sign(loc.texts[0].value).join(' ');
      if (a !== b) add('placeholders', 'error', key, `valeurs insérées attendues « ${a || 'aucune'} », trouvées « ${b || 'aucune'} »`);
    } else if (loc.kind !== 'string' && ref.kind !== 'string') {
      const refUnion = unionOf(ref.texts, sign);
      const locUnion = unionOf(loc.texts, sign);
      if (!sameSet(refUnion, locUnion)) {
        add('placeholders', 'error', key, `valeurs insérées attendues {${describeSignatures(refUnion)}}, trouvées {${describeSignatures(locUnion)}}`);
      }
      // Les formes « autre » (other, many) portent le nombre comme le français ; seule la forme « one » peut s'en passer (« Un membre »).
      const refById = new Map(ref.texts.map((t) => [t.id, t]));
      for (const text of loc.texts) {
        const category = planCategory(text.id);
        const signs = sign(text.value);
        const extra = signs.filter((x) => !refUnion.has(x));
        if (extra.length > 0) add('placeholders', 'error', key, `valeur(s) insérée(s) inconnue(s) {${extra.join(', ')}} [${text.id}]`);
        if (category === 'other' || category === 'many') {
          const refText = refById.get(text.id) ?? refById.get(text.id.includes(':') ? `${text.id.split(':')[0]}:other` : 'other');
          if (refText && sign(refText.value).join(' ') !== signs.join(' ')) {
            add('placeholders', 'error', key, `forme [${text.id}] : valeurs insérées attendues « ${sign(refText.value).join(' ') || 'aucune'} », trouvées « ${signs.join(' ') || 'aucune'} »`);
          }
        }
      }
    }

    // --- Espaces insécables
    const refNbsp = ref.texts.reduce((n, t) => n + meaningfulNbspCount(t.value), 0);
    const locNbsp = loc.texts.reduce((n, t) => n + meaningfulNbspCount(t.value), 0);
    if (refNbsp > 0 && locNbsp === 0) {
      const required = nbspRequired.some((r) => r.test(key));
      add('nbsp-lost', required ? 'error' : 'warning', key, `le ${source} a ${refNbsp} espace(s) insécable(s) entre une valeur et son unité ou un mot, aucune ici${required ? ' (durée : à conserver)' : ''}`);
    }

    // --- Identique au français, longueurs
    const byId = new Map(ref.texts.map((t) => [t.id, t.value]));
    for (const text of loc.texts) {
      const refValue = byId.get(text.id) ?? byId.get('other') ?? byId.get(`${text.id.split(':')[0]}:other`);
      if (refValue === undefined) continue;
      const strip = (s) => withoutPlaceholders(brandRegex ? s.replace(brandRegex, '') : s);
      if (text.value === refValue && hasLetters(strip(text.value))) {
        add('identical-to-source', 'warning', key, `texte identique au ${source}${text.id ? ` [${text.id}]` : ''} : « ${text.value.length > 60 ? `${text.value.slice(0, 57)}...` : text.value} »`);
        break;
      }
    }
    const isShort = loc.kind === 'string' && shortLabelPatterns.some((r) => r.test(key));
    for (const text of loc.texts) {
      const refValue = byId.get(text.id) ?? byId.get('other');
      if (refValue === undefined) continue;
      const refLength = codePoints(refValue);
      const length = codePoints(text.value);
      const base = Math.max(refLength, ratio.minSourceLength);
      if (isShort) {
        const budget = registry.lengthBudgets?.[key] ?? Math.ceil(base * (1 + ratio.shortLabelExtra));
        if (length > budget) add('short-label-budget', 'warning', key, `étiquette courte : ${length} caractères pour un budget de ${budget} (${source} : ${refLength})`);
      } else if (length > base * ratio.maxVsSource) {
        add('length', 'warning', key, `${length} caractères contre ${refLength} en ${source} (plus de ${ratio.maxVsSource} fois)${text.id ? ` [${text.id}]` : ''}`);
      }
    }

    // --- Longueur relative à l'anglais (textes simples)
    if (loc.kind === 'string' && fallback !== lang) {
      const en = localization(fallback, key);
      if (en?.kind === 'string' && en.texts[0].value.length > 0) {
        ratioChars += codePoints(loc.texts[0].value);
        ratioEnChars += codePoints(en.texts[0].value);
      }
    }
  }

  const expectedExtra = extraKeys ? extraKeys() : [];
  for (const key of expectedExtra) add('extra-key', 'error', key, `clé absente du ${source} (à supprimer ou à ajouter au ${source})`);

  return {
    findings,
    stats: {
      expected: referenceKeys.size,
      present: present.size,
      missing: referenceKeys.size - present.size,
      extra: expectedExtra.length,
      lengthRatioVsFallback: ratioEnChars > 0 ? Math.round((ratioChars / ratioEnChars) * 100) / 100 : null,
    },
  };
}

/** Table Android d'une langue (Map issue de `parseAndroidStrings`, ou null si le dossier n'existe pas). */
export function androidLocalization(map, key) {
  const entry = map?.get(key);
  if (!entry || !entry.translatable) return null;
  return { kind: entry.kind, texts: entry.texts.map((t) => ({ id: t.id, raw: t.raw, value: decodeAndroidValue(t.raw) })) };
}

export { catalogLocalization };
