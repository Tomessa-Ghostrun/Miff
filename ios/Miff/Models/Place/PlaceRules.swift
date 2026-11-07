import Foundation

// SÉCURITÉ, pour ce fichier : le client applique les mêmes règles que backend/firestore.rules (et celles, plus strictes, du
// ticket) pour que le refus du serveur reste un filet de sécurité et jamais la première fois qu'une règle est rencontrée. Les
// coordonnées, l'adresse et le nom passent par ici et nulle part ailleurs : rien n'est journalisé.

/// Les chiffres du ticket (§6.1, §6.2, §6.3, §12) et des règles, à UN seul endroit.
enum PlaceLimits {
    static let nameMin = 2
    static let nameMax = 50
    static let addressMax = 200

    static let radiusMin = 50
    static let radiusMax = 800
    static let radiusStep = 50
    static let radiusDefault = 100

    /// Sous ce rayon, la note « la détection peut manquer un passage » s'affiche (décision Q12).
    static let radiusRecommended = 100

    /// Formule gratuite : 2 lieux (décision QL9), dans les emplacements fixes des règles.
    static let freePlaces = 2
    static let freeIds = ["p1", "p2"]

    /// Un lieu est écrit dans 5 Miffs au plus à la fois (décision Q4) : les règles lisent 20 documents au plus par lot.
    static let maxMiffs = 5

    /// Les règles lisent au plus 20 documents pour un lot (backend/data-model.md §12).
    static let ruleReadBudget = 20

    /// Les suggestions d'adresse commencent à 3 caractères, 300 ms après la dernière frappe (ticket ADR1).
    static let suggestionMinChars = 3
    static let suggestionDebounceMillis = 300
    static let suggestionCount = 5
}

/// Le nom d'un lieu : 2 à 50 caractères une fois les espaces des deux bouts retirés (les règles acceptent 1 à 60).
enum PlaceName {
    /// Ce que le champ garde de ce qui est tapé : nettoyé, 50 unités au plus, coupé entre deux caractères entiers.
    static func limit(_ typed: String) -> NameInput {
        MiffName.limit(typed, maximum: PlaceLimits.nameMax)
    }

    /// Le nom tel qu'il est enregistré : le texte tapé sans les espaces des deux bouts.
    static func stored(_ typed: String) -> String {
        limit(typed).text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// « 2 caractères minimum » s'affiche quand c'est faux et que quelque chose est tapé (le bouton marche dès 1 caractère).
    static func isValid(_ typed: String) -> Bool {
        let length = stored(typed).utf16.count
        return length >= PlaceLimits.nameMin && length <= PlaceLimits.nameMax
    }

    /// Le bouton « Enregistrer » est actif dès UN caractère visible (décision Q10) : le minimum est dit au toucher.
    static func hasSomething(_ typed: String) -> Bool {
        !stored(typed).isEmpty
    }
}

/// Le rayon du périmètre : 50 à 800 m par pas de 50.
enum PlaceRadius {
    static let values: [Int] = Array(stride(
        from: PlaceLimits.radiusMin,
        through: PlaceLimits.radiusMax,
        by: PlaceLimits.radiusStep
    ))

    /// La valeur permise la plus proche de `raw` (une position de curseur, ou une valeur lue quelque part).
    static func snap(_ raw: Double) -> Int {
        guard raw.isFinite else {
            return PlaceLimits.radiusDefault
        }
        // Borné AVANT la conversion en entier (une valeur énorme ferait planter la conversion).
        let bounded = min(max(raw, 0), Double(PlaceLimits.radiusMax))
        let stepped = Int((bounded / Double(PlaceLimits.radiusStep)).rounded()) * PlaceLimits.radiusStep
        return min(max(stepped, PlaceLimits.radiusMin), PlaceLimits.radiusMax)
    }

    static func isAllowed(_ radiusM: Int) -> Bool {
        values.contains(radiusM)
    }

    /// Sous 100 m : la note discrète (jamais une erreur rouge : la valeur reste permise).
    static func isBelowRecommended(_ radiusM: Int) -> Bool {
        radiusM < PlaceLimits.radiusRecommended
    }
}

/// Latitude et longitude comme les règles et le ticket les veulent : 5 décimales (environ 1 m), sur la planète.
enum PlaceCoordinates {
    /// Arrondi « moitié vers le haut » comme `Math.round` d'Android (mêmes valeurs écrites des deux côtés).
    static func round5(_ value: Double) -> Double {
        (value * 100_000 + 0.5).rounded(.down) / 100_000
    }

