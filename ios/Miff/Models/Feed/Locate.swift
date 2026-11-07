import Foundation

// CONFIDENTIALITÉ, pour ce fichier : une position est parmi les données les plus sensibles de l'app. Chaque type qui en porte une a
// une `description` qui ne montre rien (critère L4 : aucune coordonnée dans un journal, un rapport de plantage, un événement
// d'analyse, un message de partage ni l'état sauvegardé d'un écran). Les coordonnées n'existent, en mémoire de l'écran, qu'entre
// la lecture de `positions/{uid}` et la fermeture de la feuille « Localiser » : le Feed ne les garde jamais. (La copie que le SDK
// Firestore garde dans son propre cache est une limite connue, voir `FirestorePositionReader`.)

/// Ce que la lecture UNIQUE de `positions/{uid}` (auprès du SERVEUR, jamais du cache, sans écoute) a donné.
enum PositionLookup: Equatable, CustomStringConvertible {
    case found(lat: Double, lng: Double, measuredAtMillis: Int)
    /// Le document n'existe pas : « n'a pas encore de position connue ».
    case notFound
    /// Les règles ont refusé (réciprocité, partage arrêté, retrait) : UN seul message neutre pour tous les refus, personne n'apprend qui a arrêté.
    case denied
    /// Il n'y a pas de réseau : « Pas de connexion pour le moment ». Une vraie absence de réseau seulement.
    case offline
    /// Autre chose d'inattendu (quota du plan atteint, délai dépassé, connexion expirée, serveur) : « Impossible d'afficher la position
    /// pour le moment. » Jamais un mot technique, jamais « quota ».
    case failed

    var description: String {
        "PositionLookup(redacted)"
    }
}

/// Les coordonnées d'une position récente, gardées seulement tant que la feuille est ouverte.
struct PositionFix: Equatable, CustomStringConvertible {
    let lat: Double
    let lng: Double

    var description: String {
        "PositionFix(redacted)"
    }
}

/// Ce que dit la feuille « Dernière position connue de {Prénom} » (six réponses calmes : critères L1 à L3 et l'échec inattendu).
enum LocateResult: Equatable, CustomStringConvertible {
    /// Mesurée il y a moins de 24 h : l'heure de la mesure, « ce n'est pas une position en direct », et le chemin vers Plans.
    case recent(fix: PositionFix, measuredAt: Moment, age: Age)
    /// Mesurée il y a plus de 24 h : la DATE seulement, aucune coordonnée, aucun bouton de carte.
    case old(measuredAt: Moment)
    case notFound
    case denied
    case offline
    case unavailable

    var description: String {
        "LocateResult(redacted)"
    }
}

enum Locate {
    /// Une position plus ancienne n'est plus montrée (décision du PM, 2026-11-01) : la date reste, les coordonnées s'en vont.
    static let shownForMillis = FeedMillis.day

    /// « Pas de connexion » n'est dit que pour une vraie absence de réseau : un « injoignable » alors que le téléphone A un réseau
    /// est un échec inattendu, et un échec inattendu sans réseau est l'absence de réseau.
    static func reconcile(_ lookup: PositionLookup, networkOnline: Bool) -> PositionLookup {
        if lookup == .offline && networkOnline {
            return .failed
        }
        if lookup == .failed && !networkOnline {
            return .offline
        }
        return lookup
    }

    static func interpret(_ lookup: PositionLookup, nowMillis: Int, zone: TimeZone) -> LocateResult {
        switch lookup {
        case .notFound:
            return .notFound
        case .denied:
            return .denied
        case .offline:
            return .offline
        case .failed:
            return .unavailable
        case .found(let lat, let lng, let measuredAtMillis):
            let moment = Moment.of(atMillis: measuredAtMillis, nowMillis: nowMillis, zone: zone)
            // Strictement plus de 24 h : les coordonnées sont abandonnées ICI, elles ne vont même pas jusqu'à l'écran.
            if nowMillis - measuredAtMillis > shownForMillis {
                return .old(measuredAt: moment)
            }
            return .recent(
                fix: PositionFix(lat: lat, lng: lng),
                measuredAt: moment,
                age: Age.of(thenMillis: measuredAtMillis, nowMillis: nowMillis, zone: zone)
            )
        }
    }

    /// Cinq décimales (environ un mètre) : la précision avec laquelle la position a été écrite.
    private static func coordinate(_ value: Double) -> String {
        String(format: "%.5f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    /// L'adresse donnée à Plans : les COORDONNÉES SEULES (l'épingle tombe dessus), aucun nom, aucune étiquette, aucune personne
    /// (critère L2). Écrite en `en_US_POSIX` : un téléphone en français écrirait « 48,85661 » et casserait l'adresse.
    static func mapsUrl(_ fix: PositionFix) -> String {
        let point = "\(coordinate(fix.lat)),\(coordinate(fix.lng))"
        return "https://maps.apple.com/?ll=\(point)&q=\(point)"
    }
}
