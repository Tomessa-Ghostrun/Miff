// Plan de test, section 13 : AUTH-B, consentement facultatif (b) « statistiques d'usage et rapports de plantage »
// et acceptation (a) des CGU, sur users/{uid} (ticket docs/tickets/authentification.md, §8, §10, §15).
//   AB1 à AB5   : création du profil (sans (b), (b) refusée, (b) acceptée, (b) falsifiée, forme du bloc)
//   AB6         : (a) CGU et politique : horodatage serveur et version, impossible de créer un profil sans elles
//   AB7, AB8    : usurpation d'uid, mise à jour par autrui
//   AB9 à AB12  : activation, retrait, réactivation, rétrogradation de la version (Réglages)
//   AB13, AB14  : champs verrouillés ou inconnus, non-régression du profil
//   AB15, AB16  : suppression du profil, lecture réservée au propriétaire
//   AB17        : minimisation (aucun âge, date de naissance, e-mail, téléphone, position)
//   AB18 à AB22 : pas de Miff créée ni rejointe sans profil portant (a) ; profil possible dans le même lot ; départ non bloqué
// Même jeu de données qu'à la section 0 du plan (alice, bob, carol, dave, zoe ont un profil sans bloc de consentement ;
// eve et frank n'en ont pas). Chaque cas isole UNE protection (voir tests/tools/mutations.mjs).
import assert from 'node:assert/strict';
import {
  ID, CODE, commit, createMiffOps, joinOps, leaveOps, removeMemberOps, set, del,
  T0, HOUR, suite, assertFails, assertSucceeds, serverTs, inMs, bytes, Timestamp, FieldValue, userDoc, profile,
  PHONE, CONSENT_V1, CONSENT_V2, consentOff, consentOnNow, consentOnSeed, consentRevokedSeed, consentRevokeNow, userWithConsent,
} from '../helpers/fixtures.js';

const s = suite({ extraProfiles: false });

const omit = (obj, ...keys) => Object.fromEntries(Object.entries(obj).filter(([k]) => !keys.includes(k)));

/** Lit un document avec les règles désactivées (vérification de l'état stocké, jamais une autorisation). */
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

// ---------------------------------------------------------------------------------------------------------
// Création du profil (inscription ou panneau de consentement d'un nouvel utilisateur social)
// ---------------------------------------------------------------------------------------------------------
s.tc('AB1', 'création sans bloc de consentement : acceptée, et aucun consentement n\'est enregistré (absence = pas de consentement)', async ({ db, admin }) => {
  await assertSucceeds(db('eve').doc('users/eve').set(userDoc()));
  const doc = await stored(admin, 'users/eve');
  assert.equal('analyticsConsent' in doc, false);
});

s.tc('AB2', 'création avec (b) refusée (case décochée) : acceptée, état « non consenti » enregistré sans date ni version', async ({ db, admin }) => {
  await assertSucceeds(db('eve').doc('users/eve').set(userDoc({ analyticsConsent: consentOff() })));
  const doc = await stored(admin, 'users/eve');
  assert.deepEqual(doc.analyticsConsent, consentOff());
});

s.tc('AB3', 'création avec (b) acceptée : acceptée, date = heure serveur (celle des CGU), version enregistrée', async ({ db, admin }) => {
  await assertSucceeds(db('eve').doc('users/eve').set(userDoc({ analyticsConsent: consentOnNow() })));
  const doc = await stored(admin, 'users/eve');
  const c = doc.analyticsConsent;
  assert.equal(c.enabled, true);
  assert.equal(c.consentVersion, CONSENT_V1);
  assert.equal(c.revokedAt, null);
  assert.ok(c.consentedAt.isEqual(doc.termsAcceptedAt), 'consentedAt et termsAcceptedAt viennent du même instant serveur');
  assert.ok(Math.abs(c.consentedAt.toMillis() - Date.now()) < 60_000, 'consentedAt est proche de l\'heure réelle');
});

