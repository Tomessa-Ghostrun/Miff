// SRV-7 : client Google Play (src/achatGoogle.ts) et configuration Apple (src/configApple.ts) avec un faux `fetch` et de faux jetons : aucun reseau, aucun secret.
// Criteres : URL exacte et jeton d'achat ENCODE (injection de chemin), correspondance des statuts HTTP, repli par usurpation d'identite de soi-meme (hypothese BK9),
// accuse de reception, reponse reduite aux champs utilises, cle Apple lue a un seul endroit et jamais dans une erreur.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const G = require('../../lib/achatGoogle.js');
const C = require('../../lib/configApple.js');

const JETON = 'kpdlbgmpjflhhldfpdgnhaii.AO-J1OxAbCdEfGhIjKlMnOpQrStUvWxYz_0123456789';
const rep = (statut, corps) => new Response(corps === undefined ? null : JSON.stringify(corps), { status: statut, headers: { 'Content-Type': 'application/json' } });
const raison = async (fn) => {
  try {
    await fn();
  } catch (e) {
    return { raison: e.raison ?? `autre:${e.message}`, transitoire: e.transitoire };
  }
  return null;
};

/** Faux fetch : `reponses` = file de reponses (ou fonction) ; garde chaque appel. */
function fauxFetch(reponses) {
  const f = async (url, init) => {
    f.appels.push({ url: String(url), init });
    const r = typeof reponses === 'function' ? reponses(f.appels.length, String(url), init) : reponses[Math.min(f.appels.length - 1, reponses.length - 1)];
    if (r instanceof Error) throw r;
    // Une réponse HTTP ne se lit qu'une fois : chaque appel reçoit une copie (la même réponse peut être servie plusieurs fois).
    return r instanceof Response ? r.clone() : r;
  };
  f.appels = [];
  return f;
}
/** Faux fournisseur de jetons : `refuseDirect` simule « la portee n'est pas accordee » ; `impersonation` indique si le repli a ete utilise. */
function fauxJetons({ erreurDirect = false, erreurImpersonation = false } = {}) {
  const j = {
    appels: [],
    async jeton(portee, impersonation = false) {
      j.appels.push({ portee, impersonation });
      if (impersonation ? erreurImpersonation : erreurDirect) throw new Error('refus');
      return impersonation ? 'jeton-usurpe' : 'jeton-direct';
    },
  };
  return j;
}

// --- lire ----------------------------------------------------------------------------------------------------------------------------

