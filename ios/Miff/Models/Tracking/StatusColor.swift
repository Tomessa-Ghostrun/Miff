import Foundation

// Fonction pure de la couleur d'un membre (ticket Lieux §6.9, critères STA6, STA9). Rien n'est lu, rien n'est écrit : la couleur est
// calculée par le téléphone de celui qui REGARDE, avec l'heure du moment, et n'est JAMAIS stockée dans Firestore. Le Feed (ticket
// suivant) l'appelle ; la même fonction et les mêmes deux constantes sont écrites à l'identique sur Android (les cas des tests sont le
// contrat).

enum StatusLevel: Equatable {
    case neutral
    case green
    case orange
    case red
}

/// Les deux seuils du ticket Statut, en UN seul endroit (critère STA9) : le Feed ne doit jamais réécrire 1 h ni 2 h.
enum StatusThresholds {
    static let orangeAfterMillis = 60 * 60 * 1000
    static let redAfterMillis = 2 * 60 * 60 * 1000
}

/// Ce dont la couleur a besoin dans le document d'un membre, rien d'autre. Heures en millisecondes depuis 1970 ; nil quand le champ
/// manque.
struct MemberStatusFacts: Equatable, CustomStringConvertible {
    let sharingEnabled: Bool
    let presence: PresenceValue?
    let presenceAtMillis: Int?
    let lastUpdateAtMillis: Int?
    /// MODE VOYAGE : la fin du voyage quand `presence` est `travel` (la couleur n'en a pas besoin : un voyage est toujours neutre ; le Feed si).
    var travelUntilMillis: Int?

    var description: String {
        "MemberStatusFacts(redacted)"
    }
}

enum StatusColor {
    /// L'ordre du ticket : partage arrêté ou statut absent = neutre ; zone de confiance = vert ; sinon zone de danger = rouge ;
    /// `unknown` = neutre (une personne jamais passée en zone de confiance, décision N4) ; sinon l'ancienneté du repère = le plus
    /// récent du départ et de la dernière update (décision N2) : moins d'1 h vert, de 1 h à 2 h INCLUSE orange, plus de 2 h rouge.
    /// Un repère dans le futur (téléphone à l'heure fausse) compte comme « maintenant » : vert.
    static func level(_ facts: MemberStatusFacts?, nowMillis: Int) -> StatusLevel {
        guard let facts, facts.sharingEnabled, let presence = facts.presence else {
            return .neutral
        }
        switch presence {
        case .inTrusted:
            return .green
        case .inRisk:
            return .red
        case .unknown:
            return .neutral
        case .travel:
            // MODE VOYAGE : un voyageur est ignoré dans la couleur de la Miff, avant ET après la fin du voyage (après, la carte dit « Voyage
            // terminé » jusqu'à la première mesure : jamais une couleur tirée d'un état qui n'est plus vrai).
            return .neutral
        case .out:
            let references = [facts.presenceAtMillis, facts.lastUpdateAtMillis].compactMap { $0 }
            guard let reference = references.max() else {
                return .neutral
            }
            let elapsed = nowMillis - reference
            if elapsed < StatusThresholds.orangeAfterMillis {
                return .green
            }
            return elapsed <= StatusThresholds.redAfterMillis ? .orange : .red
        }
    }
}
