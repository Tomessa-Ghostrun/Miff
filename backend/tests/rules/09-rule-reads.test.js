// C17 et critère T5 / R15 : nombre de lectures de règles (get/exists/getAfter/existsAfter) par opération.
// Limites Firestore documentées : 10 par requête simple, 20 par lot. Voir tests/helpers/pads.js et measure.js pour la méthode,
// et tests/helpers/calibration.js pour les limites réellement appliquées par l'émulateur (liste : 20 au lieu de 10).
// Résultat écrit dans backend/test-output/rule-reads.md (et .json).
// Depuis DUR-3 (R15) : une mesure non concluante, discordante ou à marge inférieure à 2 lectures FAIT ÉCHOUER le test.
import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import {
  consentOnNow, consentRevokeNow, consentOnSeed, userWithConsent, userDoc,
  ID, CODE, commit, createMiffOps, joinOps, leaveOps, removeMemberOps, leaveWithPositionOps, removeMemberWithPositionOps, set, update, del, statusDoc, placeInput, placeSeed, photoDoc,
  serverTs, sharingOnNow, T0, createShareOps, joinShareOps, PHONE_B, positionDoc, positionSeed, sharingOnNowV3, CONSENT_POSITION,
  statusInitial, DEVICE_A, sessionSeed, SESSION_PATH, inMs, photoLegacyDoc, Timestamp,
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

// Photos injectées pour les lots de PURGE (la règle de suppression ne regarde pas la localisation) et pour la lecture d'un ANCIEN document sans
// localisation (aucune application publiée n'en écrit plus : lecture permissive conservée, 2 lectures de règles).
const seedBobPhoto = (env) =>
  env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/photos/bob`).set(photoDoc({ createdAt: T0, measuredAt: T0 })));
const seedAlicePhoto = (env) =>
  env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/photos/alice`).set(photoLegacyDoc({ createdAt: T0 })));

// R-LIEU-POS : consentement v3 et dernière position enregistrés pour bob / alice (les deux ont déjà un statut dans le jeu de départ).
const seedV3Position = (uid) => (env) =>
  env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    await f.doc(`${M1}/members/${uid}`).update({ 'sharing.consentVersion': CONSENT_POSITION });
    await f.doc(`${M1}/positions/${uid}`).set(positionSeed());
  });
const seedBobV3Position = seedV3Position('bob');
const seedAliceV3Position = seedV3Position('alice');
const seedAliceBobV3Positions = async (env) => {
  await seedAliceV3Position(env);
  await seedBobV3Position(env);
};

