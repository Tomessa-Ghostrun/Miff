import Foundation

/// Ce que dit la bulle d'incitation : rien, « Tu es passé en orange… » ou « Tu es passé en rouge… » (les textes des maquettes du PM, D21).
enum PromptLevel: Equatable {
    case hidden
    case orange
    case red
}

/// Ce que l'Update sait de MON statut dans la Miff affichée : seulement ce dont la couleur, la bulle et le nettoyage de la photo ont besoin.
struct MyStatusFacts: Equatable, CustomStringConvertible {
    let presence: PresenceValue?
    let presenceAtMillis: Int?
    let lastUpdateAtMillis: Int?
    let hasPhoto: Bool

    var description: String {
        "MyStatusFacts(redacted)"
    }
}

// Quand la bulle d'incitation au-dessus de la barre d'onglets s'affiche, et de quelle couleur est le bouton central (ticket Update, D18,
// D19, D21, critères A1, A2, B1) : une fonction pure de MON statut dans la Miff affichée et de l'horloge. Elle réutilise
// `StatusColor.level` (UN seul calcul de la couleur pour toute l'app) et ajoute la seule règle qui lui est propre : en zone de danger la
// couleur reste rouge après une update (une update ne ramène jamais au vert là, D8), donc la bulle disparaît quand une update a été
// publiée APRÈS l'arrivée (`lastUpdateAt >= presenceAt`, C8), pas quand la couleur change.
enum UpdatePrompt {
    /// Le niveau de mon statut (la couleur du bouton central, de l'anneau de ma photo) : `StatusColor.level` inchangé.
    static func level(_ status: MyStatusFacts?, sharing: Bool, nowMillis: Int) -> StatusLevel {
        guard let status, sharing else {
            return .neutral
        }
        let facts = MemberStatusFacts(
            sharingEnabled: sharing,
            presence: status.presence,
            presenceAtMillis: status.presenceAtMillis,
            lastUpdateAtMillis: status.lastUpdateAtMillis
        )
        return StatusColor.level(facts, nowMillis: nowMillis)
    }

    static func prompt(_ status: MyStatusFacts?, sharing: Bool, nowMillis: Int) -> PromptLevel {
        guard let status else {
            return .hidden
        }
        switch level(status, sharing: sharing, nowMillis: nowMillis) {
        case .orange:
            return .orange
        case .red:
            // Zone de danger : la bulle disparaît quand une update suit l'arrivée.
            if status.presence == .inRisk && updatedSinceArrival(status) {
                return .hidden
            }
            return .red
        case .green, .neutral:
            // Vert ou neutre : rien à encourager (arriver dans une zone de confiance sans publier met aussi fin à la bulle, D18).
            return .hidden
        }
    }

    private static func updatedSinceArrival(_ status: MyStatusFacts) -> Bool {
        guard let update = status.lastUpdateAtMillis else {
            return false
        }
        guard let arrival = status.presenceAtMillis else {
            return true
        }
        return update >= arrival
    }

    /// La bulle est annoncée au lecteur d'écran une fois à son apparition et au passage de l'orange au rouge (D34), jamais à chaque minute.
    static func shouldAnnounce(previous: PromptLevel, current: PromptLevel) -> Bool {
        current != .hidden && current != previous
    }
}
