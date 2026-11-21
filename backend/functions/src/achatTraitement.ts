// Traitement du premium reel (SRV-7, ticket docs/tickets/premium-achat-reel.md §5, §8, §9, §10). Les clients du store (Google, Apple), Firestore, l'horloge et le generateur
// d'identifiants sont INJECTES : tout est teste sans reseau (test/integration/achat-*.test.mjs, a l'emulateur Firestore).
//
// PRINCIPES (revue de securite : usurpation d'achat, rejeu, confusion de comptes, injection d'identifiant, abus de quota, ecriture directe de `entitlements`) :
//  1. SEUL CE CODE ECRIT `entitlements/{uid}` pour un achat reel (Admin SDK) ; les regles Firestore l'interdisent a tout client (test BL*).
//  2. Le store est la PREUVE : l'etat vient d'une relecture chez Google (`subscriptionsv2.get`) ou Apple (API Server + JWS verifie), jamais du contenu d'une notification
//     ni d'une affirmation de l'app. Une notification falsifiee ne peut au pire que declencher une relecture sans effet.
//  3. LIAISON achat <-> compte par un identifiant ALEATOIRE fabrique ici (`billingAccounts/{uid}.purchaseId`, jamais derive de l'uid ni de l'e-mail) : l'app le transmet au store
//     a l'achat (Google `obfuscatedAccountId`, Apple `appAccountToken`) ; le store le renvoie ; il doit etre CELUI DE L'APPELANT authentifie. Un achat deja lie a un compte
//     existant ne peut pas etre lie a un autre (« un achat = un compte »). Un achat sans identifiant (fait hors de l'app) est lie a l'appelant qui presente la preuve
//     (jeton d'achat Google, transaction signee Apple), s'il n'est lie a personne.
//  4. Une notification ne lie JAMAIS un achat a un compte que par l'identifiant d'achat lu chez le store, le lien deja etabli, ou le jeton de l'achat precedent.
//  5. Idempotence : l'etat ecrit est toujours celui relu au store ; un etat plus ANCIEN que celui deja ecrit est ignore ; un identifiant de message deja traite est ignore
//     (`billingEvents`, TTL 7 jours) ; l'achat, l'acces et l'identifiant de message s'ecrivent dans UNE transaction.
//  6. Aucune reference d'achat (jeton Google, transaction Apple) n'est conservee : la cle d'un achat est un SHA-256.
import { Timestamp, type DocumentSnapshot, type Firestore, type Query, type QuerySnapshot, type Transaction } from "firebase-admin/firestore";
import type { ClientApple } from "./achatApple.js";
import { cheminAcces, cheminAchat, cheminCompte, cheminEvenement, COLLECTION_ACHATS, COLLECTION_COMPTES, idEvenementValide, uidValide } from "./achatChemins.js";
import { ErreurAchat, type RaisonAchat } from "./achatErreurs.js";
import type { ClientGoogle } from "./achatGoogle.js";
import {
  calculerAcces,
  cleAchat,
  decoderMessageGoogle,
  DELAI_MIN_SYNCHRO_MS,
  DUREE_ACHAT_APRES_ECHEANCE_MS,
  DUREE_EVENEMENT_MS,
  DUREE_MAX_REPRISE_MS,
  DUREE_PURGE_COMPTE_SUPPRIME_MS,
  etatDepuisGoogle,
  jetonGoogleValide,
  jwsValide,
  normaliserIdAchat,
  TYPE_GOOGLE_REVOQUE,
  uuidValide,
  type AccesActuel,
  type AchatLie,
  type Etat,
  type EtatStore,
  type Plan,
  type Store,
} from "./achatRegles.js";
import { journaliser } from "./journal.js";

export interface DepsAchat {
  db: Firestore;
  google: ClientGoogle;
  apple: ClientApple;
  maintenant: () => Date;
  /** UUID aleatoire (v4) : `crypto.randomUUID`. Injecte pour les tests. */
  uuid: () => string;
}

interface Bilan {
  lectures: number;
  ecritures: number;
}
const nouveauBilan = (): Bilan => ({ lectures: 0, ecritures: 0 });

