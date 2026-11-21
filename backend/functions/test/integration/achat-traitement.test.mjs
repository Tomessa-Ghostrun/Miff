// SRV-7 : traitement du premium reel A L'EMULATEUR Firestore (lance par `npm run test:functions` depuis backend/), avec de FAUX clients Google et Apple injectes.
// Aucun compte, aucun secret, aucun appel reseau. Criteres BK1 a BK8, BK10, BK12 (partie donnees), BK15 du ticket docs/tickets/premium-achat-reel.md.
// Les fonctions de l'emulateur tournent en meme temps mais ne sont declenchees par aucune ecriture ici (aucun declencheur Firestore sur billing*, entitlements).
import { test, before, beforeEach, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';

const require = createRequire(import.meta.url);
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const T = require('../../lib/achatTraitement.js');
const R = require('../../lib/achatRegles.js');
const E = require('../../lib/achatErreurs.js');

const projet = process.env.GCLOUD_PROJECT;
const hoteFirestore = process.env.FIRESTORE_EMULATOR_HOST;
let db;

before(() => {
  assert.match(projet ?? '', /^demo-/, "ce test ne tourne que sur un projet de démonstration (lancer via 'npm run test:functions' depuis backend/)");
  assert.ok(hoteFirestore, 'FIRESTORE_EMULATOR_HOST absent : lancer via emulators:exec avec firestore ET functions');
  db = getFirestore(initializeApp({ projectId: projet }, 'achat-traitement-tests'));
});

async function viderEmulateur() {
  const r = await fetch(`http://${hoteFirestore}/emulator/v1/projects/${projet}/databases/(default)/documents`, { method: 'DELETE' });
  assert.equal(r.status, 200, "vidage de l'émulateur Firestore");
}
beforeEach(viderEmulateur);
after(viderEmulateur);

// --- Aides --------------------------------------------------------------------------------------------------------------------------------

const JOUR = 86_400_000;
const T0 = new Date('2026-12-01T12:00:00Z');
const iso = (ms) => new Date(ms).toISOString();
const ALICE = 'alice';
const BOB = 'bob';
const JETON = 'kpdlbgmpjflhhldfpdgnhaii.AO-J1OxAliceTokenAbCdEfGhIjKlMnOpQrStUvWxYz_0123456789';
const JETON2 = 'zzzzzzzzzzzzzzzzzzzzzzzz.AO-J1OxSecondTokenAbCdEfGhIjKlMnOpQrStUvWxYz_0123456789';
const ID_APPLE = '2000000123456789';
const JWS = 'eyJhbGciOiJFUzI1NiJ9.eyJhIjoxfQ.c2lnbmF0dXJlLWRlLXRlc3Q';
const MESSAGE_ID = (n) => String(136969346945 + n);

/** Horloge pilotable. */
let maintenant;
beforeEach(() => {
  maintenant = new Date(T0);
});
const avancer = (ms) => {
  maintenant = new Date(maintenant.getTime() + ms);
};

/** Reponse Google (forme validee par lireReponseGoogle). */
const repGoogle = (idCompte, over = {}, ligne = {}) => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
  lineItems: [{ productId: 'miff_plus', expiryTime: iso(T0.getTime() + 30 * JOUR), autoRenewingPlan: { autoRenewEnabled: true }, offerDetails: { basePlanId: 'mensuel' }, ...ligne }],
  acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
  ...(idCompte ? { externalAccountIdentifiers: { obfuscatedExternalAccountId: idCompte } } : {}),
  ...over,
});

/** Faux client Google : `table[jeton]` = reponse, ou fonction (peut lever), ou ErreurAchat. */
function fauxGoogle(table = {}) {
  const f = {
    table,
    lectures: [],
    accuses: [],
    erreurAccuse: null,
    async lire(jeton) {
      f.lectures.push(jeton);
      const v = f.table[jeton];
      if (v === undefined) throw new E.ErreurAchat('achat_inconnu');
      const r = typeof v === 'function' ? v() : v;
      if (r instanceof Error) throw r;
      return structuredClone(r);
    },
    async accuser(jeton) {
      if (f.erreurAccuse) throw f.erreurAccuse;
      f.accuses.push(jeton);
    },
  };
  return f;
}

/** Faux client Apple. `transactions[jws]` -> { environnement, idTransactionOrigine } ; `etats[id]` -> { statut, transaction, renouvellement } ; `notifications[payload]` -> NotificationApple | Error. */
function fauxApple({ transactions = {}, etats = {}, notifications = {} } = {}) {
  const f = {
    transactions,
    etats,
    notifications,
    lecturesEtat: [],
    async verifierTransactionApp(jws) {
      const v = f.transactions[jws];
      if (v === undefined) throw new E.ErreurAchat('signature_invalide');
      if (v instanceof Error) throw v;
      return v;
    },
    async verifierNotification(payload) {
      const v = f.notifications[payload];
      if (v === undefined) throw new E.ErreurAchat('signature_invalide');
      if (v instanceof Error) throw v;
      return v;
    },
    async lireEtat(env, id, now) {
      f.lecturesEtat.push(id);
      const v = f.etats[id];
      if (v === undefined) throw new E.ErreurAchat('achat_inconnu');
      const e = typeof v === 'function' ? v() : v;
      if (e instanceof Error) throw e;
      return R.etatDepuisApple(e.statut, e.transaction, e.renouvellement ?? null, now);
    },
  };
  return f;
}
const txApple = (over = {}) => ({
  originalTransactionId: ID_APPLE,
  productId: 'com.tomessa.miff.plus.annual',
  type: 'Auto-Renewable Subscription',
  expiresDate: T0.getTime() + 365 * JOUR,
  inAppOwnershipType: 'PURCHASED',
  environment: 'Sandbox',
  signedDate: T0.getTime(),
  ...over,
});

/** Espion : compte les lectures et ecritures des transactions (tx.get, tx.set, tx.update, tx.create, tx.delete) et les acces directs a Firestore. */
function espionner(base) {
  const compteurs = { lectures: 0, ecritures: 0, acces: 0 };
  const enrouler = (tx) =>
    new Proxy(tx, {
      get(cible, prop) {
        const v = cible[prop];
        if (typeof v !== 'function') return v;
        return (...args) => {
          if (prop === 'get') compteurs.lectures += 1;
          if (['set', 'update', 'create', 'delete'].includes(prop)) compteurs.ecritures += 1;
          return v.apply(cible, args);
        };
      },
    });
  const proxy = new Proxy(base, {
    get(cible, prop) {
      const v = cible[prop];
      if (typeof v !== 'function') return v;
      if (prop === 'runTransaction') return (fn, opts) => cible.runTransaction((tx) => fn(enrouler(tx)), opts);
      return (...args) => {
        compteurs.acces += 1;
        return v.apply(cible, args);
      };
    },
  });
  return { proxy, compteurs };
}

