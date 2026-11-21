// SRV-7 : regles PURES du premium reel (src/achatRegles.ts), sans reseau ni Firestore. Critere BK5 du ticket (une fixture par etat et par store), BK6 (decodage des messages
// Google), BK8 (remboursement), BK10 (accord « a la main » respecte), plus les formes d'entree (injection d'identifiant).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const R = require('../../lib/achatRegles.js');

const T = new Date('2026-12-01T12:00:00Z');
const plus = (jours) => new Date(T.getTime() + jours * 86_400_000);
const iso = (d) => d.toISOString();
const raison = (fn) => {
  try {
    fn();
  } catch (e) {
    return e.raison ?? `autre:${e.message}`;
  }
  return null;
};

// --- Cles et formes d'entree -----------------------------------------------------------------------------------------------------------

test('cleAchat : SHA-256 en hexadecimal, deterministe, distincte selon le store, sans trace de la reference', () => {
  const a = R.cleAchat('google_play', 'jeton-secret-0123456789');
  assert.match(a, /^[0-9a-f]{64}$/);
  assert.equal(a, R.cleAchat('google_play', 'jeton-secret-0123456789'));
  assert.notEqual(a, R.cleAchat('app_store', 'jeton-secret-0123456789'));
  assert.notEqual(a, R.cleAchat('google_play', 'jeton-secret-0123456780'));
  assert.ok(!a.includes('jeton'));
});

test("formes d'entree : un jeton Google ne peut ni contenir d'espace, de saut de ligne ni etre trop court ou trop long ; les caracteres d'URL restent acceptes (ils sont encodes)", () => {
  assert.ok(R.jetonGoogleValide('kpdlbgmpjflhhldfpdgnhaii.AO-J1OxAbCdEfGhIjKlMnOpQrStUvWxYz_0123456789'));
  assert.ok(R.jetonGoogleValide('a/b?c#d..e%2F0123456789'), 'tout caractere imprimable est permis : il est encode avant d\'entrer dans une URL (test du client Google)');
  for (const mauvais of ['', 'court', 'a'.repeat(15), 'a'.repeat(1501), 'jeton avec espace 1234', 'jeton\nsaut-de-ligne-123', 'jetonéaccentue-123456', 42, null, undefined, {}]) {
    assert.ok(!R.jetonGoogleValide(mauvais), `devrait etre refuse : ${String(mauvais)}`);
  }
});

test("formes d'entree : une transaction signee (JWS) a trois segments base64url et 16 Kio au plus", () => {
  assert.ok(R.jwsValide('eyJhbGciOiJFUzI1NiJ9.eyJhIjoxfQ.c2lnbmF0dXJl'));
  for (const mauvais of ['', 'a.b', 'aaaa.bbbb', 'aaaa.bbbb.cc dd', 'aaaa.bbbb.cccc.dddd', `aaaa.${'b'.repeat(16 * 1024)}.cccc`, 5, null]) assert.ok(!R.jwsValide(mauvais), String(mauvais).slice(0, 30));
  assert.ok(R.idTransactionAppleValide('2000000123456789'));
  for (const mauvais of ['', 'abc', '12 34', '../1', '1'.repeat(21), 12]) assert.ok(!R.idTransactionAppleValide(mauvais));
  assert.ok(R.uuidValide('aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'));
  assert.ok(!R.uuidValide('aaaaaaaabbbb4ccc8dddeeeeeeeeeeee') && !R.uuidValide('../x'));
});

// --- Google : une fixture par etat (BK5) -----------------------------------------------------------------------------------------------

const google = (over = {}, ligne = {}) => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
  lineItems: [
    {
      productId: 'miff_plus',
      expiryTime: iso(plus(30)),
      autoRenewingPlan: { autoRenewEnabled: true },
      offerDetails: { basePlanId: 'mensuel' },
      ...ligne,
    },
  ],
  externalAccountIdentifiers: { obfuscatedExternalAccountId: 'AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE' },
  acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
  ...over,
});

