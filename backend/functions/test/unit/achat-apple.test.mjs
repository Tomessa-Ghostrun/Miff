// SRV-7 : verification des JWS d'Apple par le VRAI verificateur de la bibliotheque officielle (@apple/app-store-server-library), avec une PKI de TEST qui imite la chaine
// d'Apple (test/helpers/pki-apple.mjs : cles generees a l'execution, jamais ecrites dans le depot). Criteres BK3 et BK7 du ticket : signature invalide, mauvais bundle,
// mauvais environnement, chaine incomplete, algorithme force, environnements non signes (Xcode, LocalTesting) refuses ; lecture d'etat par l'API (simulee) ; erreurs de l'API.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { X509Certificate } from 'node:crypto';
import { creerPki, opensslDisponible } from '../helpers/pki-apple.mjs';

const require = createRequire(import.meta.url);
const A = require('../../lib/achatApple.js');
const racines = require('../../lib/appleRacines.js');

const T = new Date('2026-12-01T12:00:00Z');
const ID_ORIGINE = '2000000123456789';
const ID_APP = 1234567890;
const raison = async (fn) => {
  try {
    await fn();
  } catch (e) {
    return e.raison ?? `autre:${e.message}`;
  }
  return null;
};

let pki;
let autrePki;
before(() => {
  if (!opensslDisponible()) {
    assert.ok(!process.env.CI, "openssl est absent : ce test ne peut pas verifier les JWS (en CI, c'est une erreur, pas une omission)");
    return;
  }
  pki = creerPki();
  autrePki = creerPki();
});
const t = (nom, fn) => test(nom, async (ctx) => {
  if (!pki) return ctx.skip("openssl absent (local seulement)");
  await fn();
});

// --- Racine d'Apple embarquee ---------------------------------------------------------------------------------------------------------

test("racine d'Apple : certificat public « Apple Root CA - G3 », autorite de certification, empreinte SHA-256 connue, valable jusqu'en 2039", () => {
  const [der] = racines.racinesApple();
  const c = new X509Certificate(der);
  assert.match(c.subject, /CN=Apple Root CA - G3/);
  assert.ok(c.ca);
  assert.equal(c.fingerprint256, racines.EMPREINTE_RACINE_APPLE_G3);
  assert.equal(c.fingerprint256, '63:34:3A:BF:B8:9A:6A:03:EB:B5:7E:9B:3F:5F:A7:BE:7C:4F:5C:75:6F:30:17:B3:A8:C4:88:C3:65:3E:91:79');
  assert.ok(new Date(c.validTo).getFullYear() >= 2039);
  assert.equal(racines.racinesApple().length, 1);
});

// --- Formes ---------------------------------------------------------------------------------------------------------------------------

test("environnementDeclare : Production et Sandbox seulement ; Xcode, LocalTesting (non signes) et tout le reste -> nul", () => {
  const jws = (charge) => `e30.${Buffer.from(JSON.stringify(charge)).toString('base64url')}.c2ln`;
  assert.equal(A.environnementDeclare(jws({ environment: 'Production' })), 'Production');
  assert.equal(A.environnementDeclare(jws({ data: { environment: 'Sandbox' } })), 'Sandbox');
  for (const mauvais of [{ environment: 'Xcode' }, { environment: 'LocalTesting' }, { environment: 'production' }, { environment: 1 }, {}, { data: {} }]) assert.equal(A.environnementDeclare(jws(mauvais)), null, JSON.stringify(mauvais));
  for (const mauvais of ['', 'a.b', 'a.!!!.c', 'a.bm9uLWpzb24.c', 'a.b.c.d']) assert.equal(A.environnementDeclare(mauvais), null, mauvais);
});

test("entetePlausible : ES256 et trois certificats exiges ; alg none, HS256, chaine courte ou absente refuses", () => {
  const entete = (e) => `${Buffer.from(JSON.stringify(e)).toString('base64url')}.e30.c2ln`;
  assert.ok(A.entetePlausible(entete({ alg: 'ES256', x5c: ['a', 'b', 'c'] })));
  for (const e of [{ alg: 'none', x5c: ['a', 'b', 'c'] }, { alg: 'HS256', x5c: ['a', 'b', 'c'] }, { alg: 'ES256', x5c: ['a', 'b'] }, { alg: 'ES256' }, { alg: 'ES256', x5c: [1, 2, 3] }, { alg: 'ES256', x5c: ['a', 'b', ''] }]) {
    assert.ok(!A.entetePlausible(entete(e)), JSON.stringify(e));
  }
  assert.ok(!A.entetePlausible('pas-un-jws') && !A.entetePlausible('!!!.e30.c2ln'));
});

