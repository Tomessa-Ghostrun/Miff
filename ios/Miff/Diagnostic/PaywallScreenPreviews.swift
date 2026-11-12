#if DEBUG
import SwiftUI

/// L'écran d'offre montré SEUL avec un état fixe, pour les captures d'écran de la CI
/// (`xcrun simctl launch ... -MiffPreview paywall-trial`). Nom : `paywall-<état>[-danger|-miff][-cross]` ; états : `soon` (la variante
/// sans achat, la seule de la production), `trial`, `plans`, `loading`, `unavailable`, `purchasing`, `pending`, `failed`, `restore`,
/// `subscribed`. Les prix sont des données de démonstration (jamais ceux d'un store) ; aucun réseau, aucun achat.
/// Sans `-cross`, la croix est absente (délai de 1 heure : la capture montre l'écran « avant 3 secondes ») ; avec `-cross`, le délai est
/// de 0 (la croix est là, comme après 3 secondes). Compilé en configuration Debug SEULEMENT, comme `ScreenPreview`.
@MainActor
struct PaywallScreenPreview: View {
    let name: String

    @StateObject private var model: PaywallViewModel

    init(name: String) {
        self.name = name
        _model = StateObject(wrappedValue: PaywallScreenPreview.makeModel(name))
    }

    var body: some View {
        PaywallScreen(model: model, onClose: {})
    }

    private static let offers = PaywallOffers(
        annual: PaywallPrice(amount: Decimal(5988) / 100, currencyCode: "EUR"),
        monthly: PaywallPrice(amount: Decimal(699) / 100, currencyCode: "EUR"),
        trialEligible: true
    )

    private static let offersWithoutTrial = PaywallOffers(
        annual: offers.annual,
        monthly: offers.monthly,
        trialEligible: false
    )

    private static func makeModel(_ name: String) -> PaywallViewModel {
        let parts = name.components(separatedBy: "-").dropFirst()
        let reason: PaywallReason = parts.contains("danger") ? .danger : (parts.contains("miff") ? .extraMiff : .limit)
        let delay: TimeInterval = parts.contains("cross") ? 0 : 3600
        return PaywallViewModel(reason: reason, state: state(parts.first ?? "soon"), closeDelay: delay)
    }

    private static func state(_ key: String) -> PaywallState {
        switch key {
        case "trial":
            return .offers(offers)
        case "plans":
            return .offers(offersWithoutTrial)
        case "loading":
            return .loading
        case "unavailable":
            return .unavailable
        case "purchasing":
            return .purchasing(offers, .annual)
        case "pending":
            return .pending(offersWithoutTrial)
        case "failed":
            return .failed(offers)
        case "restore":
            return .restoreNone(offers)
        case "subscribed":
            return .subscribed
        default:
            return .comingSoon
        }
    }
}
#endif
