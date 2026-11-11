import Foundation

// CONFIDENTIALITÉ, pour ce fichier : un statut dit où est quelqu'un (un identifiant de lieu, jamais une coordonnée) et comment va
// son téléphone. Il vit en mémoire seulement ; `description` ne montre rien, pour qu'une ligne de journal ou un rapport de
// plantage ne puisse jamais le laisser fuiter.

/// Ce que le téléphone d'un membre dit de sa permission de localisation (`locationAccess` d'un statut).
enum MemberLocationAccess: String, Equatable {
    case background
    case foreground
    case denied
    case unknown

    static func fromWire(_ value: String?) -> MemberLocationAccess? {
        value.flatMap { MemberLocationAccess(rawValue: $0) }
    }
}

/// Les champs d'un statut tels que le serveur les décrit, AVANT toute vérification (une fonction à plus de 5 paramètres est refusée par
/// SwiftLint : ils voyagent ensemble).
struct RawStatus {
    var presence: String?
    var placeId: String?
    var presenceAtMillis: Int?
    var lastUpdateAtMillis: Int?
    var hasPhoto: Bool?
    var batteryPct: Double?
    var locationAccess: String?
    var updatedAtMillis: Int?
    /// MODE VOYAGE : la fin du voyage (un instant) et le jour choisi (`AAAA-MM-JJ`, affichage seul), quand la présence est `travel`.
    var travelUntilMillis: Int?
    var travelUntilDay: String?
}

/// Le document `miffs/{id}/status/{uid}` d'un membre, tel que le Feed le lit (backend/data-model.md §2.7). Heures en
/// millisecondes depuis 1970 ; nil quand elles manquent. La couleur n'est JAMAIS stockée : `StatusColor` la calcule.
struct MemberStatus: Equatable, CustomStringConvertible {
    let presence: PresenceValue?
    let placeId: String?
    let presenceAtMillis: Int?
    let lastUpdateAtMillis: Int?
    let hasPhoto: Bool
    let batteryPct: Int?
    let locationAccess: MemberLocationAccess?
    let updatedAtMillis: Int?
    /// MODE VOYAGE : la fin du voyage et le jour choisi, quand `presence` est `travel` ; nil sinon.
    var travelUntilMillis: Int?
    var travelUntilDay: String?

    var description: String {
        "MemberStatus(redacted)"
    }

    /// Le plus récent du départ et de la dernière update : le repère de la couleur (décision N2). nil quand aucun n'est connu.
    var referenceMillis: Int? {
        [presenceAtMillis, lastUpdateAtMillis].compactMap { $0 }.max()
    }

    /// Un statut tel que le serveur le décrit, nettoyé avant d'être utilisé (quelqu'un d'autre l'a écrit) : une présence inconnue
    /// est nil (neutre, jamais une supposition), un identifiant de lieu qui n'a pas la forme des nôtres est écarté, une batterie
    /// hors de 0 à 100 est écartée, un accès inconnu est nil.
    static func fromServer(_ raw: RawStatus) -> MemberStatus {
        let presence = PresenceValue.fromWire(raw.presence)
        // Seul un voyage porte une fin : ce que quelqu'un d'autre a écrit à côté d'une autre présence est écarté.
        let isTravel = presence == .travel
        return MemberStatus(
            presence: presence,
            placeId: raw.placeId.flatMap { Place.isPlaceId($0) ? $0 : nil },
            presenceAtMillis: raw.presenceAtMillis,
            lastUpdateAtMillis: raw.lastUpdateAtMillis,
            hasPhoto: raw.hasPhoto == true,
            batteryPct: raw.batteryPct.flatMap { value in
                value.isFinite && value >= 0 && value <= 100 ? Int(value) : nil
            },
            locationAccess: MemberLocationAccess.fromWire(raw.locationAccess),
            updatedAtMillis: raw.updatedAtMillis,
            travelUntilMillis: isTravel ? raw.travelUntilMillis : nil,
            travelUntilDay: isTravel ? raw.travelUntilDay : nil
        )
    }
}
