import Foundation

/// L'identifiant d'achat ALÉATOIRE du serveur pour ce compte (un UUID), ou pourquoi on ne l'a pas eu.
enum PrepareResult: Equatable, CustomStringConvertible {
    case ready(purchaseId: String)
    /// Pas de connexion, ou le service n'a pas répondu : rien n'a été lancé, la personne peut réessayer.
    case unavailable
    /// Le serveur refuse (pas connecté, App Check) : rien à réessayer en boucle.
    case refused

    var description: String {
        switch self {
        case .ready:
            return "ready(redacted)"
        case .unavailable:
            return "unavailable"
        case .refused:
            return "refused"
        }
    }
}

/// La réponse du serveur à « vérifie cet achat et dis-moi ce qu'il vaut ».
enum PurchaseSyncResult: Equatable {
    /// Vérifié. `state` est l'état calculé par le serveur : trial, active, canceled, grace, on_hold, expired, revoked, ou pending (payé plus tard : RIEN d'accordé).
    case synced(state: String?)
    /// L'achat appartient à un AUTRE compte Miff (un achat = un compte). Rien n'est dit de cet autre compte.
    case alreadyLinked
    /// Une vérification par minute et par compte : redemandé trop tôt.
    case rateLimited
    /// Le serveur n'accepte pas cet achat (produit inconnu, autre application, partage en famille, texte illisible) : réessayer ne changerait rien.
    case rejected
    /// Pas de connexion, service momentanément en panne, ou appel refusé par App Check : l'achat est revérifié plus tard.
    case unavailable
}

/// Les fonctions du serveur de Miff qui traitent les achats (`backend/functions/src/preparerAchat.ts`, `synchroniserAchat.ts`, `supprimerDonneesAbonnement.ts`).
protocol PurchaseBackend: AnyObject {
    func preparePurchaseId() async -> PrepareResult

    func sync(signedTransaction: String) async -> PurchaseSyncResult

    /// À la suppression du compte, AVANT que le compte d'authentification parte (la fonction exige la connexion) : le serveur supprime l'identifiant d'achat et les liens.
    /// Il ne résilie PAS l'abonnement chez le store. Un échec est TOLÉRÉ par l'appelant (ce qui reste est purgé par les règles d'expiration) : rend faux, ne lève jamais.
    func purgeSubscriptionData() async -> Bool
}

/// Les trois choses auxquelles l'achat de Miff+ parle : le store, le serveur de Miff, et l'endroit où se lisent les droits.
@MainActor
struct BillingStack {
    let gateway: BillingGateway
    let backend: PurchaseBackend
    let entitlements: SubscriptionStore
}