test('Google : actif (mensuel) -> active, droit accorde, echeance du store, renouvellement', () => {
  const e = R.etatDepuisGoogle(google(), T);
  assert.deepEqual(
    { state: e.state, active: e.active, trial: e.trial, willRenew: e.willRenew, plan: e.plan, test: e.test, store: e.store, produit: e.produit },
    { state: 'active', active: true, trial: false, willRenew: true, plan: 'monthly', test: false, store: 'google_play', produit: 'miff_plus' },
  );
  assert.equal(e.expiresAt.getTime(), plus(30).getTime());
  assert.equal(e.idCompte, 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'identifiant d\'achat compare en minuscules');
  assert.equal(e.observeA, T.getTime());
  assert.equal(e.accuser, false);
});

test("Google : actif avec phase d'essai gratuit (annuel) -> trial", () => {
  const e = R.etatDepuisGoogle(google({}, { offerDetails: { basePlanId: 'annuel' }, offerPhase: { freeTrial: true } }), T);
  assert.deepEqual({ state: e.state, active: e.active, trial: e.trial, plan: e.plan }, { state: 'trial', active: true, trial: true, plan: 'yearly' });
});

test('Google : annule non expire -> canceled, droit accorde jusqu\'a l\'echeance, plus de renouvellement', () => {
  const e = R.etatDepuisGoogle(google({ subscriptionState: 'SUBSCRIPTION_STATE_CANCELED' }, { autoRenewingPlan: { autoRenewEnabled: false } }), T);
  assert.deepEqual({ state: e.state, active: e.active, willRenew: e.willRenew }, { state: 'canceled', active: true, willRenew: false });
});

