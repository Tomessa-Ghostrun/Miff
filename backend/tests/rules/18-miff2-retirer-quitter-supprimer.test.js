// Plan de test, section 17 : MIFF-2, renouveler le code, retirer un proche, quitter, supprimer la Miff (ticket
// docs/tickets/miff.md, §5.3, §5.6, critères N1 à N3, R1 à R7, Q1 à Q4, S1 à S8).
//   MN1-MN3 : renouveler le code (lot de 3) ; MR1-MR8 : retirer un proche (lot de 6, code renouvelé)
//   MQ1-MQ4 : quitter (lot de 4)           ; MS1-MS7 : supprimer la Miff (retraits, lieux, lot final de 7), reprise, idempotence
// Les lots sont ceux que le client Android écrit (MiffWrites.kt) : mêmes chemins, mêmes clés, même ordre. Aucune règle n'est
// modifiée : ces cas figent le contrat dont le client dépend. Les lectures de règles de ces lots sont mesurées dans 09-rule-reads.
import assert from 'node:assert/strict';
import {
  ID, CODE, commit, createShareOps, joinShareOps, leaveOps, removeMemberOps, set, update, del, photoDoc, placeSeed,
  suite, assertFails, assertSucceeds, serverTs, T0,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;

async function stored(admin, path) {
  let data;
  await admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : undefined;
  });
  return data;
}

const seedPhoto = (admin, uid) => admin((f) => f.doc(`${M1}/photos/${uid}`).set(photoDoc({ createdAt: T0 })));

/** Renouvellement : la Miff pointe vers le nouveau code, le nouveau code existe, l'ancien disparaît (lot de 3 du client). */
const renewOps = ({ oldCode = CODE.M1, newCode = CODE.REGEN, name = 'Famille' } = {}) => [
  update(M1, { joinCode: newCode }),
  set(`joinCodes/${newCode}`, { miffId: ID.M1, miffName: name, createdAt: serverTs() }),
  del(`joinCodes/${oldCode}`),
];

/** Dernier lot de la suppression : Miff, pierre tombale, code COURANT, statut, photo, fiche et index du créateur (7 écritures). */
const finalOps = ({ uid = 'alice', code = CODE.M1, key = 'free', withIndex = true, withCode = true } = {}) => [
  del(M1),
  set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
  ...(withCode ? [del(`joinCodes/${code}`)] : []),
  del(`${M1}/status/${uid}`),
  del(`${M1}/photos/${uid}`),
  del(`${M1}/members/${uid}`),
  ...(withIndex ? [del(`users/${uid}/memberships/${key}`)] : []),
];

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
// MN : renouveler le code
// ---------------------------------------------------------------------------------------------------------
s.tc('MN1', 'la créatrice renouvelle le code : lot de 3 accepté ; l\'ancien code ne fonctionne plus, le nouveau fonctionne ; les membres restent', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), renewOps()));
  assert.equal((await stored(admin, M1)).joinCode, CODE.REGEN);
  assert.equal(await stored(admin, `joinCodes/${CODE.M1}`), undefined);
  assert.equal((await stored(admin, `joinCodes/${CODE.REGEN}`)).miffId, ID.M1);
  // Les membres actuels restent et lisent la Miff avec son nouveau code.
  const seen = await assertSucceeds(db('bob').doc(M1).get());
  assert.equal(seen.data().joinCode, CODE.REGEN);
  assert.ok(await stored(admin, `${M1}/members/bob`));
  // Un second compte : l'ancien code est refusé (message unique côté client), le nouveau fonctionne.
  await assertFails(commit(db('eve'), Object.values(joinShareOps({ uid: 'eve', code: CODE.M1 }))));
  await assertSucceeds(commit(db('eve'), Object.values(joinShareOps({ uid: 'eve', code: CODE.REGEN }))));
});

s.tc('MN2', 'renouvellement : un membre simple, un lot incomplet ou un nouveau code incohérent sont refusés, et rien n\'est écrit', async ({ db, admin }) => {
  const [update1, newDoc, oldDel] = renewOps();
  await assertAllFail([
    ['un membre simple (bob) renouvelle', () => commit(db('bob'), renewOps())],
    ['sans création du nouveau code', () => commit(db('alice'), [update1, oldDel])],
    ['sans suppression de l\'ancien code', () => commit(db('alice'), [update1, newDoc])],
    ['nouveau code qui pointe vers une autre Miff', () => commit(db('alice'), [update1, set(`joinCodes/${CODE.REGEN}`, { miffId: ID.M2, miffName: 'Famille', createdAt: serverTs() }), oldDel])],
    ['nom du nouveau code différent de celui de la Miff', () => commit(db('alice'), renewOps({ name: 'Autre nom' }))],
    ['nouveau code mal formé', () => commit(db('alice'), renewOps({ newCode: 'abc' }))],
    ['code du client d\'un autre créateur (zoe renouvelle M1)', () => commit(db('zoe'), renewOps())],
  ]);
  assert.equal((await stored(admin, M1)).joinCode, CODE.M1);
  assert.ok(await stored(admin, `joinCodes/${CODE.M1}`));
  assert.equal(await stored(admin, `joinCodes/${CODE.REGEN}`), undefined);
  await assertSucceeds(commit(db('alice'), renewOps()));
});

