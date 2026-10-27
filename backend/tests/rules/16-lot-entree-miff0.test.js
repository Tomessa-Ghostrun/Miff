// Plan de test, section 15 : MIFF-0, lot d'entrée « Rejoindre et partager » / « Créer et partager » (ticket
// docs/tickets/miff.md, §5.4, §9, décision QM2 et son extension à la création, 2026-10-24).
//   EN1 : rejoindre et partager = fiche (partage activé) + index + status/{moi} presence=unknown, en UN lot atomique
//   EN2 : créer et partager = Miff + code + fiche du créateur (partage activé) + index + status/{moi} initial, en UN lot
//   EN3 : un lot d'entrée dont une écriture est refusée n'écrit RIEN (adhésion)
//   EN4 : idem pour la création
//   EN5 : le statut initial : valeurs de locationAccess, absence de coordonnées, presence non imposée par les règles (contrat client)
//   EN6 : après l'entrée, la réciprocité est immédiate (le nouveau membre lit les statuts, les autres lisent le sien)
// Le plan de départ est celui de la section 0 (eve et frank ont un profil avec numéro, comme dans la plupart des fichiers).
// Chaque cas isole UNE protection (voir tests/tools/mutations-onbb.mjs).
import assert from 'node:assert/strict';
import {
  ID, CODE, commit, createShareOps, joinShareOps, statusInitial, set, del, sharingOnNow, sharingOff,
  suite, assertFails, assertSucceeds, serverTs, userDoc, PHONE, T0,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const NEWM = `miffs/${ID.NEW}`;
const all = (ops) => Object.values(ops);

async function stored(admin, path) {
  let data;
  await admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : undefined;
  });
  return data;
}

/** Aucun des documents du lot n'existe (le lot refusé n'a rien écrit). */
async function assertNothingWritten(admin, paths) {
  for (const path of paths) assert.equal(await stored(admin, path), undefined, `${path} ne devrait pas exister`);
}

const joinPaths = (uid) => [`${M1}/members/${uid}`, `${M1}/status/${uid}`, `users/${uid}/memberships/free`];
const createPaths = (uid) => [NEWM, `joinCodes/${CODE.NEW}`, `${NEWM}/members/${uid}`, `${NEWM}/status/${uid}`, `users/${uid}/memberships/free`];

async function assertAllFail(attempts) {
  for (const [label, attempt] of attempts) {
    try {
      await assertFails(attempt());
    } catch (error) {
      throw new Error(`« ${label} » aurait dû être refusé : ${error.message}`);
    }
  }
}

// ---------------------------------------------------------------------------------------------------------
// EN1 : Rejoindre et partager
// ---------------------------------------------------------------------------------------------------------
s.tc('EN1', 'rejoindre et partager : fiche (partage activé) + index + statut initial en un seul lot ; accepté, consentement tracé', async ({ db, admin }) => {
  const ops = joinShareOps({ uid: 'eve' });
  await assertSucceeds(commit(db('eve'), all(ops)));
  const member = await stored(admin, `${M1}/members/eve`);
  assert.equal(member.role, 'member');
  assert.equal(member.joinCode, CODE.M1);
  assert.equal(member.phone, PHONE);
  assert.equal(member.sharing.enabled, true);
  assert.ok(member.sharing.consentedAt, 'consentedAt = heure serveur');
  assert.equal(member.sharing.consentVersion, 'v1');
  assert.equal(member.sharing.revokedAt, null);
  const status = await stored(admin, `${M1}/status/eve`);
  assert.equal(status.presence, 'unknown');
  assert.equal(status.placeId, null);
  assert.equal(status.hasPhoto, false);
  assert.equal((await stored(admin, 'users/eve/memberships/free')).miffId, ID.M1);
  // L'ordre des écritures dans le lot n'a pas d'importance (le lot est évalué dans son état final).
  const frank = joinShareOps({ uid: 'frank' });
  await assertSucceeds(commit(db('frank'), [frank.status, frank.index, frank.member]));
  assert.equal((await stored(admin, `${M1}/status/frank`)).presence, 'unknown');
});

s.tc('EN1b', 'rejoindre et partager avec profil créé dans le même lot (profil + fiche + index + statut) : accepté', async ({ db, admin }) => {
  await admin((f) => f.doc('users/eve').delete());
  const ops = joinShareOps({ uid: 'eve' });
  await assertSucceeds(commit(db('eve'), [set('users/eve', userDoc({ phone: PHONE })), ...all(ops)]));
  assert.equal((await stored(admin, 'users/eve')).phone, PHONE);
  assert.equal((await stored(admin, `${M1}/status/eve`)).presence, 'unknown');
});

