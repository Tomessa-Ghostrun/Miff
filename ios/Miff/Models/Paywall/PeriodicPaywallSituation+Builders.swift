import Foundation

// Les faits de l'écran que l'offre périodique lit, en fonctions pures (testées sans écran). Rien de personnel n'y est gardé : des booléens.

extension PeriodicPaywallSituation {
    /// La personne a-t-elle Miff+ ? Seulement ce que le SERVEUR a dit (`entitlements/{uid}`, SRV-8I) : une formule inconnue (lecture échouée) ou venue du cache du téléphone
    /// donne nil, et nil ne montre jamais l'offre (une estimation n'est pas un droit).
    static func miffPlus(of state: DirectoryState) -> Bool? {
        guard case .ready(let ready) = state, ready.premiumKnown else {
            return nil
        }
        return ready.premium
    }

    /// Un message de l'app est à l'écran ou attend la personne : l'offre ne le recouvre jamais et ne le cache jamais. Mêmes cas que la fenêtre d'avis (accès perdu, accord en
    /// attente, feuille « Localiser » et pop-in « Pas de connexion », « Tu as arrêté de partager », « En attente de ton accord »).
    static func messageOpen(
        lostAccessNotice: Bool,
        awaitingAgreement: Bool,
        locateSheetOpen: Bool,
        myLabel: StateLabel?,
        connectionPopInOpen: Bool
    ) -> Bool {
        if lostAccessNotice || awaitingAgreement || locateSheetOpen || connectionPopInOpen {
            return true
        }
        return myLabel == .sharingStopped || myLabel == .waitingAgreement
    }

    /// La ligne « {Prénom} a déclaré une alerte » attend, non lue, dans le centre de notifications : tant qu'elle est là, l'offre ne vient pas (ticket D2).
    static func alertLineUnread(in items: [NotificationItem]) -> Bool {
        items.contains { $0.type == .n10 && $0.isUnread }
    }
}

/// Ce que le Feed dit à l'offre périodique de ce qu'il montre, lu au moment de décider (jamais gardé, jamais envoyé).
struct PeriodicFeedSnapshot: Equatable {
    /// La carte d'un proche porte une alerte active.
    var alertActive = false
    var myLabel: StateLabel?
    var locateSheetOpen = false
    var connectionPopInOpen = false

    /// Ce que dit un Feed prêt : l'alerte d'un proche, ma carte.
    static func of(content: FeedContent?) -> PeriodicFeedSnapshot {
        PeriodicFeedSnapshot(
            alertActive: content?.cards.contains { $0.alertActive } ?? false,
            myLabel: content?.myLabel
        )
    }
}
