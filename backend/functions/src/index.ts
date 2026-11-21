// Point d'entree des Cloud Functions Miff. Toute fonction exportee ici est deployee : en ajouter une est une decision de ticket, jamais un detail.
// Les tests (test/unit/garde-couts.test.mjs) refusent toute fonction hors des limites de options.ts.
// La fonction de demonstration `ping` (SRV-1) a ete retiree : elle tournait avec l'identite par defaut du projet (compte Compute, potentiellement
// large) sans rien faire d'utile. Aucune fonction ne doit plus tourner avec cette identite (voir FONCTIONS_IDENTITE_PAR_DEFAUT_AUTORISEES).
import "./options.js";

/** `notifierUpdate` (SRV-4B) : notification "un proche a donne des nouvelles" aux autres membres quand une photo d'update est publiee. */
export { notifierUpdate } from "./notifierUpdate.js";

/** `notifierAlerte` (AD-B2) : notification prioritaire « un proche a declare une alerte » (debut) ou « a mis fin a son alerte » (fin manuelle seulement, jamais a l'expiration). */
export { notifierAlerte } from "./notifierAlerte.js";

// ---------------------------------------------------------------------------------------------------------------------------------
// Premium reel Miff+ (SRV-7, ticket docs/tickets/premium-achat-reel.md §10) : cinq fonctions, UN compte de service dedie `billing-sync`. Ecriture de `entitlements/{uid}` PAR LE SERVEUR SEUL.
// ---------------------------------------------------------------------------------------------------------------------------------
/** `preparerAchat` (appelable) : identifiant d'achat aleatoire du compte, transmis au store a l'achat. */
export { preparerAchat } from "./preparerAchat.js";
/** `synchroniserAchat` (appelable) : verification d'un achat (jeton Google / transaction signee Apple), liaison au compte, ecriture de l'acces. */
export { synchroniserAchat } from "./synchroniserAchat.js";
/** `recevoirNotificationsGooglePlay` (Pub/Sub `play-rtdn`) : notifications temps reel de Google Play (le message est un signal, l'etat est relu chez Google). */
export { recevoirNotificationsGooglePlay } from "./recevoirNotificationsGooglePlay.js";
/** `recevoirNotificationsApple` (HTTPS public, SEUL point d'entree public) : notifications serveur V2 d'Apple, verifiees par leur signature. */
export { recevoirNotificationsApple } from "./recevoirNotificationsApple.js";
/** `supprimerDonneesAbonnement` (appelable) : purge a la suppression du compte (identifiant d'achat, liens, droit inactif avec date de purge). */
export { supprimerDonneesAbonnement } from "./supprimerDonneesAbonnement.js";