s.tc('AB4', 'création avec un consentement (b) falsifié (date client, état incohérent, version absente) : refusée', async ({ db }) => {
  const ref = db('eve').doc('users/eve');
  const create = (analyticsConsent) => () => ref.set(userDoc({ analyticsConsent }));
  await assertAllFail([
    ['date de consentement ancienne fournie par le client', create(consentOnNow({ consentedAt: T0 }))],
    ['date de consentement dans le futur', create(consentOnNow({ consentedAt: inMs(HOUR) }))],
    ['date « maintenant » prise sur l\'horloge du client', create(consentOnNow({ consentedAt: Timestamp.now() }))],
    ['consentement donné sans date', create(consentOnNow({ consentedAt: null }))],
    ['consentement donné sans version (null)', create(consentOnNow({ consentVersion: null }))],
    ['consentement donné avec une version vide', create(consentOnNow({ consentVersion: '' }))],
    ['consentement donné sans la clé de version', create(omit(consentOnNow(), 'consentVersion'))],
    ['consentement donné avec une date de retrait', create(consentOnNow({ revokedAt: serverTs() }))],
    ['non consenti avec une date de consentement', create(consentOff({ consentedAt: serverTs() }))],
    ['non consenti avec une version', create(consentOff({ consentVersion: CONSENT_V1 }))],
    ['non consenti avec une date de retrait', create(consentOff({ revokedAt: serverTs() }))],
  ]);
  // Contrôle : le même profil avec un consentement valide est accepté (les refus ci-dessus viennent bien du consentement).
  await assertSucceeds(ref.set(userDoc({ analyticsConsent: consentOnNow() })));
});

s.tc('AB5', 'création : forme du bloc de consentement (type, clés, format de version) strictement contrôlée', async ({ db }) => {
  const ref = db('eve').doc('users/eve');
  const create = (analyticsConsent) => () => ref.set(userDoc({ analyticsConsent }));
  await assertAllFail([
    ['bloc booléen', create(true)],
    ['bloc texte', create('oui')],
    ['bloc nul', create(null)],
    ['bloc vide', create({})],
    ['clé en trop', create(consentOnNow({ consentedBy: 'eve' }))],
    ['clé revokedAt manquante', create(omit(consentOnNow(), 'revokedAt'))],
    ['enabled texte', create(consentOnNow({ enabled: 'true' }))],
    ['enabled nombre', create(consentOnNow({ enabled: 1 }))],
    ['version nombre', create(consentOnNow({ consentVersion: 202610 }))],
    ['version sans zéro de tête', create(consentOnNow({ consentVersion: '2026-10-v1' }))],
    ['version sans le préfixe v', create(consentOnNow({ consentVersion: '2026-10-01' }))],
    ['version avec un mois impossible', create(consentOnNow({ consentVersion: '2026-13-v01' }))],
    ['version trop longue', create(consentOnNow({ consentVersion: '2026-10-v01-surplus' }))],
  ]);
  // Contrôle : la forme valide est bien acceptée sur ce même document.
  await assertSucceeds(ref.set(userDoc({ analyticsConsent: consentOnNow() })));
});

