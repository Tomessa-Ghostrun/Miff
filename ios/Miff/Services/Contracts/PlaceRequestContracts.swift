import Foundation

// Les contrats entre la logique des demandes d'ajout d'un lieu (dépôt, view models) et le monde extérieur (Firestore). Du Swift pur : la
// vraie implémentation est dans `Services/Firebase` ; les tests utilisent des doubles en mémoire.

// SÉCURITÉ, pour ce fichier : le nom, l'adresse et les coordonnées d'une demande sont sensibles. Ils ne sont jamais journalisés, jamais
// dans un événement Analytics, jamais dans un message d'erreur. Chaque échec sort d'ici sous la forme d'une `MiffFailure` (sans message,
// sans chemin) ; chaque type qui porte une demande cache son contenu dans `description`.

/// Les opérations Firestore derrière `FirestorePlaceRequestRepository`. Les lectures qui décident d'un emplacement viennent du SERVEUR (une
/// vieille copie ferait choisir le mauvais) ; les écritures sont des TRANSACTIONS (celles de `MiffStore`) : rien n'est mis en attente hors
/// ligne, donc « pas de réponse » veut toujours dire « rien d'écrit » — SAUF `enqueue`, la file du téléphone, réservée à la demande faite
/// sans réseau (critère M11).
protocol PlaceRequestStore {
    /// Les demandes de `uid` dans `miffId` (3 au plus), par une requête FILTRÉE sur `requestedBy` (les règles l'exigent). `fromServer` vrai :
    /// une réponse du serveur ou une exception (l'emplacement d'une nouvelle demande se choisit dessus) ; faux : la copie du téléphone quand
    /// le serveur est hors d'atteinte, qui garde les demandes écrites hors ligne et pas encore parties.
    func readMine(miffId: String, uid: String, fromServer: Bool) async throws -> [PlaceRequest]

    /// UNE transaction avec ces écritures (il faut le serveur : rien n'est mis en file, « pas de réponse » veut dire « rien d'écrit »).
    func commit(_ writes: [DocWrite]) async throws

    /// UN lot confié à la file du téléphone : il part quand le réseau revient (même après la fermeture de l'app) et les règles le jugent
    /// ALORS (un refus fait disparaître la demande sans un mot : risque R10 du ticket). Rend la main tout de suite.
    func enqueue(_ writes: [DocWrite])

    // MARK: - Le créateur

    /// Le créateur : toutes les demandes de `miffId`, en direct (`placeRequests` n'est lisible que du créateur). Le flux se termine avec
    /// l'erreur du SDK quand les règles refusent.
    func observeAll(miffId: String) -> AsyncThrowingStream<RequestsSnapshot, Error>

    /// Le créateur : toutes les demandes de `miffId`, une fois (la copie du téléphone quand le serveur est hors d'atteinte).
    func readAll(miffId: String) async throws -> [PlaceRequest]

    /// Le créateur : une demande, auprès du SERVEUR (nil : elle n'existe plus).
    func readOne(miffId: String, requestId: String) async throws -> PlaceRequest?

    /// Le créateur : qui a demandé. Auprès du serveur (une réponse du cache n'est jamais « n'est plus membre ») ; lève quand il ne peut pas le dire.
    func readRequester(miffId: String, uid: String) async throws -> RequesterRead
}

/// Les demandes d'une Miff telles qu'un écouteur les a données ; `fromCache` tant que le serveur n'a pas répondu.
struct RequestsSnapshot: Equatable, CustomStringConvertible {
    let requests: [PlaceRequest]
    let fromCache: Bool

    var description: String {
        "RequestsSnapshot(redacted)"
    }
}

/// La fiche de l'auteur d'une demande, lue dans `members/{uid}` de la Miff.
enum RequesterRead: Equatable, CustomStringConvertible {
    case found(RequesterCard)
    /// Le serveur a répondu et il n'y a pas de telle fiche : la personne n'est plus dans la Miff.
    case notMember

    var description: String {
        "RequesterRead(redacted)"
    }
}

/// Ce que le formulaire d'une demande doit savoir de la personne avant de s'ouvrir (tout lu auprès du SERVEUR : une limite ne se décide
/// jamais sur une copie).
struct RequestContext: Equatable, CustomStringConvertible {
    /// Le nom de la Miff pour laquelle on demande (montré dans le formulaire, la Miff est fixe).
    let miffName: String
    /// Miff+ (un abonnement actif qui n'a pas expiré) : décide seulement si la zone de danger est proposée. Les règles décident pour de vrai.
    let premium: Bool
    /// Les demandes en attente de la personne dans cette Miff (non expirées).
    let mine: [PlaceRequest]

    var description: String {
        "RequestContext(redacted)"
    }
}

enum RequestContextLoad: Equatable, CustomStringConvertible {
    case loaded(RequestContext)
    /// La Miff n'existe plus, ou la personne n'est pas un membre qui peut demander (c'est la sienne, ou l'accès est perdu).
    case unavailable
    case failed(MiffFailure)

    var description: String {
        "RequestContextLoad(redacted)"
    }
}

