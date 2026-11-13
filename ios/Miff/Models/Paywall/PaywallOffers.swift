import Foundation

/// Un prix tel que le store l'annonce : un montant et une devise. JAMAIS écrit à la main ni dans une traduction (décision du PM du
/// 2026-11-12 et ticket `paywall-v2.md`, E3) : il viendra du store (StoreKit, ticket SRV-8I) ; ici il n'existe que dans les tests
/// et les aperçus.
struct PaywallPrice: Equatable {
    let amount: Decimal
    let currencyCode: String
}

/// Ce que le store propose à CE compte : les deux formules et le droit à l'essai (le store décide, Miff n'ajoute aucun blocage).
struct PaywallOffers: Equatable {
    let annual: PaywallPrice
    let monthly: PaywallPrice
    /// L'essai de 7 jours de l'annuel est proposé à ce compte du store. Sinon (déjà utilisé, ou autre raison) on ne l'affiche pas.
    let trialEligible: Bool
}

enum PaywallPlan: Equatable {
    case annual
    case monthly
}

/// L'écriture des prix : la devise et le séparateur suivent la langue du téléphone (décision de langue du projet), jamais une
/// chaîne construite à la main.
enum PaywallPriceFormat {
    static func string(_ price: PaywallPrice, locale: Locale = .current) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = locale
        formatter.currencyCode = price.currencyCode
        return formatter.string(from: price.amount as NSDecimalNumber) ?? "\(price.amount) \(price.currencyCode)"
    }

    /// « soit 4,99 € par mois » : le prix ANNUEL du store divisé par 12, arrondi au centime. Calculé, jamais écrit : un prix change
    /// selon le pays et le palier du store (ticket E3).
    static func monthlyEquivalent(ofAnnual annual: PaywallPrice) -> PaywallPrice {
        var perMonth = annual.amount / 12
        var rounded = Decimal()
        NSDecimalRound(&rounded, &perMonth, 2, .plain)
        return PaywallPrice(amount: rounded, currencyCode: annual.currencyCode)
    }
}