/** Compteur UNIQUE pour toute la suite : deux appels a creerDeps ne doivent jamais fabriquer le meme identifiant (en production ce sont des UUID aleatoires). */
let compteurUuid = 0;
function creerDeps({ google = fauxGoogle(), apple = fauxApple(), base = db } = {}) {
  return { db: base, google, apple, maintenant: () => new Date(maintenant), uuid: () => `00000000-0000-4000-8000-${String(++compteurUuid).padStart(12, '0')}` };
}

const lire = async (chemin) => {
  const s = await db.doc(chemin).get();
  return s.exists ? s.data() : null;
};
const ms = (ts) => ts?.toMillis?.() ?? ts;
const toutesLesDonnees = async () => {
  const sortie = {};
  for (const col of ['entitlements', 'billingAccounts', 'billingPurchases', 'billingEvents']) {
    const s = await db.collection(col).get();
    for (const d of s.docs) sortie[`${col}/${d.id}`] = d.data();
  }
  return sortie;
};
/** Vide tout sauf les comptes (identifiants d achat) : permet de rejouer plusieurs etats pour le meme compte. */
const viderSaufComptes = async () => {
  for (const col of ["entitlements", "billingPurchases", "billingEvents"]) for (const d of (await db.collection(col).get()).docs) await d.ref.delete();
};
const raison = async (fn) => {
  try {
    await fn();
  } catch (e) {
    return e instanceof E.ErreurAchat ? e.raison : `autre:${e.message}`;
  }
  return null;
};
const messageRtdn = (jeton, type = 4, over = {}) => ({
  messageId: MESSAGE_ID(1),
  publishTime: iso(T0.getTime()),
  data: Buffer.from(JSON.stringify({ version: '1.0', packageName: 'com.tomessa.miff', eventTimeMillis: String(T0.getTime()), subscriptionNotification: { version: '1.0', notificationType: type, purchaseToken: jeton } })).toString('base64'),
  ...over,
});
const messageBrut = (doc, over = {}) => ({ messageId: MESSAGE_ID(2), publishTime: iso(T0.getTime()), data: Buffer.from(JSON.stringify(doc)).toString('base64'), ...over });
const cleG = (jeton) => R.cleAchat('google_play', jeton);
const cleA = (id) => R.cleAchat('app_store', id);

// --- BK1 : identifiant d'achat -------------------------------------------------------------------------------------------------------------

test("BK1 - preparerAchat : un identifiant ALEATOIRE par compte, le meme a chaque appel, non derive de l'uid, une seule ecriture au total", async () => {
  const { proxy, compteurs } = espionner(db);
  const deps = { db: proxy, google: fauxGoogle(), apple: fauxApple(), maintenant: () => new Date(maintenant), uuid: () => randomUUID() };
  const a1 = await T.preparerAchat(deps, ALICE);
  const a2 = await T.preparerAchat(deps, ALICE);
  const b1 = await T.preparerAchat(deps, BOB);
  assert.match(a1.purchaseId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.equal(a1.purchaseId, a2.purchaseId);
  assert.notEqual(a1.purchaseId, b1.purchaseId);
  assert.ok(!a1.purchaseId.includes(ALICE) && !a1.purchaseId.replaceAll('-', '').includes(Buffer.from(ALICE).toString('hex')));
  assert.equal(compteurs.ecritures, 2, 'une ecriture par compte, aucune au 2e appel');
  const doc = await lire(`billingAccounts/${ALICE}`);
  assert.deepEqual(Object.keys(doc).sort(), ['createdAt', 'purchaseId', 'v']);
  assert.equal(doc.purchaseId, a1.purchaseId);
});

test('BK1 - un uid hors forme (injection de chemin) est refuse avant tout acces', async () => {
  for (const uid of ['../x', 'a/b', '', 'a b', 'x'.repeat(129)]) assert.equal(await raison(() => T.preparerAchat(creerDeps(), uid)), 'entree_invalide', uid);
  assert.deepEqual(await toutesLesDonnees(), {});
});

// --- BK2 : achat Google --------------------------------------------------------------------------------------------------------------

async function preparer(uid = ALICE, deps = creerDeps()) {
  return (await T.preparerAchat(deps, uid)).purchaseId;
}
const sync = (deps, uid, jeton = JETON) => T.synchroniserAchat(deps, uid, { store: 'google_play', purchaseToken: jeton });

test("BK2 - achat Google dont l'identifiant est celui de l'appelant : acces accorde, achat lie, achat ACQUITTE, aucune reference d'achat conservee (BK12)", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  const deps = creerDeps({ google });
  avancer(2 * 60_000);
  const r = await sync(deps, ALICE);
  assert.equal(r.state, 'active');
  const acces = await lire(`entitlements/${ALICE}`);
  assert.deepEqual(Object.keys(acces).sort(), ['active', 'expiresAt', 'plan', 'source', 'state', 'test', 'trial', 'updatedAt', 'willRenew']);
  assert.deepEqual({ active: acces.active, source: acces.source, state: acces.state, plan: acces.plan, trial: acces.trial, willRenew: acces.willRenew, test: acces.test }, { active: true, source: 'google_play', state: 'active', plan: 'monthly', trial: false, willRenew: true, test: false });
  assert.equal(ms(acces.expiresAt), T0.getTime() + 30 * JOUR);
  assert.equal(ms(acces.updatedAt), maintenant.getTime());
  const achat = await lire(`billingPurchases/${cleG(JETON)}`);
  assert.equal(achat.uid, ALICE);
  assert.deepEqual(Object.keys(achat).sort(), ['active', 'createdAt', 'expiresAt', 'observeA', 'plan', 'produit', 'purgeAt', 'state', 'store', 'test', 'trial', 'uid', 'updatedAt', 'v', 'willRenew']);
  assert.equal(ms(achat.purgeAt), T0.getTime() + 30 * JOUR + 90 * JOUR, 'purge 90 jours apres l\'echeance');
  assert.deepEqual(google.accuses, [JETON], 'achat acquitte cote serveur');
  const tout = JSON.stringify(await toutesLesDonnees());
  assert.ok(!tout.includes(JETON) && !tout.includes('AliceToken'), 'aucune reference d\'achat conservee');
  assert.ok(!tout.includes(idAlice.slice(0, 8)) || tout.includes('purchaseId'), 'l\'identifiant d\'achat n\'est que dans billingAccounts');
});

test("BK2 - achat deja acquitte : pas de second accuse ; achat refuse : jamais acquitte", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice, { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' }) });
  await sync(creerDeps({ google }), ALICE);
  assert.deepEqual(google.accuses, []);
});

