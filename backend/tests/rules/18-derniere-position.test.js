// Plan de test, section 17 : dernière position connue (R-LIEU-POS, décision du PM du 2026-11-01), cas DP1 à DP15.
// Règles PRÉPARÉES et NON déployées. La position est un document SÉPARÉ du statut : miffs/{id}/positions/{uid}
// = { lat, lng, measuredAt }, rien d'autre, aucun historique.
import {
  ID, CODE, T0, HOUR, DAY, suite, assertFails, assertSucceeds, commit, update, set, del, serverTs, Timestamp, inMs,
  statusDoc, photoDoc, positionDoc, positionSeed, sharingOnNow, sharingOnNowV3, sharingOnSeedV3, CONSENT_POSITION,
  leaveOps, removeMemberOps, adminSeedMiff,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const V3 = CONSENT_POSITION;
const MIN = 60_000;

// --- Préparation d'état (règles désactivées) -----------------------------------------------------------------
/** Passe le consentement de ces membres de M1 au texte « position » (version 3), sans toucher au reste de la fiche. */
const consentV3 = (h, ...uids) =>
  h.admin(async (f) => {
    for (const uid of uids) await f.doc(`${M1}/members/${uid}`).update({ 'sharing.consentVersion': V3 });
  });
/** Enregistre une dernière position pour ces membres de M1. */
const seedPositions = (h, ...uids) =>
  h.admin(async (f) => {
    for (const uid of uids) await f.doc(`${M1}/positions/${uid}`).set(positionSeed());
  });
/** Cas de référence : alice et bob ont accepté le texte v3 et ont chacun une position publiée (leur statut existe déjà). */
const bothPublished = async (h) => {
  await consentV3(h, 'alice', 'bob');
  await seedPositions(h, 'alice', 'bob');
};

/** Vrai si le document existe (lecture avec les règles désactivées). */
const docExists = async (h, path) => {
  let found;
  await h.admin(async (f) => {
    found = (await f.doc(path).get()).exists;
  });
  return found;
};
const countDocs = async (h, path) => {
  let n;
  await h.admin(async (f) => {
    n = (await f.collection(path).get()).size;
  });
  return n;
};

const readOk = (h, reader, target = 'alice') => assertSucceeds(h.db(reader).doc(`${M1}/positions/${target}`).get());
const readKo = (h, reader, target = 'alice') => assertFails(h.db(reader).doc(`${M1}/positions/${target}`).get());

// bob révoque son partage (fiche passée à « arrêté » ; la trace du consentement est conservée).
const bobRevoke = () => ({ enabled: false, consentedAt: T0, consentVersion: V3, revokedAt: serverTs() });

// --- Écriture ------------------------------------------------------------------------------------------------
s.tc('DP1', 'écrire puis mettre à jour sa dernière position (partage actif, consentement version 3) : accepté', async (h) => {
  await consentV3(h, 'bob');
  const ref = h.db('bob').doc(`${M1}/positions/bob`);
  await assertSucceeds(ref.set(positionDoc()));
  await assertSucceeds(ref.set(positionDoc({ lat: 48.9, lng: 2.4 })));
  await assertSucceeds(ref.update({ lat: 48.91, measuredAt: inMs(-MIN) }));
  // Aucun historique : un seul document, remplacé à chaque écriture.
  if ((await countDocs(h, `${M1}/positions`)) !== 1) throw new Error('un seul document de position attendu');
});

s.tc('DP2', 'pas de position sans le nouveau consentement : versions 1 et 2 refusées, versions 3 et plus acceptées', async (h) => {
  const ref = h.db('bob').doc(`${M1}/positions/bob`);
  const tryVersion = async (version) => {
    await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': version }));
    return ref.set(positionDoc());
  };
  // Le consentement actuel des comptes existants (texte sans position) est refusé.
  for (const v of ['v1', '2026-10-v1', '2026-10-v2', '2026-10-v02', '2026-11-v0', '2026-11-v00', '2026-09-v1']) {
    await assertFails(tryVersion(v));
  }
  // Format invalide : jamais accepté, même si le numéro paraît grand.
  for (const v of ['v3', '3', '2026-11-3', '2026-13-v3', '2026-00-v3', '2026-11-v3x', 'x2026-11-v3', '2026-11-V3', '26-11-v3', '2026-11-v-3', 'version 3']) {
    await assertFails(tryVersion(v));
  }
  for (const v of ['2026-11-v3', '2026-10-v3', '2026-10-v03', '2027-01-v4', '2026-12-v9', '2026-12-v10', '2027-02-v100']) {
    await assertSucceeds(tryVersion(v));
  }
});

s.tc('DP2b', 'consentement de la fiche non texte ou absent : aucune position', async (h) => {
  const ref = h.db('bob').doc(`${M1}/positions/bob`);
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': null }));
  await assertFails(ref.set(positionDoc()));
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': 3 }));
  await assertFails(ref.set(positionDoc()));
});

s.tc('DP3', 'partage arrêté ou jamais activé : aucune position, même avec le texte version 3', async (h) => {
  // carol : partage désactivé depuis le départ.
  await h.admin((f) => f.doc(`${M1}/members/carol`).update({ 'sharing.consentVersion': V3 }));
  await assertFails(h.db('carol').doc(`${M1}/positions/carol`).set(positionDoc()));
  // bob : partage arrêté (consentement v3 conservé comme trace, revokedAt renseigné).
  await h.admin((f) =>
    f.doc(`${M1}/members/bob`).update({ sharing: { enabled: false, consentedAt: T0, consentVersion: V3, revokedAt: T0 } }),
  );
  await assertFails(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc()));
});

