import Foundation

// CONFIDENTIALITÉ, pour ce fichier : c'est la COPIE LOCALE de ce dont la détection a besoin : les lieux des Miffs où la personne
// partage (pour choisir les 20 plus proches sans interroger le serveur), où la personne est (les zones où elle se trouve), sa dernière
// position, et le statut qui attend d'être écrit. Elle vit dans UN fichier du stockage privé de l'app, EXCLU des sauvegardes (iCloud
// et ordinateur), et elle est EFFACÉE à la déconnexion, à la suppression du compte et quand l'accès à une Miff ou le partage est
// perdu (critères POS5, VIE3). Elle ne garde ni nom ni adresse de lieu, ne va jamais dans un journal ni un événement. Chaque type qui
// porte une coordonnée a une `description` qui ne montre rien. Miroir de `TrackingLocalStore.kt` d'Android (LOC-2b).

struct LocalPlace: Codable, Equatable, CustomStringConvertible {
    let id: String
    let lat: Double
    let lng: Double
    let radiusM: Int
    let type: String

    init(fact: PlaceFact) {
        id = fact.id
        lat = fact.lat
        lng = fact.lng
        radiusM = fact.radiusM
        type = fact.type.rawValue
    }

    var fact: PlaceFact? {
        PlaceType(rawValue: type).map { PlaceFact(id: id, lat: lat, lng: lng, radiusM: radiusM, type: $0) }
    }

    var description: String {
        "LocalPlace(redacted)"
    }
}

/// Un statut qui n'a pas pu être écrit tout de suite (pas de réseau...) : le DERNIER gagne, il porte l'heure réelle de l'événement.
struct PendingStatus: Codable, Equatable, CustomStringConvertible {
    let presence: String
    let placeId: String?
    let atMillis: Int
    let setLastUpdate: Bool

    init(fact: StatusFact) {
        presence = fact.presence.rawValue
        placeId = fact.placeId
        atMillis = fact.presenceAtMillis
        setLastUpdate = fact.setLastUpdate
    }

    var fact: StatusFact? {
        PresenceValue.fromWire(presence).map {
            StatusFact(presence: $0, placeId: placeId, presenceAtMillis: atMillis, setLastUpdate: setLastUpdate)
        }
    }

    var description: String {
        "PendingStatus(redacted)"
    }
}

/// Ce que le téléphone retient d'UNE Miff où la personne partage (avec le consentement de version 3).
struct LocalMiff: Codable, Equatable, CustomStringConvertible {
    var places: [LocalPlace] = []
    /// Quand les lieux et le consentement de cette Miff ont été lus auprès du serveur pour la dernière fois (0 = jamais).
    var readAtMillis = 0
    /// La présence que ce téléphone a déduite en dernier pour cette Miff (ce qui a été écrit, ou attend) : pour n'écrire qu'un CHANGEMENT.
    var presence: String?
    var presencePlaceId: String?
    /// Le `locationAccess` écrit en dernier (pour qu'un changement d'autorisation soit écrit une seule fois).
    var access: String?
    /// La dernière position écrite dans cette Miff (garde-fou de fréquence).
    var positionLat: Double?
    var positionLng: Double?
    var positionAtMillis: Int?
    var pending: PendingStatus?

    var derived: DerivedPresence? {
        get {
            PresenceValue.fromWire(presence).map { DerivedPresence(presence: $0, placeId: presencePlaceId) }
        }
        set {
            presence = newValue?.presence.rawValue
            presencePlaceId = newValue?.placeId
        }
    }

    var written: WrittenPosition? {
        get {
            guard let lat = positionLat, let lng = positionLng, let at = positionAtMillis else {
                return nil
            }
            return WrittenPosition(lat: lat, lng: lng, atMillis: at)
        }
        set {
            positionLat = newValue?.lat
            positionLng = newValue?.lng
            positionAtMillis = newValue?.atMillis
        }
    }

    /// Plus rien de ce qui dépend de la position ou du consentement : où la personne est, sa dernière position écrite, le statut en
    /// attente. Les lieux restent (peu coûteux, aucun secret).
    mutating func resetDetection() {
        derived = nil
        written = nil
        pending = nil
    }

    var description: String {
        "LocalMiff(redacted)"
    }
}

/// La copie locale entière. `uid` est le compte à qui elle appartient : la copie d'un autre compte n'est jamais utilisée.
struct LocalState: Codable, Equatable, CustomStringConvertible {
    var uid: String
    var miffs: [String: LocalMiff] = [:]
    /// Les zones où la personne est maintenant : clé de zone -> heure d'entrée.
    var inside: [String: Int] = [:]
    /// Les zones actuellement enregistrées auprès de la surveillance de régions du système.
    var armed: Set<String> = []
    var fixLat: Double?
    var fixLng: Double?
    var fixAtMillis: Int?
    var breaker: BreakerState?
    /// Le système a été invité à signaler les changements de position significatifs.
    var updatesArmed = false
    /// Quand la liste des Miffs où la personne partage (avec la version 3) a été lue auprès du serveur (0 = jamais).
    var checkedAtMillis = 0
    /// La dernière Miff servie par un passage de statuts / de positions : le suivant commence APRÈS elle, pour qu'un disjoncteur ne
    /// ménage pas toujours les mêmes Miffs.
    var statusCursor: String?
    var positionCursor: String?

    var fix: Fix? {
        get {
            guard let lat = fixLat, let lng = fixLng, let at = fixAtMillis else {
                return nil
            }
            return Fix(lat: lat, lng: lng, measuredAtMillis: at)
        }
        set {
            fixLat = newValue?.lat
            fixLng = newValue?.lng
            fixAtMillis = newValue?.measuredAtMillis
        }
    }

    /// Change une Miff de la copie, si elle y est.
    mutating func update(_ miffId: String, _ change: (inout LocalMiff) -> Void) {
        guard var current = miffs[miffId] else {
            return
        }
        change(&current)
        miffs[miffId] = current
    }

    var description: String {
        "LocalState(redacted)"
    }
}

/// La copie locale (un double dans les tests). SYNCHRONE exprès : une fenêtre de relance a quelques secondes, et le fichier est petit.
protocol TrackingLocalStore {
    func load() -> LocalState?
    func save(_ state: LocalState)
    func clear()
}
