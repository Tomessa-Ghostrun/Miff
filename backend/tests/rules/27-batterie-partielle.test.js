// Plan de test, section 23 : rafraîchissement de la batterie par un update PARTIEL du statut (apps iOS #106 et Android #110), cas BP1 à BP10.
// Les applications écrivent seulement { batteryPct, updatedAt (heure du serveur), deviceId } sur miffs/{id}/status/{uid} avec update() :
// les règles jugent le document RÉSULTANT (fusion de la base et de la requête). AUCUNE modification de firestore.rules : ce fichier prouve que
// validStatus (R-LIEU-1 « conservé », SESS-1 fromActiveDevice) suffit. Règles telles que livrées (TEMPS 1 de SESS-1) ; le temps 2 est dans
// 28-batterie-partielle-temps2.test.js.
import {
  ID, T0, HOUR, DAY, suite, assertFails, assertSucceeds, serverTs, Timestamp, FieldValue,
  statusDoc, inMs, DEVICE_A, DEVICE_B, sessionSeed, SESSION_PATH,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const MIN = 60_000;

// Statut de bob déjà en base, plus ancien que 24 h, avec des valeurs DIFFÉRENTES dans presenceAt / lastUpdateAt / locationAccess pour que
// toute confusion ou réécriture soit visible.
const OLD_PRESENCE = T0; // 2026-01-01 10:00
const OLD_UPDATE = Timestamp.fromMillis(T0.toMillis() + HOUR); // 2026-01-01 11:00
const seedStatus = (h, over = {}, uid = 'bob') =>
  h.admin((f) =>
    f.doc(`${M1}/status/${uid}`).set(
      statusDoc({
        presence: 'out', placeId: 'p1', presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE, updateText: 'Tout va bien',
        batteryPct: 80, locationAccess: 'denied', updatedAt: T0, ...over,
      }),
    ),
  );
const seedSession = (h, uid = 'bob', deviceId = DEVICE_A) => h.admin((f) => f.doc(SESSION_PATH(uid)).set(sessionSeed(deviceId)));
const readStatus = async (h, uid = 'bob') => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(`${M1}/status/${uid}`).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};
// Mise à jour partielle exactement comme les applications : trois clés, jamais plus (une clé à `undefined` est omise de la requête).
const partial = (h, over = {}, uid = 'bob', target = uid) => {
  const data = { batteryPct: 55, updatedAt: serverTs(), deviceId: DEVICE_A, ...over };
  for (const k of Object.keys(data)) if (data[k] === undefined) delete data[k];
  return h.db(uid).doc(`${M1}/status/${target}`).update(data);
};
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const assertOnlyBatteryChanged = async (h, before, { batteryPct, deviceId = DEVICE_A }) => {
  const after = await readStatus(h);
  if (after.batteryPct !== batteryPct) throw new Error(`batteryPct attendu ${batteryPct}, lu ${after.batteryPct}`);
  if (after.deviceId !== deviceId) throw new Error(`deviceId attendu ${deviceId}, lu ${after.deviceId}`);
  if (after.updatedAt.toMillis() <= before.updatedAt.toMillis()) throw new Error('updatedAt devrait avoir avancé (heure du serveur)');
  for (const k of ['presence', 'placeId', 'updateText', 'hasPhoto', 'locationAccess', 'escalationDueAt']) {
    if (after[k] !== before[k]) throw new Error(`${k} a changé : ${before[k]} -> ${after[k]}`);
  }
  for (const k of ['presenceAt', 'lastUpdateAt']) {
    if (after[k].toMillis() !== before[k].toMillis()) throw new Error(`${k} a changé : ${before[k].toMillis()} -> ${after[k].toMillis()}`);
  }
  const keys = Object.keys(after).sort().join();
  if (keys !== Object.keys({ ...before, deviceId: 1 }).sort().join()) throw new Error(`jeu de clés modifié : ${keys}`);
};

s.tc('BP1', 'cas nominal : presenceAt ET lastUpdateAt vieux de plus de 24 h, deviceId déjà en base et égal à la session : le rafraîchissement passe, presenceAt / lastUpdateAt / locationAccess restent inchangés', async (h) => {
  await seedStatus(h, { deviceId: DEVICE_A });
  await seedSession(h, 'bob', DEVICE_A);
  const before = await readStatus(h);
  if (before.presenceAt.toMillis() > Date.now() - 24 * HOUR || before.lastUpdateAt.toMillis() > Date.now() - 24 * HOUR) {
    throw new Error('le statut de départ doit dater de plus de 24 h');
  }
  await assertSucceeds(partial(h, { batteryPct: 37 }));
  await assertOnlyBatteryChanged(h, before, { batteryPct: 37 });
  // Deuxième rafraîchissement d'affilée (le plafond de 40 par jour est un contrat du client, pas une règle) : toujours accepté.
  await assertSucceeds(partial(h, { batteryPct: 36 }));
  if ((await readStatus(h)).batteryPct !== 36) throw new Error('le second rafraîchissement aurait dû être écrit');
});

