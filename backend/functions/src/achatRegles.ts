// Regles PURES du premium reel (SRV-7, ticket docs/tickets/premium-achat-reel.md §5, §8, §9) : aucune entree/sortie ici (ni Firestore, ni reseau, ni horloge :
// l'heure est passee en argument). Tout est teste directement (test/unit/achat-regles.test.mjs).
//   - correspondance des etats du store (Google, Apple) vers l'etat Miff (§8) ;
//   - choix de l'acces a ecrire dans entitlements/{uid} (achat le plus favorable, accord « a la main » respecte) ;
//   - decodage et validation des entrees (message Pub/Sub de Google, jeton d'achat, transaction signee) ;
//   - cle d'un achat (SHA-256, aucune reference d'achat n'est conservee).
import { createHash } from "node:crypto";
import { ErreurAchat } from "./achatErreurs.js";

export type Store = "google_play" | "app_store";
export type Plan = "yearly" | "monthly";
/** Etats informatifs affiches par les apps (§8). `pending` n'est jamais ecrit dans entitlements (aucun droit accorde). */
export type Etat = "trial" | "active" | "canceled" | "grace" | "on_hold" | "expired" | "revoked" | "pending";

// --- Produits (ticket Q7, decides le 2026-11-12) : identifiants DEFINITIFS une fois crees chez les stores ---------------------------------
export const PACKAGE_GOOGLE = "com.tomessa.miff";
export const BUNDLE_APPLE = "com.tomessa.miff";
export const PRODUIT_GOOGLE = "miff_plus";
const FORFAITS_GOOGLE: Readonly<Record<string, Plan>> = { annuel: "yearly", mensuel: "monthly" };
export const PRODUITS_APPLE: Readonly<Record<string, Plan>> = {
  "com.tomessa.miff.plus.annual": "yearly",
  "com.tomessa.miff.plus.monthly": "monthly",
};

// --- Durees ---------------------------------------------------------------------------------------------------------------------------
const JOUR_MS = 24 * 60 * 60 * 1000;
/** 1 appel de synchronisation par minute et par compte (ticket §10). */
export const DELAI_MIN_SYNCHRO_MS = 60_000;
/** Un identifiant de message deja traite est retenu 7 jours (TTL Firestore sur `billingEvents.purgeAt`). */
export const DUREE_EVENEMENT_MS = 7 * JOUR_MS;
/** Un achat reste lie 90 jours apres son echeance (le jeton Google n'est plus valable 60 jours apres l'expiration) puis il est supprime par TTL (`billingPurchases.purgeAt`). */
export const DUREE_ACHAT_APRES_ECHEANCE_MS = 90 * JOUR_MS;
/** Apres une suppression de compte, le droit est inactif et le document est supprime 30 jours plus tard (decision du PM du 2026-10-24 ; `entitlements.purgeAt`). */
export const DUREE_PURGE_COMPTE_SUPPRIME_MS = 30 * JOUR_MS;
/** Une erreur passagere sur un message Google est abandonnee apres 24 h (evite 7 jours de reprises sur un message qui ne passera jamais). */
export const DUREE_MAX_REPRISE_MS = JOUR_MS;

// --- Formes d'entree acceptees ----------------------------------------------------------------------------------------------------------
/** Jeton d'achat Google : texte ASCII imprimable sans espace, 16 a 1500 caracteres ; il est de toute facon encode (encodeURIComponent) avant d'entrer dans une URL. */
const FORME_JETON_GOOGLE = /^[\x21-\x7e]{16,1500}$/;
/** Transaction signee Apple (JWS) : trois segments base64url, 16 Kio au plus. */
const FORME_JWS = /^[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}$/;
const TAILLE_MAX_JWS = 16 * 1024;
const FORME_ID_TRANSACTION_APPLE = /^[0-9]{1,20}$/;
const FORME_UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export const jetonGoogleValide = (v: unknown): v is string => typeof v === "string" && FORME_JETON_GOOGLE.test(v);
export const jwsValide = (v: unknown): v is string => typeof v === "string" && v.length <= TAILLE_MAX_JWS && FORME_JWS.test(v);
export const idTransactionAppleValide = (v: unknown): v is string => typeof v === "string" && FORME_ID_TRANSACTION_APPLE.test(v);
export const uuidValide = (v: unknown): v is string => typeof v === "string" && FORME_UUID.test(v);
/** Les identifiants d'achat sont compares en minuscules (Apple renvoie l'`appAccountToken` en minuscules). */
export const normaliserIdAchat = (v: string): string => v.toLowerCase();