s.tc('DP4', 'écrire la position d\'un autre membre (même la créatrice) ou sans être membre : refusé', async (h) => {
  await bothPublished(h);
  await assertFails(h.db('bob').doc(`${M1}/positions/alice`).set(positionDoc()));
  await assertFails(h.db('alice').doc(`${M1}/positions/bob`).set(positionDoc()));
  await assertFails(h.db('alice').doc(`${M1}/positions/bob`).update({ lat: 1 }));
  await assertFails(h.db('eve').doc(`${M1}/positions/eve`).set(positionDoc()));
  await assertFails(h.anon().doc(`${M1}/positions/bob`).set(positionDoc()));
  // Membre d'une autre Miff : pas de fiche ici, pas de position ici.
  await assertFails(h.db('zoe').doc(`${M1}/positions/zoe`).set(positionDoc()));
});

s.tc('DP5', 'forme : clés inconnues (précision, vitesse, historique...), clé manquante, lat/lng hors plage ou non numériques', async (h) => {
  await consentV3(h, 'bob');
  const ref = h.db('bob').doc(`${M1}/positions/bob`);
  for (const extra of [{ accuracyM: 12 }, { speed: 1 }, { altitude: 30 }, { updatedAt: serverTs() }, { history: [] }, { address: 'rue X' }, { placeId: 'p1' }]) {
    await assertFails(ref.set(positionDoc(extra)));
  }
  for (const key of ['lat', 'lng', 'measuredAt']) {
    const doc = positionDoc();
    delete doc[key];
    await assertFails(ref.set(doc));
  }
  await assertFails(ref.set(positionDoc({ lat: 90.5 })));
  await assertFails(ref.set(positionDoc({ lat: -90.5 })));
  await assertFails(ref.set(positionDoc({ lng: 180.5 })));
  await assertFails(ref.set(positionDoc({ lng: -180.5 })));
  for (const bad of [Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY, '48.85', null, true]) {
    await assertFails(ref.set(positionDoc({ lat: bad })));
    await assertFails(ref.set(positionDoc({ lng: bad })));
  }
  // Bornes incluses.
  await assertSucceeds(ref.set(positionDoc({ lat: 90, lng: 180 })));
  await assertSucceeds(ref.set(positionDoc({ lat: -90, lng: -180 })));
  await assertSucceeds(ref.set(positionDoc({ lat: 0, lng: 0 })));
  await assertSucceeds(ref.set(positionDoc({ lat: 48, lng: 2 })));
});

s.tc('DP5b', 'heure de mesure obligatoire et bornée : -24 h / +5 min (comme les horodatages du statut)', async (h) => {
  await consentV3(h, 'bob');
  const ref = h.db('bob').doc(`${M1}/positions/bob`);
  await assertFails(ref.set(positionDoc({ measuredAt: inMs(-25 * HOUR) })));
  await assertFails(ref.set(positionDoc({ measuredAt: inMs(-3 * DAY) })));
  await assertFails(ref.set(positionDoc({ measuredAt: inMs(10 * MIN) })));
  await assertFails(ref.set(positionDoc({ measuredAt: inMs(DAY) })));
  for (const bad of [null, '2026-11-01T10:00:00Z', 1_793_000_000_000, true, Timestamp.fromMillis(0)]) {
    await assertFails(ref.set(positionDoc({ measuredAt: bad })));
  }
  await assertSucceeds(ref.set(positionDoc({ measuredAt: inMs(-23 * HOUR) })));
  await assertSucceeds(ref.set(positionDoc({ measuredAt: inMs(4 * MIN) })));
  await assertSucceeds(ref.set(positionDoc({ measuredAt: serverTs() })));
});

