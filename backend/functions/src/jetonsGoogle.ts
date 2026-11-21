// Jetons d'acces Google (SRV-7) : AUCUNE cle de compte de service. L'identite est celle de la fonction (compte `billing-sync`, gere par Google).
//  - Chemin direct : identifiants par defaut de l'environnement (serveur de metadonnees de Cloud Run) avec la portee demandee.
//  - Chemin de repli (HYPOTHESE A CONFIRMER, critere BK9 du ticket) : si Google Play refuse le jeton direct (401/403), le compte de service se fait lui-meme
//    emettre un jeton de la bonne portee par l'API IAM Credentials (usurpation d'identite de soi-meme, documentee par Google ; demande que le compte ait le
//    role « Createur de jetons de compte de service » SUR LUI-MEME : etape optionnelle du PM, docs/dev/deploiement-functions.md). Le repli est memorise
//    pour la vie de l'instance.
// Les jetons ne sont jamais journalises ni conserves hors memoire.
import { GoogleAuth, Impersonated } from "google-auth-library";
import { COMPTE_SERVICE_FACTURATION } from "./options.js";

export const PORTEE_PLAY = "https://www.googleapis.com/auth/androidpublisher";
export const PORTEE_CLOUD = "https://www.googleapis.com/auth/cloud-platform";

export interface FournisseurJetons {
  /** `impersonation` = chemin de repli. Renvoie un jeton d'acces OAuth (jamais journalise). */
  jeton(portee: string, impersonation?: boolean): Promise<string>;
}

export function creerFournisseurJetons(): FournisseurJetons {
  const directs = new Map<string, GoogleAuth>();
  const usurpes = new Map<string, Impersonated>();
  return {
    async jeton(portee, impersonation = false) {
      if (!impersonation) {
        let auth = directs.get(portee);
        if (!auth) {
          auth = new GoogleAuth({ scopes: [portee] });
          directs.set(portee, auth);
        }
        const jeton = await auth.getAccessToken();
        if (!jeton) throw new Error("jeton absent");
        return jeton;
      }
      let client = usurpes.get(portee);
      if (!client) {
        const source = await new GoogleAuth({ scopes: [PORTEE_CLOUD] }).getClient();
        client = new Impersonated({ sourceClient: source, targetPrincipal: COMPTE_SERVICE_FACTURATION, targetScopes: [portee], lifetime: 3600, delegates: [] });
        usurpes.set(portee, client);
      }
      const { token } = await client.getAccessToken();
      if (!token) throw new Error("jeton absent");
      return token;
    },
  };
}
