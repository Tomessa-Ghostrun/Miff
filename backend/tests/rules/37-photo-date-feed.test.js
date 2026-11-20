// Plan de test, section 31 : FEED-ORDRE-PHOTO, date de la dernière photo publiée (`photoAt`) dans le statut (ticket docs/tickets/feed-ordre-photo.md,
// décision du PM du 2026-11-20 : le Feed classe les cartes UNIQUEMENT selon la date de la dernière photo). NON DÉPLOYÉ.
// Clé FACULTATIVE `photoAt` de miffs/{id}/status/{uid} : absente ou nulle ; CONSERVÉE (identique à la base) ; ou NOUVELLE = exactement l'heure du serveur
// avec `hasPhoto` vrai. Rien d'autre ne change : clés obligatoires, schéma fermé, bornes de lastUpdateAt / presenceAt (R-LIEU-1), budgets de lectures de règles.
import {
  ID, T0, DAY, HOUR, suite, assertFails, assertSucceeds, commit, set, update, del, Timestamp, serverTs, inMs,
  statusDoc, statusInitial, photoDoc, setConsentV3, DEVICE_A, sessionSeed, SESSION_PATH, sharingOnNow, readRules,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const MIN = 60_000;

// Date de photo déjà en base, vieille de plus de 24 h (la photo est expirée, la date est gardée).
const OLD_PHOTO = Timestamp.fromMillis(T0.toMillis() + 2 * HOUR);

const statusRef = (h, as, of = as) => h.db(as).doc(`${M1}/status/${of}`);
/** Statut déjà en base (règles désactivées), avec ou sans photoAt. */
const seed = (h, uid = 'bob', over = {}) => h.admin((f) => f.doc(`${M1}/status/${uid}`).set(statusDoc({ updatedAt: T0, ...over })));
const seedWithDate = (h, uid = 'bob', over = {}) => seed(h, uid, { hasPhoto: true, photoAt: OLD_PHOTO, ...over });
const stored = async (h, uid = 'bob') => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(`${M1}/status/${uid}`).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};
const photoAtOf = async (h, uid = 'bob') => {
  const d = await stored(h, uid);
  return d && d.photoAt ? d.photoAt.toMillis() : null;
};
/** Mise à jour PARTIELLE telle que les clients actuels l'écrivent (le document fusionné est jugé). */
const partial = (h, fields, uid = 'bob') => statusRef(h, uid).update({ updatedAt: serverTs(), ...fields });
/** Écriture COMPLÈTE (`set`, sans fusion) : un document entier, comme à l'entrée dans une Miff. */
const full = (h, over, uid = 'bob') => statusRef(h, uid).set(statusDoc(over));

s.tc('PA1', 'publication d\'une photo : lot [photo, statut {hasPhoto, lastUpdateAt, photoAt = heure du serveur}] accepté ; les trois heures sont la MÊME heure du serveur', async (h) => {
  await setConsentV3(h, 'bob');
  await seed(h);
  await h.admin((f) => f.doc(SESSION_PATH('bob')).set(sessionSeed(DEVICE_A)));
  await assertSucceeds(
    commit(h.db('bob'), [
      set(`${M1}/photos/bob`, photoDoc()),
      update(`${M1}/status/bob`, { hasPhoto: true, updateText: null, lastUpdateAt: serverTs(), photoAt: serverTs(), updatedAt: serverTs(), deviceId: DEVICE_A }),
    ]),
  );
  const status = await stored(h);
  let photo;
  await h.admin(async (f) => {
    photo = (await f.doc(`${M1}/photos/bob`).get()).data();
  });
  if (!status.photoAt || status.photoAt.toMillis() !== photo.createdAt.toMillis() || status.photoAt.toMillis() !== status.lastUpdateAt.toMillis()) {
    throw new Error('photoAt, createdAt de la photo et lastUpdateAt devraient être la même heure du serveur');
  }
  // Une deuxième photo REMPLACE la date (nouvelle heure du serveur, jamais antérieure).
  const first = status.photoAt.toMillis();
  await new Promise((r) => setTimeout(r, 30));
  await assertSucceeds(
    commit(h.db('bob'), [
      set(`${M1}/photos/bob`, photoDoc()),
      update(`${M1}/status/bob`, { hasPhoto: true, lastUpdateAt: serverTs(), photoAt: serverTs(), updatedAt: serverTs(), deviceId: DEVICE_A }),
    ]),
  );
  if ((await photoAtOf(h)) <= first) throw new Error('la date de la deuxième photo devrait être plus récente');
});

