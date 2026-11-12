// Application Firebase Admin PAR DEFAUT, creee a la demande (au premier appel, jamais au chargement du module : le chargement doit rester leger).
// Identite = celle de la fonction (compte de service dedie, options.ts) ; aucune cle, aucun identifiant explicite.
// On cherche l'application "[DEFAULT]" par son nom : l'emulateur peut deja avoir enregistre une autre application, ce qui ferait croire a tort
// que celle-ci existe (getApps().length > 0) alors que getFirestore() ne la trouverait pas.
import { getApps, initializeApp, type App } from "firebase-admin/app";

export function appAdmin(): App {
  return getApps().find((a) => a.name === "[DEFAULT]") ?? initializeApp();
}
