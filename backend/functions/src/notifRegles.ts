// Regles PURES de la notification "un proche a donne des nouvelles" (SRV-4B, ticket docs/tickets/notification-update-membres.md) :
// prenom assaini et tronque, heures calmes, forme EXACTE des messages FCM, classement des erreurs FCM.
// Aucune entree/sortie ici (pas de Firestore, pas de reseau, pas de journal) : tout est teste directement (test/unit/notif-regles.test.mjs).
//
// CONTENU STRICT du message (ticket §4, test de forme exacte) : prenom de l'auteur (tronque a 20 caracteres), identifiant de la Miff,
// identifiant technique de l'auteur (pour ouvrir sa carte et remplacer l'ancienne notification), type, indicateur de nuit. JAMAIS : photo,
// position, nom de lieu, nom de famille, nom de la Miff, legende, heure precise, couleur du statut.
import type { Message } from "firebase-admin/messaging";

export type Plateforme = "android" | "ios";

/** Duree de vie d'un message non livre : 2 h (une "nouvelle" de la veille serait trompeuse). */
export const DUREE_DE_VIE_MS = 2 * 60 * 60 * 1000;
/** Plafond : 1 notification par auteur, par Miff et par heure (fenetre depuis la derniere notification envoyee). */
export const PLAFOND_FENETRE_MS = 60 * 60 * 1000;
/** Un evenement dont la photo a plus de 5 min au moment du traitement n'envoie rien (pas de notification tardive apres un incident). */
export const AGE_MAX_EVENEMENT_MS = 5 * 60 * 1000;
/** Bornes de cout : fiches de membres lues, destinataires, appareils lus par destinataire. */
export const MAX_FICHES = 30;
export const MAX_DESTINATAIRES = 20;
export const MAX_APPAREILS = 3;
/** Nouvelles tentatives d'envoi FCM (erreur passagere) : au plus 2, delai croissant, dans la meme execution. */
export const DELAIS_REPRISE_MS = [500, 1500] as const;
/** notifState/{miffId} est supprime automatiquement 2 jours apres sa derniere ecriture (TTL Firestore, a activer par le PM). */
export const DUREE_ETAT_MS = 2 * 24 * 60 * 60 * 1000;

export const LONGUEUR_PRENOM_MAX = 20;
/** Heures calmes : de 22 h (incluse) a 7 h (exclue), heure locale du destinataire. */
export const NUIT_DEBUT_H = 22;
export const NUIT_FIN_H = 7;

/** Cles de texte resolues par l'app (Android : composees en code ; iOS : par le systeme depuis Localizable). Clefs du ticket §4. */
export const CLE_TITRE = "notif_circle_update_title";
export const CLE_CORPS = "notif_circle_update_body";
export const TYPE_MESSAGE = "circle_update";

// Caracteres retires du prenom (remplaces par une espace AVANT le recadrage) : controles, separateurs de ligne/paragraphe, TOUS les caracteres de format
// (Cf : espace de largeur nulle, marques et isolats bidirectionnels, trait d'union conditionnel, balises U+E0000 a U+E007F...) SAUF le liant de largeur nulle
// U+200D (il compose les emojis de famille), et les "remplisseurs" invisibles (U+3164, U+115F, U+1160, U+FFA0, U+2800). Le selecteur de variation U+FE0F
// (emoji en couleur) est conserve. Un prenom qui n'a plus AUCUN caractere visible (lettre, chiffre, symbole, ponctuation) est considere vide.
const CARACTERES_INTERDITS = /(?!\u200D)\p{Cf}|[\p{Cc}\p{Zl}\p{Zp}\u3164\u115F\u1160\uFFA0\u2800\u{E0000}-\u{E007F}]/gu;
const CARACTERE_VISIBLE = /[\p{L}\p{N}\p{S}\p{P}]/u;

/** Prenom affichable : controles retires, espaces ramenes a un seul, tronque a 20 caracteres (graphemes) dont le "…" final. "" si rien d'affichable. */
export function prenomAffichable(brut: unknown): string {
  if (typeof brut !== "string") return "";
  const propre = brut.replace(CARACTERES_INTERDITS, " ").replace(/\s+/g, " ").trim();
  if (!CARACTERE_VISIBLE.test(propre)) return "";
  const graphemes = Array.from(new Intl.Segmenter("fr", { granularity: "grapheme" }).segment(propre), (s) => s.segment);
  if (graphemes.length <= LONGUEUR_PRENOM_MAX) return graphemes.join("");
  return graphemes.slice(0, LONGUEUR_PRENOM_MAX - 1).join("").trimEnd() + "…";
}

/** Heure locale (0 a 23) d'un instant dans un fuseau IANA ; null si le fuseau est inconnu ou invalide. */
export function heureLocale(fuseau: unknown, instant: Date): number | null {
  if (typeof fuseau !== "string" || fuseau.length === 0 || fuseau.length > 64) return null;
  try {
    const parties = new Intl.DateTimeFormat("en-GB", { timeZone: fuseau, hour: "2-digit", hourCycle: "h23" }).formatToParts(instant);
    const heure = Number(parties.find((p) => p.type === "hour")?.value);
    return Number.isInteger(heure) && heure >= 0 && heure <= 23 ? heure : null;
  } catch {
    return null;
  }
}

/** Heures calmes (22 h - 7 h chez le destinataire). Fuseau inconnu = calme (on prefere ne pas reveiller que reveiller). */
export function estNuit(fuseau: unknown, instant: Date): boolean {
  const h = heureLocale(fuseau, instant);
  return h === null ? true : h >= NUIT_DEBUT_H || h < NUIT_FIN_H;
}