// ---------------------------------------------------------------------------------------------------------
// (a) CGU et politique de confidentialité
// ---------------------------------------------------------------------------------------------------------
s.tc('AB6', '(a) : pas de profil sans acceptation datée (heure serveur) et versionnée, même avec (b) acceptée', async ({ db, admin }) => {
  const ref = db('eve').doc('users/eve');
  const withB = (over) => () => ref.set(userDoc({ analyticsConsent: consentOnNow(), ...over }));
  await assertAllFail([
    ['sans termsAcceptedAt', () => ref.set(omit(userDoc({ analyticsConsent: consentOnNow() }), 'termsAcceptedAt'))],
    ['sans termsVersion', () => ref.set(omit(userDoc({ analyticsConsent: consentOnNow() }), 'termsVersion'))],
    ['sans aucune des deux', () => ref.set(omit(userDoc({ analyticsConsent: consentOnNow() }), 'termsAcceptedAt', 'termsVersion'))],
    ['termsAcceptedAt fourni par le client (ancien)', withB({ termsAcceptedAt: T0 })],
    ['termsAcceptedAt sur l\'horloge du client', withB({ termsAcceptedAt: Timestamp.now() })],
    ['termsAcceptedAt nul', withB({ termsAcceptedAt: null })],
    ['termsVersion vide', withB({ termsVersion: '' })],
    ['termsVersion de 21 caractères', withB({ termsVersion: 'x'.repeat(21) })],
    ['termsVersion nombre', withB({ termsVersion: 1 })],
  ]);
  // Contrôle : (a) valide + (b) acceptée, créés ensemble, partagent le même instant serveur.
  await assertSucceeds(ref.set(userDoc({ analyticsConsent: consentOnNow() })));
  const doc = await stored(admin, 'users/eve');
  assert.equal(doc.termsVersion, 'v1');
  assert.ok(doc.termsAcceptedAt.isEqual(doc.createdAt));
});

// ---------------------------------------------------------------------------------------------------------
// Usurpation et tiers
// ---------------------------------------------------------------------------------------------------------
s.tc('AB7', 'création du profil d\'un autre uid (ou sans connexion), avec ou sans (b) : refusée', async ({ db, anon, admin }) => {
  await assertAllFail([
    ['eve crée users/frank avec (b) acceptée', () => db('eve').doc('users/frank').set(userDoc({ analyticsConsent: consentOnNow() }))],
    ['eve crée users/frank sans (b)', () => db('eve').doc('users/frank').set(userDoc())],
    ['eve écrase users/alice avec (b) acceptée', () => db('eve').doc('users/alice').set(userDoc({ analyticsConsent: consentOnNow() }))],
    ['sans connexion', () => anon().doc('users/frank').set(userDoc({ analyticsConsent: consentOnNow() }))],
  ]);
  assert.equal(await stored(admin, 'users/frank'), undefined, 'aucun profil créé pour frank');
  // Contrôle : frank crée son propre profil avec la même donnée.
  await assertSucceeds(db('frank').doc('users/frank').set(userDoc({ analyticsConsent: consentOnNow() })));
});

s.tc('AB8', 'mise à jour du consentement (b) d\'un autre utilisateur : refusée, même par le créateur de sa Miff', async ({ db, anon, admin }) => {
  await admin((f) => f.doc('users/alice').set(userWithConsent('alice', consentOff())));
  await admin((f) => f.doc('users/carol').set(userWithConsent('carol', consentOnSeed())));
  const grant = () => ({ analyticsConsent: consentOnNow() });
  const revoke = () => ({ analyticsConsent: consentRevokeNow() });
  await assertAllFail([
    ['bob active le consentement d\'alice', () => db('bob').doc('users/alice').update(grant())],
    ['eve (étrangère) active le consentement d\'alice', () => db('eve').doc('users/alice').update(grant())],
    ['alice (créatrice de la Miff) retire le consentement de carol', () => db('alice').doc('users/carol').update(revoke())],
    ['bob retire le consentement de carol', () => db('bob').doc('users/carol').update(revoke())],
    ['sans connexion', () => anon().doc('users/carol').update(revoke())],
    ['écriture complète (set) par un tiers', () => db('bob').doc('users/carol').set(userWithConsent('carol', consentOff()))],
  ]);
  const c = (await stored(admin, 'users/carol')).analyticsConsent;
  assert.equal(c.enabled, true, 'le consentement de carol est intact');
  // Contrôle : carol et alice modifient chacune LEUR consentement avec les mêmes écritures.
  await assertSucceeds(db('carol').doc('users/carol').update(revoke()));
  await assertSucceeds(db('alice').doc('users/alice').update(grant()));
});

