// Plan de test, section 8 : profil, appareils, suppression de compte (U1 à U8)
import {
  ID, CODE, T0, suite, assertFails, assertSucceeds, commit, leaveOps, set, del, serverTs, bytes, userDoc, sessionSeed, SESSION_PATH, deviceDoc, deviceSeed, DEVICE_DOC_PATH,
} from '../helpers/fixtures.js';

const s = suite({ extraProfiles: false });
const M1 = `miffs/${ID.M1}`;

s.tc('U1', 'créer son profil avec termsAcceptedAt = heure serveur : accepté', async ({ db }) => {
  await assertSucceeds(db('eve').doc('users/eve').set(userDoc()));
});

s.tc('U2', 'profil invalide (prénom vide, avatar > 20 Ko, clé inconnue) : refusé', async ({ db }) => {
  const ref = db('eve').doc('users/eve');
  await assertFails(ref.set(userDoc({ firstName: '' })));
  await assertFails(ref.set(userDoc({ avatar: bytes(20001) })));
  await assertFails(ref.set(userDoc({ surnom: 'Evie' })));
});

s.tc('U3', 'createdAt du profil non modifiable', async ({ db, admin }) => {
  await admin((f) => f.doc('users/eve').set(userDoc({ createdAt: T0, termsAcceptedAt: T0 })));
  await assertFails(db('eve').doc('users/eve').update({ createdAt: serverTs() }));
});

s.tc('U4', 'on supprime son propre profil', async ({ db, admin }) => {
  await admin((f) => f.doc('users/eve').set(userDoc({ createdAt: T0, termsAcceptedAt: T0 })));
  await assertSucceeds(db('eve').doc('users/eve').delete());
});

s.tc('U5', "appareil (SRV-2R, jeton de notification) : platform ios accepté, web refusé ; le document porte l'identifiant de la session", async ({ db, admin }) => {
  await admin((f) => f.doc(SESSION_PATH('eve')).set(sessionSeed()));
  const ref = db('eve').doc(DEVICE_DOC_PATH('eve'));
  await assertFails(ref.set(deviceDoc({ platform: 'web' })));
  await assertSucceeds(ref.set(deviceDoc({ platform: 'ios' })));
});

s.tc('U6', "les appareils d'un autre utilisateur ne sont pas lisibles", async ({ db, admin }) => {
  await admin((f) => f.doc(DEVICE_DOC_PATH('eve')).set(deviceSeed()));
  await assertFails(db('bob').doc(DEVICE_DOC_PATH('eve')).get());
});

s.tc('U7', 'suppression de compte d\'une membre simple : un batch par Miff, puis le profil', async ({ db }) => {
  await assertSucceeds(commit(db('bob'), [...Object.values(leaveOps('bob')), del('users/bob')]));
});

s.tc('U8', 'créatrice avec membres restants : suppression de la Miff acceptée avec pierre tombale et suppression du code', async ({ db }) => {
  // Les règles acceptent (la Miff devient orpheline mais son identifiant et son code sont morts : ATK1 et ATK2) ;
  // c'est au client de retirer les membres d'abord (risque documenté dans data-model.md).
  await assertSucceeds(
    commit(db('alice'), [
      del(M1),
      set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
      del(`joinCodes/${CODE.M1}`),
      del(`${M1}/members/alice`),
      del(`${M1}/status/alice`),
      del('users/alice/memberships/free'),
    ]),
  );
});
