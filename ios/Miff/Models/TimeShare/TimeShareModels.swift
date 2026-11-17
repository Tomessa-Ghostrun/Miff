import Foundation

// CONFIDENTIALITÉ, pour ce fichier : les noms de lieux sont lus ICI dans la liste actuelle des lieux et restent en mémoire, pour l'écran
// seulement ; chaque type qui en porte cache son contenu dans `description` (aucune ligne de journal, aucun événement, aucun rapport de
// plantage ne doit pouvoir montrer un lieu, une durée ou un pourcentage : critère TS-4).

/// Ce que le calcul sait d'un lieu de la Miff, tel que la liste actuelle le dit (nom, catégorie et type ne sont jamais gardés dans le journal).
struct TimeSharePlace: Equatable, CustomStringConvertible {
    let id: String
    let name: String
    let type: PlaceType
    let category: PlaceCategory?

    init(id: String, name: String, type: PlaceType, category: PlaceCategory?) {
        self.id = id
        self.name = name
        self.type = type
        self.category = category
    }

    init(_ place: Place) {
        self.init(id: place.id, name: place.name, type: place.type, category: place.category)
    }

    var description: String {
        "TimeSharePlace(redacted)"
    }
}

/// Une ligne de la légende, dans l'ordre de l'anneau (sens horaire depuis midi).
enum TimeShareRowKind: Equatable {
    /// Un lieu de confiance nommé (son identifiant).
    case place(String)
    /// « {n} autres lieux » : les lieux de confiance au-delà des 5 nommés (n = leur nombre).
    case otherPlaces(Int)
    /// « Zone de danger » : UNE ligne agrégée (n = nombre de lieux de ce type où du temps a été passé).
    case danger(Int)
    case elsewhere
}

struct TimeShareRow: Equatable, CustomStringConvertible {
    let kind: TimeShareRowKind
    /// Le nom actuel du lieu (seulement pour `.place`).
    let name: String?
    let category: PlaceCategory?
    let millis: Int
    /// Pourcentage entier ; 0 pour une part réelle de moins de 1 % (affichée « < 1 % »).
    let percent: Int
    let isUnderOnePercent: Bool

    var description: String {
        "TimeShareRow(redacted)"
    }
}

/// Une ligne du détail de la zone de danger (un lieu) : la somme des pourcentages égale celui de la ligne agrégée.
struct TimeShareDangerDetail: Equatable, CustomStringConvertible {
    let placeId: String
    let name: String
    let category: PlaceCategory?
    let millis: Int
    let percent: Int
    let isUnderOnePercent: Bool

    var description: String {
        "TimeShareDangerDetail(redacted)"
    }
}

/// Ce que dit la phrase de synthèse (ticket §5.4) : descriptive, jamais un jugement, ne nomme jamais la zone de danger.
enum TimeShareSynthesis: Equatable {
    /// La plus grande part est un lieu de confiance nommé.
    case mostAtPlace(String)
    case mostlyElsewhere
    /// Plus grande part = « {n} autres lieux » ou « Zone de danger » (ou égalité avec elle).
    case generic
}

struct TimeShareResult: Equatable, CustomStringConvertible {
    let windowDays: Int
    let measuredMillis: Int
    let rows: [TimeShareRow]
    let dangerDetails: [TimeShareDangerDetail]
    /// Au centre de l'anneau : la somme des lieux de confiance SEULEMENT (ni zone de danger ni « Ailleurs »).
    let trustedPercent: Int
    let synthesis: TimeShareSynthesis

    var description: String {
        "TimeShareResult(redacted)"
    }
}

enum TimeShareOutcome: Equatable, CustomStringConvertible {
    /// Moins de 24 h mesurées dans la fenêtre : aucun pourcentage.
    case notEnough(measuredMillis: Int)
    case ready(TimeShareResult)

    var description: String {
        "TimeShareOutcome(redacted)"
    }
}