// ---------------------------------------------------------------------------------------------------------
// Réglages : activer, retirer, réactiver
// ---------------------------------------------------------------------------------------------------------
s.tc('AB9', 'activer (b) soi-même : accepté depuis « absent » ou « non consenti », avec date serveur et version ; falsifications refusées', async ({ db, admin }) => {
  await admin((f) => f.doc('users/zoe').set(userWithConsent('zoe', consentOff())));
  // dave : profil sans bloc (compte ancien) ; zoe : (b) refusée à l'inscription.
  await assertSucceeds(db('dave').doc('users/dave').update({ analyticsConsent: consentOnNow() }));
  await assertSucceeds(db('zoe').doc('users/zoe').update({ analyticsConsent: consentOnNow() }));
  const dave = (await stored(admin, 'users/dave')).analyticsConsent;
  assert.equal(dave.enabled, true);
  assert.equal(dave.consentVersion, CONSENT_V1);
  assert.ok(Math.abs(dave.consentedAt.toMillis() - Date.now()) < 60_000);
  assert.equal(dave.revokedAt, null);

  // Falsifications sur un profil encore « non consenti » (bob n'a pas de bloc).
  const bob = db('bob').doc('users/bob');
  await assertAllFail([
    ['date de consentement du client', () => bob.update({ analyticsConsent: consentOnNow({ consentedAt: T0 }) })],
    ['sans date', () => bob.update({ analyticsConsent: consentOnNow({ consentedAt: null }) })],
    ['sans version', () => bob.update({ analyticsConsent: consentOnNow({ consentVersion: null }) })],
    ['avec une date de retrait', () => bob.update({ analyticsConsent: consentOnNow({ revokedAt: serverTs() }) })],
    ['clé en trop', () => bob.update({ analyticsConsent: consentOnNow({ source: 'reglages' }) })],
    ['bloc non consenti avec une date de consentement', () => bob.update({ analyticsConsent: consentOff({ consentedAt: serverTs() }) })],
  ]);
  // Profil + consentement dans la même écriture : accepté.
  await assertSucceeds(bob.update({ firstName: 'Bobby', analyticsConsent: consentOnNow() }));
});

s.tc('AB10', 'retirer (b) : accepté avec heure serveur en conservant la trace du consentement ; effacer la preuve ou antidater : refusé', async ({ db, admin }) => {
  await admin((f) => f.doc('users/carol').set(userWithConsent('carol', consentOnSeed())));
  await admin((f) => f.doc('users/alice').set(userWithConsent('alice', consentOff())));
  const carol = db('carol').doc('users/carol');
  await assertAllFail([
    ['retrait qui efface la date du consentement', () => carol.update({ analyticsConsent: consentRevokeNow({ consentedAt: null }) })],
    ['retrait qui efface la version', () => carol.update({ analyticsConsent: consentRevokeNow({ consentVersion: null }) })],
    ['retrait qui change la version conservée', () => carol.update({ analyticsConsent: consentRevokeNow({ consentVersion: CONSENT_V2 }) })],
    ['retrait qui change la date conservée', () => carol.update({ analyticsConsent: consentRevokeNow({ consentedAt: serverTs() }) })],
    ['date de retrait du client', () => carol.update({ analyticsConsent: consentRevokeNow({ revokedAt: T0 }) })],
    ['date de retrait sur l\'horloge du client', () => carol.update({ analyticsConsent: consentRevokeNow({ revokedAt: Timestamp.now() }) })],
    ['retrait sans date de retrait', () => carol.update({ analyticsConsent: consentRevokeNow({ revokedAt: null }) })],
    ['suppression pure et simple du bloc', () => carol.update({ analyticsConsent: FieldValue.delete() })],
    ['« retrait » d\'un profil qui n\'a jamais consenti', () => db('alice').doc('users/alice').update({ analyticsConsent: consentRevokeNow({ consentedAt: null, consentVersion: null }) })],
  ]);
  await assertSucceeds(carol.update({ analyticsConsent: consentRevokeNow() }));
  const c = (await stored(admin, 'users/carol')).analyticsConsent;
  assert.equal(c.enabled, false);
  assert.ok(c.consentedAt.isEqual(T0), 'la date du consentement est conservée');
  assert.equal(c.consentVersion, CONSENT_V1, 'la version du consentement est conservée');
  assert.ok(Math.abs(c.revokedAt.toMillis() - Date.now()) < 60_000, 'revokedAt est l\'heure du serveur');
});

