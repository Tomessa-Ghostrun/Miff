// Plan de test, section 22 : localisation de la photo d'update (R-PHOTO-LOC, décision du PM du 2026-11-05), cas PL1 à PL20.
// Règles PRÉPARÉES et NON déployées. Le document photo accepte trois clés FACULTATIVES `lat`, `lng` et `measuredAt` (la position figée au moment de la
// photo et l'heure de sa mesure, décision du PM du 2026-11-06 : measuredAt obligatoire avec la paire, interdite sans) ; elles sont effacées avec la photo ; une photo localisée n'est lisible que par son auteur ou par un membre qui a publié sa propre
// position. Les applications actuelles (photo sans lat ni lng, photo supprimée dans les lots) ne doivent JAMAIS être refusées à tort.
import {
  ID, CODE, T0, HOUR, DAY, suite, assertFails, assertSucceeds, commit, update, set, del, serverTs, inMs, bytes, FieldValue,
  statusDoc, photoDoc, positionSeed, sharingOnNowV3, CONSENT_POSITION,
  leaveOps, leaveWithPositionOps, removeMemberOps, removeMemberWithPositionOps,
  DEVICE_A, DEVICE_B, sessionSeed, SESSION_PATH, readRules,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const V3 = CONSENT_POSITION;

// --- Préparation d'état (règles désactivées) -------------------------------------------------------------------
const consentV3 = (h, ...uids) =>
  h.admin(async (f) => {
    for (const uid of uids) await f.doc(`${M1}/members/${uid}`).update({ 'sharing.consentVersion': V3 });
  });
const seedPositions = (h, ...uids) =>
  h.admin(async (f) => {
    for (const uid of uids) await f.doc(`${M1}/positions/${uid}`).set(positionSeed());
  });
/** Photo localisée déjà enregistrée (état injecté). */
const seedLocPhoto = (h, uid, over = {}) =>
  h.admin((f) => f.doc(`${M1}/photos/${uid}`).set(photoDoc({ createdAt: T0, lat: 48.85661, lng: 2.35222, measuredAt: T0, ...over })));
const seedPlainPhoto = (h, uid, over = {}) => h.admin((f) => f.doc(`${M1}/photos/${uid}`).set(photoDoc({ createdAt: T0, ...over })));
const docExists = async (h, path) => {
  let found;
  await h.admin(async (f) => {
    found = (await f.doc(path).get()).exists;
  });
  return found;
};
const photoRef = (h, as, of = as) => h.db(as).doc(`${M1}/photos/${of}`);
const MIN = 60_000;
/** Heure de mesure récente (une minute avant la photo). */
const mAt = () => inMs(-MIN);
/** Localisation telle qu'un client l'écrit : la paire lat / lng ET l'heure de la mesure (R-PHOTO-LOC, décision du PM du 2026-11-06). */
const loc = (over = {}) => ({ lat: 48.85661, lng: 2.35222, measuredAt: mAt(), ...over });
const bobRevoke = () => ({ enabled: false, consentedAt: T0, consentVersion: V3, revokedAt: serverTs() });

// --- Compatibilité : les applications actuelles ----------------------------------------------------------------
s.tc('PL1', 'applications actuelles : une photo SANS lat ni lng (consentement v1) est écrite, remplacée et lue comme avant ; lat / lng nuls acceptés', async (h) => {
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(photoDoc())); // le document exact d'aujourd'hui (jpeg, createdAt, expiresAt)
  await assertSucceeds(ref.set(photoDoc({ jpeg: bytes(150000) })));
  await assertSucceeds(ref.set(photoDoc({ lat: null, lng: null }))); // « pas de localisation » écrit explicitement
  await assertSucceeds(ref.update({ createdAt: serverTs(), expiresAt: inMs(DAY) })); // mise à jour partielle d'une photo non localisée
  // Lecture par un membre qui n'a publié AUCUNE position (cas des applications actuelles) : inchangée.
  await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  // Photo non localisée avec lat / lng nuls : lisible aussi sans position publiée.
  await seedPlainPhoto(h, 'alice', { lat: null, lng: null });
  await assertSucceeds(photoRef(h, 'bob', 'alice').get());
});

