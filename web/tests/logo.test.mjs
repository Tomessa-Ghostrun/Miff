// Tests du logo officiel du PM (validé le 2026-10-27) dans le site : favicon, icônes, mot-symbole de l'en-tête.
// Aucune dépendance : node --test web/tests
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { ROOT, WEB, buildTemp, htmlPages, read, rel, tags, visibleText, walk } from './helpers.mjs';

const site = buildTemp();
const pages = htmlPages(site.out);
const firebase = JSON.parse(read(join(WEB, 'firebase.json')));

const PNG_SIGNATURE = '89504e470d0a1a0a';
const pngSize = (file) => {
  const data = readFileSync(file);
  assert.equal(data.subarray(0, 8).toString('hex'), PNG_SIGNATURE, `${file} : signature PNG`);
  return { width: data.readUInt32BE(16), height: data.readUInt32BE(20) };
};

// Fichiers attendus : chemin publié, taille en pixels, poids maximal en octets.
const ICONS = [
  { path: 'apple-touch-icon.png', size: 180, max: 8000 },
  { path: 'icon-192.png', size: 192, max: 10000 },
  { path: 'icon-512.png', size: 512, max: 30000 },
  { path: 'icon-maskable-512.png', size: 512, max: 30000 },
];

test('LOGO - favicon.svg, favicon.ico et icônes PNG présents, aux bonnes dimensions et de poids raisonnable', () => {
  for (const { path, size, max } of ICONS) {
    const file = join(site.out, path);
    assert.ok(existsSync(file), `${path} manquant`);
    assert.deepEqual(pngSize(file), { width: size, height: size }, `${path} : dimensions`);
    const bytes = statSync(file).size;
    assert.ok(bytes > 200 && bytes <= max, `${path} : poids ${bytes} octets hors de 200..${max}`);
  }
  const svg = read(join(site.out, 'favicon.svg'));
  assert.match(svg, /^(<\?xml[^>]*\?>\s*)?<svg\b/, 'favicon.svg : document SVG');
  assert.ok(statSync(join(site.out, 'favicon.svg')).size < 4000);
  assert.match(svg, /#043CD1/i, 'favicon.svg : bleu de la marque');
  assert.match(svg, /#D1D828/i, 'favicon.svg : cœur jaune-vert');
  // L'ancien favicon provisoire (cœur seul sur carré bleu de 64 x 64) a disparu.
  assert.ok(!svg.includes('viewBox="0 0 64 64"'), 'favicon.svg : ancien favicon provisoire');
  assert.ok(!existsSync(join(WEB, 'src', 'favicon.svg')), 'web/src/favicon.svg (provisoire) doit être supprimé : le favicon vient de design/assets/logo/');

  // .ico : en-tête valide, une image PNG 32 x 32 incluse
  const ico = readFileSync(join(site.out, 'favicon.ico'));
  assert.equal(ico.readUInt16LE(0), 0);
  assert.equal(ico.readUInt16LE(2), 1, 'favicon.ico : type icône');
  assert.equal(ico.readUInt16LE(4), 1, 'favicon.ico : une image');
  assert.equal(ico[6], 32);
  assert.equal(ico[7], 32);
  const length = ico.readUInt32LE(14);
  const offset = ico.readUInt32LE(18);
  assert.equal(offset + length, ico.length, 'favicon.ico : taille cohérente');
  assert.equal(ico.subarray(offset, offset + 8).toString('hex'), PNG_SIGNATURE);
  assert.equal(ico.readUInt32BE(offset + 16), 32);
  assert.ok(ico.length < 3000);
});

test('LOGO - les fichiers du site sont identiques aux fichiers officiels de design/assets/logo (aucune copie modifiée)', () => {
  const same = (published, source) => assert.deepEqual(readFileSync(join(site.out, published)), readFileSync(join(ROOT, source)), `${published} diffère de ${source}`);
  same('favicon.svg', 'design/assets/logo/web/favicon.svg');
  same('apple-touch-icon.png', 'design/assets/logo/web/apple-touch-icon-180.png');
  same('icon-192.png', 'design/assets/logo/web/icon-192.png');
  same('icon-512.png', 'design/assets/logo/web/icon-512.png');
  same('icon-maskable-512.png', 'design/assets/logo/web/icon-maskable-512.png');
  same('assets/logo/miff-wordmark-color.svg', 'design/assets/logo/master/miff-wordmark-color.svg');
});

test('LOGO - chaque page déclare les mêmes icônes, avec types et tailles corrects, vers des fichiers qui existent', () => {
  const expected = [
    { rel: 'icon', href: '/favicon.ico', sizes: '32x32' },
    { rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' },
    { rel: 'icon', href: '/icon-192.png', type: 'image/png', sizes: '192x192' },
    { rel: 'apple-touch-icon', href: '/apple-touch-icon.png', sizes: '180x180' },
  ];
  assert.ok(pages.length >= 9);
  for (const { name, html } of pages) {
    const links = tags(html, 'link').filter((t) => /icon/.test(t.attrs.rel ?? ''));
    assert.equal(links.length, expected.length, `${name} : nombre de liens d'icône`);
    for (const want of expected) {
      const got = links.find((l) => l.attrs.href === want.href);
      assert.ok(got, `${name} : lien ${want.href} manquant`);
      for (const [key, value] of Object.entries(want)) assert.equal(got.attrs[key], value, `${name} : ${want.href} ${key}`);
      assert.ok(existsSync(join(site.out, want.href.slice(1))), `${name} : ${want.href} absent du site`);
    }
    // La taille déclarée correspond à la vraie taille du PNG.
    for (const l of links) {
      if (l.attrs.type === 'image/png') assert.equal(pngSize(join(site.out, l.attrs.href.slice(1))).width, Number(l.attrs.sizes.split('x')[0]), `${name} : taille déclarée de ${l.attrs.href}`);
    }
  }
});

test("LOGO - mot-symbole « Miff » dans l'en-tête de chaque page : alt accessible, dimensions explicites, ressource locale", () => {
  for (const { name, html } of pages) {
    const header = html.match(/<header class="site-header">[\s\S]*?<\/header>/)?.[0];
    assert.ok(header, `${name} : en-tête`);
    const imgs = tags(header, 'img');
    assert.equal(imgs.length, 1, `${name} : un seul logo dans l'en-tête`);
    const [img] = imgs;
    assert.equal(img.attrs.alt, 'Miff', `${name} : alt du logo`);
    assert.equal(img.attrs.src, '/assets/logo/miff-wordmark-color.svg', `${name} : source du logo`);
    assert.ok(existsSync(join(site.out, img.attrs.src.slice(1))), `${name} : logo absent du site`);
    // Dimensions explicites, au ratio 981:380 du mot-symbole (aucun décalage de mise en page au chargement).
    const width = Number(img.attrs.width);
    const height = Number(img.attrs.height);
    assert.ok(Number.isInteger(width) && Number.isInteger(height) && width > 0 && height > 0, `${name} : width/height explicites`);
    assert.ok(Math.abs(width / height - 981 / 380) < 0.05, `${name} : ratio du logo ${width}x${height}`);
    assert.ok(!/\sstyle\s*=/i.test(img.raw), `${name} : style en ligne sur le logo`);
    // Le logo seul porte le nom (pas de texte « Miff » en double dans le lien).
    const brand = header.match(/<(a|span) class="brand"[^>]*>([\s\S]*?)<\/\1>/);
    assert.ok(brand, `${name} : élément .brand`);
    assert.equal(visibleText(brand[2]), '', `${name} : texte en double à côté du logo`);
  }
  // Accueil, pages légales et pages autonomes : le logo est bien dans l'en-tête.
  for (const name of ['index.html', '404.html', 'reset-password.html', 'fr/index.html', 'en/index.html', 'fr/cgu.html', 'en/terms.html', 'fr/confidentialite.html', 'en/privacy.html']) {
    assert.ok(pages.some((p) => p.name === name), `${name} manquante`);
  }
});

test('LOGO - mot-symbole et icônes sans ressource externe ; les SVG sont inertes (pas de script)', () => {
  for (const file of ['favicon.svg', 'assets/logo/miff-wordmark-color.svg']) {
    const svg = read(join(site.out, file));
    assert.ok(!/<script\b/i.test(svg), `${file} : script dans le SVG`);
    assert.ok(!/\son[a-z]+\s*=/i.test(svg), `${file} : gestionnaire d'événement dans le SVG`);
    assert.ok(!/<foreignObject|<image\b|<use\b/i.test(svg), `${file} : contenu externe ou intégré`);
    assert.ok(!/(href|src)\s*=\s*["'](https?:)?\/\//i.test(svg), `${file} : lien externe`);
    assert.ok(!/url\(\s*["']?(https?:)?\/\//i.test(svg), `${file} : url() externe`);
    assert.ok(!/@import/.test(svg), `${file} : @import`);
    assert.ok(statSync(join(site.out, file)).size < 8000, `${file} : poids`);
  }
  for (const { name, html } of pages) {
    for (const img of tags(html, 'img')) assert.ok(img.attrs.src.startsWith('/') && !img.attrs.src.startsWith('//'), `${name} : image non locale`);
  }
});

test("LOGO - la politique de sécurité n'est pas affaiblie : images en 'self' seulement, manifeste toujours refusé", () => {
  const headers = firebase.hosting.headers.find((h) => h.source === '**').headers;
  const csp = headers.find((h) => h.key === 'Content-Security-Policy').value;
  assert.match(csp, /img-src 'self'(;|$)/);
  assert.match(csp, /manifest-src 'none'/);
  assert.match(csp, /default-src 'none'/);
  assert.ok(!/img-src[^;]*(data:|https?:|\*)/.test(csp), 'img-src ne doit autoriser ni data: ni domaine externe');
  assert.ok(!/unsafe-inline|unsafe-eval/.test(csp));
  // Pas de manifeste web : la CSP le refuse, et le site n'est pas une application installable.
  for (const { name, html } of pages) assert.ok(!tags(html, 'link').some((l) => l.attrs.rel === 'manifest'), `${name} : lien vers un manifeste (refusé par manifest-src 'none')`);
  assert.deepEqual(walk(site.out, (f) => /manifest/.test(f)).map((f) => rel(site.out, f)), []);
});
