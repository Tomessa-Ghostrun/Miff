// SRV-7 : `supprimerDonneesAbonnement` (appelable), a appeler AVANT la suppression du compte d'authentification (ticket §9.5). Supprime l'identifiant d'achat et les liens du compte,
// marque le droit inactif avec une date de purge a +30 jours. Ne resilie pas l'abonnement chez le store. Aucune donnee en entree : toujours le compte de l'appelant.
import { appelable } from "./appelable.js";
import { versErreurAppelable } from "./achatErreurs.js";
import { creerDepsAchat } from "./achatDeps.js";
import { supprimerDonneesAbonnement as supprimer } from "./achatTraitement.js";
import { journaliser } from "./journal.js";
import { LIMITES_FACTURATION } from "./options.js";

export const supprimerDonneesAbonnement = appelable<unknown, { ok: true }>(
  async (requete) => {
    try {
      await supprimer(creerDepsAchat(), (requete.auth as { uid: string }).uid);
      return { ok: true as const };
    } catch (e) {
      journaliser("error", "billing.suppression.erreur", { resultat: "erreur" });
      throw versErreurAppelable(e);
    }
  },
  {
    serviceAccount: "billing-sync@miff-3d283.iam.gserviceaccount.com",
    maxInstances: LIMITES_FACTURATION.maxInstances,
    memory: LIMITES_FACTURATION.memory,
  },
);
