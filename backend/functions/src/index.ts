// Point d'entree des Cloud Functions Miff. Toute fonction exportee ici est deployee : en ajouter une est une decision de ticket, jamais un detail.
// Les tests (test/unit/garde-couts.test.mjs) refusent toute fonction hors des limites de options.ts.
// La fonction de demonstration `ping` (SRV-1) a ete retiree : elle tournait avec l'identite par defaut du projet (compte Compute, potentiellement
// large) sans rien faire d'utile. Aucune fonction ne doit plus tourner avec cette identite (voir FONCTIONS_IDENTITE_PAR_DEFAUT_AUTORISEES).
import "./options.js";

/** `notifierUpdate` (SRV-4B) : notification "un proche a donne des nouvelles" aux autres membres quand une photo d'update est publiee. */
export { notifierUpdate } from "./notifierUpdate.js";
