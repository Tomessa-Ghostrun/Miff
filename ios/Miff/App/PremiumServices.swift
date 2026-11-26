import Foundation

/// L'achat de Miff+ (SRV-8I), construit d'un seul tenant. Rien n'est construit tant que l'achat est éteint dans ce build (`BuildFlavor.includesPremiumPurchase`) : aucune
/// connexion à l'App Store, aucun appel au serveur d'achat, aucun écouteur de transactions. Allumé, le coordinateur est créé TOUT DE SUITE au lancement (IO2) et son
/// écoute des transactions est en place avant le premier écran.
@MainActor
struct PremiumServices {
    /// Le coordinateur de l'achat (nil : l'achat est éteint, « Miff+ arrive bientôt »).
    let coordinator: PremiumCoordinator?
    /// La lecture de `entitlements/{uid}` (nil quand l'achat est éteint).
    let subscriptions: SubscriptionRepository?
    /// À la suppression du compte : le serveur supprime l'identifiant d'achat et les liens (nil quand l'achat est éteint). Un échec est toléré.
    let purgeSubscriptionData: (() async -> Bool)?

    static func make(currentUid: @escaping () -> String?) -> PremiumServices {
        guard let stack = BillingStacks.create() else {
            return PremiumServices(coordinator: nil, subscriptions: nil, purgeSubscriptionData: nil)
        }
        let subscriptions = SubscriptionRepository(store: stack.entitlements, currentUid: currentUid)
        let coordinator = PremiumCoordinator(
            gateway: stack.gateway,
            backend: stack.backend,
            subscriptions: subscriptions,
            currentUid: currentUid
        )
        coordinator.startListening()
        let backend = stack.backend
        return PremiumServices(
            coordinator: coordinator,
            subscriptions: subscriptions,
            purgeSubscriptionData: { await backend.purgeSubscriptionData() }
        )
    }
}

/// Choisit les trois choses auxquelles l'achat parle : le FAUX trio dans un build qui contient `BILLING_FAKE` (Debug, ou Internal sur demande), le VRAI (StoreKit, les
/// fonctions du serveur, `entitlements/{uid}`) quand seul `PREMIUM_PURCHASE` existe, RIEN sinon. Le faux n'est même pas compilé dans un build de publication.
enum BillingStacks {
    @MainActor
    static func create() -> BillingStack? {
        #if BILLING_FAKE
        return FakeBilling.stack()
        #elseif PREMIUM_PURCHASE
        return BillingStack(
            gateway: StoreKitBillingGateway(),
            backend: FirebasePurchaseBackend(),
            entitlements: FirestoreSubscriptionStore()
        )
        #else
        return nil
        #endif
    }
}
