#!/usr/bin/env node
// Outil de développement (jamais lancé par la CI ni par le build) : fabrique les images de la page d'accueil.
//
//   node web/tools/make-landing-images.mjs
//
// - web/src/assets/img/avatar-*.webp : portraits 128 x 128 tirés des photos du carrousel de bienvenue
//   (design/exports/onboarding-photos, licence Pexels, auteurs dans CREDITS.md) ;
// - web/src/assets/img/living-room.webp : miniature de salon (520 px de large) ;
// - web/src/assets/og/miff-og-fr.png et miff-og-en.png : images de partage 1200 x 630, rendues depuis
//   web/tools/og-image.html (logo officiel + polices du design system).
// Les fichiers générés sont versionnés : ce script ne sert qu'à les refaire. Il a besoin de Chrome ou d'Edge (variable
// MIFF_BROWSER pour indiquer un autre chemin) et n'ouvre que des adresses locales.

import { createServer } from 'node:http';
import { mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { launchBrowser } from './chrome-cdp.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const OUT_IMG = join(ROOT, 'web', 'src', 'assets', 'img');
const OUT_OG = join(ROOT, 'web', 'src', 'assets', 'og');
const PHOTOS = '/design/exports/onboarding-photos';

const TYPES = { '.html': 'text/html; charset=utf-8', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.png': 'image/png' };

const server = createServer((req, res) => {
  const path = normalize(decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname)).replace(/^[/\\]+/, '');
  const full = resolve(ROOT, path);
  try {
    if (!full.startsWith(ROOT + sep) || !statSync(full).isFile()) throw new Error('absent');
    res.writeHead(200, { 'Content-Type': TYPES[extname(full)] ?? 'application/octet-stream' });
    res.end(readFileSync(full));
  } catch {
    res.writeHead(404);
    res.end();
  }
});
await new Promise((ok) => server.listen(0, '127.0.0.1', ok));
const origin = `http://127.0.0.1:${server.address().port}`;

// Exécuté dans le navigateur : redimensionne par paliers (qualité) puis encode en WebP.
const toWebp = (url, width, height, quality) => `(async () => {
  const img = new Image();
  img.src = ${JSON.stringify(url)};
  await img.decode();
  let source = img;
  let w = img.naturalWidth;
  let h = img.naturalHeight;
  const draw = (target, tw, th) => {
    const canvas = document.createElement('canvas');
    canvas.width = tw; canvas.height = th;
    const ctx = canvas.getContext('2d');
    ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(target, 0, 0, tw, th);
    return canvas;
  };
  while (w / 2 > ${width}) { w = Math.round(w / 2); h = Math.round(h / 2); source = draw(source, w, h); }
  const final = draw(source, ${width}, ${height});
  const blob = await new Promise((ok) => final.toBlob(ok, 'image/webp', ${quality}));
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
})()`;

const browser = await launchBrowser();
try {
  mkdirSync(OUT_IMG, { recursive: true });
  mkdirSync(OUT_OG, { recursive: true });
  const page = await browser.newPage({ width: 800, height: 800 });
  await page.goto(`${origin}/web/tools/og-image.html`, { settle: 200 });
  const jobs = [
    ['avatar-thomas.webp', `${PHOTOS}/thomas-alt-800.jpg`, 128, 128, 0.82],
    ['avatar-amandine.webp', `${PHOTOS}/amandine-800.jpg`, 128, 128, 0.82],
    ['avatar-maman.webp', `${PHOTOS}/maman-800.jpg`, 128, 128, 0.82],
    ['living-room.webp', `${PHOTOS}/salon-1200.jpg`, 520, 347, 0.78],
  ];
  for (const [name, url, w, h, q] of jobs) {
    const base64 = await page.evaluate(toWebp(`${origin}${url}`, w, h, q));
    writeFileSync(join(OUT_IMG, name), Buffer.from(base64, 'base64'));
    console.log(`${name} : ${statSync(join(OUT_IMG, name)).size} octets`);
  }
  await page.close();

  for (const lang of ['fr', 'en']) {
    const og = await browser.newPage({ width: 1200, height: 630 });
    await og.goto(`${origin}/web/tools/og-image.html?lang=${lang}`, { settle: 800 });
    const png = await og.screenshot({ clip: { x: 0, y: 0, width: 1200, height: 630 } });
    writeFileSync(join(OUT_OG, `miff-og-${lang}.png`), png);
    console.log(`miff-og-${lang}.png : ${png.length} octets`);
    await og.close();
  }
} finally {
  await browser.close();
  server.close();
}
