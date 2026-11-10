// Plan de test, section 24 (suite) : mode voyage au TEMPS 2 de SESS-1 (deviceId OBLIGATOIRE), cas VY25. Mêmes règles, une seule ligne basculée
// (`temps2` de fixtures.js). AUCUNE modification supplémentaire de firestore.rules.
import {
  ID, CODE, DAY, suite, assertFails, assertSucceeds, statusDoc, inMs, DEVICE_A, DEVICE_B, sessionSeed, SESSION_PATH, temps2,
} from '../helpers/fixtures.js';

const s = suite({ transformRules: temps2 });
const M3 = `miffs/${ID.M3}`;
const dayOf = (ts) => new Date(ts.toMillis()).toISOString().slice(0, 10);
const travel = (over = {}) => {
  const until = inMs(7 * DAY);
  return statusDoc({ presence: 'travel', placeId: null, batteryPct: null, travelUntil: until, travelUntilDay: dayOf(until), ...over });
};

s.tc('VY25', 'temps 2 : le voyage SANS deviceId est refusé (premium compris), avec un périmé refusé, avec celui de la session accepté ; la désactivation suit la même règle', async (h) => {
  await h.seedMiff({
    miffId: ID.M3, name: 'Voyageurs', code: CODE.M3, ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: true, key: ID.M3 }], status: ['dave'],
  });
  await h.admin((f) => f.doc(SESSION_PATH('dave')).set(sessionSeed(DEVICE_A)));
  const put = (data) => h.db('dave').doc(`${M3}/status/dave`).set(data);
  await assertFails(put(travel()));
  await assertFails(put(travel({ deviceId: DEVICE_B })));
  await assertSucceeds(put(travel({ deviceId: DEVICE_A })));
  // Désactivation : sans la clé, refusée au temps 2 comme tout statut ; avec la clé, acceptée.
  await assertFails(put(statusDoc({ presence: 'unknown', placeId: null, batteryPct: null })));
  await assertSucceeds(put(statusDoc({ presence: 'unknown', placeId: null, batteryPct: null, deviceId: DEVICE_A })));
});
