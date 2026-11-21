// SRV-7 : notifications temps reel de Google Play (RTDN), message Pub/Sub du sujet `play-rtdn`. Pas d'adresse publique : seul un editeur du sujet (le compte
// google-play-developer-notifications@system.gserviceaccount.com, droit donne par le PM) peut y publier. Le contenu du message n'est JAMAIS cru : il sert de signal, le serveur
// relit l'etat chez Google (achatTraitement.ts). Nouvelle tentative AUTORISEE pour cette fonction seulement (options.ts, FONCTIONS_AVEC_RETRY_AUTORISEES) : traitement idempotent,
// erreur definitive consommee, erreur passagere abandonnee au bout de 24 h.
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { creerDepsAchat } from "./achatDeps.js";
import { traiterMessageGoogle } from "./achatTraitement.js";
import { LIMITES_FACTURATION } from "./options.js";

export const recevoirNotificationsGooglePlay = onMessagePublished(
  {
    topic: "play-rtdn",
    // Compte DEDIE (voir preparerAchat.ts) ; droit `run.invoker` sur le service Cloud Run de cette fonction a poser apres le premier deploiement (docs/dev/deploiement-functions.md).
    serviceAccount: "billing-sync@miff-3d283.iam.gserviceaccount.com",
    maxInstances: LIMITES_FACTURATION.maxInstances,
    memory: LIMITES_FACTURATION.memory,
    retry: true,
  },
  async (evenement) => {
    const message = evenement.data.message;
    await traiterMessageGoogle(creerDepsAchat(), { messageId: message.messageId, publishTime: message.publishTime, data: message.data });
  },
);
