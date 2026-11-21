// Configuration de l'App Store Server API (SRV-7) : deux morceaux, lus a la demande et gardes 5 minutes en memoire (la rotation de la cle ne demande aucun deploiement).
//  - NON SECRET : `billingConfig/apple` { keyId, issuerId, appAppleId } dans Firestore (saisi par le PM dans la console ; collection refusee a tout client, comme
//    `notifState`). 1 lecture au plus toutes les 5 minutes par instance.
//  - SECRET : la cle « In-App Purchase » (.p8) dans le gestionnaire de secrets Google, secret `apple-iap-key`, lue par le compte `billing-sync` seul (role
//    « Accesseur de secrets » accorde SUR CE SECRET, jamais sur le projet). Lecture par l'API REST avec le jeton du compte de service : aucun secret lie a la fonction
//    (le deploiement ne depend donc pas de lui : Android peut etre mis en ligne avant la cle Apple). La valeur n'est jamais journalisee ni ecrite ailleurs qu'en memoire.
// Tant que la configuration est incomplete, Apple est « non configure » : les achats Apple sont refuses calmement, Google n'est pas touche.
import type { DocumentSnapshot, Firestore } from "firebase-admin/firestore";
import { CHEMIN_CONFIG_APPLE, CHEMIN_CONFIG_OPTIONS } from "./achatChemins.js";
import { ErreurAchat } from "./achatErreurs.js";
import { creerFournisseurJetons, PORTEE_CLOUD, type FournisseurJetons } from "./jetonsGoogle.js";

export const SECRET_APPLE = "apple-iap-key";
const DUREE_CACHE_MS = 5 * 60 * 1000;
const DUREE_ECHEC_MS = 30 * 1000;
const FORME_PROJET = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/;
const FORME_KEY_ID = /^[A-Z0-9]{10}$/;
const FORME_ISSUER = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
/** Une cle PEM (PKCS#8) : on ne controle que l'en-tete, jamais le contenu. */
const FORME_PEM = /^\s*-----BEGIN [A-Z ]+-----[\s\S]+-----END [A-Z ]+-----\s*$/;

export interface ConfigApple {
  /** Contenu du fichier .p8 : SECRET. */
  cle: string;
  keyId: string;
  issuerId: string;
  /** Identifiant Apple de l'app (numerique, non secret) : exige par la verification en PRODUCTION. */
  appAppleId: number | null;
}
export type ChargerConfigApple = () => Promise<ConfigApple | null>;

export interface OptionsConfigApple {
  db: Firestore;
  jetons?: FournisseurJetons;
  fetch?: typeof fetch;
  maintenant?: () => Date;
  projet?: string;
}

/** Repli : l'identifiant du projet dans `FIREBASE_CONFIG` (JSON pose par la plateforme). Jamais une entree de l'appelant. */
function projetDeLaConfigurationFirebase(): string {
  try {
    const id = (JSON.parse(process.env["FIREBASE_CONFIG"] ?? "{}") as { projectId?: unknown }).projectId;
    return typeof id === "string" ? id : "";
  } catch {
    return "";
  }
}

/**
 * Coupe-circuit des achats de TEST (revue de securite C2) : `billingConfig/options.acceptTest`. DEFAUT = VRAI (document absent, champ absent ou non booleen) : les stores exigent
 * d'accepter le bac a sable (Apple) et les testeurs de licence (Google) pendant leur revue. Seul un `false` BOOLEEN explicite le coupe. Lu au plus toutes les 5 minutes par instance.
 * Erreur de lecture : l'exception remonte (jamais « vrai » par defaut sur une panne : un achat de test ne s'accorde pas sur une lecture ratee).
 */
