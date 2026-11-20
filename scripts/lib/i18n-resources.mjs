// Lecture des ressources de traduction (Android strings.xml, String Catalog iOS) pour scripts/check-i18n-parity.mjs.
// Aucune dépendance. Ne lit que du texte : n'écrit jamais dans les ressources.

const NAMED_ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };

/** Retire les commentaires XML. */
export function stripXmlComments(xml) {
  return xml.replace(/<!--[\s\S]*?-->/g, '');
}

/**
 * Éléments d'un strings.xml Android.
 * @returns {Map<string, {kind: 'string'|'plural'|'array', translatable: boolean, texts: {id: string, raw: string}[]}>}
 * `id` : '' pour une chaîne simple, la catégorie (`one`, `other`...) pour un pluriel, l'indice pour un tableau.
 */
export function parseAndroidStrings(xml) {
  const entries = new Map();
  const body = stripXmlComments(xml);
  for (const match of body.matchAll(/<(string|plurals|string-array)\b([^>]*?)>([\s\S]*?)<\/\1>/g)) {
    const [, tag, attributes, inner] = match;
    const name = /\bname\s*=\s*"([^"]+)"/.exec(attributes);
    if (!name) continue;
    const translatable = !/\btranslatable\s*=\s*"false"/.test(attributes);
    if (tag === 'string') {
      entries.set(name[1], { kind: 'string', translatable, texts: [{ id: '', raw: inner }] });
    } else {
      const texts = [];
      let index = 0;
      for (const item of inner.matchAll(/<item\b([^>]*?)>([\s\S]*?)<\/item>/g)) {
        const quantity = /\bquantity\s*=\s*"([^"]+)"/.exec(item[1]);
        texts.push({ id: tag === 'plurals' ? (quantity ? quantity[1] : '?') : String(index), raw: item[2] });
        index += 1;
      }
      entries.set(name[1], { kind: tag === 'plurals' ? 'plural' : 'array', translatable, texts });
    }
  }
  return entries;
}

/** Texte d'une ressource Android tel que l'affiche l'app (entités XML, échappements, guillemets englobants). */
export function decodeAndroidValue(raw) {
  let text = raw.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1');
  const trimmed = text.trim();
  const quoted = trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"');
  if (quoted) text = trimmed.slice(1, -1);
  else text = text.trim().replace(/\s+/g, ' ');
  text = text.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (all, c) => {
    if (c[0] === 'u' && c.length === 5) return String.fromCharCode(parseInt(c.slice(1), 16));
    if (c === 'n') return '\n';
    if (c === 't') return '\t';
    return c;
  });
  return text.replace(/&(#x[0-9a-fA-F]+|#\d+|[a-z]+);/g, (all, e) => {
    if (e[0] === '#') {
      const code = e[1] === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return Number.isFinite(code) ? String.fromCodePoint(code) : all;
    }
    return NAMED_ENTITIES[e] ?? all;
  });
}

/**
 * Défauts d'échappement d'une valeur Android BRUTE (celle du fichier). Chaque élément = un message.
 * - apostrophe non échappée (`\'`) hors d'une valeur entre guillemets ; guillemet droit non échappé ;
 * - `&` qui n'est pas une entité ; `<` isolé ; valeur qui commence par `@` ou `?` non échappé (référence de ressource) ;
 * - `%` isolé dans un texte qui a des valeurs insérées : il faut écrire `%%`.
 */
export function androidEscapeProblems(raw) {
  const problems = [];
  const withoutCdata = raw.replace(/<!\[CDATA\[[\s\S]*?\]\]>/g, '');
  const trimmed = withoutCdata.trim();
  const quoted = trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"');
  const inner = quoted ? trimmed.slice(1, -1) : withoutCdata;
  if (!quoted && /(?<!\\)'/.test(inner)) problems.push("apostrophe non échappée (écrire \\')");
  if (!quoted && /(?<!\\)"/.test(inner)) problems.push('guillemet droit non échappé (écrire \\")');
  if (/&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)/.test(inner)) problems.push("« & » qui n'est pas une entité (écrire &amp;)");
  if (/<(?![A-Za-z/])/.test(inner)) problems.push('« < » isolé (écrire &lt;)');
  if (/^[@?]/.test(trimmed)) problems.push('valeur qui commence par @ ou ? non échappé (écrire \\@ ou \\?)');
  // Une chaîne SANS valeur insérée n'est pas un format : un « % » seul y est toléré (30 % d'économie). Avec valeur insérée, il faut %%.
  const decoded = decodeAndroidValue(raw);
  if (placeholderSignatures(decoded).length > 0 && strayPercents(decoded) > 0) problems.push('« % » isolé dans un texte à valeurs insérées : écrire %% pour un pourcentage');
  return problems;
}

// Valeur insérée : %[n$][drapeaux][largeur][.précision][longueur]type ; %% = pourcent littéral ; %#@nom@ = substitution iOS.
// `%arg` : valeur d'une substitution iOS dans le texte d'une forme de pluriel.
export const PLACEHOLDER = /%%|%#@(\w+)@|%(arg)\b|%(?:(\d+)\$)?([-#+0,(]*)(\d+)?(?:\.\d+)?(?:hh|h|ll|l|q|L|z|t|j)?([a-zA-Z@])/g;

