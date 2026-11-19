// Extraction, par langue, du glossaire et du guide de style (docs/i18n/*.md) pour les lots de traduction.
// Aucune dépendance. Ne lit que du texte : n'écrit jamais.
//
// Les documents sont des tableaux Markdown dont une colonne par langue (es, it, pt-PT, de). Le lecteur est volontairement générique :
// il retrouve la colonne d'une langue par son en-tête (« es », « **es** (neutre) », « iOS es », « pt-PT »...) et renvoie les lignes.

/** Retire le gras, les accents graves et les espaces de bord d'une cellule. */
export function cleanCell(cell) {
  return cell.replace(/\*\*/g, '').replace(/`/g, '').trim();
}

/** Cellules d'une ligne de tableau (`| a | b |`), ou null si ce n'est pas une ligne de tableau. */
export function splitRow(line) {
  const trimmed = line.trim();
  if (!trimmed.startsWith('|')) return null;
  const cells = trimmed.replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
  return cells;
}

const isSeparator = (cells) => cells.every((c) => /^:?-{2,}:?$/.test(c));

/** Tableaux d'un texte Markdown : [{header: string[], rows: string[][]}]. */
export function parseTables(markdown) {
  const tables = [];
  const lines = markdown.split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    const header = splitRow(lines[i]);
    const separator = i + 1 < lines.length ? splitRow(lines[i + 1]) : null;
    if (!header || !separator || !isSeparator(separator)) continue;
    const rows = [];
    let j = i + 2;
    for (; j < lines.length; j += 1) {
      const cells = splitRow(lines[j]);
      if (!cells) break;
      rows.push(cells);
    }
    tables.push({ header, rows });
    i = j - 1;
  }
  return tables;
}

/** Sections Markdown : [{level, title, body}] ; `body` va jusqu'au titre suivant, quel que soit son niveau. */
export function parseSections(markdown) {
  const sections = [];
  let current = null;
  for (const line of markdown.split(/\r?\n/)) {
    const m = /^(#{1,6})\s+(.*)$/.exec(line);
    if (m) {
      current = { level: m[1].length, title: m[2].trim(), body: [] };
      sections.push(current);
    } else if (current) {
      current.body.push(line);
    }
  }
  return sections.map((s) => ({ ...s, body: s.body.join('\n').trim() }));
}

/** Section dont le titre commence par ce préfixe (« 2.1 », « 4 », « 4.1 »...) ; titres du type « ## 4. Titre » ou « ### 4.1 Titre ». */
export function findSection(sections, number) {
  const re = new RegExp(`^${number.replace(/\./g, '\\.')}(?:[.\\s]|$)`);
  return sections.find((s) => re.test(s.title)) ?? null;
}

/** Corps d'une section et de ses sous-sections (jusqu'au prochain titre de niveau égal ou supérieur). */
export function sectionWithChildren(sections, number) {
  const start = sections.findIndex((s) => new RegExp(`^${number.replace(/\./g, '\\.')}(?:[.\\s]|$)`).test(s.title));
  if (start < 0) return '';
  const parts = [sections[start].body];
  for (let i = start + 1; i < sections.length && sections[i].level > sections[start].level; i += 1) {
    parts.push(`${'#'.repeat(sections[i].level)} ${sections[i].title}\n${sections[i].body}`);
  }
  return parts.join('\n\n').trim();
}

/** Expressions d'en-tête reconnaissant la colonne d'une langue. */
const COLUMN = {
  es: /(^|[^a-z])es([^a-z]|$)/i,
  it: /(^|[^a-z])it([^a-z]|$)/i,
  pt: /(^|[^a-z])pt([^a-z]|$)/i,
  de: /(^|[^a-z])de([^a-z]|$)/i,
  fr: /^\**fr([^a-z]|$)/i,
  en: /^\**en([^a-z]|$)/i,
};

/** Indice de la colonne d'une langue dans un en-tête de tableau, ou -1. Les colonnes « fr » et « en » sont reconnues en début de cellule. */
export function languageColumn(header, lang) {
  const cells = header.map(cleanCell);
  const matcher = COLUMN[lang];
  if (!matcher) return -1;
  // « Notes / interdits » ou « Libellé (fr) » ne sont pas des colonnes de langue.
  const notLanguage = /^(notes|libell|langue|cat[ée]gorie|famille|situation|pi[èe]ge|cl[ée])/i;
  return cells.findIndex((c, index) => index > 0 && !notLanguage.test(c) && c.length <= 80 && matcher.test(c));
}

/**
 * Lignes d'un texte Markdown pour une langue : [{etiquette, fr, cible, notes}].
 * `etiquette` = première cellule (identifiant G12, nom de famille, clé...), `fr` = cellule de la colonne « fr » si elle existe,
 * `cible` = cellule de la langue, `notes` = dernière colonne « Notes » si elle existe.
 */
export function languageRows(markdown, lang) {
  const rows = [];
  for (const table of parseTables(markdown)) {
    const column = languageColumn(table.header, lang);
    if (column < 0) continue;
    const header = table.header.map(cleanCell);
    const frColumn = header.findIndex((c, index) => index > 0 && COLUMN.fr.test(c));
    const notesColumn = header.findIndex((c) => /^notes/i.test(c));
    for (const cells of table.rows) {
      if (cells.length <= column) continue;
      const row = { etiquette: cleanCell(cells[0]), cible: cleanCell(cells[column]) };
      if (frColumn >= 0 && cells[frColumn] !== undefined) row.fr = cleanCell(cells[frColumn]);
      if (notesColumn >= 0 && cells[notesColumn] !== undefined) row.notes = cleanCell(cells[notesColumn]);
      rows.push(row);
    }
  }
  return rows;
}

/** Ligne du tableau « Formes d'adresse » (glossaire §3) pour une langue : lignes dont la première cellule contient le code de langue. */
export function addressRow(markdown, lang) {
  const labels = { es: /\bes\b/i, it: /\bit\b/i, pt: /\bpt/i, de: /\bde\b/i };
  for (const table of parseTables(markdown)) {
    for (const cells of table.rows) {
      if (labels[lang]?.test(cleanCell(cells[0] ?? ''))) {
        return Object.fromEntries(table.header.map((h, i) => [cleanCell(h), cleanCell(cells[i] ?? '')]));
      }
    }
  }
  return null;
}

/** Sous-section du guide dédiée à une langue (§2.2 es, §2.3 it, §2.4 pt, §2.5 de) : corps Markdown brut. */
export function guideLanguageSection(sections, lang) {
  const number = { es: '2.2', it: '2.3', pt: '2.4', de: '2.5' }[lang];
  return number ? (findSection(sections, number)?.body ?? '') : '';
}

/**
 * Tout ce qu'un agent traducteur doit lire pour une langue, extrait de `docs/i18n/glossaire.md` et de `docs/i18n/guide-de-style.md`.
 * Les clés absentes (document modifié) donnent des listes vides : la commande des lots signale alors un avertissement.
 */
export function extractLanguageKit({ glossaire, guide }, lang) {
  const gSections = parseSections(glossaire);
  const uSections = parseSections(guide);
  const kit = { avertissements: [] };
  const take = (label, value) => {
    if (value === null || value === undefined || (Array.isArray(value) && value.length === 0) || value === '') kit.avertissements.push(`${label} : rien trouvé pour « ${lang} »`);
    return value;
  };

  // Termes figés (§2) : toutes les lignes G..
  const termes = [];
  for (const table of parseTables(sectionWithChildren(gSections, '2'))) {
    const column = languageColumn(table.header, lang);
    if (column < 0) continue;
    const header = table.header.map(cleanCell);
    const enColumn = header.findIndex((c, i) => i > 0 && COLUMN.en.test(c));
    const frColumn = header.findIndex((c, i) => i > 0 && COLUMN.fr.test(c));
    const notesColumn = header.findIndex((c) => /^notes/i.test(c));
    for (const cells of table.rows) {
      if (!/^G\d+/.test(cleanCell(cells[0] ?? ''))) continue;
      termes.push({
        id: cleanCell(cells[0]),
        fr: cleanCell(cells[frColumn] ?? ''),
        en: cleanCell(cells[enColumn] ?? ''),
        cible: cleanCell(cells[column] ?? ''),
        notes: cleanCell(cells[notesColumn] ?? ''),
      });
    }
  }
  kit.termesFiges = take('glossaire §2', termes);
  kit.adresse = take('glossaire §3', addressRow(sectionWithChildren(gSections, '3'), lang));
  kit.termesProscrits = take('glossaire §4', languageRows(sectionWithChildren(gSections, '4'), lang));
  kit.phrasesAssemblees = take('glossaire §5', languageRows(sectionWithChildren(gSections, '5'), lang));
  kit.reglagesSysteme = take('glossaire §6', languageRows(sectionWithChildren(gSections, '6'), lang));
  kit.pieges = take('glossaire §7', languageRows(sectionWithChildren(gSections, '7'), lang));
  kit.guide = {
    principes: take('guide §1', findSection(uSections, '1')?.body ?? ''),
    fiche: take('guide §2.1', languageRows(findSection(uSections, '2.1')?.body ?? '', lang)),
    langue: take('guide §2.x', guideLanguageSection(uSections, lang)),
    pluriels: take('guide §3', sectionWithChildren(uSections, '3')),
    budgets: take('guide §4', sectionWithChildren(uSections, '4')),
    ton: take('guide §5', sectionWithChildren(uSections, '5')),
  };
  return kit;
}
