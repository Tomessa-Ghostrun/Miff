// Outils communs des tests de règles : initialisation de l'émulateur, jeu de
// données de départ (plan de test, section 0), constructeurs de batches.
//
// Les noms de comptes ('alice', 'bob'...) sont aussi les UID Firebase Auth simulés.

import { readFileSync } from 'node:fs';
import { before, beforeEach, after, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import firebase from 'firebase/compat/app';
import 'firebase/compat/firestore';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { assertSafeEnvironment } from './guard.js';

export { assertSucceeds, assertFails };

export const PROJECT_ID = 'demo-miff-rules';
export const RULES_PATH = fileURLToPath(new URL('../../firestore.rules', import.meta.url));
// MIFF_RULES_FILE : réservé à l'outil de mutation (tests/tools/mutation-check.mjs), qui teste des règles volontairement affaiblies.
export const readRules = () => readFileSync(process.env.MIFF_RULES_FILE ?? RULES_PATH, 'utf8');

// ---------------------------------------------------------------------------
// Types Firestore (API "compat" utilisée par @firebase/rules-unit-testing)
// ---------------------------------------------------------------------------
const { Timestamp, FieldValue, Blob, FieldPath } = firebase.firestore;
export { Timestamp, FieldValue, Blob, FieldPath };
export const serverTs = () => FieldValue.serverTimestamp();
export const bytes = (n) => Blob.fromUint8Array(new Uint8Array(n));
export const inMs = (ms) => Timestamp.fromMillis(Date.now() + ms);
export const HOUR = 3_600_000;
export const DAY = 86_400_000;
export const T0 = Timestamp.fromDate(new Date('2026-01-01T10:00:00Z'));

// ---------------------------------------------------------------------------
// Identifiants de la section 0 du plan (un ID de Miff = 20 caractères alphanumériques)
// ---------------------------------------------------------------------------
export const ID = {
  M1: 'M1'.padEnd(20, 'x'),
  M2: 'M2'.padEnd(20, 'x'),
  M3: 'M3'.padEnd(20, 'x'),
  M4: 'M4'.padEnd(20, 'x'),
  NEW: 'NEW'.padEnd(20, 'x'),
  NEW2: 'NEWTWO'.padEnd(20, 'x'),
};
// Codes secrets valides : 12 caractères parmi A-H J-K M-N P-Z 2-9.
export const CODE = {
  M1: 'K7M2QX9PDW4H',
  M2: 'R5T8VNBCFG3Y',
  M3: 'D3FGHK5MNP7R',
  M4: 'QSTVWXYZ2345',
  NEW: 'BCDFGHK23456',
  NEW2: 'N2P3Q4R5S6T7',
  REGEN: 'XYZ23456789A',
  UNKNOWN: 'ZZZZZZZZZZZZ',
};

// ---------------------------------------------------------------------------
// Constructeurs de documents
// ---------------------------------------------------------------------------
export const sharingOff = () => ({ enabled: false, consentedAt: null, consentVersion: null, revokedAt: null });
export const sharingOnSeed = () => ({ enabled: true, consentedAt: T0, consentVersion: 'v1', revokedAt: null });
export const sharingOnNow = () => ({ enabled: true, consentedAt: serverTs(), consentVersion: 'v1', revokedAt: null });

export const profile = (first) => ({ firstName: first, lastName: 'Test' });

export const statusDoc = (over = {}) => ({
  presence: 'in_trusted',
  placeId: 'p1',
  presenceAt: inMs(-HOUR),
  lastUpdateAt: inMs(-HOUR),
  updateText: null,
  hasPhoto: false,
  batteryPct: 80,
  locationAccess: 'background',
  escalationDueAt: null,
  updatedAt: serverTs(),
  ...over,
});

export const placeSeed = (over = {}) => ({
  name: 'Maison',
  type: 'trusted',
  address: null,
  lat: 48.8566,
  lng: 2.3522,
  radiusM: 100,
  priority: null,
  createdBy: 'alice',
  createdAt: T0,
  updatedAt: T0,
  ...over,
});

// Lieu tel qu'un client l'envoie à la création.
export const placeInput = (uid, over = {}) => ({
  name: 'Ecole',
  type: 'trusted',
  address: null,
  lat: 48.85,
  lng: 2.35,
  radiusM: 150,
  priority: null,
  createdBy: uid,
  createdAt: serverTs(),
  updatedAt: serverTs(),
  ...over,
});

export const photoDoc = (over = {}) => ({
  jpeg: bytes(1000),
  createdAt: serverTs(),
  expiresAt: inMs(DAY),
  ...over,
});

export const userDoc = (over = {}) => ({
  firstName: 'Eve',
  lastName: 'Test',
  createdAt: serverTs(),
  termsAcceptedAt: serverTs(),
  termsVersion: 'v1',
  ...over,
});

// ---------------------------------------------------------------------------
// Opérations de batch : { type: 'set' | 'update' | 'delete', path, data }
// ---------------------------------------------------------------------------
export const set = (path, data) => ({ type: 'set', path, data });
export const update = (path, data) => ({ type: 'update', path, data });
export const del = (path) => ({ type: 'delete', path });

export function commit(db, ops) {
  const batch = db.batch();
  for (const op of ops) {
    const ref = db.doc(op.path);
    if (op.type === 'set') batch.set(ref, op.data);
    else if (op.type === 'update') batch.update(ref, op.data);
    else batch.delete(ref);
  }
  return batch.commit();
}

/** Création d'une Miff : 4 écritures (Miff, code, membre créateur, index). */
export function createMiffOps({
  uid,
  miffId = ID.NEW,
  code = CODE.NEW,
  name = 'Cercle',
  key = 'free',
  sharing = sharingOff(),
} = {}) {
  return {
    miff: set(`miffs/${miffId}`, { ownerUid: uid, name, joinCode: code, createdAt: serverTs() }),
    code: set(`joinCodes/${code}`, { miffId, miffName: name, createdAt: serverTs() }),
    member: set(`miffs/${miffId}/members/${uid}`, {
      role: 'owner',
      ...profile(uid),
      joinedAt: serverTs(),
      sharing,
    }),
    index: set(`users/${uid}/memberships/${key}`, { miffId, joinedAt: serverTs() }),
  };
}

/** Rejoindre une Miff : 2 écritures (membre, index). */
export function joinOps({ uid, miffId = ID.M1, code = CODE.M1, key = 'free', sharing = sharingOff() } = {}) {
  return {
    member: set(`miffs/${miffId}/members/${uid}`, {
      role: 'member',
      ...profile(uid),
      joinedAt: serverTs(),
      joinCode: code,
      sharing,
    }),
    index: set(`users/${uid}/memberships/${key}`, { miffId, joinedAt: serverTs() }),
  };
}

/** Quitter une Miff (batch de 4 suppressions). */
export function leaveOps(uid, miffId = ID.M1, key = 'free') {
  return {
    member: del(`miffs/${miffId}/members/${uid}`),
    status: del(`miffs/${miffId}/status/${uid}`),
    photo: del(`miffs/${miffId}/photos/${uid}`),
    index: del(`users/${uid}/memberships/${key}`),
  };
}

// ---------------------------------------------------------------------------
// Jeu de données de départ (plan, section 0) + M2 (nécessaire aux cas J5, J10 à J12)
// ---------------------------------------------------------------------------
function addMiff(batch, f, { miffId, name, code, ownerUid, members, status = [], place = false }) {
  batch.set(f.doc(`miffs/${miffId}`), { ownerUid, name, joinCode: code, createdAt: T0 });
  batch.set(f.doc(`joinCodes/${code}`), { miffId, miffName: name, createdAt: T0 });
  for (const m of members) {
    const data = {
      role: m.role,
      ...profile(m.uid),
      joinedAt: T0,
      sharing: m.sharing ? sharingOnSeed() : sharingOff(),
    };
    if (m.role === 'member') data.joinCode = code;
    batch.set(f.doc(`miffs/${miffId}/members/${m.uid}`), data);
    batch.set(f.doc(`users/${m.uid}/memberships/${m.key ?? 'free'}`), { miffId, joinedAt: T0 });
  }
  for (const uid of status) {
    batch.set(f.doc(`miffs/${miffId}/status/${uid}`), statusDoc({ updatedAt: T0 }));
  }
  if (place) batch.set(f.doc(`miffs/${miffId}/places/p1`), placeSeed({ createdBy: ownerUid }));
}

async function seedBase(env) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    const batch = f.batch();
    for (const uid of ['alice', 'bob', 'carol', 'dave', 'zoe']) {
      batch.set(f.doc(`users/${uid}`), userDoc({ ...profile(uid), createdAt: T0, termsAcceptedAt: T0 }));
    }
    batch.set(f.doc('entitlements/dave'), { active: true, expiresAt: inMs(30 * DAY), source: 'manual', updatedAt: T0 });
    batch.set(f.doc('entitlements/frank'), { active: true, expiresAt: inMs(-DAY), source: 'manual', updatedAt: T0 });
    addMiff(batch, f, {
      miffId: ID.M1,
      name: 'Famille',
      code: CODE.M1,
      ownerUid: 'alice',
      members: [
        { uid: 'alice', role: 'owner', sharing: true },
        { uid: 'bob', role: 'member', sharing: true },
        { uid: 'carol', role: 'member', sharing: false },
      ],
      status: ['alice', 'bob'],
      place: true,
    });
    addMiff(batch, f, {
      miffId: ID.M2,
      name: 'Amis',
      code: CODE.M2,
      ownerUid: 'zoe',
      members: [{ uid: 'zoe', role: 'owner', sharing: false }],
    });
    await batch.commit();
  });
}