s.tc('PL2', 'schéma fermé : aucune autre clé que jpeg, createdAt, expiresAt, lat, lng, measuredAt (ni deviceId, ni précision, ni adresse, ni autre heure)', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(photoDoc(loc())));
  for (const extra of [{ deviceId: DEVICE_A }, { accuracyM: 5 }, { address: 'Paris' }, { locationAt: serverTs() }, { updatedAt: serverTs() }, { speed: 1 }, { position: { lat: 1, lng: 1 } }]) {
    await assertFails(ref.set(photoDoc({ ...loc(), ...extra })));
    await assertFails(ref.set(photoDoc(extra)));
  }
  // Une clé obligatoire manquante reste refusée (avec ou sans localisation).
  const { jpeg: _j, ...noJpeg } = photoDoc(loc());
  await assertFails(ref.set(noJpeg));
  const { expiresAt: _e, ...noExpires } = photoDoc(loc());
  await assertFails(ref.set(noExpires));
});

// --- Écriture : consentement et partage ------------------------------------------------------------------------
s.tc('PL3', 'photo localisée : refusée avec le consentement v1 ou v2 (sans position), acceptée avec v03 ou plus (même condition que la position)', async (h) => {
  const ref = photoRef(h, 'bob');
  for (const v of ['v1', '2026-10-v1', '2026-10-v2', '2026-10-v02', '2026-11-v3', '2026-11-v00', '2026-11-v01', '2026-11-v003', '2026-13-v03', null]) {
    await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': v }));
    await assertFails(ref.set(photoDoc(loc())));
    // La même photo SANS localisation reste acceptée avec ce consentement : seul le texte de la position est exigé pour des coordonnées.
    if (v !== null) await assertSucceeds(ref.set(photoDoc()));
  }
  for (const v of ['2026-11-v03', '2026-12-v04', '2027-01-v05', '2026-11-v99']) {
    await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': v }));
    await assertSucceeds(ref.set(photoDoc(loc())));
  }
});

s.tc('PL4', 'photo localisée : partage arrêté refusé ; activer le partage (texte v3) et poser la photo dans le même lot accepté ; révoquer et poser refusé', async (h) => {
  const carol = h.db('carol'); // partage arrêté dans le jeu de départ
  await assertFails(carol.doc(`${M1}/photos/carol`).set(photoDoc(loc())));
  await assertFails(carol.doc(`${M1}/photos/carol`).set(photoDoc())); // contrôle : comme avant, refusée aussi sans localisation
  // Avec le consentement v1 dans le même lot : la photo localisée est refusée (le texte ne couvre pas la position).
  await assertFails(commit(carol, [update(`${M1}/members/carol`, { sharing: { enabled: true, consentedAt: serverTs(), consentVersion: 'v1', revokedAt: null } }), set(`${M1}/photos/carol`, photoDoc(loc()))]));
  await assertSucceeds(commit(carol, [update(`${M1}/members/carol`, { sharing: sharingOnNowV3() }), set(`${M1}/photos/carol`, photoDoc(loc()))]));
  // Révoquer et poser la photo dans le même lot : refusé (getAfter).
  await consentV3(h, 'bob');
  await assertFails(commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: bobRevoke() }), set(`${M1}/photos/bob`, photoDoc(loc()))]));
});

