#!/usr/bin/env node
// Genere ios/Miff/DesignSystem/Logo/LogoPaths.swift a partir des sources vectorielles du logo (design/assets/logo).
//
// Le logo officiel (valide par le PM le 2026-10-27) est dessine dans l'app par des chemins vectoriels (aucune image :
// net a toute taille, couleurs prises dans les tokens du design system). Ce script recopie, sans les modifier, les
// donnees de chemin (attribut `d`) des fichiers sources :
//   - design/assets/logo/master/miff-wordmark-color.svg : lettres « Miff » et coeur (viewBox 981 x 380) ;
//   - design/assets/logo/android/ic_launcher_foreground.xml : « M + i » et coeur de l'icone (canevas 108 x 108).
// Aucune dependance : modules integres a Node.js. Usage : node ios/tools/generate-logo-paths.mjs

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const wordmark = readFileSync(resolve(root, "design/assets/logo/master/miff-wordmark-color.svg"), "utf8");
const launcher = readFileSync(resolve(root, "design/assets/logo/android/ic_launcher_foreground.xml"), "utf8");

function svgPath(text, id) {
  const match = new RegExp(`<path id="${id}"[^>]* d="([^"]+)"`).exec(text);
  if (!match) throw new Error(`chemin ${id} introuvable`);
  return match[1];
}

function androidPaths(text) {
  return [...text.matchAll(/android:pathData="([^"]+)"/g)].map((m) => m[1]);
}

// Decoupe en morceaux d'au plus 96 caracteres, a une frontiere de commande ou d'espace (jamais au milieu d'un nombre).
function chunks(data) {
  const normalized = data.replace(/\s+/g, " ").trim();
  const out = [];
  let current = "";
  for (const piece of normalized.split(/(?=[A-Za-z])/)) {
    if (current.length + piece.length > 96 && current.length > 0) {
      out.push(current);
      current = "";
    }
    if (piece.length > 96) {
      // Commande tres longue (suite de nombres) : coupe sur les espaces.
      const words = piece.split(" ");
      for (const word of words) {
        if (current.length + word.length + 1 > 96) {
          out.push(current);
          current = "";
        }
        current += (current && !current.endsWith(" ") && !/^[A-Za-z]$/.test(current.slice(-1)) ? " " : "") + word;
      }
    } else {
      current += piece;
    }
  }
  if (current) out.push(current);
  return out;
}

function swiftArray(name, data) {
  const lines = chunks(data).map((chunk) => `        "${chunk}"`);
  return `    static let ${name}: [PathCommand] = SVGPathParser.parse(\n    [\n${lines.join(",\n")}\n    ].joined())`;
}

const [markLetters, markHeart] = androidPaths(launcher);
const output = `// GENERE par ios/tools/generate-logo-paths.mjs : ne pas modifier a la main (relancer le script).
// Source : design/assets/logo (logo officiel, valide par le PM le 2026-10-27). Les couleurs ne sont PAS ici : elles
// viennent des tokens du design system (colorActionPrimary, colorBrandPrimary, colorTextOnAction).

/// Donnees de chemin du logo, lues par \`SVGPathParser\`.
enum LogoPaths {
    /// Largeur et hauteur du mot-symbole (viewBox du fichier source).
    static let wordmarkWidth = 981.0
    static let wordmarkHeight = 380.0
    /// Canevas de l'icone (le meme que celui de l'icone adaptative d'Android).
    static let markCanvas = 108.0

${swiftArray("wordmarkLetters", svgPath(wordmark, "miff-lettres"))}

${swiftArray("wordmarkHeart", svgPath(wordmark, "miff-coeur"))}

${swiftArray("markLetters", markLetters)}

${swiftArray("markHeart", markHeart)}
}
`;
writeFileSync(resolve(root, "ios/Miff/DesignSystem/Logo/LogoPaths.swift"), output);
console.log("LogoPaths.swift ecrit");
