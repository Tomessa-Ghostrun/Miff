// Mesure du nombre de lectures de règles par opération (voir pads.js pour le principe).
//
// Limites constatées dans l'émulateur (sonde de calibration, tests/helpers/calibration.js, vérifiée à chaque exécution) :
//   - une lecture (get) ou une écriture isolée : 10 lectures de règles ;
//   - une LISTE (requête) : 20 dans l'émulateur, alors que la documentation Firebase annonce 10 pour les requêtes ;
//   - un lot d'écritures : 10 par écriture ET 20 pour l'ensemble du lot (cumul constaté : 7+7+6 passe, 7+7+7 échoue) ;
//   - cache : les lectures d'un même document ne sont comptées qu'une fois dans l'émulateur (la documentation dit seulement
//     « Some document access calls may be cached, and cached calls do not count towards the limits »).
//
// Cause des mesures « non concluantes » ou fausses du premier jet (R15) :
//   1. les lectures factices en `|| true` pouvaient ne pas être comptées (corrigé dès DUR-1 avec `!exists`) ;
//   2. la liste des statuts : l'outil cherchait jusqu'à 10+1 lectures factices alors que l'émulateur en accepte 20 pour une
//      liste : la requête ne échouait jamais, d'où « non concluant » (corrigé : recherche jusqu'à 20, limite documentée 10) ;
//   3. les lots : mettre toutes les lectures factices sur UNE écriture butte sur la limite de 10 de cette écriture avant la
//      limite de 20 du lot, ce qui donnait des chiffres plafonnés (« 10 à 12 ») qui n'étaient pas des totaux (corrigé :
//      measureLot, ci-dessous).
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { assertSafeEnvironment } from './guard.js';
import { PROJECT_ID, readRules, seedBase } from './fixtures.js';
import { injectPads, injectPadCounts } from './pads.js';

export const OUTPUT_DIR = fileURLToPath(new URL('../../test-output/', import.meta.url));

/** Limite par écriture dans un lot (constatée, voir calibration.js). */
const PER_WRITE_LIMIT = 10;
/** Limite d'un lot d'écritures (constatée et documentée). */
const BATCH_LIMIT = 20;

/** Exécute le scénario avec ces règles : vrai s'il réussit, faux s'il est refusé. */
async function passes(rulesText, { prepare, run }, errorSink) {
  const env = await initializeTestEnvironment({ projectId: PROJECT_ID, firestore: { rules: rulesText } });
  try {
    await env.clearFirestore();
    await seedBase(env);
    if (prepare) await prepare(env);
    try {
      await run(env);
      return true;
    } catch (error) {
      if (errorSink) errorSink.error = error;
      return false;
    }
  } finally {
    await env.cleanup();
  }
}

/**
 * Plus grand k de [0, hi] pour lequel `pass(k)` est vrai, en supposant la monotonie (plus de lectures factices = plus de
 * lectures : si k échoue, k+1 échoue). Retourne -1 si pass(0) échoue ; `hi` si pass(hi) réussit encore.
 */
export async function bisectMax(pass, hi) {
  if (!(await pass(0))) return -1;
  if (await pass(hi)) return hi;
  let lo = 0; // pass(lo) vrai
  let top = hi; // pass(top) faux
  while (top - lo > 1) {
    const mid = Math.floor((lo + top) / 2);
    if (await pass(mid)) lo = mid;
    else top = mid;
  }
  return lo;
}

/**
 * Mesure d'UNE opération (get, liste, écriture simple) ou d'un lot dont toutes les lectures factices sont placées sur `hosts`.
 * @param {{
 *   name: string, limit: number, hosts: {match: string, method: string}[],
 *   prepare?: (env: any) => Promise<void>, run: (env: any) => Promise<unknown>
 * }} scenario
 * @returns {Promise<{name: string, limit: number, status: 'ok' | 'baseline-failed' | 'inconclusive', maxExtraReads: number, readsUpperBound: number | null, error?: string}>}
 */
export async function measureReads({ name, limit, hosts, prepare, run }) {
  assertSafeEnvironment({ projectId: PROJECT_ID });
  const baseRules = readRules();
  const sink = {};
  const maxExtraReads = await bisectMax((k) => passes(injectPads(baseRules, hosts, k), { prepare, run }, sink), limit + 1);

  if (maxExtraReads < 0) {
    return {
      name, limit, status: 'baseline-failed', maxExtraReads, readsUpperBound: null,
      error: String(sink.error?.message ?? sink.error),
    };
  }
  if (maxExtraReads >= limit + 1) {
    // Les lectures factices n'ont pas été comptées : la mesure ne prouve rien.
    return { name, limit, status: 'inconclusive', maxExtraReads, readsUpperBound: null };
  }
  return { name, limit, status: 'ok', maxExtraReads, readsUpperBound: limit - maxExtraReads };
}

