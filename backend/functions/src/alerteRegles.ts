// Regles PURES de la notification d'ALERTE « Je suis en danger » (AD-B2, ticket docs/tickets/alerte-danger.md §10.3, §11) : quel evenement Firestore est un
// debut ou une fin MANUELLE (jamais une expiration), forme EXACTE des messages FCM, plafonds. Aucune entree/sortie (pas de Firestore, pas de reseau, pas de
// journal) : tout est teste directement (test/unit/alerte-regles.test.mjs).
//
// CONTENU STRICT du message (ticket §11.2, test de forme exacte) : prenom de l'auteur (tronque a 20 caracteres, comme notifRegles.prenomAffichable),
// identifiant de la Miff et de l'auteur (pour ouvrir sa carte et remplacer l'ancienne notification), type, phase, identifiant de la ligne du journal (entryId, CNC-B). JAMAIS : position, lieu, nom de la Miff,
// nom de famille, photo, heure precise.
import type { Message } from "firebase-admin/messaging";
import { Timestamp } from "firebase-admin/firestore";
import { DUREE_DE_VIE_MS, tronquerOctets, type Plateforme } from "./notifRegles.js";

/** Type du message (Android : l'app compose la notification ; iOS : champ de donnees de l'app). */
export const TYPE_ALERTE = "circle_alert";
export type Phase = "debut" | "fin";

/** Cles de texte resolues par l'app dans la langue du DESTINATAIRE (Android : composees en code ; iOS : par le systeme depuis Localizable). FR/EN : ticket §11.2. */
export const CLES_ALERTE = {
  debut: { titre: "notif_alert_start_title", corps: "notif_alert_start_body" },
  fin: { titre: "notif_alert_end_title", corps: "notif_alert_end_body" },
} as const;

/** Valeur du champ `event` du message de donnees. */
export const EVENEMENT_ALERTE: Record<Phase, string> = { debut: "start", fin: "end" };

/**
 * Garde de la fonction : au plus 1 DEBUT notifie par minute, par auteur et par Miff (silencieux : l'alerte reste visible dans le Feed, sans push). Le plafond de 3 debuts par 24 h
 * et par utilisateur (decision du PM du 2026-11-18) est applique PAR LES REGLES FIRESTORE (users/{uid}/quota/alerts, refus permission-denied du 4e) : la fonction ne voit donc que des
 * debuts deja acceptes et n'a plus de plafond journalier propre.
 */
export const INTERVALLE_MIN_DEBUTS_MS = 60 * 1000;
/** Une ecriture de plus de 5 min au moment du traitement n'envoie rien (pas d'alerte tardive apres un incident de la fonction). */
export const AGE_MAX_EVENEMENT_MS = 5 * 60 * 1000;
/**
 * Bornes de cout : fiches de membres lues et destinataires. Plus larges que celles de notifierUpdate (30 / 20) : une alerte doit atteindre TOUT le cercle
 * (plafond doux de 15 membres dans l'app, aucune borne serveur). 40 fiches -> 39 destinataires au plus : <= 40 + 2 x 39 + 3 = 121 lectures par evenement.
 */
export const MAX_FICHES_ALERTE = 40;
export const MAX_DESTINATAIRES_ALERTE = 39;
/**
 * Duree de vie de l'etat de la fonction (document notifState/{miffId}_alertes, SERVEUR SEUL) : 9 jours apres la derniere reservation d'un DEBUT = 7 jours
 * d'alerte au plus + 2 jours (comme `purgeAt`). Pourquoi pas les 2 jours de notifState/{miffId} : la notification de FIN d'une alerte longue doit retrouver
 * la trace de son debut (une fin dont le debut n'a pas ete notifie n'envoie rien) ; avec 2 jours elle serait perdue des le 3e jour.
 */
export const DUREE_ETAT_ALERTE_MS = 9 * 24 * 60 * 60 * 1000;

/** Donnees lues dans le document d'alerte (SEULES ces clefs sont lues : jamais deviceId, seq ni purgeAt). */
export interface DonneesAlerte {
  status?: unknown;
  startedAt?: unknown;
  expiresAt?: unknown;
  endedAt?: unknown;
}

export type MotifIgnore = "suppression" | "sans_changement" | "expiration" | "date_absente";
export type Decision = { action: "debut" } | { action: "fin" } | { action: "rien"; motif: MotifIgnore };

function horodatage(valeur: unknown): Timestamp | null {
  if (valeur instanceof Timestamp) return valeur;
  const v = valeur as { seconds?: unknown; nanoseconds?: unknown } | null;
  if (v && typeof v.seconds === "number" && typeof v.nanoseconds === "number") return new Timestamp(v.seconds, v.nanoseconds);
  return null;
}

