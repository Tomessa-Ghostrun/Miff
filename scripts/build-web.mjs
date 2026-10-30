#!/usr/bin/env node
// Construction du site statique Miff (AUTH-W) : web/src/ + docs/legal/*.md  ->  web/public/
//
// Sans dépendance (Node 24 seulement). Ce que fait le script :
//   1. génère les pages CGU et politique de confidentialité (FR/EN) à partir de docs/legal/*.md ;
//   2. renseigne l'éditeur, son adresse postale et son e-mail de contact à partir d'UNE seule source,
//      web/site.config.json (décidés par le PM) : emplacements correspondants des textes légaux, page de
//      suppression de compte et pied de page ;
//   3. enveloppe les pages rédigées à la main (web/src/<langue>/*.html) dans web/src/layout.html ;
//   4. copie les feuilles de style, scripts et polices (polices OFL prises dans android/, pas de doublon dans le dépôt) ;
//   5. injecte au build, depuis l'environnement, la clé d'API web publique (MIFF_WEB_API_KEY), qui n'est pas dans le dépôt ;
//   6. liste dans .build-info.json les emplacements « [À COMPLÉTER] » restant dans les pages (le garde-fou de
//      déploiement refuse tout site qui en contient).
//
// Usage : node scripts/build-web.mjs [--out <dossier>] [--demo] [--legal-dir <dossier>]
//   --demo : prévisualisation locale uniquement (API de démonstration, clé fictive) ; le garde-fou de déploiement la refuse.
//
// Codes de sortie : 0 = site construit, 1 = erreur (message en français).

import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

export const LEGAL_SOURCES = ['cgu.fr.md', 'cgu.en.md', 'politique-confidentialite.fr.md', 'politique-confidentialite.en.md'];
export const LANGUAGES = ['fr', 'en'];
export const API_KEY_PATTERN = /^AIza[0-9A-Za-z_-]{35}$/;
export const EMAIL_PATTERN = /^[^\s@<>"'()]+@[^\s@<>"'()]+\.[^\s@<>"'()]+$/;
export const DEFAULT_API_BASE = 'https://identitytoolkit.googleapis.com/v1';
export const DEMO_API_BASE = '/demo-api';
export const DEMO_API_KEY = 'demo-key-not-a-real-key';
export const SITE_URL_PATTERN = /^https:\/\/[a-z0-9.-]+(:\d+)?$/;
export const OG_IMAGE = { width: 1200, height: 630, type: 'image/png' };

// Emplacements des textes légaux (docs/legal, autre PR) renseignés au build depuis web/site.config.json.
const LEGAL_CONTACT_PLACEHOLDERS = ['[E-MAIL DE CONTACT À DÉFINIR]', '[CONTACT E-MAIL TO BE DEFINED]', '[ADRESSE DE CONTACT]', '[CONTACT ADDRESS]'];
const LEGAL_PUBLISHER_PLACEHOLDERS = ['[NOM / RAISON SOCIALE À COMPLÉTER]', '[NAME / COMPANY NAME TO BE COMPLETED]'];
const LEGAL_ADDRESS_PLACEHOLDERS = ['[ADRESSE À COMPLÉTER]', '[ADDRESS TO BE COMPLETED]'];

/** Lit web/site.config.json : seul endroit du code web où figurent le nom de l'éditeur, son adresse et son contact. */
export function loadSiteConfig(root = REPO_ROOT) {
  const config = JSON.parse(readFileSync(join(root, 'web', 'site.config.json'), 'utf8'));
  const { name, postalAddress, contactEmail } = config.publisher ?? {};
  if (typeof name !== 'string' || name.trim() === '') throw new Error('web/site.config.json : publisher.name manquant.');
  if (typeof postalAddress !== 'string' || postalAddress.trim() === '') throw new Error('web/site.config.json : publisher.postalAddress manquant.');
  if (typeof contactEmail !== 'string' || !EMAIL_PATTERN.test(contactEmail)) throw new Error('web/site.config.json : publisher.contactEmail n’a pas la forme d’une adresse e-mail.');
  const url = config.site?.url;
  if (typeof url !== 'string' || !SITE_URL_PATTERN.test(url)) throw new Error('web/site.config.json : site.url doit être l’adresse publique du site en https, sans « / » final (ex. https://miff-3d283.web.app).');
  return config;
}

