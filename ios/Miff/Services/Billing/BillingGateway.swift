import Foundation

// SÉCURITÉ, pour ce fichier et tout le dossier : une TRANSACTION SIGNÉE est la preuve d'un paiement. Elle va d'Apple au serveur de Miff (`synchroniserAchat`) et nulle
// part ailleurs : jamais journalisée, jamais dans un événement d'analyse, jamais dans un message d'erreur, jamais écrite sur le téléphone, jamais affichée (chaque type qui
// en tient une cache son contenu dans sa description). Le seul identifiant donné à Apple est l'identifiant d'achat ALÉATOIRE fait par le serveur (jamais l'identifiant du
// compte, l'e-mail ni aucune donnée personnelle). L'app n'accorde JAMAIS Miff+ : elle ne fait que présenter la preuve au serveur.

/// Une transaction de Miff+ telle que le store la liste. Le texte signé (JWS) n'est lisible que dans ce module.
final class OwnedTransaction: CustomStringConvertible {
    let id: UInt64
    let productId: String
    let signed: String
    private let finisher: () async -> Void

    init(id: UInt64, productId: String, signed: String, finish: @escaping () async -> Void) {
        self.id = id
        self.productId = productId
        self.signed = signed
        self.finisher = finish
    }

    /// « Termine » la transaction auprès du store : seulement une fois le serveur d'accord (sinon le store la re-présente au prochain lancement, c'est le filet).
    func finish() async {
        await finisher()
    }

    var description: String {
        "OwnedTransaction(redacted)"
    }
}

/// Le catalogue du store : les produits de Miff+ de ce compte, ou pourquoi il n'y en a pas.
enum CatalogResult {
    case loaded([StoreProductInfo])
    /// Pas de tel produit pour ce compte ou cet appareil (pas publié, pas d'App Store, non pris en charge).
    case notAvailable
    /// Le store n'a pas répondu (pas de connexion, service en panne).
    case error
}

enum OwnedResult {
    case loaded([OwnedTransaction])
    case error
}

/// Ce que dit le store à la fin de la feuille d'achat.
enum StorePurchaseResult {
    case purchased(OwnedTransaction)
    /// Paiement différé (« Ask to Buy ») : RIEN n'est accordé tant que l'abonnement n'est pas confirmé.
    case pending
    case cancelled
    /// Le compte du store possède déjà cet abonnement.
    case alreadyOwned
    case unavailable
    case network
    case failed
}

/// Ce que dit le store d'une synchronisation demandée par la personne (« Restaurer mes achats »).
enum StoreSyncResult {
    case done
    /// La personne a refusé l'identification Apple.
    case cancelled
    case network
    case failed
}

/// Tout ce dont l'app a besoin du store, derrière un protocole : le vrai est StoreKit 2 (`StoreKitBillingGateway`) ; les builds de test ont aussi un FAUX magasin
/// (`FakeBillingGateway`, sous `BILLING_FAKE`) pour essayer toute l'interface sans produit dans App Store Connect ; les tests ont leur propre double.
@MainActor
protocol BillingGateway: AnyObject {
    /// Les produits de Miff+ proposés à ce compte. Ne lève jamais.
    func loadCatalog() async -> CatalogResult

    /// Les abonnements de Miff+ que le store connaît pour ce compte, en cours de validité (un abonnement suspendu n'est pas listé). Ne lève jamais, et ne demande JAMAIS
    /// l'identification Apple.
    func ownedTransactions() async -> OwnedResult

    /// Ouvre la feuille d'achat du store pour ce produit, avec l'identifiant d'achat ALÉATOIRE du serveur comme seul identifiant donné au store. Ne lève jamais.
    func purchase(productId: String, purchaseId: String) async -> StorePurchaseResult

    /// « Restaurer mes achats » : synchronise avec le store (identification Apple). JAMAIS appelé tout seul, seulement sur le geste de la personne.
    func syncWithStore() async -> StoreSyncResult

    /// Écoute les transactions qui arrivent hors d'un achat en cours (autre appareil, approbation « Ask to Buy », code promotionnel, transaction restée non terminée) :
    /// à appeler TOUT AU DÉBUT du lancement, car les transactions non terminées ne sont livrées qu'une fois.
    func listen(_ handler: @escaping @MainActor (OwnedTransaction) -> Void)

    /// Ouvre la gestion des abonnements du système (résilier, reprendre, changer).
    func openManagement() async
}