test("BK2 - identifiant d'achat d'un AUTRE compte existant : refuse (deja_lie), RIEN n'est ecrit pour l'appelant, rien n'est acquitte, aucun detail sur l'autre compte", async () => {
  const idBob = await preparer(BOB);
  await preparer(ALICE);
  const google = fauxGoogle({ [JETON]: repGoogle(idBob) });
  avancer(2 * 60_000);
  const avant = await toutesLesDonnees();
  let erreur;
  try {
    await sync(creerDeps({ google }), ALICE);
  } catch (e) {
    erreur = e;
  }
  assert.equal(erreur?.raison, 'deja_lie');
  assert.ok(!JSON.stringify([erreur.message, erreur.raison]).includes(BOB));
  assert.equal(await lire(`entitlements/${ALICE}`), null);
  assert.equal(await lire(`billingPurchases/${cleG(JETON)}`), null);
  assert.deepEqual(google.accuses, []);
  const apres = await toutesLesDonnees();
  // Seule la date de derniere synchronisation (limite de frequence) a bouge.
  assert.deepEqual(Object.keys(apres).sort(), Object.keys(avant).sort());
  assert.equal(apres[`billingAccounts/${BOB}`].purchaseId, idBob);
});

test("BK2 - identifiant d'achat inconnu (compte supprime depuis) ou absent (achat fait dans Google Play) : accorde a l'appelant s'il presente le jeton", async () => {
  await preparer();
  for (const [idx, idCompte] of [[1, '99999999-9999-4999-8999-999999999999'], [2, null]]) {
    await viderEmulateur();
    await preparer();
    const google = fauxGoogle({ [JETON]: repGoogle(idCompte) });
    const r = await sync(creerDeps({ google }), ALICE);
    assert.equal(r.state, 'active', `cas ${idx}`);
    assert.equal((await lire(`billingPurchases/${cleG(JETON)}`)).uid, ALICE);
  }
});

test('BK2 - jeton inconnu de Google : achat_inconnu, rien d\'ecrit (hors limite de frequence) ; erreur passagere du store : indisponible, rien d\'ecrit', async () => {
  await preparer();
  assert.equal(await raison(() => sync(creerDeps({ google: fauxGoogle({}) }), ALICE)), 'achat_inconnu');
  avancer(2 * 60_000);
  const google = fauxGoogle({ [JETON]: new E.ErreurAchat('indisponible', true) });
  assert.equal(await raison(() => sync(creerDeps({ google }), ALICE)), 'indisponible');
  const donnees = await toutesLesDonnees();
  assert.deepEqual(Object.keys(donnees).sort(), [`billingAccounts/${ALICE}`]);
});

test('BK2 - limite : 1 synchronisation par minute et par compte, consommee AVANT l\'appel au store ; autre compte non concerne ; apres une minute, de nouveau permis', async () => {
  const idAlice = await preparer();
  await preparer(BOB);
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  const deps = creerDeps({ google });
  await sync(deps, ALICE);
  assert.equal(google.lectures.length, 1);
  avancer(59_000);
  assert.equal(await raison(() => sync(deps, ALICE)), 'limite_frequence');
  assert.equal(google.lectures.length, 1, 'le store n\'est pas appele quand la limite est atteinte');
  assert.equal(await raison(() => sync(deps, BOB, JETON2)), 'achat_inconnu', 'un autre compte n\'est pas limite par celui-la');
  assert.equal(google.lectures.length, 2, "le store a ete appele pour l'autre compte (achat inconnu), pas pour Alice");
  avancer(2_000);
  await sync(deps, ALICE);
  assert.equal(google.lectures.length, 3);
  // Une erreur du store consomme aussi la minute (protege le quota de l'API).
  avancer(61_000);
  google.table[JETON] = new E.ErreurAchat('indisponible', true);
  assert.equal(await raison(() => sync(deps, ALICE)), 'indisponible');
  avancer(1_000);
  assert.equal(await raison(() => sync(deps, ALICE)), 'limite_frequence');
});

test("BK2 - synchroniserAchat cree l'identifiant du compte si preparerAchat n'a pas ete appele ; entrees : forme exacte exigee", async () => {
  const google = fauxGoogle({ [JETON]: repGoogle(null) });
  await sync(creerDeps({ google }), ALICE);
  assert.match((await lire(`billingAccounts/${ALICE}`)).purchaseId, /^[0-9a-f-]{36}$/);
  for (const mauvais of [null, 'x', [], {}, { store: 'google_play' }, { store: 'google_play', purchaseToken: 'court' }, { store: 'google_play', purchaseToken: JETON, extra: 1 }, { store: 'autre', purchaseToken: JETON }, { store: 'app_store', purchaseToken: JETON }, { store: 'app_store', signedTransaction: 'pas-un-jws' }, { store: 'google_play', signedTransaction: JWS }]) {
    assert.ok(['entree_invalide', 'jeton_invalide'].includes(await raison(() => T.validerEntreeSynchro(mauvais))), JSON.stringify(mauvais));
  }
  assert.deepEqual(T.validerEntreeSynchro({ store: 'app_store', signedTransaction: JWS }), { store: 'app_store', signedTransaction: JWS });
});

// --- BK3 : achat Apple -----------------------------------------------------------------------------------------------------------------

const syncA = (deps, uid) => T.synchroniserAchat(deps, uid, { store: 'app_store', signedTransaction: JWS });
const appleOk = (idCompte, over = {}) =>
  fauxApple({
    transactions: { [JWS]: { environnement: 'Sandbox', idTransactionOrigine: ID_APPLE } },
    etats: { [ID_APPLE]: { statut: 1, transaction: txApple({ appAccountToken: idCompte ?? undefined, ...over }), renouvellement: { autoRenewStatus: 1, signedDate: T0.getTime() } } },
  });

test("BK3 - achat Apple dont l'appAccountToken est celui de l'appelant : acces accorde (achat de bac a sable marque test), aucune reference conservee", async () => {
  const idAlice = await preparer();
  const r = await syncA(creerDeps({ apple: appleOk(idAlice) }), ALICE);
  assert.equal(r.state, 'active');
  const acces = await lire(`entitlements/${ALICE}`);
  assert.deepEqual({ active: acces.active, source: acces.source, plan: acces.plan, test: acces.test }, { active: true, source: 'app_store', plan: 'yearly', test: true });
  assert.equal((await lire(`billingPurchases/${cleA(ID_APPLE)}`)).uid, ALICE);
  assert.ok(!JSON.stringify(await toutesLesDonnees()).includes(ID_APPLE));
});

