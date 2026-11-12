// Tests de android/ci/check-elf-16kb.mjs : de petits fichiers ELF fabriques a la main (aucun binaire reel dans le depot).
// Lancer : node --test android/ci/check-elf-16kb.test.mjs
import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import { checkDirectory, readLoadAlignment } from "./check-elf-16kb.mjs";

/** ELF 64 bits petit-boutiste minimal : un en-tete de 64 octets suivi d'une table de segments. */
function elf({ aligns, types, bits = 2, endian = 1 }) {
  const phnum = aligns.length;
  const buffer = Buffer.alloc(64 + 56 * phnum);
  buffer.writeUInt32BE(0x7f454c46, 0);
  buffer[4] = bits;
  buffer[5] = endian;
  buffer.writeBigUInt64LE(64n, 0x20); // e_phoff
  buffer.writeUInt16LE(56, 0x36); // e_phentsize
  buffer.writeUInt16LE(phnum, 0x38); // e_phnum
  aligns.forEach((align, i) => {
    const base = 64 + 56 * i;
    buffer.writeUInt32LE(types ? types[i] : 1, base); // p_type
    buffer.writeBigUInt64LE(BigInt(align), base + 48); // p_align
  });
  return buffer;
}

function withLibs(files, run) {
  const root = mkdtempSync(join(tmpdir(), "miff-elf-"));
  try {
    for (const [path, content] of Object.entries(files)) {
      const full = join(root, path);
      mkdirSync(join(full, ".."), { recursive: true });
      writeFileSync(full, content);
    }
    return run(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test("le plus petit alignement des segments PT_LOAD est lu, les autres segments sont ignores", () => {
  const result = readLoadAlignment(elf({ aligns: [0x10000, 0x4000, 8], types: [1, 1, 2] }));
  assert.equal(result.minLoadAlign, 0x4000);
  assert.equal(result.loadSegments, 2);
});

test("un fichier qui n'est pas un ELF, un ELF 32 bits et un ELF sans PT_LOAD sont refuses avec une raison", () => {
  assert.throws(() => readLoadAlignment(Buffer.from("pas un elf, mais assez long pour depasser les soixante-quatre octets de l'en-tete")), /pas un fichier ELF/);
  assert.throws(() => readLoadAlignment(elf({ aligns: [0x4000], bits: 1 })), /32 bits/);
  assert.throws(() => readLoadAlignment(elf({ aligns: [0x4000], types: [2] })), /PT_LOAD/);
});

test("un dossier dont toutes les bibliotheques 64 bits sont alignees sur 16 Ko est accepte", () => {
  withLibs(
    {
      "base/lib/arm64-v8a/liba.so": elf({ aligns: [0x4000, 0x4000] }),
      "base/lib/x86_64/liba.so": elf({ aligns: [0x10000] }),
    },
    (root) => {
      const { rows } = checkDirectory(root);
      assert.equal(rows.length, 2);
      assert.ok(rows.every((row) => row.ok));
    },
  );
});

test("une bibliotheque alignee sur 4 Ko est signalee, avec son chemin", () => {
  withLibs(
    {
      "base/lib/arm64-v8a/ok.so": elf({ aligns: [0x4000] }),
      "base/lib/arm64-v8a/old.so": elf({ aligns: [0x1000] }),
    },
    (root) => {
      const bad = checkDirectory(root).rows.filter((row) => !row.ok);
      assert.deepEqual(
        bad.map((row) => row.path),
        ["base/lib/arm64-v8a/old.so"],
      );
      assert.match(bad[0].problem, /0x1000/);
    },
  );
});

test("les ABI 32 bits et les fichiers qui ne sont pas des .so ne sont pas controles", () => {
  withLibs(
    {
      "base/lib/armeabi-v7a/old.so": elf({ aligns: [0x1000], bits: 1 }),
      "base/lib/x86/old.so": elf({ aligns: [0x1000], bits: 1 }),
      "base/lib/arm64-v8a/readme.txt": Buffer.from("rien"),
    },
    (root) => assert.equal(checkDirectory(root).rows.length, 0),
  );
});

test("une bibliotheque illisible compte comme un echec, jamais comme un succes", () => {
  withLibs({ "base/lib/arm64-v8a/broken.so": Buffer.from("tronque") }, (root) => {
    const { rows } = checkDirectory(root);
    assert.equal(rows.length, 1);
    assert.equal(rows[0].ok, false);
  });
});
