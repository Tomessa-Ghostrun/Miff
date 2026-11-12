// Fabrique des fonctions APPELABLES depuis les apps (onCall). Toute fonction appelable de Miff passe par ici :
// - App Check exige (une requete sans jeton App Check valide est rejetee avant d'executer notre code) ;
// - connexion exigee, et connexion ANONYME refusee (le projet n'active pas la connexion anonyme : on le verifie aussi ici) ;
// - pas d'en-tetes CORS (les apps natives n'en ont pas besoin ; le site web n'appelle aucune fonction).
// Les limites (region, instances, delai, memoire) viennent de options.ts.
//
// Deux limites a connaitre :
// - Une requete rejetee par App Check est QUAND MEME une invocation (facturee, elle occupe une instance) : le vrai frein de
//   cout est `maxInstances` ; App Check evite seulement d'executer notre code pour un robot.
// - App Check ne protege pas contre le REJEU d'un jeton valide vole. Pour une fonction sensible (ex. validation d'achat, SRV-7),
//   ajouter `consumeAppCheckToken: true` a onCall (jeton a usage unique : les apps doivent alors fournir des jetons a usage limite).
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import "./options.js";

export function appelable<Entree, Sortie>(
  traiter: (requete: CallableRequest<Entree>) => Sortie | Promise<Sortie>,
) {
  return onCall<Entree, Sortie | Promise<Sortie>>({ enforceAppCheck: true, cors: false }, (requete) => {
    if (!requete.auth || requete.auth.token.firebase?.sign_in_provider === "anonymous") {
      throw new HttpsError("unauthenticated", "Connexion requise.");
    }
    return traiter(requete);
  });
}