// Logo officiel du PM (validé le 2026-10-27), repris de design/assets/logo/ (une seule source, pas de doublon dans web/src).
// La CSP n'autorise que 'self' : tout est servi par le site, rien n'est chargé depuis l'extérieur.
export const LOGO_FILES = [
  { from: 'design/assets/logo/web/favicon.svg', to: 'favicon.svg' },
  { from: 'design/assets/logo/web/apple-touch-icon-180.png', to: 'apple-touch-icon.png' },
  { from: 'design/assets/logo/web/icon-192.png', to: 'icon-192.png' },
  { from: 'design/assets/logo/web/icon-512.png', to: 'icon-512.png' },
  { from: 'design/assets/logo/web/icon-maskable-512.png', to: 'icon-maskable-512.png' },
  { from: 'design/assets/logo/master/miff-wordmark-color.svg', to: 'assets/logo/miff-wordmark-color.svg' },
];
const FAVICON_PNG_32 = 'design/assets/logo/web/favicon-32.png';

/** Emballe un PNG 32 x 32 dans un fichier .ico (format ICO : l'image PNG est incluse telle quelle, sans re-encodage). */
export function pngToIco(png) {
  const header = Buffer.alloc(22);
  header.writeUInt16LE(0, 0); // réservé
  header.writeUInt16LE(1, 2); // type : icône
  header.writeUInt16LE(1, 4); // une seule image
  header.writeUInt8(32, 6); // largeur
  header.writeUInt8(32, 7); // hauteur
  header.writeUInt16LE(1, 10); // plans
  header.writeUInt16LE(32, 12); // bits par pixel
  header.writeUInt32LE(png.length, 14); // taille des données
  header.writeUInt32LE(22, 18); // décalage des données
  return Buffer.concat([header, png]);
}

const FONT_FILES = [
  { from: 'android/app/src/main/res/font/baloo_thambi_2.ttf', to: 'assets/fonts/baloo_thambi_2.ttf' },
  { from: 'android/app/src/main/res/font/hanken_grotesk.ttf', to: 'assets/fonts/hanken_grotesk.ttf' },
  { from: 'android/third_party/fonts/OFL-BalooThambi2.txt', to: 'assets/fonts/OFL-BalooThambi2.txt' },
  { from: 'android/third_party/fonts/OFL-HankenGrotesk.txt', to: 'assets/fonts/OFL-HankenGrotesk.txt' },
];

// ---------------------------------------------------------------------------------------------------------------
// Markdown -> HTML (sous-ensemble utilisé par docs/legal : titres, citations, listes, tableaux, gras, italique, liens, code)
// ---------------------------------------------------------------------------------------------------------------

