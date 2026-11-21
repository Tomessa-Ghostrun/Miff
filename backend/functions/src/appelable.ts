// Fabrique des fonctions APPELABLES depuis les apps (onCall). Toute fonction appelable de Miff passe par ici :
// - App Check exige (une requete sans jeton App Check valide est rejetee avant d'executer notre code) ;
// - connexion exigee, et connexion ANONYME refusee (le projet n'active pas la connexion anonyme : on le verifie aussi ici) ;
// - pas d'en-tetes CORS (les apps natives n'en ont pas besoin ; le site web n'appelle aucune fonction).
// Les limites (region, instances, delai, memoire) viennent de options.ts ; une fonction peut les preciser par `options` (compte de service dedie, memoire...) :
// `cors` et `enforceAppCheck` ne sont JAMAIS modifiables (ils sont poses APRES les options de la fonction).
//
// Deux limites a connaitre :
// - Une requete rejetee par App Check est QUAND MEME une invocation (facturee, elle occupe une instance) : le vrai frein de
//   cout est `maxInstances` ; App Check evite seulement d'executer notre code pour un robot.
// - App Check ne protege pas contre le REJEU d'un jeton valide vole. Option `consumeAppCheckToken: true` (jeton a usage unique, BETA) : les apps doivent alors
//   fournir des jetons a usage limite et le compte de service de la fonction doit avoir le role « Firebase App Check Token Verifier ». NON ACTIVE pour les
//   fonctions de facturation en v1 (raisons et marche a suivre : docs/dev/deploiement-functions.md, section SRV-7) : un jeton App Check vole ne suffit pas, il faut aussi la
//   connexion du compte, et `synchroniserAchat` est limitee a 1 appel par minute et par compte.
import { HttpsError, onCall, type CallableOptions, type CallableRequest } from "firebase-functions/v2/https";
import "./options.js";

export type OptionsAppelable = Omit<CallableOptions, "cors" | "enforceAppCheck">;

export function appelable<Entree, Sortie>(
  traiter: (requete: CallableRequest<Entree>) => Sortie | Promise<Sortie>,
  options: OptionsAppelable = {},
) {
  return onCall<Entree, Sortie | Promise<Sortie>>({ ...options, enforceAppCheck: true, cors: false }, (requete) => {
    if (!requete.auth || requete.auth.token.firebase?.sign_in_provider === "anonymous") {
      throw new HttpsError("unauthenticated", "Connexion requise.");
    }
    return traiter(requete);
  });
}