test('Google : periode de grace -> grace, droit CONSERVE (decision Q4)', () => {
  const e = R.etatDepuisGoogle(google({ subscriptionState: 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD' }, { expiryTime: iso(plus(5)) }), T);
  assert.deepEqual({ state: e.state, active: e.active }, { state: 'grace', active: true });
  assert.equal(e.expiresAt.getTime(), plus(5).getTime());
});

test('Google : suspendu (on hold) et en pause -> on_hold, droit COUPE, echeance dans le passe', () => {
  for (const s of ['SUBSCRIPTION_STATE_ON_HOLD', 'SUBSCRIPTION_STATE_PAUSED']) {
    const e = R.etatDepuisGoogle(google({ subscriptionState: s }, { expiryTime: iso(plus(-3)) }), T);
    assert.deepEqual({ state: e.state, active: e.active }, { state: 'on_hold', active: false }, s);
  }
});

test('Google : expire -> expired, aucun droit', () => {
  const e = R.etatDepuisGoogle(google({ subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED' }, { expiryTime: iso(plus(-1)) }), T);
  assert.deepEqual({ state: e.state, active: e.active, trial: e.trial, willRenew: e.willRenew }, { state: 'expired', active: false, trial: false, willRenew: false });
});

test('Google : en attente de paiement (et attente annulee) -> pending, aucun droit, pas d\'echeance, jamais acquitte', () => {
  for (const s of ['SUBSCRIPTION_STATE_PENDING', 'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED']) {
    const e = R.etatDepuisGoogle(google({ subscriptionState: s, acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' }, { expiryTime: undefined }), T);
    assert.deepEqual({ state: e.state, active: e.active, expiresAt: e.expiresAt, accuser: e.accuser }, { state: 'pending', active: false, expiresAt: null, accuser: false }, s);
  }
});

test('Google : remboursement / revocation (indice du message type 12 ou « achat annule ») -> revoked, acces coupe TOUT DE SUITE (BK8, decision Q3), meme si l\'API dit encore actif', () => {
  const e = R.etatDepuisGoogle(google(), T, { revoque: true });
  assert.deepEqual({ state: e.state, active: e.active, willRenew: e.willRenew, trial: e.trial }, { state: 'revoked', active: false, willRenew: false, trial: false });
  assert.equal(e.expiresAt.getTime(), T.getTime());
});

test("Google : un droit « actif » dont l'echeance est deja passee (lecture perimee) n'est pas un droit", () => {
  const e = R.etatDepuisGoogle(google({}, { expiryTime: iso(plus(-1)) }), T);
  assert.deepEqual({ state: e.state, active: e.active }, { state: 'expired', active: false });
});

test('Google : achat de test marque ; achat a acquitter signale ; achat precedent (changement de forfait) signale ; forfait inconnu = plan nul', () => {
  const e = R.etatDepuisGoogle(google({ testPurchase: {}, acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING', linkedPurchaseToken: 'ancien-jeton-aaaaaaaaaa' }, { offerDetails: { basePlanId: 'autre' } }), T);
  assert.deepEqual({ test: e.test, accuser: e.accuser, lien: e.lienPrecedent, plan: e.plan }, { test: true, accuser: true, lien: 'ancien-jeton-aaaaaaaaaa', plan: null });
});

test("Google : sans identifiant d'achat (achat fait hors de l'app) -> idCompte nul", () => {
  const e = R.etatDepuisGoogle(google({ externalAccountIdentifiers: undefined }), T);
  assert.equal(e.idCompte, null);
});

test("Google : un autre produit, un etat inconnu, une echeance manquante ou illisible sont refuses", () => {
  assert.equal(raison(() => R.etatDepuisGoogle(google({}, { productId: 'autre_produit' }), T)), 'produit_inconnu');
  assert.equal(raison(() => R.etatDepuisGoogle(google({ lineItems: [] }), T)), 'produit_inconnu');
  assert.equal(raison(() => R.etatDepuisGoogle(google({ subscriptionState: 'SUBSCRIPTION_STATE_UNSPECIFIED' }), T)), 'reponse_invalide');
  assert.equal(raison(() => R.etatDepuisGoogle(google({ subscriptionState: undefined }), T)), 'reponse_invalide');
  assert.equal(raison(() => R.etatDepuisGoogle(google({}, { expiryTime: undefined }), T)), 'reponse_invalide');
  assert.equal(raison(() => R.etatDepuisGoogle(google({}, { expiryTime: 'pas une date' }), T)), 'reponse_invalide');
});

test('Google : plusieurs lignes -> celle qui expire en dernier ; les autres produits sont ignores', () => {
  const r = google({ lineItems: [{ productId: 'autre', expiryTime: iso(plus(900)) }, { productId: 'miff_plus', expiryTime: iso(plus(10)), autoRenewingPlan: { autoRenewEnabled: true } }, { productId: 'miff_plus', expiryTime: iso(plus(20)), autoRenewingPlan: { autoRenewEnabled: true } }] });
  assert.equal(R.etatDepuisGoogle(r, T).expiresAt.getTime(), plus(20).getTime());
});

// --- Apple : une fixture par etat (BK5) --------------------------------------------------------------------------------------------------

const apple = (over = {}) => ({
  originalTransactionId: '2000000123456789',
  productId: 'com.tomessa.miff.plus.annual',
  type: 'Auto-Renewable Subscription',
  expiresDate: plus(365).getTime(),
  appAccountToken: 'AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE',
  inAppOwnershipType: 'PURCHASED',
  environment: 'Production',
  signedDate: T.getTime() - 1000,
  ...over,
});
const renouvellement = (over = {}) => ({ autoRenewStatus: 1, signedDate: T.getTime() - 500, ...over });

test("Apple : statut 1 (actif) annuel -> active ; avec offre d'introduction -> trial ; renouvellement coupe -> canceled", () => {
  let e = R.etatDepuisApple(1, apple(), renouvellement(), T);
  assert.deepEqual({ state: e.state, active: e.active, trial: e.trial, willRenew: e.willRenew, plan: e.plan, test: e.test, store: e.store }, { state: 'active', active: true, trial: false, willRenew: true, plan: 'yearly', test: false, store: 'app_store' });
  assert.equal(e.idCompte, 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee');
  assert.equal(e.observeA, T.getTime() - 500, "heure signee par Apple, la plus recente des deux");
  e = R.etatDepuisApple(1, apple({ offerType: 1, offerDiscountType: 'FREE_TRIAL', expiresDate: plus(7).getTime() }), renouvellement(), T);
  assert.deepEqual({ state: e.state, trial: e.trial, active: e.active }, { state: 'trial', trial: true, active: true });
  e = R.etatDepuisApple(1, apple(), renouvellement({ autoRenewStatus: 0 }), T);
  assert.deepEqual({ state: e.state, active: e.active, willRenew: e.willRenew }, { state: 'canceled', active: true, willRenew: false });
  e = R.etatDepuisApple(1, apple({ productId: 'com.tomessa.miff.plus.monthly' }), null, T);
  assert.equal(e.plan, 'monthly');
  assert.equal(e.state, 'canceled', "sans information de renouvellement : pas de renouvellement affirme");
});

test('Apple : statut 4 (periode de grace) -> grace, droit conserve jusqu\'a la fin de la grace ; grace deja finie -> on_hold', () => {
  let e = R.etatDepuisApple(4, apple({ expiresDate: plus(-1).getTime() }), renouvellement({ gracePeriodExpiresDate: plus(6).getTime() }), T);
  assert.deepEqual({ state: e.state, active: e.active }, { state: 'grace', active: true });
  assert.equal(e.expiresAt.getTime(), plus(6).getTime());
  e = R.etatDepuisApple(4, apple({ expiresDate: plus(-9).getTime() }), renouvellement({ gracePeriodExpiresDate: plus(-2).getTime() }), T);
  assert.deepEqual({ state: e.state, active: e.active }, { state: 'on_hold', active: false });
});

test('Apple : statut 3 (nouvelle tentative de facturation) -> on_hold ; statut 2 -> expired', () => {
  let e = R.etatDepuisApple(3, apple({ expiresDate: plus(-2).getTime() }), renouvellement(), T);
  assert.deepEqual({ state: e.state, active: e.active }, { state: 'on_hold', active: false });
  e = R.etatDepuisApple(2, apple({ expiresDate: plus(-2).getTime() }), renouvellement({ autoRenewStatus: 0 }), T);
  assert.deepEqual({ state: e.state, active: e.active, willRenew: e.willRenew }, { state: 'expired', active: false, willRenew: false });
});

test('Apple : statut 5 ou date de revocation -> revoked, acces coupe (BK8) ; REFUND_REVERSED = Apple repasse le statut a 1 sans revocation : acces retabli par la relecture', () => {
  let e = R.etatDepuisApple(5, apple({ revocationDate: plus(-1).getTime() }), renouvellement(), T);
  assert.deepEqual({ state: e.state, active: e.active }, { state: 'revoked', active: false });
  assert.equal(e.expiresAt.getTime(), plus(-1).getTime());
  e = R.etatDepuisApple(1, apple({ revocationDate: plus(-1).getTime() }), renouvellement(), T);
  assert.equal(e.state, 'revoked', 'une date de revocation suffit, meme avec le statut 1');
  e = R.etatDepuisApple(1, apple(), renouvellement(), T);
  assert.deepEqual({ state: e.state, active: e.active }, { state: 'active', active: true });
});

test("Apple : statut 1 mais echeance passee (lecture perimee) -> expired ; bac a sable marque ; partage en famille signale ; produit ou type inconnu refuse ; statut inconnu refuse", () => {
  assert.equal(R.etatDepuisApple(1, apple({ expiresDate: plus(-1).getTime() }), renouvellement(), T).state, 'expired');
  assert.equal(R.etatDepuisApple(1, apple({ environment: 'Sandbox' }), renouvellement(), T).test, true);
  assert.equal(R.etatDepuisApple(1, apple({ inAppOwnershipType: 'FAMILY_SHARED' }), renouvellement(), T).famille, true);
  assert.equal(raison(() => R.etatDepuisApple(1, apple({ productId: 'autre' }), renouvellement(), T)), 'produit_inconnu');
  assert.equal(raison(() => R.etatDepuisApple(1, apple({ type: 'Consumable' }), renouvellement(), T)), 'produit_inconnu');
  assert.equal(raison(() => R.etatDepuisApple(9, apple(), renouvellement(), T)), 'reponse_invalide');
  assert.equal(raison(() => R.etatDepuisApple(1, apple({ expiresDate: undefined }), renouvellement(), T)), 'reponse_invalide');
});

// --- Message Pub/Sub de Google (BK6) -----------------------------------------------------------------------------------------------------

const message = (doc) => Buffer.from(JSON.stringify(doc)).toString('base64');
const JETON = 'kpdlbgmpjflhhldfpdgnhaii.AO-J1OxAbCdEfGhIjKlMnOpQrStUvWxYz_0123456789';

test('decoderMessageGoogle : abonnement, achat annule, test, autre package, autres genres, non conforme', () => {
  assert.deepEqual(R.decoderMessageGoogle(message({ version: '1.0', packageName: 'com.tomessa.miff', subscriptionNotification: { version: '1.0', notificationType: 4, purchaseToken: JETON } })), { sorte: 'abonnement', jeton: JETON, type: 4 });
  assert.deepEqual(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', voidedPurchaseNotification: { purchaseToken: JETON, orderId: 'GS.0000', productType: 1, refundType: 1 } })), { sorte: 'annule', jeton: JETON });
  assert.deepEqual(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', testNotification: { version: '1.0' } })), { sorte: 'test' });
  assert.deepEqual(R.decoderMessageGoogle(message({ packageName: 'com.autre.app', subscriptionNotification: { notificationType: 4, purchaseToken: JETON } })), { sorte: 'autre_package' });
  assert.deepEqual(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', voidedPurchaseNotification: { purchaseToken: JETON, productType: 2 } })), { sorte: 'ignore', motif: 'type_ignore' });
  assert.deepEqual(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', oneTimeProductNotification: { notificationType: 1, purchaseToken: JETON, sku: 'x' } })), { sorte: 'ignore', motif: 'type_ignore' });
  assert.deepEqual(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', pendingRefundReviewNotification: {} })), { sorte: 'ignore', motif: 'type_ignore' });
  for (const mauvais of [undefined, null, 5, '', '!!!pas-du-base64!!!', Buffer.from('pas du json').toString('base64'), message([1, 2]), message('texte'), 'a'.repeat(20_000)]) {
    assert.equal(R.decoderMessageGoogle(mauvais).sorte === 'ignore' || R.decoderMessageGoogle(mauvais).sorte === 'autre_package', true, `ne doit jamais lever : ${String(mauvais).slice(0, 20)}`);
  }
});

test("decoderMessageGoogle : un jeton d'achat invalide (injection, vide, trop court) ou un type non entier est refuse ; un type inconnu est accepte (la relecture est sans effet)", () => {
  for (const jeton of ['court', 'avec espace dans le jeton', 'saut\nde-ligne-0123456789', 5, undefined, '']) {
    assert.deepEqual(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', subscriptionNotification: { notificationType: 4, purchaseToken: jeton } })), { sorte: 'ignore', motif: 'entree_invalide' }, String(jeton));
  }
  assert.equal(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', subscriptionNotification: { notificationType: '4', purchaseToken: JETON } })).sorte, 'ignore');
  assert.equal(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', subscriptionNotification: { notificationType: 4.5, purchaseToken: JETON } })).sorte, 'ignore');
  assert.equal(R.decoderMessageGoogle(message({ packageName: 'com.tomessa.miff', subscriptionNotification: { notificationType: 999, purchaseToken: JETON } })).sorte, 'abonnement');
  assert.equal(R.TYPE_GOOGLE_REVOQUE, 12);
});

// --- Acces a ecrire (BK10) ------------------------------------------------------------------------------------------------------------------

const achat = (over = {}) => ({ cle: 'a'.repeat(64), store: 'google_play', state: 'active', active: true, expiresAt: plus(30), plan: 'monthly', trial: false, willRenew: true, test: false, ...over });

test('calculerAcces : aucun achat, ou seulement des achats en attente -> rien a ecrire', () => {
  assert.deepEqual(R.calculerAcces([], null, T), { ecrire: false, raison: 'aucun_achat' });
  assert.deepEqual(R.calculerAcces([achat({ state: 'pending', active: false, expiresAt: null })], null, T), { ecrire: false, raison: 'aucun_achat' });
});

test('calculerAcces : un achat actif -> acces ecrit avec les 9 champs, source du store', () => {
  const d = R.calculerAcces([achat({ trial: true, state: 'trial', plan: 'yearly' })], null, T);
  assert.equal(d.ecrire, true);
  assert.deepEqual(Object.keys(d.acces).sort(), ['active', 'expiresAt', 'plan', 'source', 'state', 'test', 'trial', 'updatedAt', 'willRenew']);
  assert.deepEqual({ active: d.acces.active, source: d.acces.source, state: d.acces.state, plan: d.acces.plan, trial: d.acces.trial, willRenew: d.acces.willRenew }, { active: true, source: 'google_play', state: 'trial', plan: 'yearly', trial: true, willRenew: true });
  assert.equal(d.acces.expiresAt.getTime(), plus(30).getTime());
  assert.equal(R.calculerAcces([achat({ store: 'app_store' })], null, T).acces.source, 'app_store');
});

test('calculerAcces : rembourse / suspendu / expire -> active=false, etat dit la verite, echeance conservee', () => {
  for (const [state, expiresAt] of [['revoked', T], ['on_hold', plus(-3)], ['expired', plus(-1)]]) {
    const d = R.calculerAcces([achat({ state, active: false, expiresAt, trial: false, willRenew: false })], null, T);
    assert.equal(d.ecrire, true);
    assert.deepEqual({ active: d.acces.active, state: d.acces.state }, { active: false, state }, state);
  }
});

test("calculerAcces : un achat « actif » dont l'echeance est passee n'accorde plus rien et devient expired", () => {
  const d = R.calculerAcces([achat({ expiresAt: plus(-0.01) })], null, T);
  assert.deepEqual({ active: d.acces.active, state: d.acces.state, trial: d.acces.trial, willRenew: d.acces.willRenew }, { active: false, state: 'expired', trial: false, willRenew: false });
});

test("calculerAcces : deux achats (Google et Apple) -> celui qui donne le droit le plus longtemps ; un actif l'emporte sur un expire plus recent", () => {
  const google = achat({ cle: 'b'.repeat(64), expiresAt: plus(10) });
  const apple = achat({ cle: 'c'.repeat(64), store: 'app_store', expiresAt: plus(50), plan: 'yearly' });
  assert.equal(R.calculerAcces([google, apple], null, T).acces.source, 'app_store');
  assert.equal(R.calculerAcces([apple, google], null, T).acces.source, 'app_store', 'ordre indifferent');
  const expire = achat({ cle: 'd'.repeat(64), store: 'app_store', state: 'expired', active: false, expiresAt: plus(-1) });
  assert.equal(R.calculerAcces([expire, google], null, T).acces.source, 'google_play');
});

test("calculerAcces : un accord « a la main » (4 champs) encore valide n'est PAS ecrase par un achat a l'echeance plus courte ou egale (BK10)", () => {
  const manuel = { active: true, expiresAt: plus(100), source: 'manual', updatedAt: T };
  assert.deepEqual(R.calculerAcces([achat({ expiresAt: plus(30) })], manuel, T), { ecrire: false, raison: 'manuel_conserve' });
  assert.deepEqual(R.calculerAcces([achat({ expiresAt: plus(100) })], manuel, T), { ecrire: false, raison: 'manuel_conserve' });
  assert.deepEqual(R.calculerAcces([achat({ state: 'expired', active: false, expiresAt: plus(-1) })], manuel, T), { ecrire: false, raison: 'manuel_conserve' }, 'un achat expire ne remplace pas un accord manuel valide');
});

test("calculerAcces : l'accord manuel est remplace si le store va PLUS LOIN, ou s'il est expire ou desactive", () => {
  const manuel = { active: true, expiresAt: plus(10), source: 'manual', updatedAt: T };
  assert.equal(R.calculerAcces([achat({ expiresAt: plus(30) })], manuel, T).ecrire, true);
  assert.equal(R.calculerAcces([achat({ expiresAt: plus(30) })], { ...manuel, expiresAt: plus(-1) }, T).ecrire, true);
  assert.equal(R.calculerAcces([achat({ expiresAt: plus(30) })], { ...manuel, active: false }, T).ecrire, true);
  assert.equal(R.calculerAcces([achat({ expiresAt: plus(30) })], { ...manuel, active: 'true' }, T).ecrire, true, "un champ mal type n'est pas un accord valide");
});

test('calculerAcces : aucune ecriture si rien ne change (economise une ecriture) ; une difference d\'un seul champ ecrit', () => {
  const d = R.calculerAcces([achat()], null, T);
  const actuel = { ...d.acces };
  assert.deepEqual(R.calculerAcces([achat()], actuel, plus(0.001)), { ecrire: false, raison: 'inchange' });
  for (const [champ, valeur] of [['willRenew', false], ['state', 'canceled'], ['plan', 'yearly'], ['test', true], ['expiresAt', plus(31)], ['source', 'app_store'], ['trial', true], ['active', false]]) {
    assert.equal(R.calculerAcces([achat()], { ...actuel, [champ]: valeur }, T).ecrire, true, champ);
  }
});

test("calculerAcces : le drapeau test suit l'achat retenu", () => {
  assert.equal(R.calculerAcces([achat({ test: true })], null, T).acces.test, true);
});