const ts = (d: Date): Timestamp => Timestamp.fromDate(d);
const dateDe = (v: unknown): Date | null => (v instanceof Timestamp ? v.toDate() : null);
const ETATS: readonly Etat[] = ["trial", "active", "canceled", "grace", "on_hold", "expired", "revoked", "pending"];
const ETATS_AVEC_DROIT: readonly Etat[] = ["trial", "active", "canceled", "grace"];

// ---------------------------------------------------------------------------------------------------------------------------------
// preparerAchat : l'identifiant d'achat du compte (cree au premier appel, identique ensuite)
// ---------------------------------------------------------------------------------------------------------------------------------
export async function preparerAchat(deps: DepsAchat, uid: string): Promise<{ purchaseId: string }> {
  const bilan = nouveauBilan();
  const ref = deps.db.doc(cheminCompte(uid));
  const resultat = await deps.db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    bilan.lectures += 1;
    const existant: unknown = snap.get("purchaseId");
    if (uuidValide(existant)) return { purchaseId: normaliserIdAchat(existant) };
    const purchaseId = normaliserIdAchat(deps.uuid());
    tx.set(ref, { v: 1, purchaseId, createdAt: ts(deps.maintenant()) }, { merge: true });
    bilan.ecritures += 1;
    return { purchaseId };
  });
  journaliser("info", "billing.prepare.ok", { resultat: "ok", lectures: bilan.lectures, ecritures: bilan.ecritures });
  return resultat;
}

// ---------------------------------------------------------------------------------------------------------------------------------
// synchroniserAchat : l'app presente un achat (jeton Google ou transaction signee Apple)
// ---------------------------------------------------------------------------------------------------------------------------------
export type EntreeSynchro = { store: "google_play"; purchaseToken: string } | { store: "app_store"; signedTransaction: string };

/** Valide la forme d'une entree d'appelable (donnees non fiables) : tout champ en trop est refuse. */
export function validerEntreeSynchro(donnees: unknown): EntreeSynchro {
  if (typeof donnees !== "object" || donnees === null || Array.isArray(donnees)) throw new ErreurAchat("entree_invalide");
  const d = donnees as Record<string, unknown>;
  const cles = Object.keys(d).sort().join(",");
  if (d["store"] === "google_play" && cles === "purchaseToken,store") {
    if (!jetonGoogleValide(d["purchaseToken"])) throw new ErreurAchat("jeton_invalide");
    return { store: "google_play", purchaseToken: d["purchaseToken"] };
  }
  if (d["store"] === "app_store" && cles === "signedTransaction,store") {
    if (!jwsValide(d["signedTransaction"])) throw new ErreurAchat("jeton_invalide");
    return { store: "app_store", signedTransaction: d["signedTransaction"] };
  }
  throw new ErreurAchat("entree_invalide");
}

export async function synchroniserAchat(deps: DepsAchat, uid: string, entree: EntreeSynchro): Promise<{ state: Etat }> {
  const bilan = nouveauBilan();
  try {
    await reserverSynchro(deps, uid, bilan);
    let etat: EtatStore;
    let cle: string;
    let cleLiee: string | null = null;
    if (entree.store === "google_play") {
      const lecture = deps.maintenant();
      etat = etatDepuisGoogle(await deps.google.lire(entree.purchaseToken), lecture);
      cle = cleAchat("google_play", entree.purchaseToken);
      cleLiee = etat.lienPrecedent === null ? null : cleAchat("google_play", etat.lienPrecedent);
    } else {
      const identite = await deps.apple.verifierTransactionApp(entree.signedTransaction);
      etat = await deps.apple.lireEtat(identite.environnement, identite.idTransactionOrigine, deps.maintenant());
      cle = cleAchat("app_store", identite.idTransactionOrigine);
    }
    if (etat.famille) throw new ErreurAchat("partage_famille");
    const resultat = await appliquer(deps, bilan, { cle, etat, appelant: uid, evenementId: null, cleLiee });
    if (resultat.accuser && entree.store === "google_play") await deps.google.accuser(entree.purchaseToken);
    journaliser("info", "billing.sync.ok", { resultat: "ok", lectures: bilan.lectures, ecritures: bilan.ecritures });
    return { state: resultat.etat };
  } catch (e) {
    if (e instanceof ErreurAchat) journaliser(e.transitoire ? "warn" : "info", "billing.sync.refuse", { resultat: "refuse", motif: e.raison });
    else journaliser("error", "billing.sync.erreur", { resultat: "erreur" });
    throw e;
  }
}

