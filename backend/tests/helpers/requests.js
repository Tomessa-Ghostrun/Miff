// Outils des tests de la DEMANDE D'AJOUT D'UN LIEU (LIEU-REQ-A, ticket docs/tickets/demande-ajout-lieu.md) : chemins, documents et lots
// tels qu'un client les écrit (contrat : docs/dev/demande-lieu-clients.md).
import { FieldValue, ID, T0, serverTs, set, setMerge, del, placeInput, CODE, profile, sharingOff, inMs, DAY } from './fixtures.js';

export const reqPath = (miffId, uid, k = 1) => `miffs/${miffId}/placeRequests/${uid}_${k}`;
export const counterPath = (miffId = ID.M1) => `miffs/${miffId}/meta/placeRequests`;
export const reqId = (uid, k = 1) => `${uid}_${k}`;

/** Demande telle qu'un client l'envoie : 8 clés, heure du serveur. */
export const requestInput = (uid, over = {}) => ({
  requestedBy: uid,
  name: 'Chez Mamie',
  type: 'trusted',
  category: 'relative',
  address: '12 rue des Lilas, 69003 Lyon',
  lat: 45.764,
  lng: 4.8357,
  radiusM: 150,
  createdAt: serverTs(),
  ...over,
});

/** Demande déjà enregistrée (état injecté avec les règles désactivées), créée il y a `ageDays` jours. */
export const requestSeed = (uid, over = {}, ageDays = 1) => ({ ...requestInput(uid), createdAt: inMs(-ageDays * DAY), ...over });

/** Écriture du compteur par un membre (création ou retrait de SA demande) : `set` avec fusion, increment de ±1, lien vers la demande. */
export const counterMove = (rid, delta, over = {}) => ({ pending: FieldValue.increment(delta), lastReqId: rid, updatedAt: serverTs(), ...over });

/** Écriture du compteur par le créateur : valeur absolue (recalage) ou increment, jamais de lien vers une demande. */
export const counterOwner = (pending, over = {}) => ({ pending, lastReqId: null, updatedAt: serverTs(), ...over });

/** Compteur déjà enregistré (état injecté). */
export const counterSeed = (pending, lastReqId = null) => ({ pending, lastReqId, updatedAt: T0 });

/** Lot de création d'une demande par un membre : la demande + le compteur (+1), 2 écritures. */
export function createRequestOps({ uid, miffId = ID.M1, k = 1, data = requestInput(uid), counter = counterMove(reqId(uid, k), 1) } = {}) {
  return { request: set(reqPath(miffId, uid, k), data), counter: setMerge(counterPath(miffId), counter) };
}

/** Lot de retrait d'une demande par son auteur : suppression + compteur (-1), 2 écritures. */
export function withdrawRequestOps({ uid, miffId = ID.M1, k = 1, counter = counterMove(reqId(uid, k), -1) } = {}) {
  return { request: del(reqPath(miffId, uid, k)), counter: setMerge(counterPath(miffId), counter) };
}

/** Lot « Refuser » du créateur : suppression de la demande + compteur (increment(-1), ou valeur absolue `left` = demandes restantes lues dans sa liste), 2 écritures. */
export function declineRequestOps({ from = 'bob', miffId = ID.M1, k = 1, left = null } = {}) {
  return { request: del(reqPath(miffId, from, k)), counter: setMerge(counterPath(miffId), counterOwner(left === null ? FieldValue.increment(-1) : left)) };
}

/**
 * Lot « Accepter » du créateur : création du lieu (copie des champs de la demande ; `category` copiée de la demande ;
 * `priority` nulle) + suppression de la demande + compteur (-1), 3 écritures.
 */
export function acceptRequestOps({ owner = 'alice', from = 'bob', miffId = ID.M1, k = 1, placeId = 'p2', place = {}, left = null } = {}) {
  return {
    place: set(`miffs/${miffId}/places/${placeId}`, placeInput(owner, {
      name: 'Chez Mamie', type: 'trusted', category: 'relative', address: '12 rue des Lilas, 69003 Lyon', lat: 45.764, lng: 4.8357, radiusM: 150, ...place,
    })),
    request: del(reqPath(miffId, from, k)),
    counter: setMerge(counterPath(miffId), counterOwner(left === null ? FieldValue.increment(-1) : left)),
  };
}

/** Injecte (règles désactivées) des demandes et leur compteur cohérent : `list` = [[uid, k], ...]. */
export const seedRequests = (h, list, { miffId = ID.M1, counter = list.length, over = {} } = {}) =>
  h.admin(async (f) => {
    for (const [uid, k] of list) await f.doc(reqPath(miffId, uid, k)).set(requestSeed(uid, over));
    if (counter !== null) await f.doc(counterPath(miffId)).set(counterSeed(counter));
  });

/** Ajoute `dave` (premium actif dans le jeu de départ) comme MEMBRE de M1 (règles désactivées) : demandeur Miff+. */
export const addPremiumMember = (h) =>
  h.admin((f) => f.doc(`miffs/${ID.M1}/members/dave`).set({
    role: 'member', ...profile('dave'), joinedAt: T0, joinCode: CODE.M1, sharing: sharingOff(),
  }));
