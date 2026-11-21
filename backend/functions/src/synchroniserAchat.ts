// SRV-7 : `synchroniserAchat` (appelable). L'app presente un achat : jeton d'achat Google ou transaction signee Apple. Le serveur verifie aupres du store, applique les regles de
// liaison (achatTraitement.ts), ecrit `entitlements/{uid}` et (Google) acquitte l'achat. Limite : 1 appel par minute et par compte. Reponse : l'etat seulement ; l'app lit ensuite
// `entitlements/{uid}`. Entree : { store: "google_play", purchaseToken } ou { store: "app_store", signedTransaction } (aucun autre champ).
import { appelable } from "./appelable.js";
import { versErreurAppelable } from "./achatErreurs.js";
import { creerDepsAchat } from "./achatDeps.js";
import { synchroniserAchat as synchroniser, validerEntreeSynchro } from "./achatTraitement.js";
import { journaliser } from "./journal.js";
import { LIMITES_FACTURATION } from "./options.js";
import type { Etat } from "./achatRegles.js";

export const synchroniserAchat = appelable<unknown, { state: Etat }>(
  async (requete) => {
    try {
      const entree = validerEntreeSynchro(requete.data);
      return await synchroniser(creerDepsAchat(), (requete.auth as { uid: string }).uid, entree);
    } catch (e) {
      // Les refus attendus sont deja journalises par le traitement (code fixe) ; ici seulement l'inattendu.
      if (!(e instanceof Error && e.name === "ErreurAchat")) journaliser("error", "billing.sync.erreur", { resultat: "erreur" });
      throw versErreurAppelable(e);
    }
  },
  {
    serviceAccount: "billing-sync@miff-3d283.iam.gserviceaccount.com",
    maxInstances: LIMITES_FACTURATION.maxInstances,
    memory: LIMITES_FACTURATION.memory,
  },
);
