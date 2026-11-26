import Foundation

/// Une ligne de texte de l'écran de l'abonnement : la phrase, et si elle est petite (grise) ou séparée de la précédente.
struct SubscriptionLine: Equatable {
    let text: String
    let small: Bool
    let gap: Bool

    init(_ text: String, small: Bool = false, gap: Bool = false) {
        self.text = text
        self.small = small
        self.gap = gap
    }
}

/// Les phrases de Réglages > Abonnement (ticket `docs/tickets/premium-achat-reel.md` §8). Une phrase par état, la date au format long de la langue (formateur du système), et
/// JAMAIS un prix : voir `SubscriptionViewModel`. Le ton : ce qui se passe, ce qui reste vrai, la prochaine étape ; jamais de rouge, d'alarme ni de culpabilisation.
enum SubscriptionTexts {
    static func lines(
        for status: SubscriptionStatus,
        locale: Locale = AppLanguage.locale,
        zone: TimeZone = .current
    ) -> [SubscriptionLine] {
        switch status {
        case .free:
            return [SubscriptionLine(L10n.string("settings_subscription_free"))]
        case .manual(let expiresAtMillis):
            let until = SubscriptionDates.long(millis: expiresAtMillis, locale: locale, zone: zone)
            return [
                SubscriptionLine(L10n.format("settings_subscription_active_until", until)),
                SubscriptionLine(L10n.string("settings_subscription_source_manual"), small: true),
                SubscriptionLine(L10n.string("settings_subscription_no_store_note"), small: true, gap: true)
            ]
        case .store(let subscription):
            return storeLines(subscription, locale: locale, zone: zone)
        }
    }

    private static func storeLines(
        _ subscription: StoreSubscription,
        locale: Locale,
        zone: TimeZone
    ) -> [SubscriptionLine] {
        let date = subscription.expiresAtMillis.map { SubscriptionDates.long(millis: $0, locale: locale, zone: zone) }
        var lines = stateLines(subscription, date: date)
        // Un abonnement souscrit sur un téléphone Android se gère là-bas : dit, avec le lien du bouton « Gérer mon abonnement ».
        if subscription.source == .googlePlay {
            lines.append(SubscriptionLine(L10n.string("ios_settings_subscription_other_store"), small: true, gap: true))
        }
        // Un achat de test (bac à sable) : dit sans détour, pour que personne ne le prenne pour un vrai paiement.
        if subscription.test {
            lines.append(SubscriptionLine(L10n.string("settings_subscription_test_note"), small: true, gap: true))
        }
        return lines
    }

    private static func stateLines(_ subscription: StoreSubscription, date: String?) -> [SubscriptionLine] {
        switch subscription.state {
        case .trial:
            return date.map { [SubscriptionLine(L10n.format("settings_subscription_trial_noprice", $0))] } ?? []
        case .active:
            return activeLines(subscription, date: date)
        case .canceled:
            return date.map { [SubscriptionLine(L10n.format("settings_subscription_ends", $0))] } ?? []
        case .grace:
            return date.map { [SubscriptionLine(L10n.format("settings_subscription_grace", $0))] } ?? []
        case .onHold:
            return [SubscriptionLine(L10n.string("settings_subscription_on_hold"))]
        case .expired:
            return [SubscriptionLine(L10n.string("settings_subscription_expired"))]
        case .revoked:
            return [SubscriptionLine(L10n.string("settings_subscription_revoked"))]
        }
    }

    /// Actif : « Prochain renouvellement le {date} » (sans prix) ; une origine inconnue garde l'ancien libellé neutre « Actif jusqu'au {date} ».
    private static func activeLines(_ subscription: StoreSubscription, date: String?) -> [SubscriptionLine] {
        guard let date else {
            return []
        }
        if subscription.source == .other {
            return [
                SubscriptionLine(L10n.format("settings_subscription_active_until", date)),
                SubscriptionLine(L10n.string("settings_subscription_source_other"), small: true)
            ]
        }
        return [SubscriptionLine(L10n.format("settings_subscription_renews_noprice", date))]
    }

    /// La phrase sur la dernière restauration : celles de l'offre sont reprises (les mêmes mots partout).
    static func text(for notice: SubscriptionNotice) -> String {
        switch notice {
        case .nothingFound:
            return L10n.format("paywall_restore_none", L10n.string("ios_paywall_store_name"))
        case .otherAccount:
            return L10n.format("paywall_other_account", AppLinks.supportEmail)
        case .pending:
            return L10n.string("paywall_pending")
        case .refreshFailed:
            return L10n.string("settings_subscription_refresh_failed")
        }
    }
}
