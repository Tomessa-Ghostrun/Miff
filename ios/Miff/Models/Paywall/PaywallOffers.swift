import Foundation

/// Un prix tel que le store l'annonce : un montant et une devise. JAMAIS écrit à la main ni dans une traduction (décision du PM du
/// 2026-11-12 et ticket `paywall-v2.md`, E3) : il vient du store (StoreKit, ticket SRV-8I) ; en dehors de lui il n'existe que dans les tests,
/// les aperçus et le faux magasin des builds de test.
struct PaywallPrice: Equatable {
    let amount: Decimal
    let currencyCode: String
    /// Le texte du prix tel que le STORE l'écrit (devise et pays du compte du store, pas ceux du téléphone). Quand il est connu, c'est lui qui
    /// s'affiche ; sans lui (tests, aperçus), le prix est écrit par le formateur du système.
    let displayText: String?
    /// Le douzième d'un prix annuel, écrit par le store dans la même devise (« soit 4,99 € par mois »). Nil : il est calculé.
    let monthlyDisplayText: String?

    init(amount: Decimal, currencyCode: String, displayText: String? = nil, monthlyDisplayText: String? = nil) {
        self.amount = amount
        self.currencyCode = currencyCode
        self.displayText = displayText
        self.monthlyDisplayText = monthlyDisplayText
    }
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

/// L'écriture des prix : celle du store quand elle existe ; sinon la devise et le séparateur suivent la langue du téléphone (décision de langue du
/// projet), jamais une chaîne construite à la main.
enum PaywallPriceFormat {
    static func string(_ price: PaywallPrice, locale: Locale = .current) -> String {
        if let text = price.displayText, !text.isEmpty {
            return text
        }
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = locale
        formatter.currencyCode = price.currencyCode
        return formatter.string(from: price.amount as NSDecimalNumber) ?? "\(price.amount) \(price.currencyCode)"
    }

    /// « soit 4,99 € par mois » : le prix ANNUEL du store divisé par 12, arrondi au centime. Calculé, jamais écrit : un prix change
    /// selon le pays et le palier du store (ticket E3). Quand le store a écrit lui-même ce douzième, c'est son texte qui est repris.
    static func monthlyEquivalent(ofAnnual annual: PaywallPrice) -> PaywallPrice {
        var perMonth = annual.amount / 12
        var rounded = Decimal()
        NSDecimalRound(&rounded, &perMonth, 2, .plain)
        return PaywallPrice(amount: rounded, currencyCode: annual.currencyCode, displayText: annual.monthlyDisplayText)
    }
}

/// La pastille « presque 30 % d'économie » est écrite une fois (décision du PM du 2026-11-07), mais les prix viennent du store et peuvent
/// changer (autre pays, autre palier) : elle ne s'affiche que tant qu'elle reste vraie. L'économie est calculée avec les montants du store :
/// `1 - annuel / (12 x mensuel)`, dans la même devise. Sous 25 % la pastille est cachée (jamais une allégation publicitaire inexacte,
/// Apple 2.3.1(a)) ; le texte, lui, ne change pas. Même règle qu'Android (`PaywallSavings`).
enum PaywallSavings {
    static let minimumShown = 0.25

    static func saving(annual: PaywallPrice, monthly: PaywallPrice) -> Double? {
        guard annual.currencyCode == monthly.currencyCode, annual.amount > 0, monthly.amount > 0 else {
            return nil
        }
        let yearOfMonths = (monthly.amount * 12) as NSDecimalNumber
        let paid = annual.amount as NSDecimalNumber
        return 1.0 - paid.doubleValue / yearOfMonths.doubleValue
    }

    static func isWorthShowing(annual: PaywallPrice, monthly: PaywallPrice) -> Bool {
        (saving(annual: annual, monthly: monthly) ?? 0) >= minimumShown
    }
}
