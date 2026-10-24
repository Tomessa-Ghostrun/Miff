// Calibration des limites de lectures de règles de l'émulateur, avec des règles SYNTHÉTIQUES (aucun rapport avec
// firestore.rules) : sert à établir, à chaque exécution, les limites sur lesquelles repose la mesure (voir measure.js).
// Constaté le 2026-10-23 (firebase-tools 15.34.0, émulateur Firestore) :
//   écriture isolée : 10 ; lot d'écritures : 10 par écriture et 20 pour le lot ; get : 10 ;
//   LISTE : 20 (la documentation Firebase annonce 10 pour les requêtes : l'émulateur est plus permissif).
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { assertSafeEnvironment } from './guard.js';
import { PROJECT_ID } from './fixtures.js';

const PAD = (n, base) =>
  Array.from({ length: n }, (_, i) => `!exists(/databases/$(database)/documents/zz_pad/p${base + i})`).join(' && ') || 'true';

function rulesFor({ write = {}, read = {} }) {
  const cols = [...Object.keys(write), ...Object.keys(read)];
  const base = (col) => cols.indexOf(col) * 100;
  const writes = Object.entries(write)
    .map(([col, k]) => `match /${col}/{id} { allow create: if ${PAD(k, base(col))}; }`)
    .join('\n');
  const reads = Object.entries(read)
    .map(([col, k]) => `match /${col}/{id} { allow get: if ${PAD(k, base(col))}; allow list: if ${PAD(k, base(col))}; }`)
    .join('\n');
  return `rules_version = '2'; service cloud.firestore { match /databases/{database}/documents {\n${writes}\n${reads}\nmatch /{d=**} { allow read, write: if false; } } }`;
}

async function attempt(spec, run) {
  assertSafeEnvironment({ projectId: PROJECT_ID });
  const env = await initializeTestEnvironment({ projectId: PROJECT_ID, firestore: { rules: rulesFor(spec) } });
  try {
    await env.clearFirestore();
    await run(env.authenticatedContext('u').firestore());
    return true;
  } catch {
    return false;
  } finally {
    await env.cleanup();
  }
}

const batchOf = (cols) => (db) => {
  const batch = db.batch();
  for (const c of cols) batch.set(db.doc(`${c}/x`), { a: 1 });
  return batch.commit();
};

/** @returns {Promise<Record<string, boolean>>} chaque clé = un essai ; la valeur = accepté ou refusé par l'émulateur */
export async function probeEmulatorLimits() {
  const out = {};
  out['ecriture 10'] = await attempt({ write: { zz1: 10 } }, batchOf(['zz1']));
  out['ecriture 11'] = await attempt({ write: { zz1: 11 } }, batchOf(['zz1']));
  out['lot 10+10'] = await attempt({ write: { zz1: 10, zz2: 10 } }, batchOf(['zz1', 'zz2']));
  out['lot 10+11'] = await attempt({ write: { zz1: 10, zz2: 11 } }, batchOf(['zz1', 'zz2']));
  out['lot 7+7+6'] = await attempt({ write: { zz1: 7, zz2: 7, zz3: 6 } }, batchOf(['zz1', 'zz2', 'zz3']));
  out['lot 7+7+7'] = await attempt({ write: { zz1: 7, zz2: 7, zz3: 7 } }, batchOf(['zz1', 'zz2', 'zz3']));
  out['get 10'] = await attempt({ read: { zz1: 10 } }, (db) => db.doc('zz1/x').get());
  out['get 11'] = await attempt({ read: { zz1: 11 } }, (db) => db.doc('zz1/x').get());
  out['liste 10'] = await attempt({ read: { zz1: 10 } }, (db) => db.collection('zz1').get());
  out['liste 11'] = await attempt({ read: { zz1: 11 } }, (db) => db.collection('zz1').get());
  out['liste 20'] = await attempt({ read: { zz1: 20 } }, (db) => db.collection('zz1').get());
  out['liste 21'] = await attempt({ read: { zz1: 21 } }, (db) => db.collection('zz1').get());
  return out;
}

/** Ce que l'émulateur doit répondre pour que les hypothèses de measure.js restent valables. */
export const EXPECTED_LIMITS = {
  'ecriture 10': true,
  'ecriture 11': false,
  'lot 10+10': true,
  'lot 10+11': false,
  'lot 7+7+6': true,
  'lot 7+7+7': false,
  'get 10': true,
  'get 11': false,
  'liste 10': true,
  'liste 11': true,
  'liste 20': true,
  'liste 21': false,
};
