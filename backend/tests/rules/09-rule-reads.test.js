// C17 et critère T5 / R15 : nombre de lectures de règles (get/exists/getAfter/existsAfter) par opération.
// Limites Firestore documentées : 10 par requête simple, 20 par lot. Voir tests/helpers/pads.js et measure.js pour la méthode,
// et tests/helpers/calibration.js pour les limites réellement appliquées par l'émulateur (liste : 20 au lieu de 10).
// Résultat écrit dans backend/test-output/rule-reads.md (et .json).
// Depuis DUR-3 (R15) : une mesure non concluante, discordante ou à marge inférieure à 2 lectures FAIT ÉCHOUER le test.
import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import {
  consentOnNow, consentRevokeNow, consentOnSeed, userWithConsent, userDoc,
  ID, CODE, commit, createMiffOps, joinOps, leaveOps, removeMemberOps, set, update, del, statusDoc, placeInput, photoDoc,
  serverTs, sharingOnNow, T0, createShareOps, joinShareOps, PHONE_B,
} from '../helpers/fixtures.js';
import { measureReads, measureLot, writeReadsReport } from '../helpers/measure.js';
import { probeEmulatorLimits, EXPECTED_LIMITS } from '../helpers/calibration.js';
import { H } from '../helpers/hosts.js';

const M1 = `miffs/${ID.M1}`;

// Marge minimale exigée entre les lectures utilisées et la limite documentée (critère R11 du ticket de durcissement).
const MIN_MARGIN = 2;

const db = (env, uid) => env.authenticatedContext(uid).firestore();
const all = (ops) => Object.values(ops);
const seedDaveMiff = (env) =>
  env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    const b = f.batch();
    b.set(f.doc(`miffs/${ID.M3}`), { ownerUid: 'dave', name: 'Premiere', joinCode: CODE.M3, createdAt: T0 });
    b.set(f.doc(`joinCodes/${CODE.M3}`), { miffId: ID.M3, miffName: 'Premiere', createdAt: T0 });
    b.set(f.doc(`miffs/${ID.M3}/members/dave`), {
      role: 'owner', firstName: 'dave', lastName: 'Test', joinedAt: T0,
      sharing: { enabled: true, consentedAt: T0, consentVersion: 'v1', revokedAt: null },
    });
    b.set(f.doc('users/dave/memberships/free'), { miffId: ID.M3, joinedAt: T0 });
    await b.commit();
  });

const seedBobPhoto = (env) =>
  env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/photos/bob`).set(photoDoc({ createdAt: T0 })));
const seedAlicePhoto = (env) =>
  env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/photos/alice`).set(photoDoc({ createdAt: T0 })));