/** Limite de frequence : 1 synchronisation par minute et par compte, consommee AVANT tout appel au store (protege le quota de l'API de Google). Cree le compte si besoin. */
async function reserverSynchro(deps: DepsAchat, uid: string, bilan: Bilan): Promise<void> {
  const ref = deps.db.doc(cheminCompte(uid));
  await deps.db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    bilan.lectures += 1;
    const maintenant = deps.maintenant();
    const derniere = dateDe(snap.get("lastSyncAt"));
    if (derniere !== null) {
      const ecart = maintenant.getTime() - derniere.getTime();
      if (ecart >= 0 && ecart < DELAI_MIN_SYNCHRO_MS) throw new ErreurAchat("limite_frequence");
    }
    const existant: unknown = snap.get("purchaseId");
    if (uuidValide(existant)) tx.update(ref, { lastSyncAt: ts(maintenant) });
    else tx.set(ref, { v: 1, purchaseId: normaliserIdAchat(deps.uuid()), createdAt: ts(maintenant), lastSyncAt: ts(maintenant) }, { merge: true });
    bilan.ecritures += 1;
  });
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Coeur : lier, ecrire l'achat, recalculer l'acces (UNE transaction)
// ---------------------------------------------------------------------------------------------------------------------------------
interface EntreeAppliquer {
  cle: string;
  etat: EtatStore;
  /** Appelant authentifie (synchroniserAchat) ; `null` pour une notification (aucun appelant : le compte vient du lien, de l'identifiant d'achat ou de l'achat precedent). */
  appelant: string | null;
  /** Identifiant de message / notification a marquer comme traite dans la MEME transaction ; `null` pour une synchronisation. */
  evenementId: string | null;
  cleLiee: string | null;
}
interface Resultat {
  statut: "ecrit" | "ancien" | "inconnu" | "attente";
  uid: string | null;
  etat: Etat;
  /** Google : l'achat doit etre acquitte (le droit vient d'etre accorde et l'achat n'est pas encore acquitte). */
  accuser: boolean;
}