/** Normalise le type : `s`, `S` et `@` = texte ; `d`, `i`, `u` (avec `l`, `ll`...) = entier. */
function normalizeType(type) {
  if ('sS@'.includes(type)) return 's';
  if ('diu'.includes(type)) return 'd';
  if ('fFeEgG'.includes(type)) return 'f';
  return type.toLowerCase();
}

/**
 * Signatures des valeurs insérées d'un texte, positions implicites numérotées dans l'ordre.
 * `%1$s` et `%1$@` donnent la même signature `1s` ; `%2$02d` et `%2$02lld` donnent `2d02` ; les substitutions iOS `%#@arg@` donnent `@arg`.
 */
export function placeholderSignatures(text) {
  const result = [];
  let implicit = 0;
  for (const match of text.matchAll(PLACEHOLDER)) {
    if (match[0] === '%%') continue;
    if (match[1] !== undefined) {
      result.push(`@${match[1]}`);
      continue;
    }
    if (match[2] !== undefined) {
      result.push('arg');
      continue;
    }
    const position = match[3] !== undefined ? Number(match[3]) : (implicit += 1);
    const pad = `${match[4].includes('0') ? '0' : ''}${match[5] ?? ''}`;
    result.push(`${position}${normalizeType(match[6])}${pad}`);
  }
  return result.sort();
}

/** Texte sans ses valeurs insérées (pour les contrôles de style : un `%#@n@` ne doit pas ressembler à un @ inclusif). */
export function withoutPlaceholders(text) {
  return text.replace(PLACEHOLDER, (all) => (all === '%%' ? '%' : ' '));
}

/** Nombre de « % » qui ne forment ni une valeur insérée ni `%%`. */
export function strayPercents(text) {
  let count = 0;
  const consumed = text.replace(PLACEHOLDER, '');
  for (const char of consumed) if (char === '%') count += 1;
  return count;
}

/** Entrées d'un String Catalog iOS : {sourceLanguage, entries: Map<clé, {shouldTranslate, localizations}>}. */
export function parseCatalog(catalogText) {
  const catalog = JSON.parse(catalogText);
  if (catalog === null || typeof catalog !== 'object' || typeof catalog.strings !== 'object') {
    throw new Error('format de String Catalog inattendu : objet "strings" absent');
  }
  const entries = new Map();
  for (const [key, entry] of Object.entries(catalog.strings)) {
    entries.set(key, { shouldTranslate: entry?.shouldTranslate !== false, localizations: entry?.localizations ?? {} });
  }
  return { sourceLanguage: catalog.sourceLanguage ?? 'fr', entries };
}

const unitText = (unit) => ({ value: unit?.value ?? '', state: unit?.state ?? null });

/**
 * Une langue d'une entrée de catalogue : {kind: 'string'|'plural'|'substituted'|'unsupported', texts: [{id, value, state}]}, ou null si absente.
 * - `plural` : `variations.plural` (id = catégorie) ;
 * - `substituted` : texte avec `%#@arg@` + `substitutions` (id '' pour le modèle, `arg:catégorie` pour les formes).
 */
export function catalogLocalization(entry, lang) {
  const loc = entry.localizations?.[lang];
  if (!loc || typeof loc !== 'object') return null;
  if (loc.variations?.plural) {
    const texts = Object.entries(loc.variations.plural).map(([category, form]) => ({ id: category, ...unitText(form?.stringUnit) }));
    return { kind: 'plural', texts };
  }
  if (loc.substitutions && typeof loc.substitutions === 'object') {
    const texts = [{ id: '', ...unitText(loc.stringUnit) }];
    for (const [name, sub] of Object.entries(loc.substitutions)) {
      for (const [category, form] of Object.entries(sub?.variations?.plural ?? {})) {
        texts.push({ id: `${name}:${category}`, ...unitText(form?.stringUnit) });
      }
    }
    return { kind: 'substituted', texts };
  }
  if (loc.variations) return { kind: 'unsupported', texts: [] };
  return { kind: 'string', texts: [{ id: '', ...unitText(loc.stringUnit) }] };
}

/** Caractères de contrôle ou de direction parasites (hors saut de ligne et tabulation). */
export const CONTROL_CHARS = new RegExp('[\u0000-\u0008\u000B-\u001F\u007F-\u009F\u200B-\u200F\u2028\u2029\u202A-\u202E\u2060-\u2064\u2066-\u206F\uFEFF]', 'u');

export function describeControlChar(text) {
  const match = CONTROL_CHARS.exec(text);
  return match ? `U+${match[0].codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}` : null;
}

export const isNbsp = (char) => char === '\u00A0' || char === '\u202F';

/** Nombre d'espaces insécables « de sens » : on ignore celles qui précèdent `? ! : ; »` ou suivent `«` (usage français de la ponctuation). */
export function meaningfulNbspCount(text) {
  let count = 0;
  const chars = [...text];
  for (let i = 0; i < chars.length; i += 1) {
    if (!isNbsp(chars[i])) continue;
    if (i + 1 < chars.length && '?!:;»'.includes(chars[i + 1])) continue;
    if (i > 0 && chars[i - 1] === '«') continue;
    count += 1;
  }
  return count;
}
