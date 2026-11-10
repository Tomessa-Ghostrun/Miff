import Foundation

// Les contrats entre la logique des lieux (dépôt, view models) et le monde extérieur (Firestore). Du Swift pur : la vraie
// implémentation est dans `Services/Firebase` ; les tests utilisent des doubles en mémoire.

// SÉCURITÉ, pour ce fichier : le nom, l'adresse et les coordonnées d'un lieu sont sensibles. Ils ne sont jamais journalisés,
// jamais dans un événement Analytics, jamais dans un message d'erreur. Chaque échec sort d'ici sous la forme d'une
// `MiffFailure` (sans message, sans chemin).

/// Une ligne de `users/{uid}/memberships` : quelle Miff, et quand la personne l'a rejointe (0 quand le document n'a pas de date).
struct PlaceMembership: Equatable, CustomStringConvertible {
    let miffId: String
    let joinedAtMillis: Int

    var description: String {
        "PlaceMembership(redacted)"
    }
}

/// Les opérations Firestore derrière `FirestorePlaceRepository`. Les lectures qui décident d'une limite viennent du SERVEUR (une
/// vieille copie ferait choisir le mauvais emplacement) ; les écritures sont des TRANSACTIONS (celles de `MiffStore`) : rien
/// n'est mis en attente hors ligne, donc « pas de réponse » veut toujours dire « rien d'écrit » (ticket §6.1, PL9). Derrière
/// un protocole pour que la logique soit testée avec un double.
protocol PlaceStore {
    /// `entitlements/{uid}`, lu auprès du SERVEUR (lève hors ligne : jamais « pas Miff+ » par défaut).
    func readEntitlement(uid: String) async throws -> EntitlementRead?

    /// `users/{uid}/memberships`, lu auprès du SERVEUR.
    func readMemberships(uid: String) async throws -> [PlaceMembership]

    /// `get miffs/{id}`, lu auprès du SERVEUR.
    func readMiff(miffId: String) async throws -> MiffRead

    /// `miffs/{id}/places`, lu auprès du SERVEUR ; un document qui ne peut pas être un lieu de cette app est laissé de côté.
    func readPlaces(miffId: String) async throws -> [Place]

    /// UNE transaction avec ces écritures (10 documents au plus ici, voir `PlaceWrites.change`).
    func commit(_ writes: [DocWrite]) async throws
}

/// Ce que le formulaire doit savoir de la personne avant de s'ouvrir (tout lu auprès du SERVEUR : une limite ne se décide jamais
/// sur une copie).
struct PlaceContext: Equatable, CustomStringConvertible {
    /// Miff+ (un abonnement actif qui n'a pas expiré). Les règles décident pour de vrai : ceci choisit seulement ce qui est montré.
    let premium: Bool
    /// Les Miffs que la personne a CRÉÉES, la première créée d'abord. Seules celles-là peuvent porter un lieu (ticket §6.1).
    let ownedMiffs: [OwnedMiff]
    /// La Miff rejointe le plus récemment, créée par la personne ou non (le défaut du formulaire, décision Q4).
    let lastJoinedMiffId: String?
    /// Tous les lieux des Miffs que la personne a créées : une copie par Miff.
    let places: [Place]

    var groups: [PlaceGroup] {
        PlaceGroup.groupById(places)
    }

    /// Les identifiants pris, Miff par Miff.
    var idsByMiff: [String: Set<String>] {
        var result: [String: Set<String>] = [:]
        for miff in ownedMiffs {
            result[miff.id] = Set(places.filter { $0.miffId == miff.id }.map { $0.id })
        }
        return result
    }

    /// Combien de lieux différents la personne a (un lieu dans 3 Miffs compte pour un) : ce que limite la formule gratuite.
    var distinctPlaces: Int {
        groups.count
    }

    var description: String {
        "PlaceContext(redacted)"
    }
}

enum PlaceContextLoad: Equatable {
    case loaded(PlaceContext)
    case failed(MiffFailure)
}

/// Comment s'est terminée une écriture de lieu. `done` seulement après que le SERVEUR a accepté le lot (critère PL9).
enum PlaceWriteResult: Equatable {
    case done(placeId: String)
    /// Rien n'a été envoyé : la formule gratuite a 3 lieux et pas de zone de danger (critères LIM1, LIM3).
    case needsPremium(PaywallReason)
    /// Une des Miffs choisies n'existe plus, ou n'est plus celle de la personne (cas limite 14 du ticket) : tout le lot a été refusé.
    case miffUnavailable
    /// Le lieu a été supprimé depuis un autre téléphone pendant qu'il était ouvert.
    case placeGone
    /// L'emplacement choisi pour ce lieu est déjà pris dans une des Miffs (un autre téléphone l'a écrit depuis l'ouverture du
    /// formulaire) : rien n'a été écrit, la personne rouvre le formulaire.
    case idTaken
    case failed(MiffFailure)
}

/// Ce que font les écrans avec les lieux. La lecture de la liste est en direct : voir `MiffRepository.observe`.
protocol PlaceRepository {
    /// Lit qui est la personne pour le formulaire : Miff+ ou non, les Miffs qu'elle a créées, les lieux qu'elle a déjà.
    func prepare(uid: String) async -> PlaceContextLoad

    func create(uid: String, draft: PlaceDraft, context: PlaceContext) async -> PlaceWriteResult

    /// Change `original` (toutes ses copies) en `draft` : les champs, le type, et les Miffs auxquelles il est attaché.
    func update(uid: String, original: PlaceGroup, draft: PlaceDraft, context: PlaceContext) async -> PlaceWriteResult

    /// Supprime le lieu de TOUTES les Miffs où il est, en UN lot (décision Q4). Sans écran, exprès : le ticket Premium (fin d'un
    /// abonnement) l'appelle sans écran (critère PL8). `miffIds` sont les Miffs de la personne qui en détiennent une copie.
    func delete(placeId: String, miffIds: [String]) async -> PlaceWriteResult
}