// --- Transactions envoyees par l'app (BK3) ---------------------------------------------------------------------------------------------

const transaction = (over = {}) => ({
  originalTransactionId: ID_ORIGINE,
  transactionId: ID_ORIGINE,
  bundleId: 'com.tomessa.miff',
  productId: 'com.tomessa.miff.plus.annual',
  type: 'Auto-Renewable Subscription',
  expiresDate: T.getTime() + 365 * 86_400_000,
  purchaseDate: T.getTime(),
  appAccountToken: '123e4567-e89b-42d3-a456-426614174000',
  inAppOwnershipType: 'PURCHASED',
  environment: 'Sandbox',
  signedDate: T.getTime(),
  ...over,
});
const config = async () => ({ cle: 'cle-de-test', keyId: 'ABCDE12345', issuerId: '123e4567-e89b-42d3-a456-426614174000', appAppleId: ID_APP });
const client = (extra = {}, racinesTest) => A.creerClientApple({ config, racines: racinesTest ?? [pki.racineDer], verificationsEnLigne: false, ...extra });

t('transaction valide (bac a sable) : identite de l\'achat renvoyee', async () => {
  assert.deepEqual(await client().verifierTransactionApp(pki.signer(transaction())), { environnement: 'Sandbox', idTransactionOrigine: ID_ORIGINE });
});

t('transaction valide (production, avec l\'identifiant Apple de l\'app configure)', async () => {
  assert.deepEqual(await client().verifierTransactionApp(pki.signer(transaction({ environment: 'Production' }))), { environnement: 'Production', idTransactionOrigine: ID_ORIGINE });
});

t('production SANS identifiant Apple de l\'app configure : non configure (jamais « accepte sans controle »)', async () => {
  const sans = client({ config: async () => null });
  assert.equal(await raison(() => sans.verifierTransactionApp(pki.signer(transaction({ environment: 'Production' })))), 'non_configure');
  const sansId = client({ config: async () => ({ ...(await config()), appAppleId: null }) });
  assert.equal(await raison(() => sansId.verifierTransactionApp(pki.signer(transaction({ environment: 'Production' })))), 'non_configure');
  // Le bac a sable n'a pas besoin de cet identifiant.
  assert.equal((await sans.verifierTransactionApp(pki.signer(transaction()))).environnement, 'Sandbox');
});

t('signature invalide : contenu modifie apres signature, signee par une AUTRE autorite, algorithme force, chaine incomplete -> signature_invalide', async () => {
  const c = client();
  assert.equal(await raison(() => c.verifierTransactionApp(pki.signer(transaction(), { falsifier: true }))), 'signature_invalide');
  assert.equal(await raison(() => c.verifierTransactionApp(autrePki.signer(transaction()))), 'signature_invalide', 'chaine d\'une autre racine');
  assert.equal(await raison(() => c.verifierTransactionApp(pki.signer(transaction(), { entete: { alg: 'none' } }))), 'signature_invalide');
  assert.equal(await raison(() => c.verifierTransactionApp(pki.signer(transaction(), { entete: { alg: 'HS256' } }))), 'signature_invalide');
  assert.equal(await raison(() => c.verifierTransactionApp(pki.signer(transaction(), { x5c: ['AAAA'] }))), 'signature_invalide', 'chaine incomplete');
  assert.equal(await raison(() => c.verifierTransactionApp('pas.un.jws')), 'signature_invalide');
});

t("chaine sans les extensions d'Apple (feuille et intermediaire) -> signature_invalide", async () => {
  const nue = creerPki({ extensionsApple: false });
  assert.equal(await raison(() => client({}, [nue.racineDer]).verifierTransactionApp(nue.signer(transaction()))), 'signature_invalide');
});

t("mauvais bundle (une transaction d'une AUTRE app) et mauvais environnement -> signature_invalide", async () => {
  const c = client();
  assert.equal(await raison(() => c.verifierTransactionApp(pki.signer(transaction({ bundleId: 'com.autre.app' })))), 'signature_invalide');
  // Un JWS qui declare « Sandbox » est verifie par le verificateur du bac a sable : la donnee signee doit dire la meme chose (elle le dit : valide) ; ici on force un decalage entre
  // l'en-tete (choix du verificateur) et la donnee signee en signant une notification dont `data.environment` differe de celui de la transaction interne.
  const interne = pki.signer(transaction({ environment: 'Production' }));
  const notification = pki.signer({ notificationType: 'DID_RENEW', notificationUUID: '8a7b4f50-1111-4222-8333-444455556666', version: '2.0', signedDate: T.getTime(), data: { environment: 'Sandbox', bundleId: 'com.tomessa.miff', signedTransactionInfo: interne } });
  assert.equal(await raison(() => c.verifierNotification(notification)), 'signature_invalide');
});

