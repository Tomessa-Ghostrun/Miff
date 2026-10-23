// Plan de test, section 10 : cas complémentaires issus de la revue de la PR #2 (X1 à X8).
// Chaque cas isole UNE règle (voir tests/tools/mutations.mjs : l'outil de mutation vérifie que le test
// devient rouge quand cette règle est affaiblie).
import {
  ID, CODE, DAY, HOUR, T0, suite, assertFails, assertSucceeds, commit, createMiffOps, removeMemberOps, update, set, del, serverTs,
  inMs, statusDoc, photoDoc, placeInput, sharingOnNow,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const all = (ops) => Object.values(ops);

s.tc('X1', 'photo : lisible par un membre qui partage, pas par un membre dont le partage est désactivé', async ({ db, admin }) => {
  await admin((f) => f.doc(`${M1}/photos/alice`).set(photoDoc()));
  await assertSucceeds(db('bob').doc(`${M1}/photos/alice`).get());
  await assertFails(db('carol').doc(`${M1}/photos/alice`).get());
});

s.tc('X2', 'joinCodes : un non-propriétaire ne le modifie ni ne le supprime', async ({ db }) => {
  for (const uid of ['bob', 'eve']) {
    // Modification neutre (même nom) : seul le contrôle du propriétaire peut refuser.
    await assertFails(db(uid).doc(`joinCodes/${CODE.M1}`).update({ miffName: 'Famille' }));
    await assertFails(db(uid).doc(`joinCodes/${CODE.M1}`).delete());
  }
});

s.tc('X3', 'profil : un autre utilisateur ne supprime pas users/{uid}', async ({ db }) => {
  await assertFails(db('eve').doc('users/alice').delete());
  await assertFails(db('bob').doc('users/alice').delete());
});

s.tc('X4', 'photo : refusée si le partage est désactivé, acceptée si activé dans le même batch', async ({ db }) => {
  const carol = db('carol');
  await assertFails(carol.doc(`${M1}/photos/carol`).set(photoDoc()));
  await assertSucceeds(
    commit(carol, [update(`${M1}/members/carol`, { sharing: sharingOnNow() }), set(`${M1}/photos/carol`, photoDoc())]),
  );
});

s.tc('X5', 'statut : lastUpdateAt, locationAccess, escalationDueAt et hasPhoto sont validés', async ({ db }) => {
  const ref = db('bob').doc(`${M1}/status/bob`);
  // lastUpdateAt : entre -1 jour et +5 minutes
  await assertFails(ref.set(statusDoc({ lastUpdateAt: inMs(DAY) })));
  await assertFails(ref.set(statusDoc({ lastUpdateAt: inMs(-3 * DAY) })));
  await assertSucceeds(ref.set(statusDoc({ lastUpdateAt: inMs(-2 * HOUR) })));
  // locationAccess : valeurs connues seulement
  await assertFails(ref.set(statusDoc({ locationAccess: 'banane' })));
  await assertSucceeds(ref.set(statusDoc({ locationAccess: 'foreground' })));
  // escalationDueAt : null, ou horodatage entre -1 jour et +1 jour
  await assertFails(ref.set(statusDoc({ escalationDueAt: inMs(3 * DAY) })));
  await assertFails(ref.set(statusDoc({ escalationDueAt: inMs(-3 * DAY) })));
  await assertFails(ref.set(statusDoc({ escalationDueAt: 'demain' })));
  await assertSucceeds(ref.set(statusDoc({ escalationDueAt: inMs(2 * HOUR) })));
  await assertSucceeds(ref.set(statusDoc({ escalationDueAt: null })));
  // hasPhoto : booléen
  await assertFails(ref.set(statusDoc({ hasPhoto: 'oui' })));
  await assertFails(ref.set(statusDoc({ hasPhoto: 1 })));
  await assertSucceeds(ref.set(statusDoc({ hasPhoto: true })));
});

s.tc('X6', 'photo restante : bloque le départ, le retrait et la désactivation du partage', async ({ db, admin }) => {
  await admin((f) => f.doc(`${M1}/photos/bob`).set(photoDoc({ createdAt: T0, expiresAt: inMs(DAY) })));
  const bob = db('bob');
  const alice = db('alice');
  // Départ en oubliant la photo
  await assertFails(
    commit(bob, [del(`${M1}/members/bob`), del(`${M1}/status/bob`), del('users/bob/memberships/free')]),
  );
  // Retrait par la créatrice en oubliant la photo (code renouvelé dans le lot : seule la photo manque)
  const { photo: _photo, ...withoutPhoto } = removeMemberOps({ target: 'bob' });
  await assertFails(commit(alice, Object.values(withoutPhoto)));
  // Désactivation du partage en oubliant la photo
  const off = { enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: serverTs() };
  await assertFails(commit(bob, [update(`${M1}/members/bob`, { sharing: off }), del(`${M1}/status/bob`)]));
  // Contrôle : la même désactivation avec la photo supprimée est acceptée.
  await assertSucceeds(
    commit(bob, [update(`${M1}/members/bob`, { sharing: off }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]),
  );
});

s.tc('X7', 'premium : un abonnement non expiré mais active=false ne donne pas les droits premium', async ({ db, admin }) => {
  await admin((f) => f.doc('entitlements/gina').set({ active: false, expiresAt: inMs(30 * DAY), source: 'manual', updatedAt: T0 }));
  await assertFails(commit(db('gina'), all(createMiffOps({ uid: 'gina', key: ID.NEW }))));
  // Contrôle : le même abonnement avec active=true est accepté.
  await admin((f) => f.doc('entitlements/gina').update({ active: true }));
  await assertSucceeds(commit(db('gina'), all(createMiffOps({ uid: 'gina', key: ID.NEW }))));
});

s.tc('X8', 'lieu : un simple membre ne crée pas p1 même s\'il est libre ; la créatrice le peut', async ({ db, admin }) => {
  await admin((f) => f.doc(`${M1}/places/p1`).delete());
  await assertFails(db('bob').doc(`${M1}/places/p1`).set(placeInput('bob')));
  await assertSucceeds(db('alice').doc(`${M1}/places/p1`).set(placeInput('alice')));
});