// `lot: true` : plusieurs écritures dans un lot. `hosts` = UNE règle hôte par écriture du lot (mesure par écriture puis lot entier).
// Sinon : une seule opération (get, liste, écriture simple), `hosts` = la règle mesurée.
// `docLimit` : limite documentée quand l'émulateur en applique une autre (liste : 10 documentés, 20 dans l'émulateur).
const scenarios = [
  {
    id: 'C17',
    title: 'création de Miff premium (batch de 4) : sous la limite de 20 lectures de règles',
    name: 'Création de Miff premium (batch de 4)',
    lot: true,
    hosts: [H.miffsCreate, H.joinCodesCreate, H.membersCreate, H.membershipsCreate],
    prepare: seedDaveMiff,
    run: (env) => commit(db(env, 'dave'), all(createMiffOps({ uid: 'dave', key: ID.NEW }))),
  },
  {
    id: 'READS-create-free',
    title: 'création de Miff gratuite (batch de 4)',
    name: 'Création de Miff gratuite (batch de 4)',
    lot: true,
    hosts: [H.miffsCreate, H.joinCodesCreate, H.membersCreate, H.membershipsCreate],
    run: (env) => commit(db(env, 'eve'), all(createMiffOps({ uid: 'eve' }))),
  },
  {
    id: 'READS-create-with-profile',
    title: 'profil + création de Miff gratuite dans le même lot (batch de 5, AUTH-B)',
    name: 'Profil + création de Miff gratuite (batch de 5)',
    limit: 20,
    hosts: [H.joinCodesCreate, H.membershipsCreate],
    prepare: (env) => env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc('users/eve').delete()),
    run: (env) => commit(db(env, 'eve'), [set('users/eve', userDoc()), ...all(createMiffOps({ uid: 'eve' }))]),
  },
  {
    id: 'READS-join',
    title: 'rejoindre une Miff (batch de 2)',
    name: 'Rejoindre une Miff (batch de 2)',
    lot: true,
    hosts: [H.membersCreate, H.membershipsCreate],
    run: (env) => commit(db(env, 'eve'), all(joinOps({ uid: 'eve' }))),
  },
  {
    id: 'READS-leave',
    title: 'quitter une Miff (batch de 4)',
    name: 'Quitter une Miff (batch de 4)',
    lot: true,
    hosts: [H.membersDelete, H.statusDelete, H.photosDelete, H.membershipsDelete],
    run: (env) => commit(db(env, 'bob'), all(leaveOps('bob'))),
  },
  {
    id: 'READS-remove-member',
    title: 'retirer un membre avec renouvellement du code (batch de 6, avec photo)',
    name: 'Retirer un membre + nouveau code (batch de 6)',
    lot: true,
    hosts: [H.membersDelete, H.statusDelete, H.photosDelete, H.miffsUpdate, H.joinCodesCreate, H.joinCodesDelete],
    prepare: seedBobPhoto,
    run: (env) => commit(db(env, 'alice'), all(removeMemberOps({ target: 'bob' }))),
  },
  {
    id: 'READS-delete-miff',
    title: 'la créatrice supprime la Miff (batch de 6, avec pierre tombale)',
    name: 'Supprimer la Miff (batch de 6, avec pierre tombale)',
    lot: true,
    hosts: [H.miffsDelete, H.miffsDeletedCreate, H.joinCodesDelete, H.membersDelete, H.statusDelete, H.membershipsDelete],
    run: (env) =>
      commit(db(env, 'alice'), [
        del(M1),
        set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
        del(`joinCodes/${CODE.M1}`),
        del(`${M1}/members/alice`),
        del(`${M1}/status/alice`),
        del('users/alice/memberships/free'),
      ]),
  },
  {
    id: 'READS-regen-code',
    title: 'régénérer le code (batch de 3)',
    name: 'Régénérer le code secret (batch de 3)',
    lot: true,
    hosts: [H.miffsUpdate, H.joinCodesCreate, H.joinCodesDelete],
    run: (env) =>
      commit(db(env, 'alice'), [
        update(M1, { joinCode: CODE.REGEN }),
        set(`joinCodes/${CODE.REGEN}`, { miffId: ID.M1, miffName: 'Famille', createdAt: serverTs() }),
        del(`joinCodes/${CODE.M1}`),
      ]),
  },
  {
    id: 'READS-consent-off',
    title: 'désactiver le partage (batch de 3)',
    name: 'Désactiver le partage (batch de 3)',
    lot: true,
    hosts: [H.membersUpdate, H.statusDelete, H.photosDelete],
    run: (env) =>
      commit(db(env, 'bob'), [
        update(`${M1}/members/bob`, {
          sharing: { enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: serverTs() },
        }),
        del(`${M1}/status/bob`),
        del(`${M1}/photos/bob`),
      ]),
  },
  {
    id: 'READS-consent-on',
    title: 'activer le partage et créer son statut (batch de 2)',
    name: 'Activer le partage + créer son statut (batch de 2)',
    lot: true,
    hosts: [H.membersUpdate, H.statusCreate],
    run: (env) =>
      commit(db(env, 'carol'), [update(`${M1}/members/carol`, { sharing: sharingOnNow() }), set(`${M1}/status/carol`, statusDoc())]),
  },
  {
    id: 'READS-status-update',
    title: 'mise à jour du statut (écriture simple)',
    name: 'Mise à jour du statut (écriture simple)',
    limit: 10,
    hosts: [H.statusUpdate],
    run: (env) => db(env, 'bob').doc(`${M1}/status/bob`).set(statusDoc()),
  },
  {
    id: 'READS-place-create-premium',
    title: 'création d\'un lieu premium (écriture simple)',
    name: 'Création d\'un lieu premium (écriture simple)',
    limit: 10,
    hosts: [H.placesCreate],
    prepare: seedDaveMiff,
    run: (env) => db(env, 'dave').collection(`miffs/${ID.M3}/places`).doc().set(placeInput('dave')),
  },
  {
    // Cause de la mesure « non concluante » du premier jet (R15) : l'émulateur accepte 20 lectures de règles pour une LISTE
    // (documentation : 10). Avec la limite de 10 de l'outil, 11 lectures factices ne suffisaient pas à faire échouer la requête.
    // On cherche donc jusqu'à 20 (limite constatée par la calibration) et on compare à la limite documentée (10).
    id: 'READS-read-status-list',
    title: 'lecture de la liste des statuts',
    name: 'Lecture de la liste des statuts',
    limit: 20,
    docLimit: 10,
    hosts: [H.statusList],
    run: (env) => db(env, 'bob').collection(`${M1}/status`).get(),
  },
  {
    id: 'READS-read-status-get',
    title: "lecture du statut d'un autre membre (get)",
    name: "Lecture du statut d'un membre (get)",
    limit: 10,
    hosts: [H.statusGet],
    run: (env) => db(env, 'bob').doc(`${M1}/status/alice`).get(),
  },
  {
    id: 'READS-read-photo',
    title: "lecture de la photo d'un autre membre (get)",
    name: "Lecture d'une photo (get)",
    limit: 10,
    hosts: [H.photosGet],
    prepare: seedAlicePhoto,
    run: (env) => db(env, 'bob').doc(`${M1}/photos/alice`).get(),
  },
  {
    id: 'READS-read-place',
    title: "lecture d'un lieu (get)",
    name: "Lecture d'un lieu (get)",
    limit: 10,
    hosts: [H.placesGet],
    run: (env) => db(env, 'bob').doc(`${M1}/places/p1`).get(),
  },
  {
    id: 'READS-read-miff',
    title: 'lecture du document Miff par un membre',
    name: 'Lecture du document Miff',
    limit: 10,
    hosts: [H.miffsGet],
    run: (env) => db(env, 'bob').doc(M1).get(),
  },
  {
    id: 'READS-profile-create',
    title: 'création du profil avec (a) et (b) (écriture simple, AUTH-B)',
    name: 'Création du profil + consentements (écriture simple)',
    limit: 10,
    hosts: [H.usersCreate],
    prepare: (env) => env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc('users/eve').delete()),
    run: (env) => db(env, 'eve').doc('users/eve').set(userDoc({ analyticsConsent: consentOnNow() })),
  },
  {
    id: 'READS-consent-stats-update',
    title: 'retrait du consentement (b) statistiques (écriture simple, AUTH-B)',
    name: 'Retrait du consentement statistiques (écriture simple)',
    limit: 10,
    hosts: [H.usersUpdate],
    prepare: (env) => env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc('users/carol').set(userWithConsent('carol', consentOnSeed()))),
    run: (env) => db(env, 'carol').doc('users/carol').update({ analyticsConsent: consentRevokeNow() }),
  },
  {
    // MIFF-0 : lot d'entrée « Rejoindre et partager » (fiche avec partage activé + index + statut initial).
    id: 'READS-join-share',
    title: 'rejoindre et partager : fiche + index + statut initial (batch de 3, MIFF-0)',
    name: 'Rejoindre et partager (batch de 3)',
    lot: true,
    hosts: [H.membersCreate, H.membershipsCreate, H.statusCreate],
    run: (env) => commit(db(env, 'eve'), all(joinShareOps({ uid: 'eve' }))),
  },
  {
    id: 'READS-create-share',
    title: 'créer et partager : Miff + code + fiche + index + statut initial (batch de 5, MIFF-0)',
    name: 'Créer et partager, gratuit (batch de 5)',
    lot: true,
    hosts: [H.miffsCreate, H.joinCodesCreate, H.membersCreate, H.membershipsCreate, H.statusCreate],
    run: (env) => commit(db(env, 'eve'), all(createShareOps({ uid: 'eve' }))),
  },
  {
    id: 'READS-create-share-premium',
    title: 'créer et partager en premium (batch de 5, MIFF-0)',
    name: 'Créer et partager, premium (batch de 5)',
    lot: true,
    hosts: [H.miffsCreate, H.joinCodesCreate, H.membersCreate, H.membershipsCreate, H.statusCreate],
    prepare: seedDaveMiff,
    run: (env) => commit(db(env, 'dave'), all(createShareOps({ uid: 'dave', key: ID.NEW }))),
  },
  {
    id: 'READS-create-share-profile',
    title: 'profil + créer et partager dans le même lot (batch de 6, MIFF-0 + AUTH-B + ONB-B)',
    name: 'Profil + créer et partager (batch de 6)',
    lot: true,
    hosts: [H.usersCreate, H.miffsCreate, H.joinCodesCreate, H.membersCreate, H.membershipsCreate, H.statusCreate],
    prepare: (env) => env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc('users/eve').delete()),
    run: (env) => commit(db(env, 'eve'), [set('users/eve', userDoc()), ...all(createShareOps({ uid: 'eve' }))]),
  },
  {
    id: 'READS-join-share-profile',
    title: 'profil + rejoindre et partager dans le même lot (batch de 4, MIFF-0 + AUTH-B + ONB-B)',
    name: 'Profil + rejoindre et partager (batch de 4)',
    lot: true,
    hosts: [H.usersCreate, H.membersCreate, H.membershipsCreate, H.statusCreate],
    prepare: (env) => env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc('users/eve').delete()),
    run: (env) => commit(db(env, 'eve'), [set('users/eve', userDoc()), ...all(joinShareOps({ uid: 'eve' }))]),
  },
  {
    // ONB-B : modification du numéro = profil + fiche de chaque Miff dans un même lot (ici une Miff).
    id: 'READS-phone-update',
    title: 'modifier son numéro : profil + fiche de la Miff (batch de 2, ONB-B)',
    name: 'Modifier le numéro : profil + fiche (batch de 2)',
    lot: true,
    hosts: [H.usersUpdate, H.membersUpdate],
    run: (env) =>
      commit(db(env, 'bob'), [update('users/bob', { phone: PHONE_B }), update(`${M1}/members/bob`, { phone: PHONE_B })]),
  },
  {
    // Le profil a déjà été mis à jour dans un lot précédent (cas d'un compte premium dans plus de 15 Miffs, ou nouvelle tentative) :
    // la fiche est alors comparée au profil déjà enregistré (une lecture de règles de plus que dans le lot commun).
    id: 'READS-phone-member-only',
    title: 'modifier le numéro de la fiche seule, le profil étant déjà à jour (écriture simple, ONB-B)',
    name: 'Modifier le numéro de la fiche seule (écriture simple)',
    limit: 10,
    hosts: [H.membersUpdate],
    prepare: (env) => env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc('users/bob').update({ phone: PHONE_B })),
    run: (env) => db(env, 'bob').doc(`${M1}/members/bob`).update({ phone: PHONE_B }),
  },
  {
    id: 'READS-phone-user-only',
    title: 'modifier son numéro dans le profil seul (écriture simple, ONB-B)',
    name: 'Modifier le numéro du profil seul (écriture simple)',
    limit: 10,
    hosts: [H.usersUpdate],
    run: (env) => db(env, 'bob').doc('users/bob').update({ phone: PHONE_B }),
  },
  {
    id: 'READS-code-lookup',
    title: 'recherche d\'un code secret (get joinCodes)',
    name: 'Recherche d\'un code secret',
    limit: 10,
    hosts: [H.joinCodesGet],
    run: (env) => db(env, 'eve').doc(`joinCodes/${CODE.M1}`).get(),
  },
];

