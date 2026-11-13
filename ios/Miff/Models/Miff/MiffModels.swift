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

/// Une ligne de la liste des proches (copie du profil gardée dans `members/{uid}`). Le numéro (FEED-5) ne sert qu'aux boutons
/// « Écrire à » et « Appeler » du Feed : jamais affiché, jamais journalisé, vérifié (E.164) avant d'arriver ici.
struct MiffMember: Equatable, CustomStringConvertible {
    let uid: String
    let firstName: String
    let lastName: String
    let avatarJpeg: Data?
    let role: MiffRole
    let sharingEnabled: Bool
    /// E.164 (« +33612345678 »), ou nil : compte antérieur au numéro obligatoire, ou valeur qui n'a pas cette forme.
    var phone: String?

    var displayName: String {
        "\(firstName) \(lastName)".trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Comment les questions du créateur nomment cette personne (« Retirer {prénom} de la Miff ? ») : le prénom, ou le nom de
    /// famille quand le prénom n'a rien de visible. Jamais écartée de la liste pour autant : le créateur doit pouvoir la retirer.
    var callName: String {
        let first = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
        return first.isEmpty ? lastName.trimmingCharacters(in: .whitespacesAndNewlines) : first
    }

    var description: String {
        "MiffMember(redacted)"
    }
}

/// Un numéro lu sur le réseau n'est gardé que s'il a la forme E.164 : rien d'autre n'arrive jamais à l'adresse d'un `sms:` ou d'un `tel:`.
extension MiffMember {
    static func phoneFromServer(_ value: Any?) -> String? {
        guard let text = value as? String, ProfileDocument.isE164(text) else {
            return nil
        }
        return text
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
    /// L'identifiant du créateur de la Miff, tel que le document de la Miff le dit (déjà lu pour construire la liste : aucune lecture de
    /// plus) ; nil quand il n'est pas connu. Il décide seulement ce que le Profil DESSINE (les boutons des lieux d'un créateur) : ce qui
    /// est permis, ce sont les règles de Firestore.
    var ownerUid: String?

    var description: String {
        "MiffSummary(redacted)"
    }
}

/// Tout ce que montre l'écran d'une Miff, gardé ensemble pour que les trois écoutes donnent une image cohérente.
struct MiffSnapshot: Equatable, CustomStringConvertible {
    let miff: MiffDetails
    let members: [MiffMember]
    let places: [Place]
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
/// 20 caractères) et sur l'écran de re-consentement.
enum MiffSharingConsent {
    /// Constante partagée avec Android : à changer EN MÊME TEMPS que le texte des clés `miff_consent3_*` (critère K8).
    /// Version « 2026-11-v03 » (LOC-4a, miroir de LOC-2a) : le texte de docs/legal/consentement-localisation-v3.md, option A
    /// (décision du PM du 2026-11-04), qui dit que Miff garde la DERNIÈRE POSITION CONNUE avec l'heure de la mesure, que les
    /// membres qui partagent aussi peuvent la voir, que Miff utilise la localisation du téléphone même app fermée et qu'elle
    /// est effacée quand la personne arrête. Une nouvelle formulation = une nouvelle version ; le numéro NN ne repart jamais
    /// de zéro et le format est `AAAA-MM-vNN` de LARGEUR FIXE (les règles comparent les versions comme des chaînes :
    /// backend/firestore.rules, isConsentVersion et isPositionConsentVersion). (« 2026-10-v1 » et « 2026-10-v2 » sont les
    /// anciens textes, qui ne disent rien d'une position.)
    static let version = "2026-11-v03"

    /// Nombre maximal de membres, créateur compris (décision QM5) : un plafond doux, montré par l'app seulement.
    static let softMemberLimit = 15

    /// Le format que les règles exigent d'une version qu'on leur demande de renouveler : année, mois, « v », deux chiffres.
    private static let fixedWidthPattern = "20[0-9]{2}-(0[1-9]|1[0-2])-v[0-9]{2}"

    /// Les deux textes écrits avant la largeur fixe (octobre 2026) : lus comme d'ANCIENNES versions, jamais réécrits.
    private static let legacyPattern = "20[0-9]{2}-(0[1-9]|1[0-2])-v[0-9]+"

    /// Ce que `isPositionConsentVersion` des règles accepte : NN de 03 à 99. Seul un tel texte couvre la dernière position.
    private static let coversPositionPattern = "20[0-9]{2}-(0[1-9]|1[0-2])-v(0[3-9]|[1-9][0-9])"

    /// Vrai si la chaîne ENTIÈRE a la forme du motif (jamais un morceau, jamais un retour à la ligne en plus).
    private static func matches(_ pattern: String, _ value: String) -> Bool {
        value.range(of: pattern, options: .regularExpression) == value.startIndex..<value.endIndex
    }

    static func isFixedWidth(_ version: String) -> Bool {
        matches(fixedWidthPattern, version)
    }

    /// Le texte de cette version couvre-t-il la dernière position connue ? (LOC-4b n'écrira une position que sous un tel texte.)
    static func coversPosition(_ version: String?) -> Bool {
        guard let version else {
            return false
        }
        return matches(coversPositionPattern, version)
    }

    /// Un membre qui partage sous un texte PLUS ANCIEN voit l'écran de re-consentement (R-LIEU-3 des règles : actif vers
    /// actif, version strictement supérieure). Une version qui n'a aucune des formes connues n'est pas touchée (les règles
    /// refuseraient le renouvellement : la comparaison se fait sur des chaînes de même forme), et une version plus récente
    /// que celle de ce build n'est jamais « renouvelée » en arrière.
    static func isRenewalDue(_ stored: String?) -> Bool {
        guard let stored, matches(fixedWidthPattern, stored) || matches(legacyPattern, stored) else {
            return false
        }
        return stored < version
    }
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
