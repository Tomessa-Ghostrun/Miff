// Plan de test, section 14 : ONB-B, numéro de téléphone obligatoire (décision Q1 du PM, 2026-10-24), sur users/{uid}
// et sur sa copie dans miffs/{id}/members/{uid} (ticket docs/tickets/onboarding.md, §6.4, §8, critères B1 à B3).
//   PH1, PH2    : users, création (formes E.164 acceptées, tout le reste refusé, clé obligatoire)
//   PH3         : users, usurpation d'uid
//   PH4         : users, mise à jour (modifiable, non vidable, non supprimable ; compte antérieur sans numéro)
//   PH5         : users, lecture : le numéro n'est lisible que par son propriétaire
//   PH6, PH7    : members, création (copie identique au profil, obligatoire, clés fermées)
//   PH8, PH9    : members, mise à jour (copie identique au profil au moment de l'écriture, lot atomique)
//   PH10        : members, lecture : membres de la Miff seulement (non-membre, ex-membre, membre retiré, Miff supprimée)
//   PH11        : limites assumées (divergence passagère, nouvel arrivant avec le code lit les numéros)
// Même jeu de données qu'à la section 0 du plan (eve et frank n'ont PAS de profil ici, comme dans le fichier 14).
// Chaque cas isole UNE protection (voir tests/tools/mutations-onbb.mjs).
import assert from 'node:assert/strict';
import {
  ID, CODE, PHONE, PHONE_B, commit, createMiffOps, joinOps, leaveOps, removeMemberOps, set, update, del, profile,
  T0, suite, assertFails, assertSucceeds, serverTs, FieldValue, userDoc, userWithConsent, consentOff,
} from '../helpers/fixtures.js';

const s = suite({ extraProfiles: false });
const M1 = `miffs/${ID.M1}`;
const all = (ops) => Object.values(ops);
const omit = (obj, ...keys) => Object.fromEntries(Object.entries(obj).filter(([k]) => !keys.includes(k)));

async function stored(admin, path) {
  let data;
  await admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : undefined;
  });
  return data;
}

async function assertAllFail(attempts) {
  for (const [label, attempt] of attempts) {
    try {
      await assertFails(attempt());
    } catch (error) {
      throw new Error(`« ${label} » aurait dû être refusé : ${error.message}`);
    }
  }
}

// Profil existant avec numéro, avec ou sans autre donnée (état injecté, règles désactivées).
const seedUser = (admin, uid, phone = PHONE) => admin((f) => f.doc(`users/${uid}`).set(userWithConsent(uid, consentOff(), phone)));

// Formes qui doivent être refusées (E.164 strict : « + », un chiffre de 1 à 9, puis 6 à 14 chiffres ; 7 à 15 chiffres au total).
const INVALID_PHONES = [
  ['format national avec zéro initial', '0612345678'],
  ['sans le signe +', '33612345678'],
  ['zéro juste après le +', '+0612345678'],
  ['6 chiffres (trop court)', '+123456'],
  ['16 chiffres (trop long)', '+1234567890123456'],
  ['espaces', '+33 6 12 34 56 78'],
  ['tirets', '+33-6-12-34-56-78'],
  ['parenthèses', '+33(0)612345678'],
  ['point', '+33.612345678'],
  ['lettres', '+33612abc678'],
  ['double plus', '++33612345678'],
  ['plus seul', '+'],
  ['chaîne vide', ''],
  ['espace au début', ' +33612345678'],
  ['espace à la fin', '+33612345678 '],
  ['retour à la ligne à la fin', '+33612345678\n'],
  ['chiffres arabo-indiques', '+٣٣٦١٢٣٤٥٦٧٨'],
  ['très long (200 caractères)', `+${'1'.repeat(199)}`],
  ['nombre au lieu de texte', 33612345678],
  ['booléen', true],
  ['null', null],
  ['liste', [PHONE]],
];

