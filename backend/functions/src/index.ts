// Point d'entree des Cloud Functions Miff. SRV-1 = socle : UNE fonction d'exemple, aucune logique metier.
// Toute fonction exportee ici est deployee : en ajouter une est une decision de ticket, jamais un detail.
// Les tests (test/unit/garde-couts.test.mjs) refusent toute fonction hors des limites de options.ts.
import "./options.js";
import { appelable } from "./appelable.js";
import { journaliser } from "./journal.js";

/**
 * `ping` : fonction temoin. Elle ne lit ni n'ecrit Firestore, ne renvoie aucune donnee d'utilisateur, ne contient aucune
 * logique metier. Elle sert a prouver que la chaine "code -> deploiement -> appel depuis une app" fonctionne
 * (region, App Check, connexion) et pourra etre supprimee quand une vraie fonction (SRV-4) fera le meme office.
 */
export const ping = appelable<unknown, { ok: true }>(() => {
  journaliser("info", "ping.ok", { resultat: "ok" });
  return { ok: true };
});