s.tc('PA2', 'conservation : une mise à jour partielle qui ne touche pas photoAt la garde, après une update SANS photo, une arrivée en lieu, une batterie, une photo expirée (hasPhoto = false), un voyage', async (h) => {
  await seedWithDate(h);
  const keep = () => photoAtOf(h).then((v) => {
    if (v !== OLD_PHOTO.toMillis()) throw new Error('photoAt aurait dû rester celle de la base');
  });
  await assertSucceeds(partial(h, { lastUpdateAt: serverTs(), updateText: null })); // update manuelle sans photo
  await keep();
  await assertSucceeds(partial(h, { presence: 'in_trusted', placeId: 'p1', presenceAt: inMs(-MIN), lastUpdateAt: serverTs() })); // arrivée en lieu de confiance
  await keep();
  await assertSucceeds(partial(h, { batteryPct: 42 })); // batterie
  await keep();
  await assertSucceeds(partial(h, { hasPhoto: false })); // photo expirée : seule la vignette disparaît, la date reste
  await keep();
  await assertSucceeds(partial(h, { presence: 'out', placeId: 'p1', presenceAt: inMs(-MIN) })); // départ
  await keep();
  await assertSucceeds(partial(h, { locationAccess: 'denied' }));
  await keep();
});

s.tc('PA3', 'refus : une NOUVELLE date qui n\'est pas l\'heure du serveur (heure d\'appareil récente, future, passée, 1970, un autre champ, 1 ms d\'écart avec la base)', async (h) => {
  await seedWithDate(h);
  for (const bad of [
    inMs(-1000), inMs(-HOUR), inMs(4 * MIN), inMs(10 * MIN), inMs(DAY), inMs(-3 * DAY), Timestamp.fromMillis(0), T0,
    Timestamp.fromMillis(OLD_PHOTO.toMillis() + 1), Timestamp.fromMillis(OLD_PHOTO.toMillis() - 1), new Timestamp(OLD_PHOTO.seconds, OLD_PHOTO.nanoseconds + 1000),
  ]) {
    await assertFails(partial(h, { hasPhoto: true, photoAt: bad }));
    await assertFails(full(h, { hasPhoto: true, photoAt: bad }));
  }
  // Même sur un statut SANS date : l'heure du serveur seule est acceptée.
  await seed(h);
  for (const bad of [inMs(-1000), inMs(MIN), inMs(-3 * DAY), OLD_PHOTO]) {
    await assertFails(partial(h, { hasPhoto: true, photoAt: bad }));
  }
  await assertSucceeds(partial(h, { hasPhoto: true, photoAt: serverTs() })); // contrôle
});

s.tc('PA3b', 'refus : la date n\'est jamais la valeur d\'un AUTRE champ ni d\'un autre membre (lastUpdateAt, presenceAt, date de photo d\'alice)', async (h) => {
  await seedWithDate(h, 'alice', { photoAt: Timestamp.fromMillis(T0.toMillis() + 5 * HOUR) });
  await seed(h, 'bob', { lastUpdateAt: inMs(-2 * HOUR), presenceAt: inMs(-3 * HOUR) });
  const b = await stored(h);
  await assertFails(partial(h, { hasPhoto: true, photoAt: b.lastUpdateAt }));
  await assertFails(partial(h, { hasPhoto: true, photoAt: b.presenceAt }));
  await assertFails(partial(h, { hasPhoto: true, photoAt: Timestamp.fromMillis(T0.toMillis() + 5 * HOUR) })); // la date d'alice
});

