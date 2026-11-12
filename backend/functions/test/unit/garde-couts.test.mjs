// Garde-fous de COÛT (SRV-1) : ce test ÉCHOUE si une fonction exportée n'a pas de région / maxInstances / délai / mémoire
// conformes, ou si le code contient un motif dangereux (boucle, console.log, clé de service...).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LIMITES, COMPTE_DEDIE, problemesDeGardeFous } from '../helpers/garde.mjs';

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
    'identité par défaut (fonction métier)': { ...bonEndpoint(), serviceAccountEmail: undefined },
    'compte Compute par défaut': { ...bonEndpoint(), serviceAccountEmail: '29899146512-compute@developer.gserviceaccount.com' },
    'compte App Engine par défaut': { ...bonEndpoint(), serviceAccountEmail: 'miff-3d283@appspot.gserviceaccount.com' },
    "compte de l'Admin SDK": { ...bonEndpoint(), serviceAccountEmail: 'firebase-adminsdk-fbsvc@miff-3d283.iam.gserviceaccount.com' },
    "compte d'un autre projet (Ghostrun)": { ...bonEndpoint(), serviceAccountEmail: 'notify-update@ghostrun-app.iam.gserviceaccount.com' },
    'compte sans nom explicite': { ...bonEndpoint(), serviceAccountEmail: 'editor@miff-3d283.iam.gserviceaccount.com' },
    'pas un endpoint': undefined,
  };
  for (const [nom, endpoint] of Object.entries(cas)) {
    assert.notDeepEqual(problemesDeGardeFous(nom, endpoint), [], `devrait être refusé : ${nom}`);
  }
});

test("détecteur : `ping` (aucune donnée) peut garder l'identité par défaut, pas une fonction métier", () => {
  assert.deepEqual(problemesDeGardeFous('ping', { ...bonEndpoint(), serviceAccountEmail: undefined }), []);
  assert.notDeepEqual(problemesDeGardeFous('notifierUpdate', { ...bonEndpoint(), serviceAccountEmail: undefined }), []);
});

// --- Les fonctions réellement exportées ---------------------------------------------------------------------------------

test('au moins une fonction est exportée et chacune respecte région, maxInstances, délai, mémoire, retry', () => {
  const noms = Object.keys(exportees);
  assert.ok(noms.length >= 1, 'index.ts doit exporter au moins la fonction témoin');
  const problemes = noms.flatMap((nom) => problemesDeGardeFous(nom, exportees[nom]?.__endpoint));
  assert.deepEqual(problemes, []);
});

test('fonctions exportées = la liste décidée par les tickets : `ping` (SRV-1) et `notifierUpdate` (SRV-4B) ; en ajouter une exige de modifier ce test, en PR', () => {
  assert.deepEqual(Object.keys(exportees).sort(), ['notifierUpdate', 'ping']);
});

test('notifierUpdate (SRV-4B) : déclenché par Firestore sur photos/{uid} uniquement, europe-west9, 5 instances au plus, 256 Mio, 30 s, sans reprise, compte de service dédié', () => {
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
  assert.ok(!e.callableTrigger && !e.httpsTrigger, "aucun point d'entrée appelable depuis Internet");
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

const sources = readdirSync(srcDir)
  .filter((f) => f.endsWith('.ts'))
  .map((f) => ({ f, texte: readFileSync(join(srcDir, f), 'utf8') }));
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
    if (f !== 'journal.ts') {
      assert.doesNotMatch(c, /firebase-functions\/logger|firebase-functions\/v1\/logger/, `${f} : utiliser journaliser() de journal.ts`);
    }
  }
});

test('code : aucune clé de service ni identifiants explicites (identité par défaut gérée par Google)', () => {
  for (const { f, texte } of sources) {
    assert.doesNotMatch(
      code(texte),
      /credential\.cert|applicationDefault\s*\(\s*\{|GOOGLE_APPLICATION_CREDENTIALS|private_key|BEGIN [A-Z ]*PRIVATE KEY/i,
      `${f} : aucune clé de service`,
    );
  }
});

test("code : serviceAccount seulement avec le compte DÉDIÉ explicite (options.ts le définit, notifierUpdate.ts l'utilise), jamais ailleurs", () => {
  const o = code(sources.find((s) => s.f === 'options.ts').texte);
  const m = o.match(/COMPTE_SERVICE_NOTIFICATION\s*=\s*"([^"]+)"/);
  assert.ok(m, 'COMPTE_SERVICE_NOTIFICATION absent de options.ts');
  assert.match(m[1], COMPTE_DEDIE);
  for (const { f, texte } of sources) {
    const c = code(texte);
    const utilisations = c.match(/\bserviceAccount\s*:\s*[^,\n]+/g) ?? [];
    if (f === 'notifierUpdate.ts') assert.deepEqual(utilisations, ['serviceAccount: COMPTE_SERVICE_NOTIFICATION']);
    else assert.deepEqual(utilisations, [], `${f} : serviceAccount interdit hors notifierUpdate.ts`);
  }
});

test("code : la fonction de notification n'écrit jamais dans photos/ (pas de boucle sur elle-même) et ne lit que createdAt de la photo", () => {
  const traitement = code(sources.find((s) => s.f === 'notifTraitement.ts').texte);
  assert.doesNotMatch(traitement, /photos/, 'notifTraitement.ts ne touche pas à photos/');
  const declencheur = code(sources.find((s) => s.f === 'notifierUpdate.ts').texte);
  assert.deepEqual(declencheur.match(/\.get\("[^"]*"\)/g), ['.get("createdAt")'], 'seul createdAt est lu dans la photo');
  assert.doesNotMatch(declencheur, /\.data\(\)|\.set\(|\.update\(|\.delete\(/);
});

test("code : pas de fonction HTTP publique (onRequest) ni de onCall hors de appelable.ts (App Check et connexion toujours exigés)", () => {
  for (const { f, texte } of sources) {
    const c = code(texte);
    assert.doesNotMatch(c, /\bonRequest\b/, `${f} : onRequest interdit sans décision de ticket (pas d'App Check)`);
    if (f !== 'appelable.ts') assert.doesNotMatch(c, /\bonCall\b/, `${f} : utiliser appelable() de appelable.ts`);
  }
  const a = code(sources.find((s) => s.f === 'appelable.ts').texte);
  assert.match(a, /enforceAppCheck:\s*true/);
  assert.match(a, /requete\.auth/);
});

test('options.ts : retry autorisé = liste vide, minInstances 0, enforceAppCheck global', () => {
  const o = code(readFileSync(join(srcDir, 'options.ts'), 'utf8'));
  assert.match(o, /FONCTIONS_AVEC_RETRY_AUTORISEES:\s*readonly string\[\]\s*=\s*\[\s*\]/);
  assert.match(o, /minInstances:\s*LIMITES\.minInstances/);
  assert.match(o, /minInstances:\s*0/);
  assert.match(o, /enforceAppCheck:\s*true/);
  assert.match(o, /setGlobalOptions\(/);
});
