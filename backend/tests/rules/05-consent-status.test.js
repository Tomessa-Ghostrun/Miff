// Plan de test, section 5 : consentement et statut de position (S1 à S19)
import {
  ID, T0, DAY, HOUR, suite, assertFails, assertSucceeds, commit, update, set, del, serverTs, Timestamp,
  statusDoc, photoDoc, bytes, inMs, sharingOnNow, readRules,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;

// Désactivation du partage par bob : on conserve consentedAt / consentVersion d'origine (preuve).
const bobSharingOff = () => ({ enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: serverTs() });

s.tc('S1', 'activer le partage : consentedAt = heure serveur et version du texte', async ({ db }) => {
  await assertSucceeds(db('carol').doc(`${M1}/members/carol`).update({ sharing: sharingOnNow() }));
});

s.tc('S2', 'activer le partage avec une date fournie par le client : refusé', async ({ db }) => {
  const sharing = { enabled: true, consentedAt: Timestamp.now(), consentVersion: 'v1', revokedAt: null };
  await assertFails(db('carol').doc(`${M1}/members/carol`).update({ sharing }));
});

s.tc('S3', 'activer le partage sans consentVersion : refusé', async ({ db }) => {
  const ref = db('carol').doc(`${M1}/members/carol`);
  await assertFails(ref.update({ sharing: { enabled: true, consentedAt: serverTs(), consentVersion: null, revokedAt: null } }));
  await assertFails(ref.update({ sharing: { enabled: true, consentedAt: serverTs(), revokedAt: null } }));
});

s.tc('S4', 'désactiver le partage : statut et photo supprimés dans le même batch', async ({ db }) => {
  await assertSucceeds(
    commit(db('bob'), [
      update(`${M1}/members/bob`, { sharing: bobSharingOff() }),
      del(`${M1}/status/bob`),
      del(`${M1}/photos/bob`),
    ]),
  );
});

s.tc('S5', 'désactiver le partage sans supprimer le statut : refusé', async ({ db }) => {
  await assertFails(db('bob').doc(`${M1}/members/bob`).update({ sharing: bobSharingOff() }));
});

s.tc('S6', 'désactiver en effaçant consentedAt (preuve de consentement) : refusé', async ({ db }) => {
  const sharing = { enabled: false, consentedAt: null, consentVersion: 'v1', revokedAt: serverTs() };
  await assertFails(
    commit(db('bob'), [
      update(`${M1}/members/bob`, { sharing }),
      del(`${M1}/status/bob`),
      del(`${M1}/photos/bob`),
    ]),
  );
});

s.tc('S7', 'partage désactivé : impossible de créer son statut', async ({ db }) => {
  await assertFails(db('carol').doc(`${M1}/status/carol`).set(statusDoc()));
});

s.tc('S8', 'activer le partage et créer son statut dans le même batch : accepté', async ({ db }) => {
  await assertSucceeds(
    commit(db('carol'), [update(`${M1}/members/carol`, { sharing: sharingOnNow() }), set(`${M1}/status/carol`, statusDoc())]),
  );
});

s.tc('S9', 'écrire le statut d\'un autre membre : refusé', async ({ db }) => {
  await assertFails(db('bob').doc(`${M1}/status/alice`).set(statusDoc()));
});

s.tc('S10', 'mettre à jour son statut (tous les champs, heure serveur) : accepté', async ({ db }) => {
  await assertSucceeds(db('bob').doc(`${M1}/status/bob`).set(statusDoc({ updateText: 'Bien arrivé' })));
});

s.tc('S11', 'statut avec clé inconnue ou coordonnées : refusé', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/status/bob`);
  await assertFails(ref.set(statusDoc({ lat: 48.85 })));
  await assertFails(ref.set(statusDoc({ lng: 2.35 })));
  await assertFails(ref.set(statusDoc({ coords: { lat: 48.85, lng: 2.35 } })));
});

s.tc('S12', 'statut avec presence invalide, batterie > 100 ou texte > 140 caractères : refusé', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/status/bob`);
  await assertFails(ref.set(statusDoc({ presence: 'banane' })));
  await assertFails(ref.set(statusDoc({ batteryPct: 150 })));
  await assertFails(ref.set(statusDoc({ updateText: 'x'.repeat(141) })));
});

