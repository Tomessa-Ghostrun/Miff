import Foundation
@testable import Miff

// Doubles en mémoire pour les tests des demandes d'ajout d'un lieu (LIEU-REQ). Aucun ne touche au réseau ni à Firebase ; les noms, adresses et
// coordonnées sont des valeurs de démonstration. L'identifiant d'une personne n'a que des lettres et des chiffres (la forme exigée d'un uid).

let testMemberUid = "uidmember1"
let testCreatorUid = "uidcreator1"

/// Une demande de démonstration : `{uid}_{emplacement}` dans `testMiffId`, datée de `createdAtMillis` (nil : pas encore de date serveur).
func testRequest(
    slot: Int = 1,
    uid: String = testMemberUid,
    miffId: String = testMiffId,
    name: String = "Club de judo",
    type: PlaceType = .trusted,
    category: PlaceCategory? = .sport,
    createdAtMillis: Int? = 1_000_000
) -> PlaceRequest {
    PlaceRequest(
        id: PlaceRequestLimits.idOf(uid: uid, slot: slot),
        miffId: miffId,
        requestedBy: uid,
        name: name,
        type: type,
        address: "12 rue des Écoles, 21000 Dijon, France",
        lat: 47.32207,
        lng: 5.04148,
        radiusM: 100,
        category: category,
        createdAtMillis: createdAtMillis
    )
}

/// Ce que remplit le formulaire d'une demande.
func testRequestDraft(
    name: String = "Club de judo",
    type: PlaceType = .trusted,
    category: PlaceCategory = .sport,
    radiusM: Int = 100
) -> PlaceRequestDraft {
    PlaceRequestDraft(
        name: name,
        address: "12 rue des Écoles, 21000 Dijon, France",
        lat: 47.322071,
        lng: 5.041484,
        radiusM: radiusM,
        type: type,
        category: category
    )
}

/// Les champs d'un document de demande tel que Firestore les rend (pour `PlaceRequest.fromServer`).
func requestFields(
    uid: String = testMemberUid,
    name: String = "Club de judo",
    type: String = "trusted",
    category: String? = "sport",
    radiusM: Any = Int64(100)
) -> [String: Any] {
    var fields: [String: Any] = [
        "requestedBy": uid,
        "name": name,
        "type": type,
        "address": "12 rue des Écoles, 21000 Dijon, France",
        "lat": 47.32207,
        "lng": 5.04148,
        "radiusM": radiusM
    ]
    if let category {
        fields["category"] = category
    }
    return fields
}

/// Ce que Firestore détient pour les demandes d'une personne, et ce qu'on lui a demandé (lectures, lots, file du téléphone).
final class FakePlaceRequestStore: PlaceRequestStore {
    /// Les demandes que le serveur détient (toutes personnes et toutes Miffs confondues ; `readMine` filtre).
    var requests: [PlaceRequest] = []
    var readFailure: Error?
    /// Ce qu'a dit le serveur pour chaque lot : nil = accepté, une erreur = le lot a échoué (rien n'est alors écrit).
    var commitFailures: [Error?] = []
    /// Tourne quand un lot part, avant qu'il compte (pour simuler un lot parti dont la réponse se perd, ou un autre téléphone).
    var onCommit: ([DocWrite]) -> Void = { _ in }

    private(set) var commits: [[DocWrite]] = []
    private(set) var enqueued: [[DocWrite]] = []
    private(set) var reads: [String] = []

    func readMine(miffId: String, uid: String, fromServer: Bool) async throws -> [PlaceRequest] {
        reads.append(fromServer ? "server" : "default")
        if let readFailure {
            throw readFailure
        }
        return requests.filter { $0.miffId == miffId && $0.requestedBy == uid }
    }

    func commit(_ writes: [DocWrite]) async throws {
        let failure: Error? = commitFailures.isEmpty ? nil : commitFailures.removeFirst()
        onCommit(writes)
        if let failure {
            throw failure
        }
        commits.append(writes)
        for write in writes where write.kind == .delete && write.path.contains("/placeRequests/") {
            let id = String(write.path.split(separator: "/").last ?? "")
            requests.removeAll { $0.id == id }
        }
    }

    func enqueue(_ writes: [DocWrite]) {
        enqueued.append(writes)
    }

    // MARK: - Le créateur

    /// La fiche de chaque personne qui a demandé, par uid ; absente = la personne n'est plus dans la Miff (le serveur le dit).
    var requesters: [String: RequesterCard] = [:]
    var requesterFailure: Error?
    private(set) var requesterReads: [String] = []
    private(set) var observers: [AsyncThrowingStream<RequestsSnapshot, Error>.Continuation] = []

    func observeAll(miffId: String) -> AsyncThrowingStream<RequestsSnapshot, Error> {
        AsyncThrowingStream { continuation in
            self.observers.append(continuation)
        }
    }

