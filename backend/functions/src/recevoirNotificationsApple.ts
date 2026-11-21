// SRV-7 : notifications serveur V2 d'Apple (App Store Server Notifications). SEUL POINT D'ENTREE PUBLIC du projet (httpsTrigger, joignable sans connexion ni App Check : Apple
// ne peut pas en fournir). La protection est la verification de la SIGNATURE (JWS, chaine de certificats jusqu'a la racine d'Apple : achatApple.ts), jamais un secret de lien.
// Rejet au plus tot et sans cout : seule la methode POST, un corps JSON { signedPayload } de forme valide et de taille bornee passent ; tout le reste est refuse AVANT tout acces a
// Firestore. Reponses : 200 = accuse de reception ; 400 = refuse ; 503 = reessayez (Apple relance a 1, 12, 24, 48 et 72 h en production, une seule fois en bac a sable).
import { onRequest } from "firebase-functions/v2/https";
import { creerDepsAchat } from "./achatDeps.js";
import { jwsValide } from "./achatRegles.js";
import { traiterNotificationApple } from "./achatTraitement.js";
import { journaliser } from "./journal.js";
import { LIMITES_FACTURATION } from "./options.js";

/** Une notification Apple fait quelques Kio ; au-dela de 32 Kio, ce n'est pas Apple. */
const TAILLE_MAX_CORPS = 32 * 1024;

export const recevoirNotificationsApple = onRequest(
  {
    // Compte DEDIE (voir preparerAchat.ts). Acces public voulu (Apple) : `invoker: "public"`, aucune autre fonction de Miff n'est publique (garde-couts.test.mjs).
    serviceAccount: "billing-sync@miff-3d283.iam.gserviceaccount.com",
    maxInstances: LIMITES_FACTURATION.maxInstances,
    memory: LIMITES_FACTURATION.memory,
    invoker: "public",
    cors: false,
  },
  async (requete, reponse) => {
    if (requete.method !== "POST") {
      journaliser("info", "billing.apple.ignore", { resultat: "refuse", motif: "methode" });
      reponse.set("Allow", "POST").status(405).end();
      return;
    }
    const brut = requete.rawBody;
    if (!brut || brut.length === 0 || brut.length > TAILLE_MAX_CORPS) {
      journaliser("info", "billing.apple.ignore", { resultat: "refuse", motif: "taille" });
      reponse.status(brut && brut.length > TAILLE_MAX_CORPS ? 413 : 400).end();
      return;
    }
    let charge: unknown;
    try {
      charge = JSON.parse(brut.toString("utf8"));
    } catch {
      charge = null;
    }
    const signe = typeof charge === "object" && charge !== null && !Array.isArray(charge) ? (charge as Record<string, unknown>)["signedPayload"] : undefined;
    if (!jwsValide(signe)) {
      journaliser("info", "billing.apple.ignore", { resultat: "refuse", motif: "entree_invalide" });
      reponse.status(400).end();
      return;
    }
    try {
      const resultat = await traiterNotificationApple(creerDepsAchat(), signe);
      reponse.status(resultat.http).end();
    } catch {
      // Exception inattendue (Firestore...) : journal fixe sans detail, 500 : Apple relancera.
      journaliser("error", "billing.apple.erreur", { resultat: "erreur" });
      reponse.status(500).end();
    }
  },
);
