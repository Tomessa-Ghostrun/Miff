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

/**
 * Fonctions autorisees a tourner avec l'identite PAR DEFAUT du projet (compte Compute, souvent Editeur) : uniquement les fonctions qui ne touchent
 * a aucune donnee (`ping`). Toute fonction metier declare un compte de service DEDIE (voir COMPTE_SERVICE_NOTIFICATION) ; le test le verifie.
 */
export const FONCTIONS_IDENTITE_PAR_DEFAUT_AUTORISEES: readonly string[] = ["ping"];

setGlobalOptions({
  region: LIMITES.region,
  maxInstances: LIMITES.maxInstances,
  minInstances: LIMITES.minInstances,
  timeoutSeconds: LIMITES.timeoutSeconds,
  memory: LIMITES.memory,
  // Fonctions appelables : App Check exige par defaut (appelable.ts le repose aussi explicitement).
  enforceAppCheck: true,
});

// ---------------------------------------------------------------------------------------------------------------------------------
// Fonction de notification des updates (SRV-4B, ticket docs/tickets/notification-update-membres.md §10).
// ---------------------------------------------------------------------------------------------------------------------------------
/**
 * Compte de service DEDIE de la fonction (revue de #194) : le compte "Compute" par defaut a souvent le role Editeur du projet, une fonction qui
 * lit Firestore et envoie des notifications ne doit pas s'en servir. Ce compte n'a que les roles minimaux (docs/dev/deploiement-functions.md
 * §1.4 : Firestore lecture/ecriture, envoi FCM, reception d'evenements, ecriture des journaux), AUCUNE cle JSON : c'est une identite geree par Google.
 * A creer par le PM AVANT le premier deploiement de cette fonction (sinon le deploiement echoue, et c'est le bon comportement).
 */
export const COMPTE_SERVICE_NOTIFICATION = "notify-update@miff-3d283.iam.gserviceaccount.com";

/**
 * Limites propres a la fonction de notification. Le ticket parlait de 10 instances ; 5 (= le plafond du garde, deja prevu pour "une fonction precise")
 * suffit : memoire 256 Mio => 1 demande a la fois par instance (concurrence 1), ~0,3 a 1 s par evenement => ~5 a 15 evenements par seconde, soit
 * plus de 100 000 evenements par heure, alors que l'estimation du ticket est de 20 000 PAR JOUR a 10 000 utilisateurs actifs par jour.
 * 256 Mio : le SDK Admin (Firestore + messagerie) est a l'etroit dans 128 Mio. Delai : 30 s (valeur globale), largement au-dessus des 2 reprises (2 s).
 * Relever ces valeurs = modifier LIMITES (plafonds) ET le test, dans une PR (voir docs/dev/deploiement-functions.md §9).
 */
export const LIMITES_NOTIFICATION = {
  maxInstances: LIMITES.maxInstancesPlafond,
  memory: "256MiB",
} as const;
