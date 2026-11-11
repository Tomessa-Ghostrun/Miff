#!/usr/bin/env node
// Verifie que les bibliotheques natives (.so) d'un bundle ou d'un APK sont alignees pour les pages memoire de 16 Ko (Android 15+).
//
// Regle de Google Play (obligatoire pour les mises a jour d'apps visant Android 15 ou plus, depuis le 1er novembre 2025) : les bibliotheques natives
// 64 bits (arm64-v8a et x86_64) doivent avoir leurs segments PT_LOAD alignes sur au moins 16 384 octets (0x4000). Une bibliotheque alignee sur 4 Ko
// (p_align = 0x1000) fait planter ou refuser l'app sur un appareil a pages de 16 Ko. Les ABI 32 bits (armeabi-v7a, x86) ne sont pas concernees.
//
// Usage : node check-elf-16kb.mjs <dossier>   (dossier = bundle ou APK deja extrait, par exemple `unzip app-release.aab 'base/lib/*' -d <dossier>`)
// Sortie : un tableau (une ligne par bibliotheque 64 bits) ; code 0 si tout est aligne, 1 sinon, 2 si aucune bibliotheque 64 bits n'a ete trouvee
// (ce qui voudrait dire que l'extraction a echoue : on prefere un echec visible a un contrôle qui passe a vide).
//
// Aucune dependance, aucun reseau : lecture seule des en-tetes ELF. Teste par android/ci/check-elf-16kb.test.mjs.

import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { pathToFileURL } from "node:url";

export const REQUIRED_ALIGNMENT = 0x4000; // 16 Ko
export const CHECKED_ABIS = ["arm64-v8a", "x86_64"];

const PT_LOAD = 1;

/**
 * Lit les en-tetes de programme d'un fichier ELF 64 bits petit-boutiste et renvoie l'alignement le plus faible de ses segments PT_LOAD.
 * @param {Buffer} buffer contenu du fichier .so
 * @returns {{ minLoadAlign: number, loadSegments: number }}
 */
export function readLoadAlignment(buffer) {
  if (buffer.length < 64 || buffer.readUInt32BE(0) !== 0x7f454c46) {
    throw new Error("pas un fichier ELF");
  }
  if (buffer[4] !== 2) throw new Error("ELF 32 bits : non pris en charge (seules arm64-v8a et x86_64 sont controlees)");
  if (buffer[5] !== 1) throw new Error("ELF gros-boutiste : non pris en charge");
  const phoff = Number(buffer.readBigUInt64LE(0x20));
  const phentsize = buffer.readUInt16LE(0x36);
  const phnum = buffer.readUInt16LE(0x38);
  if (phentsize < 56) throw new Error("taille d'en-tete de programme inattendue");
  if (phoff + phnum * phentsize > buffer.length) throw new Error("table des en-tetes de programme hors du fichier");
  let minLoadAlign = Infinity;
  let loadSegments = 0;
  for (let i = 0; i < phnum; i += 1) {
    const base = phoff + i * phentsize;
    if (buffer.readUInt32LE(base) !== PT_LOAD) continue;
    loadSegments += 1;
    const align = Number(buffer.readBigUInt64LE(base + 48));
    if (align < minLoadAlign) minLoadAlign = align;
  }
  if (loadSegments === 0) throw new Error("aucun segment PT_LOAD");
  return { minLoadAlign, loadSegments };
}

function* walk(dir) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    const info = statSync(full);
    if (info.isDirectory()) yield* walk(full);
    else if (info.isFile()) yield full;
  }
}

/**
 * Controle toutes les bibliotheques .so des ABI 64 bits d'un dossier.
 * @param {string} root dossier a parcourir
 * @returns {{ rows: Array<{ path: string, abi: string, minLoadAlign: number|null, ok: boolean, problem: string|null }> }}
 */
export function checkDirectory(root) {
  const rows = [];
  for (const file of walk(root)) {
    if (!file.endsWith(".so")) continue;
    const rel = relative(root, file).split(sep).join("/");
    const abi = CHECKED_ABIS.find((candidate) => rel.split("/").includes(candidate));
    if (!abi) continue;
    try {
      const { minLoadAlign } = readLoadAlignment(readFileSync(file));
      const ok = minLoadAlign >= REQUIRED_ALIGNMENT && minLoadAlign % REQUIRED_ALIGNMENT === 0;
      rows.push({ path: rel, abi, minLoadAlign, ok, problem: ok ? null : `alignement 0x${minLoadAlign.toString(16)} au lieu de 0x4000 minimum` });
    } catch (error) {
      rows.push({ path: rel, abi, minLoadAlign: null, ok: false, problem: error.message });
    }
  }
  rows.sort((a, b) => a.path.localeCompare(b.path));
  return { rows };
}

function main(argv) {
  const root = argv[2];
  if (!root) {
    console.error("Usage : node check-elf-16kb.mjs <dossier>");
    return 2;
  }
  const { rows } = checkDirectory(root);
  if (rows.length === 0) {
    console.error(`::error title=Aucune bibliotheque native 64 bits::Aucun .so arm64-v8a ou x86_64 trouve dans ${root} : l'extraction a probablement echoue.`);
    return 2;
  }
  for (const row of rows) {
    const align = row.minLoadAlign === null ? "?" : `0x${row.minLoadAlign.toString(16)}`;
    console.log(`${row.ok ? "OK " : "KO "} ${align.padStart(8)}  ${row.path}${row.problem && !row.ok ? `  (${row.problem})` : ""}`);
  }
  const bad = rows.filter((row) => !row.ok);
  if (bad.length > 0) {
    console.error(
      `::error title=Pages memoire de 16 Ko::${bad.length} bibliotheque(s) native(s) non alignee(s) sur 16 Ko : ${bad.map((row) => row.path).join(", ")}. ` +
        "Mettre a jour la bibliotheque concernee (version recompilee avec NDK r28 ou plus), voir docs/release/checklist-android.md.",
    );
    return 1;
  }
  console.log(`${rows.length} bibliotheque(s) native(s) 64 bits, toutes alignees sur 16 Ko ou plus.`);
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exit(main(process.argv));
}
