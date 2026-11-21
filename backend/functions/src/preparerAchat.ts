// SRV-7 : `preparerAchat` (appelable). Remet a l'utilisateur CONNECTE son identifiant d'achat aleatoire (cree au premier appel, identique ensuite) : l'app le transmet au store
// a l'achat (Google `obfuscatedAccountId`, Apple `appAccountToken`). Aucune donnee en entree. App Check et connexion exiges (appelable.ts), connexion anonyme refusee.
import { appelable } from "./appelable.js";
import { versErreurAppelable } from "./achatErreurs.js";
import { creerDepsAchat } from "./achatDeps.js";
import { preparerAchat as preparer } from "./achatTraitement.js";
import { journaliser } from "./journal.js";
import { LIMITES_FACTURATION } from "./options.js";

export const preparerAchat = appelable<unknown, { purchaseId: string }>(
  async (requete) => {
    try {
      return await preparer(creerDepsAchat(), (requete.auth as { uid: string }).uid);
    } catch (e) {
      journaliser("error", "billing.prepare.erreur", { resultat: "erreur" });
      throw versErreurAppelable(e);
    }
  },
  {
    // Compte DEDIE aux roles minimaux, jamais le compte par defaut ; litteral exige par le test garde-couts. Un seul compte pour les 5 fonctions de facturation : docs/dev/deploiement-functions.md (SRV-7).
    serviceAccount: "billing-sync@miff-3d283.iam.gserviceaccount.com",
    maxInstances: LIMITES_FACTURATION.maxInstances,
    memory: LIMITES_FACTURATION.memory,
  },
);
