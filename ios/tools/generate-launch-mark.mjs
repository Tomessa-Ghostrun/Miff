#!/usr/bin/env node
// Genere l'image de l'ecran de lancement systeme de l'app : le « M + i » blanc SANS le coeur, sur fond transparent
// (le fond bleu #043CD1 est la couleur `LaunchBackground` du catalogue d'actifs). Le coeur n'y est pas : c'est l'animation
// de lancement (ios/Miff/Views/Splash) qui le fait apparaitre puis gonfler, exactement a la meme place.
//
// Le dessin est celui de l'icone officielle (design/assets/logo/android/ic_launcher_foreground.xml, premier chemin,
// canevas de 108 unites), dessine a 288 points comme le splash anime (`SplashArtwork.canvasPoints`), en 1x, 2x et 3x.
// Aucune dependance : modules integres a Node.js (fs, zlib). Usage : node ios/tools/generate-launch-mark.mjs

import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { deflateSync } from "node:zlib";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const launcher = readFileSync(resolve(root, "design/assets/logo/android/ic_launcher_foreground.xml"), "utf8");
const pathData = /android:pathData="([^"]+)"/.exec(launcher)[1];

// Le chemin du « M + i » ne contient que M, L et Z : un polygone.
const points = [...pathData.matchAll(/[ML]\s*([-\d.]+)[ ,]([-\d.]+)/g)].map((m) => [Number(m[1]), Number(m[2])]);
if (points.length < 10) throw new Error("polygone du M + i introuvable");

const CANVAS = 108;
const POINTS = 288;
const SUPERSAMPLING = 4;

function inside(px, py) {
  let result = false;
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const [xi, yi] = points[i];
    const [xj, yj] = points[j];
    if (yi > py !== yj > py && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) result = !result;
  }
  return result;
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (let n = 0; n < buffer.length; n++) {
    crc ^= buffer[n];
    for (let k = 0; k < 8; k++) crc = crc & 1 ? 0xedb88320 ^ (crc >>> 1) : crc >>> 1;
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

// PNG RGBA (type de couleur 6) : blanc, alpha = couverture du polygone.
function render(scale) {
  const size = POINTS * scale;
  const unit = CANVAS / size;
  const rows = [];
  for (let y = 0; y < size; y++) {
    const row = Buffer.alloc(1 + size * 4);
    for (let x = 0; x < size; x++) {
      let hits = 0;
      for (let sy = 0; sy < SUPERSAMPLING; sy++) {
        for (let sx = 0; sx < SUPERSAMPLING; sx++) {
          const px = (x + (sx + 0.5) / SUPERSAMPLING) * unit;
          const py = (y + (sy + 0.5) / SUPERSAMPLING) * unit;
          if (inside(px, py)) hits++;
        }
      }
      const offset = 1 + x * 4;
      row[offset] = 255;
      row[offset + 1] = 255;
      row[offset + 2] = 255;
      row[offset + 3] = Math.round((hits / (SUPERSAMPLING * SUPERSAMPLING)) * 255);
    }
    rows.push(row);
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8;
  header[9] = 6;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(Buffer.concat(rows), { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const directory = resolve(root, "ios/Miff/Resources/Assets.xcassets/LaunchMark.imageset");
mkdirSync(directory, { recursive: true });
const images = [];
for (const scale of [1, 2, 3]) {
  const filename = `launch-mark-${scale}x.png`;
  writeFileSync(resolve(directory, filename), render(scale));
  images.push({ filename, idiom: "universal", scale: `${scale}x` });
}
writeFileSync(
  resolve(directory, "Contents.json"),
  JSON.stringify({ images, info: { author: "xcode", version: 1 } }, null, 2) + "\n",
);
console.log("LaunchMark.imageset ecrit (3 images)");