s.tc('PL5', "photo localisée : seul l'intéressé l'écrit (pas un autre membre, pas la créatrice, pas un étranger, pas un visiteur)", async (h) => {
  await consentV3(h, 'bob', 'alice');
  for (const who of ['alice', 'carol', 'zoe', 'eve']) await assertFails(photoRef(h, who, 'bob').set(photoDoc(loc())));
  await assertFails(h.anon().doc(`${M1}/photos/bob`).set(photoDoc(loc())));
  await assertSucceeds(photoRef(h, 'bob').set(photoDoc(loc())));
  // L'auteur ne peut pas non plus modifier la localisation d'une photo d'un autre par une mise à jour.
  await assertFails(photoRef(h, 'alice', 'bob').update({ lat: 1, lng: 1, createdAt: serverTs() }));
});

s.tc('PL6', 'coordonnées : lat et lng vont ensemble (nombres valides ou nulles) ; bornes incluses ; types, valeurs hors plage, NaN et infini refusés', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  // Une seule clé, ou une seule valeur : refusé.
  await assertFails(ref.set(photoDoc({ lat: 48.85661, measuredAt: mAt() })));
  await assertFails(ref.set(photoDoc({ lng: 2.35222, measuredAt: mAt() })));
  await assertFails(ref.set(photoDoc({ lat: 48.85661, lng: null, measuredAt: mAt() })));
  await assertFails(ref.set(photoDoc({ lat: null, lng: 2.35222, measuredAt: mAt() })));
  // Paire à moitié nulle SANS heure de mesure (seule la photo sans localisation n'en porte pas) : refusé, la valeur non nulle n'est jamais stockée sans contrôle.
  await assertFails(ref.set(photoDoc({ lat: null, lng: 2.35222 })));
  await assertFails(ref.set(photoDoc({ lat: 48.85661, lng: null })));
  await assertFails(ref.set(photoDoc({ lat: null })));
  await assertFails(ref.set(photoDoc({ lng: null })));
  // Bornes.
  for (const [lat, lng] of [[90, 180], [-90, -180], [0, 0], [-0.00001, 0.00001]]) await assertSucceeds(ref.set(photoDoc(loc({ lat, lng }))));
  for (const [lat, lng] of [[90.00001, 0], [-90.00001, 0], [0, 180.00001], [0, -180.00001], [91, 0], [0, 181], [1000, 1000]]) {
    await assertFails(ref.set(photoDoc(loc({ lat, lng }))));
  }
  // Types et valeurs non finies.
  for (const bad of [Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY, '48.85', true, [1], { v: 1 }]) {
    await assertFails(ref.set(photoDoc(loc({ lat: bad, lng: 2.35 }))));
    await assertFails(ref.set(photoDoc(loc({ lat: 48.85, lng: bad }))));
  }
  // Mise à jour partielle qui ajoute une localisation à une photo existante sans localisation : jugée sur le résultat.
  await assertSucceeds(ref.set(photoDoc()));
  await assertSucceeds(ref.update({ createdAt: serverTs(), lat: 48.85661, lng: 2.35222, measuredAt: mAt() }));
  await assertFails(ref.update({ createdAt: serverTs(), lat: 91 })); // la paire et l'heure restent complètes, lat hors plage
  // Les autres contrôles de la photo restent actifs avec une localisation.
  await assertFails(ref.set(photoDoc(loc({ jpeg: bytes(150001) }))));
  await assertFails(ref.set(photoDoc(loc({ jpeg: bytes(0) }))));
  await assertFails(ref.set(photoDoc(loc({ expiresAt: inMs(-HOUR) }))));
  await assertFails(ref.set(photoDoc(loc({ expiresAt: inMs(30 * DAY) }))));
  await assertFails(ref.set(photoDoc(loc({ createdAt: T0 }))));
  await assertSucceeds(ref.set(photoDoc(loc({ expiresAt: inMs(25 * HOUR - 60_000) }))));
});