s.tc('AB11', 'réactiver (b) après un retrait : accepté avec nouvelle date serveur, revokedAt vidé, version identique ou plus récente', async ({ db, admin }) => {
  await admin((f) => f.doc('users/carol').set(userWithConsent('carol', consentRevokedSeed())));
  await admin((f) => f.doc('users/bob').set(userWithConsent('bob', consentRevokedSeed({ consentVersion: '2026-09-v09' }))));
  await admin((f) => f.doc('users/zoe').set(userWithConsent('zoe', consentRevokedSeed({ consentVersion: '2026-10-v09' }))));
  await assertSucceeds(db('carol').doc('users/carol').update({ analyticsConsent: consentOnNow({ consentVersion: CONSENT_V2 }) }));
  await assertSucceeds(db('bob').doc('users/bob').update({ analyticsConsent: consentOnNow({ consentVersion: '2026-10-v10' }) }));
  // Le zéro de tête garantit l'ordre : v09 -> v10 est une montée de version.
  await assertSucceeds(db('zoe').doc('users/zoe').update({ analyticsConsent: consentOnNow({ consentVersion: '2026-10-v10' }) }));
  const c = (await stored(admin, 'users/carol')).analyticsConsent;
  assert.equal(c.enabled, true);
  assert.equal(c.revokedAt, null);
  assert.ok(!c.consentedAt.isEqual(T0), 'nouvelle date de consentement');
});

s.tc('AB12', 'rétrogradation de la version du consentement (b) refusée ; réécriture sans retrait préalable refusée', async ({ db, admin }) => {
  await admin((f) => f.doc('users/carol').set(userWithConsent('carol', consentRevokedSeed())));      // retiré, dernière version v02
  await admin((f) => f.doc('users/bob').set(userWithConsent('bob', consentOnSeed({ consentVersion: CONSENT_V2 })))); // actif v02
  await assertAllFail([
    ['réactivation avec la version v01 (retiré en v02)', () => db('carol').doc('users/carol').update({ analyticsConsent: consentOnNow({ consentVersion: CONSENT_V1 }) })],
    ['réactivation avec un mois plus ancien', () => db('carol').doc('users/carol').update({ analyticsConsent: consentOnNow({ consentVersion: '2026-09-v99' }) })],
    ['réactivation avec une année plus ancienne', () => db('carol').doc('users/carol').update({ analyticsConsent: consentOnNow({ consentVersion: '2025-12-v05' }) })],
    ['actif en v02 : réécriture en v01 sans retrait', () => db('bob').doc('users/bob').update({ analyticsConsent: consentOnNow({ consentVersion: CONSENT_V1 }) })],
    ['actif en v02 : réécriture en version plus récente sans retrait', () => db('bob').doc('users/bob').update({ analyticsConsent: consentOnNow({ consentVersion: '2026-11-v01' }) })],
    ['actif : réécriture à l\'identique avec une nouvelle date', () => db('bob').doc('users/bob').update({ analyticsConsent: consentOnNow({ consentVersion: CONSENT_V2 }) })],
  ]);
  const c = (await stored(admin, 'users/carol')).analyticsConsent;
  assert.equal(c.enabled, false, 'rien n\'a été écrit');
  assert.equal(c.consentVersion, CONSENT_V2);
  // Contrôle : la réactivation à la version enregistrée (v02) est acceptée, donc les refus ci-dessus viennent bien de la version.
  await assertSucceeds(db('carol').doc('users/carol').update({ analyticsConsent: consentOnNow({ consentVersion: CONSENT_V2 }) }));
});

