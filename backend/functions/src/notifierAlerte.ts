// Declencheur AD-B2 : une alerte « Je suis en danger » demarre ou se termine MANUELLEMENT dans une Miff -> notification prioritaire a TOUS les autres membres
// qui ont un appareil (ticket docs/tickets/alerte-danger.md §11). Toute la logique est dans alerteTraitement.ts (testee avec un faux client FCM) et
// alerteRegles.ts (regles pures).
//
// Declencheur : creation, remplacement ou modification de miffs/{miffId}/alerts/{uid}. Un effacement (arret du partage, depart, retrait, nettoyage, TTL) ne declenche
// RIEN, de meme qu'une ecriture sans changement d'etat et qu'une fin POSTERIEURE a l'echeance : l'EXPIRATION a 7 jours n'est une ecriture de personne (aucune
// fonction planifiee), donc elle ne notifie JAMAIS (decision du PM, D19).
// Seuls status, startedAt, expiresAt et endedAt sont lus dans le document (jamais deviceId, seq ni purgeAt). Cette fonction n'ecrit JAMAIS dans alerts/ (pas de
// boucle sur elle-meme) ; elle n'ecrit que son etat (notifState/{miffId}_alertes) et supprime un jeton que FCM dit inconnu.
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { appAdmin } from "./appAdmin.js";
import { LIMITES_NOTIFICATION } from "./options.js";
import { journaliser } from "./journal.js";
import { creerClientFcm } from "./notifFcm.js";
import { traiterAlerte } from "./alerteTraitement.js";
import { classerEcriture, type DonneesAlerte } from "./alerteRegles.js";

type Instantane = { exists: boolean; get(champ: string): unknown } | undefined;

const donnees = (instantane: Instantane): DonneesAlerte | null =>
  instantane?.exists
    ? { status: instantane.get("status"), startedAt: instantane.get("startedAt"), expiresAt: instantane.get("expiresAt"), endedAt: instantane.get("endedAt") }
    : null;

export const notifierAlerte = onDocumentWritten(
  {
    document: "miffs/{miffId}/alerts/{uid}",
    // Compte DEDIE aux roles minimaux, jamais le compte par defaut ; litteral exige par le test garde-couts. Decision du PM : MEME compte que notifierUpdate (ses 4 roles couvrent aussi cette fonction) ; seul le droit run.invoker sur le service notifieralerte est a poser apres le deploiement : docs/dev/deploiement-alerte-danger.md.
    serviceAccount: "notify-update@miff-3d283.iam.gserviceaccount.com",
    maxInstances: LIMITES_NOTIFICATION.maxInstances,
    memory: LIMITES_NOTIFICATION.memory,
    retry: false,
  },
  async (evenement) => {
    const avant = donnees(evenement.data?.before);
    const apres = donnees(evenement.data?.after);
    const decision = classerEcriture(avant, apres);
    if (decision.action === "rien") {
      journaliser("info", "notif.alerte.ignore", { resultat: "refuse", motif: decision.motif });
      return;
    }
    try {
      const app = appAdmin();
      await traiterAlerte(
        {
          db: getFirestore(app),
          fcm: creerClientFcm(),
          maintenant: () => new Date(),
          attendre: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
        },
        {
          miffId: evenement.params.miffId,
          auteurUid: evenement.params.uid,
          phase: decision.action,
          startedAt: apres?.startedAt,
          endedAt: apres?.endedAt,
        },
      );
    } catch {
      // Initialisation impossible : journal fixe, sans le detail de l'exception (il pourrait contenir un chemin, donc un identifiant), et pas de relance.
      journaliser("warn", "notif.alerte.erreur", { resultat: "erreur" });
    }
  },
);