s.tc('PA4', 'refus : une NOUVELLE date sans photo annoncée (hasPhoto faux : écrit, ou fusionné depuis la base) ; acceptée avec hasPhoto vrai, y compris déjà vrai en base', async (h) => {
  await seed(h); // hasPhoto = false en base
  await assertFails(partial(h, { photoAt: serverTs() })); // fusion : hasPhoto reste faux
  await assertFails(partial(h, { hasPhoto: false, photoAt: serverTs() }));
  await assertFails(full(h, { hasPhoto: false, photoAt: serverTs() }));
  await seedWithDate(h); // hasPhoto = true en base, date ancienne
  await assertFails(partial(h, { hasPhoto: false, photoAt: serverTs() }));
  await assertSucceeds(partial(h, { photoAt: serverTs() })); // fusion : hasPhoto vrai (cas d'un statut qui annonce déjà une photo)
  if ((await photoAtOf(h)) === OLD_PHOTO.toMillis()) throw new Error('la date devrait avoir été remplacée par celle du serveur');
});

s.tc('PA5', 'type : texte, nombre, booléen, liste, objet, durée refusés (même identiques à la base) ; null accepté (équivaut à « pas de date »)', async (h) => {
  await seed(h);
  for (const bad of ['2026-11-20T10:00:00Z', 'x', '', 0, 1790000000000, true, false, [], [1, 2], {}, { seconds: 1 }]) {
    await assertFails(partial(h, { hasPhoto: true, photoAt: bad }));
    await assertFails(full(h, { hasPhoto: true, photoAt: bad }));
  }
  await assertSucceeds(partial(h, { photoAt: null })); // contrôle : « pas de date »
  // Base corrompue par un état injecté (règles désactivées) : un texte en base, renvoyé tel quel, reste refusé.
  await seed(h, 'bob', { photoAt: 'hier' });
  await assertFails(partial(h, { batteryPct: 40 }));
});

s.tc('PA6', 'recopie par un `set` complet : la date de la base est acceptée à l\'identique (à la microseconde), quel que soit son âge ; une autre valeur est refusée', async (h) => {
  await seedWithDate(h); // photoAt vieille de plus de 24 h
  const before = await stored(h);
  const cur = before.photoAt;
  await assertSucceeds(full(h, { hasPhoto: true, photoAt: cur, presenceAt: before.presenceAt, lastUpdateAt: before.lastUpdateAt }));
  await assertFails(full(h, { hasPhoto: true, photoAt: Timestamp.fromMillis(cur.toMillis() + 1000) }));
  await assertFails(full(h, { hasPhoto: true, photoAt: Timestamp.fromMillis(cur.toMillis() - 1000) }));
  // Sans statut en base (création, ou recréation) : la valeur « conservée » n'existe pas, seule l'heure du serveur avec hasPhoto passe (PA10).
});

s.tc('PA7', 'ancien client : aucune écriture existante n\'est refusée (statut sans la clé, mise à jour partielle sans la clé, photo publiée sans la date) et la date en base n\'est NI effacée NI modifiée par une mise à jour partielle', async (h) => {
  // 1. base sans date : tout comme avant.
  await seed(h);
  await assertSucceeds(full(h, { hasPhoto: true, lastUpdateAt: inMs(-MIN) }));
  await assertSucceeds(partial(h, { batteryPct: 10 }));
  if ((await stored(h)).photoAt !== undefined) throw new Error('un ancien client ne doit pas créer la clé');
  // 2. base avec date : l'ancien client publie une photo (lastUpdateAt = maintenant, hasPhoto vrai) sans écrire photoAt.
  await seedWithDate(h);
  await assertSucceeds(partial(h, { hasPhoto: true, updateText: null, lastUpdateAt: serverTs() }));
  await assertSucceeds(partial(h, { hasPhoto: false }));
  await assertSucceeds(partial(h, { presence: 'out', placeId: 'p1', presenceAt: inMs(-MIN) }));
  if ((await photoAtOf(h)) !== OLD_PHOTO.toMillis()) throw new Error('les mises à jour partielles d\'un ancien client doivent laisser la date telle quelle');
  // 3. Limite documentée : un `set` complet sans la clé (aucun client actuel n'en écrit sur un statut existant) fait perdre la date à son auteur seul.
  await assertSucceeds(full(h, { hasPhoto: true }));
  if ((await stored(h)).photoAt !== undefined) throw new Error('un set complet sans la clé la retire (limite documentée)');
});

