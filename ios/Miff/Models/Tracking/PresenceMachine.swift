import Foundation

// CONFIDENTIALITÉ, pour ce fichier : seulement des identifiants de lieux et des heures ; ni coordonnée, ni nom, ni adresse. Du
// Swift pur : rien n'est lu sur le téléphone. Miroir de `PresenceMachine.kt` d'Android (LOC-2b).

/// Une zone d'UNE Miff, pour ce qui est de la présence : sa `key` nomme la zone sur le terrain, son `placeId` est l'identifiant du
/// lieu dans cette Miff.
struct ZoneRef: Equatable {
    let key: String
    let placeId: String
    let type: PlaceType
}

/// Ce qu'un événement change dans UNE Miff : l'état déduit, et le fait à écrire.
struct PresenceChange: Equatable {
    let derived: DerivedPresence
    let fact: StatusFact
}

/// Un événement de la surveillance de régions : la nature, la zone, l'heure à laquelle il est arrivé.
struct ZoneTransition: Equatable {
    let kind: TransitionKind
    let zoneKey: String
    let atMillis: Int
}

/// Des événements de zone au statut d'UNE Miff (ticket §6.6, critères DET2, DET3, STA1). L'état local « où je suis » est une
/// table zone -> heure d'entrée, commune à toutes les Miffs ; chaque Miff en déduit son propre statut avec SES zones : quitter une
/// zone de la Miff A ne change jamais ce que dit la Miff B.
enum PresenceMachine {
    /// La présence d'une Miff d'après les zones où la personne est : une zone de CONFIANCE l'emporte sur une zone de danger (« la
    /// zone de confiance l'emporte », décision du PM, ticket Statut) ; entre plusieurs zones du même type, la dernière entrée est
    /// nommée (quitter A en restant dans B = `in_trusted` sur B) ; aucune zone de la Miff = `out`. Le `placeId` est celui de la
    /// Miff ; celui d'un `out` est rempli par l'appelant (la zone qu'on vient de quitter).
    static func derive(zones: [ZoneRef], inside: [String: Int]) -> DerivedPresence {
        if let placeId = latest(.trusted, in: zones, inside: inside) {
            return DerivedPresence(presence: .inTrusted, placeId: placeId)
        }
        if let placeId = latest(.risk, in: zones, inside: inside) {
            return DerivedPresence(presence: .inRisk, placeId: placeId)
        }
        return DerivedPresence(presence: .out, placeId: nil)
    }

    private static func latest(_ type: PlaceType, in zones: [ZoneRef], inside: [String: Int]) -> String? {
        let candidates = zones.filter { $0.type == type && inside[$0.key] != nil }
        let ordered = candidates.sorted { left, right in
            let leftTime = inside[left.key] ?? 0
            let rightTime = inside[right.key] ?? 0
            return leftTime != rightTime ? leftTime > rightTime : left.placeId < right.placeId
        }
        return ordered.first?.placeId
    }

    /// Ce qu'un événement change dans une Miff : nil quand le lieu n'est pas un lieu de cette Miff ou que rien ne change pour elle
    /// (entrer dans une zone de danger en étant dans une zone de confiance, entrer dans la zone que le statut nomme déjà).
    /// `insideAfter` est l'état local APRÈS l'événement ; `last` est la présence que ce téléphone a déduite en dernier pour la
    /// Miff (nil s'il n'y en a pas encore).
    static func onTransition(
        _ transition: ZoneTransition,
        zones: [ZoneRef],
        insideAfter: [String: Int],
        last: DerivedPresence?
    ) -> PresenceChange? {
        guard let zone = zones.first(where: { $0.key == transition.zoneKey }) else {
            return nil
        }
        let raw = derive(zones: zones, inside: insideAfter)
        // Un départ qui ne laisse aucune zone dit LAQUELLE a été quittée (l'étiquette « A quitté {lieu} » du Feed).
        let derived = raw.presence == .out && transition.kind == .exit
            ? DerivedPresence(presence: .out, placeId: zone.placeId)
            : raw
        if derived == last {
            return nil
        }
        let fact = StatusFact(
            presence: derived.presence,
            placeId: derived.placeId,
            presenceAtMillis: transition.atMillis,
            // Une arrivée en zone de confiance est aussi une update : le repère de la couleur avance avec elle (ticket §6.6).
            setLastUpdate: derived.presence == .inTrusted
        )
        return PresenceChange(derived: derived, fact: fact)
    }

    /// Le système dit que la personne est HORS d'une zone que la copie locale disait occupée : la sortie a été manquée (app fermée de
    /// force, téléphone éteint). Son heure est inconnue : si la Miff n'a plus aucune zone occupée, le statut devient `unknown` (jamais
    /// un `out` inventé) ; si la personne est encore dans une autre zone de la Miff, c'est cette zone-là qui est dite, à l'heure `atMillis`.
    /// nil quand rien ne change pour la Miff.
    static func onMissedExit(
        zoneKey: String,
        zones: [ZoneRef],
        insideAfter: [String: Int],
        last: DerivedPresence?,
        atMillis: Int
    ) -> PresenceChange? {
        guard zones.contains(where: { $0.key == zoneKey }) else {
            return nil
        }
        let derived = derive(zones: zones, inside: insideAfter)
        if derived.presence == .out {
            guard let last, last.presence == .inTrusted || last.presence == .inRisk else {
                return nil
            }
            return PresenceChange(
                derived: DerivedPresence(presence: .unknown, placeId: nil),
                fact: unknown(atMillis: atMillis)
            )
        }
        if derived == last {
            return nil
        }
        let fact = StatusFact(
            presence: derived.presence,
            placeId: derived.placeId,
            presenceAtMillis: atMillis,
            setLastUpdate: derived.presence == .inTrusted
        )
        return PresenceChange(derived: derived, fact: fact)
    }

    /// La présence ne peut plus être confirmée (lieu supprimé ou modifié, zone dont la sortie a été manquée) : `unknown`, jamais
    /// une supposition.
    static func unknown(atMillis: Int) -> StatusFact {
        StatusFact(presence: .unknown, placeId: nil, presenceAtMillis: atMillis, setLastUpdate: false)
    }
}
