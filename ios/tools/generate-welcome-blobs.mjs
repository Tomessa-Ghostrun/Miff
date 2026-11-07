#!/usr/bin/env node
// Genere ios/Miff/Models/Onboarding/WelcomeBlobData.swift a partir des formes vert pale d'Android
// (android/.../ui/onboarding/WelcomeBlobs.kt) : ces contours ont ete TRACES sur les maquettes du PM
// (design/exports/onboarding-original : « Welcoming screen », « -16 », « -17 ») et valides par le PM le 2026-10-30.
// Les memes coordonnees (unites de 320 de large) servent aux deux plateformes ; ce script les recopie sans les modifier.
// Aucune dependance. Usage : node ios/tools/generate-welcome-blobs.mjs

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const source = readFileSync(
  resolve(root, "android/app/src/main/java/com/tomessa/miff/ui/onboarding/WelcomeBlobs.kt"),
  "utf8",
);

const marker = "private val SLIDES: List<List<String>> =";
const start = source.indexOf(marker);
if (start < 0) throw new Error("SLIDES introuvable");
let index = source.indexOf("listOf(", start);

// Lit une liste Kotlin `listOf(...)` (listes imbriquees et chaines concatenees avec +) et la rend en tableaux JS.
function readList() {
  index += "listOf(".length;
  const items = [];
  for (;;) {
    while (/[\s,]/.test(source[index])) index += 1;
    if (source[index] === ")") {
      index += 1;
      return items;
    }
    if (source.startsWith("listOf(", index)) {
      items.push(readList());
    } else if (source[index] === '"') {
      let text = "";
      for (;;) {
        const end = source.indexOf('"', index + 1);
        text += source.slice(index + 1, end);
        index = end + 1;
        // Chaine suivante concatenee avec « + » ?
        const rest = source.slice(index).match(/^\s*\+\s*"/);
        if (!rest) break;
        index += rest[0].length - 1;
      }
      items.push(text);
    } else {
      throw new Error("syntaxe inattendue pres de : " + source.slice(index, index + 40));
    }
  }
}

const slides = readList();
if (slides.length !== 3) throw new Error("3 slides attendues, trouve " + slides.length);

function swiftOutline(outline) {
  // Decoupe en lignes d'au plus ~100 caracteres, a une frontiere de point.
  const points = outline.trim().split(" ");
  const lines = [];
  let current = "";
  for (const point of points) {
    if (current.length + point.length + 1 > 96 && current) {
      lines.push(current);
      current = "";
    }
    current += (current ? " " : "") + point;
  }
  if (current) lines.push(current);
  // Un contour = les morceaux d'une liste, recolles par joined() (evite une longue chaine de « + » que le compilateur
  // Swift met du temps a typer).
  const items = lines
    .map((line, i) => `                    "${line}${i < lines.length - 1 ? " " : ""}"`)
    .join(",\n");
  return `            [\n${items}\n            ].joined()`;
}

const body = slides
  .map(
    (outlines) =>
      "        [\n" +
      outlines.map((outline) => swiftOutline(outline)).join(",\n") +
      "\n        ]",
  )
  .join(",\n");

const output = `// GENERE par ios/tools/generate-welcome-blobs.mjs : ne pas modifier a la main (relancer le script).
// Source : android/.../ui/onboarding/WelcomeBlobs.kt (contours traces sur les maquettes du PM, valides le 2026-10-30).

/// Contours des formes vert pale du carrousel de bienvenue, une liste par slide. Coordonnees en unites des maquettes
/// (320 de large, y mesure depuis le haut de l'ecran) : « x,y » separes par des espaces.
enum WelcomeBlobData {
    static let slides: [[String]] = [
${body}
    ]
}
`;
writeFileSync(resolve(root, "ios/Miff/Models/Onboarding/WelcomeBlobData.swift"), output);
console.log("WelcomeBlobData.swift ecrit :", slides.map((s) => s.length + " formes").join(", "));
