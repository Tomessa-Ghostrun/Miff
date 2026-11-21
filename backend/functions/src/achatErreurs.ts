// Erreurs du premium reel (SRV-7). Une `ErreurAchat` porte une RAISON FERMEE (liste ci-dessous, reprise par la liste MOTIFS de journal.ts) et jamais de detail :
// ni jeton d'achat, ni transaction, ni identifiant d'achat, ni uid, ni numero de commande. `transitoire` = une nouvelle tentative a un sens (panne du store, quota,
// reseau) ; sinon l'erreur est DEFINITIVE (donnee refusee, achat inconnu) et ne doit jamais etre rejouee en boucle.
import { HttpsError } from "firebase-functions/v2/https";

export type RaisonAchat =
  | "entree_invalide"
  | "jeton_invalide"
  | "signature_invalide"
  | "achat_inconnu"
  | "produit_inconnu"
  | "autre_package"
  | "deja_lie"
  | "limite_frequence"
  | "partage_famille"
  | "non_configure"
  | "acces_refuse"
  | "indisponible"
  | "reponse_invalide";

export class ErreurAchat extends Error {
  readonly raison: RaisonAchat;
  readonly transitoire: boolean;
  constructor(raison: RaisonAchat, transitoire = false) {
    super(raison);
    this.name = "ErreurAchat";
    this.raison = raison;
    this.transitoire = transitoire;
  }
}

/**
 * Traduction pour une fonction APPELABLE : code Firebase + message neutre en francais (les apps affichent leurs propres textes, choisis d'apres `details.raison`).
 * Jamais d'indication sur l'autre compte (`deja_lie`), ni sur la cause technique (`acces_refuse`, `non_configure` : « indisponible », l'app reessaie plus tard).
 */
export function versErreurAppelable(erreur: unknown): HttpsError {
  if (erreur instanceof HttpsError) return erreur;
  if (!(erreur instanceof ErreurAchat)) return new HttpsError("internal", "Une erreur est survenue. Reessaie dans un instant.", { raison: "interne" });
  const details = { raison: erreur.raison };
  switch (erreur.raison) {
    case "entree_invalide":
    case "jeton_invalide":
    case "signature_invalide":
      return new HttpsError("invalid-argument", "Cet achat n'a pas pu etre verifie.", details);
    case "achat_inconnu":
      return new HttpsError("not-found", "Cet achat est introuvable.", details);
    case "deja_lie":
      return new HttpsError("already-exists", "Cet abonnement est deja utilise avec un autre compte Miff.", details);
    case "limite_frequence":
      return new HttpsError("resource-exhausted", "Reessaie dans un instant.", details);
    case "produit_inconnu":
    case "autre_package":
    case "partage_famille":
      return new HttpsError("failed-precondition", "Cet achat ne peut pas etre associe a Miff+.", details);
    case "non_configure":
    case "acces_refuse":
    case "indisponible":
    case "reponse_invalide":
      return new HttpsError("unavailable", "Le service d'abonnement est momentanement indisponible. Reessaie dans un instant.", details);
  }
}
