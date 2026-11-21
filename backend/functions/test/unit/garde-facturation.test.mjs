// SRV-7 : garde-fous de COUT et de SECURITE propres aux cinq fonctions du premium reel (criteres BK12, BK13, BK14 du ticket docs/tickets/premium-achat-reel.md), en plus de
// garde-couts.test.mjs (qui s'applique a TOUTES les fonctions : region, instances, delai, memoire, compte dedie, motifs dangereux).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LIMITES, FONCTIONS_HTTP_PUBLIQUES_AUTORISEES, problemesDeGardeFous } from '../helpers/garde.mjs';

const require = createRequire(import.meta.url);
const exportees = require('../../lib/index.js');
const options = require('../../lib/options.js');
const srcDir = fileURLToPath(new URL('../../src/', import.meta.url));
const racineFonctions = fileURLToPath(new URL('../../', import.meta.url));

const FACTURATION = ['preparerAchat', 'synchroniserAchat', 'recevoirNotificationsGooglePlay', 'recevoirNotificationsApple', 'supprimerDonneesAbonnement'];
const COMPTE = 'billing-sync@miff-3d283.iam.gserviceaccount.com';

const sources = readdirSync(srcDir, { recursive: true })
  .map(String)
  .filter((f) => f.endsWith('.ts'))
  .map((f) => ({ f: f.replaceAll(String.fromCharCode(92), '/'), texte: readFileSync(join(srcDir, f), 'utf8') }));
const nomDe = (f) => f.split('/').pop();
const code = (t) => t.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '').replace(/\s\/\/.*$/gm, '');
const source = (nom) => code(sources.find((s) => nomDe(s.f) === nom).texte);

// --- BK13 : limites -----------------------------------------------------------------------------------------------------------------------

test('BK13 - les cinq fonctions de facturation respectent les garde-fous (region, instances, delai, memoire, compte dedie, pas d\'instance permanente)', () => {
  for (const nom of FACTURATION) {
    const e = exportees[nom]?.__endpoint;
    assert.ok(e, `${nom} doit etre exportee`);
    assert.deepEqual(problemesDeGardeFous(nom, e), [], nom);
    assert.deepEqual(e.region, ['europe-west9'], nom);
    assert.ok(e.maxInstances <= LIMITES.maxInstancesPlafond && e.maxInstances === 3, `${nom} : 3 instances au plus (plafond du test : 5)`);
    assert.equal(e.availableMemoryMb, 256, nom);
    // Le point d'entree d'Apple a 60 s (plafond du garde-fou) : le controle de revocation des certificats (OCSP) a son propre delai de 30 s ; les quatre autres restent a 30 s.
    assert.equal(e.timeoutSeconds, nom === 'recevoirNotificationsApple' ? LIMITES.timeoutSecondsPlafond : 30, nom);
    assert.ok(!e.minInstances, `${nom} : aucune instance permanente`);
    assert.equal(e.serviceAccountEmail, COMPTE, `${nom} : compte dedie billing-sync`);
    assert.equal(e.platform, 'gcfv2');
  }
  assert.equal(options.COMPTE_SERVICE_FACTURATION, COMPTE, 'la constante de repli (usurpation de soi-meme) designe le meme compte que le litteral des declencheurs');
  assert.equal(options.LIMITES_FACTURATION.maxInstances, 3);
});

test('BK13 - declencheurs : trois fonctions appelables, une fonction Pub/Sub `play-rtdn` avec reprise, UNE seule fonction HTTP publique', () => {
  for (const nom of ['preparerAchat', 'synchroniserAchat', 'supprimerDonneesAbonnement']) {
    const e = exportees[nom].__endpoint;
    assert.ok(e.callableTrigger && !e.httpsTrigger && !e.eventTrigger, nom);
  }
  const rtdn = exportees.recevoirNotificationsGooglePlay.__endpoint;
  assert.equal(rtdn.eventTrigger.eventType, 'google.cloud.pubsub.topic.v1.messagePublished');
  assert.equal(rtdn.eventTrigger.eventFilters.topic, 'play-rtdn');
  assert.equal(rtdn.eventTrigger.retry, true, 'reprise autorisee : traitement idempotent (BK6)');
  const apple = exportees.recevoirNotificationsApple.__endpoint;
  assert.ok(apple.httpsTrigger && !apple.callableTrigger && !apple.eventTrigger);
  assert.deepEqual(apple.httpsTrigger.invoker, ['public'], "invoker public voulu : Apple n'envoie ni connexion ni jeton");
  assert.deepEqual(FONCTIONS_HTTP_PUBLIQUES_AUTORISEES, ['recevoirNotificationsApple']);
  const http = Object.entries(exportees).filter(([, f]) => f.__endpoint?.httpsTrigger).map(([n]) => n);
  assert.deepEqual(http, ['recevoirNotificationsApple'], 'une seule fonction joignable sans connexion');
});

