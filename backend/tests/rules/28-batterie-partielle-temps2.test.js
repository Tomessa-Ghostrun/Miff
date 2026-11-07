// Plan de test, section 23 (suite) : rafraîchissement partiel de la batterie au TEMPS 2 de SESS-1 (deviceId OBLIGATOIRE), cas BP11 à BP13.
// Mêmes règles, une seule ligne basculée (`temps2` de fixtures.js). AUCUNE modification de firestore.rules.
import {
  ID, T0, HOUR, suite, assertFails, assertSucceeds, serverTs, Timestamp,
  statusDoc, DEVICE_A, DEVICE_B, sessionSeed, SESSION_PATH, temps2,
} from '../helpers/fixtures.js';

const s = suite({ transformRules: temps2 });
const M1 = `miffs/${ID.M1}`;
const OLD_PRESENCE = T0;
const OLD_UPDATE = Timestamp.fromMillis(T0.toMillis() + HOUR);
const seedStatus = (h, over = {}) =>
  h.admin((f) =>
    f.doc(`${M1}/status/bob`).set(
      statusDoc({ presence: 'out', presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE, locationAccess: 'denied', updatedAt: T0, ...over }),
    ),
  );
const seedSession = (h, deviceId = DEVICE_A) => h.admin((f) => f.doc(SESSION_PATH('bob')).set(sessionSeed(deviceId)));
const readStatus = async (h) => {
  let data = null;
  await h.admin(async (f) => {
    data = (await f.doc(`${M1}/status/bob`).get()).data();
  });
  return data;
};
const partial = (h, over = {}) => {
  const data = { batteryPct: 55, updatedAt: serverTs(), deviceId: DEVICE_A, ...over };
  for (const k of Object.keys(data)) if (data[k] === undefined) delete data[k];
  return h.db('bob').doc(`${M1}/status/bob`).update(data);
};

s.tc('BP11', 'temps 2 : le rafraîchissement AVEC le bon deviceId passe sur un statut de plus de 24 h (presenceAt, lastUpdateAt, locationAccess inchangés) ; même chose si la base n\'avait pas encore de deviceId', async (h) => {
  await seedSession(h, DEVICE_A);
  for (const base of [{ deviceId: DEVICE_A }, {}]) {
    await seedStatus(h, base);
    const before = await readStatus(h);
    await assertSucceeds(partial(h, { batteryPct: 61 }));
    const after = await readStatus(h);
    if (after.batteryPct !== 61 || after.deviceId !== DEVICE_A) throw new Error('batterie et deviceId devraient être écrits');
    if (
      after.presenceAt.toMillis() !== before.presenceAt.toMillis() ||
      after.lastUpdateAt.toMillis() !== before.lastUpdateAt.toMillis() ||
      after.locationAccess !== 'denied'
    ) {
      throw new Error('presenceAt, lastUpdateAt et locationAccess doivent rester inchangés');
    }
  }
});

s.tc('BP12', 'temps 2 : SANS deviceId dans la requête, refusé quand la base n\'en porte pas, ou porte un identifiant périmé ; deviceId faux ou nul refusé', async (h) => {
  await seedStatus(h); // base sans deviceId
  await seedSession(h, DEVICE_A);
  await assertFails(partial(h, { deviceId: undefined }));
  await assertFails(partial(h, { deviceId: DEVICE_B }));
  await assertFails(partial(h, { deviceId: null }));
  await seedStatus(h, { deviceId: DEVICE_A }); // base périmée : la session est passée à B
  await seedSession(h, DEVICE_B);
  await assertFails(partial(h, { deviceId: undefined }));
  await assertFails(partial(h, { deviceId: DEVICE_A }));
  await assertSucceeds(partial(h, { deviceId: DEVICE_B }));
});

s.tc('BP13', 'temps 2, LIMITE CONNUE (voir SF9) : sans deviceId dans la requête, le document résultant garde celui de la base ; il passe s\'il est égal à la session. Contrat : les clients l\'envoient toujours', async (h) => {
  await seedStatus(h, { deviceId: DEVICE_A });
  await seedSession(h, DEVICE_A);
  await assertSucceeds(partial(h, { deviceId: undefined }));
});