const results = [];

test('READS-calibration - limites de lectures de règles appliquées par l\'émulateur (hypothèses de la mesure)', async () => {
  const observed = await probeEmulatorLimits();
  // Si l'émulateur change de comportement, les chiffres du rapport ne sont plus fiables : on s'arrête et on réexamine.
  assert.deepEqual(observed, EXPECTED_LIMITS);
});

for (const sc of scenarios) {
  test(`${sc.id} - ${sc.title}`, async (t) => {
    const r = sc.lot ? await measureLot(sc) : await measureReads(sc);
    const result = { ...r, ...(sc.docLimit ? { docLimit: sc.docLimit } : {}) };
    results.push(result);
    if (r.status === 'baseline-failed') {
      throw new Error(`${sc.name} : l'opération de référence échoue (${r.error})`);
    }
    assert.equal(r.status, 'ok', `${sc.name} : mesure non concluante (${r.status}) ; par écriture : ${JSON.stringify(r.perOp ?? '')}`);
    const documented = sc.docLimit ?? r.limit;
    const margin = documented - r.readsUpperBound;
    t.diagnostic(`${sc.name} : <= ${r.readsUpperBound} lectures de règles sur ${documented} (marge >= ${margin})`);
    assert.ok(margin >= MIN_MARGIN, `${sc.name} : marge de ${margin} lecture(s), moins de ${MIN_MARGIN} exigées`);
  });
}

after(() => {
  writeReadsReport(results);
});
