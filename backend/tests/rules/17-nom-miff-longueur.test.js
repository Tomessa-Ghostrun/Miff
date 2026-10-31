// Plan de test, section 16 : MIFF-1, longueur du nom d'une Miff (ticket docs/tickets/miff.md, §5.2, décision QM6).
//   ND1 : 40 caractères ASCII acceptés, 41 refusés (à la création et au renommage)
//   ND2 : la limite du client Android (40 unités UTF-16 au plus, emojis compris) est toujours acceptée par les règles :
//         20 emojis (40 unités UTF-16), 40 lettres accentuées (80 octets UTF-8)
//   ND3 : mesure d'information : comment les règles comptent un emoji (code point ou unité UTF-16) ; non bloquant
// Le client compte en unités UTF-16 (`String.length` en Kotlin) : jamais moins que les caractères que les règles peuvent
// compter, donc un nom accepté par le client est accepté par les règles, quelle que soit leur façon de compter (sauf en octets,
// que ND2 exclut). Chaque cas isole UNE protection (isStr(d.name, 1, 40) de validMiffCreate et validMiffUpdate).
import assert from 'node:assert/strict';
import { ID, CODE, commit, createShareOps, update, suite, assertFails, assertSucceeds } from '../helpers/fixtures.js';

const s = suite();
const all = (ops) => Object.values(ops);
const EMOJI = '\u{1F3E0}'; // maison : 1 code point, 2 unités UTF-16, 4 octets UTF-8

const create = (db, name) => commit(db('eve'), all(createShareOps({ uid: 'eve', name })));

s.tc('ND1', 'nom de 40 caractères accepté, de 41 refusé (création)', async ({ db }) => {
  await assertFails(create(db, 'a'.repeat(41)));
  await assertSucceeds(create(db, 'a'.repeat(40)));
});

s.tc('ND1b', 'renommage : 40 caractères acceptés, 41 refusés', async ({ db }) => {
  const rename = (name) =>
    commit(db('alice'), [
      update(`miffs/${ID.M1}`, { name }),
      update(`joinCodes/${CODE.M1}`, { miffName: name }),
    ]);
  await assertFails(rename('b'.repeat(41)));
  await assertSucceeds(rename('b'.repeat(40)));
});

s.tc('ND2', 'la limite du client (40 unités UTF-16) est acceptée : 20 emojis, 40 lettres accentuées', async ({ db }) => {
  await assertSucceeds(create(db, EMOJI.repeat(20)));
});

s.tc('ND2b', '40 lettres accentuées (80 octets UTF-8) sont acceptées : les règles comptent des caractères, pas des octets', async ({ db }) => {
  await assertSucceeds(create(db, 'é'.repeat(40)));
});

s.tc('ND3', 'information : 30 emojis (30 code points, 60 unités UTF-16) : accepté ou refusé selon la façon de compter des règles', async ({ db }) => {
  let accepted = true;
  try {
    await assertSucceeds(create(db, EMOJI.repeat(30)));
  } catch {
    accepted = false;
  }
  console.log(
    `ND3 : 30 emojis ${accepted ? 'ACCEPTÉS (les règles comptent des code points)' : 'REFUSÉS (les règles comptent des unités UTF-16)'} ; ` +
      'le client plafonne à 40 unités UTF-16 dans les deux cas.',
  );
});

export default s;