s.tc('S13', 'presenceAt dans le futur (+1 jour) ou trop ancien (-3 jours) refusé ; -2 h accepté', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/status/bob`);
  await assertFails(ref.set(statusDoc({ presenceAt: inMs(DAY) })));
  await assertFails(ref.set(statusDoc({ presenceAt: inMs(-3 * DAY) })));
  await assertSucceeds(ref.set(statusDoc({ presenceAt: inMs(-2 * HOUR) })));
});

s.tc('S14', 'updatedAt fourni par le client : refusé', async ({ db }) => {
  await assertFails(db('bob').doc(`${M1}/status/bob`).set(statusDoc({ updatedAt: Timestamp.now() })));
});

s.tc('S15', 'statut avec une clé manquante : refusé', async ({ db }) => {
  // Chacune des dix clés, une par une. (hasAll est redondant avec la lecture de chaque clé par les règles :
  // une clé absente est de toute façon une erreur d'évaluation => refus. Voir tests/tools/mutations.mjs.)
  for (const key of Object.keys(statusDoc())) {
    const doc = statusDoc();
    delete doc[key];
    await assertFails(db('bob').doc(`${M1}/status/bob`).set(doc));
  }
});

s.tc('S16', 'photo : 150 000 octets / 24 h acceptée ; 150 001 octets ou +30 jours refusée', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/photos/bob`);
  await assertSucceeds(ref.set(photoDoc({ jpeg: bytes(150000), expiresAt: inMs(DAY) })));
  await assertFails(ref.set(photoDoc({ jpeg: bytes(150001), expiresAt: inMs(DAY) })));
  await assertFails(ref.set(photoDoc({ jpeg: bytes(1000), expiresAt: inMs(30 * DAY) })));
});

s.tc('S17', 'écrire la photo d\'un autre membre : refusé', async ({ db }) => {
  await assertFails(db('bob').doc(`${M1}/photos/alice`).set(photoDoc()));
});

s.tc('S18', 'la créatrice purge le statut d\'un membre ; l\'inverse est refusé', async ({ db }) => {
  await assertSucceeds(db('alice').doc(`${M1}/status/bob`).delete());
  await assertFails(db('bob').doc(`${M1}/status/alice`).delete());
});

s.tc('S19', 'revue de schéma : ni le statut ni la photo n\'ont de champ de coordonnées', async ({ admin }) => {
  const forbidden = ['lat', 'lng', 'latitude', 'longitude', 'coords', 'coordinates', 'geopoint', 'position', 'address'];
  const rules = readRules();
  // 1) Les listes de clés autorisées par les règles (hasOnly) du statut et de la photo.
  for (const fn of ['validStatus', 'validPhoto']) {
    const body = rules.slice(rules.indexOf(`function ${fn}(`));
    const hasOnly = body.match(/hasOnly\(\[([^\]]*)\]\)/);
    if (!hasOnly) throw new Error(`liste hasOnly introuvable dans ${fn}`);
    const keys = hasOnly[1].split(',').map((k) => k.trim().replace(/'/g, ''));
    const bad = keys.filter((k) => forbidden.includes(k.toLowerCase()));
    if (bad.length > 0) throw new Error(`${fn} autorise des champs de position : ${bad.join(', ')}`);
  }
  // 2) Les documents réellement présents dans les données de départ.
  await admin(async (f) => {
    const snaps = await Promise.all([f.doc(`${M1}/status/alice`).get(), f.doc(`${M1}/status/bob`).get()]);
    for (const snap of snaps) {
      const bad = Object.keys(snap.data()).filter((k) => forbidden.includes(k.toLowerCase()));
      if (bad.length > 0) throw new Error(`${snap.ref.path} contient des champs de position : ${bad.join(', ')}`);
    }
  });
});