/** Ajoute, avec les règles désactivées, une Miff supplémentaire (ex. M3 de dave, M4 de frank). */
export async function adminSeedMiff(env, spec) {
  await adminDo(env, async (f) => {
    const batch = f.batch();
    addMiff(batch, f, spec);
    await batch.commit();
  });
}

/** Exécute du code avec les règles désactivées (préparation d'état uniquement). */
export async function adminDo(env, callback) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await callback(ctx.firestore());
  });
}

// ---------------------------------------------------------------------------
// Suite de tests : branche l'émulateur, nettoie et recharge les données avant chaque cas
// ---------------------------------------------------------------------------
export function suite() {
  let env;
  let contexts = new Map();

  const h = {
    get env() {
      return env;
    },
    /** Firestore authentifié comme `uid`. */
    db(uid) {
      if (!contexts.has(uid)) contexts.set(uid, env.authenticatedContext(uid).firestore());
      return contexts.get(uid);
    },
    /** Firestore sans connexion. */
    anon() {
      if (!contexts.has('<anon>')) contexts.set('<anon>', env.unauthenticatedContext().firestore());
      return contexts.get('<anon>');
    },
    admin: (callback) => adminDo(env, callback),
    seedMiff: (spec) => adminSeedMiff(env, spec),
  };

  before(async () => {
    assertSafeEnvironment({ projectId: PROJECT_ID });
    env = await initializeTestEnvironment({ projectId: PROJECT_ID, firestore: { rules: readRules() } });
  });
  beforeEach(async () => {
    contexts = new Map();
    await env.clearFirestore();
    await seedBase(env);
  });
  after(async () => {
    if (env) await env.cleanup();
  });

  /** Déclare un test dont le nom commence par l'identifiant du plan (R1, C8, J16...). */
  h.tc = (id, title, fn) => test(`${id} - ${title}`, () => fn(h));
  /** Cas non automatisable : affiché « ignoré » avec la raison. */
  h.skipped = (id, title, reason) => test(`${id} - ${title}`, { skip: reason }, () => {});
  return h;
}

export { seedBase };
