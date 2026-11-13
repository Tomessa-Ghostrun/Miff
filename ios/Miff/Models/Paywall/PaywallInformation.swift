import Foundation

/// La disposition de l'offre : celle de la maquette « essai gratuit » (un bouton) ou celle de la maquette « annuel / sans
/// engagement » (deux boutons). Ticket `paywall-v2.md`, E7 : la première est l'état « éligible à l'essai », la seconde l'état « non
/// éligible » ; une personne éligible peut passer à la seconde par « Voir toutes les offres » (sinon elle ne pourrait jamais choisir
/// le mensuel).
enum PaywallLayout: Equatable {
    case trial
    case plans

    static func of(_ offers: PaywallOffers, showsAllOffers: Bool) -> PaywallLayout {
        offers.trialEligible && !showsAllOffers ? .trial : .plans
    }
}

/// Le bloc d'information OBLIGATOIRE des stores (ticket §5.4) : prix facturé, renouvellement automatique, annulation facile. Les
/// phrases sont complètes, une par cas ; les prix sont ceux du store, écrits par le formateur du système.
enum PaywallInformation {
    /// Les paragraphes sous l'offre, dans l'ordre. L'essai (et « au moins 24 heures avant sa fin », propre à iOS) n'est mentionné que si
    /// le store le propose à ce compte.
    static func paragraphs(
        offers: PaywallOffers,
        layout: PaywallLayout,
        storeName: String = L10n.string("ios_paywall_store_name"),
        locale: Locale = .current
    ) -> [String] {
        let annual = PaywallPriceFormat.string(offers.annual, locale: locale)
        let monthly = PaywallPriceFormat.string(offers.monthly, locale: locale)
        var result = [
            L10n.format(offers.trialEligible ? "paywall_info_trial" : "paywall_info_annual", annual)
        ]
        if layout == .plans {
            result.append(L10n.format("paywall_info_monthly", monthly))
        }
        var cancellation = L10n.format("paywall_info_cancel", storeName)
        if offers.trialEligible {
            cancellation += " " + L10n.string("ios_paywall_info_cancel_trial")
        }
        result.append(cancellation)
        return result
    }

    /// « La formule gratuite reste disponible : 1 Miff et N lieux de confiance. » N est la limite RÉELLEMENT appliquée par l'app
    /// (`PlaceLimits.freePlaces`), jamais un nombre écrit dans le texte : si la limite change, la phrase reste vraie.
    static func freePlanSentence(freePlaces: Int = PlaceLimits.freePlaces) -> String {
        L10n.format("paywall_free_plan_stays", freePlaces)
    }
}