/// Comment s'est terminé l'envoi d'une demande. `done` seulement après que le SERVEUR a accepté le lot ; `queued` quand le téléphone l'a
/// gardé pour le retour du réseau.
enum RequestSubmitResult: Equatable, CustomStringConvertible {
    case done(PlaceRequest)
    /// Pas de réseau : le téléphone garde la demande et l'envoie dès qu'il peut (critère M11). Les règles la jugent alors.
    case queued
    /// Rien n'a été envoyé : la zone de danger est Miff+ (un membre qui ne l'est pas ne peut pas en demander une).
    case needsPremium(PaywallReason)
    /// Rien n'a été envoyé : la personne a déjà 3 demandes en attente dans cette Miff (critère M5).
    case memberLimit
    /// Le serveur a refusé : la Miff a déjà toutes les demandes qu'elle prend (10), ou une règle a dit non.
    case miffLimit
    /// La Miff n'existe plus, ou la personne n'en est plus.
    case miffUnavailable
    /// Le serveur ne prend pas encore les demandes (ses règles refusent même leur lecture) : rien n'a été envoyé, une phrase calme le dit.
    case requestsUnavailable
    case failed(MiffFailure)

    var description: String {
        "RequestSubmitResult(redacted)"
    }
}

enum RequestWithdrawResult: Equatable {
    case done
    case failed(MiffFailure)
}

/// Ce qu'une lecture des demandes a donné.
enum MineRead: Equatable, CustomStringConvertible {
    case loaded([PlaceRequest])
    case failed(MiffFailure)

    var description: String {
        "MineRead(redacted)"
    }
}

/// Ce que font les écrans avec les demandes d'ajout d'un lieu : le membre (B).
protocol PlaceRequestRepository {
    /// Lit qui est la personne pour le formulaire : Miff+ ou non, la Miff, les demandes qu'elle a déjà.
    func prepare(uid: String, miffId: String) async -> RequestContextLoad

    func submit(
        uid: String,
        miffId: String,
        draft: PlaceRequestDraft,
        context: RequestContext,
        online: Bool
    ) async -> RequestSubmitResult

    /// L'auteur reprend sa demande (critère M6).
    func withdraw(_ request: PlaceRequest) async -> RequestWithdrawResult

    /// Les demandes de la personne dans `miffId`, pour la liste : le serveur quand il répond, sinon la copie du téléphone. Les demandes
    /// expirées ne sont pas données.
    func readMine(uid: String, miffId: String) async -> MineRead

    /// La personne va quitter `miffId` (critère L1) : ses demandes en attente sont reprises, un lot chacune. Ne bloque jamais le départ :
    /// un échec est ignoré (le créateur masque les demandes de quelqu'un qui n'est plus membre et les supprime).
    func withdrawAllMine(uid: String, miffId: String) async

    // MARK: - Le créateur

    /// Les demandes de `miffId` en direct, pour son créateur (UN écouteur, 10 documents au plus). Se termine tout seul sur un refus.
    func observe(miffId: String) -> AsyncStream<RequestsObservation>

    /// Les demandes de `miffId` une fois (la liste du créateur quand aucun écouteur ne la nourrit).
    func readAll(miffId: String) async -> MineRead

    /// Qui a demandé : la fiche du membre (prénom, photo ou initiales), lue UNE fois par personne et par session par l'appelant.
    func readRequester(miffId: String, uid: String) async -> RequesterState

    /// ACCEPTER (critères C3, C5 à C8) : la formule est lue auprès du SERVEUR, les limites de la formule gratuite sont appliquées AVANT
    /// d'écrire (une zone de danger, un 4e lieu), puis UN lot crée le lieu comme demandé (mêmes règles qu'une création par le créateur),
    /// supprime la demande et pose le compteur. `knownCount` est le nombre de demandes que le créateur voit maintenant (le compteur est
    /// écrit à ce nombre réel, ce qui corrige aussi une dérive).
    func accept(uid: String, request: PlaceRequest, knownCount: Int) async -> RequestAcceptResult

    /// REFUSER (critère C4) : UN lot supprime la demande et pose le compteur. RIEN n'est écrit pour le demandeur.
    func decline(_ request: PlaceRequest, knownCount: Int) async -> RequestDeclineResult

    /// Le créateur supprime des demandes que personne ne traitera (plus de 30 jours, ou faites par quelqu'un qui a quitté la Miff), 4 par
    /// lot au plus. Au mieux : un échec est ignoré (elles restent masquées et seront rencontrées de nouveau).
    func purge(miffId: String, requestIds: [String], remaining: Int) async
}

/// Ce que dit l'écouteur du créateur : les demandes de la Miff, ou qu'on ne peut rien en dire. Une coupure réseau n'est jamais `accessLost`.
enum RequestsObservation: Equatable, CustomStringConvertible {
    case data(requests: [PlaceRequest], fromCache: Bool)
    /// Le serveur a refusé l'accès (retiré, ou la Miff n'existe plus) : rien de cette Miff ne reste.
    case accessLost
    /// Autre chose s'est mal passé : ce qui est montré reste tel quel.
    case failed

    var description: String {
        "RequestsObservation(redacted)"
    }
}

/// Comment s'est terminée l'acceptation d'une demande par le créateur (critères C3, C5 à C8).
enum RequestAcceptResult: Equatable, CustomStringConvertible {
    /// UN lot est passé : le lieu existe, la demande est supprimée, le compteur est posé.
    case done(placeId: String)
    /// Rien n'a été écrit : la formule gratuite a 3 lieux, ou la demande est une zone de danger et le créateur n'est pas Miff+. La demande
    /// reste en attente.
    case needsPremium(PaywallReason)
    /// La formule n'a pas pu être lue (pas de réseau, trop lent) : l'offre ne s'ouvre JAMAIS sur un doute.
    case subscriptionUnknown
    /// La demande n'est plus en attente (traitée sur un autre téléphone, reprise par son auteur, expirée), ou la Miff n'est plus à la personne.
    case gone
    case failed(MiffFailure)

    var description: String {
        "RequestAcceptResult(redacted)"
    }
}

enum RequestDeclineResult: Equatable {
    case done
    case failed(MiffFailure)
}