s.tc('MN3', 'renouvellements successifs : deux lots de suite sont acceptés (le client relit le code courant dans la transaction)', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), renewOps()));
  await assertSucceeds(commit(db('alice'), renewOps({ oldCode: CODE.REGEN, newCode: CODE.NEW })));
  assert.equal((await stored(admin, M1)).joinCode, CODE.NEW);
  assert.equal(await stored(admin, `joinCodes/${CODE.REGEN}`), undefined);
  // Un lot calculé sur une copie périmée du code (l'ancien code n'existe plus) est refusé : c'est pourquoi le client lit dans la transaction.
  await assertFails(commit(db('alice'), renewOps({ oldCode: CODE.M1, newCode: CODE.NEW2 })));
});

// ---------------------------------------------------------------------------------------------------------
// MR : retirer un proche
// ---------------------------------------------------------------------------------------------------------
s.tc('MR1', 'retirer bob (avec statut et photo) : lot de 6 accepté ; fiche, statut, photo supprimés, code renouvelé, plus de lecture, ancien code mort', async ({ db, admin }) => {
  await seedPhoto(admin, 'bob');
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'bob' }))));
  for (const path of [`${M1}/members/bob`, `${M1}/status/bob`, `${M1}/photos/bob`, `joinCodes/${CODE.M1}`]) {
    assert.equal(await stored(admin, path), undefined, `${path} devrait être supprimé`);
  }
  assert.equal((await stored(admin, M1)).joinCode, CODE.REGEN);
  assert.equal((await stored(admin, `joinCodes/${CODE.REGEN}`)).miffId, ID.M1);
  await assertFails(db('bob').doc(M1).get());
  await assertFails(db('bob').collection(`${M1}/members`).get());
  // Il ne revient pas avec le code qu'il connaît.
  await assertFails(commit(db('bob'), Object.values(joinShareOps({ uid: 'bob', code: CODE.M1 }))));
});

s.tc('MR2', 'cible déjà partie : le lot est accepté (suppressions de documents absents sans effet) et le code est renouvelé quand même', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'bob' }))));
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'bob', oldCode: CODE.REGEN, newCode: CODE.NEW }))));
  assert.equal((await stored(admin, M1)).joinCode, CODE.NEW);
  assert.equal(await stored(admin, `joinCodes/${CODE.REGEN}`), undefined);
});

s.tc('MR3', 'retirer un membre sans statut ni photo (partage arrêté), puis le dernier proche : acceptés ; la créatrice reste seule', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'carol' }))));
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'bob', oldCode: CODE.REGEN, newCode: CODE.NEW }))));
  const left = [];
  await admin(async (f) => {
    (await f.collection(`${M1}/members`).get()).forEach((d) => left.push(d.id));
  });
  assert.deepEqual(left, ['alice']);
});

s.tc('MR4', 'retrait : un lot qui ne renouvelle pas le code, ou le renouvelle de travers, est refusé et rien n\'est écrit', async ({ db, admin }) => {
  const ops = removeMemberOps({ target: 'bob' });
  const without = (...keys) => Object.entries(ops).filter(([k]) => !keys.includes(k)).map(([, v]) => v);
  await assertAllFail([
    ['sans aucun changement de code (R4)', () => commit(db('alice'), without('miff', 'newCode', 'oldCode'))],
    ['code changé mais ancien code conservé', () => commit(db('alice'), without('oldCode'))],
    ['code changé mais nouveau code absent', () => commit(db('alice'), without('newCode'))],
    ['nouveau code qui pointe vers une autre Miff', () => commit(db('alice'), [...without('newCode'), set(`joinCodes/${CODE.REGEN}`, { miffId: ID.M2, miffName: 'Famille', createdAt: serverTs() })])],
    ['statut de la cible oublié (trace de position qui survit)', () => commit(db('alice'), without('status'))],
    ['photo de la cible oubliée alors qu\'elle existe', async () => {
      await seedPhoto(admin, 'bob');
      return commit(db('alice'), without('photo'));
    }],
  ]);
  assert.ok(await stored(admin, `${M1}/members/bob`), 'bob est toujours là');
  assert.equal((await stored(admin, M1)).joinCode, CODE.M1);
  await assertSucceeds(commit(db('alice'), Object.values(ops)));
});

