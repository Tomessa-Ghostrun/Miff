// Journalisation SANS donnee personnelle (SRV-1) : c'est la seule porte vers les journaux APPLICATIFS de nos fonctions.
// Les journaux TECHNIQUES de la plateforme Google (Cloud Run / Cloud Logging) ne passent pas par ici : ils gardent l'adresse IP et
// le user-agent de l'appelant (donnee personnelle a citer dans la politique de confidentialite).
// Interdit dans nos journaux applicatifs : position (latitude/longitude), prenom/nom, jeton (notification, authentification, App Check),
// adresse e-mail, texte saisi par un utilisateur et, par prudence, les identifiants (uid, code de Miff, id de document).
// Principe : liste BLANCHE de champs (types fermes : nombres, booleens, quelques mots fixes) ; tout le reste est remplace par
// "[omis]". Le test `test/unit/journal.test.mjs` verifie ce comportement ; `garde-couts.test.mjs` interdit `console.*` et
// `firebase-functions/logger` en dehors de ce fichier.
import * as logger from "firebase-functions/logger";

export type Niveau = "debug" | "info" | "warn" | "error";
export const RESULTATS = ["ok", "refuse", "erreur"] as const;

export const MOTIFS = [
  "suppression", "date_absente", "trop_ancien", "identifiant_invalide", "auteur_absent", "auteur_sans_partage", "prenom_vide",
  "aucun_destinataire", "deja_vu", "plafond", "miff_absente",
  // Alerte (AD-B2) : ecriture sans changement d'etat, fin posterieure a l'echeance (jamais de notification), fin dont le debut n'a pas ete notifie.
  "sans_changement", "expiration", "debut_non_notifie",
  // Premium reel (SRV-7) : raisons FERMEES d'un refus ou d'un abandon (jamais le detail d'un jeton, d'une transaction, d'un identifiant d'achat ni d'un compte).
  "entree_invalide", "jeton_invalide", "signature_invalide", "achat_inconnu", "produit_inconnu", "autre_package", "deja_lie", "limite_frequence",
  "partage_famille", "test_refuse", "non_configure", "acces_refuse", "indisponible", "reponse_invalide", "ancien", "type_ignore", "test_notification", "compte_inconnu",
  "attente", "abandon", "methode", "taille",
] as const;

export interface ChampsJournal {
  resultat?: (typeof RESULTATS)[number];
  /** Code numerique (statut HTTP, code d'erreur FCM converti en nombre...), jamais un texte libre. */
  code?: number;
  /** Quantites : destinataires, documents traites... */
  nombre?: number;
  dureeMs?: number;
  /** Raison fermee pour laquelle une fonction n'a rien envoye (liste MOTIFS : mots fixes ecrits dans le source). */
  motif?: (typeof MOTIFS)[number];
  /** Compteurs d'une execution (SRV-4B) : appareils examines / messages envoyes / ecartes, jetons purges, lectures et ecritures Firestore. */
  examines?: number;
  envoyes?: number;
  ecartes?: number;
  purges?: number;
  /** Lignes ecrites dans le journal de notifications (CNC-B) : un nombre seul. */
  journalisees?: number;
  lectures?: number;
  ecritures?: number;
}

const CHAMPS_NOMBRES = ["code", "nombre", "dureeMs", "examines", "envoyes", "ecartes", "purges", "journalisees", "lectures", "ecritures"];
const CHAMPS_AUTORISES = ["resultat", "motif", ...CHAMPS_NOMBRES];
/** Nom d'evenement : un code fixe ecrit dans le source (ex. "notif.update.bilan"), jamais une donnee d'utilisateur. */
const MOTIF_EVENEMENT = /^[a-z][a-z0-9_.-]{0,39}$/;

/** Renvoie uniquement des valeurs sures ; exporte pour les tests (la TypeScript ne protege pas un appel venu de JavaScript). */
export function assainir(champs: object = {}): Record<string, string | number | boolean> {
  const sortie: Record<string, string | number | boolean> = {};
  for (const [cle, valeur] of Object.entries(champs)) {
    if (!CHAMPS_AUTORISES.includes(cle)) {
      sortie["champOmis"] = "[omis]";
    } else if (CHAMPS_NOMBRES.includes(cle) && typeof valeur === "number" && Number.isFinite(valeur)) {
      sortie[cle] = valeur;
    } else if (cle === "resultat" && (RESULTATS as readonly unknown[]).includes(valeur)) {
      sortie[cle] = valeur as string;
    } else if (cle === "motif" && (MOTIFS as readonly unknown[]).includes(valeur)) {
      sortie[cle] = valeur as string;
    } else {
      sortie[cle] = "[omis]";
    }
  }
  return sortie;
}

export function journaliser(niveau: Niveau, evenement: string, champs?: ChampsJournal): void {
  const message = MOTIF_EVENEMENT.test(evenement) ? evenement : "evenement.invalide";
  logger[niveau](message, assainir(champs));
}
