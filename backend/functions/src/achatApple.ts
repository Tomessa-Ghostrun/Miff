// Client Apple (SRV-7) : verification des transactions et notifications SIGNEES (JWS, chaine de certificats jusqu'a la racine d'Apple) et lecture des statuts par
// l'App Store Server API. Bibliotheque officielle `@apple/app-store-server-library` (Node 22 ou plus) : `SignedDataVerifier` et `AppStoreServerAPIClient`.
// Format lu dans la bibliotheque et la documentation d'Apple (2026-11-21) : transaction (`JWSTransactionDecodedPayload`), renouvellement (`JWSRenewalInfoDecodedPayload`),
// notification V2 (`ResponseBodyV2DecodedPayload` : notificationType, subtype, notificationUUID, data { environment, bundleId, appAppleId, signedTransactionInfo,
// signedRenewalInfo, status }).
// Securite :
//  - seuls les environnements « Production » et « Sandbox » sont acceptes : les environnements « Xcode » et « LocalTesting » ne sont PAS signes par Apple, la bibliotheque
//    saute alors toute verification ; un JWS qui s'en reclame est refuse AVANT d'atteindre la bibliotheque ;
//  - l'environnement lu dans le JWS NON verifie ne sert qu'a CHOISIR le verificateur ; le verificateur controle ensuite qu'il est bien celui de la donnee signee ;
//  - la verification en production exige l'identifiant Apple de l'app (`appAppleId`) ; sans lui, la production est « non configuree » (refus), jamais « acceptee sans controle » ;
//  - verifications en ligne activees (revocation des certificats, date du jour) : une panne reseau est une erreur PASSAGERE (Apple reessaie ses notifications) ;
//  - l'etat qui decide du droit vient de l'API Apple (relecture), jamais du seul contenu d'une notification.
import type { SignedDataVerifier, AppStoreServerAPIClient, Environment as EnvironnementLib } from "@apple/app-store-server-library";
import { ErreurAchat } from "./achatErreurs.js";
import { BUNDLE_APPLE, etatDepuisApple, idTransactionAppleValide, PRODUITS_APPLE, type EtatStore, type RenouvellementApple, type TransactionApple } from "./achatRegles.js";
import { racinesApple } from "./appleRacines.js";
import type { ChargerConfigApple, ConfigApple } from "./configApple.js";

export type EnvApple = "Production" | "Sandbox";

export interface NotificationApple {
  /** Identifiant unique de la notification (idempotence). */
  uuid: string;
  type: string;
  sousType: string | null;
  environnement: EnvApple;
  /** Identifiant de la transaction d'origine de l'abonnement ; `null` si la notification n'en porte pas (TEST...). */
  idTransactionOrigine: string | null;
}

export interface ClientApple {
  /** Verifie la signature d'une transaction envoyee par l'app (StoreKit 2 `jwsRepresentation`) et renvoie l'identite de l'achat. */
  verifierTransactionApp(jws: string): Promise<{ environnement: EnvApple; idTransactionOrigine: string }>;
  /** Verifie la signature d'une notification serveur V2. Leve `signature_invalide` (definitive) ou `indisponible` (passagere). */
  verifierNotification(signedPayload: string): Promise<NotificationApple>;
  /** Etat COURANT de l'abonnement (statuts de l'App Store Server API), relu chez Apple. */
  lireEtat(environnement: EnvApple, idTransactionOrigine: string, maintenant: Date): Promise<EtatStore>;
}

type BibliothequeApple = typeof import("@apple/app-store-server-library");

/** Point d'extension des tests : tout est injecte, rien n'atteint le reseau. */
export interface OptionsClientApple {
  config: ChargerConfigApple;
  racines?: Buffer[];
  verificationsEnLigne?: boolean;
  bibliotheque?: () => Promise<BibliothequeApple>;
  /** Fabrique de l'API Apple (par defaut `AppStoreServerAPIClient`). */
  fabriqueApi?: (bib: BibliothequeApple, env: EnvApple, config: ConfigApple) => Pick<AppStoreServerAPIClient, "getAllSubscriptionStatuses">;
}

/** Lit `environment` dans un JWS SANS le verifier (choix du verificateur seulement). `null` pour tout ce qui n'est pas Production ou Sandbox. */
export function environnementDeclare(jws: string): EnvApple | null {
  try {
    const segments = jws.split(".");
    if (segments.length !== 3) return null;
    const charge = JSON.parse(Buffer.from(segments[1] as string, "base64url").toString("utf8")) as { environment?: unknown; data?: { environment?: unknown } };
    const env = charge.environment ?? charge.data?.environment;
    return env === "Production" || env === "Sandbox" ? env : null;
  } catch {
    return null;
  }
}

/** Controle de FORME avant toute verification (et avant tout acces a Firestore) : un JWS d'Apple est signe ES256 et porte une chaine de 3 certificats (x5c). */
export function entetePlausible(jws: string): boolean {
  try {
    const segments = jws.split(".");
    if (segments.length !== 3) return false;
    const entete = JSON.parse(Buffer.from(segments[0] as string, "base64url").toString("utf8")) as { alg?: unknown; x5c?: unknown };
    return entete.alg === "ES256" && Array.isArray(entete.x5c) && entete.x5c.length === 3 && entete.x5c.every((c) => typeof c === "string" && c.length > 0 && c.length < 4096);
  } catch {
    return false;
  }
}