test('BK13 - reprise automatique : SEULE la fonction RTDN la demande ; une autre fonction (ou le point Apple) avec reprise est refusee', () => {
  const avecReprise = Object.entries(exportees).filter(([, f]) => f.__endpoint?.eventTrigger?.retry).map(([n]) => n);
  assert.deepEqual(avecReprise, ['recevoirNotificationsGooglePlay']);
  assert.deepEqual(options.FONCTIONS_AVEC_RETRY_AUTORISEES, ['recevoirNotificationsGooglePlay']);
  const e = exportees.synchroniserAchat.__endpoint;
  assert.notDeepEqual(problemesDeGardeFous('synchroniserAchat', { ...e, callableTrigger: undefined, eventTrigger: { eventType: 'x', retry: true } }), []);
});

test("BK13 - le detecteur refuse une fonction HTTP publique autre que celle d'Apple, un secret lie, un compte d'un autre projet, un compte « admin »", () => {
  const e = exportees.recevoirNotificationsApple.__endpoint;
  assert.deepEqual(problemesDeGardeFous('recevoirNotificationsApple', e), []);
  assert.notDeepEqual(problemesDeGardeFous('autreFonction', e), [], 'httpsTrigger sous un autre nom');
  assert.notDeepEqual(problemesDeGardeFous('recevoirNotificationsApple', { ...e, secretEnvironmentVariables: [{ key: 'X' }] }), []);
  assert.notDeepEqual(problemesDeGardeFous('recevoirNotificationsApple', { ...e, serviceAccountEmail: 'billing-sync@ghostrun-app.iam.gserviceaccount.com' }), []);
  assert.notDeepEqual(problemesDeGardeFous('recevoirNotificationsApple', { ...e, serviceAccountEmail: 'billing-admin@miff-3d283.iam.gserviceaccount.com' }), []);
  assert.notDeepEqual(problemesDeGardeFous('recevoirNotificationsApple', { ...e, maxInstances: 50 }), []);
});