// ---------------------------------------------------------------------------------------------------------
// Champs verrouillés ou inconnus, non-régression
// ---------------------------------------------------------------------------------------------------------
s.tc('AB13', 'mise à jour : termsAcceptedAt, termsVersion, createdAt et tout champ inconnu sont verrouillés', async ({ db, admin }) => {
  await admin((f) => f.doc('users/alice').set(userWithConsent('alice', consentOnSeed())));
  const ref = db('alice').doc('users/alice');
  await assertAllFail([
    ['ré-horodater termsAcceptedAt', () => ref.update({ termsAcceptedAt: serverTs() })],
    ['antidater termsAcceptedAt', () => ref.update({ termsAcceptedAt: Timestamp.fromDate(new Date('2020-01-01T00:00:00Z')) })],
    ['changer termsVersion', () => ref.update({ termsVersion: 'v2' })],
    ['rétrograder termsVersion', () => ref.update({ termsVersion: 'v0' })],
    ['changer la version et la date ensemble', () => ref.update({ termsVersion: 'v2', termsAcceptedAt: serverTs() })],
    ['changer createdAt', () => ref.update({ createdAt: serverTs() })],
    ['champ inconnu : email', () => ref.update({ email: 'alice@example.org' })],
    ['champ inconnu : isPremium', () => ref.update({ isPremium: true })],
    ['champ inconnu : analytics (mauvais nom du bloc)', () => ref.update({ analytics: consentOnNow() })],
    ['champ inconnu glissé avec une modification de profil valide', () => ref.update({ firstName: 'Alicia', phoneNumber: '+33600000000' })],
    // ONB-B : `phone` existe désormais (E.164) ; un numéro au format national reste refusé (voir PH4).
    ['numéro au format national glissé avec une modification de profil valide', () => ref.update({ firstName: 'Alicia', phone: '0600000000' })],
  ]);
  const doc = await stored(admin, 'users/alice');
  assert.equal(doc.termsVersion, 'v1');
  assert.ok(doc.termsAcceptedAt.isEqual(T0));
});

s.tc('AB14', 'non-régression : un profil à six clés (sans bloc) et un profil avec bloc se modifient (prénom, nom, avatar) sans toucher au consentement', async ({ db, admin }) => {
  await admin((f) => f.doc('users/carol').set(userWithConsent('carol', consentOnSeed())));
  // bob : profil à six clés d'origine, sans bloc.
  await assertSucceeds(db('bob').doc('users/bob').update({ firstName: 'Bobby', lastName: 'Martin', avatar: bytes(2000) }));
  await assertSucceeds(db('carol').doc('users/carol').update({ firstName: 'Caro', lastName: 'Durand' }));
  const carol = await stored(admin, 'users/carol');
  assert.deepEqual(carol.analyticsConsent, consentOnSeed());
  assert.equal(carol.firstName, 'Caro');
  assert.equal('analyticsConsent' in (await stored(admin, 'users/bob')), false);
  // Un changement de profil ne permet pas de faire passer un consentement falsifié.
  await assertFails(db('carol').doc('users/carol').update({ firstName: 'Carolina', analyticsConsent: consentOnSeed({ consentedAt: serverTs(), consentVersion: '2026-12-v01' }) }));
});

// ---------------------------------------------------------------------------------------------------------
// Suppression et lecture
// ---------------------------------------------------------------------------------------------------------
s.tc('AB15', 'suppression du profil : par son propriétaire seul (le consentement disparaît avec) ; supprimer un profil inexistant est sans effet', async ({ db, admin }) => {
  await admin((f) => f.doc('users/carol').set(userWithConsent('carol', consentOnSeed())));
  await assertFails(db('bob').doc('users/carol').delete());
  await assertFails(db('alice').doc('users/carol').delete());
  assert.ok(await stored(admin, 'users/carol'), 'le profil de carol existe toujours');
  await assertSucceeds(db('carol').doc('users/carol').delete());
  assert.equal(await stored(admin, 'users/carol'), undefined, 'profil et consentement supprimés');
  // Refus du panneau de consentement d'un nouvel utilisateur social : aucun profil n'a été écrit, le nettoyage reste inoffensif.
  await assertSucceeds(db('eve').doc('users/eve').delete());
  // Après suppression, le même uid peut recréer un profil : nouvel enregistrement, nouvelle acceptation.
  await assertSucceeds(db('carol').doc('users/carol').set(userDoc({ ...profile('carol'), analyticsConsent: consentOff() })));
});