/** Cle d'un achat : SHA-256 de « store + saut de ligne + reference » (jeton d'achat Google, identifiant de transaction d'origine Apple). La reference n'est PAS conservee. */
export function cleAchat(store: Store, reference: string): string {
  return createHash("sha256").update(`${store}\n${reference}`).digest("hex");
}

// --- Etat d'un achat lu chez le store ----------------------------------------------------------------------------------------------------
export interface EtatStore {
  store: Store;
  /** Identifiant de produit du store (`miff_plus`, `com.tomessa.miff.plus.annual`...). */
  produit: string;
  plan: Plan | null;
  state: Etat;
  /** Droit accorde par le store a cet instant (essai, actif, annule non expire, grace). */
  active: boolean;
  /** Fin d'acces (echeance du store ou fin de la grace) ; `null` tant que rien n'est paye (en attente). */
  expiresAt: Date | null;
  willRenew: boolean;
  trial: boolean;
  /** Achat de test (Google `testPurchase`, Apple « Sandbox ») : accepte et MARQUE (risque R10 du ticket). */
  test: boolean;
  /** Heure (ms) a laquelle cet etat a ete observe : sert a ignorer un etat plus ancien que celui deja ecrit. */
  observeA: number;
  /** Identifiant d'achat aleatoire fourni a l'achat (Google `obfuscatedExternalAccountId`, Apple `appAccountToken`), en minuscules ; `null` s'il n'a pas ete fourni. */
  idCompte: string | null;
  /** Google : jeton de l'achat precedent (nouvelle souscription, changement de forfait), pour retrouver le compte. */
  lienPrecedent: string | null;
  /** Apple : transaction partagee en famille (refusee en v1, ticket Q6). */
  famille: boolean;
  /** Google : l'achat n'est pas encore acquitte (le serveur doit l'acquitter apres avoir accorde le droit, sous 3 jours sinon remboursement automatique). */
  accuser: boolean;
}

// --- Google : subscriptionsv2.get ---------------------------------------------------------------------------------------------------------
/** Sous-ensemble de `SubscriptionPurchaseV2` (https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2) ; valide a la lecture (achatGoogle.ts). */
export interface ReponseGoogle {
  subscriptionState?: string;
  lineItems?: Array<{
    productId?: string;
    expiryTime?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean };
    offerDetails?: { basePlanId?: string };
    offerPhase?: { freeTrial?: unknown };
  }>;
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
  testPurchase?: unknown;
  acknowledgementState?: string;
  linkedPurchaseToken?: string;
}

export interface IndiceGoogle {
  /** Message « revoque » (type 12) ou « achat annule/rembourse » : l'acces est coupe tout de suite (decision Q3). */
  revoque?: boolean;
}

