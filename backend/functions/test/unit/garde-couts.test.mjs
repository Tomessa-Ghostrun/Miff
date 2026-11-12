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
  assert.ok(noms.length >= 1, 'index.ts doit exporter au moins la fonction témoin');
  const problemes = noms.flatMap((nom) => problemesDeGardeFous(nom, exportees[nom]?.__endpoint));
  assert.deepEqual(problemes, []);
});

test('SRV-1 : seule la fonction témoin `ping` existe (aucune fonction métier avant le ticket qui la demande)', () => {
  assert.deepEqual(Object.keys(exportees), ['ping']);
});

test('les valeurs par défaut sont bien celles de options.ts (setGlobalOptions appliqué)', () => {
  const e = exportees.ping.__endpoint;
  assert.equal(e.maxInstances, LIMITES.maxInstances);
  assert.equal(e.timeoutSeconds, LIMITES.timeoutSeconds);
  assert.equal(e.availableMemoryMb, 128);
  assert.deepEqual(e.region, ['europe-west9']);
  assert.ok(e.callableTrigger, 'ping est une fonction appelable');
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
