// Plan de test, section 22 (suite) : localisation de la photo (R-PHOTO-LOC) au TEMPS 2 de SESS-1 (clé deviceId obligatoire dans le statut
// et la position), cas PL17 (localisation obligatoire, décision du PM du 2026-11-06). Mêmes règles que firestore.rules avec la seule ligne du temps 2 basculée (voir `temps2` dans fixtures.js).
import {
  ID, inMs, suite, assertFails, assertSucceeds, commit, set, statusDoc, photoDoc, photoLegacyDoc, DEVICE_A, DEVICE_B, sessionSeed, SESSION_PATH, temps2,
  CONSENT_POSITION,
} from '../helpers/fixtures.js';

const s = suite({ transformRules: temps2 });
const M1 = `miffs/${ID.M1}`;

s.tc('PL17', "temps 2 : la photo (toujours localisée) ne porte toujours PAS de deviceId (refusé) et n'en exige pas ; la mise à jour manuelle exige celui du STATUT", async (h) => {
  await h.admin(async (f) => {
    await f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': CONSENT_POSITION });
    await f.doc(SESSION_PATH('bob')).set(sessionSeed(DEVICE_A));
  });
  const photo = (over) => photoDoc({ lat: 48.85661, lng: 2.35222, measuredAt: inMs(-60_000), ...over });
  await assertSucceeds(h.db('bob').doc(`${M1}/photos/bob`).set(photo())); // photo seule localisée, sans clé : acceptée (comme SF5 pour la photo seule)
  await assertFails(h.db('bob').doc(`${M1}/photos/bob`).set(photoLegacyDoc())); // sans localisation : refusée aussi au temps 2 (localisation obligatoire)
  await assertFails(h.db('bob').doc(`${M1}/photos/bob`).set(photo({ deviceId: DEVICE_A }))); // schéma fermé : pas de deviceId dans la photo
  // Mise à jour manuelle : le statut doit porter le bon deviceId ; sinon la photo n'est pas écrite non plus.
  const manual = (deviceId) => [set(`${M1}/photos/bob`, photo()), set(`${M1}/status/bob`, statusDoc({ hasPhoto: true, ...(deviceId ? { deviceId } : {}) }))];
  await assertFails(commit(h.db('bob'), manual(undefined)));
  await assertFails(commit(h.db('bob'), manual(DEVICE_B)));
  await assertSucceeds(commit(h.db('bob'), manual(DEVICE_A)));
});
