// Plan de test, section 22 : localisation de la photo d'update (R-PHOTO-LOC), cas PL1 à PL23.
// Version OBLIGATOIRE (décision du PM du 2026-11-06 : « une photo d'update doit être attachée à une position, forcément »), PRÉPARÉE et NON déployée.
// Le document photo porte TOUJOURS `lat`, `lng` et `measuredAt` (la position figée au moment de la photo et l'heure de sa mesure), jamais nulles ;
// elles sont effacées avec la photo ; une photo n'est lisible que par son auteur ou par un membre qui a publié sa propre position (un éventuel ANCIEN
// document sans localisation, écrit avant le resserrage, reste lisible comme avant). Une photo sans localisation est REFUSÉE à l'écriture.
import {
  ID, CODE, T0, HOUR, DAY, suite, assertFails, assertSucceeds, commit, update, set, del, serverTs, inMs, bytes, FieldValue,
  statusDoc, photoDoc, photoLegacyDoc, positionSeed, sharingOnNowV3, CONSENT_POSITION,
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
/** ANCIEN document sans localisation (état injecté : les règles actuelles refusent de l'écrire). */
const seedPlainPhoto = (h, uid, over = {}) => h.admin((f) => f.doc(`${M1}/photos/${uid}`).set(photoLegacyDoc({ createdAt: T0, ...over })));
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

// --- Une photo sans localisation est REFUSÉE (décision du PM du 2026-11-06) ------------------------------------
s.tc('PL1', "une photo SANS localisation est refusée sous toutes ses formes (aucune clé, clés nulles, mise à jour d'un ancien document) ; un ancien document reste lisible et supprimable", async (h) => {
  await consentV3(h, 'bob'); // le consentement est valide : le seul défaut est l'absence de localisation
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(photoDoc())); // contrôle : la même photo AVEC sa localisation passe
  await assertFails(ref.set(photoLegacyDoc())); // le document d'avant le resserrage (jpeg, createdAt, expiresAt)
  await assertFails(ref.set(photoLegacyDoc({ jpeg: bytes(150000) })));
  await assertFails(ref.set(photoLegacyDoc({ lat: null, lng: null }))); // « pas de localisation » écrit explicitement
  await assertFails(ref.set(photoLegacyDoc({ lat: null, lng: null, measuredAt: null })));
  await assertFails(ref.set(photoDoc({ lat: null, lng: null, measuredAt: null })));
  // Même avec le consentement le plus ancien (v1) : refusée (deux défauts : pas de localisation, pas de texte v3).
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': 'v1' }));
  await assertFails(ref.set(photoLegacyDoc()));
  await consentV3(h, 'bob');
  // Un ancien document sans localisation (état injecté) : on ne peut pas le « rafraîchir » sans lui donner sa localisation...
  await seedPlainPhoto(h, 'bob');
  await assertFails(ref.update({ createdAt: serverTs(), expiresAt: inMs(DAY) })); // le document fusionné reste sans localisation
  await assertSucceeds(ref.update({ createdAt: serverTs(), lat: 48.85661, lng: 2.35222, measuredAt: mAt() })); // ...mais on peut la lui ajouter
  // Un ancien document est toujours LISIBLE (lecture inchangée, y compris par un membre sans position publiée) et SUPPRIMABLE.
  await seedPlainPhoto(h, 'alice');
  await assertSucceeds(photoRef(h, 'bob', 'alice').get());
  await seedPlainPhoto(h, 'bob', { lat: null, lng: null });
  await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  await assertSucceeds(photoRef(h, 'bob').delete());
});

s.tc('PL2', 'schéma fermé : aucune autre clé que jpeg, createdAt, expiresAt, lat, lng, measuredAt (ni deviceId, ni précision, ni adresse, ni autre heure) ; chacune des six est obligatoire', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(photoDoc(loc())));
  for (const extra of [{ deviceId: DEVICE_A }, { accuracyM: 5 }, { address: 'Paris' }, { locationAt: serverTs() }, { updatedAt: serverTs() }, { speed: 1 }, { position: { lat: 1, lng: 1 } }]) {
    await assertFails(ref.set(photoDoc({ ...loc(), ...extra })));
    await assertFails(ref.set(photoDoc(extra)));
  }
  // Chacune des six clés manquante, une par une : refusé (jpeg, createdAt, expiresAt et les trois clés de localisation).
  for (const key of Object.keys(photoDoc())) {
    const doc = photoDoc();
    delete doc[key];
    await assertFails(ref.set(doc));
  }
});