export function creerLecteurAcceptTest(options: { db: Firestore; maintenant?: () => Date }): () => Promise<boolean> {
  const maintenant = options.maintenant ?? (() => new Date());
  let cache: { valeur: boolean; expire: number } | null = null;
  return async () => {
    const t = maintenant().getTime();
    if (cache && cache.expire > t) return cache.valeur;
    const doc = await options.db.doc(CHEMIN_CONFIG_OPTIONS).get();
    const valeur = !(doc.exists && doc.get("acceptTest") === false);
    cache = { valeur, expire: t + DUREE_CACHE_MS };
    return valeur;
  };
}

export function creerChargeurConfigApple(options: OptionsConfigApple): ChargerConfigApple {
  const jetons = options.jetons ?? creerFournisseurJetons();
  const appeler = options.fetch ?? fetch;
  const maintenant = options.maintenant ?? (() => new Date());
  let cache: { config: ConfigApple | null; expire: number } | null = null;
  /** Echec recent (secret illisible, droit manquant) : rejoue 30 s, pour qu'un point d'entree public martele ne multiplie pas les appels a Firestore et au gestionnaire de secrets. */
  let echec: { erreur: ErreurAchat; expire: number } | null = null;

  async function lireSecret(projet: string): Promise<string | null> {
    let jeton: string;
    try {
      jeton = await jetons.jeton(PORTEE_CLOUD);
    } catch {
      throw new ErreurAchat("indisponible", true);
    }
    let reponse: Response;
    try {
      reponse = await appeler(`https://secretmanager.googleapis.com/v1/projects/${projet}/secrets/${SECRET_APPLE}/versions/latest:access`, {
        headers: { Authorization: `Bearer ${jeton}` },
        signal: AbortSignal.timeout(10_000),
      });
    } catch {
      throw new ErreurAchat("indisponible", true);
    }
    // 404 = secret ou version absente : pas encore configure. 403 = droit non accorde : meme effet cote utilisateur, le journal distingue.
    if (reponse.status === 404) return null;
    if (reponse.status === 403 || reponse.status === 401) throw new ErreurAchat("acces_refuse");
    if (!reponse.ok) throw new ErreurAchat("indisponible", true);
    try {
      const corps = (await reponse.json()) as { payload?: { data?: unknown } };
      const donnee = corps.payload?.data;
      return typeof donnee === "string" ? Buffer.from(donnee, "base64").toString("utf8") : null;
    } catch {
      throw new ErreurAchat("reponse_invalide");
    }
  }

  return async () => {
    const t = maintenant().getTime();
    if (cache && cache.expire > t) return cache.config;
    if (echec && echec.expire > t) throw echec.erreur;
    const projet = options.projet ?? process.env["GCLOUD_PROJECT"] ?? process.env["GOOGLE_CLOUD_PROJECT"] ?? projetDeLaConfigurationFirebase();
    if (!FORME_PROJET.test(projet)) throw new ErreurAchat("non_configure");
    let config: ConfigApple | null = null;
    try {
      const doc = await options.db.doc(CHEMIN_CONFIG_APPLE).get();
      config = await construire(doc, projet);
    } catch (e) {
      if (e instanceof ErreurAchat) echec = { erreur: e, expire: t + DUREE_ECHEC_MS };
      throw e;
    }
    cache = { config, expire: t + DUREE_CACHE_MS };
    return config;
  };

  async function construire(doc: DocumentSnapshot, projet: string): Promise<ConfigApple | null> {
    let config: ConfigApple | null = null;
    if (doc.exists) {
      const keyId: unknown = doc.get("keyId");
      const issuerId: unknown = doc.get("issuerId");
      const appAppleId: unknown = doc.get("appAppleId");
      if (typeof keyId === "string" && FORME_KEY_ID.test(keyId) && typeof issuerId === "string" && FORME_ISSUER.test(issuerId)) {
        const cle = await lireSecret(projet);
        if (cle !== null && FORME_PEM.test(cle)) {
          config = { cle, keyId, issuerId, appAppleId: typeof appAppleId === "number" && Number.isSafeInteger(appAppleId) && appAppleId > 0 ? appAppleId : null };
        }
      }
    }
    return config;
  }
}
