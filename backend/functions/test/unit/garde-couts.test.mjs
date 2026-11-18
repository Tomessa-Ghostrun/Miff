// Garde-fous de COÛT (SRV-1) : ce test ÉCHOUE si une fonction exportée n'a pas de région / maxInstances / délai / mémoire
// conformes, ou si le code contient un motif dangereux (boucle, console.log, clé de service...).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LIMITES, compteDeServiceValide, problemesDeGardeFous } from '../helpers/garde.mjs';

const require = createRequire(import.meta.url);
const srcDir = fileURLToPath(new URL('../../src/', import.meta.url));
const exportees = require('../../lib/index.js');

const bonEndpoint = () => ({
  platform: 'gcfv2',
  region: ['europe-west9'],
  maxInstances: 3,
  minInstances: 0,
  timeoutSeconds: 30,
  availableMemoryMb: 128,
  callableTrigger: {},
  serviceAccountEmail: 'notify-update@miff-3d283.iam.gserviceaccount.com',
});

// --- Le détecteur lui-même : il doit voir les erreurs, sinon les tests suivants ne prouvent rien ---------------------------

test('détecteur : accepte un endpoint conforme', () => {
  assert.deepEqual(problemesDeGardeFous('ok', bonEndpoint()), []);
});

test('détecteur : refuse une fonction sans maxInstances, hors région, trop longue, trop gourmande, avec retry ou instance permanente', () => {
  const cas = {
    'sans maxInstances': { ...bonEndpoint(), maxInstances: undefined },
    'maxInstances trop haut': { ...bonEndpoint(), maxInstances: 100 },
    'mauvaise région': { ...bonEndpoint(), region: ['us-central1'] },
    'sans région': { ...bonEndpoint(), region: undefined },
    '1re génération': { ...bonEndpoint(), platform: 'gcfv1' },
    'délai trop long': { ...bonEndpoint(), timeoutSeconds: 540 },
    'sans délai': { ...bonEndpoint(), timeoutSeconds: undefined },
    'mémoire trop haute': { ...bonEndpoint(), availableMemoryMb: 2048 },
    'instance permanente': { ...bonEndpoint(), minInstances: 1 },
    retry: { ...bonEndpoint(), callableTrigger: undefined, eventTrigger: { eventType: 'x', retry: true } },
    'secret lié': { ...bonEndpoint(), secretEnvironmentVariables: [{ key: 'X' }] },
    'HTTP public (httpsTrigger)': { ...bonEndpoint(), callableTrigger: undefined, httpsTrigger: {} },
    'planifiée (scheduleTrigger)': { ...bonEndpoint(), callableTrigger: undefined, scheduleTrigger: { schedule: 'every 5 minutes' } },
    'sans déclencheur': { ...bonEndpoint(), callableTrigger: undefined },
    'compte par défaut Compute': { ...bonEndpoint(), serviceAccountEmail: '123456789-compute@developer.gserviceaccount.com' },
    'compte App Engine par défaut': { ...bonEndpoint(), serviceAccountEmail: 'miff-3d283@appspot.gserviceaccount.com' },
    'compte nommé editor': { ...bonEndpoint(), serviceAccountEmail: 'miff-editor@miff-3d283.iam.gserviceaccount.com' },
    'identité par défaut (fonction métier)': { ...bonEndpoint(), serviceAccountEmail: undefined },
    "compte de l'Admin SDK": { ...bonEndpoint(), serviceAccountEmail: 'firebase-adminsdk-fbsvc@miff-3d283.iam.gserviceaccount.com' },
    "compte d'un autre projet (Ghostrun)": { ...bonEndpoint(), serviceAccountEmail: 'notify-update@ghostrun-app.iam.gserviceaccount.com' },
    'pas un endpoint': undefined,
  };
  for (const [nom, endpoint] of Object.entries(cas)) {
    assert.notDeepEqual(problemesDeGardeFous(nom, endpoint), [], `devrait être refusé : ${nom}`);
  }
});