    static func isValid(lat: Double, lng: Double) -> Bool {
        lat.isFinite && lng.isFinite && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
    }
}

/// L'adresse d'un lieu, comme un texte des règles : nettoyée, 200 caractères au plus, coupée entre deux caractères entiers.
enum PlaceAddressText {
    static func stored(_ label: String) -> String {
        MiffName.limit(label, maximum: PlaceLimits.addressMax).text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Laquelle des deux limites demande Miff+. La zone de danger est Miff+ seulement (décision du ticket §6.2) ; le 3e lieu est
/// la limite.
enum PaywallReason: Hashable {
    case limit
    case danger
}

enum DraftProblem: Equatable {
    case name
    case miffs
    case address
    case radius
}

/// Ce que la personne a rempli, prêt à être écrit. Construit par le formulaire seulement quand `problem` est nil. Les Miffs
/// sont les identifiants de Miffs que LA PERSONNE A CRÉÉES (le dépôt le revérifie auprès du serveur).
struct PlaceDraft: Equatable, CustomStringConvertible {
    let name: String
    let miffIds: [String]
    let address: String
    let lat: Double
    let lng: Double
    let radiusM: Int
    let type: PlaceType
    let category: PlaceCategory

    var description: String {
        "PlaceDraft(redacted)"
    }

    /// Ce qui ne va pas dans ce brouillon, ou nil. Reprend les règles (le formulaire le montre avant, le serveur le contrôle
    /// après).
    var problem: DraftProblem? {
        if !PlaceName.isValid(name) {
            return .name
        }
        if miffIds.isEmpty || miffIds.count > PlaceLimits.maxMiffs || Set(miffIds).count != miffIds.count {
            return .miffs
        }
        let blank = address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        if blank || address.utf16.count > PlaceLimits.addressMax || !PlaceCoordinates.isValid(lat: lat, lng: lng) {
            return .address
        }
        return PlaceRadius.isAllowed(radiusM) ? nil : .radius
    }
}

/// La réponse de `PlaceQuota` : quel identifiant écrire, ou pourquoi Miff+ est nécessaire.
enum PlaceSlot: Equatable {
    /// Écrire avec cet identifiant (`p1` / `p2` en gratuit, un neuf avec Miff+).
    case use(String)
    case needsPremium(PaywallReason)
}

/// Les limites de la formule gratuite, décidées AVANT que rien soit envoyé (les règles ne sont que le filet, ticket §6.2).
///
/// Gratuit : 2 lieux de confiance dans les emplacements fixes `p1` et `p2` des règles ; pas de zone de danger. Miff+ : autant
/// qu'on veut, de tout type, un identifiant neuf tiré une fois sur le téléphone.
enum PlaceQuota {
    /// - existingIdsByMiff : les identifiants déjà pris dans chaque Miff que la personne a créée (TOUTES, pas seulement celles
    ///   choisies).
    /// - distinctPlaces : combien de lieux DIFFÉRENTS la personne a (un lieu dans 3 Miffs compte pour un) : ce que limite la
    ///   formule gratuite.
    static func forCreate(
        premium: Bool,
        type: PlaceType,
        existingIdsByMiff: [String: Set<String>],
        distinctPlaces: Int,
        newId: () -> String
    ) -> PlaceSlot {
        if premium {
            return .use(newId())
        }
        if type == .risk {
            return .needsPremium(.danger)
        }
        if distinctPlaces >= PlaceLimits.freePlaces {
            return .needsPremium(.limit)
        }
        // L'emplacement doit être libre dans TOUTES les Miffs que la personne a créées, pas seulement dans celles choisies :
        // deux lieux différents au même identifiant seraient lus comme UN lieu dans deux Miffs (le compte de la limite et la
        // modification seraient faux).
        let taken = existingIdsByMiff.values.reduce(into: Set<String>()) { $0.formUnion($1) }
        guard let free = PlaceLimits.freeIds.first(where: { !taken.contains($0) }) else {
            return .needsPremium(.limit)
        }
        return .use(free)
    }

    /// La formule gratuite ne peut pas faire d'un lieu une zone de danger (R-LIEU-6) ; revenir à « confiance » est toujours permis.
    static func forTypeChange(premium: Bool, from: PlaceType, to: PlaceType) -> PaywallReason? {
        !premium && to == .risk && from != .risk ? .danger : nil
    }

    /// « Ajouter un lieu » d'un créateur ouvre-t-il l'écran de la limite plutôt que le formulaire (gratuit, 2 lieux déjà) ?
    static func isAtFreeLimit(premium: Bool, distinctPlaces: Int) -> Bool {
        !premium && distinctPlaces >= PlaceLimits.freePlaces
    }
}

/// Les Miffs cochées à l'ouverture du formulaire (décision Q4).
enum DefaultMiffChoice {
    /// La dernière Miff rejointe SI la personne l'a créée, sinon la première qu'elle a créée ; nil quand elle n'en a créé aucune.
    /// - owned : les Miffs que la personne a créées, dans n'importe quel ordre
    /// - lastJoinedId : la Miff rejointe le plus récemment, créée par la personne ou non
    static func of(owned: [OwnedMiff], lastJoinedId: String?) -> String? {
        if let lastJoinedId, owned.contains(where: { $0.id == lastJoinedId }) {
            return lastJoinedId
        }
        return owned.min { $0.joinedAtMillis < $1.joinedAtMillis }?.id
    }
}