// --- Écriture : consentement et partage ------------------------------------------------------------------------
s.tc('PL3', 'consentement : toute photo est refusée avec le texte v1, v2 ou absent, acceptée avec v03 ou plus (même condition que la position)', async (h) => {
  const ref = photoRef(h, 'bob');
  for (const v of ['v1', '2026-10-v1', '2026-10-v2', '2026-10-v02', '2026-11-v3', '2026-11-v00', '2026-11-v01', '2026-11-v003', '2026-13-v03', null]) {
    await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': v }));
    await assertFails(ref.set(photoDoc(loc())));
  }
  for (const v of ['2026-11-v03', '2026-12-v04', '2027-01-v05', '2026-11-v99']) {
    await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': v }));
    await assertSucceeds(ref.set(photoDoc(loc())));
  }
});

s.tc('PL4', 'partage : arrêté refusé ; activer le partage (texte v3) et poser la photo dans le même lot accepté ; avec le texte v1 ou en révoquant refusé', async (h) => {
  const carol = h.db('carol'); // partage arrêté dans le jeu de départ
  await consentV3(h, 'carol'); // texte de version 03 déjà accepté : le seul défaut du premier essai est le partage arrêté
  await assertFails(carol.doc(`${M1}/photos/carol`).set(photoDoc(loc())));
  // Avec le consentement v1 dans le même lot : la photo est refusée (le texte ne couvre pas la position).
  await assertFails(commit(carol, [update(`${M1}/members/carol`, { sharing: { enabled: true, consentedAt: serverTs(), consentVersion: 'v1', revokedAt: null } }), set(`${M1}/photos/carol`, photoDoc(loc()))]));
  await assertSucceeds(commit(carol, [update(`${M1}/members/carol`, { sharing: sharingOnNowV3() }), set(`${M1}/photos/carol`, photoDoc(loc()))]));
  // Révoquer et poser la photo dans le même lot : refusé (getAfter).
  await consentV3(h, 'bob');
  await assertFails(commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: bobRevoke() }), set(`${M1}/photos/bob`, photoDoc(loc()))]));
});

s.tc('PL5', "seul l'intéressé écrit sa photo (pas un autre membre, pas la créatrice, pas un étranger, pas un visiteur)", async (h) => {
  await consentV3(h, 'bob', 'alice');
  for (const who of ['alice', 'carol', 'zoe', 'eve']) await assertFails(photoRef(h, who, 'bob').set(photoDoc(loc())));
  await assertFails(h.anon().doc(`${M1}/photos/bob`).set(photoDoc(loc())));
  await assertSucceeds(photoRef(h, 'bob').set(photoDoc(loc())));
  // L'auteur ne peut pas non plus modifier la localisation d'une photo d'un autre par une mise à jour.
  await assertFails(photoRef(h, 'alice', 'bob').update({ lat: 1, lng: 1, createdAt: serverTs() }));
});

