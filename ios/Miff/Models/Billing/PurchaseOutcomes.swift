import Foundation

/// Comment UNE tentative d'achat s'est terminée, telle que l'écran d'offre la raconte (ticket `docs/tickets/premium-achat-reel.md` §6, §7 et critères
/// AN2 à AN5, AN9, AN12). Une valeur pure : aucun objet du store, aucun jeton, rien de personnel.
enum PurchaseOutcome: Equatable {
    /// Le serveur a vérifié l'achat et Miff+ est allumé pour ce compte.
    case granted
    /// Le store dit que le paiement n'est pas final (« Ask to Buy », paiement différé) : RIEN n'est accordé tant qu'il n'est pas confirmé.
    case pending
    /// La personne a fermé la feuille du store : aucun message.
    case cancelled
    /// L'achat appartient déjà à un AUTRE compte Miff : refus calme, rien n'est dit de l'autre compte (décision Q6 : un achat = un compte).
    case otherAccount
    /// Le produit (ou le store) n'est pas disponible ici : l'offre montre son état calme « pas disponible ».
    case unavailable
    /// Pas de connexion, ou un service qui ne répond pas : rien n'est perdu, un achat payé est revérifié à la prochaine ouverture.
    case network
    /// L'abonnement existe mais le store l'a suspendu (paiement qui échoue) : l'offre le dit et renvoie à la page du store.
    case onHold
    /// Tout le reste.
    case failed
}

/// Comment un « Restaurer mes achats » s'est terminé.
enum RestoreOutcome: Equatable {
    /// Un abonnement Miff+ existe pour ce compte (retrouvé, ou déjà connu du serveur).
    case found
    /// Rien sur ce compte du store.
    case noneFound
    /// Le seul abonnement trouvé est utilisé par un autre compte Miff.
    case otherAccount
    /// Un paiement de ce compte du store attend encore sa confirmation.
    case pending
    /// Le store ne liste rien mais le serveur connaît un abonnement SUSPENDU.
    case onHold
    /// La personne a refusé l'identification Apple : aucun message.
    case cancelled
    case network
    case failed
}

/// Ce que le store répond pour les offres : les offres choisies, « pas disponible », ou une erreur qui mérite un second essai.
enum OffersResult: Equatable {
    case loaded(PaywallOffers)
    /// Le produit n'existe pas pour ce compte ou cet appareil (pas publié, pays, restriction) : ni prix ni bouton, une phrase calme.
    case notAvailable
    /// Pas de connexion, ou le store n'a pas répondu à temps.
    case error
}

/// Ce que montrent les écrans du travail en cours (un achat ou une restauration).
enum PurchaseSession: Equatable {
    /// Rien en cours, rien à raconter.
    case idle
    /// La feuille du store est ouverte, ou le serveur vérifie : les boutons dorment et l'écran dit « Un instant… ».
    case working(restoring: Bool)
    /// Le dernier travail est fini. Il RESTE jusqu'au suivant : chaque écran vivant peut le raconter une fois (il garde le `serial` déjà raconté),
    /// pour que deux écrans ne se le prennent jamais. `outcome` pour un achat, `restore` pour une restauration (exactement un des deux).
    case finished(serial: Int, outcome: PurchaseOutcome?, restore: RestoreOutcome?)
}