// ---------------------------------------------------------------------------------------------------------
// users : création
// ---------------------------------------------------------------------------------------------------------
s.tc('PH1', 'users, création : numéro E.164 valide (7 à 15 chiffres) accepté et enregistré tel quel', async ({ db, admin }) => {
  await assertSucceeds(db('eve').doc('users/eve').set(userDoc({ phone: PHONE })));
  assert.equal((await stored(admin, 'users/eve')).phone, PHONE);
  for (const [uid, phone] of [['frank', '+1234567'], ['gina', '+123456789012345'], ['hugo', '+242061234567']]) {
    await assertSucceeds(db(uid).doc(`users/${uid}`).set(userDoc({ phone })));
    assert.equal((await stored(admin, `users/${uid}`)).phone, phone);
  }
});

s.tc('PH2', 'users, création : numéro absent, nul, vide ou de forme invalide refusé (téléphone obligatoire)', async ({ db, admin }) => {
  const ref = db('eve').doc('users/eve');
  await assertAllFail([
    ['numéro absent (profil AUTH-B sans téléphone)', () => ref.set(omit(userDoc(), 'phone'))],
    ...INVALID_PHONES.map(([label, value]) => [label, () => ref.set(userDoc({ phone: value }))]),
    ['clé téléphone en trop (phoneCountry)', () => ref.set(userDoc({ phoneCountry: 'FR' }))],
    ['clé téléphone en trop (phoneNumber)', () => ref.set(userDoc({ phoneNumber: PHONE }))],
  ]);
  assert.equal(await stored(admin, 'users/eve'), undefined);
  // Contrôle positif : le même profil avec un numéro valide est accepté.
  await assertSucceeds(ref.set(userDoc({ phone: PHONE })));
});

s.tc('PH3', 'users : usurpation d\'uid (créer, écraser ou modifier le profil et le numéro d\'un autre) refusée', async ({ db, admin }) => {
  await seedUser(admin, 'alice');
  await seedUser(admin, 'bob');
  await assertAllFail([
    ['eve crée le profil de frank avec un numéro', () => db('eve').doc('users/frank').set(userDoc({ phone: PHONE_B }))],
    ['eve écrase le profil de alice', () => db('eve').doc('users/alice').set(userDoc({ phone: PHONE_B }))],
    ['bob change le numéro de alice', () => db('bob').doc('users/alice').update({ phone: PHONE_B })],
    ['visiteur sans connexion crée un profil', () => s.anon().doc('users/frank').set(userDoc({ phone: PHONE_B }))],
  ]);
  assert.equal((await stored(admin, 'users/alice')).phone, PHONE);
});

// ---------------------------------------------------------------------------------------------------------
// users : mise à jour
// ---------------------------------------------------------------------------------------------------------
s.tc('PH4', 'users, mise à jour : numéro modifiable par son propriétaire, jamais vidé ni supprimé ni de forme invalide', async ({ db, admin }) => {
  await seedUser(admin, 'alice');
  const ref = db('alice').doc('users/alice');
  await assertAllFail([
    ...INVALID_PHONES.map(([label, value]) => [`modifier pour : ${label}`, () => ref.update({ phone: value })]),
    ['supprimer la clé téléphone', () => ref.update({ phone: FieldValue.delete() })],
    ['numéro invalide glissé avec un prénom valide', () => ref.update({ firstName: 'Alicia', phone: '0600000000' })],
    ['clé téléphone en trop (phoneCountry)', () => ref.update({ phoneCountry: 'FR' })],
  ]);
  assert.equal((await stored(admin, 'users/alice')).phone, PHONE);
  await assertSucceeds(ref.update({ phone: PHONE_B }));
  assert.equal((await stored(admin, 'users/alice')).phone, PHONE_B);
  await assertSucceeds(ref.update({ firstName: 'Alicia', phone: PHONE }));
  const doc = await stored(admin, 'users/alice');
  assert.equal(doc.phone, PHONE);
  assert.equal(doc.firstName, 'Alicia');
});