t('environnements non signes (Xcode, LocalTesting) : refuses AVANT la bibliotheque (qui sauterait toute verification)', async () => {
  const c = client();
  for (const env of ['Xcode', 'LocalTesting']) {
    assert.equal(await raison(() => c.verifierTransactionApp(pki.signer(transaction({ environment: env })))), 'signature_invalide', env);
  }
});

t("produit inconnu ou identifiant de transaction illisible -> produit_inconnu / signature_invalide", async () => {
  const c = client();
  assert.equal(await raison(() => c.verifierTransactionApp(pki.signer(transaction({ productId: 'com.autre.produit' })))), 'produit_inconnu');
  assert.equal(await raison(() => c.verifierTransactionApp(pki.signer(transaction({ originalTransactionId: '../../x' })))), 'signature_invalide');
});

// --- Notifications serveur V2 (BK7) --------------------------------------------------------------------------------------------------

const notification = (over = {}, data = {}) => ({
  notificationType: 'DID_RENEW',
  subtype: 'BILLING_RECOVERY',
  notificationUUID: '8a7b4f50-1111-4222-8333-444455556666',
  version: '2.0',
  signedDate: T.getTime(),
  data: { environment: 'Sandbox', bundleId: 'com.tomessa.miff', bundleVersion: '1', status: 1, signedTransactionInfo: pki.signer(transaction()), ...data },
  ...over,
});

t('notification valide : identifiant, type, sous-type, environnement et transaction d\'origine', async () => {
  assert.deepEqual(await client().verifierNotification(pki.signer(notification())), {
    uuid: '8a7b4f50-1111-4222-8333-444455556666',
    type: 'DID_RENEW',
    sousType: 'BILLING_RECOVERY',
    environnement: 'Sandbox',
    idTransactionOrigine: ID_ORIGINE,
  });
});

t('notification sans transaction (essai « TEST ») : identifiant d\'origine nul', async () => {
  const n = await client().verifierNotification(pki.signer(notification({ notificationType: 'TEST', subtype: undefined }, { signedTransactionInfo: undefined })));
  assert.equal(n.idTransactionOrigine, null);
  assert.equal(n.type, 'TEST');
});

t("notification : signature invalide, autre autorite, mauvais bundle, mauvais identifiant d'app en production -> refus", async () => {
  const c = client();
  assert.equal(await raison(() => c.verifierNotification(pki.signer(notification(), { falsifier: true }))), 'signature_invalide');
  assert.equal(await raison(() => c.verifierNotification(autrePki.signer(notification()))), 'signature_invalide');
  assert.equal(await raison(() => c.verifierNotification(pki.signer(notification({}, { bundleId: 'com.autre.app' })))), 'signature_invalide');
  const prod = notification({}, { environment: 'Production', appAppleId: ID_APP, signedTransactionInfo: pki.signer(transaction({ environment: 'Production' })) });
  assert.equal((await c.verifierNotification(pki.signer(prod))).environnement, 'Production');
  const autreApp = notification({}, { environment: 'Production', appAppleId: ID_APP + 1, signedTransactionInfo: pki.signer(transaction({ environment: 'Production' })) });
  assert.equal(await raison(() => c.verifierNotification(pki.signer(autreApp))), 'signature_invalide', "identifiant Apple d'une autre app");
});

t('notification dont la transaction interne est invalide (signee par une autre autorite) -> refus', async () => {
  const n = notification({}, { signedTransactionInfo: autrePki.signer(transaction()) });
  assert.equal(await raison(() => client().verifierNotification(pki.signer(n))), 'signature_invalide');
});

// --- Lecture de l'etat par l'API (simulee) --------------------------------------------------------------------------------------------

const reponseApi = (over = {}, ligne = {}) => ({
  environment: 'Sandbox',
  bundleId: 'com.tomessa.miff',
  appAppleId: ID_APP,
  data: [{ subscriptionGroupIdentifier: '21000000', lastTransactions: [{ status: 1, originalTransactionId: ID_ORIGINE, signedTransactionInfo: pki.signer(transaction()), signedRenewalInfo: pki.signer({ autoRenewStatus: 1, environment: 'Sandbox', signedDate: T.getTime() }), ...ligne }] }],
  ...over,
});
const avecApi = (reponse) => client({ fabriqueApi: () => ({ getAllSubscriptionStatuses: async () => (typeof reponse === 'function' ? reponse() : reponse) }) });

