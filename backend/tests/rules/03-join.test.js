// Plan de test, section 3 : rejoindre une Miff avec le code secret (J1 à J18)
import {
  ID, CODE, T0, userDoc, suite, assertFails, assertSucceeds, commit, joinOps, FieldPath, Timestamp, serverTs,
} from '../helpers/fixtures.js';

const s = suite();
const all = (ops) => Object.values(ops);

s.tc('J1', 'get joinCodes/<code> : renvoie miffId et nom', async ({ db }) => {
  const snap = await assertSucceeds(db('eve').doc(`joinCodes/${CODE.M1}`).get());
  if (!snap.exists || snap.data().miffId !== ID.M1 || snap.data().miffName !== 'Famille') {
    throw new Error(`document joinCodes inattendu : ${JSON.stringify(snap.data())}`);
  }
});

s.tc('J2', 'code inexistant : document introuvable, sans erreur de permission', async ({ db }) => {
  const snap = await assertSucceeds(db('eve').doc(`joinCodes/${CODE.UNKNOWN}`).get());
  if (snap.exists) throw new Error(`le code ${CODE.UNKNOWN} ne devrait pas exister`);
});

s.tc('J3', 'code au mauvais format : refusé', async ({ db }) => {
  await assertFails(db('eve').doc('joinCodes/abc').get());
});

s.tc('J4', 'rejoindre avec le bon code : accepté', async ({ db }) => {
  await assertSucceeds(commit(db('eve'), all(joinOps({ uid: 'eve' }))));
});

s.tc('J5', 'code d\'une autre Miff : refusé', async ({ db }) => {
  await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve', miffId: ID.M1, code: CODE.M2 }))));
});

s.tc('J6', 'sans champ joinCode : refusé', async ({ db }) => {
  const ops = joinOps({ uid: 'eve' });
  delete ops.member.data.joinCode;
  await assertFails(commit(db('eve'), all(ops)));
});

s.tc('J7', 'identifiant de Miff connu mais code inventé : refusé', async ({ db }) => {
  await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve', code: CODE.NEW }))));
});

s.tc('J8', 'rejoindre avec role="owner" : refusé', async ({ db }) => {
  const ops = joinOps({ uid: 'eve' });
  ops.member.data.role = 'owner';
  await assertFails(commit(db('eve'), all(ops)));
});

s.tc('J9', 'sans écrire l\'index memberships : refusé', async ({ db }) => {
  await assertFails(commit(db('eve'), [joinOps({ uid: 'eve' }).member]));
});

s.tc('J10', 'gratuit déjà dans une Miff : second slot "free" refusé', async ({ db }) => {
  await assertFails(commit(db('bob'), all(joinOps({ uid: 'bob', miffId: ID.M2, code: CODE.M2 }))));
});

s.tc('J11', 'gratuit : clé = identifiant de Miff (réservée au premium) refusée', async ({ db }) => {
  await assertFails(commit(db('bob'), all(joinOps({ uid: 'bob', miffId: ID.M2, code: CODE.M2, key: ID.M2 }))));
});

s.tc('J12', 'premium : rejoindre une Miff avec la clé = identifiant : accepté', async ({ db }) => {
  await assertSucceeds(commit(db('dave'), all(joinOps({ uid: 'dave', miffId: ID.M2, code: CODE.M2, key: ID.M2 }))));
});

s.tc('J13', 'écrire le document membre d\'un autre utilisateur : refusé', async ({ db, admin }) => {
  // Modification du document d'un membre existant (chemin "update").
  const ops = joinOps({ uid: 'bob' });
  await assertFails(commit(db('eve'), [ops.member]));
  // Création du document d'un non-membre (chemin "create") : l'index et le profil de zed existent déjà et le code est le bon,
  // donc seule la règle « on n'écrit que son propre document » peut refuser.
  await admin((f) => f.doc('users/zed').set(userDoc({ createdAt: T0, termsAcceptedAt: T0 })));
  await admin((f) => f.doc('users/zed/memberships/free').set({ miffId: ID.M1, joinedAt: new Date() }));
  await assertFails(commit(db('eve'), [joinOps({ uid: 'zed' }).member]));
});

s.tc('J14', 'partage activé avec consentedAt fourni par le client : refusé', async ({ db }) => {
  const sharing = { enabled: true, consentedAt: Timestamp.now(), consentVersion: 'v1', revokedAt: null };
  await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve', sharing }))));
});

s.tc('J15', 'partage activé sans consentVersion (null ou absent) : refusé', async ({ db }) => {
  const nullVersion = { enabled: true, consentedAt: serverTs(), consentVersion: null, revokedAt: null };
  await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve', sharing: nullVersion }))));
  const missingVersion = { enabled: true, consentedAt: serverTs(), revokedAt: null };
  await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve', sharing: missingVersion }))));
});

s.tc('J16', 'après régénération du code, l\'ancien code ne permet plus de rejoindre', async ({ db, admin }) => {
  await admin(async (f) => {
    const batch = f.batch();
    batch.update(f.doc(`miffs/${ID.M1}`), { joinCode: CODE.REGEN });
    batch.set(f.doc(`joinCodes/${CODE.REGEN}`), { miffId: ID.M1, miffName: 'Famille', createdAt: Timestamp.now() });
    batch.delete(f.doc(`joinCodes/${CODE.M1}`));
    await batch.commit();
  });
  await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve', code: CODE.M1 }))));
  // Contrôle : le nouveau code, lui, fonctionne.
  await assertSucceeds(commit(db('eve'), all(joinOps({ uid: 'eve', code: CODE.REGEN }))));
});

s.tc('J17', 'énumération : list sur joinCodes refusé (avec ou sans filtre)', async ({ db }) => {
  const eve = db('eve');
  await assertFails(eve.collection('joinCodes').get());
  await assertFails(eve.collection('joinCodes').where('miffId', '==', ID.M1).get());
  await assertFails(eve.collection('joinCodes').orderBy(FieldPath.documentId()).startAt('A').get());
});

s.tc('J18', 'lire la Miff avant d\'être membre : refusé', async ({ db }) => {
  await assertFails(db('eve').doc(`miffs/${ID.M1}`).get());
});
