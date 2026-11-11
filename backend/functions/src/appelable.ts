// Fabrique des fonctions APPELABLES depuis les apps (onCall). Toute fonction appelable de Miff passe par ici :
// - App Check exige (une requete sans jeton App Check valide est rejetee avant d'executer notre code) ;
// - connexion exigee (utilisateur anonyme interdit : le projet n'active pas la connexion anonyme) ;
// - pas d'en-tetes CORS (les apps natives n'en ont pas besoin ; le site web n'appelle aucune fonction).
// Les limites (region, instances, delai, memoire) viennent de options.ts.
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import "./options.js";

export function appelable<Entree, Sortie>(
  traiter: (requete: CallableRequest<Entree>) => Sortie | Promise<Sortie>,
) {
  return onCall<Entree, Sortie | Promise<Sortie>>({ enforceAppCheck: true, cors: false }, (requete) => {
    if (!requete.auth) {
      throw new HttpsError("unauthenticated", "Connexion requise.");
    }
    return traiter(requete);
  });
}