s.tc('MR5', 'retirer : un membre simple ne retire personne ; la créatrice ne peut pas se retirer elle-même (pas de départ sans suppression)', async ({ db, admin }) => {
  await assertAllFail([
    ['bob retire carol', () => commit(db('bob'), Object.values(removeMemberOps({ target: 'carol' })))],
    ['zoe (autre Miff) retire carol', () => commit(db('zoe'), Object.values(removeMemberOps({ target: 'carol' })))],
    ['alice se retire elle-même avec code renouvelé', () => commit(db('alice'), Object.values(removeMemberOps({ target: 'alice' })))],
  ]);
  assert.ok(await stored(admin, `${M1}/members/alice`));
  assert.ok(await stored(admin, `${M1}/members/carol`));
});

s.tc('MR6', 'après un retrait, l\'index de la personne retirée reste : elle le libère elle-même (la créatrice ne peut pas), puis peut rejoindre une autre Miff', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'bob' }))));
  await assertFails(db('alice').doc('users/bob/memberships/free').delete());
  assert.ok(await stored(admin, 'users/bob/memberships/free'));
  // Rejoindre une autre Miff avant le nettoyage : refusé (un seul index gratuit, il est encore là)...
  await assertFails(commit(db('bob'), Object.values(joinShareOps({ uid: 'bob', miffId: ID.M2, code: CODE.M2 }))));
  // ... le nettoyage de l'index est accepté (elle n'est plus membre), puis l'entrée l'est aussi.
  await assertSucceeds(db('bob').doc('users/bob/memberships/free').delete());
  await assertSucceeds(commit(db('bob'), Object.values(joinShareOps({ uid: 'bob', miffId: ID.M2, code: CODE.M2 }))));
});

s.tc('MR7', 'retrait : le code courant a déjà disparu (document absent) : supprimer l\'ancien code est refusé, le lot sans cette suppression est accepté', async ({ db, admin }) => {
  await admin((f) => f.doc(`joinCodes/${CODE.M1}`).delete());
  const ops = removeMemberOps({ target: 'bob' });
  await assertFails(commit(db('alice'), Object.values(ops)));
  const { oldCode, ...rest } = ops;
  await assertSucceeds(commit(db('alice'), Object.values(rest)));
  assert.equal((await stored(admin, M1)).joinCode, CODE.REGEN);
});

// ---------------------------------------------------------------------------------------------------------
// MQ : quitter
// ---------------------------------------------------------------------------------------------------------
s.tc('MQ1', 'bob quitte (statut et photo présents) : lot de 4 accepté, index libéré ; il peut ensuite rejoindre une autre Miff', async ({ db, admin }) => {
  await seedPhoto(admin, 'bob');
  await assertSucceeds(commit(db('bob'), Object.values(leaveOps('bob'))));
  for (const path of [`${M1}/members/bob`, `${M1}/status/bob`, `${M1}/photos/bob`, 'users/bob/memberships/free']) {
    assert.equal(await stored(admin, path), undefined, `${path} devrait être supprimé`);
  }
  await assertFails(db('bob').doc(M1).get());
  // Le code de la Miff n'est pas renouvelé par un départ volontaire (le créateur est invité à le faire, c'est facultatif).
  assert.equal((await stored(admin, M1)).joinCode, CODE.M1);
  await assertSucceeds(commit(db('bob'), Object.values(joinShareOps({ uid: 'bob', miffId: ID.M2, code: CODE.M2 }))));
});

s.tc('MQ2', 'carol quitte alors que son partage est arrêté (ni statut ni photo) : lot de 4 accepté', async ({ db, admin }) => {
  await assertSucceeds(commit(db('carol'), Object.values(leaveOps('carol'))));
  assert.equal(await stored(admin, `${M1}/members/carol`), undefined);
  assert.equal(await stored(admin, 'users/carol/memberships/free'), undefined);
});

s.tc('MQ3', 'supprimer un index qui n\'existe pas est refusé : le client omet l\'index quand il ne l\'a pas trouvé ; le lot de 3 reste accepté', async ({ db, admin }) => {
  await assertFails(db('eve').doc('users/eve/memberships/free').delete());
  await admin((f) => f.doc('users/bob/memberships/free').delete());
  const { index, ...threeOnly } = leaveOps('bob');
  await assertFails(commit(db('bob'), Object.values(leaveOps('bob'))));
  await assertSucceeds(commit(db('bob'), Object.values(threeOnly)));
  assert.equal(await stored(admin, `${M1}/members/bob`), undefined);
});

