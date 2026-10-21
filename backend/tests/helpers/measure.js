// Mesure du nombre de lectures de règles par opération (voir pads.js pour le principe).
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { assertSafeEnvironment } from './guard.js';
import { PROJECT_ID, readRules, seedBase } from './fixtures.js';
import { injectPads } from './pads.js';

export const OUTPUT_DIR = fileURLToPath(new URL('../../test-output/', import.meta.url));

/**
 * @param {{
 *   name: string, limit: 10 | 20, hosts: {match: string, method: string}[],
 *   prepare?: (env: any) => Promise<void>, run: (env: any) => Promise<unknown>
 * }} scenario
 * @returns {Promise<{name: string, limit: number, status: 'ok' | 'baseline-failed' | 'inconclusive', maxExtraReads: number, readsUpperBound: number | null, error?: string}>}
 */
export async function measureReads({ name, limit, hosts, prepare, run }) {
  assertSafeEnvironment({ projectId: PROJECT_ID });
  const baseRules = readRules();
  let maxExtraReads = -1;
  let lastError;

  for (let k = 0; k <= limit + 1; k += 1) {
    const env = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: { rules: injectPads(baseRules, hosts, k) },
    });
    let ok = false;
    try {
      await env.clearFirestore();
      await seedBase(env);
      if (prepare) await prepare(env);
      try {
        await run(env);
        ok = true;
      } catch (error) {
        lastError = error;
      }
    } finally {
      await env.cleanup();
    }
    if (!ok) break;
    maxExtraReads = k;
  }

  if (maxExtraReads < 0) {
    return {
      name, limit, status: 'baseline-failed', maxExtraReads, readsUpperBound: null,
      error: String(lastError?.message ?? lastError),
    };
  }
  if (maxExtraReads >= limit + 1) {
    // Les lectures factices n'ont pas été comptées : la mesure ne prouve rien.
    return { name, limit, status: 'inconclusive', maxExtraReads, readsUpperBound: null };
  }
  return { name, limit, status: 'ok', maxExtraReads, readsUpperBound: limit - maxExtraReads };
}

export function writeReadsReport(results) {
  mkdirSync(OUTPUT_DIR, { recursive: true });
  writeFileSync(`${OUTPUT_DIR}rule-reads.json`, `${JSON.stringify(results, null, 2)}\n`);
  const lines = [
    '| Opération | Limite | Lectures de règles utilisées (borne haute) | Marge restante (au moins) | Statut |',
    '|---|---|---|---|---|',
  ];
  for (const r of results) {
    const used = r.readsUpperBound === null ? 'n/a' : `<= ${r.readsUpperBound}`;
    const margin = r.readsUpperBound === null ? 'n/a' : `>= ${r.maxExtraReads}`;
    let status = 'mesuré';
    if (r.status === 'baseline-failed') status = `ECHEC de l'opération de référence : ${r.error}`;
    else if (r.status === 'inconclusive') status = 'mesure non concluante';
    else if (r.maxExtraReads < 3) status = 'MARGE FAIBLE (moins de 3 lectures)';
    lines.push(`| ${r.name} | ${r.limit} | ${used} | ${margin} | ${status} |`);
  }
  writeFileSync(`${OUTPUT_DIR}rule-reads.md`, `${lines.join('\n')}\n`);
}