    /// L'écouteur donne ces demandes.
    func emit(_ list: [PlaceRequest], fromCache: Bool = false) {
        for observer in observers {
            observer.yield(RequestsSnapshot(requests: list, fromCache: fromCache))
        }
    }

    /// Le serveur refuse l'écouteur (ou une coupure) : le flux se termine avec cette erreur.
    func failObservation(_ error: Error) {
        for observer in observers {
            observer.finish(throwing: error)
        }
    }

    func readAll(miffId: String) async throws -> [PlaceRequest] {
        reads.append("all")
        if let readFailure {
            throw readFailure
        }
        return requests.filter { $0.miffId == miffId }
    }

    func readOne(miffId: String, requestId: String) async throws -> PlaceRequest? {
        reads.append("one")
        if let readFailure {
            throw readFailure
        }
        return requests.first { $0.miffId == miffId && $0.id == requestId }
    }

    func readRequester(miffId: String, uid: String) async throws -> RequesterRead {
        requesterReads.append(uid)
        if let requesterFailure {
            throw requesterFailure
        }
        if let card = requesters[uid] {
            return .found(card)
        }
        return .notMember
    }
}

/// Le dépôt des demandes tel que les view models le voient : ce que disent ses lectures, et chaque appel reçu.
final class FakePlaceRequestRepository: PlaceRequestRepository {
    var prepareResult = RequestContextLoad.unavailable
    var submitResult = RequestSubmitResult.done(testRequest())
    var withdrawResult = RequestWithdrawResult.done
    var mineResult = MineRead.loaded([])
    /// Tenue fermée : une opération « en route » (pour prouver qu'un second appui ne fait rien).
    var gate: TestGate?
    private(set) var submitted: [PlaceRequestDraft] = []
    private(set) var submitOnline: [Bool] = []
    private(set) var withdrawn: [PlaceRequest] = []
    private(set) var readMineCalls: [String] = []
    private(set) var withdrawAllCalls: [String] = []

    func prepare(uid: String, miffId: String) async -> RequestContextLoad {
        prepareResult
    }

    func submit(
        uid: String,
        miffId: String,
        draft: PlaceRequestDraft,
        context: RequestContext,
        online: Bool
    ) async -> RequestSubmitResult {
        submitted.append(draft)
        submitOnline.append(online)
        await gate?.wait()
        return submitResult
    }

    func withdraw(_ request: PlaceRequest) async -> RequestWithdrawResult {
        withdrawn.append(request)
        await gate?.wait()
        return withdrawResult
    }

    func readMine(uid: String, miffId: String) async -> MineRead {
        readMineCalls.append(miffId)
        await gate?.wait()
        return mineResult
    }

    func withdrawAllMine(uid: String, miffId: String) async {
        withdrawAllCalls.append(miffId)
    }

    // MARK: - Le créateur

    var readAllResult = MineRead.loaded([])
    var requesterResults: [String: RequesterState] = [:]
    var acceptResult = RequestAcceptResult.done(placeId: "p1")
    var declineResult = RequestDeclineResult.done
    private(set) var readAllCalls: [String] = []
    private(set) var requesterCalls: [String] = []
    private(set) var accepted: [PlaceRequest] = []
    private(set) var acceptedCounts: [Int] = []
    private(set) var declined: [PlaceRequest] = []
    private(set) var declinedCounts: [Int] = []
    private(set) var purged: [[String]] = []
    private(set) var purgedRemaining: [Int] = []
    private(set) var observedMiffs: [String] = []
    private var continuations: [AsyncStream<RequestsObservation>.Continuation] = []

    func observe(miffId: String) -> AsyncStream<RequestsObservation> {
        observedMiffs.append(miffId)
        return AsyncStream { continuation in
            self.continuations.append(continuation)
        }
    }

    /// L'écouteur dit ceci à tous ceux qui l'écoutent.
    func emit(_ observation: RequestsObservation) {
        for continuation in continuations {
            continuation.yield(observation)
        }
    }

    func readAll(miffId: String) async -> MineRead {
        readAllCalls.append(miffId)
        return readAllResult
    }

    func readRequester(miffId: String, uid: String) async -> RequesterState {
        requesterCalls.append(uid)
        return requesterResults[uid] ?? .unknown
    }

    func accept(uid: String, request: PlaceRequest, knownCount: Int) async -> RequestAcceptResult {
        accepted.append(request)
        acceptedCounts.append(knownCount)
        await gate?.wait()
        return acceptResult
    }

    func decline(_ request: PlaceRequest, knownCount: Int) async -> RequestDeclineResult {
        declined.append(request)
        declinedCounts.append(knownCount)
        await gate?.wait()
        return declineResult
    }

    func purge(miffId: String, requestIds: [String], remaining: Int) async {
        purged.append(requestIds)
        purgedRemaining.append(remaining)
    }
}
