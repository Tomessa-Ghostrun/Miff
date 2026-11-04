import Foundation

// SÉCURITÉ, pour ce fichier : noms, code, photos et numéros sont des données personnelles ou des secrets. Chaque type qui
// en porte un a une `description` qui ne montre rien, pour qu'une ligne de journal ou un rapport de plantage ne puisse
// jamais les laisser fuiter.

enum MiffRole: Equatable {
    case owner
    case member
}

/// La Miff telle que `miffs/{id}` la contient.
struct MiffDetails: Equatable, CustomStringConvertible {
    let id: String
    let name: String
    let joinCode: String
    let ownerUid: String

    var description: String {
        "MiffDetails(redacted)"
    }
}

/// Une ligne de la liste des proches (copie du profil gardée dans `members/{uid}`). Pas de numéro : il n'est pas montré ici.
struct MiffMember: Equatable, CustomStringConvertible {
    let uid: String
    let firstName: String
    let lastName: String
    let avatarJpeg: Data?
    let role: MiffRole
    let sharingEnabled: Bool

    var displayName: String {
        "\(firstName) \(lastName)".trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var description: String {
        "MiffMember(redacted)"
    }
}

/// Un lieu de la Miff, par son nom seulement (création et modification : ticket Lieux).
struct MiffPlace: Equatable, CustomStringConvertible {
    let id: String
    let name: String

    var description: String {
        "MiffPlace(redacted)"
    }

    /// Un lieu tel que le serveur le décrit : le nom est nettoyé (`MiffName.fromServer`) ; un lieu sans nom visible n'est pas
    /// montré (nil).
    static func fromServer(id: String, rawName: String) -> MiffPlace? {
        MiffName.fromServer(rawName).map { MiffPlace(id: id, name: $0) }
    }
}

/// Un document de `users/{uid}/memberships` : `key` est l'identifiant du document, `miffId` le CHAMP qui dit quelle Miff.
struct MembershipRef: Equatable {
    let key: String
    let miffId: String
}

/// Une Miff de la personne, pour la pastille de l'accueil.
struct MiffSummary: Equatable, CustomStringConvertible {
    let id: String
    let name: String

    var description: String {
        "MiffSummary(redacted)"
    }
}

/// Tout ce que montre l'écran d'une Miff, gardé ensemble pour que les trois écoutes donnent une image cohérente.
struct MiffSnapshot: Equatable, CustomStringConvertible {
    let miff: MiffDetails
    let members: [MiffMember]
    let places: [MiffPlace]
    /// Vrai tant que ce qui est montré vient seulement du cache du téléphone (pas encore de réponse du serveur, ou hors ligne).
    let fromCache: Bool

    var description: String {
        "MiffSnapshot(redacted)"
    }
}

/// Ce que l'observation d'une Miff dit à l'écran. Un problème de réseau n'est JAMAIS `accessLost` (ticket §5.7, critère D3).
enum MiffObservation: Equatable {
    case loading
    case data(MiffSnapshot)
    /// Le serveur a répondu « non » (retrait) ou « cette Miff n'existe pas » (suppression).
    case accessLost
    /// Autre chose a échoué (pas une coupure réseau : les écoutes attendent le réseau toutes seules).
    case failed
}

/// Version du texte montré avant « Rejoindre et partager » / « Créer et partager » / « Reprendre le partage » (règles : 1 à
/// 20 caractères).
enum MiffSharingConsent {
    /// Constante partagée avec Android : à changer EN MÊME TEMPS que le texte des clés `miff_consent_*` (critère K8).
    /// Version « 2026-10-v2 » : la phrase du PM, la courte liste de ce que les membres voient (prénom, nom, photo, numéro),
    /// la phrase sur les lieux (choisis par le créateur, visibles de tous), et le rappel sur la permission du téléphone. Une
    /// nouvelle formulation = une nouvelle version. (« 2026-10-v1 » était le même texte sans la phrase sur les lieux.)
    static let version = "2026-10-v2"

    /// Nombre maximal de membres, créateur compris (décision QM5) : un plafond doux, montré par l'app seulement.
    static let softMemberLimit = 15
}

/// Ce que la permission de localisation de l'appareil dit, écrit dans le premier statut (ticket §5.4, étape 7).
enum LocationAccess: String {
    case background
    case foreground
    case denied
    case unknown
}

/// Les phrases calmes qui peuvent terminer une action (jamais « quota », jamais un code ni un mot technique).
enum ActionMessage: Equatable {
    /// « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. »
    case offline
    /// « Ça n'a pas fonctionné. Réessaie dans un instant. »
    case generic
    /// « Pour l'instant, ce compte peut faire partie d'une seule Miff à la fois. » Un compte gratuit qui a déjà une Miff : le
    /// ticket Premium décidera de ce qui est proposé, ceci dit seulement honnêtement ce qui est.
    case oneMiff
    /// Le profil (ou son numéro) manque : la personne retourne à l'étape profil.
    case noProfile

    init(failure: MiffFailure) {
        switch failure {
        case .offline:
            self = .offline
        case .noProfile:
            self = .noProfile
        case .refused, .unknown:
            self = .generic
        }
    }
}