s.tc('PH4b', 'users, compte antérieur sans numéro : peut l\'ajouter (étape 2b) et reste modifiable sans lui ; les champs figés le restent', async ({ db, admin }) => {
  await admin((f) => f.doc('users/carol').set(omit(userWithConsent('carol', consentOff()), 'phone')));
  const ref = db('carol').doc('users/carol');
  await assertSucceeds(ref.update({ firstName: 'Caro' })); // pas de numéro exigé pour un simple changement de prénom
  assert.equal('phone' in (await stored(admin, 'users/carol')), false);
  await assertAllFail([
    ['ajouter un numéro invalide', () => ref.update({ phone: '0600000000' })],
    ['ajouter un numéro nul', () => ref.update({ phone: null })],
    ['ajouter le numéro avec une CGU réécrite', () => ref.update({ phone: PHONE, termsVersion: 'v2' })],
  ]);
  await assertSucceeds(ref.update({ phone: PHONE }));
  assert.equal((await stored(admin, 'users/carol')).phone, PHONE);
});

// ---------------------------------------------------------------------------------------------------------
// users : lecture
// ---------------------------------------------------------------------------------------------------------
s.tc('PH5', 'users, lecture : le numéro n\'est lisible que par son propriétaire (ni membre, ni créateur, ni inconnu, ni liste)', async ({ db, admin }) => {
  for (const uid of ['alice', 'bob', 'carol']) await seedUser(admin, uid);
  await seedUser(admin, 'eve');
  const snap = await assertSucceeds(db('bob').doc('users/bob').get());
  assert.equal(snap.data().phone, PHONE);
  await assertAllFail([
    ['carol (même Miff) lit le profil de bob', () => db('carol').doc('users/bob').get()],
    ['alice (créatrice) lit le profil de bob', () => db('alice').doc('users/bob').get()],
    ['eve (sans Miff) lit le profil de bob', () => db('eve').doc('users/bob').get()],
    ['visiteur lit le profil de bob', () => s.anon().doc('users/bob').get()],
    ['liste des profils par bob', () => db('bob').collection('users').get()],
    ['recherche par numéro par bob', () => db('bob').collection('users').where('phone', '==', PHONE).get()],
    ['recherche par numéro (groupe de collections) par bob', () => db('bob').collectionGroup('users').where('phone', '==', PHONE).get()],
  ]);
});

// ---------------------------------------------------------------------------------------------------------
// members : création
// ---------------------------------------------------------------------------------------------------------
const joinWith = (uid, phone, extra = {}) => {
  const ops = joinOps({ uid });
  const data = { ...ops.member.data, phone, ...extra };
  if (phone === undefined) delete data.phone;
  return [set(`${M1}/members/${uid}`, data), ops.index];
};
const createWith = (uid, phone, extra = {}) => {
  const ops = createMiffOps({ uid });
  const data = { ...ops.member.data, phone, ...extra };
  if (phone === undefined) delete data.phone;
  return [ops.miff, ops.code, set(`miffs/${ID.NEW}/members/${uid}`, data), ops.index];
};

s.tc('PH6', 'members, création : la fiche porte le numéro du profil (adhésion et création) ; absent, invalide ou différent du profil : refusé', async ({ db, admin }) => {
  await seedUser(admin, 'eve');
  await seedUser(admin, 'frank', PHONE_B);
  await assertAllFail([
    ['adhésion sans numéro', () => commit(db('eve'), joinWith('eve', undefined))],
    ...INVALID_PHONES.map(([label, value]) => [`adhésion avec numéro : ${label}`, () => commit(db('eve'), joinWith('eve', value))]),
    ['adhésion avec un numéro valide mais différent du profil', () => commit(db('eve'), joinWith('eve', PHONE_B))],
    ['création sans numéro', () => commit(db('eve'), createWith('eve', undefined))],
    ['création avec un numéro différent du profil', () => commit(db('eve'), createWith('eve', PHONE_B))],
    ['création avec un numéro invalide', () => commit(db('eve'), createWith('eve', '0612345678'))],
  ]);
  assert.equal(await stored(admin, `${M1}/members/eve`), undefined);
  assert.equal(await stored(admin, `miffs/${ID.NEW}`), undefined);
  // Contrôles positifs : copie identique au profil.
  await assertSucceeds(commit(db('eve'), joinWith('eve', PHONE)));
  assert.equal((await stored(admin, `${M1}/members/eve`)).phone, PHONE);
  await assertSucceeds(commit(db('frank'), createWith('frank', PHONE_B)));
  assert.equal((await stored(admin, `miffs/${ID.NEW}/members/frank`)).phone, PHONE_B);
});