test("fonctions appelables : App Check et connexion exiges par la fabrique commune, jeton a usage unique NON active en v1 (decision documentee)", () => {
  for (const nom of ['preparerAchat.ts', 'synchroniserAchat.ts', 'supprimerDonneesAbonnement.ts']) {
    const c = source(nom);
    assert.match(c, /appelable[<(]/, `${nom} : passe par appelable()`);
    assert.doesNotMatch(c, /onCall|enforceAppCheck|cors/, `${nom} : ne reconfigure ni App Check ni CORS`);
    assert.doesNotMatch(c, /consumeAppCheckToken/);
  }
  const a = source('appelable.ts');
  assert.match(a, /\{\s*\.\.\.options,\s*enforceAppCheck:\s*true,\s*cors:\s*false\s*\}/, 'les options de la fonction ne peuvent jamais desactiver App Check (posees APRES)');
});

// --- C1 : verifications en ligne des certificats Apple -----------------------------------------------------------------------------------

test("C1 - les verifications EN LIGNE (revocation, date du jour) des certificats Apple sont actives par defaut dans le code ; jamais desactivees dans src/ (seuls les tests les coupent)", () => {
  assert.match(source('achatApple.ts'), /const enLigne = options\.verificationsEnLigne \?\? true;/, "valeur par defaut : vrai");
  for (const { f, texte } of sources) {
    const c = code(texte);
    assert.doesNotMatch(c, /verificationsEnLigne\s*:\s*false|enableOnlineChecks\s*:\s*false|enLigne\s*=\s*false/, `${f} : verifications en ligne desactivees`);
    if (nomDe(f) !== 'achatApple.ts') assert.doesNotMatch(c, /SignedDataVerifier/, `${f} : seul achatApple.ts construit un verificateur`);
  }
  // Le verificateur recoit bien ce booleen (et non une constante).
  assert.match(source('achatApple.ts'), /new bib\.SignedDataVerifier\(racines, enLigne,/);
  // Aucun appelant de creerClientApple dans src/ ne passe l'option.
  assert.doesNotMatch(source('achatDeps.ts'), /verificationsEnLigne/);
  // Le test lui-meme sait detecter l'erreur (garde-fou du garde-fou).
  assert.match('{ verificationsEnLigne: false }', /verificationsEnLigne\s*:\s*false/);
});

// --- BK14 : secrets -------------------------------------------------------------------------------------------------------------------------

test("BK14 - aucun secret lie a une fonction, aucune cle dans le code ; la cle Apple n'est lue qu'a UN endroit (configApple.ts), par le gestionnaire de secrets, jamais journalisee", () => {
  for (const nom of FACTURATION) assert.ok(!exportees[nom].__endpoint.secretEnvironmentVariables?.length, nom);
  for (const { f, texte } of sources) {
    const c = code(texte);
    assert.doesNotMatch(c, /defineSecret|\bsecrets\s*:|SecretManagerServiceClient|\.p8|BEGIN [A-Z ]*KEY/, `${f} : aucun secret lie, aucune cle`);
    if (nomDe(f) !== 'configApple.ts') assert.doesNotMatch(c, /secretmanager\.googleapis\.com|SECRET_APPLE/, `${f} : la cle Apple n'est lue que par configApple.ts`);
  }
  const conf = source('configApple.ts');
  assert.match(conf, /secrets\/\$\{SECRET_APPLE\}\/versions\/latest:access/);
  assert.match(conf, /export const SECRET_APPLE = "apple-iap-key";/);
  // La valeur du secret ne sort jamais de configApple.ts vers un journal ou un message d'erreur.
  assert.doesNotMatch(conf, /journaliser|console|logger/);
  assert.doesNotMatch(conf, /new ErreurAchat\([^)]*(cle|secret)/i);
});

test("aucune cle de compte de service : l'identite vient de l'environnement (jetonsGoogle.ts), le repli est l'usurpation de SOI-MEME par IAM Credentials, jamais une cle", () => {
  const j = source('jetonsGoogle.ts');
  assert.match(j, /new GoogleAuth\(\{ scopes: \[portee\] \}\)/);
  assert.match(j, /targetPrincipal: COMPTE_SERVICE_FACTURATION/);
  assert.doesNotMatch(j, /keyFile|keyFilename|credentials\s*:|fromJSON|private_key|client_email/);
});

// --- BK12 : journaux ------------------------------------------------------------------------------------------------------------------------

test('BK12 - journaux de la facturation : chaque appel est « billing.* » avec un code fixe et des champs de la liste blanche ; aucune variable (jeton, uid, identifiant, transaction) n\'y entre', () => {
  const fichiers = ['achatTraitement.ts', 'preparerAchat.ts', 'synchroniserAchat.ts', 'supprimerDonneesAbonnement.ts', 'recevoirNotificationsApple.ts', 'recevoirNotificationsGooglePlay.ts'];
  let appels = 0;
  for (const nom of fichiers) {
    const c = source(nom);
    for (const m of c.matchAll(/journaliser\(([^;]*?)\);/gs)) {
      appels += 1;
      const arg = m[1];
      assert.match(arg, /^("(info|warn|error)"|e\.transitoire \? "warn" : "info"), "billing\.[a-z]+\.[a-z-]+"/, `${nom} : niveau et code fixes : ${arg.slice(0, 60)}`);
      // Champs : uniquement resultat, motif, lectures, ecritures ; valeurs : mots fixes, raison d'une ErreurAchat ou compteurs.
      const champs = [...arg.matchAll(/\b([a-zA-Z]+)\s*:/g)].map((x) => x[1]);
      for (const champ of champs) assert.ok(['resultat', 'motif', 'lectures', 'ecritures'].includes(champ), `${nom} : champ de journal interdit « ${champ} »`);
      assert.doesNotMatch(arg, /jeton|uid|purchaseId|signed|token|email|notification\.(?!motif\b)|message\.|\bcle\b|evenementId|identite/i, `${nom} : aucune donnee personnelle ou technique dans un journal : ${arg.slice(0, 80)}`);
    }
  }
  assert.ok(appels >= 25, `les appels de journal sont bien trouves (${appels})`);
  // Aucune sortie directe : tout passe par journaliser() (le test garde-couts interdit console.* et logger hors de journal.ts).
  for (const { f, texte } of sources) if (/(billing|achat|Achat|Apple|Google)/.test(nomDe(f))) assert.doesNotMatch(code(texte), /console\.|logger\./, f);
});

test('BK12 - la liste des motifs de journal ne contient QUE des mots fixes (aucune donnee), y compris ceux de la facturation', () => {
  const { MOTIFS } = require('../../lib/journal.js');
  for (const motif of MOTIFS) assert.match(motif, /^[a-z_]{3,24}$/, motif);
  const { ErreurAchat } = require('../../lib/achatErreurs.js');
  for (const raison of ['entree_invalide', 'jeton_invalide', 'signature_invalide', 'achat_inconnu', 'produit_inconnu', 'autre_package', 'deja_lie', 'limite_frequence', 'partage_famille', 'test_refuse', 'non_configure', 'acces_refuse', 'indisponible', 'reponse_invalide']) {
    assert.ok(MOTIFS.includes(raison), `${raison} doit etre un motif de journal valide`);
    assert.equal(new ErreurAchat(raison).raison, raison);
  }
});

// --- Donnees : chemins, ecritures, reseau -------------------------------------------------------------------------------------------------------

test("`entitlements` et les collections billing* : les chemins ne sont construits que par achatChemins.ts ; l'acces n'est ecrit que par achatTraitement.ts", () => {
  for (const { f, texte } of sources) {
    const c = code(texte);
    if (nomDe(f) !== 'achatChemins.ts') assert.doesNotMatch(c, /(entitlements|billingAccounts|billingPurchases|billingEvents|billingConfig)\//, `${f} : chemin construit hors achatChemins.ts`);
    if (!['achatChemins.ts', 'achatTraitement.ts'].includes(nomDe(f))) assert.doesNotMatch(c, /\bcheminAcces\b/, `${f} : seul achatTraitement.ts ecrit l'acces`);
  }
  const t = source('achatTraitement.ts');
  const docs = [...t.matchAll(/\bdb\.doc\(([^()]*(?:\([^()]*\))?[^()]*)\)/g)].map((m) => m[1].replace(/\(.*\)/, '()'));
  const permis = new Set(['cheminCompte()', 'cheminAchat()', 'cheminAcces()', 'cheminEvenement()']);
  assert.ok(docs.length >= 8);
  for (const d of docs) assert.ok(permis.has(d), `db.doc(${d}) : chemin non fourni par achatChemins.ts`);
  const collections = [...t.matchAll(/\.collection\(([^)]*)\)/g)].map((m) => m[1]);
  for (const c of collections) assert.ok(['COLLECTION_ACHATS', 'COLLECTION_COMPTES'].includes(c), `collection ${c}`);
});

test("reseau : seuls l'API Google Play et le gestionnaire de secrets sont appeles par notre code (Apple passe par sa bibliotheque), chaque appel a un delai maximal, jamais de revocation ni de remboursement", () => {
  const hotes = new Set();
  for (const { f, texte } of sources) {
    for (const m of code(texte).matchAll(/https?:\/\/([a-z0-9.-]+)/g)) hotes.add(`${nomDe(f)} -> ${m[1]}`);
  }
  assert.deepEqual([...hotes].sort(), [
    'achatGoogle.ts -> androidpublisher.googleapis.com',
    'configApple.ts -> secretmanager.googleapis.com',
    'jetonsGoogle.ts -> www.googleapis.com',
  ]);
  for (const nom of ['achatGoogle.ts', 'configApple.ts']) assert.match(source(nom), /AbortSignal\.timeout\(/, `${nom} : delai maximal des appels`);
  for (const { f, texte } of sources) assert.doesNotMatch(code(texte), /:revoke|fullRefund|proratedRefund|refund/i, `${f} : le serveur ne revoque ni ne rembourse jamais un achat (decision documentee)`);
  const g = source('achatGoogle.ts');
  assert.match(g, /purchases\/subscriptionsv2\/tokens\/\$\{encodeURIComponent\(jeton\)\}/, 'le jeton d\'achat est ENCODE avant d\'entrer dans une URL (injection de chemin)');
  assert.match(g, /purchases\/subscriptions\/\$\{PRODUIT_GOOGLE\}\/tokens\/\$\{encodeURIComponent\(jeton\)\}:acknowledge/);
  assert.match(g, /const BASE = "https:\/\/androidpublisher\.googleapis\.com\/androidpublisher\/v3\/applications";/);
  assert.match(g, /\$\{BASE\}\/\$\{PACKAGE_GOOGLE\}\//, 'le package est la constante du code, jamais une entree');
});

test('dependances : la bibliotheque officielle d\'Apple et la bibliotheque d\'authentification de Google, en versions EXACTES, sans autre ajout', () => {
  const pkg = JSON.parse(readFileSync(join(racineFonctions, 'package.json'), 'utf8'));
  assert.deepEqual(Object.keys(pkg.dependencies).sort(), ['@apple/app-store-server-library', 'firebase-admin', 'firebase-functions', 'google-auth-library']);
  assert.match(pkg.dependencies['@apple/app-store-server-library'], /^\d+\.\d+\.\d+$/);
  assert.match(pkg.dependencies['google-auth-library'], /^\d+\.\d+\.\d+$/);
  assert.ok(Number(process.versions.node.split('.')[0]) >= 22, "la bibliotheque d'Apple exige Node 22 ou plus (runtime des fonctions : 24)");
});

test("l'identifiant du package Google Play et le bundle Apple sont des constantes du code (jamais une entree de l'appelant ni de l'environnement)", () => {
  const r = source('achatRegles.ts');
  assert.match(r, /export const PACKAGE_GOOGLE = "com\.tomessa\.miff";/);
  assert.match(r, /export const BUNDLE_APPLE = "com\.tomessa\.miff";/);
  assert.match(r, /export const PRODUIT_GOOGLE = "miff_plus";/);
  for (const { f, texte } of sources) assert.doesNotMatch(code(texte), /process\.env\[?\.?["']?(PACKAGE|BUNDLE|PRODUIT)/i, f);
});
