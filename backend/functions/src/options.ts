// Garde-fous de COUT et de SECURITE appliques a TOUTES les fonctions (SRV-1).
// Ce fichier est le seul endroit ou ces valeurs sont decidees ; le test `test/unit/garde-couts.test.mjs` echoue si une
// fonction exportee s'en ecarte (region, maxInstances, delai, memoire, minInstances, retry).
//
// Pourquoi ces valeurs (voir docs/dev/deploiement-functions.md, section "Choix economiques") :
// - region europe-west9 (Paris) : meme region que Firestore ; Paris n'accepte que la 2e generation (OK, c'est ce qu'on utilise).
// - maxInstances bas : c'est le PLAFOND de montee en charge, donc le plafond de depense en cas d'emballement ou d'abus.
// - delai court : une fonction bloquee ne facture pas plus de quelques secondes.
// - memoire minimale : on ne la releve que fonction par fonction, avec une raison ecrite.
import { setGlobalOptions } from "firebase-functions/v2";

export const REGION = "europe-west9";

/** Valeurs appliquees par defaut ET plafonds verifies par les tests (relever un plafond = modifier ce fichier ET le test, en PR). */
export const LIMITES = {
  region: REGION,
  /** Plafond d'instances simultanees d'une fonction. */
  maxInstances: 3,
  /** Plafond d'instances pour une fonction precise, avec raison ecrite (aucune pour l'instant). */
  maxInstancesPlafond: 5,
  /** Les instances "chaudes" permanentes sont facturees meme sans trafic : toujours 0. */
  minInstances: 0,
  timeoutSeconds: 30,
  timeoutSecondsPlafond: 60,
  memory: "128MiB",
  /** Memoire maximale tolerable pour une fonction precise (ex. 256MiB si le SDK Admin manque de memoire). */
  memoryPlafondMiB: 256,
} as const;

/**
 * Nouvelles tentatives automatiques (`retry: true` des declencheurs d'evenements) : DESACTIVEES.
 * Une tentative rejouee en boucle sur une erreur permanente est le scenario classique de facture surprise.
 * Une fonction qui en a besoin doit etre ajoutee ici avec sa raison (et idempotente), jamais en silence.
 */
export const FONCTIONS_AVEC_RETRY_AUTORISEES: readonly string[] = [];

setGlobalOptions({
  region: LIMITES.region,
  maxInstances: LIMITES.maxInstances,
  minInstances: LIMITES.minInstances,
  timeoutSeconds: LIMITES.timeoutSeconds,
  memory: LIMITES.memory,
  // Fonctions appelables : App Check exige par defaut (appelable.ts le repose aussi explicitement).
  enforceAppCheck: true,
});