s.tc('PH6b', 'members, création : profil créé dans le même lot (getAfter) : copie identique acceptée, copie différente refusée ; profil sans numéro : refusé', async ({ db, admin }) => {
  await assertAllFail([
    ['lot profil + adhésion, copie différente du profil du lot', () => commit(db('eve'), [set('users/eve', userDoc({ phone: PHONE })), ...joinWith('eve', PHONE_B)])],
    ['lot profil + création, copie différente', () => commit(db('eve'), [set('users/eve', userDoc({ phone: PHONE })), ...createWith('eve', PHONE_B)])],
    ['lot profil sans numéro + adhésion', () => commit(db('eve'), [set('users/eve', omit(userDoc(), 'phone')), ...joinWith('eve', PHONE)])],
  ]);
  assert.equal(await stored(admin, 'users/eve'), undefined);
  await assertSucceeds(commit(db('eve'), [set('users/eve', userDoc({ phone: PHONE_B })), ...joinWith('eve', PHONE_B)]));
  await assertSucceeds(commit(db('frank'), [set('users/frank', userDoc({ phone: PHONE })), ...createWith('frank', PHONE)]));
  // Compte antérieur sans numéro : ne peut pas rejoindre tant qu'il n'a pas ajouté son numéro à son profil.
  await admin((f) => f.doc('users/gina').set(omit(userWithConsent('gina', consentOff()), 'phone')));
  await assertFails(commit(db('gina'), joinWith('gina', PHONE)));
  await assertSucceeds(commit(db('gina'), [update('users/gina', { phone: PHONE }), ...joinWith('gina', PHONE)]));
});

s.tc('PH7', 'members, création : clés fermées (aucune clé de contact en trop, aucun e-mail, aucune position)', async ({ db, admin }) => {
  await seedUser(admin, 'eve');
  await assertAllFail([
    ['phoneCountry en trop', () => commit(db('eve'), joinWith('eve', PHONE, { phoneCountry: 'FR' }))],
    ['email en trop', () => commit(db('eve'), joinWith('eve', PHONE, { email: 'eve@example.org' }))],
    ['position en trop', () => commit(db('eve'), joinWith('eve', PHONE, { lat: 48.85 }))],
  ]);
  await assertSucceeds(commit(db('eve'), joinWith('eve', PHONE)));
});

// ---------------------------------------------------------------------------------------------------------
// members : mise à jour
// ---------------------------------------------------------------------------------------------------------
s.tc('PH8', 'members, mise à jour : le numéro de la fiche doit être celui du profil au moment de l\'écriture ; valide ; jamais vidé', async ({ db, admin }) => {
  await seedUser(admin, 'bob');
  await admin((f) => f.doc(`${M1}/members/bob`).update({ phone: PHONE }));
  const ref = db('bob').doc(`${M1}/members/bob`);
  await assertAllFail([
    ['copie différente du profil (profil inchangé)', () => ref.update({ phone: PHONE_B })],
    ...INVALID_PHONES.map(([label, value]) => [`numéro : ${label}`, () => ref.update({ phone: value })]),
    ['supprimer la clé téléphone de la fiche', () => ref.update({ phone: FieldValue.delete() })],
    ['numéro valide mais avec un rôle changé', () => ref.update({ phone: PHONE, role: 'owner' })],
    ['clé téléphone en trop', () => ref.update({ phoneCountry: 'FR' })],
    ['un autre membre (carol) change le numéro de la fiche de bob', () => db('carol').doc(`${M1}/members/bob`).update({ phone: PHONE })],
    ['la créatrice (alice) change le numéro de la fiche de bob', () => db('alice').doc(`${M1}/members/bob`).update({ phone: PHONE })],
  ]);
  assert.equal((await stored(admin, `${M1}/members/bob`)).phone, PHONE);
  // Le profil change d'abord (lot précédent) : la fiche peut alors suivre.
  await assertSucceeds(db('bob').doc('users/bob').update({ phone: PHONE_B }));
  await assertSucceeds(ref.update({ phone: PHONE_B }));
  assert.equal((await stored(admin, `${M1}/members/bob`)).phone, PHONE_B);
  // Un simple changement de prénom ne touche pas au numéro et n'exige rien de plus.
  await assertSucceeds(ref.update({ firstName: 'Bobby' }));
});

