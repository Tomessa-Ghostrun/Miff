import Foundation

// Les contrats entre la logique des Miffs (dépôt, annuaire, view models) et le monde extérieur (Firestore, système). Du
// Swift pur : les vraies implémentations sont dans `Services/Firebase` ; les tests utilisent des doubles en mémoire.

// SÉCURITÉ, pour ce fichier : codes, noms et profils sont des secrets ou des données personnelles. Ils ne sont jamais
// journalisés, jamais dans un événement Analytics, jamais dans un message d'erreur. Chaque échec sort d'ici sous la forme
// d'une `MiffFailure` (sans message, sans code).

/// Ce qu'a dit `get miffs/{id}`. Un refus des règles est une erreur (`permissionDenied`), pas une valeur.
enum MiffRead: Equatable {
    case exists(MiffDetails)
    /// Le serveur a répondu, et cette Miff n'existe pas (supprimée).
    case missing
}

/// `entitlements/{uid}` : seulement ce dont le client a besoin pour choisir la clé de l'index.
struct EntitlementRead: Equatable {
    let active: Bool
    /// Date d'expiration, en millisecondes depuis 1970.
    let expiresAtMillis: Int?
}

/// Les opérations Firestore derrière `FirestoreMiffRepository`. Les écritures sont des TRANSACTIONS (comme celles du
/// profil) : un lot simple fait sans réseau est mis en attente et part plus tard, tout seul ; une transaction n'existe
/// qu'en ligne, donc « pas de confirmation » veut toujours dire « rien d'écrit » (ticket Miff §5.11, risque R4). Derrière un
/// protocole pour que la logique soit testée avec un double.
protocol MiffStore {
    /// `joinCodes/{code}` : `miffId` et `miffName`, ou nil s'il n'y a pas de tel code.
    func readJoinCode(_ code: String) async throws -> [String: Any]?

    /// `users/{uid}/memberships`, lu auprès du SERVEUR quand `fromServer` (échoue hors ligne), sinon où Firestore l'a.
    func readMemberships(uid: String, fromServer: Bool) async throws -> [MembershipRef]

    func readMiff(miffId: String, fromServer: Bool) async throws -> MiffRead

    func readEntitlement(uid: String) async throws -> EntitlementRead?

    /// Ma fiche dit-elle que je partage ? Lu auprès du SERVEUR (pour vérifier un lot dont la réponse s'est perdue) ; nil
    /// s'il n'y a pas de fiche.
    func readSharing(miffId: String, uid: String) async throws -> Bool?

    /// Une lecture que seule la personne peut faire et qui réussit toujours quand le serveur est joignable et App Check en
    /// ordre.
    func serverReachable(uid: String) async -> Bool

    /// UNE transaction : le profil est lu d'abord, `build` fait le lot à partir de lui (Firestore peut le rappeler s'il
    /// réessaie). Un lot vide est refusé avant envoi.
    func commitWithProfile(uid: String, build: @escaping (ProfileCopy) -> [DocWrite]) async throws

    /// UNE transaction avec ces écritures.
    func commit(_ writes: [DocWrite]) async throws
}

/// L'écoute en direct d'une Miff (critères G5, D1, D7). L'écoute s'arrête quand le flux est abandonné.
protocol MiffObserver {
    func observe(miffId: String) -> AsyncStream<MiffObservation>
}

/// La permission de localisation de l'appareil, telle qu'elle est MAINTENANT, écrite dans le premier statut (ticket Miff
/// §5.4, étape 7).
protocol LocationAccessProbe {
    func current() -> LocationAccess
}

/// La Miff que montre l'accueil quand il y en a plusieurs (Premium) : une préférence locale, effacée à la déconnexion (Feed
/// F8). Pas un secret, mais jamais partagée.
protocol ActiveMiffStore {
    func chosen() -> String?
    func choose(_ miffId: String)
    func clear()
}

/// Comment s'est terminée une écriture du ticket Miff. `done` seulement après que le SERVEUR a accepté le lot.
enum MiffWriteResult: Equatable {
    case done
    case failed(MiffFailure)
}

