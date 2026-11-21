// Dependances REELLES des fonctions de facturation (SRV-7) : Firestore, clients Google et Apple, horloge, identifiants aleatoires. Creees a la demande (jamais au chargement
// du module : le chargement doit rester leger), puis gardees pour la vie de l'instance (jetons Google en cache, verificateurs Apple et configuration en cache).
// Identite : celle de la fonction (compte `billing-sync`, aucune cle). Dans l'EMULATEUR (FUNCTIONS_EMULATOR=true) les clients des stores ne sont JAMAIS reels : ils
// repondent « non configure » (un poste connecte a gcloud ne doit pas pouvoir appeler par erreur les API de Google Play ou d'Apple pendant un essai local).
import { randomUUID } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import { creerClientApple, type ClientApple } from "./achatApple.js";
import type { DepsAchat } from "./achatTraitement.js";
import { ErreurAchat } from "./achatErreurs.js";
import { creerClientGoogle, type ClientGoogle } from "./achatGoogle.js";
import { appAdmin } from "./appAdmin.js";
import { creerChargeurConfigApple } from "./configApple.js";

let instance: DepsAchat | undefined;

export function creerDepsAchat(): DepsAchat {
  if (instance) return instance;
  const db = getFirestore(appAdmin());
  const emulateur = process.env["FUNCTIONS_EMULATOR"] === "true";
  const google: ClientGoogle = emulateur ? { lire: async () => { throw new ErreurAchat("non_configure"); }, accuser: async () => { throw new ErreurAchat("non_configure"); } } : creerClientGoogle();
  const apple: ClientApple = emulateur
    ? {
        verifierTransactionApp: async () => { throw new ErreurAchat("non_configure"); },
        verifierNotification: async () => { throw new ErreurAchat("non_configure"); },
        lireEtat: async () => { throw new ErreurAchat("non_configure"); },
      }
    : creerClientApple({ config: creerChargeurConfigApple({ db }) });
  instance = { db, google, apple, maintenant: () => new Date(), uuid: () => randomUUID() };
  return instance;
}