// --- Lecture : réciprocité formelle ----------------------------------------------------------------------------
s.tc('PL7', 'lecture d\'une photo localisée : par un membre qui a publié sa propre position et dont le partage est actif ; refusée sans position publiée', async (h) => {
  await consentV3(h, 'alice', 'bob');
  await seedLocPhoto(h, 'bob');
  await assertFails(photoRef(h, 'alice', 'bob').get()); // alice n'a publié aucune position
  await seedPositions(h, 'alice');
  const snap = await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  if (snap.data().lat !== 48.85661 || snap.data().lng !== 2.35222 || !snap.data().measuredAt) throw new Error('la localisation et son heure de mesure devraient être lues telles qu\'enregistrées');
  // La position publiée de la CIBLE ne suffit pas à son lecteur : seule la position du lecteur compte.
  await h.admin((f) => f.doc(`${M1}/positions/alice`).delete());
  await seedPositions(h, 'bob');
  await assertFails(photoRef(h, 'alice', 'bob').get());
});

s.tc('PL8', "l'auteur relit sa propre photo localisée même sans position publiée (transparence) ; la photo non localisée d'un autre reste lisible sans position", async (h) => {
  await seedLocPhoto(h, 'bob');
  await assertSucceeds(photoRef(h, 'bob').get()); // bob : partage actif, aucune position
  await seedPlainPhoto(h, 'alice');
  await assertSucceeds(photoRef(h, 'bob', 'alice').get()); // contrôle : non localisée, sans position publiée
});

s.tc('PL9', 'lecture refusée aux autres : membre dont le partage est arrêté (même avec une position), étranger, membre d\'une autre Miff, visiteur ; jamais de liste', async (h) => {
  await consentV3(h, 'alice', 'bob');
  await seedLocPhoto(h, 'bob');
  await seedPositions(h, 'alice', 'carol');
  await assertSucceeds(photoRef(h, 'alice', 'bob').get()); // contrôle : le cas nominal passe
  await assertFails(photoRef(h, 'carol', 'bob').get()); // carol : position injectée, mais partage arrêté
  for (const who of ['eve', 'zoe', 'dave']) await assertFails(photoRef(h, who, 'bob').get());
  await assertFails(h.anon().doc(`${M1}/photos/bob`).get());
  await assertFails(h.db('alice').collection(`${M1}/photos`).get());
});

s.tc('PL10', 'la créatrice n\'a aucun droit de lecture de plus : sans position publiée elle ne lit pas la photo localisée d\'un membre', async (h) => {
  await seedLocPhoto(h, 'bob');
  await assertFails(photoRef(h, 'alice', 'bob').get()); // alice est créatrice, partage actif, mais aucune position
  await consentV3(h, 'alice');
  await seedPositions(h, 'alice');
  await assertSucceeds(photoRef(h, 'alice', 'bob').get());
});

s.tc('PL11', "reliquat (décision du PM du 2026-11-06, pas de contrôle « l'auteur partage encore ») : une photo localisée restée après l'arrêt du partage de son auteur reste lisible jusqu'à son expiration seulement ; la photo non localisée garde le comportement d'avant", async (h) => {
  await consentV3(h, 'alice');
  await seedPositions(h, 'alice');
  await seedLocPhoto(h, 'bob', { expiresAt: inMs(DAY) });
  await seedPlainPhoto(h, 'carol'); // carol ne partage pas : sa photo non localisée ne devrait pas exister, comportement d'avant conservé
  await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  // État impossible par les règles (les lots de purge exigent la suppression de la photo avec l'arrêt du partage) : reliquat injecté.
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ sharing: { enabled: false, consentedAt: T0, consentVersion: V3, revokedAt: T0 } }));
  await assertSucceeds(photoRef(h, 'alice', 'bob').get()); // exposition bornée par expiresAt, pas par un contrôle de l'auteur
  await assertSucceeds(photoRef(h, 'alice', 'carol').get()); // inchangé (non localisée)
  // Le reliquat expiré n'est plus lisible (l'expiration est le seul garde-fou du reliquat).
  await seedLocPhoto(h, 'bob', { expiresAt: inMs(-60_000) });
  await assertFails(photoRef(h, 'alice', 'bob').get());
  // Un lecteur qui a lui-même arrêté son partage ne lit rien, reliquat ou non.
  await seedLocPhoto(h, 'bob', { expiresAt: inMs(DAY) });
  await h.admin((f) => f.doc(`${M1}/positions/carol`).set(positionSeed()));
  await assertFails(photoRef(h, 'carol', 'bob').get());
});