// SESS-1 : session de l'appareil actif, pour mesurer les écritures de statut et de position qui portent la clé deviceId (une lecture de règles de plus).
const seedSessions = (...uids) => (env) =>
  env.withSecurityRulesDisabled(async (ctx) => {
    for (const uid of uids) await ctx.firestore().doc(SESSION_PATH(uid)).set(sessionSeed(DEVICE_A));
  });

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
    title: 'quitter une Miff (batch de 5, avec la dernière position)',
    name: 'Quitter une Miff (batch de 5)',
    lot: true,
    hosts: [H.membersDelete, H.statusDelete, H.photosDelete, H.positionsDelete, H.membershipsDelete],
    prepare: seedBobV3Position,
    run: (env) => commit(db(env, 'bob'), all(leaveWithPositionOps('bob'))),
  },
  {
    id: 'READS-remove-member',
    title: 'retirer un membre avec renouvellement du code (batch de 7, avec photo et dernière position)',
    name: 'Retirer un membre + nouveau code (batch de 7)',
    lot: true,
    hosts: [H.membersDelete, H.statusDelete, H.photosDelete, H.positionsDelete, H.miffsUpdate, H.joinCodesCreate, H.joinCodesDelete],
    prepare: async (env) => {
      await seedBobPhoto(env);
      await seedBobV3Position(env);
    },
    run: (env) => commit(db(env, 'alice'), all(removeMemberWithPositionOps({ target: 'bob' }))),
  },
  {
    id: 'READS-delete-miff',
    title: 'la créatrice supprime la Miff (batch de 7, avec pierre tombale et sa dernière position)',
    name: 'Supprimer la Miff (batch de 7, avec pierre tombale)',
    lot: true,
    hosts: [H.miffsDelete, H.miffsDeletedCreate, H.joinCodesDelete, H.membersDelete, H.statusDelete, H.positionsDelete, H.membershipsDelete],
    prepare: seedAliceV3Position,
    run: (env) =>
      commit(db(env, 'alice'), [
        del(M1),
        set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
        del(`joinCodes/${CODE.M1}`),
        del(`${M1}/members/alice`),
        del(`${M1}/status/alice`),
        del(`${M1}/positions/alice`),
        del('users/alice/memberships/free'),
      ]),
  },
  {
    // MIFF-2 : le lot final de la suppression, tel que le client l'écrit (avec la photo de la créatrice : 7 écritures).
    id: 'READS-delete-miff-final',
    title: 'suppression de la Miff, lot final du client (batch de 7 : Miff, pierre tombale, code, statut, photo, fiche, index, MIFF-2)',
    name: 'Supprimer la Miff, lot final (batch de 7, MIFF-2)',
    lot: true,
    hosts: [H.miffsDelete, H.miffsDeletedCreate, H.joinCodesDelete, H.statusDelete, H.photosDelete, H.membersDelete, H.membershipsDelete],
    prepare: seedAlicePhoto,
    run: (env) =>
      commit(db(env, 'alice'), [
        del(M1),
        set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
        del(`joinCodes/${CODE.M1}`),
        del(`${M1}/status/alice`),
        del(`${M1}/photos/alice`),
        del(`${M1}/members/alice`),
        del('users/alice/memberships/free'),
      ]),
  },
  {
    // MIFF-2 : cinq lieux supprimés dans un même lot (étape 2 de la suppression). Une seule règle hôte : les cinq écritures évaluent
    // la même règle sur les mêmes documents (comptés une fois avec le cache de l'émulateur ; au pire 5 fois sans cache).
    id: 'READS-delete-places',
    title: 'suppression de 5 lieux dans un lot (MIFF-2)',
    name: 'Supprimer 5 lieux (batch de 5, MIFF-2)',
    limit: 20,
    hosts: [H.placesDelete],
    prepare: (env) =>
      env.withSecurityRulesDisabled(async (ctx) => {
        const f = ctx.firestore();
        for (const id of ['p2', 'p3', 'p4', 'p5']) await f.doc(`${M1}/places/${id}`).set(placeSeed({ createdBy: 'alice' }));
      }),
    run: (env) => commit(db(env, 'alice'), ['p1', 'p2', 'p3', 'p4', 'p5'].map((id) => del(`${M1}/places/${id}`))),
  },
  {
    // MIFF-2 : retrait d'une cible déjà partie (fiche et statut absents) : même lot de 6, avec des suppressions de documents absents.
    id: 'READS-remove-member-gone',
    title: 'retirer un membre déjà parti avec renouvellement du code (batch de 6, MIFF-2)',
    name: 'Retirer un membre déjà parti + nouveau code (batch de 6, MIFF-2)',
    lot: true,
    hosts: [H.membersDelete, H.statusDelete, H.photosDelete, H.miffsUpdate, H.joinCodesCreate, H.joinCodesDelete],
    prepare: (env) =>
      env.withSecurityRulesDisabled(async (ctx) => {
        const f = ctx.firestore();
        await f.doc(`${M1}/members/bob`).delete();
        await f.doc(`${M1}/status/bob`).delete();
      }),
    run: (env) => commit(db(env, 'alice'), all(removeMemberOps({ target: 'bob' }))),
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
    title: 'désactiver le partage (batch de 4, avec la dernière position)',
    name: 'Désactiver le partage (batch de 4)',
    lot: true,
    hosts: [H.membersUpdate, H.statusDelete, H.photosDelete, H.positionsDelete],
    prepare: seedBobV3Position,
    run: (env) =>
      commit(db(env, 'bob'), [
        update(`${M1}/members/bob`, {
          sharing: { enabled: false, consentedAt: T0, consentVersion: CONSENT_POSITION, revokedAt: serverTs() },
        }),
        del(`${M1}/status/bob`),
        del(`${M1}/photos/bob`),
        del(`${M1}/positions/bob`),
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
    // Lieux, évolutions du 2026-11-01 (R-LIEU-5, -6) : coût PAR DOCUMENT ; un lot de N Miffs coûte au plus N fois le coût sans cache
    // (comptage manuel dans data-model.md §12 : création premium 3, gratuite p1/p2 2, passage à risk 3, suppression 2 -> 5 Miffs = 15, 10, 15, 10 sur 20).
    // Les lectures factices (zz_pad) ont des chemins fixes que l'émulateur met en cache d'une écriture à l'autre du lot : un lot de N
    // écritures identiques ne se mesure donc pas par injection ; on mesure une écriture et on multiplie par le pire cas manuel.
    id: 'READS-place-create-free',
    title: "création d'un lieu gratuit sous p2 (écriture simple)",
    name: "Création d'un lieu gratuit p2 (écriture simple)",
    limit: 10,
    hosts: [H.placesCreate],
    run: (env) => db(env, 'alice').doc(`${M1}/places/p2`).set(placeInput('alice')),
  },
  {
    id: 'READS-place-update-risk',
    title: "passage d'un lieu à la zone de danger, premium (écriture simple)",
    name: "Lieu : passage à risk, premium (écriture simple)",
    limit: 10,
    hosts: [H.placesUpdate],
    prepare: async (env) => {
      await seedDaveMiff(env);
      await env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`miffs/${ID.M3}/places/abc`).set(placeSeed({ createdBy: 'dave' })));
    },
    run: (env) => db(env, 'dave').doc(`miffs/${ID.M3}/places/abc`).update({ type: 'risk', updatedAt: serverTs() }),
  },
  {
    id: 'READS-place-delete',
    title: "suppression d'un lieu (écriture simple)",
    name: "Suppression d'un lieu (écriture simple)",
    limit: 10,
    hosts: [H.placesDelete],
    run: (env) => db(env, 'alice').doc(`${M1}/places/p1`).delete(),
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
    title: "lecture d'un ANCIEN document de photo sans localisation d'un autre membre (get : Miff + mon partage seulement)",
    name: "Lecture d'une photo sans localisation (get)",
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
    // R-LIEU-POS : la dernière position est une écriture ISOLÉE (jamais dans un lot de lieux). Une lecture de règles : getAfter de la
    // fiche du membre (partage actif + texte de version 3 ou plus). Un lot de N Miffs coûte N (comptage manuel, data-model.md §12).
    id: 'READS-position-write',
    title: 'écrire sa dernière position (écriture simple, R-LIEU-POS)',
    name: 'Écriture de la dernière position (écriture simple)',
    limit: 10,
    hosts: [H.positionsCreate],
    prepare: (env) => env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': CONSENT_POSITION })),
    run: (env) => db(env, 'bob').doc(`${M1}/positions/bob`).set(positionDoc()),
  },
  {
    id: 'READS-position-get',
    title: "lecture de la dernière position d'un autre membre (get, R-LIEU-POS)",
    name: "Lecture de la dernière position d'un membre (get)",
    limit: 10,
    hosts: [H.positionsGet],
    prepare: seedAliceBobV3Positions,
    run: (env) => db(env, 'bob').doc(`${M1}/positions/alice`).get(),
  },
  {
    id: 'READS-position-delete-owner',
    title: "la créatrice supprime la position d'un membre (écriture simple, R-LIEU-POS)",
    name: "Suppression de la position d'un membre par la créatrice (écriture simple)",
    limit: 10,
    hosts: [H.positionsDelete],
    prepare: seedBobV3Position,
    run: (env) => db(env, 'alice').doc(`${M1}/positions/bob`).delete(),
  },
  {
    id: 'READS-position-activate',
    title: 'activer le partage (texte v3) + statut + dernière position (batch de 3, R-LIEU-POS)',
    name: 'Activer le partage + statut + position (batch de 3)',
    lot: true,
    hosts: [H.membersUpdate, H.statusCreate, H.positionsCreate],
    run: (env) =>
      commit(db(env, 'carol'), [
        update(`${M1}/members/carol`, { sharing: sharingOnNowV3() }),
        set(`${M1}/status/carol`, statusDoc()),
        set(`${M1}/positions/carol`, positionDoc()),
      ]),
  },
  {
    // R-LIEU-3 : renouveler un consentement actif = une écriture de fiche, aucune lecture de règles.
    id: 'READS-consent-renew',
    title: 'renouveler un consentement de partage actif (écriture simple, R-LIEU-3)',
    name: 'Renouvellement du consentement de partage (écriture simple)',
    limit: 10,
    hosts: [H.membersUpdate],
    prepare: (env) => env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': '2026-10-v2' })),
    run: (env) =>
      db(env, 'bob').doc(`${M1}/members/bob`).update({ sharing: { enabled: true, consentedAt: serverTs(), consentVersion: CONSENT_POSITION, revokedAt: null } }),
  },
  // --- SESS-1 : un compte, un seul appareil. Chaque statut / position écrit avec deviceId lit la session (+1) ; mesures avec la clé. ---
  {
    id: 'READS-sess-status',
    title: 'SESS-1 : écrire son statut avec le deviceId de la session (écriture simple)',
    name: 'Statut avec deviceId (écriture simple)',
    limit: 10,
    maxReads: 2, // VOY-B : le mode voyage ne doit pas ajouter de lecture à un statut ordinaire
    hosts: [H.statusUpdate],
    prepare: seedSessions('bob'),
    run: (env) => db(env, 'bob').doc(`${M1}/status/bob`).set(statusDoc({ deviceId: DEVICE_A })),
  },
  {
    id: 'READS-sess-position',
    title: 'SESS-1 : écrire sa dernière position avec le deviceId de la session (écriture simple)',
    name: 'Position avec deviceId (écriture simple)',
    limit: 10,
    hosts: [H.positionsCreate],
    prepare: async (env) => {
      await seedSessions('bob')(env);
      await env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': CONSENT_POSITION }));
    },
    run: (env) => db(env, 'bob').doc(`${M1}/positions/bob`).set(positionDoc({ deviceId: DEVICE_A })),
  },
  {
    id: 'READS-sess-join-share',
    title: 'SESS-1 : rejoindre et partager avec le deviceId (batch de 3)',
    name: 'Rejoindre et partager avec deviceId (batch de 3)',
    lot: true,
    hosts: [H.membersCreate, H.membershipsCreate, H.statusCreate],
    prepare: seedSessions('eve'),
    run: (env) => commit(db(env, 'eve'), all(joinShareOps({ uid: 'eve', status: statusInitial({ deviceId: DEVICE_A }) }))),
  },
  {
    id: 'READS-sess-create-share',
    title: 'SESS-1 : créer et partager (gratuit) avec le deviceId (batch de 5)',
    name: 'Créer et partager gratuit avec deviceId (batch de 5)',
    lot: true,
    hosts: [H.miffsCreate, H.joinCodesCreate, H.membersCreate, H.membershipsCreate, H.statusCreate],
    prepare: seedSessions('eve'),
    run: (env) => commit(db(env, 'eve'), all(createShareOps({ uid: 'eve', status: statusInitial({ deviceId: DEVICE_A }) }))),
  },
  {
    // Pire cas du lot (data-model.md §12) : un compte premium qui garde son emplacement gratuit et crée une 2e Miff. 18 appels sans cache.
    id: 'READS-sess-create-share-premium',
    title: 'SESS-1 : créer et partager en premium avec le deviceId (batch de 5, pire cas 18 sur 20)',
    name: 'Créer et partager premium avec deviceId (batch de 5)',
    lot: true,
    hosts: [H.miffsCreate, H.joinCodesCreate, H.membersCreate, H.membershipsCreate, H.statusCreate],
    prepare: async (env) => {
      await seedDaveMiff(env);
      await seedSessions('dave')(env);
    },
    run: (env) => commit(db(env, 'dave'), all(createShareOps({ uid: 'dave', key: ID.NEW, status: statusInitial({ deviceId: DEVICE_A }) }))),
  },
  {
    id: 'READS-sess-activate',
    title: 'SESS-1 : activer le partage (texte v3) + statut + position avec le deviceId (batch de 3)',
    name: 'Activer le partage + statut + position avec deviceId (batch de 3)',
    lot: true,
    hosts: [H.membersUpdate, H.statusCreate, H.positionsCreate],
    prepare: seedSessions('carol'),
    run: (env) =>
      commit(db(env, 'carol'), [
        update(`${M1}/members/carol`, { sharing: sharingOnNowV3() }),
        set(`${M1}/status/carol`, statusDoc({ deviceId: DEVICE_A })),
        set(`${M1}/positions/carol`, positionDoc({ deviceId: DEVICE_A })),
      ]),
  },
  {
    id: 'READS-sess-manual-update',
    title: 'SESS-1 : mise à jour manuelle (photo localisée + statut) avec le deviceId (batch de 2)',
    name: 'Mise à jour manuelle avec deviceId (batch de 2)',
    lot: true,
    hosts: [H.photosCreate, H.statusUpdate],
    prepare: async (env) => {
      // Toute photo exige le texte de consentement de version 03 (localisation obligatoire) ; la position publiée n'est pas exigée à l'écriture.
      await env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': CONSENT_POSITION }));
      await seedSessions('bob')(env);
    },
    run: (env) => commit(db(env, 'bob'), [set(`${M1}/photos/bob`, photoDoc()), set(`${M1}/status/bob`, statusDoc({ hasPhoto: true, deviceId: DEVICE_A }))]),
  },
  // --- MODE VOYAGE (VOY-B, disponible pour tous les comptes) : `maxReads` = borne exigée sur les lectures de règles utilisées. Un statut de voyage
  // coûte 2 (consentement + session), comme un statut ordinaire (READS-sess-status, maxReads 2) : aucune lecture du premium. ---
  {
    id: 'READS-voyage-activate',
    title: 'VOY-B : activer le voyage (statut existant) = consentement + session : 2 lectures de règles, comme un statut ordinaire',
    name: 'Activation du voyage (écriture simple)',
    limit: 10,
    maxReads: 2,
    hosts: [H.statusUpdate],
    prepare: async (env) => {
      await seedDaveMiff(env);
      await seedSessions('dave')(env);
      await env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`miffs/${ID.M3}/status/dave`).set(statusDoc({ updatedAt: T0 })));
    },
    run: (env) => db(env, 'dave').doc(`miffs/${ID.M3}/status/dave`).set(travelStatus()),
  },
  {
    id: 'READS-voyage-activate-lot',
    title: 'VOY-B : activation du voyage + suppression de MA dernière position dans le même lot (batch de 2, une Miff)',
    name: 'Activation du voyage + suppression de la position (batch de 2)',
    lot: true,
    maxReads: 2,
    hosts: [H.statusUpdate, H.positionsDelete],
    prepare: async (env) => {
      await seedDaveMiff(env);
      await seedSessions('dave')(env);
      await env.withSecurityRulesDisabled(async (ctx) => {
        await ctx.firestore().doc(`miffs/${ID.M3}/status/dave`).set(statusDoc({ updatedAt: T0 }));
        await ctx.firestore().doc(`miffs/${ID.M3}/positions/dave`).set(positionSeed());
      });
    },
    run: (env) => commit(db(env, 'dave'), [set(`miffs/${ID.M3}/status/dave`, travelStatus()), del(`miffs/${ID.M3}/positions/dave`)]),
  },
  // R-PHOTO-LOC (localisation OBLIGATOIRE, décision du PM du 2026-11-06) : toute photo écrite est localisée. La lecture de la photo d'un autre coûte donc
  // 3 lectures de règles (4 facturées avec le document) au lieu de 2 ; l'écriture reste à 1 lecture.
  {
    id: 'READS-photo-loc-read',
    title: "R-PHOTO-LOC : lecture de la photo (toujours localisée) d'un autre membre (get : Miff + mon partage + MA position)",
    name: "Lecture d'une photo localisée (get)",
    limit: 10,
    hosts: [H.photosGet],
    prepare: async (env) => {
      await seedAliceBobV3Positions(env);
      await env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`${M1}/photos/alice`).set(photoDoc({ createdAt: T0, lat: 48.85661, lng: 2.35222, measuredAt: T0 })));
    },
    run: (env) => db(env, 'bob').doc(`${M1}/photos/alice`).get(),
  },
  {
    id: 'READS-photo-loc-manual-update',
    title: 'R-PHOTO-LOC : mise à jour manuelle avec photo LOCALISÉE + statut avec le deviceId (batch de 2)',
    name: 'Mise à jour manuelle avec photo localisée (batch de 2)',
    lot: true,
    hosts: [H.photosCreate, H.statusUpdate],
    prepare: async (env) => {
      await seedBobV3Position(env);
      await seedSessions('bob')(env);
    },
    run: (env) =>
      commit(db(env, 'bob'), [
        set(`${M1}/photos/bob`, photoDoc({ lat: 48.85661, lng: 2.35222, measuredAt: inMs(-60_000) })),
        set(`${M1}/status/bob`, statusDoc({ hasPhoto: true, deviceId: DEVICE_A })),
      ]),
  },
  {
    id: 'READS-photo-loc-activate-all',
    title: 'R-PHOTO-LOC : pire cas (non recommandé) activer le partage + statut + position + photo localisée dans un lot de 4, avec le deviceId',
    name: 'Activation + statut + position + photo localisée (batch de 4)',
    lot: true,
    hosts: [H.membersUpdate, H.statusCreate, H.positionsCreate, H.photosCreate],
    prepare: seedSessions('carol'),
    run: (env) =>
      commit(db(env, 'carol'), [
        update(`${M1}/members/carol`, { sharing: sharingOnNowV3() }),
        set(`${M1}/status/carol`, statusDoc({ deviceId: DEVICE_A })),
        set(`${M1}/positions/carol`, positionDoc({ deviceId: DEVICE_A })),
        set(`${M1}/photos/carol`, photoDoc({ lat: 48.85661, lng: 2.35222, measuredAt: inMs(-60_000) })),
      ]),
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

// Mode voyage (VOY-B) : statut de voyage tel qu'un client l'écrit (fin dans 7 jours, deviceId de la session de dave).
const TRAVEL_UNTIL = Timestamp.fromMillis(Date.now() + 7 * 86_400_000);
const travelStatus = (over = {}) =>
  statusDoc({
    presence: 'travel', placeId: null, batteryPct: null, escalationDueAt: null, deviceId: DEVICE_A,
    travelUntil: TRAVEL_UNTIL, travelUntilDay: TRAVEL_UNTIL.toDate().toISOString().slice(0, 10), ...over,
  });

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
    if (sc.maxReads !== undefined) {
      assert.ok(r.readsUpperBound <= sc.maxReads, `${sc.name} : ${r.readsUpperBound} lecture(s) de règles, plus que les ${sc.maxReads} documentées`);
    }
  });
}

after(() => {
  writeReadsReport(results);
});