s.tc('BP2', 'temps 1, deviceId : absent de la base (statut écrit par une ancienne version) : la requête l\'apporte ; présent en base mais périmé : la requête porte celui de la session, la règle le compare à la session', async (h) => {
  await seedStatus(h); // pas de deviceId
  await seedSession(h, 'bob', DEVICE_A);
  const before = await readStatus(h);
  if ('deviceId' in before) throw new Error('le statut de départ ne doit pas porter de deviceId');
  await assertSucceeds(partial(h, { batteryPct: 12 }));
  await assertOnlyBatteryChanged(h, before, { batteryPct: 12 });
  // Présent en base avec une autre valeur (ancienne session), la session ayant changé.
  await seedStatus(h, { deviceId: DEVICE_A });
  await seedSession(h, 'bob', DEVICE_B);
  await assertFails(partial(h, { deviceId: DEVICE_A })); // la requête porte l'ancien identifiant : refusé
  await assertSucceeds(partial(h, { deviceId: DEVICE_B }));
  if ((await readStatus(h)).deviceId !== DEVICE_B) throw new Error('le statut devrait porter le deviceId de la session courante');
});

s.tc('BP2b', 'temps 1 : le rafraîchissement SANS deviceId reste accepté (applications d\'avant SESS-1) ; le contrat des clients est d\'envoyer la clé', async (h) => {
  await seedStatus(h);
  await assertSucceeds(partial(h, { deviceId: undefined, batteryPct: 44 })); // aucune session nécessaire : aucune lecture
  if ((await readStatus(h)).batteryPct !== 44) throw new Error('le rafraîchissement sans clé aurait dû être écrit au temps 1');
});

s.tc('BP3', 'refus : deviceId différent de la session de l\'appareil (appareil remplacé, identifiant inventé, nul, mal formé, session absente, session d\'un autre compte)', async (h) => {
  await seedStatus(h, { deviceId: DEVICE_A });
  await assertFails(partial(h, { deviceId: DEVICE_A })); // aucune session : le get() échoue
  await seedSession(h, 'bob', DEVICE_B);
  await assertFails(partial(h, { deviceId: DEVICE_A })); // appareil remplacé
  await assertFails(partial(h, { deviceId: 'Autre1234567890123456' }));
  await assertFails(partial(h, { deviceId: null }));
  await assertFails(partial(h, { deviceId: 42 }));
  await assertFails(partial(h, { deviceId: DEVICE_B.slice(0, 15) }));
  await assertSucceeds(partial(h, { deviceId: DEVICE_B })); // contrôle : le bon identifiant passe
  // La session d'un autre compte n'est pas la sienne.
  await seedSession(h, 'alice', DEVICE_A);
  await assertFails(partial(h, { deviceId: DEVICE_A }));
});

s.tc('BP4', 'refus : aucune création par un update partiel (statut absent) : update() puis set merge avec les trois clés seulement', async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await h.admin((f) => f.doc(`${M1}/status/bob`).delete());
  await assertFails(partial(h));
  await assertFails(h.db('bob').doc(`${M1}/status/bob`).set({ batteryPct: 55, updatedAt: serverTs(), deviceId: DEVICE_A }, { merge: true }));
  if ((await readStatus(h)) !== null) throw new Error('aucun statut ne devait être créé');
});

s.tc('BP5', 'refus : partage désactivé (members/{uid}.sharing.enabled = false), puis fiche de membre retirée', async (h) => {
  await seedStatus(h, { deviceId: DEVICE_A });
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(partial(h)); // contrôle : tant que le partage est actif
  await h.admin((f) =>
    f.doc(`${M1}/members/bob`).update({ sharing: { enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: T0 } }),
  );
  await assertFails(partial(h, { batteryPct: 20 }));
  if ((await readStatus(h)).batteryPct !== 55) throw new Error('la batterie ne devait pas être modifiée');
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete());
  await assertFails(partial(h, { batteryPct: 21 }));
});

