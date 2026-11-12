// Journalisation SANS donnee personnelle (SRV-1) : c'est la seule porte vers les journaux Cloud Logging.
// Interdit dans les journaux : position (latitude/longitude), prenom/nom, jeton (notification, authentification, App Check),
// adresse e-mail, texte saisi par un utilisateur et, par prudence, les identifiants (uid, code de Miff, id de document).
// Principe : liste BLANCHE de champs (types fermes : nombres, booleens, quelques mots fixes) ; tout le reste est remplace par
// "[omis]". Le test `test/unit/journal.test.mjs` verifie ce comportement ; `garde-couts.test.mjs` interdit `console.*` et
// `firebase-functions/logger` en dehors de ce fichier.
import * as logger from "firebase-functions/logger";

export type Niveau = "debug" | "info" | "warn" | "error";
export const RESULTATS = ["ok", "refuse", "erreur"] as const;

export const MOTIFS = [
  "suppression", "date_absente", "trop_ancien", "identifiant_invalide", "auteur_absent", "auteur_sans_partage", "prenom_vide",
  "aucun_destinataire", "deja_vu", "plafond",
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
  lectures?: number;
  ecritures?: number;
}

const CHAMPS_NOMBRES = ["code", "nombre", "dureeMs", "examines", "envoyes", "ecartes", "purges", "lectures", "ecritures"];
const CHAMPS_AUTORISES = ["resultat", "motif", ...CHAMPS_NOMBRES];
/** Nom d'evenement : un code fixe ecrit dans le source (ex. "ping.ok"), jamais une donnee d'utilisateur. */
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
