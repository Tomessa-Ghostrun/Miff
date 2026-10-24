#!/usr/bin/env node
// Garde-fou de déploiement du site web Miff (AUTH-W). Lancé automatiquement par `firebase deploy` (hook
// « predeploy » de web/firebase.json) : un échec ici ANNULE le déploiement. Peut aussi être lancé à la main :
//   node scripts/web-predeploy-check.mjs
//
// Ce que le script refuse, sans exception (aucune variable ne le contourne) :
//   - un lancement depuis la CI (le déploiement est toujours manuel, en local) ;
//   - un projet Firebase autre que miff-3d283 ;
//   - un site périmé (sources modifiées depuis le dernier build), construit en mode démonstration ou sans clé d'API web ;
//   - toute page qui contient encore un emplacement « [À COMPLÉTER] », « [À VALIDER ...] », « [... À DÉFINIR] » ou
//     tout autre texte entre crochets de ce type (décision du PM du 2026-10-23 : plus de relecture juridique,
//     donc plus de « brouillon » : on publie des pages complètes ou on ne publie pas).
// Le déploiement lui-même exige en plus l'accord explicite du PM (voir web/README.md) : ce script ne le remplace pas.
//
// Aucun accès réseau, aucune commande Firebase : le script ne lit que web/public/ et les sources.

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { API_KEY_PATTERN, REPO_ROOT, computeSourceHash, findPlaceholders } from './build-web.mjs';

export const FIREBASE_PROJECT = 'miff-3d283';

function htmlFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...htmlFiles(full));
    else if (entry.name.endsWith('.html')) files.push(full);
  }
  return files.sort();
}

/** Retourne { errors, placeholders } : errors = tout ce qui bloque (y compris les emplacements restants). */
export function checkSite({ root = REPO_ROOT, publicDir = join(root, 'web', 'public'), legalDir, env = process.env } = {}) {
  const errors = [];

  if (env.CI || env.GITHUB_ACTIONS) {
    errors.push('Lancé depuis une CI : le déploiement du site web est toujours manuel, en local (accord explicite du PM).');
  }
  if (env.GCLOUD_PROJECT && env.GCLOUD_PROJECT !== FIREBASE_PROJECT) {
    errors.push(`Projet Firebase « ${env.GCLOUD_PROJECT} » refusé : seul ${FIREBASE_PROJECT} est autorisé pour Miff.`);
  }
  try {
    const rc = JSON.parse(readFileSync(join(root, 'web', '.firebaserc'), 'utf8'));
    const projects = Object.values(rc.projects ?? {});
    if (projects.length !== 1 || projects[0] !== FIREBASE_PROJECT) errors.push(`web/.firebaserc doit contenir uniquement le projet ${FIREBASE_PROJECT}.`);
  } catch {
    errors.push('web/.firebaserc illisible.');
  }

  const infoPath = join(publicDir, '.build-info.json');
  if (!existsSync(infoPath)) {
    errors.push('Aucun site construit (web/public/.build-info.json absent). Lance d’abord : node scripts/build-web.mjs');
    return { errors, placeholders: [] };
  }
  const info = JSON.parse(readFileSync(infoPath, 'utf8'));

  if (info.demo) errors.push('Le site a été construit en mode démonstration (--demo) : reconstruis-le sans cette option.');
  if (info.sourceHash !== computeSourceHash({ root, legalDir })) {
    errors.push('Le site est périmé : les sources (web/src, web/site.config.json, docs/legal, build-web.mjs) ont changé depuis le dernier build. Relance : node scripts/build-web.mjs');
  }

  const pages = htmlFiles(publicDir).map((file) => ({ file, text: readFileSync(file, 'utf8') }));
  const resetPage = pages.find((p) => p.file.endsWith('reset-password.html'));
  const key = resetPage?.text.match(/<meta name="miff-api-key" content="([^"]*)">/)?.[1] ?? '';
  if (!API_KEY_PATTERN.test(key) || !info.apiKeyInjected) {
    errors.push('Clé d’API web absente : construis le site avec la variable MIFF_WEB_API_KEY (voir web/README.md).');
  }

  const placeholders = [...new Set(pages.flatMap((page) => findPlaceholders(page.text)))];
  if (placeholders.length > 0) {
    errors.push(
      `${placeholders.length} emplacement(s) à compléter restent dans les pages (exemples : ${placeholders
        .slice(0, 3)
        .map((p) => p.slice(0, 50))
        .join(' ; ')}). Complète les textes de docs/legal puis reconstruis le site.`,
    );
  }
  return { errors, placeholders };
}

function main() {
  const { errors } = checkSite();
  if (errors.length === 0) {
    console.log('Garde-fou de déploiement : OK. Rappel : un déploiement exige l’accord explicite du PM et se fait uniquement en local.');
    return 0;
  }
  console.error('DÉPLOIEMENT REFUSÉ par le garde-fou de web/ :');
  for (const message of errors) console.error(`  - ${message}`);
  return 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exitCode = main();
}