s.tc('MQ4', 'la créatrice ne peut pas quitter sans supprimer la Miff (QM1) : le lot de 4 est refusé et rien n\'est écrit ; un lot sans statut oublie la trace : refusé aussi', async ({ db, admin }) => {
  await assertFails(commit(db('alice'), Object.values(leaveOps('alice'))));
  const { status, ...noStatus } = leaveOps('bob');
  await assertFails(commit(db('bob'), Object.values(noStatus)));
  assert.ok(await stored(admin, `${M1}/members/alice`));
  assert.ok(await stored(admin, `${M1}/members/bob`));
  assert.ok(await stored(admin, `${M1}/status/bob`));
});

// ---------------------------------------------------------------------------------------------------------
// MS : supprimer la Miff
// ---------------------------------------------------------------------------------------------------------
s.tc('MS1', 'suppression complète dans l\'ordre du client : retrait de bob, retrait de carol (code renouvelé à chaque fois), lieux, lot final de 7 ; accepté', async ({ db, admin }) => {
  await seedPhoto(admin, 'alice');
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'bob' }))));
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'carol', oldCode: CODE.REGEN, newCode: CODE.NEW }))));
  await assertSucceeds(commit(db('alice'), [del(`${M1}/places/p1`)]));
  await assertSucceeds(commit(db('alice'), finalOps({ code: CODE.NEW })));
  for (const path of [M1, `${M1}/members/alice`, `${M1}/status/alice`, `${M1}/photos/alice`, `joinCodes/${CODE.NEW}`, 'users/alice/memberships/free']) {
    assert.equal(await stored(admin, path), undefined, `${path} devrait être supprimé`);
  }
  assert.ok((await stored(admin, `miffsDeleted/${ID.M1}`)).deletedAt, 'la pierre tombale existe');
  // La Miff n'est plus lisible de personne, et son identifiant ne peut plus servir (pierre tombale).
  await assertFails(db('alice').doc(M1).get());
  await assertFails(commit(db('alice'), Object.values(createShareOps({ uid: 'alice', miffId: ID.M1, code: CODE.NEW2 }))));
  // La créatrice est libre de créer une autre Miff gratuite : son index a été libéré dans le même lot.
  await assertSucceeds(commit(db('alice'), Object.values(createShareOps({ uid: 'alice', miffId: ID.NEW, code: CODE.NEW2 }))));
});

s.tc('MS2', 'reprise : tout état intermédiaire se termine ; relancer des étapes déjà faites (lieu déjà supprimé, membre déjà parti) est accepté', async ({ db, admin }) => {
  // Interrompu après un retrait : on relit qui reste, on retire carol, on supprime les lieux, on termine.
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'bob' }))));
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'carol', oldCode: CODE.REGEN, newCode: CODE.NEW }))));
  // Interrompu après les lieux : la suppression d'un lieu déjà supprimé est sans effet (la reprise peut tout refaire).
  await assertSucceeds(commit(db('alice'), [del(`${M1}/places/p1`)]));
  await assertSucceeds(commit(db('alice'), [del(`${M1}/places/p1`)]));
  // Un retrait refait pour un membre déjà parti est accepté aussi (le code est renouvelé de nouveau, le client relit le code courant).
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'carol', oldCode: CODE.NEW, newCode: CODE.NEW2 }))));
  await assertSucceeds(commit(db('alice'), finalOps({ code: CODE.NEW2 })));
  assert.equal(await stored(admin, M1), undefined);
});

s.tc('MS2b', 'lot final dont la réponse a été perdue : la Miff n\'est plus lisible (refus), l\'index a disparu avec le lot ; sans index trouvé, il se supprime ensuite seul', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), [...Object.values(removeMemberOps({ target: 'bob' })), ]));
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'carol', oldCode: CODE.REGEN, newCode: CODE.NEW }))));
  // Le client n'a pas trouvé l'index (lecture qui a échoué) : lot final de 6, l'index reste.
  await assertSucceeds(commit(db('alice'), finalOps({ code: CODE.NEW, withIndex: false })));
  await assertFails(db('alice').doc(M1).get());
  assert.ok(await stored(admin, 'users/alice/memberships/free'));
  // La reprise le constate (Miff illisible, serveur joignable) et libère l'index : accepté (elle n'est plus membre).
  await assertSucceeds(db('alice').doc('users/alice/memberships/free').delete());
});

