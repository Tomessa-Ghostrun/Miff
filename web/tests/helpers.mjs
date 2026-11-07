// Outils communs aux tests du site web (node:test, sans dépendance).
import { mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { after } from 'node:test';
import { build } from '../../scripts/build-web.mjs';

export const ROOT = fileURLToPath(new URL('../../', import.meta.url));
export const WEB = join(ROOT, 'web');

/** Clé d'API factice, assemblée à l'exécution pour ne jamais ressembler à une vraie clé dans le dépôt. */
export const FAKE_KEY = `${'AIza'}${'Z'.repeat(35)}`;
export const FAKE_CONTACT = `${'contact'}@${'example.com'}`;

const temporaryDirs = [];
after(() => {
  for (const dir of temporaryDirs) rmSync(dir, { recursive: true, force: true });
});

export function tempDir(prefix = 'miff-web-') {
  const dir = mkdtempSync(join(tmpdir(), prefix));
  temporaryDirs.push(dir);
  return dir;
}

/** Construit le site dans un dossier temporaire (jamais dans web/public) et retourne { out, info, pages }. */
export function buildTemp(options = {}) {
  const out = join(tempDir(), 'public');
  const result = build({ out, env: {}, ...options });
  return { ...result, out };
}

export function walk(dir, filter = () => true) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...walk(full, filter));
    else if (filter(full)) files.push(full);
  }
  return files.sort();
}

export const rel = (base, file) => relative(base, file).split(sep).join('/');
export const read = (file) => readFileSync(file, 'utf8');

export function htmlPages(out) {
  return walk(out, (f) => f.endsWith('.html')).map((file) => ({ file, name: rel(out, file), html: read(file) }));
}

export function parseAttrs(source) {
  const attrs = {};
  for (const m of source.matchAll(/([A-Za-z_:][\w:.-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g)) {
    attrs[m[1].toLowerCase()] = m[2] ?? m[3] ?? m[4] ?? '';
  }
  return attrs;
}

/** Liste les balises <name ...> avec leurs attributs. */
export function tags(html, name) {
  const found = [];
  for (const m of html.matchAll(new RegExp(`<${name}\\b([^>]*)>`, 'gi'))) found.push({ raw: m[0], attrs: parseAttrs(m[1]), index: m.index });
  return found;
}

const ENTITIES = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'", '&nbsp;': ' ' };
export const decode = (text) => text.replace(/&(amp|lt|gt|quot|#39|nbsp);/g, (m) => ENTITIES[m]);

/** Texte visible approximatif d'une page (sans script, style ni balises). */
export function visibleText(html) {
  return decode(
    html
      .replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
      .replace(/<style\b[\s\S]*?<\/style>/gi, ' ')
      .replace(/<!--[\s\S]*?-->/g, ' ')
      .replace(/<[^>]+>/g, ' '),
  )
    .replace(/\s+/g, ' ')
    .trim();
}

/** Valeurs de tous les attributs id d'une page. */
export function ids(html) {
  return new Set([...html.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]));
}

/** Convertit une adresse du site (avec cleanUrls) en chemin de fichier relatif au dossier public, ou null. */
export function urlToCandidates(pathname) {
  if (pathname === '/') return ['index.html'];
  const clean = pathname.replace(/^\//, '');
  if (pathname.endsWith('/')) return [`${clean}index.html`];
  return [clean, `${clean}.html`, `${clean}/index.html`];
}