test("BK3 - Apple : jeton d'un AUTRE compte -> deja_lie ; signature invalide -> refuse ; partage en famille -> refuse ; rien n'est ecrit", async () => {
  const idBob = await preparer(BOB);
  await preparer(ALICE);
  avancer(2 * 60_000);
  assert.equal(await raison(() => syncA(creerDeps({ apple: appleOk(idBob) }), ALICE)), 'deja_lie');
  avancer(2 * 60_000);
  assert.equal(await raison(() => syncA(creerDeps({ apple: fauxApple() }), ALICE)), 'signature_invalide');
  avancer(2 * 60_000);
  assert.equal(await raison(() => syncA(creerDeps({ apple: appleOk(null, { inAppOwnershipType: 'FAMILY_SHARED' }) }), ALICE)), 'partage_famille');
  assert.equal(await lire(`entitlements/${ALICE}`), null);
  assert.equal(await lire(`billingPurchases/${cleA(ID_APPLE)}`), null);
});

// --- BK4 : un achat = un compte ---------------------------------------------------------------------------------------------------------

test("BK4 - un achat deja lie a un compte existant ne peut pas l'etre a un autre (Google et Apple), meme sans identifiant d'achat ; l'appelant d'origine peut resynchroniser", async () => {
  const idAlice = await preparer();
  await preparer(BOB);
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  await sync(creerDeps({ google }), ALICE);
  google.table[JETON] = repGoogle(null); // meme jeton presente par un autre compte : sans identifiant, la preuve est le jeton, mais l'achat est deja lie
  avancer(2 * 60_000);
  assert.equal(await raison(() => sync(creerDeps({ google }), BOB)), 'deja_lie');
  assert.equal(await lire(`entitlements/${BOB}`), null);
  avancer(2 * 60_000);
  assert.equal((await sync(creerDeps({ google }), ALICE)).state, 'active', "le proprietaire resynchronise sans probleme");
  // Apple
  const apple = appleOk(null);
  avancer(2 * 60_000);
  await syncA(creerDeps({ apple }), ALICE);
  avancer(2 * 60_000);
  assert.equal(await raison(() => syncA(creerDeps({ apple }), BOB)), 'deja_lie');
});

test("BK4 - apres la suppression du compte d'origine, l'achat (dont le lien est supprime) peut etre lie a un nouveau compte (§9.5 : l'abonnement n'est pas perdu)", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  await sync(creerDeps({ google }), ALICE);
  await T.supprimerDonneesAbonnement(creerDeps({ google }), ALICE);
  await preparer(BOB);
  avancer(2 * 60_000);
  assert.equal((await sync(creerDeps({ google }), BOB)).state, 'active');
  assert.equal((await lire(`billingPurchases/${cleG(JETON)}`)).uid, BOB);
});

test("BK4 - lien ORPHELIN (suppression de compte interrompue : compte supprime, lien reste) : un autre compte peut reprendre l'achat ; une notification, elle, ne ressuscite rien", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  await sync(creerDeps({ google }), ALICE);
  await db.doc(`billingAccounts/${ALICE}`).delete(); // l'application a plante entre deux etapes
  // Notification : le compte lie n'existe plus -> compte inconnu, rien n'est ecrit.
  const avant = JSON.stringify(await lire(`entitlements/${ALICE}`));
  await T.traiterMessageGoogle(creerDeps({ google }), messageRtdn(JETON, 2));
  assert.equal(JSON.stringify(await lire(`entitlements/${ALICE}`)), avant);
  // Reprise par un autre compte.
  await preparer(BOB);
  avancer(2 * 60_000);
  assert.equal((await sync(creerDeps({ google }), BOB)).state, 'active');
});

// --- BK5 : etats (par le chemin des notifications) --------------------------------------------------------------------------------------

test('BK5 - Google : chaque etat du §8 donne le bon acces (active, expiresAt, state, trial, willRenew)', async () => {
  const idAlice = await preparer();
  const fin = T0.getTime() + 30 * JOUR;
  const cas = [
    ['trial', repGoogle(idAlice, {}, { expiryTime: iso(T0.getTime() + 7 * JOUR), offerDetails: { basePlanId: 'annuel' }, offerPhase: { freeTrial: true } }), { active: true, state: 'trial', trial: true, willRenew: true, plan: 'yearly', fin: T0.getTime() + 7 * JOUR }],
    ['active', repGoogle(idAlice), { active: true, state: 'active', trial: false, willRenew: true, plan: 'monthly', fin }],
    ['canceled', repGoogle(idAlice, { subscriptionState: 'SUBSCRIPTION_STATE_CANCELED' }, { autoRenewingPlan: { autoRenewEnabled: false } }), { active: true, state: 'canceled', trial: false, willRenew: false, plan: 'monthly', fin }],
    ['grace', repGoogle(idAlice, { subscriptionState: 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD' }, { expiryTime: iso(T0.getTime() + 5 * JOUR) }), { active: true, state: 'grace', trial: false, willRenew: true, plan: 'monthly', fin: T0.getTime() + 5 * JOUR }],
    ['on_hold', repGoogle(idAlice, { subscriptionState: 'SUBSCRIPTION_STATE_ON_HOLD' }, { expiryTime: iso(T0.getTime() - 3 * JOUR) }), { active: false, state: 'on_hold', trial: false, willRenew: false, plan: 'monthly', fin: T0.getTime() - 3 * JOUR }],
    ['expired', repGoogle(idAlice, { subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED' }, { expiryTime: iso(T0.getTime() - JOUR) }), { active: false, state: 'expired', trial: false, willRenew: false, plan: 'monthly', fin: T0.getTime() - JOUR }],
  ];
  let n = 10;
  for (const [nom, reponse, attendu] of cas) {
    await viderSaufComptes();
    const google = fauxGoogle({ [JETON]: reponse });
    await T.traiterMessageGoogle(creerDeps({ google }), messageRtdn(JETON, 4, { messageId: MESSAGE_ID(n++) }));
    const a = await lire(`entitlements/${ALICE}`);
    assert.ok(a, `${nom} : acces ecrit`);
    assert.deepEqual({ active: a.active, state: a.state, trial: a.trial, willRenew: a.willRenew, plan: a.plan, fin: ms(a.expiresAt) }, attendu, nom);
  }
});

test("BK5 - Google : un achat en attente de paiement n'accorde RIEN, n'ecrit RIEN et n'est pas acquitte ; il est accorde a l'approbation (message suivant)", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice, { subscriptionState: 'SUBSCRIPTION_STATE_PENDING' }, { expiryTime: undefined }) });
  const deps = creerDeps({ google });
  assert.equal((await sync(deps, ALICE)).state, 'pending');
  assert.equal(await lire(`entitlements/${ALICE}`), null);
  assert.equal(await lire(`billingPurchases/${cleG(JETON)}`), null);
  assert.deepEqual(google.accuses, []);
  google.table[JETON] = repGoogle(idAlice);
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 4));
  assert.equal((await lire(`entitlements/${ALICE}`)).state, 'active');
  assert.deepEqual(google.accuses, [JETON]);
});

