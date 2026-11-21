// Chemins Firestore du premium reel (SRV-7) : le SEUL endroit ou ils sont construits (comme journalNotif.ts pour le journal de notifications). Tout identifiant
// qui entre dans un chemin est valide AVANT (un `/` dans un identifiant changerait le document vise : injection de chemin).
//   entitlements/{uid}           statut premium lu par les apps (ecrit par le SERVEUR seul)
//   billingAccounts/{uid}        identifiant d'achat aleatoire du compte (+ date de la derniere synchronisation, pour la limite de frequence)
//   billingPurchases/{cle}       un achat du store lie a un compte (cle = SHA-256 de « store + reference », aucune reference n'est conservee)
//   billingEvents/{id}           identifiant d'un message ou d'une notification deja traite (idempotence ; TTL 7 jours)
//   billingConfig/apple          configuration NON SECRETE de l'API Apple (saisie par le PM dans la console)
import { ErreurAchat } from "./achatErreurs.js";

const FORME_UID = /^[A-Za-z0-9]{1,128}$/;
const FORME_ID_EVENEMENT = /^[A-Za-z0-9-]{1,64}$/;
const FORME_CLE = /^[0-9a-f]{64}$/;

export const COLLECTION_COMPTES = "billingAccounts";
export const COLLECTION_ACHATS = "billingPurchases";
export const COLLECTION_EVENEMENTS = "billingEvents";
export const CHEMIN_CONFIG_APPLE = "billingConfig/apple";

export function uidValide(uid: unknown): uid is string {
  return typeof uid === "string" && FORME_UID.test(uid);
}
export function idEvenementValide(id: unknown): id is string {
  return typeof id === "string" && FORME_ID_EVENEMENT.test(id);
}

export const cheminAcces = (uid: string): string => `entitlements/${controlerUid(uid)}`;
export const cheminCompte = (uid: string): string => `billingAccounts/${controlerUid(uid)}`;
export const cheminAchat = (cle: string): string => `billingPurchases/${controlerCle(cle)}`;
export const cheminEvenement = (id: string): string => `billingEvents/${controlerIdEvenement(id)}`;

function controlerUid(uid: string): string {
  if (!uidValide(uid)) throw new ErreurAchat("entree_invalide");
  return uid;
}
function controlerCle(cle: string): string {
  if (!FORME_CLE.test(cle)) throw new ErreurAchat("entree_invalide");
  return cle;
}
function controlerIdEvenement(id: string): string {
  if (!idEvenementValide(id)) throw new ErreurAchat("entree_invalide");
  return id;
}