export function etatDepuisGoogle(reponse: ReponseGoogle, maintenant: Date, indice: IndiceGoogle = {}): EtatStore {
  const lignes = (reponse.lineItems ?? []).filter((l) => l.productId === PRODUIT_GOOGLE);
  if (lignes.length === 0) throw new ErreurAchat("produit_inconnu");
  const echeance = (l: (typeof lignes)[number]): number => (l.expiryTime ? Date.parse(l.expiryTime) : Number.NaN);
  const ligne = lignes.reduce((a, b) => ((Number.isNaN(echeance(b)) ? -Infinity : echeance(b)) > (Number.isNaN(echeance(a)) ? -Infinity : echeance(a)) ? b : a));
  const fin = echeance(ligne);
  const essai = ligne.offerPhase?.freeTrial !== undefined && ligne.offerPhase?.freeTrial !== null;
  const identifiant = reponse.externalAccountIdentifiers?.obfuscatedExternalAccountId;
  const base = {
    store: "google_play" as const,
    produit: PRODUIT_GOOGLE,
    plan: FORFAITS_GOOGLE[ligne.offerDetails?.basePlanId ?? ""] ?? null,
    test: reponse.testPurchase !== undefined && reponse.testPurchase !== null,
    observeA: maintenant.getTime(),
    idCompte: typeof identifiant === "string" && identifiant.length > 0 ? normaliserIdAchat(identifiant) : null,
    lienPrecedent: reponse.linkedPurchaseToken ?? null,
    famille: false,
    accuser: reponse.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
  };
  const renouvelle = ligne.autoRenewingPlan?.autoRenewEnabled === true;
  const sans = (state: Etat, active: boolean, expiresAt: Date | null, willRenew: boolean, trial: boolean): EtatStore => ({
    ...base,
    state,
    active,
    expiresAt,
    willRenew,
    trial: trial && active,
  });
  if (indice.revoque) return sans("revoked", false, maintenant, false, false);
  const dateFin = Number.isNaN(fin) ? null : new Date(fin);
  // Un droit « actif » dont l'echeance est deja passee (lecture perimee) n'est pas un droit.
  const accorde = (state: Etat, willRenew: boolean): EtatStore => {
    if (dateFin === null) throw new ErreurAchat("reponse_invalide");
    if (dateFin.getTime() <= maintenant.getTime()) return sans("expired", false, dateFin, false, false);
    return sans(state, true, dateFin, willRenew, essai);
  };
  switch (reponse.subscriptionState) {
    case "SUBSCRIPTION_STATE_PENDING":
    case "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED":
      return { ...sans("pending", false, null, false, false), accuser: false };
    case "SUBSCRIPTION_STATE_ACTIVE":
      return accorde(essai ? "trial" : "active", renouvelle);
    case "SUBSCRIPTION_STATE_CANCELED":
      return accorde("canceled", false);
    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
      return accorde("grace", renouvelle);
    case "SUBSCRIPTION_STATE_ON_HOLD":
    case "SUBSCRIPTION_STATE_PAUSED":
      return sans("on_hold", false, dateFin, renouvelle, false);
    case "SUBSCRIPTION_STATE_EXPIRED":
      return sans("expired", false, dateFin, false, false);
    default:
      throw new ErreurAchat("reponse_invalide");
  }
}

// --- Apple : App Store Server API (statuts) ------------------------------------------------------------------------------------------------
/** Sous-ensemble de `JWSTransactionDecodedPayload` / `JWSRenewalInfoDecodedPayload` de @apple/app-store-server-library. */
export interface TransactionApple {
  originalTransactionId?: string;
  productId?: string;
  type?: string;
  expiresDate?: number;
  appAccountToken?: string;
  inAppOwnershipType?: string;
  revocationDate?: number;
  offerType?: number;
  offerDiscountType?: string;
  environment?: string;
  signedDate?: number;
}
export interface RenouvellementApple {
  autoRenewStatus?: number;
  gracePeriodExpiresDate?: number;
  signedDate?: number;
}
/** Statuts de l'API : 1 actif, 2 expire, 3 nouvelle tentative de facturation, 4 periode de grace, 5 revoque. */
export type StatutApple = 1 | 2 | 3 | 4 | 5;

export function etatDepuisApple(statut: number, transaction: TransactionApple, renouvellement: RenouvellementApple | null, maintenant: Date): EtatStore {
  const plan = PRODUITS_APPLE[transaction.productId ?? ""];
  if (plan === undefined) throw new ErreurAchat("produit_inconnu");
  if (transaction.type !== undefined && transaction.type !== "Auto-Renewable Subscription") throw new ErreurAchat("produit_inconnu");
  const maintenantMs = maintenant.getTime();
  const signees = [transaction.signedDate, renouvellement?.signedDate].filter((d): d is number => typeof d === "number" && Number.isFinite(d));
  const jeton = transaction.appAccountToken;
  const base = {
    store: "app_store" as const,
    produit: transaction.productId as string,
    plan,
    test: transaction.environment === "Sandbox",
    observeA: signees.length > 0 ? Math.max(...signees) : maintenantMs,
    idCompte: typeof jeton === "string" && jeton.length > 0 ? normaliserIdAchat(jeton) : null,
    lienPrecedent: null,
    famille: transaction.inAppOwnershipType === "FAMILY_SHARED",
    accuser: false,
  };
  const fin = typeof transaction.expiresDate === "number" && Number.isFinite(transaction.expiresDate) ? new Date(transaction.expiresDate) : null;
  const renouvelle = renouvellement?.autoRenewStatus === 1;
  // Hypothese (non verifiee en production) : la seule offre d'introduction de Miff+ est l'essai gratuit de 7 jours de l'annuel ; offerType 1 = offre d'introduction.
  const essai = transaction.offerType === 1 && transaction.offerDiscountType !== "PAY_AS_YOU_GO" && transaction.offerDiscountType !== "PAY_UP_FRONT";
  const fait = (state: Etat, active: boolean, expiresAt: Date | null, willRenew: boolean, trial: boolean): EtatStore => ({
    ...base,
    state,
    active,
    expiresAt,
    willRenew,
    trial: trial && active,
  });
  if (statut === 5 || typeof transaction.revocationDate === "number") {
    const revocation = typeof transaction.revocationDate === "number" ? new Date(transaction.revocationDate) : maintenant;
    return fait("revoked", false, revocation, false, false);
  }
  switch (statut) {
    case 1: {
      if (fin === null) throw new ErreurAchat("reponse_invalide");
      if (fin.getTime() <= maintenantMs) return fait("expired", false, fin, false, false);
      if (essai) return fait(renouvelle ? "trial" : "canceled", true, fin, renouvelle, true);
      return fait(renouvelle ? "active" : "canceled", true, fin, renouvelle, false);
    }
    case 4: {
      const finGrace = typeof renouvellement?.gracePeriodExpiresDate === "number" ? new Date(renouvellement.gracePeriodExpiresDate) : fin;
      if (finGrace === null) throw new ErreurAchat("reponse_invalide");
      if (finGrace.getTime() <= maintenantMs) return fait("on_hold", false, finGrace, renouvelle, false);
      return fait("grace", true, finGrace, renouvelle, false);
    }
    case 3:
      return fait("on_hold", false, fin, renouvelle, false);
    case 2:
      return fait("expired", false, fin, false, false);
    default:
      throw new ErreurAchat("reponse_invalide");
  }
}