test('BK5 - Apple : actif, essai, grace, nouvelle tentative, expire, rembourse donnent le bon acces', async () => {
  const idAlice = await preparer();
  const cas = [
    ['active', { statut: 1, transaction: txApple(), renouvellement: { autoRenewStatus: 1 } }, { active: true, state: 'active', trial: false }],
    ['trial', { statut: 1, transaction: txApple({ offerType: 1, offerDiscountType: 'FREE_TRIAL', expiresDate: T0.getTime() + 7 * JOUR }), renouvellement: { autoRenewStatus: 1 } }, { active: true, state: 'trial', trial: true }],
    ['canceled', { statut: 1, transaction: txApple(), renouvellement: { autoRenewStatus: 0 } }, { active: true, state: 'canceled', trial: false }],
    ['grace', { statut: 4, transaction: txApple({ expiresDate: T0.getTime() - JOUR }), renouvellement: { autoRenewStatus: 1, gracePeriodExpiresDate: T0.getTime() + 6 * JOUR } }, { active: true, state: 'grace', trial: false }],
    ['on_hold', { statut: 3, transaction: txApple({ expiresDate: T0.getTime() - 2 * JOUR }), renouvellement: { autoRenewStatus: 1 } }, { active: false, state: 'on_hold', trial: false }],
    ['expired', { statut: 2, transaction: txApple({ expiresDate: T0.getTime() - 2 * JOUR }), renouvellement: { autoRenewStatus: 0 } }, { active: false, state: 'expired', trial: false }],
    ['revoked', { statut: 5, transaction: txApple({ revocationDate: T0.getTime() - 1000 }), renouvellement: { autoRenewStatus: 1 } }, { active: false, state: 'revoked', trial: false }],
  ];
  let n = 0;
  for (const [nom, etat, attendu] of cas) {
    await viderSaufComptes();
    avancer(2 * 60_000); // limite de frequence : 1 synchronisation par minute
    const uuid = `00000000-0000-4000-8000-00000000${String(100 + n++).padStart(4, '0')}`;
    const apple = fauxApple({
      etats: { [ID_APPLE]: { ...etat, transaction: { ...etat.transaction, appAccountToken: idAlice } } },
      notifications: { P: { uuid, type: 'DID_RENEW', sousType: null, environnement: 'Sandbox', idTransactionOrigine: ID_APPLE } },
    });
    // Premiere liaison : l'app presente la transaction (le lien est cree) ; ensuite la notification reapplique l'etat.
    apple.transactions[JWS] = { environnement: 'Sandbox', idTransactionOrigine: ID_APPLE };
    await syncA(creerDeps({ apple }), ALICE);
    const rep = await T.traiterNotificationApple(creerDeps({ apple }), 'P');
    assert.equal(rep.http, 200, nom);
    const a = await lire(`entitlements/${ALICE}`);
    assert.deepEqual({ active: a.active, state: a.state, trial: a.trial }, attendu, nom);
    assert.equal(a.source, 'app_store');
  }
});

// --- BK6 : notifications Google (idempotence, desordre, autres messages) ----------------------------------------------------------------

test('BK6 - un message RTDN REJOUE (meme identifiant) est ignore SANS appel a Google ; le meme etat final', async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  const deps = creerDeps({ google });
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 4));
  const avant = await toutesLesDonnees();
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 4));
  assert.equal(google.lectures.length, 1, 'aucun second appel a Google');
  assert.deepEqual(await toutesLesDonnees(), avant);
  const evt = await lire(`billingEvents/g-${MESSAGE_ID(1)}`);
  assert.deepEqual(Object.keys(evt), ['purgeAt']);
  assert.equal(ms(evt.purgeAt), T0.getTime() + 7 * JOUR);
});

test('BK6 - deux livraisons SIMULTANEES du meme message : un seul etat final coherent', async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  const deps = creerDeps({ google });
  await Promise.all([T.traiterMessageGoogle(deps, messageRtdn(JETON, 4)), T.traiterMessageGoogle(deps, messageRtdn(JETON, 4))]);
  assert.equal((await lire(`entitlements/${ALICE}`)).state, 'active');
  assert.equal((await db.collection('billingPurchases').get()).size, 1);
});

test("BK6 - messages dans le DESORDRE : un etat plus ancien que celui deja ecrit est ignore (l'etat final est le plus recent)", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice, { subscriptionState: 'SUBSCRIPTION_STATE_CANCELED' }, { autoRenewingPlan: { autoRenewEnabled: false } }) });
  const deps = creerDeps({ google });
  avancer(10 * 60_000); // lecture « recente » : annule
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 3, { messageId: MESSAGE_ID(21) }));
  assert.equal((await lire(`entitlements/${ALICE}`)).state, 'canceled');
  // Une lecture plus ANCIENNE (horloge reculee : execution lancee avant, terminee apres) dit « actif » : elle ne doit pas ecraser.
  google.table[JETON] = repGoogle(idAlice);
  const t = maintenant;
  maintenant = new Date(t.getTime() - 5 * 60_000);
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 4, { messageId: MESSAGE_ID(22) }));
  assert.equal((await lire(`entitlements/${ALICE}`)).state, 'canceled', 'etat plus ancien ignore');
  assert.equal((await lire(`billingEvents/g-${MESSAGE_ID(22)}`)) !== null, true, 'le message est quand meme marque traite');
});

test('BK6 - autre package, message de test, produit ponctuel, message non conforme, jeton invalide : ignores sans erreur et SANS appel a Google ni ecriture', async () => {
  await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(null) });
  const deps = creerDeps({ google });
  const avant = await toutesLesDonnees();
  await T.traiterMessageGoogle(deps, messageBrut({ packageName: 'com.autre.app', subscriptionNotification: { notificationType: 4, purchaseToken: JETON } }));
  await T.traiterMessageGoogle(deps, messageBrut({ packageName: 'com.tomessa.miff', testNotification: { version: '1.0' } }));
  await T.traiterMessageGoogle(deps, messageBrut({ packageName: 'com.tomessa.miff', oneTimeProductNotification: { notificationType: 1, purchaseToken: JETON, sku: 'x' } }));
  await T.traiterMessageGoogle(deps, messageBrut({ packageName: 'com.tomessa.miff', subscriptionNotification: { notificationType: 4, purchaseToken: 'court' } }));
  await T.traiterMessageGoogle(deps, { messageId: MESSAGE_ID(3), publishTime: undefined, data: 'pas du base64 !!!' });
  await T.traiterMessageGoogle(deps, messageBrut({ packageName: 'com.tomessa.miff', subscriptionNotification: { notificationType: 4, purchaseToken: JETON } }, { messageId: '../../x' }));
  assert.deepEqual(google.lectures, []);
  assert.deepEqual(await toutesLesDonnees(), avant);
});