s.tc('PL12', 'expiration inchangée : une photo localisée expirée est illisible pour tous (auteur compris) ; absente = introuvable sans erreur de droits ; 24 h + 1 h tolérée', async (h) => {
  await consentV3(h, 'alice');
  await seedPositions(h, 'alice');
  await seedLocPhoto(h, 'bob', { expiresAt: inMs(-60_000) });
  await assertFails(photoRef(h, 'alice', 'bob').get());
  await assertFails(photoRef(h, 'bob').get());
  // Photo ABSENTE : « introuvable » sans erreur de droits, que l'on ait publié sa position ou non : aucune fuite sur l'existence d'une photo. Cela ne vaut
  // QUE pour une photo absente : pour une photo PRÉSENTE, le refus (sans position publiée) face à la lecture réussie d'une photo non localisée apprend
  // qu'elle est localisée. Acceptable : status.hasPhoto révèle déjà qu'une photo existe, et l'information « localisée » n'est pas une coordonnée.
  await h.admin((f) => f.doc(`${M1}/photos/bob`).delete());
  const withPosition = await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  if (withPosition.exists) throw new Error('aucune photo attendue');
  await h.admin((f) => f.doc(`${M1}/positions/alice`).delete());
  const withoutPosition = await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  if (withoutPosition.exists) throw new Error('aucune photo attendue');
  // 24 h + 1 h de tolérance à l'écriture, lisible tant qu'elle n'est pas expirée.
  await consentV3(h, 'bob');
  await seedPositions(h, 'alice');
  await assertSucceeds(photoRef(h, 'bob').set(photoDoc(loc({ expiresAt: inMs(25 * HOUR - 60_000) }))));
  await assertSucceeds(photoRef(h, 'alice', 'bob').get());
});

// --- Effacement avec la photo ----------------------------------------------------------------------------------
s.tc('PL13', 'effacement : la localisation part avec la photo dans TOUS les lots existants (arrêt du partage, départ, retrait, suppression de la Miff) ; ces lots ne changent pas', async (h) => {
  await consentV3(h, 'alice', 'bob');
  await seedPositions(h, 'alice', 'bob');
  // Arrêt du partage de bob : statut, photo (localisée), position supprimés.
  await seedLocPhoto(h, 'bob');
  const revoke = () => update(`${M1}/members/bob`, { sharing: bobRevoke() });
  await assertFails(commit(h.db('bob'), [revoke(), del(`${M1}/status/bob`), del(`${M1}/positions/bob`)])); // photo oubliée : refusé
  await assertSucceeds(commit(h.db('bob'), [revoke(), del(`${M1}/status/bob`), del(`${M1}/photos/bob`), del(`${M1}/positions/bob`)]));
  if (await docExists(h, `${M1}/photos/bob`)) throw new Error('la photo localisée devrait avoir disparu');
  // Départ de bob (réactivé) avec une photo localisée.
  await h.admin(async (f) => {
    await f.doc(`${M1}/members/bob`).update({ sharing: { enabled: true, consentedAt: T0, consentVersion: V3, revokedAt: null } });
    await f.doc(`${M1}/status/bob`).set(statusDoc({ updatedAt: T0 }));
    await f.doc(`${M1}/positions/bob`).set(positionSeed());
  });
  await seedLocPhoto(h, 'bob');
  const { photo: leavePhoto, ...leaveWithoutPhoto } = leaveWithPositionOps('bob');
  await assertFails(commit(h.db('bob'), Object.values(leaveWithoutPhoto)));
  await assertSucceeds(commit(h.db('bob'), [leavePhoto, ...Object.values(leaveWithoutPhoto)]));
  if (await docExists(h, `${M1}/photos/bob`)) throw new Error('la photo localisée devrait avoir disparu au départ');
});