/// La première étape de « Créer » : la Miff peut-elle être écrite, et sous quelle clé d'index.
struct CreateReady: Equatable {
    /// Un compte Premium met son index sous l'identifiant de la Miff ; un compte gratuit a l'unique emplacement « free ».
    let premium: Bool
    /// Vrai quand la personne n'a pas encore d'autre Miff (paramètre Analytics `is_first_miff`).
    let firstMiff: Bool
}

enum CreatePreparation: Equatable {
    case ready(CreateReady)
    /// Un compte gratuit qui a déjà une Miff : le ticket Premium décide de ce que la personne voit (rien d'inventé ici).
    case needsPremium
    case failed(MiffFailure)
}

/// La Miff qui vient d'être créée, pour l'écran « Ta Miff est prête ».
struct MiffCreation: Equatable, CustomStringConvertible {
    let miffId: String
    let joinCode: String
    let name: String
    let firstMiff: Bool

    var description: String {
        "MiffCreation(redacted)"
    }
}

enum CreateResult: Equatable {
    case created(MiffCreation)
    case needsPremium
    case failed(MiffFailure)
}

/// Ce que le code tapé par la personne donne (ticket §5.4, étapes 3 à 5).
struct JoinReady: Equatable, CustomStringConvertible {
    let miffId: String
    let miffName: String
    let code: String
    let premium: Bool
    let firstMiff: Bool

    var description: String {
        "JoinReady(redacted)"
    }
}

enum JoinPreparation: Equatable {
    case ready(JoinReady)
    /// Un seul message quelle que soit la cause (faux, renouvelé, supprimé) : jamais « ton ancien code a été remplacé »
    /// (ticket §5.4, étape 3).
    case notFound
    case alreadyMember(miffId: String, miffName: String)
    case needsPremium
    case failed(MiffFailure)
}

enum JoinResult: Equatable {
    case joined(miffId: String, firstMiff: Bool)
    /// Le code a été renouvelé ou la Miff supprimée entre la lecture et la confirmation : même message que « introuvable ».
    case notFound
    case needsPremium
    case failed(MiffFailure)
}

/// Une Miff dont l'index existe encore mais dont le serveur dit que la personne n'a plus accès : à nettoyer.
struct LostMiff: Equatable, CustomStringConvertible {
    let ref: MembershipRef
    /// Le nom, si le téléphone le connaissait encore (il garde le message humain : « Tu n'as plus accès à la Miff « X » »).
    let name: String?

    var description: String {
        "LostMiff(redacted)"
    }
}

/// Les Miffs de la personne, telles que l'accueil les montre.
struct MyMiffs: Equatable, CustomStringConvertible {
    let miffs: [MiffSummary]
    /// Les Miffs auxquelles le serveur dit que la personne n'a plus accès : à nettoyer (l'index est encore là).
    let lost: [LostMiff]
    let premium: Bool
    /// Vrai quand la réponse vient du cache du téléphone parce que le serveur n'a pas pu être joint.
    let fromCache: Bool

    var description: String {
        "MyMiffs(redacted)"
    }
}

enum MyMiffsLoad: Equatable {
    case loaded(MyMiffs)
    case unavailable
}

/// Tout ce que les tickets Miff font avec Firestore, tel que les écrans en ont besoin (ticket Miff §5.4, §5.7, §5.13) :
/// l'ordre des vérifications pour entrer, les lots, le nettoyage après une perte d'accès, la vue en direct d'une Miff.
protocol MiffRepository {
    func prepareCreate(uid: String) async -> CreatePreparation
    func create(uid: String, name: String, preparation: CreateReady) async -> CreateResult
    func prepareJoin(uid: String, typedCode: String) async -> JoinPreparation
    func join(uid: String, preparation: JoinReady) async -> JoinResult
    func rename(miffId: String, code: String, name: String) async -> MiffWriteResult
    func setSharing(miffId: String, uid: String, enabled: Bool) async -> MiffWriteResult
    func myMiffs(uid: String) async -> MyMiffsLoad

    /// Le lot « Quitter », sinon l'index seul (ticket §5.7). Vrai quand l'index a disparu.
    func cleanUpLost(uid: String, lost: LostMiff) async -> Bool

    func observe(miffId: String) -> AsyncStream<MiffObservation>
}