/**
 * Mesure d'un LOT d'écritures. `hosts` = une règle hôte par écriture du lot qui peut lire.
 *
 * Étape 1 : pour chaque écriture, lectures factices sur elle seule (limite 10 par écriture) -> lectures « facturées » à cette
 *   écriture (base_h). La somme des base_h est le total du lot tel que l'émulateur le compte (avec son cache).
 * Étape 2 : on vérifie ce total sur le LOT ENTIER : on ajoute le plus possible de lectures factices en les répartissant sans
 *   dépasser, pour chaque écriture, la limite de 10 (10 - base_h de place chacune) ; le plus grand nombre E qui passe encore
 *   prouve « lectures du lot <= 20 - E ». C'est cette borne prouvée qui est retenue ; la somme par écriture est conservée à titre
 *   d'information (elle aide à voir quelle écriture lit quoi).
 * @returns {Promise<{name: string, limit: 20, status: 'ok' | 'baseline-failed' | 'inconclusive', maxExtraReads: number, readsUpperBound: number | null, perOp?: {host: string, reads: number}[], sumPerOp?: number, spread?: number, error?: string}>}
 */
export async function measureLot({ name, hosts, prepare, run }) {
  assertSafeEnvironment({ projectId: PROJECT_ID });
  const hostNames = hosts.map((h) => `${h.match.replace(/^match \//, '').replace(/\/\{[^}]*\} \{$/, '')} ${h.method}`);
  const perOp = [];
  for (let i = 0; i < hosts.length; i += 1) {
    const r = await measureReads({ name, limit: PER_WRITE_LIMIT, hosts: [hosts[i]], prepare, run });
    if (r.status !== 'ok') return { ...r, name, limit: BATCH_LIMIT, error: r.error ?? `écriture ${hostNames[i]} seule : ${r.status}` };
    perOp.push({ host: hostNames[i], reads: r.readsUpperBound });
  }
  const sumPerOp = perOp.reduce((n, o) => n + o.reads, 0);

  // Répartition : on remplit d'abord les écritures qui ont le plus de place (10 - base_h).
  const room = perOp.map((o) => PER_WRITE_LIMIT - o.reads);
  const capacity = room.reduce((n, r) => n + r, 0);
  const allocate = (extra) => {
    const counts = hosts.map(() => 0);
    let left = extra;
    while (left > 0) {
      let best = -1;
      for (let i = 0; i < hosts.length; i += 1) {
        if (counts[i] < room[i] && (best < 0 || room[i] - counts[i] > room[best] - counts[best])) best = i;
      }
      if (best < 0) break;
      counts[best] += 1;
      left -= 1;
    }
    return counts;
  };
  const baseRules = readRules();
  const hi = Math.min(capacity, BATCH_LIMIT + 1);
  const sink = {};
  const maxExtra = await bisectMax((k) => passes(injectPadCounts(baseRules, hosts, allocate(k)), { prepare, run }, sink), hi);
  if (maxExtra < 0) {
    return { name, limit: BATCH_LIMIT, status: 'baseline-failed', maxExtraReads: maxExtra, readsUpperBound: null, perOp, error: String(sink.error?.message ?? sink.error) };
  }
  if (maxExtra >= BATCH_LIMIT + 1) {
    return { name, limit: BATCH_LIMIT, status: 'inconclusive', maxExtraReads: maxExtra, readsUpperBound: null, perOp, sumPerOp };
  }
  // Le lot passe avec maxExtra lectures factices en plus : il en utilise donc AU PLUS 20 - maxExtra. Cette borne est prouvée
  // (un lot qui réussit n'a dépassé ni la limite par écriture ni celle du lot), même si elle peut être un peu plus haute que la
  // somme des lectures par écriture mesurées une à une (constaté : création de Miff premium, 4 par écriture mais 6 pour le lot).
  const spread = BATCH_LIMIT - maxExtra;
  return { name, limit: BATCH_LIMIT, status: 'ok', maxExtraReads: maxExtra, readsUpperBound: spread, perOp, sumPerOp, spread };
}

export function writeReadsReport(results) {
  mkdirSync(OUTPUT_DIR, { recursive: true });
  writeFileSync(`${OUTPUT_DIR}rule-reads.json`, `${JSON.stringify(results, null, 2)}\n`);
  const lines = [
    '| Opération | Limite documentée | Lectures de règles utilisées (borne haute) | Marge restante (au moins) | Statut |',
    '|---|---|---|---|---|',
  ];
  for (const r of results) {
    const used = r.readsUpperBound === null ? 'n/a' : `<= ${r.readsUpperBound}`;
    // Marge par rapport à la limite DOCUMENTÉE (10 pour une requête, même si l'émulateur en accepte 20).
    const documented = r.docLimit ?? r.limit;
    const margin = r.readsUpperBound === null ? 'n/a' : `>= ${documented - r.readsUpperBound}`;
    let status = 'mesuré';
    if (r.status === 'baseline-failed') status = `ECHEC de l'opération de référence : ${r.error}`;
    else if (r.status === 'inconclusive') status = 'mesure non concluante';
    else if (documented - r.readsUpperBound < 3) status = 'MARGE FAIBLE (moins de 3 lectures)';
    const perOp = r.perOp ? ` ; par écriture (somme ${r.sumPerOp}) : ${r.perOp.map((o) => `${o.host} ${o.reads}`).join(', ')}` : '';
    lines.push(`| ${r.name} | ${documented} | ${used} | ${margin} | ${status}${perOp} |`);
  }
  writeFileSync(`${OUTPUT_DIR}rule-reads.md`, `${lines.join('\n')}\n`);
}