// --- Lecture -------------------------------------------------------------------------------------------------
s.tc('DP6', 'lecture réciproque : un membre qui partage et a publié statut ET position lit celle d\'un autre', async (h) => {
  await bothPublished(h);
  await readOk(h, 'bob', 'alice');
  await readOk(h, 'alice', 'bob');
  const snap = await h.db('bob').doc(`${M1}/positions/alice`).get();
  if (snap.data().lat !== 48.85661) throw new Error('la position lue n\'est pas celle enregistrée');
});

s.tc('DP6b', 'lecture refusée sans réciprocité : sans sa propre position, sans son statut, partage arrêté, fiche retirée', async (h) => {
  await bothPublished(h);
  // Sans sa propre position publiée.
  await h.admin((f) => f.doc(`${M1}/positions/bob`).delete());
  await readKo(h, 'bob', 'alice');
  await h.admin((f) => f.doc(`${M1}/positions/bob`).set(positionSeed()));
  await readOk(h, 'bob', 'alice');
  // Sans son propre statut publié (ATK3).
  await h.admin((f) => f.doc(`${M1}/status/bob`).delete());
  await readKo(h, 'bob', 'alice');
  await h.admin((f) => f.doc(`${M1}/status/bob`).set(statusDoc({ updatedAt: T0 })));
  await readOk(h, 'bob', 'alice');
  // Partage arrêté (état injecté : une position qui aurait survécu à un arrêt du partage).
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.enabled': false }));
  await readKo(h, 'bob', 'alice');
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.enabled': true }));
  await readOk(h, 'bob', 'alice');
  // Fiche retirée (membre retiré, position restée en base).
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete());
  await readKo(h, 'bob', 'alice');
});

s.tc('DP6c', 'lecture refusée : membre qui ne partage pas, étranger, autre Miff, visiteur, Miff supprimée', async (h) => {
  await bothPublished(h);
  await readKo(h, 'carol', 'alice');
  await readKo(h, 'eve', 'alice');
  await readKo(h, 'zoe', 'alice');
  await assertFails(h.anon().doc(`${M1}/positions/alice`).get());
  // Miff supprimée : le survivant ne lit plus rien, même si la position existe encore physiquement.
  await h.admin((f) => f.doc(M1).delete());
  await readKo(h, 'bob', 'alice');
});

s.tc('DP6d', 'un lecteur dont le consentement date d\'avant la position (version 1 ou 2) ne lit rien, même avec statut publié', async (h) => {
  // alice a publié sa position ; bob a son statut et un partage actif, mais ni consentement v3 ni position.
  await consentV3(h, 'alice');
  await seedPositions(h, 'alice');
  await readKo(h, 'bob', 'alice');
  // Même s'il écrit une position, les règles exigent le texte v3 : impossible sans changer son consentement.
  await assertFails(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc()));
  await readKo(h, 'bob', 'alice');
  // Avec le nouveau consentement et sa position : il lit.
  await consentV3(h, 'bob');
  await assertSucceeds(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc()));
  await readOk(h, 'bob', 'alice');
});

s.tc('DP7', 'le créateur n\'a AUCUN droit de plus : mêmes conditions que les autres membres', async (h) => {
  await bothPublished(h);
  await readOk(h, 'alice', 'bob');
  // Créatrice sans sa propre position : elle ne lit pas celle des membres (réciprocité, pas d\'exception pour le créateur).
  await h.admin((f) => f.doc(`${M1}/positions/alice`).delete());
  await readKo(h, 'alice', 'bob');
  await h.admin((f) => f.doc(`${M1}/positions/alice`).set(positionSeed()));
  // Créatrice dont le partage est arrêté : plus de lecture.
  await h.admin((f) => f.doc(`${M1}/members/alice`).update({ 'sharing.enabled': false }));
  await readKo(h, 'alice', 'bob');
  // Elle ne lit pas non plus la position d\'un membre dans une Miff dont elle n\'est pas membre.
  await readKo(h, 'alice', 'zoe');
});

