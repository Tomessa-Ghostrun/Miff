import Foundation

// CONFIDENTIALITÉ, pour ce fichier : la position donnée à `select` reste dans cette fonction ; elle n'est jamais gardée,
// journalisée ni envoyée. Miroir de `ZoneSelection.kt` d'Android (LOC-2b).

/// Quelles zones des Miffs où la personne partage sont surveillées, et lesquelles sont laissées de côté (ticket §6.5, critères
/// POS2, POS3, POS4). iOS permet 20 régions surveillées par application : c'est aussi le choix du PM pour Android (décision Q9).
enum ZoneSelection {
    static let maxWatched = 20

    /// Un lieu d'une Miff, avant que les copies soient remises ensemble.
    struct MiffPlace: CustomStringConvertible {
        let miffId: String
        let place: PlaceFact

        var description: String {
            "MiffPlace(redacted)"
        }
    }

    private struct Scored {
        let zone: TrackedZone
        let edge: Double
        let centre: Double
        let here: Bool
    }

    /// Les copies d'un lieu dans plusieurs Miffs sont UNE zone (critère MIF3) SEULEMENT quand c'est la même zone sur le terrain :
    /// même identifiant, même position, même rayon, même type (`ZoneKey`). Deux lieux différents qui ont par hasard le même
    /// identifiant (les `p1` / `p2` / `p3` fixes de deux créateurs gratuits) sont deux zones, comme deux copies périmées qui divergent :
    /// la prochaine lecture du serveur fait disparaître la périmée. Triées par clé : même entrée, même liste.
    static func merge(_ places: [MiffPlace]) -> [TrackedZone] {
        var groups: [String: [MiffPlace]] = [:]
        for item in places {
            groups[item.place.key, default: []].append(item)
        }
        let zones = groups.compactMap { key, copies -> TrackedZone? in
            guard let first = copies.first?.place else {
                return nil
            }
            return TrackedZone(
                key: key,
                placeId: first.id,
                lat: first.lat,
                lng: first.lng,
                radiusM: first.radiusM,
                type: first.type,
                miffIds: Set(copies.map { $0.miffId })
            )
        }
        return zones.sorted { $0.key < $1.key }
    }

    /// Les zones à surveiller : celles où la personne EST (par la position, ou par l'état local de la présence) viennent
    /// toujours en premier, pour qu'un départ ne soit jamais raté parce qu'une grande zone paraît « loin » de son centre
    /// (décision N8) ; le reste, les plus proches par le BORD de la zone (distance au centre moins le rayon), jusqu'à `limit`.
    /// À égalité : la distance au centre, puis la clé. Ne lit que la liste donnée : aucune requête au serveur (POS4).
    static func select(
        lat: Double,
        lng: Double,
        zones: [TrackedZone],
        insideKeys: Set<String>,
        limit: Int = maxWatched
    ) -> [TrackedZone] {
        let scored = zones.map { zone -> Scored in
            let centre = TrackingGeo.distanceMeters(lat1: lat, lng1: lng, lat2: zone.lat, lng2: zone.lng)
            let edge = TrackingGeo.edgeDistanceMeters(distanceToCentre: centre, radiusM: zone.radiusM)
            return Scored(zone: zone, edge: edge, centre: centre, here: edge == 0 || insideKeys.contains(zone.key))
        }
        let ordered = scored.sorted { left, right in
            if left.here != right.here {
                return left.here
            }
            let leftEdge = left.here ? 0 : left.edge
            let rightEdge = right.here ? 0 : right.edge
            if leftEdge != rightEdge {
                return leftEdge < rightEdge
            }
            if left.centre != right.centre {
                return left.centre < right.centre
            }
            return left.zone.key < right.zone.key
        }
        return ordered.prefix(Swift.max(0, limit)).map { $0.zone }
    }

    /// La position est-elle dans cette zone ? (Sur le bord compte comme dedans.)
    static func contains(_ zone: TrackedZone, lat: Double, lng: Double) -> Bool {
        TrackingGeo.distanceMeters(lat1: lat, lng1: lng, lat2: zone.lat, lng2: zone.lng) <= Double(zone.radiusM)
    }
}
