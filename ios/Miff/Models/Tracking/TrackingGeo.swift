import Foundation

// CONFIDENTIALITÉ, pour ce fichier : de la géométrie pure sur des nombres. Rien n'est lu sur le téléphone, rien n'est journalisé.
// Une coordonnée ne vit que dans un type dont la `description` ne montre rien (voir `TrackingModels.swift`). Miroir de `Geo.kt`
// d'Android (LOC-2b).

enum TrackingGeo {
    private static let earthRadiusMeters = 6_371_008.8

    /// Distance en mètres entre deux points (haversine : assez exacte pour des zones de 50 m et plus).
    static func distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double) -> Double {
        let phi1 = lat1 * .pi / 180
        let phi2 = lat2 * .pi / 180
        let deltaPhi = phi2 - phi1
        let deltaLambda = (lng2 - lng1) * .pi / 180
        let half = pow(sin(deltaPhi / 2), 2) + cos(phi1) * cos(phi2) * pow(sin(deltaLambda / 2), 2)
        return 2 * earthRadiusMeters * asin(min(1, sqrt(half)))
    }

    /// La coordonnée telle que les règles et le modèle de données la veulent : 5 décimales (environ un mètre). La seule
    /// précision écrite.
    static func round5(_ value: Double) -> Double {
        (value * 100_000).rounded() / 100_000
    }

    /// Distance au BORD d'une zone : la distance à son centre moins son rayon, jamais sous zéro (0 = dedans, ou sur le bord).
    static func edgeDistanceMeters(distanceToCentre: Double, radiusM: Int) -> Double {
        max(0, distanceToCentre - Double(radiusM))
    }
}
