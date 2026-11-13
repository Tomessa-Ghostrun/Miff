import Foundation

// SÉCURITÉ, pour ce fichier : le nom, l'adresse et les coordonnées d'un lieu sont sensibles (une maison, une école, la maison
// d'une personne qui n'utilise pas Miff). Chaque type qui en porte un a une `description` qui ne montre rien, pour qu'une
// ligne de journal, un paramètre Analytics ou un rapport de plantage ne puisse jamais les laisser fuiter. Rien ici n'est
// jamais journalisé.

/// Les deux sortes de zone. Les valeurs techniques sont celles des règles et du modèle de données (`risk` est la zone de
/// danger : « zone de danger » dans tous les textes montrés à la personne).
enum PlaceType: String, Equatable, CaseIterable {
    case trusted
    case risk
}

/// Les sept catégories, une liste fermée (règle R-LIEU-2). Les valeurs techniques sont écrites dans le document du lieu.
enum PlaceCategory: String, Equatable, CaseIterable {
    case home
    case work
    case school
    case sport
    case relative
    case leisure
    case other

    /// « Maison » est cochée à la création d'un lieu (ticket §6.1) ; la personne peut en changer, jamais la laisser vide.
    static let preselected = PlaceCategory.home
}

/// Un document `miffs/{miffId}/places/{placeId}`, tel que le serveur le décrit. Un lieu attaché à plusieurs Miffs en est
/// plusieurs, de même identifiant (décision Q4) : `PlaceGroup` les remet ensemble.
///
/// `category` est nil pour un lieu écrit avant que la catégorie existe (il ne peut être modifié qu'en en choisissant une).
struct Place: Equatable, CustomStringConvertible {
    let id: String
    let miffId: String
    let name: String
    let type: PlaceType
    let category: PlaceCategory?
    let address: String?
    let lat: Double
    let lng: Double
    let radiusM: Int
    let createdBy: String

    var description: String {
        "Place(redacted)"
    }

    /// Les bornes des règles (`validPlaceShape`) : plus larges que celles de l'écran (50 à 800).
    private static let ruleRadiusMin = 50
    private static let ruleRadiusMax = 2000

    /// Un lieu tel que le serveur le décrit, nettoyé avant d'être montré (noms et adresses sont écrits par quelqu'un d'autre :
    /// le créateur). nil quand le document ne peut pas être un lieu de cette app (identifiant invalide, pas de nom visible,
    /// type inconnu, coordonnées ou rayon hors bornes) : il n'est simplement pas montré, jamais un plantage.
    static func fromServer(id: String, miffId: String, fields: [String: Any]?) -> Place? {
        guard let fields, isPlaceId(id),
              let rawName = fields["name"] as? String,
              let name = MiffName.fromServer(rawName),
              let typeValue = fields["type"] as? String,
              let type = PlaceType(rawValue: typeValue),
              let lat = number(fields["lat"]), lat.isFinite, lat >= -90, lat <= 90,
              let lng = number(fields["lng"]), lng.isFinite, lng >= -180, lng <= 180,
              let radius = number(fields["radiusM"]), radius.isFinite, radius == radius.rounded(),
              radius >= Double(ruleRadiusMin), radius <= Double(ruleRadiusMax) else {
            return nil
        }
        let address = (fields["address"] as? String).flatMap { MiffName.fromServer($0) }
        let category = (fields["category"] as? String).flatMap { PlaceCategory(rawValue: $0) }
        return Place(
            id: id,
            miffId: miffId,
            name: name,
            type: type,
            category: category,
            address: address,
            lat: lat,
            lng: lng,
            radiusM: Int(radius),
            createdBy: fields["createdBy"] as? String ?? ""
        )
    }

    /// 1 à 20 lettres et chiffres : `p1`, `p2`, `p3`, ou un identifiant tiré par le SDK. Jamais un chemin construit avec un autre texte.
    static func isPlaceId(_ value: String) -> Bool {
        let scalars = Array(value.unicodeScalars)
        return !scalars.isEmpty && scalars.count <= 20 && scalars.allSatisfy { scalar in
            (scalar.value >= 0x30 && scalar.value <= 0x39)
                || (scalar.value >= 0x41 && scalar.value <= 0x5A)
                || (scalar.value >= 0x61 && scalar.value <= 0x7A)
        }
    }

    /// Un nombre lu sur le réseau : décimal ou entier (le SDK rend l'un ou l'autre selon la valeur).
    private static func number(_ value: Any?) -> Double? {
        if let double = value as? Double {
            return double
        }
        if let integer = value as? Int {
            return Double(integer)
        }
        if let integer = value as? Int64 {
            return Double(integer)
        }
        return nil
    }
}

/// Un lieu et toutes les Miffs auquel il est attaché : les copies d'un même identifiant, dans l'ordre des Miffs données.
struct PlaceGroup: Equatable, CustomStringConvertible {
    let id: String
    let copies: [Place]

    /// La copie qui est montrée et modifiée (les copies sont écrites ensemble : mêmes champs).
    var main: Place {
        copies[0]
    }

    var miffIds: [String] {
        copies.map { $0.miffId }
    }

    var description: String {
        "PlaceGroup(redacted)"
    }

    /// Les lieux de plusieurs Miffs remis ensemble : les copies d'UN lieu ont le même identifiant ET le même nom et la même
    /// position (elles sont écrites ensemble). Deux lieux différents qui ont par hasard le même identifiant restent deux lieux
    /// (un compte gratuit en a pu faire avant la règle « un emplacement est libre dans chaque Miff »). Un groupe garde l'ordre
    /// où il apparaît pour la première fois.
    static func groupById(_ places: [Place]) -> [PlaceGroup] {
        var order: [String] = []
        var buckets: [String: [Place]] = [:]
        for place in places {
            let key = "\(place.id)|\(place.name)|\(place.lat)|\(place.lng)"
            if buckets[key] == nil {
                order.append(key)
            }
            buckets[key, default: []].append(place)
        }
        return order.compactMap { key in
            guard let copies = buckets[key], let first = copies.first else {
                return nil
            }
            return PlaceGroup(id: first.id, copies: copies)
        }
    }
}

/// Une Miff que la personne a créée : les seules auxquelles un lieu peut être attaché (décision du ticket, §6.1).
struct OwnedMiff: Equatable, CustomStringConvertible {
    let id: String
    let name: String
    /// Quand la personne l'a rejointe (pour le créateur : quand il l'a créée), en millisecondes. Ordonne « la première créée »
    /// et « la dernière rejointe ».
    let joinedAtMillis: Int

    var description: String {
        "OwnedMiff(redacted)"
    }
}
