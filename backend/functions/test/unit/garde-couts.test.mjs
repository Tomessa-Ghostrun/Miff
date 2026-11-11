// Garde-fous de COÛT (SRV-1) : ce test ÉCHOUE si une fonction exportée n'a pas de région / maxInstances / délai / mémoire
// conformes, ou si le code contient un motif dangereux (boucle, console.log, clé de service...).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LIMITES, problemesDeGardeFous } from '../helpers/garde.mjs';

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
    'pas un endpoint': undefined,
  };
  for (const [nom, endpoint] of Object.entries(cas)) {
    assert.notDeepEqual(problemesDeGardeFous(nom, endpoint), [], `devrait être refusé : ${nom}`);
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
      /credential\.cert|applicationDefault\s*\(\s*\{|serviceAccount|GOOGLE_APPLICATION_CREDENTIALS|private_key|BEGIN [A-Z ]*PRIVATE KEY/i,
      `${f} : aucune clé de service`,
    );
  }
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