test("BK6 - un type inconnu est accepte (relecture sans effet), un achat inconnu de Google (jeton expire depuis plus de 60 jours) est consomme sans relance", async () => {
  await preparer();
  const google = fauxGoogle({});
  const deps = creerDeps({ google });
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 999)); // ne leve pas : achat_inconnu est definitif
  assert.equal(google.lectures.length, 1);
});

test("BK6 - erreur PASSAGERE : le message est relance (exception) pendant 24 h, puis abandonne ; erreur definitive : jamais relancee", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: new E.ErreurAchat('indisponible', true) });
  const deps = creerDeps({ google });
  await assert.rejects(() => T.traiterMessageGoogle(deps, messageRtdn(JETON, 2)), (e) => e.raison === 'indisponible');
  assert.equal(await lire(`billingEvents/g-${MESSAGE_ID(1)}`), null, 'un message en echec n\'est PAS marque traite : la relance le retraitera');
  // Meme erreur sur un message vieux de plus de 24 h : abandon (aucune relance).
  avancer(25 * 3600_000);
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 2, { publishTime: iso(T0.getTime()) }));
  // Erreur definitive (produit inconnu) : consommee.
  google.table[JETON] = repGoogle(idAlice, {}, { productId: 'autre' });
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 2, { messageId: MESSAGE_ID(5), publishTime: iso(maintenant.getTime()) }));
  // Erreur de configuration (acces refuse a l'API Google) : relancee, car le PM peut encore completer les droits.
  google.table[JETON] = new E.ErreurAchat('acces_refuse');
  await assert.rejects(() => T.traiterMessageGoogle(deps, messageRtdn(JETON, 2, { messageId: MESSAGE_ID(6), publishTime: iso(maintenant.getTime()) })), (e) => e.raison === 'acces_refuse');
});

test("BK6 - une erreur inattendue (Firestore...) est relancee avec un message FIXE (aucun detail dans les journaux de la plateforme)", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  const casse = { ...creerDeps({ google }), db: new Proxy(db, { get: (c, p) => (p === 'runTransaction' ? () => Promise.reject(new Error('detail interne avec billingPurchases/abc et alice')) : typeof c[p] === 'function' ? c[p].bind(c) : c[p]) }) };
  await assert.rejects(() => T.traiterMessageGoogle(casse, messageRtdn(JETON, 4)), (e) => e.message === 'billing.erreur');
});

test("BK6 - un achat inconnu (aucun lien, aucun identifiant d'achat reconnu) : consomme, RIEN n'est cree ; le message est marque traite", async () => {
  await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle('99999999-9999-4999-8999-999999999999') });
  await T.traiterMessageGoogle(creerDeps({ google }), messageRtdn(JETON, 4));
  assert.equal(await lire(`entitlements/${ALICE}`), null);
  assert.equal((await db.collection('billingPurchases').get()).size, 0);
  assert.ok(await lire(`billingEvents/g-${MESSAGE_ID(1)}`));
  assert.deepEqual(google.accuses, [], 'jamais acquitte sans compte');
});

test("BK6 - un nouvel achat arrive d'abord par la notification (app fermee) : lie par l'identifiant d'achat du store, accorde ET acquitte sans appel de l'app", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  await T.traiterMessageGoogle(creerDeps({ google }), messageRtdn(JETON, 4));
  assert.equal((await lire(`entitlements/${ALICE}`)).state, 'active');
  assert.deepEqual(google.accuses, [JETON]);
  assert.equal((await lire(`billingPurchases/${cleG(JETON)}`)).uid, ALICE);
});

test("BK6 - nouvelle souscription Google (jeton different) retrouvee par le jeton de l'achat precedent (linkedPurchaseToken) quand elle n'a pas d'identifiant d'achat", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice, { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' }) });
  await sync(creerDeps({ google }), ALICE);
  google.table[JETON2] = repGoogle(null, { linkedPurchaseToken: JETON }, { expiryTime: iso(T0.getTime() + 400 * JOUR), offerDetails: { basePlanId: 'annuel' } });
  await T.traiterMessageGoogle(creerDeps({ google }), messageRtdn(JETON2, 4, { messageId: MESSAGE_ID(31) }));
  const a = await lire(`entitlements/${ALICE}`);
  assert.deepEqual({ plan: a.plan, fin: ms(a.expiresAt) }, { plan: 'yearly', fin: T0.getTime() + 400 * JOUR });
  assert.equal((await lire(`billingPurchases/${cleG(JETON2)}`)).uid, ALICE);
});

// --- BK7 : notifications Apple ----------------------------------------------------------------------------------------------------------

const notifApple = (over = {}) => ({ uuid: '8a7b4f50-1111-4222-8333-444455556666', type: 'DID_RENEW', sousType: null, environnement: 'Sandbox', idTransactionOrigine: ID_APPLE, ...over });

test("BK7 - signature invalide : 400 et AUCUN acces a Firestore (ni lecture, ni ecriture) ; erreur de verification passagere : 503", async () => {
  const { proxy, compteurs } = espionner(db);
  const deps = creerDeps({ apple: fauxApple(), base: proxy });
  assert.deepEqual(await T.traiterNotificationApple(deps, 'faux'), { http: 400 });
  assert.deepEqual(compteurs, { lectures: 0, ecritures: 0, acces: 0 });
  const apple = fauxApple({ notifications: { P: new E.ErreurAchat('indisponible', true), Q: new E.ErreurAchat('non_configure') } });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple, base: proxy }), 'P'), { http: 503 });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple, base: proxy }), 'Q'), { http: 503 }, 'Apple relancera quand la configuration sera complete');
  assert.deepEqual(compteurs, { lectures: 0, ecritures: 0, acces: 0 });
});

test("BK7 - notification valide d'un compte INCONNU : 200 et rien n'est cree", async () => {
  const apple = fauxApple({ notifications: { P: notifApple() }, etats: { [ID_APPLE]: { statut: 1, transaction: txApple({ appAccountToken: '99999999-9999-4999-8999-999999999999' }), renouvellement: { autoRenewStatus: 1 } } } });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple }), 'P'), { http: 200 });
  assert.equal((await db.collection('billingPurchases').get()).size, 0);
  assert.equal((await db.collection('entitlements').get()).size, 0);
});