s.tc('AB16', 'lecture du consentement : par son propriétaire seul, jamais par un autre membre, un étranger ni en liste', async ({ db, anon, admin }) => {
  await admin((f) => f.doc('users/carol').set(userWithConsent('carol', consentOnSeed())));
  const snap = await assertSucceeds(db('carol').doc('users/carol').get());
  assert.equal(snap.data().analyticsConsent.enabled, true);
  await assertAllFail([
    ['bob (même Miff) lit le profil de carol', () => db('bob').doc('users/carol').get()],
    ['alice (créatrice) lit le profil de carol', () => db('alice').doc('users/carol').get()],
    ['eve (étrangère) lit le profil de carol', () => db('eve').doc('users/carol').get()],
    ['sans connexion', () => anon().doc('users/carol').get()],
    ['liste des profils par carol', () => db('carol').collection('users').get()],
    ['requête sur le consentement par carol', () => db('carol').collection('users').where('analyticsConsent.enabled', '==', true).get()],
  ]);
});

// ---------------------------------------------------------------------------------------------------------
// Minimisation
// ---------------------------------------------------------------------------------------------------------
s.tc('AB17', 'minimisation : aucun âge, date de naissance, e-mail, autre clé de contact ni position dans le profil (le téléphone E.164 est la seule donnée de contact, ONB-B)', async ({ db }) => {
  const ref = db('eve').doc('users/eve');
  const attempts = Object.entries({
    birthDate: '2010-05-01',
    dateOfBirth: Timestamp.fromDate(new Date('2010-05-01T00:00:00Z')),
    age: 14,
    ageRange: '13-15',
    email: 'eve@example.org',
    phoneNumber: '+33600000000', // ONB-B : la clé `phone` (E.164) est désormais autorisée ; toute autre clé de contact reste refusée
    phone: '0600000000', // forme nationale : refusée (E.164 exigé, voir PH2)
    lat: 48.85,
    location: 'Paris',
  }).map(([key, value]) => [`champ ${key}`, () => ref.set(userDoc({ analyticsConsent: consentOnNow(), [key]: value }))]);
  await assertAllFail(attempts);
  // Même contrôle à l'intérieur du bloc de consentement : seules les quatre clés prévues.
  await assertFails(ref.set(userDoc({ analyticsConsent: consentOnNow({ age: 14 }) })));
  await assertSucceeds(ref.set(userDoc({ analyticsConsent: consentOnNow() })));
});

// ---------------------------------------------------------------------------------------------------------
// Pas de Miff sans profil (a) : décision PM du 2026-10-24 (question ouverte 20)
// ---------------------------------------------------------------------------------------------------------
const M1 = `miffs/${ID.M1}`;
const all = (ops) => Object.values(ops);
const seedProfile = (admin, uid) => admin((f) => f.doc(`users/${uid}`).set(userWithConsent(uid, consentOff())));

s.tc('AB18', 'créer une Miff sans profil (a) : refusé (gratuite et premium) ; avec profil : accepté', async ({ db, admin }) => {
  await assertFails(commit(db('eve'), all(createMiffOps({ uid: 'eve' }))));
  await admin((f) => f.doc('users/dave').delete());
  await assertFails(commit(db('dave'), all(createMiffOps({ uid: 'dave', miffId: ID.NEW2, code: CODE.NEW2, key: ID.NEW2 }))));
  // Contrôle : les mêmes lots, une fois le profil présent.
  await seedProfile(admin, 'eve');
  await assertSucceeds(commit(db('eve'), all(createMiffOps({ uid: 'eve' }))));
  await seedProfile(admin, 'dave');
  await assertSucceeds(commit(db('dave'), all(createMiffOps({ uid: 'dave', miffId: ID.NEW2, code: CODE.NEW2, key: ID.NEW2 }))));
});

