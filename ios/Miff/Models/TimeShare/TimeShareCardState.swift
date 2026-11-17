import Foundation

// Les états de la carte (ticket §7), du Swift pur : ce que la carte dit, d'après ce qui est connu. Rien n'est lu ici, rien n'est écrit.
// Priorité : masquée > erreur > suivi à l'arrêt sans données > pas assez de données > nominal (avec, le cas échéant, une ligne de pause).
// La couleur d'alerte n'apparaît JAMAIS dans un état vide, une erreur ou le bandeau : elle est réservée à la tranche et à la ligne
// « Zone de danger ».

/// Ce que le téléphone dit du suivi, MAINTENANT, pour la Miff regardée.
struct TimeShareConditions: Equatable {
    /// Un voyage est en cours : la répartition est en pause jusqu'au retour.
    var travelActive = false
    /// Je ne partage plus mon statut avec cette Miff (dit par le Feed ; inconnu pour une Miff que le Feed n'affiche pas : faux).
    var sharingStopped = false
    /// Pas « Toujours » avec la position précise, ou localisation éteinte.
    var locationInsufficient = false
    /// Plus de 20 zones surveillables, toutes Miffs confondues : « Ailleurs » peut être gonflé.
    var overWatchedLimit = false

    /// Le suivi est-il arrêté MAINTENANT ?
    var trackingStopped: Bool {
        travelActive || sharingStopped || locationInsufficient
    }
}

enum TimeShareCardState: Equatable, CustomStringConvertible {
    /// Rien n'est dessiné (pas même une marge) : aucun lieu dans la Miff, ou lieux pas encore lus.
    case hidden
    /// Le stockage local est illisible : une phrase calme et « Réessayer ».
    case unavailable
    case travel
    case sharingStopped
    case locationInsufficient
    /// Moins de 7 jours d'historique, suivi actif : l'anneau vide et le texte figé par le PM.
    case notEnough(daysLeft: Int)
    /// Assez de données ; `pause` : le suivi est arrêté maintenant (ligne calme sous l'anneau).
    case nominal(TimeShareResult, pause: Bool)

    var description: String {
        "TimeShareCardState(redacted)"
    }

    /// `places` : les lieux actuels de la Miff regardée, nil tant qu'ils ne sont pas lus ; `outcome` : le calcul, nil quand la lecture du
    /// journal a échoué.
    static func make(
        places: [TimeSharePlace]?,
        readFailed: Bool,
        outcome: TimeShareOutcome?,
        conditions: TimeShareConditions
    ) -> TimeShareCardState {
        guard let places, !places.isEmpty else {
            return .hidden
        }
        if readFailed {
            return .unavailable
        }
        switch outcome {
        case .none:
            return .hidden
        case .some(.ready(let result)):
            return .nominal(result, pause: conditions.trackingStopped)
        case .some(.notEnough(let daysLeft)):
            // Le texte « nous analysons ta situation » n'est dit QUE si le suivi est actif : sinon, la vraie raison (risque R6 du ticket).
            if conditions.travelActive {
                return .travel
            }
            if conditions.sharingStopped {
                return .sharingStopped
            }
            if conditions.locationInsufficient {
                return .locationInsufficient
            }
            return .notEnough(daysLeft: daysLeft)
        }
    }
}
