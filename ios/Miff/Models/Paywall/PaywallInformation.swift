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

/// L'information des stores sous l'offre, réduite à ce que le PM a demandé (retour du 2026-11-25, comme sur Android) : sur la vue principale de l'essai, UNE phrase (l'essai, le prix
/// facturé à sa fin, le renouvellement automatique chaque année) ; sur les vues sans essai (« Voir toutes les offres », ou un compte qui n'y a pas droit), une phrase par formule
/// montrée. Les prix sont ceux du store, jamais écrits dans une traduction. Plus de paragraphe « comment annuler » ni de phrase « la formule gratuite reste disponible » : l'annulation
/// se fait sur la page d'Apple, atteinte depuis Réglages > Abonnement (le PM l'a décidé ; risque assumé, voir le ticket §4.4).
enum PaywallInformation {
    static func paragraphs(offers: PaywallOffers, layout: PaywallLayout, locale: Locale = .current) -> [String] {
        let annual = PaywallPriceFormat.string(offers.annual, locale: locale)
        if layout == .trial {
            return [L10n.format("paywall_info_trial_short", annual)]
        }
        let monthly = PaywallPriceFormat.string(offers.monthly, locale: locale)
        return [L10n.format("paywall_info_annual", annual), L10n.format("paywall_info_monthly", monthly)]
    }
}