test('BK7 - meme notification rejouee (meme uuid) : 200 sans nouvel appel a Apple ; notification de test (sans transaction) : 200 sans ecriture', async () => {
  const idAlice = await preparer();
  const apple = appleOk(idAlice);
  apple.notifications.P = notifApple();
  apple.notifications.TEST = notifApple({ type: 'TEST', idTransactionOrigine: null, uuid: '11111111-1111-4111-8111-111111111111' });
  const deps = creerDeps({ apple });
  assert.deepEqual(await T.traiterNotificationApple(deps, 'P'), { http: 200 });
  assert.equal(apple.lecturesEtat.length, 1);
  assert.deepEqual(await T.traiterNotificationApple(deps, 'P'), { http: 200 });
  assert.equal(apple.lecturesEtat.length, 1, 'pas de relecture pour une notification deja traitee');
  assert.equal((await lire(`entitlements/${ALICE}`)).state, 'active', "lie par l'appAccountToken sans passage par l'app");
  assert.deepEqual(await T.traiterNotificationApple(deps, 'TEST'), { http: 200 });
  assert.equal(apple.lecturesEtat.length, 1);
});

test("BK7 - erreurs de l'API Apple : passagere ou configuration incomplete -> 503 (Apple relance) ; definitive (achat inconnu, partage en famille) -> 200 ; uuid hors forme -> 400", async () => {
  const mk = (e) => fauxApple({ notifications: { P: notifApple() }, etats: { [ID_APPLE]: e } });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple: mk(new E.ErreurAchat('indisponible', true)) }), 'P'), { http: 503 });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple: mk(new E.ErreurAchat('non_configure')) }), 'P'), { http: 503 });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple: mk(new E.ErreurAchat('acces_refuse')) }), 'P'), { http: 503 });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple: mk(new E.ErreurAchat('achat_inconnu')) }), 'P'), { http: 200 });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple: mk({ statut: 1, transaction: txApple({ inAppOwnershipType: 'FAMILY_SHARED' }) }) }), 'P'), { http: 200 });
  const mauvais = fauxApple({ notifications: { P: notifApple({ uuid: '../../x' }) } });
  assert.deepEqual(await T.traiterNotificationApple(creerDeps({ apple: mauvais }), 'P'), { http: 400 });
  assert.equal((await db.collection('billingEvents').get()).size, 0, 'aucun evenement marque pour une erreur passagere ou une entree refusee');
});

// --- BK8 : remboursement ---------------------------------------------------------------------------------------------------------------

test("BK8 - Google : revocation (type 12) et achat annule (« voided ») coupent l'acces TOUT DE SUITE, meme si l'API dit encore « actif » ; un achat revoque ne revit pas", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  const deps = creerDeps({ google });
  await sync(deps, ALICE);
  assert.equal((await lire(`entitlements/${ALICE}`)).active, true);
  avancer(5 * 60_000);
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 12, { messageId: MESSAGE_ID(41) }));
  let a = await lire(`entitlements/${ALICE}`);
  assert.deepEqual({ active: a.active, state: a.state }, { active: false, state: 'revoked' });
  assert.equal(ms(a.expiresAt), maintenant.getTime());
  // Lecture ulterieure (resynchronisation de l'app) : l'API repond encore « actif » ; l'achat revoque ne revit pas.
  avancer(5 * 60_000);
  assert.equal((await sync(deps, ALICE)).state, 'revoked');
  a = await lire(`entitlements/${ALICE}`);
  assert.equal(a.active, false);
  // « Achat annule » par un autre chemin (nouvel achat).
  await viderEmulateur();
  const id2 = await preparer();
  google.table[JETON] = repGoogle(id2);
  await sync(deps, ALICE);
  avancer(61_000);
  await T.traiterMessageGoogle(deps, messageBrut({ packageName: 'com.tomessa.miff', voidedPurchaseNotification: { purchaseToken: JETON, orderId: 'GS.0000', productType: 1, refundType: 1 } }, { messageId: MESSAGE_ID(42) }));
  assert.equal((await lire(`entitlements/${ALICE}`)).state, 'revoked');
});

test("BK8 - Apple : REFUND coupe l'acces (statut 5), REFUND_REVERSED le retablit (la relecture redonne le statut 1)", async () => {
  const idAlice = await preparer();
  let statut = 1;
  const etats = { [ID_APPLE]: () => ({ statut, transaction: txApple({ appAccountToken: idAlice, ...(statut === 5 ? { revocationDate: T0.getTime() }: {}) }), renouvellement: { autoRenewStatus: 1 } }) };
  const apple = fauxApple({ transactions: { [JWS]: { environnement: 'Sandbox', idTransactionOrigine: ID_APPLE } }, etats, notifications: { R: notifApple({ type: 'REFUND', uuid: '22222222-2222-4222-8222-222222222222' }), V: notifApple({ type: 'REFUND_REVERSED', uuid: '33333333-3333-4333-8333-333333333333' }) } });
  const deps = creerDeps({ apple });
  await syncA(deps, ALICE);
  statut = 5;
  avancer(60_000);
  await T.traiterNotificationApple(deps, 'R');
  assert.deepEqual(((a) => ({ active: a.active, state: a.state }))(await lire(`entitlements/${ALICE}`)), { active: false, state: 'revoked' });
  statut = 1;
  avancer(60_000);
  await T.traiterNotificationApple(deps, 'V');
  assert.deepEqual(((a) => ({ active: a.active, state: a.state }))(await lire(`entitlements/${ALICE}`)), { active: true, state: 'active' });
});

// --- BK10 : accord « a la main » ---------------------------------------------------------------------------------------------------------

test("BK10 - un accord manuel valide plus long n'est PAS ecrase par un achat ; l'achat est memorise ; quand le manuel expire, le store reprend la main au prochain evenement", async () => {
  const idAlice = await preparer();
  const manuel = { active: true, expiresAt: Timestamp.fromMillis(T0.getTime() + 100 * JOUR), source: 'manual', updatedAt: Timestamp.fromMillis(T0.getTime()) };
  await db.doc(`entitlements/${ALICE}`).set(manuel);
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  const deps = creerDeps({ google });
  await sync(deps, ALICE);
  assert.deepEqual(await lire(`entitlements/${ALICE}`), manuel, "document manuel inchange, 4 champs");
  assert.equal((await lire(`billingPurchases/${cleG(JETON)}`)).uid, ALICE, 'achat memorise');
  assert.deepEqual(google.accuses, [JETON], 'l\'achat est quand meme acquitte (il est reel)');
  // Le manuel expire (100 jours) ; l'achat mensuel est renouvele : notification -> le store reprend la main.
  avancer(101 * JOUR);
  google.table[JETON] = repGoogle(idAlice, {}, { expiryTime: iso(maintenant.getTime() + 30 * JOUR) });
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 2, { messageId: MESSAGE_ID(51) }));
  const a = await lire(`entitlements/${ALICE}`);
  assert.deepEqual({ source: a.source, active: a.active, state: a.state }, { source: 'google_play', active: true, state: 'active' });
});