s.tc('PH8b', 'members, fiche antérieure sans numéro : restée valide (prénom modifiable) ; ajout du numéro accepté seulement s\'il est celui du profil', async ({ db, admin }) => {
  await seedUser(admin, 'carol');
  const ref = db('carol').doc(`${M1}/members/carol`);
  assert.equal('phone' in (await stored(admin, `${M1}/members/carol`)), true); // fixtures : la fiche de départ porte déjà le numéro
  await admin((f) => f.doc(`${M1}/members/carol`).update({ phone: FieldValue.delete() }));
  await assertSucceeds(ref.update({ firstName: 'Caro' }));
  await assertAllFail([
    ['ajouter un numéro différent du profil', () => ref.update({ phone: PHONE_B })],
    ['ajouter un numéro invalide', () => ref.update({ phone: '0612345678' })],
  ]);
  await assertSucceeds(ref.update({ phone: PHONE }));
  assert.equal((await stored(admin, `${M1}/members/carol`)).phone, PHONE);
});

s.tc('PH9', 'mise à jour atomique : profil + fiche dans un même lot réussit ; si une fiche est refusée, rien n\'est écrit (profil compris)', async ({ db, admin }) => {
  await seedUser(admin, 'bob');
  const lot = (memberPhone) => [update('users/bob', { phone: PHONE_B }), update(`${M1}/members/bob`, { phone: memberPhone })];
  await assertAllFail([
    ['fiche avec un autre numéro que le profil du lot', () => commit(db('bob'), lot('+33699999999'))],
    ['fiche avec un numéro invalide', () => commit(db('bob'), lot('0699999999'))],
    ['profil invalide, fiche valide', () => commit(db('bob'), [update('users/bob', { phone: '0699999999' }), update(`${M1}/members/bob`, { phone: PHONE_B })])],
  ]);
  assert.equal((await stored(admin, 'users/bob')).phone, PHONE);
  assert.equal((await stored(admin, `${M1}/members/bob`)).phone, PHONE);
  await assertSucceeds(commit(db('bob'), lot(PHONE_B)));
  assert.equal((await stored(admin, 'users/bob')).phone, PHONE_B);
  assert.equal((await stored(admin, `${M1}/members/bob`)).phone, PHONE_B);
});

// ---------------------------------------------------------------------------------------------------------
// members : lecture
// ---------------------------------------------------------------------------------------------------------
s.tc('PH10', 'members, lecture du numéro : tous les membres de la Miff (partage ou non), jamais un non-membre, un ex-membre ni une Miff supprimée', async ({ db, admin }) => {
  await admin((f) => f.doc(`${M1}/members/alice`).update({ phone: '+33600000001' }));
  const aliceFiche = `${M1}/members/alice`;
  // Membres : bob (partage actif) et carol (partage désactivé) lisent le numéro de alice (décision N1, option A).
  assert.equal((await assertSucceeds(db('bob').doc(aliceFiche).get())).data().phone, '+33600000001');
  assert.equal((await assertSucceeds(db('carol').doc(aliceFiche).get())).data().phone, '+33600000001');
  const list = await assertSucceeds(db('carol').collection(`${M1}/members`).get());
  assert.ok(list.docs.every((d) => typeof d.data().phone === 'string'));
  await assertAllFail([
    ['eve (sans Miff) lit la fiche', () => db('eve').doc(aliceFiche).get()],
    ['eve liste les fiches', () => db('eve').collection(`${M1}/members`).get()],
    ['zoe (membre d\'une autre Miff) lit la fiche', () => db('zoe').doc(aliceFiche).get()],
    ['visiteur sans connexion lit la fiche', () => s.anon().doc(aliceFiche).get()],
    ['groupe de collections members par bob', () => db('bob').collectionGroup('members').get()],
    ['recherche d\'un numéro dans toutes les Miffs par bob', () => db('bob').collectionGroup('members').where('phone', '==', '+33600000001').get()],
  ]);
});

