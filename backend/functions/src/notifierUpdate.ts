// Declencheur SRV-4B : une update (photo) est publiee dans une Miff -> notification discrete aux AUTRES membres (ticket
// docs/tickets/notification-update-membres.md). Toute la logique est dans notifTraitement.ts (testee avec un faux client FCM).
//
// Declencheur : creation OU remplacement de miffs/{miffId}/photos/{uid}. Un effacement (photo expiree, arret du partage, depart) ne declenche RIEN.
// Pourquoi la photo et pas le statut : le statut est aussi ecrit par les arrivees et departs de lieu ; la photo n'est ecrite que par une publication.
// AUCUNE lecture du contenu de la photo : seul `createdAt` est lu. Cette fonction n'ecrit JAMAIS dans photos/ (pas de boucle sur elle-meme).
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { appAdmin } from "./appAdmin.js";
import { LIMITES_NOTIFICATION } from "./options.js";
import { journaliser } from "./journal.js";
import { creerClientFcm } from "./notifFcm.js";
import { traiterPhotoPubliee } from "./notifTraitement.js";

export const notifierUpdate = onDocumentWritten(
  {
    document: "miffs/{miffId}/photos/{uid}",
    // Compte DEDIE aux roles minimaux, jamais le compte par defaut (revue de #194) ; litteral exige par le test garde-couts. A creer par le PM : docs/dev/deploiement-functions.md §1.4.
    serviceAccount: "notify-update@miff-3d283.iam.gserviceaccount.com",
    maxInstances: LIMITES_NOTIFICATION.maxInstances,
    memory: LIMITES_NOTIFICATION.memory,
    retry: false,
  },
  async (evenement) => {
    const apres = evenement.data?.after;
    if (!apres?.exists) {
      journaliser("info", "notif.update.ignore", { resultat: "refuse", motif: "suppression" });
      return;
    }
    try {
      const app = appAdmin();
      await traiterPhotoPubliee(
        {
          db: getFirestore(app),
          fcm: creerClientFcm(),
          maintenant: () => new Date(),
          attendre: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
        },
        { miffId: evenement.params.miffId, auteurUid: evenement.params.uid, createdAt: apres.get("createdAt") },
      );
    } catch {
      // Initialisation impossible : journal fixe, sans le detail de l'exception (il pourrait contenir un chemin, donc un identifiant), et pas de relance.
      journaliser("warn", "notif.update.erreur", { resultat: "erreur" });
    }
  },
);