export interface EntreeMessage {
  token: string;
  plateforme: Plateforme;
  miffId: string;
  auteurUid: string;
  prenom: string;
  nuit: boolean;
  maintenant: Date;
}

/**
 * Message FCM envoye A UN APPAREIL (jamais de sujet : un abonnement ne s'eteint pas quand on quitte la Miff).
 * - Android : "data seulement", haute priorite. L'app compose et affiche la notification (langue du telephone, canal "par defaut" avec son et
 *   vibration selon le telephone, canal calme la nuit, ligne du centre). Pas de `collapseKey` : FCM n'en tolere que 4 par appareil ; le
 *   remplacement a l'ecran se fait avec l'identifiant de l'auteur (`authorUid`, a utiliser comme etiquette de la notification).
 * - iOS : notification "alerte" dont le texte est resolu par le systeme depuis des cles + le prenom (pas d'extension de service). Remplacement par
 *   auteur : `apns-collapse-id` et `thread-id`. Nuit : niveau "passif" (ni son, ni ecran allume). Jour : son par defaut du telephone.
 */
/** Tronque une chaine a N octets UTF-8 sans couper un caractere (apns-collapse-id : 64 octets au plus). */
export function tronquerOctets(texte: string, max: number): string {
  let sortie = "";
  let octets = 0;
  for (const c of texte) {
    const n = Buffer.byteLength(c, "utf8");
    if (octets + n > max) break;
    sortie += c;
    octets += n;
  }
  return sortie;
}

export function construireMessage(e: EntreeMessage): Message {
  const expirationS = Math.floor((e.maintenant.getTime() + DUREE_DE_VIE_MS) / 1000);
  if (e.plateforme === "android") {
    return {
      token: e.token,
      data: { type: TYPE_MESSAGE, miffId: e.miffId, authorUid: e.auteurUid, firstName: e.prenom, quiet: e.nuit ? "1" : "0" },
      android: { priority: "high", ttl: DUREE_DE_VIE_MS },
    };
  }
  return {
    token: e.token,
    data: { type: TYPE_MESSAGE, miffId: e.miffId, authorUid: e.auteurUid },
    apns: {
      headers: {
        "apns-push-type": "alert",
        "apns-priority": e.nuit ? "5" : "10",
        "apns-expiration": String(expirationS),
        "apns-collapse-id": tronquerOctets(e.auteurUid, 64),
      },
      payload: {
        aps: {
          alert: { titleLocKey: CLE_TITRE, titleLocArgs: [e.prenom], locKey: CLE_CORPS },
          threadId: e.auteurUid,
          // Le SDK Admin ecrit ces noms en "title-loc-key", "loc-key", "thread-id" ; les cles inconnues du SDK (ici "interruption-level") sont copiees telles quelles dans aps.
          ...(e.nuit ? { "interruption-level": "passive" } : { sound: "default" }),
        },
      },
    },
  };
}

// --- Erreurs FCM --------------------------------------------------------------------------------------------------------------------

/** Le jeton n'existe plus (appli desinstallee, jeton renouvele) : le document de l'appareil doit etre supprime. Seul code de purge : l'API FCM v1 renvoie
 *  `registration-token-not-registered` (UNREGISTERED). `invalid-argument` n'en fait PAS partie (il peut aussi signaler un defaut de NOTRE message : supprimer
 *  sur ce code viderait tous les jetons) ; l'ancien code `invalid-registration-token` n'est plus emis par le SDK actuel et n'est donc pas traite. */
export const CODES_JETON_INVALIDE = ["messaging/registration-token-not-registered"] as const;
/** Erreurs passageres : on retente (2 fois au plus). */
export const CODES_PASSAGERS = [
  "messaging/internal-error",
  "messaging/server-unavailable",
  "messaging/quota-exceeded",
  "messaging/message-rate-exceeded",
  "messaging/device-message-rate-exceeded",
  "app/network-error",
  "app/network-timeout",
] as const;

export type CategorieErreur = "jeton_invalide" | "passager" | "definitive";

export function codeDeErreur(erreur: unknown): string {
  const code = typeof erreur === "object" && erreur !== null ? (erreur as { code?: unknown }).code : undefined;
  return typeof code === "string" ? code : "inconnu";
}

export function categoriser(code: string): CategorieErreur {
  if ((CODES_JETON_INVALIDE as readonly string[]).includes(code)) return "jeton_invalide";
  if ((CODES_PASSAGERS as readonly string[]).includes(code)) return "passager";
  return "definitive";
}

/** Code NUMERIQUE d'une erreur FCM pour les journaux (le journal n'accepte que des nombres, jamais un texte libre). 99 = autre. */
const CODES_NUMERIQUES: Record<string, number> = {
  "messaging/registration-token-not-registered": 1,
  "messaging/internal-error": 3,
  "messaging/server-unavailable": 4,
  "messaging/quota-exceeded": 5,
  "messaging/message-rate-exceeded": 6,
  "messaging/device-message-rate-exceeded": 7,
  "messaging/third-party-auth-error": 8,
  "messaging/invalid-argument": 9,
  "messaging/mismatched-credential": 10,
  "messaging/authentication-error": 11,
  "app/network-error": 12,
  "app/network-timeout": 13,
};
export function codeNumerique(code: string): number {
  return CODES_NUMERIQUES[code] ?? 99;
}