s.tc('PL6', 'coordonnées : lat et lng nombres valides et JAMAIS nulles ; bornes incluses ; types, valeurs hors plage, NaN et infini refusés', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  // Une seule clé, ou une valeur nulle : refusé (avec une heure de mesure valide, pour que le seul défaut soit la coordonnée).
  await assertFails(ref.set(photoLegacyDoc({ lat: 48.85661, measuredAt: mAt() })));
  await assertFails(ref.set(photoLegacyDoc({ lng: 2.35222, measuredAt: mAt() })));
  await assertFails(ref.set(photoDoc({ lat: 48.85661, lng: null })));
  await assertFails(ref.set(photoDoc({ lat: null, lng: 2.35222 })));
  await assertFails(ref.set(photoDoc({ lat: null, lng: null })));
  // Paire à moitié nulle ou seule, SANS heure de mesure : refusé aussi.
  await assertFails(ref.set(photoLegacyDoc({ lat: null, lng: 2.35222 })));
  await assertFails(ref.set(photoLegacyDoc({ lat: 48.85661, lng: null })));
  await assertFails(ref.set(photoLegacyDoc({ lat: null })));
  await assertFails(ref.set(photoLegacyDoc({ lng: null })));
  await assertFails(ref.set(photoLegacyDoc({ lat: 48.85661, lng: 2.35222 }))); // paire valide mais sans heure de mesure
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
  // Mise à jour partielle qui ajoute la localisation à un ANCIEN document sans localisation : jugée sur le résultat fusionné.
  await seedPlainPhoto(h, 'bob');
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

s.tc('PL8', "l'auteur relit sa propre photo localisée même sans position publiée (transparence) ; un ancien document sans localisation d'un autre reste lisible sans position", async (h) => {
  await seedLocPhoto(h, 'bob');
  await assertSucceeds(photoRef(h, 'bob').get()); // bob : partage actif, aucune position
  await seedPlainPhoto(h, 'alice');
  await assertSucceeds(photoRef(h, 'bob', 'alice').get()); // contrôle : ancien document sans localisation, sans position publiée
  // Un document À MOITIÉ localisé (état impossible par les règles, injecté) compte comme localisé : lecteur sans position publiée refusé, auteur accepté.
  await seedPlainPhoto(h, 'carol', { lat: 48.85661, lng: null });
  await assertFails(photoRef(h, 'bob', 'carol').get());
  await seedPlainPhoto(h, 'carol', { lat: null, lng: 2.35222 });
  await assertFails(photoRef(h, 'bob', 'carol').get());
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

s.tc('PL11', "reliquat (décision du PM du 2026-11-06, pas de contrôle « l'auteur partage encore ») : une photo localisée restée après l'arrêt du partage de son auteur reste lisible jusqu'à son expiration seulement ; un ancien document sans localisation garde le comportement d'avant", async (h) => {
  await consentV3(h, 'alice');
  await seedPositions(h, 'alice');
  await seedLocPhoto(h, 'bob', { expiresAt: inMs(DAY) });
  await seedPlainPhoto(h, 'carol'); // carol ne partage pas : son ancien document sans localisation ne devrait pas exister, comportement d'avant conservé
  await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  // État impossible par les règles (les lots de purge exigent la suppression de la photo avec l'arrêt du partage) : reliquat injecté.
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ sharing: { enabled: false, consentedAt: T0, consentVersion: V3, revokedAt: T0 } }));
  await assertSucceeds(photoRef(h, 'alice', 'bob').get()); // exposition bornée par expiresAt, pas par un contrôle de l'auteur
  await assertSucceeds(photoRef(h, 'alice', 'carol').get()); // inchangé (ancien document sans localisation)
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
  // QUE pour une photo absente : pour une photo PRÉSENTE, le refus (sans position publiée) face à la lecture réussie d'un ancien document non localisé apprend
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

s.tc('PL13c', 'les lots des applications actuelles (sans position) passent toujours, avec un ancien document de photo sans localisation : départ puis retrait', async (h) => {
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
s.tc('PL16', 'revue de schéma : la photo est le SEUL document, avec la position, à porter des coordonnées (et les six clés sont toutes obligatoires) ; le statut, la fiche et le profil n\'en portent toujours pas', async (h) => {
  const rules = readRules();
  const listOf = (fn, method) => {
    const body = rules.slice(rules.indexOf(`function ${fn}(`));
    const m = body.match(new RegExp(`${method}\\(\\[([^\\]]*)\\]\\)`));
    if (!m) throw new Error(`${method} introuvable dans ${fn}`);
    return m[1].split(',').map((k) => k.trim().replace(/'/g, ''));
  };
  const keysOf = (fn) => listOf(fn, 'hasOnly');
  const photoKeys = keysOf('validPhoto');
  const SIX = ['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng', 'measuredAt'];
  if (JSON.stringify(photoKeys) !== JSON.stringify(SIX)) throw new Error(`clés de photo inattendues : ${photoKeys}`);
  // Localisation OBLIGATOIRE : hasAll porte les six mêmes clés (aucune clé facultative).
  const required = listOf('validPhoto', 'hasAll');
  if (JSON.stringify(required) !== JSON.stringify(SIX)) throw new Error(`clés obligatoires de photo inattendues : ${required}`);
  const forbidden = ['latitude', 'longitude', 'coords', 'coordinates', 'geopoint', 'position', 'address', 'accuracy', 'deviceId'];
  const bad = photoKeys.filter((k) => forbidden.includes(k));
  if (bad.length > 0) throw new Error(`la photo autorise : ${bad.join(', ')}`);
  const statusKeys = keysOf('validStatus');
  if (statusKeys.some((k) => ['lat', 'lng', 'latitude', 'longitude', 'position'].includes(k))) throw new Error('le statut ne doit porter aucune coordonnée');
});

// --- Heure de la mesure (décision du PM du 2026-11-06) ------------------------------------------------------------
s.tc('PL18', 'measuredAt : OBLIGATOIRE, jamais nulle, horodatage ; de -24 h à +5 min ; mises à jour partielles jugées sur le document fusionné', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  // Obligatoire : absente, nulle, ou d'un autre type : refusé.
  const { measuredAt: _m, ...noMeasure } = photoDoc(); // photoDoc() complète toujours les trois clés : on retire l'heure APRÈS sa construction
  await assertFails(ref.set(noMeasure));
  await assertFails(ref.set(photoDoc(loc({ measuredAt: null }))));
  for (const bad of ['2026-11-06T10:00:00Z', 1_700_000_000_000, true, { s: 1 }]) await assertFails(ref.set(photoDoc(loc({ measuredAt: bad }))));
  await assertSucceeds(ref.set(photoDoc(loc())));
  // Sans localisation (paire absente ou nulle) : refusée même avec une heure valide.
  await assertFails(ref.set(photoLegacyDoc({ measuredAt: mAt() })));
  await assertFails(ref.set(photoLegacyDoc({ lat: null, lng: null, measuredAt: mAt() })));
  await assertFails(ref.set(photoLegacyDoc({ measuredAt: null })));
  // Bornes : comme positions/{uid}.measuredAt (24 h dans le passé, 5 min dans le futur, par rapport à l'heure de la photo = l'heure du serveur).
  for (const ms of [0, -MIN, -2 * HOUR, -23 * HOUR, 4 * MIN]) await assertSucceeds(ref.set(photoDoc(loc({ measuredAt: inMs(ms) }))));
  for (const ms of [-25 * HOUR, -3 * DAY, 10 * MIN, 30 * MIN, DAY]) await assertFails(ref.set(photoDoc(loc({ measuredAt: inMs(ms) }))));
  // Mises à jour PARTIELLES (le document fusionné est jugé) : retirer l'heure => refusé ; la renouveler => accepté ; mettre la paire à null => refusé ;
  // retirer TOUTE la localisation => refusé (la photo ne peut plus être non localisée) ; remplacer la localisation par une autre complète => accepté.
  await assertSucceeds(ref.set(photoDoc(loc())));
  await assertFails(ref.update({ createdAt: serverTs(), measuredAt: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), lat: null, lng: null }));
  await assertFails(ref.update({ createdAt: serverTs(), lat: null, lng: null, measuredAt: null }));
  await assertSucceeds(ref.update({ createdAt: serverTs(), measuredAt: mAt() }));
  await assertSucceeds(ref.update({ createdAt: serverTs(), lat: 48.9, lng: 2.4, measuredAt: mAt() }));
  // Une mise à jour qui ne touche pas la localisation garde l'ancienne mesure : refusée si elle date de plus de 24 h (le document fusionné doit rester valide).
  await seedLocPhoto(h, 'bob', { measuredAt: inMs(-25 * HOUR) });
  await assertFails(ref.update({ createdAt: serverTs(), expiresAt: inMs(DAY) }));
  await seedLocPhoto(h, 'bob', { measuredAt: inMs(-2 * HOUR) });
  await assertSucceeds(ref.update({ createdAt: serverTs(), expiresAt: inMs(DAY) })); // mesure encore dans la fenêtre : acceptée
  // Un autre membre ne peut pas écrire l'heure d'une photo qui n'est pas la sienne.
  await assertFails(photoRef(h, 'alice', 'bob').update({ createdAt: serverTs(), measuredAt: mAt() }));
});

s.tc('PL19', 'suppression d\'UNE ou de DEUX clés de la localisation (FieldValue.delete) : refusée ; supprimer les trois à la fois : refusée aussi (une photo ne peut plus être non localisée)', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(photoDoc(loc())));
  await assertFails(ref.update({ createdAt: serverTs(), lat: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), lng: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), lat: FieldValue.delete(), measuredAt: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), lat: FieldValue.delete(), lng: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), measuredAt: FieldValue.delete() }));
  await assertFails(ref.update({ createdAt: serverTs(), lat: FieldValue.delete(), lng: FieldValue.delete(), measuredAt: FieldValue.delete() }));
  // Rien n'a été écrit par les essais refusés : la photo porte toujours sa localisation.
  const snap = await ref.get();
  if (snap.data().lat !== 48.85661 || snap.data().lng !== 2.35222 || !snap.data().measuredAt) throw new Error('la photo devrait avoir gardé sa localisation');
});