test('lire : URL exacte (package fixe, jeton ENCODE), jeton Bearer, delai maximal, aucune autre donnee', async () => {
  const f = fauxFetch([rep(200, { subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', lineItems: [] })]);
  const c = G.creerClientGoogle({ fetch: f, jetons: fauxJetons() });
  await c.lire(JETON);
  assert.equal(f.appels.length, 1);
  assert.equal(f.appels[0].url, `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.tomessa.miff/purchases/subscriptionsv2/tokens/${encodeURIComponent(JETON)}`);
  assert.equal(f.appels[0].init.method, 'GET');
  assert.equal(f.appels[0].init.headers.Authorization, 'Bearer jeton-direct');
  assert.ok(f.appels[0].init.signal, 'delai maximal');
  assert.equal(f.appels[0].init.body, undefined);
});

test("lire : un jeton qui contient /, ?, #, %, .. ou des espaces ne peut pas changer le chemin (encodage) ou est refuse", async () => {
  const f = fauxFetch([rep(404)]);
  const c = G.creerClientGoogle({ fetch: f, jetons: fauxJetons() });
  const piege = '../../../secrets/apple?x=1#frag%2e%2e/0123456789';
  await raison(() => c.lire(piege));
  const url = f.appels[0].url;
  assert.ok(url.endsWith(`/tokens/${encodeURIComponent(piege)}`));
  assert.ok(!url.slice(url.indexOf('/tokens/') + 8).includes('/'), 'aucun separateur de chemin non encode');
  assert.ok(!url.includes('?') && !url.includes('#'));
  assert.equal((await raison(() => c.lire('avec espace 0123456789'))).raison, 'jeton_invalide');
  assert.equal((await raison(() => c.lire('court'))).raison, 'jeton_invalide');
  assert.equal(f.appels.length, 1, 'un jeton invalide n\'atteint jamais le reseau');
});

test('lire : correspondance des statuts HTTP (400/404/410 achat inconnu, 401/403 acces refuse, 429 et 5xx passagers, reponse illisible)', async () => {
  const cas = [[400, 'achat_inconnu', undefined], [404, 'achat_inconnu', undefined], [410, 'achat_inconnu', undefined], [429, 'indisponible', true], [500, 'indisponible', true], [503, 'indisponible', true], [418, 'reponse_invalide', undefined]];
  for (const [statut, attendu, transitoire] of cas) {
    const r = await raison(() => G.creerClientGoogle({ fetch: fauxFetch([rep(statut)]), jetons: fauxJetons() }).lire(JETON));
    assert.equal(r.raison, attendu, String(statut));
    assert.equal(Boolean(r.transitoire), transitoire === true, String(statut));
  }
  const illisible = await raison(() => G.creerClientGoogle({ fetch: fauxFetch([new Response('pas du json', { status: 200 })]), jetons: fauxJetons() }).lire(JETON));
  assert.equal(illisible.raison, 'reponse_invalide');
  const reseau = await raison(() => G.creerClientGoogle({ fetch: fauxFetch([new Error('ECONNRESET')]), jetons: fauxJetons() }).lire(JETON));
  assert.deepEqual(reseau, { raison: 'indisponible', transitoire: true });
});

test("lire : acces refuse par Google (401/403) -> REPLI par usurpation d'identite de soi-meme ; si ca marche, le repli est retenu (plus d'essai direct)", async () => {
  const jetons = fauxJetons();
  const f = fauxFetch((n) => (n === 1 ? rep(403) : rep(200, { subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', lineItems: [] })));
  const c = G.creerClientGoogle({ fetch: f, jetons });
  await c.lire(JETON);
  assert.deepEqual(jetons.appels.map((a) => a.impersonation), [false, true]);
  assert.equal(f.appels[1].init.headers.Authorization, 'Bearer jeton-usurpe');
  await c.lire(JETON);
  assert.deepEqual(jetons.appels.map((a) => a.impersonation), [false, true, true], 'repli memorise');
});

test("lire : refus persistant (direct ET repli) -> acces_refuse, sans boucle ; echec d'obtention du jeton direct -> passager, du jeton usurpe -> acces_refuse", async () => {
  const jetons = fauxJetons();
  const f = fauxFetch([rep(401), rep(401)]);
  const r = await raison(() => G.creerClientGoogle({ fetch: f, jetons }).lire(JETON));
  assert.equal(r.raison, 'acces_refuse');
  assert.equal(f.appels.length, 2, 'un seul repli, pas de boucle');
  const direct = await raison(() => G.creerClientGoogle({ fetch: fauxFetch([rep(200, {})]), jetons: fauxJetons({ erreurDirect: true }) }).lire(JETON));
  assert.deepEqual(direct, { raison: 'indisponible', transitoire: true });
  const usurpe = await raison(() => G.creerClientGoogle({ fetch: fauxFetch([rep(403)]), jetons: fauxJetons({ erreurImpersonation: true }) }).lire(JETON));
  assert.equal(usurpe.raison, 'acces_refuse');
});

test("lireReponseGoogle : ne garde QUE les champs utilises, verifie les types, ignore le reste (donnees d'abonnement, e-mail d'un compte Google...)", async () => {
  const r = G.lireReponseGoogle({
    kind: 'androidpublisher#subscriptionPurchaseV2',
    regionCode: 'FR',
    subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
    lineItems: [{ productId: 'miff_plus', expiryTime: '2026-12-31T00:00:00Z', latestSuccessfulOrderId: 'GPA.1234-5678', autoRenewingPlan: { autoRenewEnabled: true, recurringPrice: { currencyCode: 'EUR', units: '59' } }, offerDetails: { basePlanId: 'annuel', offerId: 'essai' }, offerPhase: { freeTrial: {} } }],
    externalAccountIdentifiers: { obfuscatedExternalAccountId: 'abc', externalAccountId: 'x' },
    subscribeWithGoogleInfo: { emailAddress: 'a@b.c', givenName: 'A' },
    testPurchase: {},
    acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
    linkedPurchaseToken: JETON,
  });
  assert.deepEqual(r, {
    lineItems: [{ productId: 'miff_plus', expiryTime: '2026-12-31T00:00:00Z', autoRenewingPlan: { autoRenewEnabled: true }, offerDetails: { basePlanId: 'annuel' }, offerPhase: { freeTrial: true } }],
    subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
    externalAccountIdentifiers: { obfuscatedExternalAccountId: 'abc' },
    testPurchase: true,
    acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
    linkedPurchaseToken: JETON,
  });
  assert.ok(!JSON.stringify(r).includes('a@b.c') && !JSON.stringify(r).includes('GPA.'));
  assert.equal((G.lireReponseGoogle({ subscriptionState: 5, lineItems: [{ productId: 7, expiryTime: {} }] })).lineItems[0].productId, undefined, 'type errone ignore');
  for (const mauvais of [null, 'x', [], 5, { lineItems: 'non' }, { lineItems: [5] }]) assert.equal((await raison(() => G.lireReponseGoogle(mauvais)))?.raison, 'reponse_invalide', JSON.stringify(mauvais));
  assert.equal(G.lireReponseGoogle({ lineItems: [], linkedPurchaseToken: 'court' }).linkedPurchaseToken, undefined, 'jeton precedent invalide ignore');
});

// --- accuser -------------------------------------------------------------------------------------------------------------------------

test("accuser : POST sur la bonne URL (produit fixe, jeton encode) avec un corps vide ; 400 = deja acquitte (absorbe) ; 429/5xx passagers ; 401/403 acces refuse ; jamais de revocation", async () => {
  const f = fauxFetch([rep(200, {})]);
  await G.creerClientGoogle({ fetch: f, jetons: fauxJetons() }).accuser(JETON);
  assert.equal(f.appels[0].url, `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.tomessa.miff/purchases/subscriptions/miff_plus/tokens/${encodeURIComponent(JETON)}:acknowledge`);
  assert.equal(f.appels[0].init.method, 'POST');
  assert.equal(f.appels[0].init.body, '{}');
  assert.ok(!f.appels[0].url.includes('revoke') && !f.appels[0].url.includes('refund'));
  await G.creerClientGoogle({ fetch: fauxFetch([rep(400)]), jetons: fauxJetons() }).accuser(JETON);
  assert.deepEqual(await raison(() => G.creerClientGoogle({ fetch: fauxFetch([rep(429)]), jetons: fauxJetons() }).accuser(JETON)), { raison: 'indisponible', transitoire: true });
  assert.deepEqual(await raison(() => G.creerClientGoogle({ fetch: fauxFetch([rep(503)]), jetons: fauxJetons() }).accuser(JETON)), { raison: 'indisponible', transitoire: true });
  assert.equal((await raison(() => G.creerClientGoogle({ fetch: fauxFetch([rep(403), rep(403)]), jetons: fauxJetons() }).accuser(JETON))).raison, 'acces_refuse');
  assert.equal((await raison(() => G.creerClientGoogle({ fetch: fauxFetch([rep(200)]), jetons: fauxJetons() }).accuser('court'))).raison, 'jeton_invalide');
});

// --- configuration Apple -------------------------------------------------------------------------------------------------------------

// Faux fichier de clé assemblé à l'exécution (aucun bloc de clé écrit en clair dans le dépôt, même factice).
const PEM = [['-----BEGIN', 'PRIVATE', 'KEY-----'].join(' '), 'TEST-NON-SECRET', ['-----END', 'PRIVATE', 'KEY-----'].join(' '), ''].join('\n');
const doc = (donnees) => ({ exists: donnees !== null, get: (c) => donnees?.[c] });
const fauxDb = (donnees) => {
  const db = { lectures: [], doc: (chemin) => ({ get: async () => { db.lectures.push(chemin); return doc(typeof donnees === 'function' ? donnees() : donnees); } }) };
  return db;
};
const CONFIG_OK = { keyId: 'ABCDE12345', issuerId: '123e4567-e89b-42d3-a456-426614174000', appAppleId: 1234567890 };
const secret = (valeur) => rep(200, { name: 'projects/1/secrets/apple-iap-key/versions/3', payload: { data: Buffer.from(valeur).toString('base64') } });
const projet = 'miff-3d283';

test("configApple : lit billingConfig/apple puis le secret `apple-iap-key` (URL exacte, jeton Bearer, delai) ; cache de 5 minutes ; la cle n'est jamais ecrite ailleurs", async () => {
  let t = new Date('2026-12-01T12:00:00Z');
  const db = fauxDb(CONFIG_OK);
  const f = fauxFetch([secret(PEM)]);
  const charger = C.creerChargeurConfigApple({ db, jetons: fauxJetons(), fetch: f, maintenant: () => t, projet });
  const c = await charger();
  assert.deepEqual(c, { cle: PEM, keyId: 'ABCDE12345', issuerId: '123e4567-e89b-42d3-a456-426614174000', appAppleId: 1234567890 });
  assert.deepEqual(db.lectures, ['billingConfig/apple']);
  assert.equal(f.appels[0].url, 'https://secretmanager.googleapis.com/v1/projects/miff-3d283/secrets/apple-iap-key/versions/latest:access');
  assert.equal(f.appels[0].init.headers.Authorization, 'Bearer jeton-direct');
  assert.ok(f.appels[0].init.signal);
  await charger();
  assert.equal(db.lectures.length, 1, 'cache : une seule lecture');
  assert.equal(f.appels.length, 1);
  t = new Date(t.getTime() + 4 * 60_000);
  await charger();
  assert.equal(f.appels.length, 1, 'encore en cache a 4 minutes');
  t = new Date(t.getTime() + 2 * 60_000);
  await charger();
  assert.equal(f.appels.length, 2, 'relue apres 5 minutes (rotation sans redeploiement)');
});

test('configApple : configuration absente, incomplete ou mal formee, secret absent ou cle illisible -> « non configure » (null), jamais une configuration a moitie', async () => {
  const mk = async (donnees, reponse) => C.creerChargeurConfigApple({ db: fauxDb(donnees), jetons: fauxJetons(), fetch: fauxFetch([reponse ?? secret(PEM)]), projet })();
  assert.equal(await mk(null), null);
  assert.equal(await mk({ ...CONFIG_OK, keyId: 'court' }), null);
  assert.equal(await mk({ ...CONFIG_OK, keyId: 'abcde12345' }), null, 'identifiant de cle en majuscules');
  assert.equal(await mk({ ...CONFIG_OK, issuerId: 'pas-un-uuid' }), null);
  assert.equal(await mk({ keyId: CONFIG_OK.keyId }), null);
  assert.equal(await mk(CONFIG_OK, rep(404)), null, 'secret absent');
  assert.equal(await mk(CONFIG_OK, secret('pas une cle')), null, 'ce n\'est pas un fichier de cle');
  assert.equal((await mk({ ...CONFIG_OK, appAppleId: '1234567890' })).appAppleId, null, 'identifiant d\'app saisi comme texte : ignore (production non configuree)');
  assert.equal((await mk({ ...CONFIG_OK, appAppleId: -4 })).appAppleId, null);
});

test("configApple : droit refuse (403) -> acces_refuse, MEMORISE 30 s (un point d'entree public martele ne multiplie pas les appels) ; panne -> passager ; projet illisible -> non_configure ; aucune cle dans les erreurs", async () => {
  let t = new Date('2026-12-01T12:00:00Z');
  const db = fauxDb(CONFIG_OK);
  const f = fauxFetch([rep(403, { error: { message: PEM } }), secret(PEM)]);
  const charger = C.creerChargeurConfigApple({ db, jetons: fauxJetons(), fetch: f, maintenant: () => t, projet });
  const r = await raison(charger);
  assert.equal(r.raison, 'acces_refuse');
  const r2 = await raison(charger);
  assert.equal(r2.raison, 'acces_refuse');
  assert.equal(db.lectures.length, 1, 'echec memorise : pas de nouvelle lecture');
  assert.equal(f.appels.length, 1);
  t = new Date(t.getTime() + 31_000);
  assert.ok(await charger(), 'apres 30 s, nouvel essai (le PM a pu poser le droit)');
  const panne = await raison(C.creerChargeurConfigApple({ db: fauxDb(CONFIG_OK), jetons: fauxJetons(), fetch: fauxFetch([rep(503)]), projet }));
  assert.deepEqual(panne, { raison: 'indisponible', transitoire: true });
  const sansProjet = await raison(C.creerChargeurConfigApple({ db: fauxDb(CONFIG_OK), jetons: fauxJetons(), fetch: fauxFetch([secret(PEM)]), projet: '../autre' }));
  assert.equal(sansProjet.raison, 'non_configure');
  for (const e of [r, r2, panne, sansProjet]) assert.ok(!JSON.stringify(e).includes('TEST-NON-SECRET'), 'jamais la cle dans une erreur');
});