s.tc('DP8', 'jamais de liste : ni la collection, ni le groupe de collections, ni une requête filtrée', async (h) => {
  await bothPublished(h);
  await assertFails(h.db('bob').collection(`${M1}/positions`).get());
  await assertFails(h.db('alice').collection(`${M1}/positions`).get());
  await assertFails(h.db('bob').collection(`${M1}/positions`).where('lat', '>', 0).get());
  await assertFails(h.db('bob').collectionGroup('positions').get());
  await assertFails(h.db('eve').collectionGroup('positions').get());
  await assertFails(h.anon().collection(`${M1}/positions`).get());
});

s.tc('DP9', 'on relit toujours sa propre position (voir ce que je partage), pas celle des autres sans conditions', async (h) => {
  await bothPublished(h);
  await readOk(h, 'bob', 'bob');
  // Même partage arrêté (position restée en base) : sa propre donnée reste lisible par son auteur seul.
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.enabled': false }));
  await readOk(h, 'bob', 'bob');
  await readKo(h, 'bob', 'alice');
  await assertFails(h.anon().doc(`${M1}/positions/bob`).get());
});

s.tc('DP10', 'pas de limite de durée dans les règles : une position ancienne reste lisible (le masquage après 24 h est un affichage)', async (h) => {
  await bothPublished(h); // positionSeed : mesurée le 2026-01-01, donc très ancienne
  const snap = await h.db('bob').doc(`${M1}/positions/alice`).get();
  if (!snap.exists) throw new Error('la position ancienne devrait rester lisible (décision d\'affichage côté client)');
});

// --- Effacement : dans le MÊME lot que le statut et la photo ---------------------------------------------------
s.tc('DP11', 'arrêt du partage : refusé tant que la position n\'est pas supprimée dans le même lot', async (h) => {
  await bothPublished(h);
  const revoke = () => update(`${M1}/members/bob`, { sharing: bobRevoke() });
  // Statut et photo supprimés mais pas la position : refusé.
  await assertFails(commit(h.db('bob'), [revoke(), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]));
  // La position seule ne suffit pas non plus.
  await assertFails(commit(h.db('bob'), [revoke(), del(`${M1}/positions/bob`)]));
  // Les trois suppressions : accepté.
  await assertSucceeds(commit(h.db('bob'), [revoke(), del(`${M1}/status/bob`), del(`${M1}/photos/bob`), del(`${M1}/positions/bob`)]));
  const left = await docExists(h, `${M1}/positions/bob`);
  if (left) throw new Error('la position devrait avoir disparu');
  // Plus aucune lecture possible pour les autres (document introuvable, pas une erreur ni une ancienne position).
  const gone = await h.db('alice').doc(`${M1}/positions/bob`).get();
  if (gone.exists) throw new Error('un autre membre lit encore la position après l\'arrêt du partage');
});

s.tc('DP11b', 'arrêt du partage sans aucune position enregistrée : accepté comme avant (rien à supprimer)', async (h) => {
  await assertSucceeds(
    commit(h.db('bob'), [
      update(`${M1}/members/bob`, { sharing: { enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: serverTs() } }),
      del(`${M1}/status/bob`),
      del(`${M1}/photos/bob`),
    ]),
  );
});

s.tc('DP12', 'quitter la Miff : fiche, statut, photo ET position supprimés dans le même lot', async (h) => {
  await bothPublished(h);
  const { position, ...withoutPosition } = leaveOps('bob');
  await assertFails(commit(h.db('bob'), Object.values(withoutPosition)));
  await assertSucceeds(commit(h.db('bob'), [position, ...Object.values(withoutPosition)]));
  const left = await docExists(h, `${M1}/positions/bob`);
  if (left) throw new Error('la position devrait avoir disparu avec la fiche');
});

s.tc('DP12b', 'retrait d\'un membre par la créatrice : la position part dans le même lot (7 écritures, nouveau code)', async (h) => {
  await bothPublished(h);
  const { position, ...withoutPosition } = removeMemberOps({ target: 'bob' });
  await assertFails(commit(h.db('alice'), Object.values(withoutPosition)));
  await assertSucceeds(commit(h.db('alice'), [position, ...Object.values(withoutPosition)]));
  const left = await docExists(h, `${M1}/positions/bob`);
  if (left) throw new Error('la position du membre retiré devrait avoir disparu');
});