test('détecteur : accepte un compte de service dédié explicite, refuse tout le reste', () => {
  assert.deepEqual(problemesDeGardeFous('ok', { ...bonEndpoint(), serviceAccountEmail: 'miff-notifications@miff-3d283.iam.gserviceaccount.com' }), []);
  assert.ok(compteDeServiceValide('miff-notifications'));
  for (const mauvais of ['', 'x', 'default', 'Miff Notifications', 'miff-admin', 'miff@gmail.com', 42, undefined]) {
    assert.ok(!compteDeServiceValide(mauvais), `devrait être refusé : ${String(mauvais)}`);
  }
});

// --- Les fonctions réellement exportées ---------------------------------------------------------------------------------

test('au moins une fonction est exportée et chacune respecte région, maxInstances, délai, mémoire, retry', () => {
  const noms = Object.keys(exportees);
  assert.ok(noms.length >= 1, 'index.ts doit exporter au moins une fonction');
  const problemes = noms.flatMap((nom) => problemesDeGardeFous(nom, exportees[nom]?.__endpoint));
  assert.deepEqual(problemes, []);
});

test('les valeurs par défaut de options.ts (setGlobalOptions : région, délai) sont bien appliquées', () => {
  const e = exportees.notifierUpdate.__endpoint;
  assert.equal(e.timeoutSeconds, LIMITES.timeoutSeconds);
  assert.deepEqual(e.region, ['europe-west9']);
  assert.ok(!e.minInstances);
});

// --- Motifs dangereux dans le code source ------------------------------------------------------------------------------

// Lecture RÉCURSIVE : un fichier rangé dans un sous-dossier de src/ est contrôlé comme les autres.
const sources = readdirSync(srcDir, { recursive: true })
  .map(String)
  .filter((f) => f.endsWith('.ts'))
  .map((f) => ({ f: f.replaceAll(String.fromCharCode(92), '/'), texte: readFileSync(join(srcDir, f), 'utf8') }));
const nomDe = (f) => f.split('/').pop();

test('code : la lecture de src/ voit bien tous les fichiers (au moins les 4 du socle)', () => {
  const noms = sources.map((s) => nomDe(s.f));
  for (const attendu of ['index.ts', 'options.ts', 'appelable.ts', 'journal.ts']) assert.ok(noms.includes(attendu), attendu);
});
/** Retire les commentaires pour ne pas signaler les mentions dans la documentation. */
const code = (t) => t.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '').replace(/\s\/\/.*$/gm, '');

