// Plan de test, section 2 : création d'une Miff (C1 à C16).
// C17 (nombre de lectures de règles) : voir 09-rule-reads.test.js
import {
  ID, CODE, suite, assertFails, assertSucceeds, commit, createMiffOps, set, serverTs, Timestamp,
} from '../helpers/fixtures.js';

const s = suite();

// Un batch de création est envoyé en bloc : il réussit ou échoue globalement.
const all = (ops) => Object.values(ops);
const without = (ops, ...names) =>
  Object.entries(ops)
    .filter(([name]) => !names.includes(name))
    .map(([, op]) => op);

s.tc('C1', 'gratuite sans Miff : le batch complet de création est accepté', async ({ db }) => {
  await assertSucceeds(commit(db('eve'), all(createMiffOps({ uid: 'eve' }))));
});

s.tc('C2', 'usurpation : ownerUid d\'un autre utilisateur refusé', async ({ db }) => {
  const ops = createMiffOps({ uid: 'eve' });
  ops.miff.data.ownerUid = 'alice';
  await assertFails(commit(db('eve'), all(ops)));
});

s.tc('C3', 'sans le document membre du créateur : refusé (Miff incomplète)', async ({ db }) => {
  await assertFails(commit(db('eve'), without(createMiffOps({ uid: 'eve' }), 'member')));
});

s.tc('C4', 'sans le document joinCodes : refusé', async ({ db }) => {
  await assertFails(commit(db('eve'), without(createMiffOps({ uid: 'eve' }), 'code')));
});

s.tc('C5', 'identifiant de Miff de 19 ou 21 caractères, ou "free" : refusé', async ({ db }) => {
  for (const miffId of [ID.NEW.slice(0, 19), `${ID.NEW}y`, 'free']) {
    await assertFails(commit(db('eve'), all(createMiffOps({ uid: 'eve', miffId }))));
  }
});

s.tc('C6', 'code de 11 ou 13 caractères, ou avec O 0 I 1 L ou minuscules : refusé', async ({ db }) => {
  const head = CODE.NEW.slice(0, 11);
  const bad = [
    head, // 11 caractères
    `${CODE.NEW}K`, // 13 caractères
    `${head}O`,
    `${head}0`,
    `${head}I`,
    `${head}1`,
    `${head}L`,
    CODE.NEW.toLowerCase(),
  ];
  for (const code of bad) {
    await assertFails(commit(db('eve'), all(createMiffOps({ uid: 'eve', code }))));
  }
});

s.tc('C7', 'createdAt fourni par le client (pas l\'heure serveur) : refusé', async ({ db }) => {
  const ops = createMiffOps({ uid: 'eve' });
  ops.miff.data.createdAt = Timestamp.now();
  await assertFails(commit(db('eve'), all(ops)));
});

s.tc('C8', 'seconde Miff avec la clé "free" déjà utilisée : refusé', async ({ db }) => {
  await assertFails(commit(db('alice'), all(createMiffOps({ uid: 'alice' }))));
});

s.tc('C9', 'gratuite : seconde Miff avec la clé = nouvel identifiant : refusé', async ({ db }) => {
  await assertFails(commit(db('alice'), all(createMiffOps({ uid: 'alice', key: ID.NEW }))));
});

s.tc('C10', 'premium : seconde Miff avec la clé = identifiant de Miff : accepté', async ({ db, seedMiff }) => {
  await seedMiff({
    miffId: ID.M3,
    name: 'Premiere',
    code: CODE.M3,
    ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: true }],
  });
  await assertSucceeds(commit(db('dave'), all(createMiffOps({ uid: 'dave', key: ID.NEW }))));
});

s.tc('C11', 'premium expiré : création avec la clé = identifiant : refusé', async ({ db }) => {
  await assertFails(commit(db('frank'), all(createMiffOps({ uid: 'frank', key: ID.NEW }))));
});

s.tc('C12', 'créer un joinCodes pour la Miff d\'un autre : refusé', async ({ db }) => {
  await assertFails(
    commit(db('eve'), [set(`joinCodes/${CODE.NEW}`, { miffId: ID.M1, miffName: 'Famille', createdAt: serverTs() })]),
  );
});

s.tc('C13', 'joinCodes.miffName différent du nom de la Miff : refusé', async ({ db }) => {
  const ops = createMiffOps({ uid: 'eve', name: 'Cercle' });
  ops.code.data.miffName = 'Autre nom';
  await assertFails(commit(db('eve'), all(ops)));
});

s.tc('C14', 'collision : créer un code qui existe déjà : refusé', async ({ db }) => {
  await assertFails(commit(db('eve'), all(createMiffOps({ uid: 'eve', code: CODE.M1 }))));
});

s.tc('C15', 'devenir "owner" d\'une Miff déjà existante : refusé', async ({ db }) => {
  const ops = createMiffOps({ uid: 'eve', miffId: ID.M1 });
  await assertFails(commit(db('eve'), [ops.member, ops.index]));
});

s.tc('C16', 'champ inconnu dans la Miff, le membre ou le code : refusé', async ({ db }) => {
  for (const target of ['miff', 'member', 'code']) {
    const ops = createMiffOps({ uid: 'eve' });
    ops[target].data.extra = 1;
    await assertFails(commit(db('eve'), all(ops)));
  }
});