s.tc('AB19', 'rejoindre une Miff avec le bon code sans profil (a) : refusé ; avec profil : accepté', async ({ db, admin }) => {
  await assertFails(commit(db('eve'), all(joinOps({ uid: 'eve' }))));
  await seedProfile(admin, 'eve');
  await assertSucceeds(commit(db('eve'), all(joinOps({ uid: 'eve' }))));
});

s.tc('AB20', 'profil créé dans le même lot que la Miff ou l\'adhésion : accepté ; profil invalide ou d\'un autre dans le lot : refusé', async ({ db }) => {
  const eveProfile = (over = {}) => set('users/eve', userDoc({ analyticsConsent: consentOnNow(), ...over }));
  await assertAllFail([
    ['lot Miff + profil sans acceptation (a)', () => commit(db('eve'), [set('users/eve', omit(userDoc(), 'termsAcceptedAt', 'termsVersion')), ...all(createMiffOps({ uid: 'eve' }))])],
    ['lot Miff + profil de CGU datées par le client', () => commit(db('eve'), [eveProfile({ termsAcceptedAt: T0 }), ...all(createMiffOps({ uid: 'eve' }))])],
    ['lot adhésion + profil d\'un autre uid', () => commit(db('eve'), [set('users/frank', userDoc()), ...all(joinOps({ uid: 'eve' }))])],
  ]);
  await assertSucceeds(commit(db('eve'), [eveProfile(), ...all(createMiffOps({ uid: 'eve' }))]));
  await assertSucceeds(commit(db('frank'), [set('users/frank', userDoc()), ...all(joinOps({ uid: 'frank' }))]));
});

s.tc('AB21', 'profil existant mais sans acceptation (a) (document ancien ou injecté) : création de Miff et adhésion refusées', async ({ db, admin }) => {
  // Depuis ONB-B, ces profils injectés portent un numéro valide : sans lui, le refus viendrait du contrôle du téléphone et non de l'absence de (a).
  await admin((f) => f.doc('users/eve').set({ firstName: 'Eve', lastName: 'Test', phone: PHONE, createdAt: T0 }));
  await admin((f) => f.doc('users/frank').set({ firstName: 'Frank', lastName: 'Test', phone: PHONE, createdAt: T0, termsVersion: 'v1' }));
  await assertFails(commit(db('eve'), all(createMiffOps({ uid: 'eve' }))));
  await assertFails(commit(db('frank'), all(joinOps({ uid: 'frank' }))));
  await admin((f) => f.doc('users/eve').set(userWithConsent('eve', consentOff())));
  await assertSucceeds(commit(db('eve'), all(createMiffOps({ uid: 'eve' }))));
});

s.tc('AB22', 'quitter, être retiré, retirer un membre et supprimer la Miff ne dépendent pas du profil (le départ n\'est jamais bloqué)', async ({ db, admin }) => {
  for (const uid of ['alice', 'bob', 'carol']) await admin((f) => f.doc(`users/${uid}`).delete());
  await assertSucceeds(commit(db('bob'), all(leaveOps('bob'))));
  await assertSucceeds(commit(db('alice'), all(removeMemberOps({ target: 'carol' }))));
  await assertSucceeds(
    commit(db('alice'), [
      del(M1),
      set(`miffsDeleted/${ID.M1}`, { deletedAt: serverTs() }),
      del(`joinCodes/${CODE.REGEN}`),
      del(`${M1}/members/alice`),
      del(`${M1}/status/alice`),
      del('users/alice/memberships/free'),
    ]),
  );
});
