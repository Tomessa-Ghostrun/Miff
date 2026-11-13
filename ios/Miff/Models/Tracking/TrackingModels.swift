import Foundation

// CONFIDENTIALITÉ, pour ce fichier : une coordonnée, une zone ou une position est parmi les données les plus sensibles de l'app.
// Chaque type qui en porte une a une `description` qui ne montre rien, pour qu'une ligne de journal, un paramètre Analytics
// ou un rapport de plantage ne puisse jamais la laisser fuiter (critère VIE1). Miroir de `TrackingModels.kt` d'Android (LOC-2b).

/// L'identité d'une ZONE sur le terrain : l'identifiant du lieu ET sa géométrie et son type. Un compte gratuit n'a que les
/// identifiants fixes `p1`, `p2` et `p3` (les règles) : deux lieux différents de deux créateurs peuvent donc porter le même identifiant,
/// et l'identifiant seul ne doit jamais nommer une zone. Un même lieu attaché à plusieurs Miffs (même identifiant, même géométrie)
/// a la même clé : une seule zone. La clé contient des coordonnées : elle ne quitte jamais le téléphone (copie locale et
/// surveillance de régions du système) et n'est JAMAIS écrite dans un journal ; `placeId` est ce qui est montré ou écrit.
enum ZoneKey {
    static func of(_ fact: PlaceFact) -> String {
        "\(fact.id)|\(fact.lat)|\(fact.lng)|\(fact.radiusM)|\(fact.type.rawValue)"
    }

    /// L'identifiant du lieu d'une clé (la partie qui peut être écrite dans un statut ou un journal).
    static func placeId(_ key: String) -> String {
        guard let separator = key.firstIndex(of: "|") else {
            return key
        }
        return String(key[key.startIndex..<separator])
    }

    private static let number = "-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?"
    private static let shape = "[A-Za-z0-9]{1,20}\\|\(number)\\|\(number)\\|[0-9]{1,4}\\|(trusted|risk)"

    /// Cette chaîne a-t-elle la forme d'une clé que nous avons faite ? (Le système rend l'identifiant d'une région tel quel :
    /// toute autre forme est ignorée.)
    static func isValid(_ value: String) -> Bool {
        value.count <= 100 && value.range(of: shape, options: .regularExpression) == value.startIndex..<value.endIndex
    }
}

/// Ce que la copie locale des lieux garde d'UN lieu d'UNE Miff : ce qu'il faut pour le surveiller, rien d'autre (ni nom ni adresse).
struct PlaceFact: Equatable, CustomStringConvertible {
    let id: String
    let lat: Double
    let lng: Double
    let radiusM: Int
    let type: PlaceType

    var key: String {
        ZoneKey.of(self)
    }

    /// Même zone sur le terrain (un lieu modifié par son créateur est une autre zone : la présence ne peut plus être confirmée).
    func sameGeometry(_ other: PlaceFact) -> Bool {
        lat == other.lat && lng == other.lng && radiusM == other.radiusM && type == other.type
    }

    var description: String {
        "PlaceFact(redacted)"
    }
}

/// UNE zone physique surveillée par le téléphone : les copies d'un lieu dans les Miffs où la personne partage sont le même lieu
/// (décision Q4, critère MIF3) : une zone, un statut par Miff. `miffIds` : les Miffs auxquelles elle appartient (jamais vide).
struct TrackedZone: Equatable, CustomStringConvertible {
    /// `ZoneKey` : l'identité de la zone (identifiant + géométrie + type).
    let key: String
    let placeId: String
    let lat: Double
    let lng: Double
    let radiusM: Int
    let type: PlaceType
    let miffIds: Set<String>

    var description: String {
        "TrackedZone(redacted)"
    }
}

/// Une position de la personne, avec l'heure de la MESURE (jamais l'heure de la livraison). Millisecondes depuis 1970.
struct Fix: Equatable, CustomStringConvertible {
    let lat: Double
    let lng: Double
    let measuredAtMillis: Int

    var description: String {
        "Fix(redacted)"
    }
}

/// Ce que dit la surveillance de régions du système : la personne est entrée dans une zone, ou en est sortie.
enum TransitionKind: Equatable {
    case enter
    case exit
}

/// Les valeurs de `presence` dans `status/{moi}` (`backend/data-model.md` §2.7). `inRisk` est la « zone de danger » des textes.
enum PresenceValue: String {
    case inTrusted = "in_trusted"
    case out
    case inRisk = "in_risk"
    case unknown
    /// MODE VOYAGE : la personne est en voyage jusqu'à une date (`travelUntil`). Toujours une couleur neutre ; une ancienne version de l'app
    /// lit cette valeur comme inconnue (neutre aussi).
    case travel

    /// Une valeur inconnue ou absente est nil : la couleur d'un tel statut est neutre, jamais une supposition.
    static func fromWire(_ value: String?) -> PresenceValue? {
        value.flatMap { PresenceValue(rawValue: $0) }
    }
}

/// L'état de la personne dans UNE Miff, tel que le moteur le déduit des zones de cette Miff.
struct DerivedPresence: Equatable {
    let presence: PresenceValue
    let placeId: String?
}

/// Ce qu'il faut écrire dans `status/{moi}` d'une Miff après un événement (ticket §6.6) : les faits seulement, l'heure RÉELLE de
/// l'événement. La couleur n'est jamais écrite. `setLastUpdate` est vrai pour une arrivée en zone de confiance (`lastUpdateAt` =
/// l'heure de l'événement) ; sinon `lastUpdateAt` n'est pas touché.
struct StatusFact: Equatable {
    let presence: PresenceValue
    let placeId: String?
    let presenceAtMillis: Int
    let setLastUpdate: Bool
}