s.tc('PL13b', 'effacement : retrait par la créatrice (7 écritures) et suppression de la Miff avec la photo localisée de la créatrice', async (h) => {
  await consentV3(h, 'alice', 'bob');
  await seedPositions(h, 'alice', 'bob');
  await seedLocPhoto(h, 'bob');
  const { photo, ...withoutPhoto } = removeMemberWithPositionOps({ target: 'bob' });
  await assertFails(commit(h.db('alice'), Object.values(withoutPhoto)));
  await assertSucceeds(commit(h.db('alice'), [photo, ...Object.values(withoutPhoto)]));
  if (await docExists(h, `${M1}/photos/bob`)) throw new Error('la photo localisée du membre retiré devrait avoir disparu');
  // Lot final de suppression de la Miff, avec la photo localisée de la créatrice.
  await seedLocPhoto(h, 'alice');
  const finalOps = (withPhoto) => [
    del(M1),
    set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
    del(`joinCodes/${CODE.REGEN}`), // le code a été renouvelé par le retrait de bob
    del(`${M1}/members/alice`),
    del(`${M1}/status/alice`),
    ...(withPhoto ? [del(`${M1}/photos/alice`)] : []),
    del(`${M1}/positions/alice`),
    del('users/alice/memberships/free'),
  ];
  await assertFails(commit(h.db('alice'), finalOps(false)));
  await assertSucceeds(commit(h.db('alice'), finalOps(true)));
});

s.tc('PL13c', 'les lots des applications actuelles (sans position, photo non localisée) passent toujours : départ puis retrait', async (h) => {
  await seedPlainPhoto(h, 'bob');
  await assertSucceeds(commit(h.db('bob'), Object.values(leaveOps('bob'))));
  // bob revient (état injecté), puis la créatrice le retire avec le lot des applications actuelles (6 écritures, sans position).
  await h.admin(async (f) => {
    await f.doc(`${M1}/members/bob`).set({ role: 'member', firstName: 'bob', lastName: 'Test', phone: '+33612345678', joinedAt: T0, joinCode: CODE.M1, sharing: { enabled: true, consentedAt: T0, consentVersion: 'v1', revokedAt: null } });
    await f.doc('users/bob/memberships/free').set({ miffId: ID.M1, joinedAt: T0 });
    await f.doc(`${M1}/status/bob`).set(statusDoc({ updatedAt: T0 }));
  });
  await seedPlainPhoto(h, 'bob');
  await assertSucceeds(commit(h.db('alice'), Object.values(removeMemberOps({ target: 'bob' }))));
});

s.tc('PL14', 'suppression d\'une photo localisée : l\'auteur, la créatrice ; un autre membre seulement une fois expirée ; jamais un étranger ni un visiteur', async (h) => {
  await seedLocPhoto(h, 'bob', { expiresAt: inMs(DAY) });
  await assertFails(photoRef(h, 'carol', 'bob').delete()); // membre, photo valide
  await assertFails(photoRef(h, 'eve', 'bob').delete());
  await assertFails(h.anon().doc(`${M1}/photos/bob`).delete());
  await assertSucceeds(photoRef(h, 'bob').delete());
  await seedLocPhoto(h, 'bob', { expiresAt: inMs(DAY) });
  await assertSucceeds(photoRef(h, 'alice', 'bob').delete()); // la créatrice
  await seedLocPhoto(h, 'bob', { expiresAt: inMs(-HOUR) });
  await assertFails(photoRef(h, 'eve', 'bob').delete()); // expirée, mais étranger
  await assertSucceeds(photoRef(h, 'carol', 'bob').delete()); // expirée : tout membre
});

