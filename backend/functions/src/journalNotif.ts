// Regles PURES du JOURNAL de notifications (CNC-B, ticket docs/tickets/centre-notifications-complet.md §8, decisions du PM du 2026-11-19) :
// users/{uid}/notifications/{entryId}, une ligne par destinataire ET par push envoye (updates et alertes), ecrite par les fonctions notifierUpdate et
// notifierAlerte DANS LEUR TRANSACTION DE RESERVATION. Aucune entree/sortie ici (ni Firestore, ni reseau) : tout est teste directement
// (test/unit/journal-notif.test.mjs).
//
// CONTENU STRICT (test de forme exacte, CNC-13) : v, kind, miffId, actorUid, createdAt, expireAt. JAMAIS : prenom, photo, lieu, position, nom de la Miff, jeton,
// ni l'identifiant du destinataire (il est dans le CHEMIN). Le prenom et la photo sont resolus par l'app a l'affichage, depuis la fiche de membre.
import { hash } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";

/** Les trois sortes de push envoyes aujourd'hui. */
export type KindJournal = "update" | "alert_start" | "alert_end";

/** Version de forme du document : un client ignore une version ou un `kind` inconnus. */
export const VERSION_JOURNAL = 1;
/** Duree de vie d'une ligne : 30 jours (regle TTL Firestore sur le groupe de collections `notifications`, champ `expireAt`, a poser par le PM). */
export const DUREE_JOURNAL_MS = 30 * 24 * 60 * 60 * 1000;

/** Prefixe de l'identifiant selon la sorte (ticket §8.1) : u = update, as = debut d'alerte, ae = fin d'alerte. */
const PREFIXES: Record<KindJournal, string> = { update: "u", alert_start: "as", alert_end: "ae" };
/** Forme que les clients valident avant tout usage (ticket §6.9). */
const FORME_IDENTIFIANT = /^[A-Za-z0-9_-]{1,128}$/;

/** Chemin de la ligne d'un destinataire. */
export const cheminEntree = (destinataireUid: string, entryId: string): string => `users/${destinataireUid}/notifications/${entryId}`;

/**
 * Identifiant DETERMINISTE de la ligne (et du champ `entryId` du message FCM) : le meme evenement donne toujours le meme identifiant, donc un evenement rejoue
 * ne cree jamais une 2e ligne, et le telephone qui recoit le push et celui qui relit le journal retrouvent la meme cle (aucun doublon).
 *   update       : u-{miffId}-{actorUid}-{createdAt de la photo, en millisecondes}
 *   alert_start  : as-{miffId}-{actorUid}-{startedAt de l'alerte, en millisecondes}
 *   alert_end    : ae-{miffId}-{actorUid}-{startedAt de l'alerte, en millisecondes}   (une seule fin par debut : l'identite de l'alerte est son startedAt)
 * Si la concatenation depasse 128 caracteres (deux identifiants de 128 : cas hors norme, les uid Firebase en font 28 et les Miffs 20), l'identifiant devient
 * {prefixe}-{SHA-256 en hexadecimal des memes elements} (66 caracteres au plus) : toujours deterministe, toujours de la forme que les clients acceptent.
 */
export function identifiantEntree(kind: KindJournal, miffId: string, actorUid: string, instantMs: number): string {
  const prefixe = PREFIXES[kind];
  const clair = `${prefixe}-${miffId}-${actorUid}-${Math.trunc(instantMs)}`;
  if (FORME_IDENTIFIANT.test(clair)) return clair;
  return `${prefixe}-${hash("sha256", `${miffId}\n${actorUid}\n${Math.trunc(instantMs)}`, "hex")}`;
}

export interface EntreeJournal {
  v: number;
  kind: KindJournal;
  miffId: string;
  actorUid: string;
  createdAt: Timestamp;
  expireAt: Timestamp;
}

/** Document ecrit (forme exacte, 6 cles). `maintenant` = heure du serveur INJECTEE dans la fonction ; sert a l'affichage et de curseur de synchronisation. */
export function construireEntree(kind: KindJournal, miffId: string, actorUid: string, maintenant: Date): EntreeJournal {
  return {
    v: VERSION_JOURNAL,
    kind,
    miffId,
    actorUid,
    createdAt: Timestamp.fromMillis(maintenant.getTime()),
    expireAt: Timestamp.fromMillis(maintenant.getTime() + DUREE_JOURNAL_MS),
  };
}
