#!/usr/bin/env node
// Genere l'icone d'application PROVISOIRE de Miff (1024 x 1024 px, opaque).
//
// PROVISOIRE : le logo officiel n'a pas ete fourni. Cette icone est un carre
// bleu (#043cd1) avec un "M" blanc et un coeur (#d1d828), dessines a la main
// par ce script. Ne pas publier : a remplacer par le fichier du PM / du Figma.
//
// Aucune dependance : uniquement des modules integres a Node.js (fs, zlib, path).
// Sortie : PNG en RGB (type de couleur 2), donc SANS canal alpha (exigence Apple :
// icone opaque, sans coins arrondis - le systeme applique lui-meme le masque).
//
// Usage :
//   node ios/tools/generate-app-icon.mjs [chemin-de-sortie.png]
// Sans argument, ecrit dans le catalogue d'actifs de l'app.

import { writeFileSync, mkdirSync } from "node:fs";
import { deflateSync } from "node:zlib";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const SIZE = 1024;
const SUPERSAMPLING = 4; // 4 x 4 echantillons par pixel : bords lisses

const BLUE = [0x04, 0x3c, 0xd1]; // action/primary
const WHITE = [0xff, 0xff, 0xff]; // text/on-action
const LIME = [0xd1, 0xd8, 0x28]; // brand/primary

// "M" : polygone simple, repere en pixels (origine en haut a gauche).
const M_POLYGON = [
  [212, 640], [212, 220], [322, 220], [512, 450], [702, 220], [812, 220],
  [812, 640], [702, 640], [702, 400], [512, 630], [322, 400], [322, 640],
];

// Coeur : courbe implicite (x^2 + y^2 - 1)^3 - x^2 * y^3 <= 0, y vers le haut.
const HEART_CENTER = [512, 810];
const HEART_SCALE = 88;

function insidePolygon(px, py, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i];
    const [xj, yj] = poly[j];
    if (yi > py !== yj > py && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) {
      inside = !inside;
    }
  }
  return inside;
}

function insideHeart(px, py) {
  const x = (px - HEART_CENTER[0]) / HEART_SCALE;
  const y = -(py - HEART_CENTER[1]) / HEART_SCALE;
  const a = x * x + y * y - 1;
  return a * a * a - x * x * y * y * y <= 0;
}

function crc32(buf) {
  let c;
  let crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

function render() {
  // Chaque ligne PNG commence par un octet de filtre (0 = aucun).
  const stride = 1 + SIZE * 3;
  const raw = Buffer.alloc(stride * SIZE);
  const step = 1 / SUPERSAMPLING;
  const samples = SUPERSAMPLING * SUPERSAMPLING;

  for (let y = 0; y < SIZE; y++) {
    raw[y * stride] = 0;
    for (let x = 0; x < SIZE; x++) {
      let white = 0;
      let lime = 0;
      for (let sy = 0; sy < SUPERSAMPLING; sy++) {
        for (let sx = 0; sx < SUPERSAMPLING; sx++) {
          const px = x + (sx + 0.5) * step;
          const py = y + (sy + 0.5) * step;
          if (insidePolygon(px, py, M_POLYGON)) white++;
          else if (insideHeart(px, py)) lime++;
        }
      }
      const blue = samples - white - lime;
      const offset = y * stride + 1 + x * 3;
      for (let channel = 0; channel < 3; channel++) {
        raw[offset + channel] = Math.round(
          (BLUE[channel] * blue + WHITE[channel] * white + LIME[channel] * lime) / samples,
        );
      }
    }
  }
  return raw;
}

function encodePng(raw) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(SIZE, 0); // largeur
  header.writeUInt32BE(SIZE, 4); // hauteur
  header[8] = 8; // 8 bits par canal
  header[9] = 2; // RGB, sans alpha
  header[10] = 0; // compression
  header[11] = 0; // filtre
  header[12] = 0; // pas d'entrelacement
  const note = Buffer.from(
    "Comment\0PROVISOIRE - icone generee par ios/tools/generate-app-icon.mjs - ne pas publier",
    "latin1",
  );
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("tEXt", note),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const here = dirname(fileURLToPath(import.meta.url));
const defaultOutput = resolve(
  here,
  "../Miff/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png",
);
const output = resolve(process.argv[2] ?? defaultOutput);

mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, encodePng(render()));
console.log(`Icone provisoire ecrite : ${output}`);