export function creerClientApple(options: OptionsClientApple): ClientApple {
  const charger = options.bibliotheque ?? (() => import("@apple/app-store-server-library"));
  const racines = options.racines ?? racinesApple();
  const enLigne = options.verificationsEnLigne ?? true;
  /** Un verificateur par environnement ET par identifiant d'app : si le PM corrige `appAppleId` dans la configuration, l'ancien verificateur n'est plus reutilise. */
  const verificateurs = new Map<string, SignedDataVerifier>();

  async function verificateur(env: EnvApple): Promise<{ v: SignedDataVerifier; bib: BibliothequeApple }> {
    const bib = await charger();
    let appAppleId: number | undefined;
    if (env === "Production") {
      const config = await options.config();
      if (config === null || config.appAppleId === null) throw new ErreurAchat("non_configure");
      appAppleId = config.appAppleId;
    }
    const cle = `${env}:${appAppleId ?? ""}`;
    const connu = verificateurs.get(cle);
    if (connu) return { v: connu, bib };
    const cree = new bib.SignedDataVerifier(racines, enLigne, env === "Production" ? bib.Environment.PRODUCTION : bib.Environment.SANDBOX, BUNDLE_APPLE, appAppleId);
    verificateurs.set(cle, cree);
    return { v: cree, bib };
  }

  /** Execute une verification et traduit l'echec : panne reseau (ex. controle de revocation) = passagere ; tout le reste = signature invalide. */
  async function verifier<T>(bib: BibliothequeApple, action: () => Promise<T>): Promise<T> {
    try {
      return await action();
    } catch (e) {
      if (e instanceof ErreurAchat) throw e;
      if (e instanceof bib.VerificationException && e.status === bib.VerificationStatus.RETRYABLE_VERIFICATION_FAILURE) throw new ErreurAchat("indisponible", true);
      throw new ErreurAchat("signature_invalide");
    }
  }

  return {
    async verifierTransactionApp(jws) {
      const env = environnementDeclare(jws);
      if (env === null || !entetePlausible(jws)) throw new ErreurAchat("signature_invalide");
      const { v, bib } = await verificateur(env);
      const t = (await verifier(bib, () => v.verifyAndDecodeTransaction(jws))) as TransactionApple;
      if (PRODUITS_APPLE[t.productId ?? ""] === undefined) throw new ErreurAchat("produit_inconnu");
      if (!idTransactionAppleValide(t.originalTransactionId)) throw new ErreurAchat("signature_invalide");
      return { environnement: env, idTransactionOrigine: t.originalTransactionId };
    },

    async verifierNotification(signedPayload) {
      const env = environnementDeclare(signedPayload);
      if (env === null || !entetePlausible(signedPayload)) throw new ErreurAchat("signature_invalide");
      const { v, bib } = await verificateur(env);
      const n = await verifier(bib, () => v.verifyAndDecodeNotification(signedPayload));
      if (typeof n.notificationUUID !== "string" || typeof n.notificationType !== "string") throw new ErreurAchat("signature_invalide");
      let idTransactionOrigine: string | null = null;
      const transaction = n.data?.signedTransactionInfo;
      if (typeof transaction === "string") {
        const t = (await verifier(bib, () => v.verifyAndDecodeTransaction(transaction))) as TransactionApple;
        if (idTransactionAppleValide(t.originalTransactionId)) idTransactionOrigine = t.originalTransactionId;
      }
      return { uuid: n.notificationUUID, type: n.notificationType, sousType: typeof n.subtype === "string" ? n.subtype : null, environnement: env, idTransactionOrigine };
    },

    async lireEtat(env, idTransactionOrigine, maintenant) {
      if (!idTransactionAppleValide(idTransactionOrigine)) throw new ErreurAchat("entree_invalide");
      const config = await options.config();
      if (config === null) throw new ErreurAchat("non_configure");
      const { v, bib } = await verificateur(env);
      const api = (options.fabriqueApi ?? fabriqueApiParDefaut)(bib, env, config);
      let reponse;
      try {
        reponse = await api.getAllSubscriptionStatuses(idTransactionOrigine);
      } catch (e) {
        const statut = (e as { httpStatusCode?: unknown }).httpStatusCode;
        if (statut === 404) throw new ErreurAchat("achat_inconnu");
        if (statut === 401 || statut === 403) throw new ErreurAchat("acces_refuse");
        throw new ErreurAchat("indisponible", true);
      }
      // L'identifiant d'app et l'environnement de la reponse sont aussi controles : une reponse d'une autre app est refusee.
      if (reponse.bundleId !== undefined && reponse.bundleId !== BUNDLE_APPLE) throw new ErreurAchat("signature_invalide");
      for (const groupe of reponse.data ?? []) {
        for (const ligne of groupe.lastTransactions ?? []) {
          if (ligne.originalTransactionId !== idTransactionOrigine || typeof ligne.signedTransactionInfo !== "string" || typeof ligne.status !== "number") continue;
          const signee = ligne.signedTransactionInfo;
          const t = (await verifier(bib, () => v.verifyAndDecodeTransaction(signee))) as TransactionApple;
          const renouvellementSigne = ligne.signedRenewalInfo;
          const r = typeof renouvellementSigne === "string" ? ((await verifier(bib, () => v.verifyAndDecodeRenewalInfo(renouvellementSigne))) as RenouvellementApple) : null;
          return etatDepuisApple(ligne.status, t, r, maintenant);
        }
      }
      throw new ErreurAchat("achat_inconnu");
    },
  };
}

function fabriqueApiParDefaut(bib: BibliothequeApple, env: EnvApple, config: ConfigApple): Pick<AppStoreServerAPIClient, "getAllSubscriptionStatuses"> {
  const environnement: EnvironnementLib = env === "Production" ? bib.Environment.PRODUCTION : bib.Environment.SANDBOX;
  return new bib.AppStoreServerAPIClient(config.cle, config.keyId, config.issuerId, BUNDLE_APPLE, environnement);
}