s.tc('PL20', 'un non-membre ne lit rien, même avec une position injectée et un reliquat : photo localisée ou ancien document', async (h) => {
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

// --- Localisation obligatoire (décision du PM du 2026-11-06) ----------------------------------------------------------
s.tc('PL21', "une seule clé ou deux clés de localisation sur trois : refusé dans toutes les combinaisons ; les trois clés valides : accepté", async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  const full = loc();
  const keys = ['lat', 'lng', 'measuredAt'];
  // Toutes les combinaisons de 0, 1 ou 2 clés présentes (les autres absentes, puis les autres nulles).
  for (let mask = 0; mask < 7; mask += 1) {
    const present = keys.filter((_, i) => (mask >> i) & 1);
    const absent = Object.fromEntries(present.map((k) => [k, full[k]]));
    await assertFails(ref.set(photoLegacyDoc(absent)));
    const nulled = Object.fromEntries(keys.map((k) => [k, present.includes(k) ? full[k] : null]));
    await assertFails(ref.set(photoLegacyDoc(nulled)));
  }
  await assertSucceeds(ref.set(photoLegacyDoc(full))); // les trois clés valides : accepté
  await assertSucceeds(ref.set(photoDoc()));
});

s.tc('PL22', 'trois clés valides + partage + consentement dans le même lot : accepté ; le même lot avec le texte v1, sans la localisation ou en révoquant : refusé', async (h) => {
  const carol = h.db('carol'); // partage arrêté dans le jeu de départ
  const activateV3 = () => update(`${M1}/members/carol`, { sharing: sharingOnNowV3() });
  const activateV1 = () => update(`${M1}/members/carol`, { sharing: { enabled: true, consentedAt: serverTs(), consentVersion: 'v1', revokedAt: null } });
  await assertFails(commit(carol, [activateV3(), set(`${M1}/photos/carol`, photoLegacyDoc())])); // sans localisation
  await assertFails(commit(carol, [activateV3(), set(`${M1}/photos/carol`, photoLegacyDoc({ lat: 48.85661, lng: 2.35222 }))])); // sans heure
  await assertFails(commit(carol, [activateV1(), set(`${M1}/photos/carol`, photoDoc())])); // consentement trop ancien
  await assertSucceeds(commit(carol, [activateV3(), set(`${M1}/photos/carol`, photoDoc())])); // les trois clés + partage + consentement
  if (!(await docExists(h, `${M1}/photos/carol`))) throw new Error('la photo localisée devrait avoir été écrite avec le partage');
  // La mise à jour manuelle (photo + statut) du même appareil, un autre jour : toujours les trois clés.
  await assertSucceeds(commit(carol, [set(`${M1}/photos/carol`, photoDoc()), set(`${M1}/status/carol`, statusDoc({ hasPhoto: true }))]));
});

s.tc('PL23', "lecture par réciprocité de la photo toujours localisée : le lecteur sans position publiée ne la lit pas, avec sa position il la lit ; l'auteur la relit ; une photo écrite par les règles est bien celle-là", async (h) => {
  await consentV3(h, 'alice', 'bob');
  await assertSucceeds(photoRef(h, 'bob').set(photoDoc(loc()))); // écrite par les règles (pas injectée)
  await assertFails(photoRef(h, 'alice', 'bob').get()); // alice n'a publié aucune position
  await assertSucceeds(photoRef(h, 'bob').get()); // l'auteur
  await seedPositions(h, 'alice');
  const snap = await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  if (snap.data().lat === undefined || snap.data().lng === undefined || !snap.data().measuredAt) throw new Error('la photo écrite devrait porter ses trois clés de localisation');
  await assertFails(photoRef(h, 'carol', 'bob').get()); // partage arrêté
  await assertFails(photoRef(h, 'eve', 'bob').get()); // étranger
});