s.tc('BP6', 'refus : batteryPct = 101, -1, texte, décimal, booléen, objet ; bornes 0 et 100 et null acceptées (contrôle)', async (h) => {
  await seedStatus(h, { deviceId: DEVICE_A });
  await seedSession(h, 'bob', DEVICE_A);
  for (const bad of [101, -1, 1000, '55', '', 'abc', 55.5, true, {}, [55]]) {
    await assertFails(partial(h, { batteryPct: bad }));
  }
  if ((await readStatus(h)).batteryPct !== 80) throw new Error('une valeur refusée ne doit rien écrire');
  await assertSucceeds(partial(h, { batteryPct: 0 }));
  await assertSucceeds(partial(h, { batteryPct: 100 }));
  await assertSucceeds(partial(h, { batteryPct: null })); // « inconnue » : schéma inchangé
});

s.tc('BP7', 'refus : updatedAt fourni par le client (différent de request.time), omis, effacé ou d\'un autre type', async (h) => {
  await seedStatus(h, { deviceId: DEVICE_A });
  await seedSession(h, 'bob', DEVICE_A);
  await assertFails(partial(h, { updatedAt: inMs(-2 * HOUR) }));
  await assertFails(partial(h, { updatedAt: inMs(2 * HOUR) }));
  await assertFails(partial(h, { updatedAt: Timestamp.fromMillis(0) }));
  await assertFails(partial(h, { updatedAt: T0 })); // même valeur que la base : ce n'est pas l'heure du serveur
  await assertFails(partial(h, { updatedAt: undefined })); // omis : updatedAt reste celui de la base, différent de request.time
  await assertFails(partial(h, { updatedAt: FieldValue.delete() }));
  await assertFails(partial(h, { updatedAt: 'now' }));
  await assertSucceeds(partial(h, { updatedAt: serverTs() })); // contrôle
});

s.tc('BP8', 'cas documentaire (limite connue, inoffensive) : escalationDueAt ancien NON nul en base => rafraîchissement refusé ; nul ou dans la fenêtre : accepté', async (h) => {
  // escalationDueAt n'est pas couvert par R-LIEU-1 : une valeur de plus de 24 h conservée par la fusion échoue la borne. Tous les clients
  // l'écrivent null (Spark : aucune escalade) ; ce test fige le comportement pour qu'un futur client qui l'utiliserait le sache.
  await seedSession(h, 'bob', DEVICE_A);
  await seedStatus(h, { deviceId: DEVICE_A, escalationDueAt: OLD_PRESENCE });
  await assertFails(partial(h));
  await seedStatus(h, { deviceId: DEVICE_A, escalationDueAt: inMs(2 * DAY) });
  await assertFails(partial(h)); // trop loin dans le futur
  await seedStatus(h, { deviceId: DEVICE_A, escalationDueAt: null });
  await assertSucceeds(partial(h));
  await seedStatus(h, { deviceId: DEVICE_A, escalationDueAt: inMs(-2 * HOUR) });
  await assertSucceeds(partial(h)); // dans la fenêtre
  await seedStatus(h, { deviceId: DEVICE_A, escalationDueAt: inMs(5 * MIN - 60_000) });
  await assertSucceeds(partial(h));
});

s.tc('BP9', 'refus : un autre utilisateur que le propriétaire (créatrice, membre sans partage, étranger, visiteur) ; le statut de bob ne change pas', async (h) => {
  await seedStatus(h, { deviceId: DEVICE_A });
  for (const uid of ['alice', 'bob', 'carol', 'zoe']) await seedSession(h, uid, DEVICE_A);
  const before = await readStatus(h);
  await assertFails(partial(h, {}, 'alice', 'bob')); // alice partage, a son statut, session valide
  await assertFails(partial(h, {}, 'carol', 'bob'));
  await assertFails(partial(h, {}, 'zoe', 'bob'));
  await assertFails(partial(h, {}, 'eve', 'bob'));
  await assertFails(h.anon().doc(`${M1}/status/bob`).update({ batteryPct: 55, updatedAt: serverTs(), deviceId: DEVICE_A }));
  if (!same(await readStatus(h), before)) throw new Error('le statut de bob ne devait pas changer');
  await assertSucceeds(partial(h)); // contrôle : le propriétaire
});

s.tc('BP10', 'pas de contournement : le rafraîchissement n\'ajoute aucune clé et ne fait passer aucune valeur hors schéma, ni nouvelle ancienne heure', async (h) => {
  await seedStatus(h, { deviceId: DEVICE_A });
  await seedSession(h, 'bob', DEVICE_A);
  await assertFails(partial(h, { lat: 48.85 }));
  await assertFails(partial(h, { lng: 2.35 }));
  await assertFails(partial(h, { batteryAt: serverTs() }));
  await assertFails(partial(h, { locationAccess: 'toujours' }));
  await assertFails(partial(h, { presence: 'banane' }));
  await assertFails(partial(h, { presenceAt: inMs(-3 * DAY) })); // nouvelle valeur ancienne : refusée même dans un update partiel
});