test('code : aucune boucle infinie ni minuteur (une fonction ne tourne jamais en boucle)', () => {
  for (const { f, texte } of sources) {
    assert.doesNotMatch(
      code(texte),
      /while\s*\(\s*(true|1)\s*\)|for\s*\(\s*;\s*;\s*\)|setInterval\s*\(|setImmediate\s*\(/,
      `${f} : boucle infinie ou minuteur interdit`,
    );
  }
});

test('code : aucun console.* ni logger hors de journal.ts (tout passe par la journalisation assainie)', () => {
  for (const { f, texte } of sources) {
    const c = code(texte);
    assert.doesNotMatch(c, /console\.(log|info|warn|error|debug)/, `${f} : console.* interdit`);
    if (nomDe(f) !== 'journal.ts') {
      assert.doesNotMatch(c, /firebase-functions\/logger|firebase-functions\/v1\/logger/, `${f} : utiliser journaliser() de journal.ts`);
    }
  }
});

test('code : aucune clé de service ni identifiants explicites (identité par défaut gérée par Google)', () => {
  for (const { f, texte } of sources) {
    assert.doesNotMatch(
      code(texte),
      /credential\.cert|applicationDefault\s*\(\s*\{|GOOGLE_APPLICATION_CREDENTIALS|private_key|BEGIN [A-Z ]*PRIVATE KEY|\.json['"]/i,
      `${f} : aucune clé de service`,
    );
  }
});

/** Occurrences de serviceAccount dans le code : seule la forme "serviceAccount: 'nom-de-compte-dedie'" est acceptée. */
function problemesServiceAccount(texte) {
  const p = [];
  for (const m of texte.matchAll(/\bserviceAccount\w*/gi)) {
    const apres = texte.slice(m.index + m[0].length);
    const forme = /^\s*:\s*(["'])([^"']+)\1/.exec(apres);
    if (m[0] !== 'serviceAccount' || !forme) p.push(m[0] + ' : forme non autorisée (attendu : serviceAccount: "nom-de-compte-dédié")');
    else if (!compteDeServiceValide(forme[2])) p.push('compte "' + forme[2] + '" : doit être un compte DÉDIÉ explicite, jamais le compte par défaut ni une clé');
  }
  return p;
}

test('détecteur serviceAccount : accepte un compte dédié, refuse clé, compte par défaut, variable et valeur calculée', () => {
  assert.deepEqual(problemesServiceAccount('onCall({ serviceAccount: "miff-notifications" }, f)'), []);
  assert.deepEqual(problemesServiceAccount("setGlobalOptions({ serviceAccount: 'miff-fcm@miff-3d283.iam.gserviceaccount.com' })"), []);
  for (const mauvais of [
    'admin.credential.cert(serviceAccount)',
    'const serviceAccount = require("./cle.json")',
    'onCall({ serviceAccount: process.env.COMPTE }, f)',
    'onCall({ serviceAccount: "default" }, f)',
    'onCall({ serviceAccount: "123456789-compute@developer.gserviceaccount.com" }, f)',
    'onCall({ serviceAccountEmail: "miff-notifications" }, f)',
  ]) assert.notDeepEqual(problemesServiceAccount(mauvais), [], 'devrait être refusé : ' + mauvais);
});

test('code : serviceAccount accepté seulement avec un nom de compte dédié explicite (jamais une clé, jamais le compte par défaut)', () => {
  for (const { f, texte } of sources) assert.deepEqual(problemesServiceAccount(code(texte)), [], f);
});

test('fonctions exportées = la liste décidée par les tickets : `notifierAlerte` (AD-B2) et `notifierUpdate` (SRV-4B) ; en ajouter une exige de modifier ce test, en PR (`ping`, retirée, ne doit pas revenir)', () => {
  assert.deepEqual(Object.keys(exportees).sort(), ['notifierAlerte', 'notifierUpdate']);
});

test('identité : AUCUNE fonction ne peut tourner avec l\'identité par défaut du projet (compte Compute) ; toutes exigent un compte dédié DU PROJET Miff', () => {
  const { FONCTIONS_IDENTITE_PAR_DEFAUT_AUTORISEES } = require('../../lib/options.js');
  assert.deepEqual(FONCTIONS_IDENTITE_PAR_DEFAUT_AUTORISEES, [], 'liste vide : plus aucune exception');
  // Une fonction du nom de l'ancienne fonction de démonstration, sans compte dédié, est refusée comme n'importe quelle autre.
  assert.notDeepEqual(problemesDeGardeFous('ping', { ...bonEndpoint(), serviceAccountEmail: undefined }), []);
  assert.notDeepEqual(problemesDeGardeFous('autreFonction', { ...bonEndpoint(), serviceAccountEmail: undefined }), []);
  assert.notDeepEqual(problemesDeGardeFous('notifierUpdate', { ...bonEndpoint(), serviceAccountEmail: undefined }), []);
  assert.notDeepEqual(problemesDeGardeFous('notifierUpdate', { ...bonEndpoint(), serviceAccountEmail: 'notify-update@ghostrun-app.iam.gserviceaccount.com' }), [], 'compte d\'un autre projet (Ghostrun)');
  assert.notDeepEqual(problemesDeGardeFous('notifierUpdate', { ...bonEndpoint(), serviceAccountEmail: 'firebase-adminsdk-fbsvc@miff-3d283.iam.gserviceaccount.com' }), [], 'compte de l\'Admin SDK');
  assert.deepEqual(problemesDeGardeFous('notifierUpdate', bonEndpoint()), []);
});

test('notifierUpdate (SRV-4B) : déclenchée par Firestore sur photos/{uid} uniquement, europe-west9, 5 instances au plus, 256 Mio, 30 s, sans reprise, compte dédié', () => {
  const e = exportees.notifierUpdate.__endpoint;
  assert.deepEqual(problemesDeGardeFous('notifierUpdate', e), []);
  assert.equal(e.eventTrigger?.eventType, 'google.cloud.firestore.document.v1.written');
  assert.equal(e.eventTrigger?.eventFilterPathPatterns?.document, 'miffs/{miffId}/photos/{uid}');
  assert.ok(!e.eventTrigger?.retry, 'pas de nouvelle tentative automatique');
  assert.equal(e.maxInstances, LIMITES.maxInstancesPlafond);
  assert.ok(e.maxInstances <= 10, 'jamais plus que les 10 du ticket');
  assert.equal(e.availableMemoryMb, 256);
  assert.equal(e.timeoutSeconds, 30);
  assert.ok(!e.minInstances);
  assert.equal(e.serviceAccountEmail, 'notify-update@miff-3d283.iam.gserviceaccount.com');
  assert.ok(!e.callableTrigger && !e.httpsTrigger, 'aucun point d\'entrée appelable depuis Internet');
});

test('comptes de service : chaque fonction écrit le sien en littéral dans SON fichier et nulle part ailleurs (deux comptes DISTINCTS : notify-update, notify-alert)', () => {
  const attendu = {
    'notifierUpdate.ts': 'serviceAccount: "notify-update@miff-3d283.iam.gserviceaccount.com"',
    'notifierAlerte.ts': 'serviceAccount: "notify-alert@miff-3d283.iam.gserviceaccount.com"',
  };
  for (const { f, texte } of sources) {
    const m = code(texte).match(/\bserviceAccount\s*:\s*"([^"]+)"/g) ?? [];
    if (attendu[nomDe(f)]) assert.deepEqual(m, [attendu[nomDe(f)]], f);
    else assert.deepEqual(m, [], `${f} : serviceAccount interdit hors des déclencheurs`);
  }
  assert.notEqual(attendu['notifierUpdate.ts'], attendu['notifierAlerte.ts']);
});

test("notifierAlerte (AD-B2) : déclenchée par Firestore sur alerts/{uid} uniquement, europe-west9, 5 instances au plus, 256 Mio, 30 s, sans reprise, compte dédié, aucun point d'entrée HTTP", () => {
  const e = exportees.notifierAlerte.__endpoint;
  assert.deepEqual(problemesDeGardeFous('notifierAlerte', e), []);
  assert.equal(e.eventTrigger?.eventType, 'google.cloud.firestore.document.v1.written');
  assert.equal(e.eventTrigger?.eventFilterPathPatterns?.document, 'miffs/{miffId}/alerts/{uid}');
  assert.ok(!e.eventTrigger?.retry, 'pas de nouvelle tentative automatique');
  assert.equal(e.maxInstances, LIMITES.maxInstancesPlafond);
  assert.equal(e.availableMemoryMb, 256);
  assert.equal(e.timeoutSeconds, 30);
  assert.ok(!e.minInstances);
  assert.equal(e.serviceAccountEmail, 'notify-alert@miff-3d283.iam.gserviceaccount.com');
  assert.notEqual(e.serviceAccountEmail, exportees.notifierUpdate.__endpoint.serviceAccountEmail, 'un compte de service par fonction');
  assert.ok(!e.callableTrigger && !e.httpsTrigger, "aucun point d'entrée appelable depuis Internet");
  assert.notDeepEqual(problemesDeGardeFous('notifierAlerte', { ...e, serviceAccountEmail: undefined }), [], 'identité par défaut refusée');
  assert.notDeepEqual(problemesDeGardeFous('notifierAlerte', { ...e, serviceAccountEmail: 'notify-alert@ghostrun-app.iam.gserviceaccount.com' }), [], 'compte de Ghostrun refusé');
  assert.notDeepEqual(problemesDeGardeFous('notifierAlerte', { ...e, maxInstances: 6 }), [], 'maxInstances au-delà du plafond refusé');
});

test("notifierAlerte : ne lit que status, startedAt, expiresAt et endedAt du document d'alerte, n'y écrit jamais, et ne transmet au traitement que l'identifiant de Miff, celui de l'auteur, la phase et deux dates", () => {
  const declencheur = code(sources.find((s) => nomDe(s.f) === 'notifierAlerte.ts').texte);
  assert.deepEqual(
    [...declencheur.matchAll(/\.get\("([^"]*)"\)/g)].map((m) => m[1]).sort(),
    ['endedAt', 'expiresAt', 'startedAt', 'status'],
    "seuls ces quatre champs sont lus : jamais deviceId, seq ni purgeAt (ni, bien sûr, une position : le document n'en contient aucune)",
  );
  assert.doesNotMatch(declencheur, /\.data\(\)|\.set\(|\.update\(|\.delete\(/);
  assert.ok(declencheur.includes('phase: decision.action'));
  assert.ok(declencheur.includes('startedAt: apres?.startedAt') && declencheur.includes('endedAt: apres?.endedAt'));
  // Aucune lecture ni écriture du dossier alerts/ par le traitement : la fonction ne boucle jamais sur elle-même.
  const traitement = code(sources.find((s) => nomDe(s.f) === 'alerteTraitement.ts').texte);
  assert.doesNotMatch(traitement, /\/alerts|alerts\//, "alerteTraitement.ts ne touche pas à miffs/{id}/alerts/");
});

test("notifierAlerte : AUCUNE fonction planifiée ni minuteur : l'expiration à 7 jours n'envoie rien (aucun déclencheur « schedule »), et jamais de notification de fin à l'expiration", () => {
  for (const { f, texte } of sources) {
    assert.doesNotMatch(code(texte), /onSchedule|scheduleTrigger|pubsub\.schedule/, `${f} : aucune fonction planifiée (SRV-6 n'est pas décidée)`);
  }
  const exportsSchedule = Object.values(exportees).filter((fn) => fn.__endpoint?.scheduleTrigger);
  assert.deepEqual(exportsSchedule, []);
});

test('notifierUpdate : n\'écrit jamais dans photos/ (pas de boucle sur elle-même) et ne lit que createdAt de la photo', () => {
  const traitement = code(sources.find((s) => nomDe(s.f) === 'notifTraitement.ts').texte);
  assert.doesNotMatch(traitement, /photos/, 'notifTraitement.ts ne touche pas à photos/');
  const declencheur = code(sources.find((s) => nomDe(s.f) === 'notifierUpdate.ts').texte);
  assert.deepEqual(declencheur.match(/\.get\("[^"]*"\)/g), ['.get("createdAt")'], 'seul createdAt est lu dans la photo');
  assert.doesNotMatch(declencheur, /\.data\(\)|\.set\(|\.update\(|\.delete\(/);
});

test("DESC-R : la description facultative d'une photo n'entre jamais dans la fonction (ni lue, ni copiée dans un message, une charge FCM, un journal ou notifState)", () => {
  for (const { f, texte } of sources) {
    // Code seulement (les commentaires qui expliquent l'absence de lecture restent permis) : aucun accès, clé ou nom de champ « description ».
    assert.doesNotMatch(code(texte), /description/i, `${f} : la description d'une photo ne doit jamais apparaître dans le code de la fonction`);
  }
  // Le déclencheur ne transmet au traitement que l'identifiant de la Miff, celui de l'auteur et createdAt (jamais le document, jamais .data()).
  const declencheur = code(sources.find((x) => nomDe(x.f) === 'notifierUpdate.ts').texte);
  assert.ok(declencheur.includes('{ miffId: evenement.params.miffId, auteurUid: evenement.params.uid, createdAt: apres.get("createdAt") }'), "le déclencheur ne transmet que miffId, auteurUid et createdAt");
});

test('notifierUpdate : jamais de sujet FCM (topic) ni d\'envoi multiple : un message par appareil', () => {
  for (const { f, texte } of sources) {
    assert.doesNotMatch(code(texte), /\btopic\b|subscribeToTopic|sendEachForMulticast|sendMulticast|sendAll\b/, `${f} : sujets et envois multiples interdits`);
  }
});

test('M8 : chemins Firestore touchés par les fonctions = liste fermée ; écriture SEULEMENT dans notifState (transaction) et suppression d\'un document devices avec précondition', () => {
  const traitement = code(sources.find((s) => nomDe(s.f) === 'notifTraitement.ts').texte);
  // Lectures : chemins passés à doc() / collection() dans tout src/ (notifTraitement.ts, puis alerteTraitement.ts : mêmes chemins + l'état d'alerte, sans fiche de partage).
  const chemins = sources.flatMap(({ texte }) => [...code(texte).matchAll(/\b(?:doc|collection)\(\s*`([^`]+)`/g)].map((m) => m[1])).sort();
  assert.deepEqual(chemins, [
    'miffs/${miffId}',
    'miffs/${miffId}',
    'miffs/${miffId}/members',
    'miffs/${miffId}/members',
    'miffs/${miffId}/members/${auteurUid}',
    'miffs/${miffId}/members/${auteurUid}',
    'notifState/${miffId}',
    'users/${d.uid}/devices/${deviceId}',
    'users/${d.uid}/devices/${deviceId}',
    'users/${d.uid}/session/active',
    'users/${d.uid}/session/active',
  ]);
  // L'état de la fonction d'alerte est un document SÉPARÉ de celui des updates (durée de vie de 9 jours, pas 2) : chemin construit par une seule fonction.
  const traitementAlerte = code(sources.find((s) => nomDe(s.f) === 'alerteTraitement.ts').texte);
  assert.match(traitementAlerte, /cheminEtatAlerte = \(miffId: string\): string => `notifState\/\$\{miffId\}_alertes`;/);
  assert.match(traitementAlerte, /const refEtat = db\.doc\(cheminEtatAlerte\(miffId\)\);/);
  // Écritures : une seule écriture de document par traitement (tx.set sur refEtat = état de la fonction) ; une seule suppression (devices, avec précondition sur la version lue).
  for (const { f, texte } of sources) {
    const ecritures = code(texte).match(/\.(set|update|create|add|delete|batch|bulkWriter|recursiveDelete)\(/g) ?? [];
    if (nomDe(f) === 'notifTraitement.ts') assert.deepEqual(ecritures, ['.set(', '.delete('], f);
    else if (nomDe(f) === 'alerteTraitement.ts') assert.deepEqual(ecritures, ['.set(', '.set('], `${f} : deux tx.set (début : horodatages et compteur ; fin : une horodatage), aucune suppression`);
    else assert.deepEqual(ecritures, [], `${f} : aucune écriture Firestore hors notifTraitement.ts et alerteTraitement.ts`);
  }
  assert.equal((traitementAlerte.match(/\.runTransaction\(/g) ?? []).length, 1);
  assert.match(traitementAlerte, /tx\.set\(refEtat,/);
  assert.match(traitement, /const refEtat = db\.doc\(`notifState\/\$\{miffId\}`\);/);
  assert.match(traitement, /tx\.set\(\s*refEtat,/);
  assert.equal((traitement.match(/\.runTransaction\(/g) ?? []).length, 1);
  assert.match(traitement, /await cible\.ref\.delete\(\{ lastUpdateTime: cible\.version \}\);/);
  // La référence supprimée vient de la lecture directe de users/{uid}/devices/{session.deviceId} (appareil.ref), jamais construite à la main.
  assert.match(traitement, /ref: appareil\.ref,/);
});

test("code : pas de fonction HTTP publique (onRequest) ni de onCall hors de appelable.ts (App Check et connexion toujours exigés)", () => {
  for (const { f, texte } of sources) {
    const c = code(texte);
    assert.doesNotMatch(c, /\bonRequest\b/, `${f} : onRequest interdit sans décision de ticket (pas d'App Check)`);
    if (nomDe(f) !== 'appelable.ts') assert.doesNotMatch(c, /\bonCall\b/, `${f} : utiliser appelable() de appelable.ts`);
  }
  const a = code(sources.find((s) => nomDe(s.f) === 'appelable.ts').texte);
  assert.match(a, /enforceAppCheck:\s*true/);
  assert.match(a, /requete\.auth/);
  assert.match(a, /sign_in_provider\s*===\s*"anonymous"/, 'la connexion anonyme est refusée explicitement');
});

test('options.ts : retry autorisé = liste vide, minInstances 0, enforceAppCheck global', () => {
  const o = code(readFileSync(join(srcDir, 'options.ts'), 'utf8'));
  assert.match(o, /FONCTIONS_AVEC_RETRY_AUTORISEES:\s*readonly string\[\]\s*=\s*\[\s*\]/);
  assert.match(o, /minInstances:\s*LIMITES\.minInstances/);
  assert.match(o, /minInstances:\s*0/);
  assert.match(o, /enforceAppCheck:\s*true/);
  assert.match(o, /setGlobalOptions\(/);
});