s.tc('PA8', 'personne n\'écrit la date d\'un autre : un membre, la créatrice, un membre au partage arrêté, une étrangère, un visiteur (serveur ou date conservée)', async (h) => {
  await seedWithDate(h, 'alice');
  await seedWithDate(h, 'bob');
  const target = () => ({ updatedAt: serverTs(), hasPhoto: true, photoAt: serverTs() });
  // bob écrit le statut d'alice, alice celui de bob, carol (partage arrêté) et eve (étrangère) celui de bob, un visiteur aussi.
  await assertFails(statusRef(h, 'bob', 'alice').update(target()));
  await assertFails(statusRef(h, 'alice', 'bob').update(target()));
  await assertFails(statusRef(h, 'carol', 'bob').update(target()));
  await assertFails(statusRef(h, 'eve', 'bob').update(target()));
  await assertFails(h.anon().doc(`${M1}/status/bob`).update(target()));
  // Même une recopie « conservée » (non modifiante) est refusée à un autre : le statut n'est écrit que par son propriétaire.
  await assertFails(statusRef(h, 'bob', 'alice').update({ updatedAt: serverTs(), batteryPct: 11 }));
  if ((await photoAtOf(h, 'alice')) !== OLD_PHOTO.toMillis() || (await photoAtOf(h, 'bob')) !== OLD_PHOTO.toMillis()) throw new Error('aucune date ne devait changer');
});

s.tc('PA9', 'schéma fermé inchangé : variantes du nom refusées ; les clés obligatoires le restent ; la date ne change pas les bornes de lastUpdateAt / presenceAt (R-LIEU-1)', async (h) => {
  await seed(h);
  for (const key of ['photoAT', 'photo_at', 'photoat', 'photosAt', 'lastPhotoAt', 'photoDate', 'PhotoAt']) {
    await assertFails(partial(h, { [key]: serverTs() }));
    await assertFails(full(h, { [key]: serverTs() }));
  }
  // Une clé obligatoire manquante reste refusée même avec photoAt valide (set complet).
  const doc = statusDoc({ hasPhoto: true, photoAt: serverTs() });
  delete doc.hasPhoto;
  await assertFails(statusRef(h, 'bob').set(doc));
  const noUpdated = statusDoc({ hasPhoto: true, photoAt: serverTs() });
  delete noUpdated.updatedAt;
  await assertFails(statusRef(h, 'bob').set(noUpdated));
  // Bornes de lastUpdateAt inchangées : une photo publiée avec une heure d'appareil de plus de 24 h pour lastUpdateAt reste refusée.
  await assertFails(partial(h, { hasPhoto: true, photoAt: serverTs(), lastUpdateAt: inMs(-3 * DAY) }));
  await assertSucceeds(partial(h, { hasPhoto: true, photoAt: serverTs(), lastUpdateAt: serverTs() }));
});

s.tc('PA10', 'révocation puis réactivation : le statut supprimé emporte la date ; recréé, il ne la retrouve pas (ancienne valeur refusée, statut initial sans clé accepté = « pas de date »)', async (h) => {
  await setConsentV3(h, 'bob');
  await seedWithDate(h);
  await h.admin((f) => f.doc(`${M1}/status/bob`).delete());
  // Recréation (resource nul) avec l'ancienne date : refusée, ce n'est plus « la valeur de la base ».
  await assertFails(full(h, { hasPhoto: true, photoAt: OLD_PHOTO, presenceAt: inMs(-MIN), lastUpdateAt: inMs(-MIN) }));
  // Une date d'appareil, ou le serveur sans photo : refusées.
  await assertFails(full(h, { hasPhoto: true, photoAt: inMs(-MIN) }));
  await assertFails(full(h, { hasPhoto: false, photoAt: serverTs() }));
  // Statut initial de l'entrée dans une Miff / de la réactivation, SANS la clé : accepté (la date est perdue, documenté).
  await assertSucceeds(commit(h.db('carol'), [update(`${M1}/members/carol`, { sharing: sharingOnNow() }), set(`${M1}/status/carol`, statusInitial())]));
  if ((await stored(h, 'carol')).photoAt !== undefined) throw new Error('un statut initial ne porte pas de date de photo');
  // Lot [supprimer, recréer] : resource est l'état d'AVANT le lot (comme RL3b). Avec la MÊME date, accepté (équivaut à une mise à jour, aucune
  // valeur nouvelle) ; avec une date d'appareil, refusé : aucune valeur inventée n'est possible.
  await seedWithDate(h);
  await assertSucceeds(commit(h.db('bob'), [del(`${M1}/status/bob`), set(`${M1}/status/bob`, statusDoc({ hasPhoto: true, photoAt: OLD_PHOTO }))]));
  await seedWithDate(h);
  await assertFails(commit(h.db('bob'), [del(`${M1}/status/bob`), set(`${M1}/status/bob`, statusDoc({ hasPhoto: true, photoAt: inMs(-MIN) }))]));
});

