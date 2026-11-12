// Client FCM reel (SRV-4B). Seule porte vers Firebase Cloud Messaging : un message = un appareil, jamais de sujet (topic).
// Identite : celle de la fonction (compte de service DEDIE, options.ts) ; aucune cle, aucun secret dans le code.
// Dans l'EMULATEUR (FUNCTIONS_EMULATOR=true) le client reel n'est JAMAIS utilise : il ne contacte pas FCM (un poste connecte a gcloud ne doit pas
// pouvoir envoyer par erreur un vrai message pendant un essai local). La livraison reelle ne se teste donc qu'apres deploiement, avec un compte de test.
import { getMessaging, type Message } from "firebase-admin/messaging";
import { appAdmin } from "./appAdmin.js";
import type { ClientFcm } from "./notifTraitement.js";

export function creerClientFcm(): ClientFcm {
  if (process.env["FUNCTIONS_EMULATOR"] === "true") {
    return { envoyer: async () => undefined };
  }
  const messagerie = getMessaging(appAdmin());
  return {
    envoyer: async (message: Message) => {
      await messagerie.send(message);
    },
  };
}
