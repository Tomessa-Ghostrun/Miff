import Foundation

/// Les identifiants publics de l'abonnement Miff+ dans App Store Connect (ticket `docs/tickets/premium-achat-reel.md`, Q7, décidés le 2026-11-12 :
/// DÉFINITIFS une fois créés, donc de simples constantes) : un groupe d'abonnements, deux produits. Ce sont des identifiants de produit, pas de
/// personne, et pas des secrets.
enum MiffPlusProducts {
    static let annualId = "com.tomessa.miff.plus.annual"
    static let monthlyId = "com.tomessa.miff.plus.monthly"
    static let all: Set<String> = [annualId, monthlyId]

    /// L'essai gratuit de l'annuel dure 7 jours : le store peut l'écrire « 7 jours » ou « 1 semaine ».
    static let trialDays = 7

    static func productId(for plan: PaywallPlan) -> String {
        switch plan {
        case .annual:
            return annualId
        case .monthly:
            return monthlyId
        }
    }

    /// La page d'Apple où la personne résilie, reprend ou change son abonnement (le moyen facile de résilier que les stores exigent).
    static let appStoreManageURL = URL(string: "https://apps.apple.com/account/subscriptions")

    /// La page de Google Play, pour un abonnement souscrit sur un téléphone Android.
    static let googlePlayManageURL = URL(string: "https://play.google.com/store/account/subscriptions")

    /// Où la personne gère son abonnement d'après le store où il a été souscrit. Rien pour un accès accordé à la main ou d'origine inconnue.
    static func manageURL(for source: SubscriptionSource) -> URL? {
        switch source {
        case .appStore:
            return appStoreManageURL
        case .googlePlay:
            return googlePlayManageURL
        case .manual, .other:
            return nil
        }
    }
}

/// Une durée de store : une unité et un nombre.
struct StorePeriod: Equatable {
    enum Unit: Equatable {
        case day
        case week
        case month
        case year
    }

    let unit: Unit
    let value: Int

    static let oneYear = StorePeriod(unit: .year, value: 1)
    static let oneMonth = StorePeriod(unit: .month, value: 1)

    /// Sept jours, écrits « 7 jours » ou « 1 semaine » par le store.
    var isSevenDays: Bool {
        (unit == .day && value == MiffPlusProducts.trialDays) || (unit == .week && value == 1)
    }
}

/// Ce que le store dit d'un produit de Miff+, copié de StoreKit par la passerelle : une valeur pure, pour que le choix des offres se teste sans StoreKit.
/// RIEN n'est écrit à la main : ni prix, ni devise, ni durée.
struct StoreProductInfo: Equatable {
    let id: String
    let amount: Decimal
    let currencyCode: String
    /// Le prix tel que le store l'écrit (devise et pays du compte du store).
    let displayPrice: String
    /// Le douzième du prix d'un produit annuel, écrit par le store dans la même devise (nil pour un autre produit).
    let monthlyDisplayPrice: String?
    let period: StorePeriod
    /// La durée de l'offre d'introduction GRATUITE du produit, s'il y en a une.
    let freeTrial: StorePeriod?
    /// Ce compte du store a droit à l'offre d'introduction (Apple tient seul le compte : un essai par groupe d'abonnements).
    let eligibleForTrial: Bool
}

/// Choisit, parmi les produits du store, ce que l'écran d'offre montre. RIEN n'est inventé : un prix manquant, un montant nul, une devise inconnue ou une période qui n'est pas
/// celle promise (un an, un mois) veut dire « pas disponible » (nil) : l'écran le dit calmement, sans prix ni bouton d'achat. L'essai n'existe que sur l'annuel, et seulement
/// quand le store l'offre à CE compte.
enum MiffPlusOfferChoice {
    static func offers(from products: [StoreProductInfo]) -> PaywallOffers? {
        guard let annual = valid(products, id: MiffPlusProducts.annualId, period: .oneYear),
              let monthly = valid(products, id: MiffPlusProducts.monthlyId, period: .oneMonth) else {
            return nil
        }
        let trial = annual.eligibleForTrial && (annual.freeTrial?.isSevenDays == true)
        return PaywallOffers(annual: price(of: annual), monthly: price(of: monthly), trialEligible: trial)
    }

    private static func valid(_ products: [StoreProductInfo], id: String, period: StorePeriod) -> StoreProductInfo? {
        guard let product = products.first(where: { $0.id == id }),
              product.period == period,
              product.amount > 0,
              !product.displayPrice.isEmpty,
              !product.currencyCode.isEmpty else {
            return nil
        }
        return product
    }

    private static func price(of product: StoreProductInfo) -> PaywallPrice {
        PaywallPrice(
            amount: product.amount,
            currencyCode: product.currencyCode,
            displayText: product.displayPrice,
            monthlyDisplayText: product.monthlyDisplayPrice
        )
    }
}
