import Foundation

// Les instants où la couleur d'un membre PEUT changer (ticket Notifications §20.2). Une fonction pure, dans le même esprit que
// `StatusColor.level` et avec les MÊMES constantes (`StatusThresholds`, une seule source) : elle sert au planificateur de rappels, qui n'a
// jamais à recopier 2 h, 4 h ni 1 h. Elle doit rester identique sur Android (les cas des tests sont le contrat).
//
// Test de contrat (`ChangeInstantsTests`) : pour chaque instant `t` rendu, `level(t - 1 ms) != level(t)`, et le niveau est constant
// entre deux instants consécutifs.

extension StatusColor {
    /// Les instants (millisecondes depuis 1970), triés, où le niveau du membre change.
    /// - `out` : le repère (le plus récent du départ et de la dernière update) + 2 h + 1 ms (orange), puis + 4 h + 1 ms (rouge). Le
    ///   « + 1 ms » vient du « plus de » : à 2 h 00 pile la personne est encore verte.
    /// - `in_risk` (règle de `dangerZoneLevel`, décision du PM du 2026-11-08) : vert seulement avec une update STRICTEMENT postérieure à
    ///   l'entrée ; alors la dernière update + 1 h + 1 ms (rouge). Sans update, avec une update antérieure ou égale à l'entrée, ou sans
    ///   heure d'entrée, elle est rouge depuis toujours : aucun instant.
    /// - les autres états (zone de confiance, inconnu, voyage, partage arrêté, statut absent) : aucun instant.
    static func changeInstants(_ facts: MemberStatusFacts?) -> [Int] {
        guard let facts, facts.sharingEnabled, let presence = facts.presence else {
            return []
        }
        switch presence {
        case .out:
            let references = [facts.presenceAtMillis, facts.lastUpdateAtMillis].compactMap { $0 }
            guard let reference = references.max() else {
                return []
            }
            return [
                reference + StatusThresholds.orangeAfterMillis + 1,
                reference + StatusThresholds.redAfterMillis + 1
            ]
        case .inRisk:
            guard let entry = facts.presenceAtMillis, let lastUpdate = facts.lastUpdateAtMillis,
                  lastUpdate > entry else {
                return []
            }
            return [lastUpdate + StatusThresholds.dangerZoneRedAfterMillis + 1]
        case .inTrusted, .unknown, .travel:
            // Un voyageur est toujours neutre (mode voyage) : sa couleur ne change jamais avec le temps, aucun rappel à programmer.
            return []
        }
    }
}