function memeInstant(a: unknown, b: unknown): boolean {
  const x = horodatage(a);
  const y = horodatage(b);
  return x !== null && y !== null && x.seconds === y.seconds && x.nanoseconds === y.nanoseconds;
}

/**
 * Classe une ecriture de miffs/{miffId}/alerts/{uid} (ticket §11.5) :
 *  - DEBUT : apres = `active` ET (rien avant, ou avant != `active`, ou `startedAt` different : un ré-armement) ;
 *  - FIN MANUELLE : avant = `active`, apres = `ended`, ET `endedAt` <= `expiresAt` (une fin posterieure a l'echeance n'est jamais notifiee) ;
 *  - RIEN : suppression (arret du partage, depart, retrait, nettoyage, TTL), toute ecriture sans changement d'etat, fin posterieure a l'echeance.
 * L'EXPIRATION a 7 jours n'est PAS une ecriture (aucune fonction planifiee) : elle ne declenche donc jamais rien (D19).
 */
export function classerEcriture(avant: DonneesAlerte | null, apres: DonneesAlerte | null): Decision {
  if (!apres) return { action: "rien", motif: "suppression" };
  if (apres.status === "active") {
    const neuf = !avant || avant.status !== "active" || !memeInstant(avant.startedAt, apres.startedAt);
    return neuf ? { action: "debut" } : { action: "rien", motif: "sans_changement" };
  }
  if (apres.status === "ended" && avant?.status === "active") {
    const fin = horodatage(apres.endedAt);
    const echeance = horodatage(apres.expiresAt);
    if (!fin || !echeance) return { action: "rien", motif: "date_absente" };
    if (fin.toMillis() > echeance.toMillis()) return { action: "rien", motif: "expiration" };
    return { action: "fin" };
  }
  return { action: "rien", motif: "sans_changement" };
}

export interface EntreeMessageAlerte {
  token: string;
  plateforme: Plateforme;
  miffId: string;
  auteurUid: string;
  prenom: string;
  phase: Phase;
  maintenant: Date;
  /** Identifiant de la ligne du journal du destinataire (journalNotif.identifiantEntree) : permet a l'app de ne jamais doubler une ligne deja relue du journal. */
  entryId: string;
}

/**
 * Message FCM envoye A UN APPAREIL (jamais de sujet). PRIORITE HAUTE (decision du PM, D18) ; AUCUNE notion d'heures calmes ni de plafond quotidien :
 * le message ne porte pas d'indicateur `quiet`, l'app n'a donc rien a differer.
 * - Android : "data seulement", priorite haute, duree de vie 2 h. L'app compose la notification (canal `miff_alerts`, importance haute).
 * - iOS : notification "alerte", texte resolu par le systeme depuis des cles + le prenom, niveau d'interruption `time-sensitive` (capacite a activer par le
 *   PM ; JAMAIS `critical`, pas de Critical Alerts en v1), `apns-priority` 10, expiration 2 h, remplacement par auteur (`apns-collapse-id` distinct de celui des
 *   updates : « alert-{uid} »), `thread-id` par Miff.
 */
export function construireMessageAlerte(e: EntreeMessageAlerte): Message {
  const expirationS = Math.floor((e.maintenant.getTime() + DUREE_DE_VIE_MS) / 1000);
  const cles = CLES_ALERTE[e.phase];
  if (e.plateforme === "android") {
    return {
      token: e.token,
      data: { type: TYPE_ALERTE, event: EVENEMENT_ALERTE[e.phase], miffId: e.miffId, authorUid: e.auteurUid, firstName: e.prenom, entryId: e.entryId },
      android: { priority: "high", ttl: DUREE_DE_VIE_MS },
    };
  }
  return {
    token: e.token,
    data: { type: TYPE_ALERTE, event: EVENEMENT_ALERTE[e.phase], miffId: e.miffId, authorUid: e.auteurUid, entryId: e.entryId },
    apns: {
      headers: {
        "apns-push-type": "alert",
        "apns-priority": "10",
        "apns-expiration": String(expirationS),
        "apns-collapse-id": tronquerOctets(`alert-${e.auteurUid}`, 64),
      },
      payload: {
        aps: {
          alert: { titleLocKey: cles.titre, titleLocArgs: [e.prenom], locKey: cles.corps, locArgs: [e.prenom] },
          threadId: tronquerOctets(`alert-${e.miffId}`, 64),
          sound: "default",
          // Cle inconnue du SDK Admin : copiee telle quelle dans aps (comme `interruption-level` de notifRegles.construireMessage).
          "interruption-level": "time-sensitive",
        },
      },
    },
  };
}