// --- Session (SESS-1) -----------------------------------------------------------------------------------------
s.tc('PL15', 'SESS-1 : la photo ne porte pas de deviceId ; dans la mise à jour manuelle (photo localisée + statut avec deviceId), un appareil remplacé est refusé EN ENTIER ; photo seule : limite connue', async (h) => {
  await consentV3(h, 'bob');
  await h.admin((f) => f.doc(SESSION_PATH('bob')).set(sessionSeed(DEVICE_A)));
  const manual = (deviceId) => [set(`${M1}/photos/bob`, photoDoc(loc())), set(`${M1}/status/bob`, statusDoc({ hasPhoto: true, deviceId }))];
  await assertFails(commit(h.db('bob'), manual(DEVICE_B))); // appareil remplacé : le statut est refusé, donc la photo aussi
  if (await docExists(h, `${M1}/photos/bob`)) throw new Error('la photo ne devrait pas avoir été écrite');
  await assertSucceeds(commit(h.db('bob'), manual(DEVICE_A)));
  // LIMITE CONNUE (comme SE15) : une photo SEULE n'est pas contrôlée, même localisée ; la lecture reste soumise à la réciprocité.
  await h.admin((f) => f.doc(SESSION_PATH('bob')).set(sessionSeed(DEVICE_B)));
  await assertSucceeds(photoRef(h, 'bob').set(photoDoc(loc({ lat: 48.9, lng: 2.4 }))));
});

// --- Revue de schéma --------------------------------------------------------------------------------------------
s.tc('PL16', 'revue de schéma : la photo est le SEUL document, avec la position, à porter des coordonnées ; le statut, la fiche et le profil n\'en portent toujours pas', async (h) => {
  const rules = readRules();
  const keysOf = (fn) => {
    const body = rules.slice(rules.indexOf(`function ${fn}(`));
    const m = body.match(/hasOnly\(\[([^\]]*)\]\)/);
    if (!m) throw new Error(`hasOnly introuvable dans ${fn}`);
    return m[1].split(',').map((k) => k.trim().replace(/'/g, ''));
  };
  const photoKeys = keysOf('validPhoto');
  if (JSON.stringify(photoKeys) !== JSON.stringify(['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng', 'measuredAt'])) throw new Error(`clés de photo inattendues : ${photoKeys}`);
  const forbidden = ['latitude', 'longitude', 'coords', 'coordinates', 'geopoint', 'position', 'address', 'accuracy', 'deviceId'];
  const bad = photoKeys.filter((k) => forbidden.includes(k));
  if (bad.length > 0) throw new Error(`la photo autorise : ${bad.join(', ')}`);
  const statusKeys = keysOf('validStatus');
  if (statusKeys.some((k) => ['lat', 'lng', 'latitude', 'longitude', 'position'].includes(k))) throw new Error('le statut ne doit porter aucune coordonnée');
});

// --- Heure de la mesure (décision du PM du 2026-11-06) ------------------------------------------------------------
s.tc('PL18', 'measuredAt : OBLIGATOIRE avec lat / lng, INTERDITE sans ; horodatage ; de -24 h à +5 min ; mises à jour partielles jugées sur le résultat', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  // Obligatoire avec la paire : absente, nulle, ou d'un autre type : refusé.
  const { measuredAt: _m, ...noMeasure } = loc();
  await assertFails(ref.set(photoDoc(noMeasure)));
  await assertFails(ref.set(photoDoc(loc({ measuredAt: null }))));
  for (const bad of ['2026-11-06T10:00:00Z', 1_700_000_000_000, true, { s: 1 }]) await assertFails(ref.set(photoDoc(loc({ measuredAt: bad }))));
  await assertSucceeds(ref.set(photoDoc(loc())));
  // Interdite sans localisation : sans paire, ou avec une paire nulle : refusé ; nulle sans paire : comme avant (accepté).
  await assertFails(ref.set(photoDoc({ measuredAt: mAt() })));
  await assertFails(ref.set(photoDoc({ lat: null, lng: null, measuredAt: mAt() })));
  await assertSucceeds(ref.set(photoDoc({ measuredAt: null })));
  await assertSucceeds(ref.set(photoDoc({ lat: null, lng: null, measuredAt: null })));
  await assertSucceeds(ref.set(photoDoc()));
  // Bornes : comme positions/{uid}.measuredAt (24 h dans le passé, 5 min dans le futur, par rapport à l'heure de la photo = l'heure du serveur).
  for (const ms of [0, -MIN, -2 * HOUR, -23 * HOUR, 4 * MIN]) await assertSucceeds(ref.set(photoDoc(loc({ measuredAt: inMs(ms) }))));
  for (const ms of [-25 * HOUR, -3 * DAY, 10 * MIN, 30 * MIN, DAY]) await assertFails(ref.set(photoDoc(loc({ measuredAt: inMs(ms) }))));
  // Mises à jour PARTIELLES (le document résultant est jugé) : on retire l'heure d'une photo localisée => refusé ; on retire toute la localisation => accepté ;
  // on efface la paire en laissant l'heure => refusé ; on ajoute une heure à une photo sans localisation => refusé ; on renouvelle l'heure => accepté.
  await assertSucceeds(ref.set(photoDoc(loc())));
  await assertFails(ref.update({ createdAt: serverTs(), measuredAt: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), lat: null, lng: null }));
  await assertSucceeds(ref.update({ createdAt: serverTs(), measuredAt: mAt() }));
  await assertSucceeds(ref.update({ createdAt: serverTs(), lat: null, lng: null, measuredAt: null }));
  await assertFails(ref.update({ createdAt: serverTs(), measuredAt: mAt() })); // la photo n'a plus de localisation
  await assertSucceeds(ref.update({ createdAt: serverTs(), lat: 48.9, lng: 2.4, measuredAt: mAt() })); // et la regagne avec son heure
  // Un autre membre ne peut pas écrire l'heure d'une photo qui n'est pas la sienne.
  await assertFails(photoRef(h, 'alice', 'bob').update({ createdAt: serverTs(), measuredAt: mAt() }));
});