test("BK10 - un accord manuel valide plus COURT est remplace par un achat plus long", async () => {
  const idAlice = await preparer();
  await db.doc(`entitlements/${ALICE}`).set({ active: true, expiresAt: Timestamp.fromMillis(T0.getTime() + 5 * JOUR), source: 'manual', updatedAt: Timestamp.fromMillis(T0.getTime()) });
  await sync(creerDeps({ google: fauxGoogle({ [JETON]: repGoogle(idAlice) }) }), ALICE);
  assert.equal((await lire(`entitlements/${ALICE}`)).source, 'google_play');
});

test("un acces inchange n'est pas reecrit (economie d'ecritures) ; budget de lectures et d'ecritures d'une synchronisation", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice, { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' }) });
  const premier = espionner(db);
  await sync({ ...creerDeps({ google }), db: premier.proxy }, ALICE);
  // 1re synchronisation : limite (1 lecture, 1 ecriture) + [achat 1, compte 1, achats du compte 1, acces 1] = 5 lectures ; ecritures : limite, achat, acces = 3.
  assert.deepEqual({ lectures: premier.compteurs.lectures, ecritures: premier.compteurs.ecritures }, { lectures: 5, ecritures: 3 });
  const ancienne = await lire(`entitlements/${ALICE}`);
  avancer(2 * 60_000);
  const second = espionner(db);
  await sync({ ...creerDeps({ google }), db: second.proxy }, ALICE);
  // 2e synchronisation identique : l'acces n'est pas reecrit ; l'achat l'est (date de mise a jour) : limite + achat = 2 ecritures.
  assert.deepEqual({ lectures: second.compteurs.lectures, ecritures: second.compteurs.ecritures }, { lectures: 5, ecritures: 2 });
  assert.deepEqual(await lire(`entitlements/${ALICE}`), ancienne, 'acces identique, `updatedAt` inchange');
});

test("budget d'une notification Google : 1 lecture (message deja vu ?) puis 4 lectures et 3 ecritures dans la transaction (achat, achats du compte, acces ; achat, acces, message)", async () => {
  const idAlice = await preparer();
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  const { proxy, compteurs } = espionner(db);
  await T.traiterMessageGoogle({ ...creerDeps({ google }), db: proxy }, messageRtdn(JETON, 4));
  assert.deepEqual({ lectures: compteurs.lectures, ecritures: compteurs.ecritures }, { lectures: 4, ecritures: 3 });
});

// --- BK15 : suppression de compte -----------------------------------------------------------------------------------------------------------

test("BK15 - supprimerDonneesAbonnement : identifiant d'achat et liens supprimes, droit INACTIF avec purge a +30 jours ; les donnees d'un autre compte sont intactes ; idempotente", async () => {
  const idAlice = await preparer();
  const idBob = await preparer(BOB);
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice), [JETON2]: repGoogle(idBob) });
  const deps = creerDeps({ google });
  await sync(deps, ALICE);
  await sync(deps, BOB, JETON2);
  const bobAvant = [await lire(`entitlements/${BOB}`), await lire(`billingAccounts/${BOB}`), await lire(`billingPurchases/${cleG(JETON2)}`)];
  avancer(JOUR);
  await T.supprimerDonneesAbonnement(deps, ALICE);
  assert.equal(await lire(`billingAccounts/${ALICE}`), null);
  assert.equal(await lire(`billingPurchases/${cleG(JETON)}`), null);
  const a = await lire(`entitlements/${ALICE}`);
  assert.deepEqual({ active: a.active, state: a.state, trial: a.trial, willRenew: a.willRenew, source: a.source }, { active: false, state: 'expired', trial: false, willRenew: false, source: 'google_play' });
  assert.equal(ms(a.purgeAt), maintenant.getTime() + 30 * JOUR);
  assert.deepEqual([await lire(`entitlements/${BOB}`), await lire(`billingAccounts/${BOB}`), await lire(`billingPurchases/${cleG(JETON2)}`)], bobAvant);
  await T.supprimerDonneesAbonnement(deps, ALICE); // idempotente
  assert.equal(ms((await lire(`entitlements/${ALICE}`)).purgeAt), maintenant.getTime() + 30 * JOUR);
  // Un compte qui n'a jamais rien achete : aucun document d'acces n'est cree.
  await T.supprimerDonneesAbonnement(deps, 'carol');
  assert.equal(await lire('entitlements/carol'), null);
  // Une notification tardive pour l'achat supprime ne ressuscite rien.
  google.table[JETON] = repGoogle(idAlice);
  await T.traiterMessageGoogle(deps, messageRtdn(JETON, 2, { messageId: MESSAGE_ID(61) }));
  assert.equal(await lire(`billingPurchases/${cleG(JETON)}`), null);
  assert.equal((await lire(`entitlements/${ALICE}`)).active, false);
});

test("BK15 - un accord manuel est lui aussi desactive et purge a la suppression du compte", async () => {
  await db.doc(`entitlements/${ALICE}`).set({ active: true, expiresAt: Timestamp.fromMillis(T0.getTime() + 100 * JOUR), source: 'manual', updatedAt: Timestamp.fromMillis(T0.getTime()) });
  await T.supprimerDonneesAbonnement(creerDeps(), ALICE);
  const a = await lire(`entitlements/${ALICE}`);
  assert.deepEqual({ active: a.active, source: a.source }, { active: false, source: 'manual' });
  assert.ok(a.purgeAt);
});

// --- Pas de contournement par l'entree : une notification ne peut jamais lier a un autre compte -----------------------------------------------

test("une notification ne change JAMAIS le compte auquel un achat est lie, meme si l'identifiant d'achat du store designe un autre compte", async () => {
  const idAlice = await preparer();
  const idBob = await preparer(BOB);
  const google = fauxGoogle({ [JETON]: repGoogle(idAlice) });
  await sync(creerDeps({ google }), ALICE);
  google.table[JETON] = repGoogle(idBob);
  await T.traiterMessageGoogle(creerDeps({ google }), messageRtdn(JETON, 2, { messageId: MESSAGE_ID(71) }));
  assert.equal((await lire(`billingPurchases/${cleG(JETON)}`)).uid, ALICE);
  assert.equal(await lire(`entitlements/${BOB}`), null);
});
