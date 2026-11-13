import Foundation

/// Les états de l'écran d'offre (ticket `paywall-v2.md`, §5.2).
///
/// En production, tant que l'achat réel n'existe pas (SRV-7 et SRV-8 non livrés, le Miff+ est accordé à la main), l'écran n'est
/// JAMAIS que dans `comingSoon` : aucun prix, aucun bouton d'essai. Les autres états sont livrés ici et testés par des états
/// simulés ; SRV-8I les branchera sur le store (le contenu d'un état ne dépend que de ce type).
enum PaywallState: Equatable {
    /// Variante « sans achat » : « Miff+ arrive bientôt ».
    case comingSoon
    case loading
    case unavailable
    case offers(PaywallOffers)
    case purchasing(PaywallOffers, PaywallPlan)
    /// Paiement en attente de validation du store : aucun droit accordé.
    case pending(PaywallOffers)
    /// Erreur calme : l'achat ou sa vérification n'a pas abouti.
    case failed(PaywallOffers)
    case restoring(PaywallOffers)
    case restoreNone(PaywallOffers)
    case subscribed

    /// Les offres connues, quand l'état en montre.
    var offers: PaywallOffers? {
        switch self {
        case .offers(let offers), .purchasing(let offers, _), .pending(let offers), .failed(let offers),
             .restoring(let offers), .restoreNone(let offers):
            return offers
        case .comingSoon, .loading, .unavailable, .subscribed:
            return nil
        }
    }

    /// Une opération est en cours ou en attente : les boutons d'achat sont inactifs (aucun double achat).
    var isBusy: Bool {
        switch self {
        case .purchasing, .restoring, .pending:
            return true
        default:
            return false
        }
    }

    /// Le bloc d'information obligatoire (renouvellement, annulation, liens, restauration) accompagne toute offre montrée.
    var showsInformation: Bool {
        offers != nil || self == .loading || self == .unavailable
    }
}

/// Ce qui arrive à l'écran d'offre (réponses du store ; gestes de la personne).
enum PaywallEvent: Equatable {
    case offersRequested
    case offersLoaded(PaywallOffers)
    case offersFailed
    case loadingTimedOut
    case purchaseStarted(PaywallPlan)
    /// La personne a fermé la feuille du store : retour à l'offre, SANS message.
    case purchaseCancelled
    case purchasePending
    case purchaseFailed
    case purchaseSucceeded
    case restoreStarted
    case restoreFound
    case restoreNothing
    case restoreFailed
}

extension PaywallState {
    /// La transition d'un état à l'autre. Un événement qui n'a pas de sens dans l'état courant est ignoré (une réponse tardive, un
    /// double toucher) : l'état ne se corrompt jamais. `comingSoon` n'en connaît AUCUN : rien ne s'achète, rien ne se restaure.
    func reduced(by event: PaywallEvent) -> PaywallState {
        if self == .comingSoon || self == .subscribed {
            return self
        }
        if event == .purchaseSucceeded {
            // Y compris une course de timing (l'abonnement arrive pendant que l'offre charge) : jamais d'offre à un abonné.
            return .subscribed
        }
        switch event {
        case .offersRequested, .offersLoaded, .offersFailed, .loadingTimedOut:
            return reducedByLoading(event)
        default:
            return reducedByAction(event)
        }
    }

    private func reducedByLoading(_ event: PaywallEvent) -> PaywallState {
        switch (self, event) {
        case (.loading, .offersLoaded(let offers)), (.unavailable, .offersLoaded(let offers)):
            return .offers(offers)
        case (.loading, .offersFailed), (.loading, .loadingTimedOut):
            return .unavailable
        case (.unavailable, .offersRequested), (.offers, .offersRequested), (.failed, .offersRequested),
             (.restoreNone, .offersRequested):
            return .loading
        default:
            return self
        }
    }

    private func reducedByAction(_ event: PaywallEvent) -> PaywallState {
        switch (self, event) {
        case (.offers(let offers), .purchaseStarted(let plan)),
             (.failed(let offers), .purchaseStarted(let plan)),
             (.restoreNone(let offers), .purchaseStarted(let plan)):
            return .purchasing(offers, plan)
        case (.purchasing(let offers, _), .purchaseCancelled):
            return .offers(offers)
        case (.purchasing(let offers, _), .purchasePending):
            return .pending(offers)
        case (.purchasing(let offers, _), .purchaseFailed):
            return .failed(offers)
        case (.offers(let offers), .restoreStarted),
             (.failed(let offers), .restoreStarted),
             (.restoreNone(let offers), .restoreStarted):
            return .restoring(offers)
        case (.restoring, .restoreFound):
            return .subscribed
        case (.restoring(let offers), .restoreNothing):
            return .restoreNone(offers)
        case (.restoring(let offers), .restoreFailed):
            return .failed(offers)
        default:
            return self
        }
    }
}