// --- Message Pub/Sub de Google (RTDN) ---------------------------------------------------------------------------------------------------------
export type NotificationGoogle =
  | { sorte: "abonnement"; jeton: string; type: number }
  /** Achat annule / rembourse (« voided purchase », abonnement). */
  | { sorte: "annule"; jeton: string }
  | { sorte: "test" }
  | { sorte: "autre_package" }
  /** Message d'un autre genre (produit ponctuel, examen de remboursement...) ou message non conforme : ignore. */
  | { sorte: "ignore"; motif: "type_ignore" | "entree_invalide" };

const TAILLE_MAX_MESSAGE = 16 * 1024;

/** Decode le champ `data` (base64) d'un message RTDN. Ne leve jamais : un message qui n'est pas conforme est « ignore », jamais rejoue. */
export function decoderMessageGoogle(donneesBase64: unknown): NotificationGoogle {
  if (typeof donneesBase64 !== "string" || donneesBase64.length === 0 || donneesBase64.length > TAILLE_MAX_MESSAGE) return { sorte: "ignore", motif: "entree_invalide" };
  let doc: unknown;
  try {
    doc = JSON.parse(Buffer.from(donneesBase64, "base64").toString("utf8"));
  } catch {
    return { sorte: "ignore", motif: "entree_invalide" };
  }
  if (typeof doc !== "object" || doc === null || Array.isArray(doc)) return { sorte: "ignore", motif: "entree_invalide" };
  const m = doc as Record<string, unknown>;
  if (m["packageName"] !== PACKAGE_GOOGLE) return { sorte: "autre_package" };
  if (typeof m["testNotification"] === "object" && m["testNotification"] !== null) return { sorte: "test" };
  const abo = m["subscriptionNotification"];
  if (typeof abo === "object" && abo !== null) {
    const a = abo as Record<string, unknown>;
    if (!jetonGoogleValide(a["purchaseToken"]) || typeof a["notificationType"] !== "number" || !Number.isInteger(a["notificationType"])) {
      return { sorte: "ignore", motif: "entree_invalide" };
    }
    return { sorte: "abonnement", jeton: a["purchaseToken"], type: a["notificationType"] };
  }
  const annule = m["voidedPurchaseNotification"];
  if (typeof annule === "object" && annule !== null) {
    const a = annule as Record<string, unknown>;
    // productType 1 = abonnement, 2 = produit ponctuel (aucun produit ponctuel n'existe).
    if (a["productType"] !== 1) return { sorte: "ignore", motif: "type_ignore" };
    if (!jetonGoogleValide(a["purchaseToken"])) return { sorte: "ignore", motif: "entree_invalide" };
    return { sorte: "annule", jeton: a["purchaseToken"] };
  }
  return { sorte: "ignore", motif: "type_ignore" };
}

