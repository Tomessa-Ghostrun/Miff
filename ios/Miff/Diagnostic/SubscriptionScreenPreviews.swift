#if DEBUG
import SwiftUI

/// Réglages > Abonnement seul, avec un état fixe, pour les captures de la CI (`-MiffPreview subscription-free`, `subscription-active`, `subscription-trial`, `subscription-canceled`,
/// `subscription-grace`, `subscription-hold`, `subscription-expired`). Le VRAI écran, nourri par le FAUX droit du build Debug (`FakeBillingControl.doc`) : aucun réseau, aucune
/// session, aucune écriture, rien n'est accordé. Compilé en configuration Debug SEULEMENT, comme `ScreenPreview`.
struct SubscriptionScreenPreview: View {
    @StateObject private var model: SubscriptionViewModel

    init(name: String) {
        FakeBillingControl.doc = Self.document(for: name)
        let stack = FakeBilling.stack()
        let repository = SubscriptionRepository(store: stack.entitlements, currentUid: { "preview" })
        let coordinator = PremiumCoordinator(
            gateway: stack.gateway, backend: stack.backend, subscriptions: repository, currentUid: { "preview" }
        )
        _model = StateObject(wrappedValue: SubscriptionViewModel(subscriptions: repository, premium: coordinator))
    }

    var body: some View {
        SubscriptionView(model: model, actions: SubscriptionActions(onBack: {}, onOffer: {}))
    }

    private static func document(for name: String) -> FakeBillingControl.Doc {
        switch name {
        case "subscription-active":
            return .active
        case "subscription-trial":
            return .trial
        case "subscription-canceled":
            return .canceled
        case "subscription-grace":
            return .grace
        case "subscription-hold":
            return .onHold
        case "subscription-expired":
            return .expired
        default:
            return .noDocument
        }
    }
}
#endif