s.tc('PA11', 'mode voyage : une update manuelle avec photo pendant un voyage écrit la date ; un voyage qui commence, change ou finit la conserve', async (h) => {
  await seedWithDate(h);
  const until = inMs(5 * DAY);
  await assertSucceeds(partial(h, { presence: 'travel', placeId: null, batteryPct: null, presenceAt: inMs(-MIN), travelUntil: until, travelUntilDay: '2026-11-25' }));
  if ((await photoAtOf(h)) !== OLD_PHOTO.toMillis()) throw new Error('commencer un voyage ne change pas la date de photo');
  await assertSucceeds(partial(h, { hasPhoto: true, updateText: null, lastUpdateAt: serverTs(), photoAt: serverTs() }));
  const dated = await photoAtOf(h);
  if (dated <= OLD_PHOTO.toMillis()) throw new Error('la photo publiée en voyage porte une nouvelle date');
  await assertSucceeds(partial(h, { presence: 'unknown', placeId: null, presenceAt: inMs(-MIN), travelUntil: null, travelUntilDay: null })); // fin du voyage
  if ((await photoAtOf(h)) !== dated) throw new Error('finir un voyage ne change pas la date de photo');
});

s.tc('PA12', 'lecture : la date est lisible par ceux qui lisent déjà le statut (membre qui partage), pas par les autres ; aucune lecture de plus (la date est DANS le statut)', async (h) => {
  await seedWithDate(h);
  const snap = await statusRef(h, 'alice', 'bob').get();
  if (!snap.exists || snap.data().photoAt.toMillis() !== OLD_PHOTO.toMillis()) throw new Error('un membre qui partage devrait lire la date dans le statut');
  await assertFails(statusRef(h, 'carol', 'bob').get()); // partage arrêté
  await assertFails(statusRef(h, 'eve', 'bob').get());
  await assertFails(h.anon().doc(`${M1}/status/bob`).get());
});

s.tc('PA13', 'revue du texte : `photoAt` dans hasOnly mais PAS dans hasAll ; validPhotoAt n\'a aucune lecture de règles (get, exists, getAfter, existsAfter) et reste appelée par validStatus', async () => {
  const text = readRules().replace(/\/\/.*$/gm, '');
  const status = text.slice(text.indexOf('function validStatus('), text.indexOf('function validPhotoAt('));
  const only = status.slice(status.indexOf('hasOnly('), status.indexOf('hasAll('));
  const all = status.slice(status.indexOf('hasAll('), status.indexOf('presence in'));
  if (!only.includes("'photoAt'")) throw new Error('photoAt devrait être dans la liste fermée (hasOnly)');
  if (all.includes("'photoAt'")) throw new Error('photoAt ne doit PAS être obligatoire (hasAll)');
  if (!status.includes('validPhotoAt(d)')) throw new Error('validStatus devrait appeler validPhotoAt');
  const fn = text.slice(text.indexOf('function validPhotoAt('), text.indexOf('function validTravel('));
  if (!fn.includes('request.time')) throw new Error('validPhotoAt devrait comparer à request.time');
  if (/\b(get|exists|getAfter|existsAfter)\s*\(\s*\//.test(fn)) throw new Error('validPhotoAt ne doit lire aucun document');
});