s.tc('DP12c', 'suppression de la Miff par la créatrice : sa propre position part dans le lot final', async (h) => {
  await bothPublished(h);
  const finalOps = (withPosition) => [
    del(M1),
    set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
    del(`joinCodes/${CODE.M1}`),
    del(`${M1}/members/alice`),
    del(`${M1}/status/alice`),
    ...(withPosition ? [del(`${M1}/positions/alice`)] : []),
    del('users/alice/memberships/free'),
  ];
  await assertFails(commit(h.db('alice'), finalOps(false)));
  await assertSucceeds(commit(h.db('alice'), finalOps(true)));
});

s.tc('DP13', 'suppression d\'une position : soi-même ou la créatrice (retrait) ; jamais un autre membre, un étranger, un visiteur', async (h) => {
  await bothPublished(h);
  await assertFails(h.db('carol').doc(`${M1}/positions/bob`).delete());
  await assertFails(h.db('bob').doc(`${M1}/positions/alice`).delete());
  await assertFails(h.db('eve').doc(`${M1}/positions/bob`).delete());
  await assertFails(h.db('zoe').doc(`${M1}/positions/bob`).delete());
  await assertFails(h.anon().doc(`${M1}/positions/bob`).delete());
  await assertSucceeds(h.db('alice').doc(`${M1}/positions/bob`).delete());
  await assertSucceeds(h.db('alice').doc(`${M1}/positions/alice`).delete());
  // Un ex-membre dont la fiche a disparu efface lui-même sa position restée en base (nettoyage au lancement).
  await seedPositions(h, 'bob');
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete());
  await assertSucceeds(h.db('bob').doc(`${M1}/positions/bob`).delete());
});

s.tc('DP13b', 'le droit de suppression de la créatrice ne lui donne aucun droit d\'écriture ni de lecture de plus', async (h) => {
  await consentV3(h, 'alice', 'bob');
  await seedPositions(h, 'bob');
  // Le droit de supprimer (retrait) n'ouvre ni l'écriture de la position d'un autre, ni sa lecture sans les conditions communes.
  await assertFails(h.db('alice').doc(`${M1}/positions/bob`).set(positionDoc()));
  await readKo(h, 'alice', 'bob');
});

// --- Nouveau consentement : réactivation ---------------------------------------------------------------------
s.tc('DP14', 'après un arrêt du partage, réactiver avec l\'ancien texte n\'autorise pas la position ; avec le texte v3, oui', async (h) => {
  await bothPublished(h);
  await assertSucceeds(
    commit(h.db('bob'), [
      update(`${M1}/members/bob`, { sharing: bobRevoke() }),
      del(`${M1}/status/bob`),
      del(`${M1}/photos/bob`),
      del(`${M1}/positions/bob`),
    ]),
  );
  // Réactivation avec l'ancien texte (v1) : le partage de présence repart, la position reste refusée.
  await assertSucceeds(
    commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: sharingOnNow() }), set(`${M1}/status/bob`, statusDoc())]),
  );
  await assertFails(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc()));
  // Arrêt puis réactivation avec le texte v3 : la position est acceptée. L'arrêt recopie la trace du consentement actuel.
  let current;
  await h.admin(async (f) => {
    current = (await f.doc(`${M1}/members/bob`).get()).data().sharing;
  });
  await assertSucceeds(
    commit(h.db('bob'), [
      update(`${M1}/members/bob`, { sharing: { enabled: false, consentedAt: current.consentedAt, consentVersion: current.consentVersion, revokedAt: serverTs() } }),
      del(`${M1}/status/bob`),
      del(`${M1}/photos/bob`),
    ]),
  );
  await assertSucceeds(
    commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: sharingOnNowV3() }), set(`${M1}/status/bob`, statusDoc())]),
  );
  await assertSucceeds(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc()));
});