// ---------------------------------------------------------------------------------------------------------
// EN2 : Créer et partager
// ---------------------------------------------------------------------------------------------------------
s.tc('EN2', 'créer et partager : Miff + code + fiche du créateur (partage activé) + index + statut initial en un seul lot ; accepté', async ({ db, admin }) => {
  await assertSucceeds(commit(db('eve'), all(createShareOps({ uid: 'eve' }))));
  const member = await stored(admin, `${NEWM}/members/eve`);
  assert.equal(member.role, 'owner');
  assert.equal(member.sharing.enabled, true);
  assert.ok(member.sharing.consentedAt);
  assert.equal((await stored(admin, `${NEWM}/status/eve`)).presence, 'unknown');
  assert.equal((await stored(admin, `joinCodes/${CODE.NEW}`)).miffId, ID.NEW);
  assert.equal((await stored(admin, NEWM)).ownerUid, 'eve');
});

s.tc('EN2b', 'créer et partager avec profil créé dans le même lot (6 écritures) ; et en premium (clé = identifiant de la Miff) : accepté', async ({ db, admin }) => {
  await admin((f) => f.doc('users/eve').delete());
  await assertSucceeds(commit(db('eve'), [set('users/eve', userDoc({ phone: PHONE })), ...all(createShareOps({ uid: 'eve' }))]));
  assert.equal((await stored(admin, `${NEWM}/status/eve`)).presence, 'unknown');
  // dave (premium) n'a pas de Miff gratuite ici : il crée avec la clé miffId.
  await assertSucceeds(commit(db('dave'), all(createShareOps({ uid: 'dave', miffId: ID.NEW2, code: CODE.NEW2, key: ID.NEW2 }))));
  assert.equal((await stored(admin, `miffs/${ID.NEW2}/status/dave`)).presence, 'unknown');
});

// ---------------------------------------------------------------------------------------------------------
// EN3, EN4 : atomicité (une écriture refusée => rien n'est écrit)
// ---------------------------------------------------------------------------------------------------------
s.tc('EN3', 'rejoindre et partager : si UNE écriture est refusée, aucune des autres n\'est écrite (fiche, index, statut)', async ({ db, admin }) => {
  const bad = (mutate) => () => {
    const ops = joinShareOps({ uid: 'eve' });
    return commit(db('eve'), all(mutate(ops)));
  };
  await assertAllFail([
    ['statut invalide (batterie 150)', bad((o) => ({ ...o, status: set(`${M1}/status/eve`, statusInitial({ batteryPct: 150 })) }))],
    ['statut avec une présence inconnue du schéma', bad((o) => ({ ...o, status: set(`${M1}/status/eve`, statusInitial({ presence: 'home' })) }))],
    ['statut avec une clé en trop (coordonnées)', bad((o) => ({ ...o, status: set(`${M1}/status/eve`, { ...statusInitial(), lat: 48.85, lng: 2.35 }) }))],
    ['statut avec updatedAt du client', bad((o) => ({ ...o, status: set(`${M1}/status/eve`, statusInitial({ updatedAt: T0 })) }))],
    ['statut écrit pour un autre membre (frank)', bad((o) => ({ ...o, status: set(`${M1}/status/frank`, statusInitial()) }))],
    ['fiche avec partage désactivé mais statut publié', bad((o) => ({ ...o, member: set(`${M1}/members/eve`, { ...o.member.data, sharing: sharingOff() }) }))],
    ['consentement daté par le client', bad((o) => ({ ...o, member: set(`${M1}/members/eve`, { ...o.member.data, sharing: { ...sharingOnNow(), consentedAt: T0 } }) }))],
    ['consentement sans version du texte', bad((o) => ({ ...o, member: set(`${M1}/members/eve`, { ...o.member.data, sharing: { ...sharingOnNow(), consentVersion: null } }) }))],
    ['code d\'une autre Miff', bad((o) => ({ ...o, member: set(`${M1}/members/eve`, { ...o.member.data, joinCode: CODE.M2 }) }))],
    ['index absent', bad((o) => ({ member: o.member, status: o.status }))],
    ['index pointant vers une autre Miff', bad((o) => ({ ...o, index: set('users/eve/memberships/free', { miffId: ID.M2, joinedAt: serverTs() }) }))],
    ['numéro de la fiche différent du profil', bad((o) => ({ ...o, member: set(`${M1}/members/eve`, { ...o.member.data, phone: '+33600000042' }) }))],
  ]);
  await assertNothingWritten(admin, [...joinPaths('eve'), `${M1}/status/frank`]);
  // Contrôle positif : le lot corrigé passe.
  await assertSucceeds(commit(db('eve'), all(joinShareOps({ uid: 'eve' }))));
});

s.tc('EN3b', 'rejoindre et partager : lot sans statut = fiche avec partage actif mais lecture des autres refusée (ATK3) ; le statut manquant se publie ensuite', async ({ db, admin }) => {
  const ops = joinShareOps({ uid: 'eve' });
  await assertSucceeds(commit(db('eve'), [ops.member, ops.index]));
  await assertFails(db('eve').collection(`${M1}/status`).get());
  await assertSucceeds(db('eve').doc(`${M1}/status/eve`).set(statusInitial()));
  await assertSucceeds(db('eve').collection(`${M1}/status`).get());
  assert.equal((await stored(admin, `${M1}/status/eve`)).presence, 'unknown');
});