/** Type 12 = SUBSCRIPTION_REVOKED (abonnement revoque avant son echeance : remboursement, rejet de paiement). */
export const TYPE_GOOGLE_REVOQUE = 12;

// --- Acces a ecrire dans entitlements/{uid} ---------------------------------------------------------------------------------------------------
export interface AchatLie {
  cle: string;
  store: Store;
  state: Etat;
  active: boolean;
  expiresAt: Date | null;
  plan: Plan | null;
  trial: boolean;
  willRenew: boolean;
  test: boolean;
}

/** Ce que les apps lisent (§2.3 de data-model.md). `purgeAt` n'est ecrit que a la suppression du compte. */
export interface AccesEcrit {
  active: boolean;
  expiresAt: Date;
  source: Store;
  updatedAt: Date;
  state: Etat;
  plan: Plan | null;
  trial: boolean;
  willRenew: boolean;
  test: boolean;
}

/** Document entitlements/{uid} deja present (lu par le serveur), tel quel : un accord manuel n'a que 4 champs. */
export interface AccesActuel {
  active?: unknown;
  expiresAt?: Date | null;
  source?: unknown;
  state?: unknown;
  plan?: unknown;
  trial?: unknown;
  willRenew?: unknown;
  test?: unknown;
}

export type DecisionAcces =
  | { ecrire: false; raison: "aucun_achat" | "manuel_conserve" | "inchange" }
  | { ecrire: true; acces: AccesEcrit };

const effectif = (a: AchatLie, maintenant: Date): boolean => a.active && a.expiresAt !== null && a.expiresAt.getTime() > maintenant.getTime();

/**
 * Choisit l'acces a ecrire a partir de TOUS les achats lies au compte (en general 1, 2 au plus apres un changement de store) et de l'accord deja present.
 *  - achat retenu : un achat qui donne le droit maintenant, le plus tardif ; a defaut, le plus recent (pour dire « expire », « rembourse »...) ;
 *  - un accord « a la main » encore valide n'est ecrase que si l'echeance du store est PLUS TARDIVE (ticket §9.1) ; sinon il est conserve tel quel ;
 *  - aucune ecriture si rien ne change (economise une ecriture et evite de reveiller les ecouteurs des apps).
 * `pending` n'accorde rien et n'est jamais retenu.
 */
export function calculerAcces(achats: readonly AchatLie[], actuel: AccesActuel | null, maintenant: Date): DecisionAcces {
  const candidats = achats.filter((a) => a.state !== "pending");
  if (candidats.length === 0) return { ecrire: false, raison: "aucun_achat" };
  const fin = (a: AchatLie): number => a.expiresAt?.getTime() ?? -Infinity;
  const retenu = [...candidats].sort((a, b) => Number(effectif(b, maintenant)) - Number(effectif(a, maintenant)) || fin(b) - fin(a) || a.cle.localeCompare(b.cle))[0] as AchatLie;
  const donne = effectif(retenu, maintenant);

  const manuelValide =
    actuel?.source === "manual" && actuel.active === true && actuel.expiresAt instanceof Date && actuel.expiresAt.getTime() > maintenant.getTime();
  if (manuelValide && (!donne || fin(retenu) <= (actuel.expiresAt as Date).getTime())) return { ecrire: false, raison: "manuel_conserve" };

  const acces: AccesEcrit = {
    active: donne,
    expiresAt: retenu.expiresAt ?? maintenant,
    source: retenu.store,
    updatedAt: maintenant,
    // Un achat dont l'echeance est passee n'est plus « actif » meme si sa derniere lecture le disait.
    state: !donne && retenu.active ? "expired" : retenu.state,
    plan: retenu.plan,
    trial: donne && retenu.trial,
    willRenew: donne && retenu.willRenew,
    test: retenu.test,
  };
  if (actuel && memeAcces(acces, actuel)) return { ecrire: false, raison: "inchange" };
  return { ecrire: true, acces };
}

function memeAcces(a: AccesEcrit, c: AccesActuel): boolean {
  return (
    c.active === a.active &&
    c.expiresAt instanceof Date &&
    c.expiresAt.getTime() === a.expiresAt.getTime() &&
    c.source === a.source &&
    c.state === a.state &&
    c.plan === a.plan &&
    c.trial === a.trial &&
    c.willRenew === a.willRenew &&
    c.test === a.test
  );
}