s.tc('PH10b', 'members, lecture : un membre qui quitte, un membre retiré ou un survivant d\'une Miff supprimée ne lit plus aucun numéro', async ({ db, admin }) => {
  const aliceFiche = `${M1}/members/alice`;
  // bob quitte : plus de lecture.
  await assertSucceeds(db('bob').doc(aliceFiche).get());
  await assertSucceeds(commit(db('bob'), all(leaveOps('bob'))));
  await assertFails(db('bob').doc(aliceFiche).get());
  await assertFails(db('bob').collection(`${M1}/members`).get());
  // carol est retirée par alice (code renouvelé) : plus de lecture.
  await assertSucceeds(db('carol').doc(aliceFiche).get());
  await assertSucceeds(commit(db('alice'), all(removeMemberOps({ target: 'carol' }))));
  await assertFails(db('carol').doc(aliceFiche).get());
  await assertFails(db('carol').collection(`${M1}/members`).get());
  // Les numéros de bob et carol disparaissent avec leurs fiches.
  assert.equal(await stored(admin, `${M1}/members/bob`), undefined);
  assert.equal(await stored(admin, `${M1}/members/carol`), undefined);
});

s.tc('PH10c', 'members, lecture : Miff supprimée => les fiches (et les numéros) restées en base sont illisibles même pour un survivant', async ({ db, admin }) => {
  await admin(async (f) => {
    const b = f.batch();
    b.delete(f.doc(M1));
    b.delete(f.doc(`joinCodes/${CODE.M1}`));
    await b.commit();
  });
  assert.equal((await stored(admin, `${M1}/members/alice`)).phone, PHONE); // la donnée existe encore physiquement
  await assertAllFail([
    ['survivant (bob) lit la fiche de alice', () => db('bob').doc(`${M1}/members/alice`).get()],
    ['survivant (bob) liste les fiches', () => db('bob').collection(`${M1}/members`).get()],
  ]);
});

// ---------------------------------------------------------------------------------------------------------
// Limites assumées (documentées dans data-model.md §2.6 et §11) : ces tests figent le comportement connu.
// ---------------------------------------------------------------------------------------------------------
s.tc('PH11', 'limites assumées : (a) divergence passagère profil/fiche acceptée ; (b) un nouvel arrivant avec le code lit les numéros ; (c) aucune unicité', async ({ db, admin }) => {
  // (a) Le profil change seul : les fiches gardent l'ancien numéro jusqu'à ce que le client les mette à jour (nouvelle tentative).
  await seedUser(admin, 'bob');
  await assertSucceeds(db('bob').doc('users/bob').update({ phone: PHONE_B }));
  assert.equal((await stored(admin, `${M1}/members/bob`)).phone, PHONE);
  await assertSucceeds(db('bob').doc(`${M1}/members/bob`).update({ phone: PHONE_B }));
  // (b) Risque R7 du ticket : quiconque connaît le code entre sans approbation et lit tous les numéros.
  await seedUser(admin, 'eve');
  await assertFails(db('eve').doc(`${M1}/members/alice`).get());
  await assertSucceeds(commit(db('eve'), joinWith('eve', PHONE)));
  const snap = await assertSucceeds(db('eve').collection(`${M1}/members`).get());
  assert.equal(snap.size, 4);
  assert.ok(snap.docs.every((d) => /^[+][1-9][0-9]{6,14}$/.test(d.data().phone)));
  // (c) Deux comptes peuvent porter le même numéro (aucune unicité, aucune vérification).
  await seedUser(admin, 'frank', PHONE);
  assert.equal((await stored(admin, 'users/frank')).phone, (await stored(admin, 'users/eve')).phone);
  await assertSucceeds(db('frank').doc('users/frank').update({ phone: PHONE }));
});