t("lireEtat : l'etat vient de l'API (statut + transaction + renouvellement signes) et s'applique aux regles de correspondance", async () => {
  const e = await avecApi(reponseApi()).lireEtat('Sandbox', ID_ORIGINE, T);
  assert.deepEqual({ state: e.state, active: e.active, plan: e.plan, test: e.test, store: e.store, willRenew: e.willRenew }, { state: 'active', active: true, plan: 'yearly', test: true, store: 'app_store', willRenew: true });
  assert.equal(e.idCompte, '123e4567-e89b-42d3-a456-426614174000');
  const grace = await avecApi(reponseApi({}, { status: 4, signedRenewalInfo: pki.signer({ autoRenewStatus: 1, environment: 'Sandbox', gracePeriodExpiresDate: T.getTime() + 5 * 86_400_000, signedDate: T.getTime() }) })).lireEtat('Sandbox', ID_ORIGINE, T);
  assert.deepEqual({ state: grace.state, active: grace.active }, { state: 'grace', active: true });
  const rembourse = await avecApi(reponseApi({}, { status: 5, signedTransactionInfo: pki.signer(transaction({ revocationDate: T.getTime() - 1000, revocationReason: 0 })) })).lireEtat('Sandbox', ID_ORIGINE, T);
  assert.deepEqual({ state: rembourse.state, active: rembourse.active }, { state: 'revoked', active: false });
});

t("lireEtat : un identifiant de transaction illisible, une reponse d'une autre app, une transaction absente ou signee par une autre autorite sont refuses", async () => {
  assert.equal(await raison(() => avecApi(reponseApi()).lireEtat('Sandbox', '../x', T)), 'entree_invalide');
  assert.equal(await raison(() => avecApi(reponseApi({ bundleId: 'com.autre.app' })).lireEtat('Sandbox', ID_ORIGINE, T)), 'signature_invalide');
  assert.equal(await raison(() => avecApi(reponseApi({ data: [] })).lireEtat('Sandbox', ID_ORIGINE, T)), 'achat_inconnu');
  assert.equal(await raison(() => avecApi(reponseApi({}, { originalTransactionId: '999' })).lireEtat('Sandbox', ID_ORIGINE, T)), 'achat_inconnu');
  assert.equal(await raison(() => avecApi(reponseApi({}, { signedTransactionInfo: autrePki.signer(transaction()) })).lireEtat('Sandbox', ID_ORIGINE, T)), 'signature_invalide');
});

t("lireEtat : erreurs de l'API Apple -> 404 achat_inconnu, 401/403 acces_refuse, 429 et 5xx passageres ; configuration absente -> non_configure", async () => {
  const erreur = (statut) => () => { throw Object.assign(new Error('api'), { httpStatusCode: statut }); };
  assert.equal(await raison(() => avecApi(erreur(404)).lireEtat('Sandbox', ID_ORIGINE, T)), 'achat_inconnu');
  for (const s of [401, 403]) assert.equal(await raison(() => avecApi(erreur(s)).lireEtat('Sandbox', ID_ORIGINE, T)), 'acces_refuse');
  for (const s of [429, 500, 503]) {
    try {
      await avecApi(erreur(s)).lireEtat('Sandbox', ID_ORIGINE, T);
      assert.fail('devait lever');
    } catch (e) {
      assert.equal(e.raison, 'indisponible');
      assert.equal(e.transitoire, true, String(s));
    }
  }
  assert.equal(await raison(() => client({ config: async () => null }).lireEtat('Sandbox', ID_ORIGINE, T)), 'non_configure');
});

t("production : si le PM corrige `appAppleId` dans la configuration, l'ancien identifiant n'est plus utilise (un verificateur par identifiant d'app)", async () => {
  let id = ID_APP + 1; // mauvais identifiant saisi au depart
  const c = A.creerClientApple({ config: async () => ({ cle: 'x', keyId: 'ABCDE12345', issuerId: '123e4567-e89b-42d3-a456-426614174000', appAppleId: id }), racines: [pki.racineDer], verificationsEnLigne: false });
  const n = pki.signer({ notificationType: 'DID_RENEW', notificationUUID: '8a7b4f50-1111-4222-8333-444455556666', version: '2.0', signedDate: T.getTime(), data: { environment: 'Production', appAppleId: ID_APP, bundleId: 'com.tomessa.miff', signedTransactionInfo: pki.signer(transaction({ environment: 'Production' })) } });
  assert.equal(await raison(() => c.verifierNotification(n)), 'signature_invalide');
  id = ID_APP; // corrige
  assert.equal((await c.verifierNotification(n)).environnement, 'Production');
});
