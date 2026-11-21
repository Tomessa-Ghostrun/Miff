// Client Google Play Developer API (SRV-7) : lecture de l'etat d'un abonnement (`purchases.subscriptionsv2.get`, « source de verite » de l'etat selon Google) et
// accuse de reception (`purchases.subscriptions.acknowledge`, toujours en usage selon la documentation lue le 2026-11-21). Aucune bibliotheque `googleapis` : deux
// appels REST (moins de code, moins de dependances).
// DECISION (ecart avec le ticket §6.5, motive dans docs/tickets/premium-achat-reel.md) : le serveur NE REVOQUE NI NE REMBOURSE JAMAIS un achat
// (`subscriptionsv2.revoke` n'est pas appele) : un achat refuse n'est simplement pas acquitte, et Google le rembourse seul au bout de 3 jours.
import { ErreurAchat } from "./achatErreurs.js";
import { jetonGoogleValide, PACKAGE_GOOGLE, PRODUIT_GOOGLE, type ReponseGoogle } from "./achatRegles.js";
import { creerFournisseurJetons, PORTEE_PLAY, type FournisseurJetons } from "./jetonsGoogle.js";

const BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";
const DELAI_APPEL_MS = 10_000;

export interface ClientGoogle {
  /** Etat d'un abonnement. Leve `ErreurAchat` (`achat_inconnu` definitive ; `acces_refuse`, `indisponible` ; `reponse_invalide`). */
  lire(jeton: string): Promise<ReponseGoogle>;
  /** Accuse de reception d'un achat. Leve `ErreurAchat("indisponible", true)` si une nouvelle tentative a un sens ; une erreur definitive du store est absorbee (deja acquitte). */
  accuser(jeton: string): Promise<void>;
}

export interface OptionsClientGoogle {
  fetch?: typeof fetch;
  jetons?: FournisseurJetons;
}

export function creerClientGoogle(options: OptionsClientGoogle = {}): ClientGoogle {
  const appeler = options.fetch ?? fetch;
  const jetons = options.jetons ?? creerFournisseurJetons();
  let repli = false;

  async function requete(methode: "GET" | "POST", url: string, impersonation: boolean): Promise<Response> {
    let jeton: string;
    try {
      jeton = await jetons.jeton(PORTEE_PLAY, impersonation);
    } catch {
      throw new ErreurAchat(impersonation ? "acces_refuse" : "indisponible", !impersonation);
    }
    try {
      return await appeler(url, {
        method: methode,
        headers: { Authorization: `Bearer ${jeton}`, ...(methode === "POST" ? { "Content-Type": "application/json" } : {}) },
        ...(methode === "POST" ? { body: "{}" } : {}),
        signal: AbortSignal.timeout(DELAI_APPEL_MS),
      });
    } catch {
      throw new ErreurAchat("indisponible", true);
    }
  }

  /** Chemin direct, puis repli par usurpation d'identite de soi-meme si Google refuse la portee (401/403). */
  async function appel(methode: "GET" | "POST", url: string): Promise<Response> {
    let reponse = await requete(methode, url, repli);
    if (!repli && (reponse.status === 401 || reponse.status === 403)) {
      const reessai = await requete(methode, url, true);
      if (reessai.status !== 401 && reessai.status !== 403) repli = true;
      reponse = reessai;
    }
    return reponse;
  }

  return {
    async lire(jeton) {
      if (!jetonGoogleValide(jeton)) throw new ErreurAchat("jeton_invalide");
      const reponse = await appel("GET", `${BASE}/${PACKAGE_GOOGLE}/purchases/subscriptionsv2/tokens/${encodeURIComponent(jeton)}`);
      if (reponse.status === 400 || reponse.status === 404 || reponse.status === 410) throw new ErreurAchat("achat_inconnu");
      if (reponse.status === 401 || reponse.status === 403) throw new ErreurAchat("acces_refuse");
      if (reponse.status === 429 || reponse.status >= 500) throw new ErreurAchat("indisponible", true);
      if (!reponse.ok) throw new ErreurAchat("reponse_invalide");
      let corps: unknown;
      try {
        corps = await reponse.json();
      } catch {
        throw new ErreurAchat("reponse_invalide");
      }
      return lireReponseGoogle(corps);
    },
    async accuser(jeton) {
      if (!jetonGoogleValide(jeton)) throw new ErreurAchat("jeton_invalide");
      const reponse = await appel("POST", `${BASE}/${PACKAGE_GOOGLE}/purchases/subscriptions/${PRODUIT_GOOGLE}/tokens/${encodeURIComponent(jeton)}:acknowledge`);
      if (reponse.ok) return;
      // 400 = deja acquitte (par une autre execution) : rien a refaire. 401/403 : acces refuse, l'achat n'est PAS acquitte : on le dit (il le sera a la prochaine lecture).
      if (reponse.status === 429 || reponse.status >= 500) throw new ErreurAchat("indisponible", true);
      if (reponse.status === 401 || reponse.status === 403) throw new ErreurAchat("acces_refuse");
    },
  };
}

const texte = (v: unknown): string | undefined => (typeof v === "string" ? v : undefined);
const objet = (v: unknown): Record<string, unknown> | undefined => (typeof v === "object" && v !== null && !Array.isArray(v) ? (v as Record<string, unknown>) : undefined);

/** Ne garde QUE les champs utilises, avec leur type verifie : tout le reste de la reponse est ignore (jamais journalise, jamais conserve). */
export function lireReponseGoogle(corps: unknown): ReponseGoogle {
  const r = objet(corps);
  if (!r) throw new ErreurAchat("reponse_invalide");
  const lignesBrutes = r["lineItems"];
  if (lignesBrutes !== undefined && !Array.isArray(lignesBrutes)) throw new ErreurAchat("reponse_invalide");
  const lineItems = ((lignesBrutes as unknown[] | undefined) ?? []).map((brut) => {
    const l = objet(brut);
    if (!l) throw new ErreurAchat("reponse_invalide");
    const plan = objet(l["autoRenewingPlan"]);
    const offre = objet(l["offerDetails"]);
    const phase = objet(l["offerPhase"]);
    const produit = texte(l["productId"]);
    const echeance = texte(l["expiryTime"]);
    const forfait = texte(offre?.["basePlanId"]);
    return {
      ...(produit !== undefined ? { productId: produit } : {}),
      ...(echeance !== undefined ? { expiryTime: echeance } : {}),
      ...(plan ? { autoRenewingPlan: { autoRenewEnabled: plan["autoRenewEnabled"] === true } } : {}),
      ...(forfait !== undefined ? { offerDetails: { basePlanId: forfait } } : {}),
      ...(phase && phase["freeTrial"] !== undefined ? { offerPhase: { freeTrial: true } } : {}),
    };
  });
  const comptes = objet(r["externalAccountIdentifiers"]);
  const identifiant = texte(comptes?.["obfuscatedExternalAccountId"]);
  const sortie: ReponseGoogle = { lineItems };
  const etat = texte(r["subscriptionState"]);
  if (etat !== undefined) sortie.subscriptionState = etat;
  if (identifiant !== undefined) sortie.externalAccountIdentifiers = { obfuscatedExternalAccountId: identifiant };
  if (r["testPurchase"] !== undefined && r["testPurchase"] !== null) sortie.testPurchase = true;
  const accuse = texte(r["acknowledgementState"]);
  if (accuse !== undefined) sortie.acknowledgementState = accuse;
  const lie = texte(r["linkedPurchaseToken"]);
  if (lie !== undefined && jetonGoogleValide(lie)) sortie.linkedPurchaseToken = lie;
  return sortie;
}
