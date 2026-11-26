import Foundation

/// Les états de l'écran d'offre (ticket `paywall-v2.md`, §5.2).
///
/// Dans un build où l'achat est éteint (`BuildFlavor.includesPremiumPurchase` faux : tout build distribué tant que le PM ne l'allume pas), l'écran n'est
/// JAMAIS que dans `comingSoon` : aucun prix, aucun bouton d'essai. Les autres états sont ceux de l'achat réel (SRV-8I) : `PaywallStoreDriver` les branche sur le coordinateur
/// de l'achat (le contenu d'un état ne dépend que de ce type, et les écrans se testent avec des états simulés).
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
    /// L'abonnement trouvé est déjà utilisé par un AUTRE compte Miff (un achat = un compte) : refus calme avec le contact, rien n'est dit de l'autre compte.
    case otherAccount(PaywallOffers)
    /// Le store a SUSPENDU l'abonnement de ce compte (paiement qui échoue) : dit comme tel, avec le lien vers la gestion du store ; jamais un second achat.
    case onHold(PaywallOffers)
    case subscribed

    /// Les offres connues, quand l'état en montre.
    var offers: PaywallOffers? {
        switch self {
        case .offers(let offers), .purchasing(let offers, _), .pending(let offers), .failed(let offers),
             .restoring(let offers), .restoreNone(let offers), .otherAccount(let offers), .onHold(let offers):
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
    /// Le store dit que le produit n'est pas disponible : l'offre redevient « pas disponible », sans prix ni bouton.
    case purchaseUnavailable
    case purchaseOtherAccount
    case purchaseOnHold
    case purchaseSucceeded
    case restoreStarted
    case restoreFound
    case restoreNothing
    case restoreFailed
    case restoreOtherAccount
    case restorePending
    case restoreOnHold
    /// La personne a refusé l'identification Apple d'une restauration : aucun message.
    case restoreCancelled
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
             (.restoreNone, .offersRequested), (.otherAccount, .offersRequested), (.onHold, .offersRequested):
            return .loading
        default:
            return self
        }
    }

    private func reducedByAction(_ event: PaywallEvent) -> PaywallState {
        switch (self, event) {
        case (.offers(let offers), .purchaseStarted(let plan)),
             (.failed(let offers), .purchaseStarted(let plan)),
             (.restoreNone(let offers), .purchaseStarted(let plan)),
             (.otherAccount(let offers), .purchaseStarted(let plan)):
            return .purchasing(offers, plan)
        case (.purchasing(let offers, _), .purchaseCancelled):
            return .offers(offers)
        case (.purchasing(let offers, _), .purchasePending):
            return .pending(offers)
        case (.purchasing(let offers, _), .purchaseFailed):
            return .failed(offers)
        case (.purchasing, .purchaseUnavailable):
            return .unavailable
        case (.purchasing(let offers, _), .purchaseOtherAccount):
            return .otherAccount(offers)
        case (.purchasing(let offers, _), .purchaseOnHold):
            return .onHold(offers)
        default:
            return reducedByRestoration(event)
        }
    }

    private func reducedByRestoration(_ event: PaywallEvent) -> PaywallState {
        switch (self, event) {
        case (.offers(let offers), .restoreStarted),
             (.failed(let offers), .restoreStarted),
             (.restoreNone(let offers), .restoreStarted),
             (.otherAccount(let offers), .restoreStarted):
            return .restoring(offers)
        case (.restoring, .restoreFound):
            return .subscribed
        case (.restoring(let offers), .restoreNothing):
            return .restoreNone(offers)
        case (.restoring(let offers), .restoreFailed):
            return .failed(offers)
        case (.restoring(let offers), .restoreOtherAccount):
            return .otherAccount(offers)
        case (.restoring(let offers), .restorePending):
            return .pending(offers)
        case (.restoring(let offers), .restoreOnHold):
            return .onHold(offers)
        case (.restoring(let offers), .restoreCancelled):
            return .offers(offers)
        default:
            return self
        }
    }
}