s.tc('PL19', 'suppression d\'UNE seule clé de la localisation (FieldValue.delete) : refusée ; supprimer les trois à la fois : acceptée (photo non localisée)', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(photoDoc(loc())));
  await assertFails(ref.update({ createdAt: serverTs(), lat: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), lng: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), lat: FieldValue.delete(), measuredAt: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), measuredAt: FieldValue.delete() }));
  await assertSucceeds(ref.update({ createdAt: serverTs(), lat: FieldValue.delete(), lng: FieldValue.delete(), measuredAt: FieldValue.delete() }));
  const snap = await ref.get();
  if (snap.data().lat !== undefined || snap.data().lng !== undefined || snap.data().measuredAt !== undefined) throw new Error('la photo ne devrait plus porter de localisation');
});

s.tc('PL20', 'un non-membre ne lit rien, même avec une position injectée et un reliquat : photo localisée ou non', async (h) => {
  await seedLocPhoto(h, 'bob');
  await seedPlainPhoto(h, 'alice');
  // eve (étrangère à la Miff) a un document de position injecté dans M1 (état impossible par les règles : reliquat ou import) : toujours refusée.
  await h.admin((f) => f.doc(`${M1}/positions/eve`).set(positionSeed()));
  await assertFails(photoRef(h, 'eve', 'bob').get());
  await assertFails(photoRef(h, 'eve', 'alice').get());
  // Un ex-membre dont la fiche a disparu mais dont la position est restée : même refus.
  await h.admin((f) => f.doc(`${M1}/positions/carol`).set(positionSeed()));
  await h.admin((f) => f.doc(`${M1}/members/carol`).delete());
  await assertFails(photoRef(h, 'carol', 'bob').get());
  await assertFails(h.anon().doc(`${M1}/photos/bob`).get());
});