async function appliquer(deps: DepsAchat, bilan: Bilan, e: EntreeAppliquer): Promise<Resultat> {
  const { db } = deps;
  const maintenant = deps.maintenant();
  const etat = e.etat;
  if (etat.famille) throw new ErreurAchat("partage_famille");
  const refAchat = db.doc(cheminAchat(e.cle));

  return db.runTransaction(async (tx: Transaction): Promise<Resultat> => {
    const lireDoc = async (ref: ReturnType<Firestore["doc"]>): Promise<DocumentSnapshot> => {
      bilan.lectures += 1;
      return tx.get(ref);
    };
    const lireRequete = async (q: Query): Promise<QuerySnapshot> => {
      const r = await tx.get(q);
      bilan.lectures += Math.max(1, r.size);
      return r;
    };
    const parIdentifiant = (id: string): Query => db.collection(COLLECTION_COMPTES).where("purchaseId", "==", id).limit(1);

    // ---- LECTURES (toutes avant la premiere ecriture) ----
    const lien = await lireDoc(refAchat);
    const lienUidBrut: unknown = lien.exists ? lien.get("uid") : null;
    const lienUid = uidValide(lienUidBrut) ? lienUidBrut : null;
    const ancien = lien.exists
      ? {
          observeA: typeof lien.get("observeA") === "number" ? (lien.get("observeA") as number) : 0,
          state: lien.get("state") as unknown,
          expiresAt: dateDe(lien.get("expiresAt")),
          createdAt: dateDe(lien.get("createdAt")),
        }
      : null;

    let uid: string | null;
    let creerCompte: string | null = null;
    if (e.appelant !== null) {
      // --- Appel de l'app : l'acces ne peut aller qu'a l'appelant ---
      const compte = await lireDoc(db.doc(cheminCompte(e.appelant)));
      const idStocke: unknown = compte.exists ? compte.get("purchaseId") : null;
      let monId = uuidValide(idStocke) ? normaliserIdAchat(idStocke) : null;
      if (monId === null) {
        monId = normaliserIdAchat(deps.uuid());
        creerCompte = monId;
      }
      if (lienUid !== e.appelant) {
        // Deja lie a un AUTRE compte qui existe encore : refus, sans rien dire de l'autre compte. Si l'autre compte a ete supprime (lien orphelin), la reprise est permise.
        if (lienUid !== null && (await lireDoc(db.doc(cheminCompte(lienUid)))).exists) throw new ErreurAchat("deja_lie");
        // Identifiant d'achat du store different du mien : permis seulement s'il n'appartient a AUCUN compte existant (achat fait avant une suppression de compte).
        if (etat.idCompte !== null && etat.idCompte !== monId) {
          const proprietaire = await lireRequete(parIdentifiant(etat.idCompte));
          if (!proprietaire.empty && proprietaire.docs[0]?.id !== e.appelant) throw new ErreurAchat("deja_lie");
        }
      }
      uid = e.appelant;
    } else {
      // --- Notification : aucun appelant ---
      // Le compte lie doit exister encore : une notification ne ressuscite jamais les donnees d'un compte supprime (lien orphelin apres une suppression interrompue).
      uid = lienUid !== null && (await lireDoc(db.doc(cheminCompte(lienUid)))).exists ? lienUid : null;
      if (uid === null && etat.idCompte !== null) {
        const proprietaire = await lireRequete(parIdentifiant(etat.idCompte));
        const trouve = proprietaire.docs[0]?.id;
        if (uidValide(trouve)) uid = trouve;
      }
      if (uid === null && e.cleLiee !== null) {
        const precedent = await lireDoc(db.doc(cheminAchat(e.cleLiee)));
        const u: unknown = precedent.exists ? precedent.get("uid") : null;
        if (uidValide(u) && (await lireDoc(db.doc(cheminCompte(u)))).exists) uid = u;
      }
    }

    const refEvenement = e.evenementId === null ? null : db.doc(cheminEvenement(e.evenementId));
    const marquer = (): void => {
      if (refEvenement === null) return;
      tx.set(refEvenement, { purgeAt: ts(new Date(maintenant.getTime() + DUREE_EVENEMENT_MS)) });
      bilan.ecritures += 1;
    };

    // Rien n'est paye ni lie : (achat en attente) pas de document, aucun droit.
    if (etat.expiresAt === null && !lien.exists) {
      marquer();
      return { statut: "attente", uid, etat: etat.state, accuser: false };
    }
    if (uid === null) {
      marquer();
      return { statut: "inconnu", uid: null, etat: etat.state, accuser: false };
    }
    // Un etat plus ANCIEN que celui deja ecrit (execution concurrente, message en retard) est ignore.
    if (ancien !== null && lienUid === uid && etat.observeA < ancien.observeA) {
      marquer();
      return { statut: "ancien", uid, etat: ETATS.includes(ancien.state as Etat) ? (ancien.state as Etat) : etat.state, accuser: false };
    }
    // Google : un achat revoque ou rembourse ne revit pas (decision Q3 : l'acces est coupe a la confirmation du store).
    let final = etat;
    if (ancien !== null && lienUid === uid && etat.store === "google_play" && ancien.state === "revoked" && etat.state !== "revoked") {
      final = { ...etat, state: "revoked", active: false, expiresAt: ancien.expiresAt ?? maintenant, willRenew: false, trial: false, accuser: false };
    }

    const achatsDuCompte = await lireRequete(db.collection(COLLECTION_ACHATS).where("uid", "==", uid));
    const acces = await lireDoc(db.doc(cheminAcces(uid)));

    // ---- ECRITURES ----
    if (creerCompte !== null && e.appelant !== null) {
      tx.set(db.doc(cheminCompte(e.appelant)), { v: 1, purchaseId: creerCompte, createdAt: ts(maintenant) }, { merge: true });
      bilan.ecritures += 1;
    }
    const finAcces = final.expiresAt !== null && final.expiresAt.getTime() > maintenant.getTime() ? final.expiresAt : maintenant;
    tx.set(refAchat, {
      v: 1,
      uid,
      store: final.store,
      produit: final.produit,
      plan: final.plan,
      state: final.state,
      active: final.active,
      expiresAt: final.expiresAt === null ? null : ts(final.expiresAt),
      willRenew: final.willRenew,
      trial: final.trial,
      test: final.test,
      observeA: final.observeA,
      createdAt: ts(ancien?.createdAt ?? maintenant),
      updatedAt: ts(maintenant),
      purgeAt: ts(new Date(finAcces.getTime() + DUREE_ACHAT_APRES_ECHEANCE_MS)),
    });
    bilan.ecritures += 1;

    const achats: AchatLie[] = [
      ...achatsDuCompte.docs.filter((d) => d.id !== e.cle).map(lireAchatLie).filter((a): a is AchatLie => a !== null),
      {
        cle: e.cle,
        store: final.store,
        state: final.state,
        active: final.active,
        expiresAt: final.expiresAt,
        plan: final.plan,
        trial: final.trial,
        willRenew: final.willRenew,
        test: final.test,
      },
    ];
    const decision = calculerAcces(achats, lireAccesActuel(acces), maintenant);
    if (decision.ecrire) {
      const a = decision.acces;
      tx.set(db.doc(cheminAcces(uid)), {
        active: a.active,
        expiresAt: ts(a.expiresAt),
        source: a.source,
        updatedAt: ts(a.updatedAt),
        state: a.state,
        plan: a.plan,
        trial: a.trial,
        willRenew: a.willRenew,
        test: a.test,
      });
      bilan.ecritures += 1;
    }
    marquer();
    return { statut: "ecrit", uid, etat: final.state, accuser: final.accuser && ETATS_AVEC_DROIT.includes(final.state) };
  });
}