export function escapeHtml(text) {
  return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function slugify(text) {
  const slug = text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return slug || 'section';
}

function renderInline(text, context) {
  const codes = [];
  let out = text.replace(/`([^`]+)`/g, (_m, code) => {
    codes.push(code);
    return `\u0000${codes.length - 1}\u0000`;
  });
  out = escapeHtml(out);
  out = out.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_m, label, url) => `<a href="${context.rewriteLink(url)}">${label}</a>`);
  out = out.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/(^|[^*\w])\*([^*\s][^*]*?)\*(?![*\w])/g, '$1<em>$2</em>');
  out = out.replace(/\u0000(\d+)\u0000/g, (_m, i) => `<code>${escapeHtml(codes[Number(i)])}</code>`);
  return out;
}

const isBlank = (line) => line.trim() === '';
const isHeading = (line) => /^#{1,6}\s+\S/.test(line);
const isRule = (line) => /^\s{0,3}([-*_])(\s*\1){2,}\s*$/.test(line);
const isQuote = (line) => /^\s{0,3}>/.test(line);
const bulletMatch = (line) => line.match(/^\s{0,3}[-*+]\s+(.*)$/);
const orderedMatch = (line) => line.match(/^\s{0,3}(\d+)[.)]\s+(.*)$/);
const isTableStart = (line, next) => /^\s*\|/.test(line) && next !== undefined && /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$/.test(next);
const isBlockStart = (line, next) =>
  isHeading(line) || isRule(line) || isQuote(line) || bulletMatch(line) || orderedMatch(line) || isTableStart(line, next);

function splitRow(line) {
  const trimmed = line.trim().replace(/^\|/, '').replace(/\|$/, '');
  return trimmed.split(/(?<!\\)\|/).map((cell) => cell.replace(/\\\|/g, '|').trim());
}

/** Convertit du Markdown en HTML. Retourne { html, title } (title = texte du premier titre de niveau 1). */
export function markdownToHtml(markdown, context) {
  const ids = new Map();
  let title = null;

  function parse(lines) {
    const html = [];
    let i = 0;
    while (i < lines.length) {
      const line = lines[i];
      if (isBlank(line)) {
        i += 1;
        continue;
      }
      if (isHeading(line)) {
        const [, hashes, text] = line.match(/^(#{1,6})\s+(.*?)\s*#*\s*$/);
        const level = hashes.length;
        if (level === 1 && title === null) title = text.replace(/[*`]/g, '');
        let id = slugify(text.replace(/[*`]/g, ''));
        const count = ids.get(id) ?? 0;
        ids.set(id, count + 1);
        if (count > 0) id = `${id}-${count + 1}`;
        html.push(`<h${level} id="${id}">${renderInline(text, context)}</h${level}>`);
        i += 1;
        continue;
      }
      if (isRule(line)) {
        html.push('<hr>');
        i += 1;
        continue;
      }
      if (isQuote(line)) {
        const inner = [];
        while (i < lines.length && isQuote(lines[i])) {
          inner.push(lines[i].replace(/^\s{0,3}>\s?/, ''));
          i += 1;
        }
        html.push(`<blockquote>\n${parse(inner)}\n</blockquote>`);
        continue;
      }
      if (isTableStart(line, lines[i + 1])) {
        const header = splitRow(line);
        i += 2;
        const rows = [];
        while (i < lines.length && /^\s*\|/.test(lines[i])) {
          rows.push(splitRow(lines[i]));
          i += 1;
        }
        const head = header.map((cell) => `<th scope="col">${renderInline(cell, context)}</th>`).join('');
        const body = rows
          .map((row) => `<tr>${header.map((_h, c) => `<td>${renderInline(row[c] ?? '', context)}</td>`).join('')}</tr>`)
          .join('\n');
        html.push(
          `<div class="table-wrap" role="region" aria-label="${escapeHtml(context.tableLabel)}" tabindex="0"><table>\n<thead><tr>${head}</tr></thead>\n<tbody>\n${body}\n</tbody>\n</table></div>`,
        );
        continue;
      }
      if (bulletMatch(line) || orderedMatch(line)) {
        const ordered = Boolean(orderedMatch(line));
        const start = ordered ? Number(orderedMatch(line)[1]) : 1;
        const items = [];
        while (i < lines.length) {
          const current = lines[i];
          const m = ordered ? orderedMatch(current) : bulletMatch(current);
          if (m) {
            items.push(ordered ? m[2] : m[1]);
            i += 1;
            continue;
          }
          if (isBlank(current)) {
            // une ligne vide ne ferme la liste que si la suite n'est pas un élément du même type
            let j = i;
            while (j < lines.length && isBlank(lines[j])) j += 1;
            const next = lines[j];
            if (next !== undefined && (ordered ? orderedMatch(next) : bulletMatch(next))) {
              i = j;
              continue;
            }
            break;
          }
          if (isBlockStart(current, lines[i + 1])) break;
          items[items.length - 1] += ` ${current.trim()}`;
          i += 1;
        }
        const tag = ordered ? 'ol' : 'ul';
        const startAttr = ordered && start !== 1 ? ` start="${start}"` : '';
        html.push(`<${tag}${startAttr}>\n${items.map((item) => `<li>${renderInline(item, context)}</li>`).join('\n')}\n</${tag}>`);
        continue;
      }
      const paragraph = [];
      while (i < lines.length && !isBlank(lines[i]) && (paragraph.length === 0 || !isBlockStart(lines[i], lines[i + 1]))) {
        paragraph.push(lines[i]);
        i += 1;
      }
      const text = paragraph
        .map((l, index) => (index < paragraph.length - 1 && /( {2,}|\\)$/.test(l) ? `${l.replace(/( {2,}|\\)$/, '')}\u0001` : l.trim()))
        .join(' ')
        .replace(/\u0001 ?/g, '\u0001');
      html.push(`<p>${renderInline(text, context).replace(/\u0001/g, '<br>')}</p>`);
    }
    return html.join('\n');
  }

  // Les caractères \u0000 et \u0001 servent de marqueurs internes : on les retire de l'entrée pour qu'elle ne puisse pas les imiter.
  const html = parse(markdown.replace(/[\u0000\u0001]/g, '').replace(/\r\n?/g, '\n').split('\n'));
  return { html, title };
}

// Emplacements « [À COMPLÉTER] », « [À VALIDER ...] », « [E-MAIL DE CONTACT À DÉFINIR] », « [ADRESSE DE CONTACT] »... :
// un crochet contenant une suite de 4 majuscules ou plus. Aucune page ne doit en contenir pour être déployée.
export const PLACEHOLDER_PATTERN = /\[[^\]\n]*[A-ZÀÂÉÈÊËÎÏÔÙÛÇ]{4,}[^\]\n]*\]/g;

/** Liste (sans doublon) les emplacements restant dans un HTML généré. */
export function findPlaceholders(html) {
  return [...new Set(html.match(PLACEHOLDER_PATTERN) ?? [])];
}

// ---------------------------------------------------------------------------------------------------------------
// Outils
// ---------------------------------------------------------------------------------------------------------------

function walk(dir) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...walk(full));
    else files.push(full);
  }
  return files.sort();
}

function toPosix(path) {
  return path.split(sep).join('/');
}

/** Empreinte des entrées du site (sources, textes légaux, script) : sert au garde-fou pour détecter un build périmé. */
export function computeSourceHash({ root = REPO_ROOT, legalDir } = {}) {
  const legal = legalDir ?? join(root, 'docs', 'legal');
  const hash = createHash('sha256');
  const files = [
    ...walk(join(root, 'web', 'src')),
    join(root, 'web', 'site.config.json'),
    ...LEGAL_SOURCES.map((name) => join(legal, name)).filter((f) => existsSync(f)),
    join(root, 'scripts', 'build-web.mjs'),
    ...[...LOGO_FILES.map((f) => f.from), FAVICON_PNG_32].map((f) => join(root, f)).filter((f) => existsSync(f)),
  ];
  for (const file of files) {
    hash.update(`${toPosix(relative(root, file))}\n`);
    hash.update(readFileSync(file));
    hash.update('\n');
  }
  return hash.digest('hex');
}

function assertSafeOut(out, root) {
  const resolved = resolve(out);
  const inWeb = resolved.startsWith(join(root, 'web') + sep);
  const inTmp = resolved.startsWith(resolve(tmpdir()) + sep);
  if (!inWeb && !inTmp) {
    throw new Error(`Dossier de sortie refusé (${out}) : il doit être dans web/ ou dans le dossier temporaire du système.`);
  }
  // Le dossier de sortie est vidé avant construction : jamais les sources ni les tests du site.
  const protectedDirs = ['src', 'tests'].map((name) => join(root, 'web', name));
  if (protectedDirs.some((dir) => resolved === dir || resolved.startsWith(dir + sep)) || resolved === join(root, 'web')) {
    throw new Error(`Dossier de sortie refusé (${out}) : il contiendrait les sources ou les tests du site (web/src, web/tests).`);
  }
  return resolved;
}

/** Remplit un gabarit de page d'accueil : {{t.clé}} (texte de la langue), {{u.id}} (adresse d'une page du registre), {{p.nom}} (extrait). */
function fillLanding(template, { strings, lang, registry, partials, name }) {
  const urls = Object.fromEntries(registry.map((page) => [page.id === 'delete-account' ? 'deleteAccount' : page.id, page[lang].url]));
  const expand = (text) =>
    text.replace(/\{\{(t|u|p)\.(\w+)\}\}/g,(_m, kind, key) => {
      if (kind === 't') {
        if (!(key in strings)) throw new Error(`${name} : texte manquant {{t.${key}}} (${lang})`);
        return strings[key];
      }
      if (kind === 'u') {
        if (!(key in urls)) throw new Error(`${name} : page inconnue {{u.${key}}}`);
        return urls[key];
      }
      if (!(key in partials)) throw new Error(`${name} : extrait inconnu {{p.${key}}}`);
      return expand(partials[key]);
    });
  return expand(template);
}

function fill(template, values, name) {
  return template.replace(/\{\{(\w+)\}\}/g, (_m, key) => {
    if (!(key in values)) throw new Error(`${name} : variable de modèle inconnue {{${key}}}`);
    return values[key];
  });
}

function write(out, path, content) {
  const target = join(out, path);
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, content);
}

function urlToFile(url) {
  if (url === '/') return 'index.html';
  if (url.endsWith('/')) return `${url.slice(1)}index.html`;
  return `${url.slice(1)}.html`;
}

// ---------------------------------------------------------------------------------------------------------------
// Construction
// ---------------------------------------------------------------------------------------------------------------

export function build({ root = REPO_ROOT, out = join(root, 'web', 'public'), legalDir, env = process.env, demo = false, log = () => {} } = {}) {
  const legal = legalDir ? resolve(legalDir) : join(root, 'docs', 'legal');
  const outDir = assertSafeOut(out, root);
  const src = join(root, 'web', 'src');

  // --- Entrées d'environnement (valeurs jamais écrites dans le dépôt, jamais journalisées) ---
  const rawKey = (env.MIFF_WEB_API_KEY ?? '').trim();
  if (rawKey && !API_KEY_PATTERN.test(rawKey)) {
    throw new Error('MIFF_WEB_API_KEY n’a pas la forme d’une clé d’API web Google (« AIza » suivi de 35 caractères). Rien n’a été construit.');
  }
  const apiKey = demo ? DEMO_API_KEY : rawKey;
  const apiBase = demo ? DEMO_API_BASE : DEFAULT_API_BASE;

  const registry = JSON.parse(readFileSync(join(src, 'pages.json'), 'utf8')).pages;
  const ui = JSON.parse(readFileSync(join(src, 'ui.json'), 'utf8'));
  const layout = readFileSync(join(src, 'layout.html'), 'utf8');
  const landingStrings = JSON.parse(readFileSync(join(src, 'landing.json'), 'utf8'));
  const landingTemplate = readFileSync(join(src, 'landing.html'), 'utf8');
  const landingPartials = { cta: readFileSync(join(src, 'landing-cta.html'), 'utf8') };

  // --- Textes légaux : lecture, bandeau, correspondance « fichier .md -> URL » pour les liens internes ---
  const legalByFile = new Map();
  for (const page of registry.filter((p) => p.kind === 'legal')) {
    for (const lang of LANGUAGES) legalByFile.set(page[lang].source, page[lang].url);
  }
  const sources = {};
  for (const name of LEGAL_SOURCES) {
    const path = join(legal, name);
    if (!existsSync(path)) throw new Error(`Texte légal introuvable : ${path}`);
    sources[name] = readFileSync(path, 'utf8');
  }

  // --- Éditeur et contact : UNE seule source, web/site.config.json (valeurs provisoires décidées par le PM) ---
  const siteConfig = loadSiteConfig(root);
  const publisher = siteConfig.publisher;
  const siteUrl = siteConfig.site.url;
  const absolute = (path) => `${siteUrl}${path}`;
  const contactLink = `<a href="mailto:${escapeHtml(publisher.contactEmail)}">${escapeHtml(publisher.contactEmail)}</a>`;
  const applyPublisher = (html) => {
    // Fonctions de remplacement : une valeur contenant « $& » ou « $1 » ne doit jamais être interprétée comme un motif.
    const swap = (text, search, value) => text.replaceAll(search, () => value);
    let result = html;
    result = swap(result, '{{CONTACT}}', contactLink);
    result = swap(result, '{{CONTACT_EMAIL}}', escapeHtml(publisher.contactEmail));
    result = swap(result, '{{PUBLISHER}}', escapeHtml(publisher.name));
    result = swap(result, '{{ADDRESS}}', escapeHtml(publisher.postalAddress));
    // Emplacements déjà présents dans les textes légaux (docs/legal, autre PR) : renseignés ici, sans toucher aux sources.
    for (const placeholder of LEGAL_CONTACT_PLACEHOLDERS) result = swap(result, placeholder, contactLink);
    for (const placeholder of LEGAL_PUBLISHER_PLACEHOLDERS) result = swap(result, placeholder, escapeHtml(publisher.name));
    for (const placeholder of LEGAL_ADDRESS_PLACEHOLDERS) result = swap(result, placeholder, escapeHtml(publisher.postalAddress));
    return result;
  };

  rmSync(outDir, { recursive: true, force: true });
  mkdirSync(outDir, { recursive: true });
  const pagesBuilt = [];

  // --- Pages localisées ---
  for (const lang of LANGUAGES) {
    const other = LANGUAGES.find((l) => l !== lang);
    for (const page of registry) {
      const entry = page[lang];
      const otherEntry = page[other];
      let content;
      let title = entry.title;
      if (page.kind === 'legal') {
        const body = sources[entry.source];
        const rewriteLink = (url) => {
          if (/^https:\/\//.test(url) || /^mailto:/.test(url) || url.startsWith('#')) return url;
          const [file, hash] = url.split('#');
          const target = legalByFile.get(file);
          if (!target) throw new Error(`${entry.source} : lien interne inconnu (${url}). Ajoute la page à web/src/pages.json ou corrige le lien.`);
          return hash ? `${target}#${hash}` : target;
        };
        const tableLabel = lang === 'fr' ? 'Tableau (défile horizontalement si besoin)' : 'Table (scrolls horizontally if needed)';
        const converted = markdownToHtml(body, { rewriteLink, tableLabel });
        content = converted.html;
        title = converted.title ?? entry.navLabel;
      } else if (page.kind === 'landing') {
        content = fillLanding(landingTemplate, { strings: landingStrings[lang], lang, registry, partials: landingPartials, name: `${lang}:${page.id}` });
      } else {
        content = readFileSync(join(src, entry.file), 'utf8');
      }
      content = applyPublisher(content);
      const strings = ui[lang];
      const navItems = registry
        .filter((p) => p.nav)
        .map((p) => {
          const current = p[lang].url === entry.url ? ' aria-current="page"' : '';
          return `<li><a href="${p[lang].url}"${current}>${escapeHtml(p[lang].navLabel)}</a></li>`;
        })
        .join('\n');
      // SEO : adresse canonique et variantes de langue en adresses complètes ; x-default = la racine pour l'accueil,
      // sinon la version anglaise de la page.
      const xDefault = page.id === 'home' ? '/' : page.en.url;
      const alternates = [
        `<link rel="canonical" href="${absolute(entry.url)}">`,
        ...LANGUAGES.map((l) => `<link rel="alternate" hreflang="${l}" href="${absolute(page[l].url)}">`),
        `<link rel="alternate" hreflang="x-default" href="${absolute(xDefault)}">`,
        '',
      ].join('\n');
      const pageTitle = page.id === 'home' || title.includes('Miff') ? title : `${title} - Miff`;
      const description = entry.description ?? strings.descriptionHome;
      const ogImage = absolute(`/assets/og/miff-og-${lang}.png`);
      const social = [
        '<meta property="og:site_name" content="Miff">',
        '<meta property="og:type" content="website">',
        `<meta property="og:locale" content="${lang === 'fr' ? 'fr_FR' : 'en_US'}">`,
        `<meta property="og:locale:alternate" content="${other === 'fr' ? 'fr_FR' : 'en_US'}">`,
        `<meta property="og:title" content="${escapeHtml(pageTitle)}">`,
        `<meta property="og:description" content="${escapeHtml(description)}">`,
        `<meta property="og:url" content="${absolute(entry.url)}">`,
        `<meta property="og:image" content="${ogImage}">`,
        `<meta property="og:image:type" content="${OG_IMAGE.type}">`,
        `<meta property="og:image:width" content="${OG_IMAGE.width}">`,
        `<meta property="og:image:height" content="${OG_IMAGE.height}">`,
        `<meta property="og:image:alt" content="${escapeHtml(strings.ogImageAlt)}">`,
        '<meta name="twitter:card" content="summary_large_image">',
        `<meta name="twitter:title" content="${escapeHtml(pageTitle)}">`,
        `<meta name="twitter:description" content="${escapeHtml(description)}">`,
        `<meta name="twitter:image" content="${ogImage}">`,
        `<meta name="twitter:image:alt" content="${escapeHtml(strings.ogImageAlt)}">`,
        '',
      ].join('\n');
      const extraStyles = (page.styles ?? []).map((href) => `<link rel="stylesheet" href="${href}">\n`).join('');
      const html = fill(
        layout,
        {
          lang,
          title: escapeHtml(pageTitle),
          description: escapeHtml(description),
          alternates,
          social,
          extraStyles,
          bodyClass: page.bodyClass ?? '',
          skip: escapeHtml(strings.skip),
          homeUrl: page.id === 'home' ? entry.url : registry.find((p) => p.id === 'home')[lang].url,
          otherUrl: otherEntry.url,
          otherLang: other,
          otherLabel: escapeHtml(strings.otherLabel),
          navLabel: escapeHtml(strings.navLabel),
          nav: navItems,
          content,
          footer: escapeHtml(strings.footer),
          publisher: applyPublisher(escapeHtml(strings.publisherLine)),
          fontsNote: escapeHtml(strings.fontsNote),
        },
        `${lang}:${page.id}`,
      );
      write(outDir, urlToFile(entry.url), html);
      pagesBuilt.push({ id: page.id, lang, url: entry.url, file: urlToFile(entry.url) });
    }
  }

  // --- Pages autonomes ---
  write(outDir, 'index.html', fill(readFileSync(join(src, 'index.html'), 'utf8'), { siteUrl }, 'index'));
  write(outDir, '404.html', readFileSync(join(src, '404.html'), 'utf8'));
  write(
    outDir,
    'reset-password.html',
    fill(readFileSync(join(src, 'reset-password.html'), 'utf8'), { apiKey: escapeHtml(apiKey), apiBase: escapeHtml(apiBase) }, 'reset-password'),
  );
  pagesBuilt.push({ id: 'root', lang: '*', url: '/', file: 'index.html' }, { id: 'reset-password', lang: '*', url: '/reset-password', file: 'reset-password.html' });

  // --- Ressources ---
  for (const { from, to } of LOGO_FILES) {
    const source = join(root, from);
    if (!existsSync(source)) throw new Error(`Logo introuvable : ${from}. Les icônes du site sont reprises de design/assets/logo/ ; si elles ont été déplacées, mets à jour LOGO_FILES dans scripts/build-web.mjs.`);
    mkdirSync(dirname(join(outDir, to)), { recursive: true });
    cpSync(source, join(outDir, to));
  }
  if (!existsSync(join(root, FAVICON_PNG_32))) throw new Error(`Logo introuvable : ${FAVICON_PNG_32}.`);
  write(outDir, 'favicon.ico', pngToIco(readFileSync(join(root, FAVICON_PNG_32))));
  cpSync(join(src, 'assets'), join(outDir, 'assets'), { recursive: true });
  for (const { from, to } of FONT_FILES) {
    const source = join(root, from);
    if (!existsSync(source)) {
      throw new Error(`Police introuvable : ${from}. Les polices du site sont reprises de android/ (licence OFL) ; si elles ont été déplacées, mets à jour FONT_FILES dans scripts/build-web.mjs.`);
    }
    mkdirSync(dirname(join(outDir, to)), { recursive: true });
    cpSync(source, join(outDir, to));
  }

  // Emplacements « [À COMPLÉTER] » restant dans les pages générées (le garde-fou de déploiement les refuse).
  const placeholders = [
    ...new Set(
      pagesBuilt.flatMap((page) => findPlaceholders(readFileSync(join(outDir, page.file), 'utf8'))),
    ),
  ];
  const info = {
    builtAt: new Date().toISOString(),
    demo,
    apiKeyInjected: Boolean(rawKey) && !demo,
    placeholders: placeholders.length,
    sourceHash: computeSourceHash({ root, legalDir: legal }),
    pages: pagesBuilt.length,
  };
  write(outDir, '.build-info.json', `${JSON.stringify(info, null, 2)}\n`);
  log(`Site construit dans ${outDir} : ${pagesBuilt.length} pages, démo=${demo}, clé=${info.apiKeyInjected ? 'injectée' : 'absente'}, emplacements à compléter=${info.placeholders}.`);
  return { outDir, info, pages: pagesBuilt };
}

// ---------------------------------------------------------------------------------------------------------------

function main(argv) {
  const options = { log: (message) => console.log(message) };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--demo') options.demo = true;
    else if (arg === '--out' && argv[i + 1]) options.out = resolve(argv[(i += 1)]);
    else if (arg === '--legal-dir' && argv[i + 1]) options.legalDir = resolve(argv[(i += 1)]);
    else {
      console.error(`Argument inconnu : ${arg}\nUsage : node scripts/build-web.mjs [--out <dossier>] [--demo] [--legal-dir <dossier>]`);
      return 1;
    }
  }
  try {
    const { info } = build(options);
    if (info.placeholders > 0) {
      console.log(`Attention : ${info.placeholders} emplacement(s) « [À COMPLÉTER] » restent dans les pages. Aperçu possible, mais le déploiement sera refusé tant qu'il en reste (voir web/README.md).`);
    }
    return 0;
  } catch (error) {
    console.error(`Erreur : ${error.message}`);
    return 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exitCode = main(process.argv.slice(2));
}