s.tc('MS3', 'lot final : sans pierre tombale, sans suppression du code, ou avec un retrait de membre dedans, il est refusé et rien n\'est supprimé', async ({ db, admin }) => {
  const ops = finalOps();
  const bobRemoval = [del(`${M1}/members/bob`), del(`${M1}/status/bob`)];
  await assertAllFail([
    ['sans pierre tombale', () => commit(db('alice'), ops.filter((o) => !o.path.startsWith('miffsDeleted/')))],
    ['sans suppression du code', () => commit(db('alice'), finalOps({ withCode: false }))],
    ['avec le retrait de bob dans le lot qui supprime la Miff', () => commit(db('alice'), [...ops, ...bobRemoval])],
    ['un membre simple supprime la Miff', () => commit(db('bob'), finalOps({ uid: 'bob' }))],
    ['avec un code qui n\'est pas le courant', () => commit(db('alice'), finalOps({ code: CODE.M2 }))],
  ]);
  assert.ok(await stored(admin, M1));
  assert.ok(await stored(admin, `joinCodes/${CODE.M1}`));
  assert.equal(await stored(admin, `miffsDeleted/${ID.M1}`), undefined);
});

s.tc('MS3b', 'limite constatée : le lot final est accepté même s\'il reste des fiches de membres (la procédure du client retire d\'abord tout le monde) ; les survivants ne lisent plus rien', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), finalOps()));
  assert.ok(await stored(admin, `${M1}/members/bob`), 'la fiche de bob existe encore physiquement');
  // Un survivant (fiche encore là) lit « la Miff n'existe pas » (lecture permise, document absent) : le client y voit un accès perdu.
  assert.equal((await assertSucceeds(db('bob').doc(M1).get())).exists, false);
  await assertFails(db('bob').collection(`${M1}/members`).get());
  await assertFails(db('bob').doc(`${M1}/members/alice`).get());
  await assertFails(db('bob').collection(`${M1}/status`).get());
});

s.tc('MS4', 'lot final sans statut ni photo de la créatrice (partage arrêté) : accepté', async ({ db, admin }) => {
  await admin(async (f) => {
    await f.doc(`${M1}/status/alice`).delete();
  });
  await assertSucceeds(commit(db('alice'), finalOps()));
  assert.equal(await stored(admin, M1), undefined);
});

s.tc('MS5', 'lieux : 5 suppressions dans un lot accepté pour la créatrice, refusé pour un membre ; supprimer un lieu absent est accepté', async ({ db, admin }) => {
  await admin(async (f) => {
    for (const id of ['p2', 'p3', 'p4', 'p5']) await f.doc(`${M1}/places/${id}`).set(placeSeed({ createdBy: 'alice' }));
  });
  const five = ['p1', 'p2', 'p3', 'p4', 'p5'].map((id) => del(`${M1}/places/${id}`));
  await assertFails(commit(db('bob'), five));
  assert.ok(await stored(admin, `${M1}/places/p5`));
  await assertSucceeds(commit(db('alice'), five));
  assert.equal(await stored(admin, `${M1}/places/p3`), undefined);
  await assertSucceeds(commit(db('alice'), [del(`${M1}/places/p1`)]));
});

s.tc('MS6', 'code courant déjà absent : le lot final avec sa suppression est refusé, sans elle il est accepté (le client lit l\'existence du code dans la transaction)', async ({ db, admin }) => {
  await admin((f) => f.doc(`joinCodes/${CODE.M1}`).delete());
  await assertFails(commit(db('alice'), finalOps()));
  assert.ok(await stored(admin, M1));
  await assertSucceeds(commit(db('alice'), finalOps({ withCode: false })));
  assert.equal(await stored(admin, M1), undefined);
});

s.tc('MS7', 'après la suppression, le code ne rouvre rien : un nouvel arrivant avec le dernier code connu est refusé (introuvable) ; les survivants ne peuvent ni relire ni se rattacher', async ({ db, admin }) => {
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'bob' }))));
  await assertSucceeds(commit(db('alice'), Object.values(removeMemberOps({ target: 'carol', oldCode: CODE.REGEN, newCode: CODE.NEW }))));
  await assertSucceeds(commit(db('alice'), finalOps({ code: CODE.NEW })));
  await assertFails(commit(db('eve'), Object.values(joinShareOps({ uid: 'eve', code: CODE.NEW }))));
  await assertFails(commit(db('eve'), Object.values(joinShareOps({ uid: 'eve', code: CODE.REGEN }))));
  await assertFails(commit(db('eve'), Object.values(joinShareOps({ uid: 'eve', code: CODE.M1 }))));
  assert.equal(await stored(admin, `${M1}/members/eve`), undefined);
});
