// C17 et critère T5 : nombre de lectures de règles (get/exists/getAfter/existsAfter) par opération.
// Limites Firestore : 10 par requête simple, 20 par batch. Voir tests/helpers/pads.js pour la méthode.
// Résultat écrit dans backend/test-output/rule-reads.md (et .json).
import { test, after } from 'node:test';
import {
  ID, CODE, commit, createMiffOps, joinOps, leaveOps, set, update, del, statusDoc, placeInput,
  serverTs, sharingOnNow, T0,
} from '../helpers/fixtures.js';
import { measureReads, writeReadsReport } from '../helpers/measure.js';
import { H } from '../helpers/hosts.js';


const M1 = `miffs/${ID.M1}`;

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

const scenarios = [
  {
    id: 'C17',
    title: 'création de Miff premium (batch de 4) : sous la limite de 20 lectures de règles',
    name: 'Création de Miff premium (batch de 4)',
    limit: 20,
    hosts: [H.joinCodesCreate, H.membershipsCreate],
    prepare: seedDaveMiff,
    run: (env) => commit(db(env, 'dave'), all(createMiffOps({ uid: 'dave', key: ID.NEW }))),
  },
  {
    id: 'READS-create-free',
    title: 'création de Miff gratuite (batch de 4)',
    name: 'Création de Miff gratuite (batch de 4)',
    limit: 20,
    hosts: [H.joinCodesCreate, H.membershipsCreate],
    run: (env) => commit(db(env, 'eve'), all(createMiffOps({ uid: 'eve' }))),
  },
  {
    id: 'READS-join',
    title: 'rejoindre une Miff (batch de 2)',
    name: 'Rejoindre une Miff (batch de 2)',
    limit: 20,
    hosts: [H.membershipsCreate, H.membersCreate],
    run: (env) => commit(db(env, 'eve'), all(joinOps({ uid: 'eve' }))),
  },
  {
    id: 'READS-leave',
    title: 'quitter une Miff (batch de 4)',
    name: 'Quitter une Miff (batch de 4)',
    limit: 20,
    hosts: [H.statusDelete, H.photosDelete],
    run: (env) => commit(db(env, 'bob'), all(leaveOps('bob'))),
  },
  {
    id: 'READS-remove-member',
    title: 'retirer un membre (batch de 3)',
    name: 'Retirer un membre (batch de 3)',
    limit: 20,
    hosts: [H.statusDelete, H.photosDelete],
    run: (env) =>
      commit(db(env, 'alice'), [del(`${M1}/members/bob`), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]),
  },
  {
    id: 'READS-delete-miff',
    title: 'la créatrice supprime la Miff (batch de 5)',
    name: 'Supprimer la Miff (batch de 5)',
    limit: 20,
    hosts: [H.statusDelete, H.membershipsDelete],
    run: (env) =>
      commit(db(env, 'alice'), [
        del(M1),
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
    limit: 20,
    hosts: [H.joinCodesCreate, H.joinCodesDelete],
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
    limit: 20,
    hosts: [H.statusDelete, H.photosDelete],
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
    limit: 20,
    hosts: [H.statusCreate],
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
    id: 'READS-read-status-list',
    title: 'lecture de la liste des statuts',
    name: 'Lecture de la liste des statuts',
    limit: 10,
    hosts: [H.statusList],
    run: (env) => db(env, 'bob').collection(`${M1}/status`).get(),
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
    id: 'READS-code-lookup',
    title: 'recherche d\'un code secret (get joinCodes)',
    name: 'Recherche d\'un code secret',
    limit: 10,
    hosts: [H.joinCodesGet],
    run: (env) => db(env, 'eve').doc(`joinCodes/${CODE.M1}`).get(),
  },
];

const results = [];

for (const sc of scenarios) {
  test(`${sc.id} - ${sc.title}`, async (t) => {
    const r = await measureReads(sc);
    results.push(r);
    if (r.status === 'ok') {
      t.diagnostic(`${sc.name} : <= ${r.readsUpperBound} lectures de règles sur ${r.limit} (marge >= ${r.maxExtraReads})`);
    } else if (r.status === 'inconclusive') {
      t.diagnostic(`${sc.name} : MESURE NON CONCLUANTE (les lectures factices ne sont pas comptées par l'émulateur)`);
    } else {
      throw new Error(`${sc.name} : l'opération de référence échoue (${r.error})`);
    }
  });
}

after(() => {
  writeReadsReport(results);
});