function lireAchatLie(doc: DocumentSnapshot): AchatLie | null {
  const store: unknown = doc.get("store");
  const state: unknown = doc.get("state");
  if ((store !== "google_play" && store !== "app_store") || typeof state !== "string" || !ETATS.includes(state as Etat)) return null;
  const plan: unknown = doc.get("plan");
  return {
    cle: doc.id,
    store: store as Store,
    state: state as Etat,
    active: doc.get("active") === true,
    expiresAt: dateDe(doc.get("expiresAt")),
    plan: plan === "yearly" || plan === "monthly" ? (plan as Plan) : null,
    trial: doc.get("trial") === true,
    willRenew: doc.get("willRenew") === true,
    test: doc.get("test") === true,
  };
}

function lireAccesActuel(doc: DocumentSnapshot): AccesActuel | null {
  if (!doc.exists) return null;
  return {
    active: doc.get("active"),
    expiresAt: dateDe(doc.get("expiresAt")),
    source: doc.get("source"),
    state: doc.get("state"),
    plan: doc.get("plan"),
    trial: doc.get("trial"),
    willRenew: doc.get("willRenew"),
    test: doc.get("test"),
  };
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Notifications temps reel de Google (Pub/Sub `play-rtdn`)
// ---------------------------------------------------------------------------------------------------------------------------------
export interface MessageGoogle {
  messageId: string;
  /** Heure de publication (RFC 3339) : sert seulement a abandonner une reprise vieille de plus de 24 h. */
  publishTime: string | undefined;
  /** Champ `data` du message, en base64. */
  data: string;
}

/**
 * Traite un message RTDN. Ne leve QUE pour demander une nouvelle tentative (erreur passagere, au plus 24 h) : une erreur definitive est consommee (journal fixe).
 * Un message qui n'est pas conforme, d'un autre package ou d'un autre genre est ignore.
 */
export async function traiterMessageGoogle(deps: DepsAchat, message: MessageGoogle): Promise<void> {
  const bilan = nouveauBilan();
  if (!idEvenementValide(message.messageId)) {
    journaliser("warn", "billing.google.ignore", { resultat: "refuse", motif: "entree_invalide" });
    return;
  }
  const notification = decoderMessageGoogle(message.data);
  if (notification.sorte === "test") {
    journaliser("info", "billing.google.ignore", { resultat: "ok", motif: "test_notification" });
    return;
  }
  if (notification.sorte === "autre_package") {
    journaliser("warn", "billing.google.ignore", { resultat: "refuse", motif: "autre_package" });
    return;
  }
  if (notification.sorte === "ignore") {
    journaliser("info", "billing.google.ignore", { resultat: "refuse", motif: notification.motif });
    return;
  }
  const evenementId = `g-${message.messageId}`;
  const vu = await deps.db.doc(cheminEvenement(evenementId)).get();
  bilan.lectures += 1;
  if (vu.exists) {
    journaliser("info", "billing.google.ignore", { resultat: "ok", motif: "deja_vu", lectures: bilan.lectures });
    return;
  }
  const publie = message.publishTime === undefined ? Number.NaN : Date.parse(message.publishTime);
  const tropVieux = !Number.isNaN(publie) && deps.maintenant().getTime() - publie > DUREE_MAX_REPRISE_MS;
  try {
    const revoque = notification.sorte === "annule" || notification.type === TYPE_GOOGLE_REVOQUE;
    const lecture = deps.maintenant();
    const etat = etatDepuisGoogle(await deps.google.lire(notification.jeton), lecture, { revoque });
    const resultat = await appliquer(deps, bilan, {
      cle: cleAchat("google_play", notification.jeton),
      etat,
      appelant: null,
      evenementId,
      cleLiee: etat.lienPrecedent === null ? null : cleAchat("google_play", etat.lienPrecedent),
    });
    if (resultat.statut === "inconnu") journaliser("info", "billing.google.notif", { resultat: "refuse", motif: "compte_inconnu", lectures: bilan.lectures, ecritures: bilan.ecritures });
    else {
      if (resultat.accuser) await deps.google.accuser(notification.jeton);
      journaliser("info", "billing.google.notif", { resultat: "ok", lectures: bilan.lectures, ecritures: bilan.ecritures });
    }
  } catch (e) {
    if (e instanceof ErreurAchat && !passager(e.raison, e.transitoire)) {
      journaliser("info", "billing.google.notif", { resultat: "refuse", motif: e.raison });
      return;
    }
    if (tropVieux) {
      journaliser("warn", "billing.google.notif", { resultat: "erreur", motif: "abandon" });
      return;
    }
    journaliser("warn", "billing.google.notif", { resultat: "erreur", ...(e instanceof ErreurAchat ? { motif: e.raison } : {}) });
    // Nouvelle tentative demandee a Pub/Sub. Une exception inattendue est remplacee par un message fixe : le detail (chemin, identifiant) n'atterrit pas dans les journaux de la plateforme.
    throw e instanceof ErreurAchat ? e : new Error("billing.erreur");
  }
}

/** Une erreur est « passagere » (nouvelle tentative utile) si elle l'est par nature, ou si elle tient a une configuration que le PM peut encore completer. */
const passager = (raison: RaisonAchat, transitoire: boolean): boolean => transitoire || raison === "acces_refuse" || raison === "non_configure";

// ---------------------------------------------------------------------------------------------------------------------------------
// Notifications serveur V2 d'Apple (HTTPS public)
// ---------------------------------------------------------------------------------------------------------------------------------
/** Code HTTP a renvoyer a Apple : 200 = accuse de reception (aucune relance) ; 400 = refuse ; 503 = reessayez (Apple relance a 1, 12, 24, 48 et 72 h en production). */
export async function traiterNotificationApple(deps: DepsAchat, signedPayload: string): Promise<{ http: 200 | 400 | 503 }> {
  const bilan = nouveauBilan();
  let notification;
  try {
    notification = await deps.apple.verifierNotification(signedPayload);
  } catch (e) {
    if (e instanceof ErreurAchat && e.transitoire) {
      journaliser("warn", "billing.apple.notif", { resultat: "erreur", motif: "indisponible" });
      return { http: 503 };
    }
    if (e instanceof ErreurAchat && e.raison === "non_configure") {
      journaliser("warn", "billing.apple.notif", { resultat: "erreur", motif: "non_configure" });
      return { http: 503 };
    }
    journaliser("warn", "billing.apple.signature-invalide", { resultat: "refuse", motif: "signature_invalide" });
    return { http: 400 };
  }
  if (!idEvenementValide(notification.uuid)) {
    journaliser("warn", "billing.apple.ignore", { resultat: "refuse", motif: "entree_invalide" });
    return { http: 400 };
  }
  if (notification.idTransactionOrigine === null) {
    journaliser("info", "billing.apple.ignore", { resultat: "ok", motif: "type_ignore" });
    return { http: 200 };
  }
  const evenementId = `a-${notification.uuid}`;
  const vu = await deps.db.doc(cheminEvenement(evenementId)).get();
  bilan.lectures += 1;
  if (vu.exists) {
    journaliser("info", "billing.apple.ignore", { resultat: "ok", motif: "deja_vu", lectures: bilan.lectures });
    return { http: 200 };
  }
  try {
    const etat = await deps.apple.lireEtat(notification.environnement, notification.idTransactionOrigine, deps.maintenant());
    const resultat = await appliquer(deps, bilan, { cle: cleAchat("app_store", notification.idTransactionOrigine), etat, appelant: null, evenementId, cleLiee: null });
    journaliser("info", "billing.apple.notif", {
      resultat: resultat.statut === "inconnu" ? "refuse" : "ok",
      ...(resultat.statut === "inconnu" ? { motif: "compte_inconnu" as const } : {}),
      lectures: bilan.lectures,
      ecritures: bilan.ecritures,
    });
    return { http: 200 };
  } catch (e) {
    if (e instanceof ErreurAchat) {
      if (passager(e.raison, e.transitoire)) {
        journaliser("warn", "billing.apple.notif", { resultat: "erreur", motif: e.raison });
        return { http: 503 };
      }
      // Definitif (achat inconnu d'Apple, produit d'une autre app, partage en famille...) : accuse de reception, aucune relance.
      journaliser("info", "billing.apple.notif", { resultat: "refuse", motif: e.raison });
      return { http: 200 };
    }
    throw e;
  }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Suppression de compte (avant la suppression du compte d'authentification, ticket §9.5)
// ---------------------------------------------------------------------------------------------------------------------------------
/**
 * Supprime l'identifiant d'achat et les liens du compte ; le droit est marque INACTIF avec une date de purge a +30 jours (suppression automatique par la regle de duree de
 * vie de `entitlements.purgeAt`). Idempotente. Ne resilie PAS l'abonnement chez le store (l'app l'a rappele a la personne). 50 achats au plus par appel (en pratique 1 ou 2).
 */
export async function supprimerDonneesAbonnement(deps: DepsAchat, uid: string): Promise<void> {
  const bilan = nouveauBilan();
  const maintenant = deps.maintenant();
  const achats = await deps.db.collection(COLLECTION_ACHATS).where("uid", "==", uid).limit(50).get();
  bilan.lectures += Math.max(1, achats.size);
  const refAcces = deps.db.doc(cheminAcces(uid));
  const acces = await refAcces.get();
  bilan.lectures += 1;
  const lot = deps.db.batch();
  lot.delete(deps.db.doc(cheminCompte(uid)));
  bilan.ecritures += 1;
  for (const doc of achats.docs) {
    lot.delete(doc.ref);
    bilan.ecritures += 1;
  }
  if (acces.exists) {
    lot.update(refAcces, {
      active: false,
      state: "expired",
      trial: false,
      willRenew: false,
      updatedAt: ts(maintenant),
      purgeAt: ts(new Date(maintenant.getTime() + DUREE_PURGE_COMPTE_SUPPRIME_MS)),
    });
    bilan.ecritures += 1;
  }
  await lot.commit();
  journaliser("info", "billing.suppression.ok", { resultat: "ok", lectures: bilan.lectures, ecritures: bilan.ecritures });
}