s.tc('DP14b', 'activer le partage (texte v3) et écrire la position dans le même lot : accepté ; révoquer et écrire dans le même lot : refusé', async (h) => {
  // carol active son partage avec le texte v3, crée son statut et sa position en un lot.
  await assertSucceeds(
    commit(h.db('carol'), [
      update(`${M1}/members/carol`, { sharing: sharingOnNowV3() }),
      set(`${M1}/status/carol`, statusDoc()),
      set(`${M1}/positions/carol`, positionDoc()),
    ]),
  );
  // Même lot avec l'ancien texte : refusé en bloc, rien n'est écrit.
  await adminSeedMiff(h.env, {
    miffId: ID.M3, name: 'Autre', code: CODE.M3, ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: false, key: ID.M3 }],
  });
  const M3 = `miffs/${ID.M3}`;
  await assertFails(
    commit(h.db('dave'), [
      update(`${M3}/members/dave`, { sharing: sharingOnNow() }),
      set(`${M3}/status/dave`, statusDoc()),
      set(`${M3}/positions/dave`, positionDoc()),
    ]),
  );
  const written = await docExists(h, `${M3}/positions/dave`);
  if (written) throw new Error('rien ne devait être écrit');
  // bob révoque dans le même lot qu'il écrit sa position : refusé (la position existerait après le lot).
  await consentV3(h, 'bob');
  await assertFails(
    commit(h.db('bob'), [
      update(`${M1}/members/bob`, { sharing: bobRevoke() }),
      del(`${M1}/status/bob`),
      del(`${M1}/photos/bob`),
      set(`${M1}/positions/bob`, positionDoc()),
    ]),
  );
});

// --- Aucun autre chemin ---------------------------------------------------------------------------------------
s.tc('DP15', 'aucune coordonnée ailleurs : statut, fiche de membre, profil, chemins hors schéma restent fermés', async (h) => {
  await bothPublished(h);
  const bob = h.db('bob');
  // Le statut garde un schéma fermé sans coordonnées (S11) : la position n'y entre pas non plus sous le nom de la position.
  await assertFails(bob.doc(`${M1}/status/bob`).set(statusDoc({ measuredAt: inMs(-MIN), lat: 48.85, lng: 2.35 })));
  await assertFails(bob.doc(`${M1}/members/bob`).update({ lat: 48.85, lng: 2.35 }));
  await assertFails(bob.doc('users/bob').update({ lastPosition: { lat: 48.85, lng: 2.35 } }));
  await assertFails(bob.doc('positions/bob').set(positionDoc()));
  await assertFails(bob.doc('users/bob/positions/x').set(positionDoc()));
  await assertFails(bob.doc(`${M1}/places/pos`).set(positionDoc()));
  await assertFails(bob.doc(`${M1}/photos/bob`).set(photoDoc({ lat: 48.85 })));
  // Un document de position ne peut pas non plus être posé sous le nom d'un autre sous-chemin de la Miff.
  await assertFails(bob.doc(`${M1}/position/bob`).set(positionDoc()));
});

// --- Budget de lectures de règles : lots de plusieurs Miffs ----------------------------------------------------
const seedManyMiffs = async (h, count) => {
  const miffIds = [];
  await h.admin(async (f) => {
    for (let i = 0; i < count; i += 1) {
      const miffId = `PM${String(i).padStart(2, '0')}`.padEnd(20, 'x');
      miffIds.push(miffId);
      const code = 'K7M2QX9PDW4H';
      const b = f.batch();
      b.set(f.doc(`miffs/${miffId}`), { ownerUid: 'dave', name: `M${i}`, joinCode: code, createdAt: T0 });
      b.set(f.doc(`miffs/${miffId}/members/dave`), {
        role: 'owner', firstName: 'dave', lastName: 'Test', phone: '+33612345678', joinedAt: T0, sharing: sharingOnSeedV3(),
      });
      await b.commit();
    }
  });
  return miffIds;
};

s.tc('DP16', 'une position écrite pour 15 Miffs d\'un compte premium dans UN lot : acceptée (1 lecture de règles par Miff)', async (h) => {
  const miffIds = await seedManyMiffs(h, 15);
  await assertSucceeds(commit(h.db('dave'), miffIds.map((id) => set(`miffs/${id}/positions/dave`, positionDoc()))));
});

s.tc('DP16b', 'au-delà de la limite de 20 lectures de règles par lot (21 Miffs) : refusé en bloc (le client écrit 15 Miffs au plus par lot)', async (h) => {
  const miffIds = await seedManyMiffs(h, 21);
  await assertFails(commit(h.db('dave'), miffIds.map((id) => set(`miffs/${id}/positions/dave`, positionDoc()))));
  const written = await docExists(h, `miffs/${miffIds[0]}/positions/dave`);
  if (written) throw new Error('un lot refusé ne doit rien écrire');
});
