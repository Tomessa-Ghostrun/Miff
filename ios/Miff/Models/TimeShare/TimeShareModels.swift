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

struct TimeShareResult: Equatable, CustomStringConvertible {
    let windowDays: Int
    let measuredMillis: Int
    let rows: [TimeShareRow]
    let dangerDetails: [TimeShareDangerDetail]
    /// Au centre de l'anneau : la somme des lieux de confiance SEULEMENT (ni zone de danger ni « Ailleurs »).
    let trustedPercent: Int

    /// Les parts montrées dans la légende : 4 au plus, les plus longues, dans l'ordre de l'anneau (égalité de durée : l'ordre de l'anneau).
    /// La ligne « Zone de danger » est TOUJOURS montrée quand sa part arrondie est d'au moins 1 % : si elle n'est pas déjà parmi les plus
    /// longues, elle prend la place de la 4e (décision du PM : le détail de la zone doit pouvoir s'ouvrir). En dessous de 1 % (« < 1 % », part
    /// qui compte 0), elle n'a PAS de ligne dans la légende, et sa place revient à la part suivante. L'anneau garde TOUTES les tranches ;
    /// « Hors-zone » suit la règle commune (aucun détail à ouvrir) ; la description du lecteur d'écran dit les parts que la légende ne montre pas.
    static let legendLimit = 4

    var legendIndices: [Int] {
        let candidates = rows.indices.filter { !isTinyDanger(rows[$0]) }
        let ranked = candidates.sorted { left, right in
            rows[left].millis != rows[right].millis ? rows[left].millis > rows[right].millis : left < right
        }
        var shown = Array(ranked.prefix(Self.legendLimit))
        let forced = candidates.first { index in
            if case .danger = rows[index].kind {
                return rows[index].percent >= 1
            }
            return false
        }
        if let forced, !shown.contains(forced), !shown.isEmpty {
            shown[shown.count - 1] = forced
        }
        return shown.sorted()
    }

    /// Une zone de danger dont la part arrondie est 0 (« < 1 % », ou rien) : jamais de ligne dans la légende.
    private func isTinyDanger(_ row: TimeShareRow) -> Bool {
        if case .danger = row.kind {
            return row.percent < 1
        }
        return false
    }

    var description: String {
        "TimeShareResult(redacted)"
    }
}

enum TimeShareOutcome: Equatable, CustomStringConvertible {
    /// Moins de 3 jours d'historique (depuis le DÉBUT du suivi de la Miff sur ce téléphone) : aucun graphique, aucun pourcentage.
    /// `daysLeft` : les jours qui restent avant le seuil d'historique (de 3 à 1), pour « Ton graphique arrive dans X jours ».
    case notEnough(daysLeft: Int)
    /// Le seuil de 3 jours est passé mais RIEN n'a été mesuré dans la fenêtre (la personne n'est jamais entrée dans un lieu) : aucun graphique,
    /// et surtout pas de décompte qui resterait bloqué à « dans 1 jour » : un texte qui dit ce qui manque.
    case noTimeMeasured
    case ready(TimeShareResult)

    var description: String {
        "TimeShareOutcome(redacted)"
    }
}