s.tc('EN4', 'créer et partager : si UNE écriture est refusée, rien n\'est écrit (Miff, code, fiche, index, statut)', async ({ db, admin }) => {
  const bad = (mutate) => () => {
    const ops = createShareOps({ uid: 'eve' });
    return commit(db('eve'), all(mutate(ops)));
  };
  await assertAllFail([
    ['statut invalide (locationAccess inconnu)', bad((o) => ({ ...o, status: set(`${NEWM}/status/eve`, statusInitial({ locationAccess: 'always' })) }))],
    ['statut invalide (texte trop long)', bad((o) => ({ ...o, status: set(`${NEWM}/status/eve`, statusInitial({ updateText: 'x'.repeat(141) })) }))],
    ['statut au nom d\'un autre', bad((o) => ({ ...o, status: set(`${NEWM}/status/frank`, statusInitial()) }))],
    ['fiche sans partage mais statut publié', bad((o) => ({ ...o, member: set(`${NEWM}/members/eve`, { ...o.member.data, sharing: sharingOff() }) }))],
    ['consentement daté par le client', bad((o) => ({ ...o, member: set(`${NEWM}/members/eve`, { ...o.member.data, sharing: { ...sharingOnNow(), consentedAt: T0 } }) }))],
    ['code absent', bad((o) => ({ miff: o.miff, member: o.member, index: o.index, status: o.status }))],
    ['index absent', bad((o) => ({ miff: o.miff, code: o.code, member: o.member, status: o.status }))],
    ['propriétaire usurpé', bad((o) => ({ ...o, miff: set(NEWM, { ...o.miff.data, ownerUid: 'alice' }) }))],
    ['numéro de la fiche différent du profil', bad((o) => ({ ...o, member: set(`${NEWM}/members/eve`, { ...o.member.data, phone: '+33600000042' }) }))],
  ]);
  await assertNothingWritten(admin, createPaths('eve'));
  await assertSucceeds(commit(db('eve'), all(createShareOps({ uid: 'eve' }))));
});

// ---------------------------------------------------------------------------------------------------------
// EN5 : le statut initial
// ---------------------------------------------------------------------------------------------------------
s.tc('EN5', 'statut initial : les quatre valeurs de locationAccess sont acceptées ; la présence « unknown » est une convention client, non imposée par les règles', async ({ db, admin }) => {
  const users = ['eve', 'frank', 'gina', 'hugo'];
  const accesses = ['background', 'foreground', 'denied', 'unknown'];
  for (const [i, access] of accesses.entries()) {
    const uid = users[i];
    await admin((f) => f.doc(`users/${uid}`).set(userDoc({ firstName: uid, phone: PHONE, createdAt: T0, termsAcceptedAt: T0 })));
    const ops = joinShareOps({ uid, status: statusInitial({ locationAccess: access }) });
    await assertSucceeds(commit(db(uid), all(ops)));
    assert.equal((await stored(admin, `${M1}/status/${uid}`)).locationAccess, access);
  }
  // Limite connue : les règles acceptent toute présence valide au premier statut (une app modifiée pourrait écrire « in_trusted »).
  await admin((f) => f.doc('users/zed').set(userDoc({ firstName: 'zed', phone: PHONE, createdAt: T0, termsAcceptedAt: T0 })));
  await assertSucceeds(commit(db('zed'), all(joinShareOps({ uid: 'zed', status: statusInitial({ presence: 'in_trusted', placeId: 'p1' }) }))));
});

// ---------------------------------------------------------------------------------------------------------
// EN6 : réciprocité immédiate après l'entrée
// ---------------------------------------------------------------------------------------------------------
s.tc('EN6', 'après le lot d\'entrée : le nouveau membre lit les statuts et les autres lisent le sien ; un lot de création rend le créateur lisible par les suivants', async ({ db, admin }) => {
  await admin((f) => f.doc('users/gina').set(userDoc({ firstName: 'gina', phone: PHONE, createdAt: T0, termsAcceptedAt: T0 })));
  await assertSucceeds(commit(db('eve'), all(joinShareOps({ uid: 'eve' }))));
  const list = await assertSucceeds(db('eve').collection(`${M1}/status`).get());
  assert.deepEqual(list.docs.map((d) => d.id).sort(), ['alice', 'bob', 'eve']);
  const seen = await assertSucceeds(db('bob').doc(`${M1}/status/eve`).get());
  assert.equal(seen.data().presence, 'unknown');
  // Carol n'a pas activé son partage : elle ne lit toujours pas (réciprocité inchangée).
  await assertFails(db('carol').doc(`${M1}/status/eve`).get());
  // Création : le créateur lit son propre statut, et un suivant (frank, avec le code) entre et lit le statut du créateur.
  await assertSucceeds(commit(db('gina'), all(createShareOps({ uid: 'gina' }))));
  await assertSucceeds(db('gina').doc(`${NEWM}/status/gina`).get());
  await assertSucceeds(commit(db('frank'), all(joinShareOps({ uid: 'frank', miffId: ID.NEW, code: CODE.NEW }))));
  const seenGina = await assertSucceeds(db('frank').doc(`${NEWM}/status/gina`).get());
  assert.equal(seenGina.data().presence, 'unknown');
});
