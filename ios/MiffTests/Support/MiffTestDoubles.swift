import Foundation
@testable import Miff

// Doubles en mémoire pour les tests du flux Miff. Aucun ne touche au réseau ni à Firebase ; les noms, codes et profils sont
// des valeurs de démonstration. Du Swift pur.

let testMiffId = "AbCdEfGhIjKlMnOpQrSt"
let testOtherMiffId = "ZyXwVuTsRqPoNmLkJiHg"
let testCode = "XTZRSP367988"

/// Une erreur Firestore telle que le SDK la donne (domaine et code).
func firestoreError(_ code: Int) -> NSError {
    NSError(domain: FirestoreErrorNumber.domain, code: code)
}

/// Le profil de démonstration copié dans la fiche d'un membre.
let testProfile = ProfileCopy(firstName: "Léa", lastName: "Martin", phone: "+33612345678", avatarJpeg: nil)

func testMiffDetails(
    id: String = testMiffId,
    name: String = "Chez nous",
    code: String = testCode,
    owner: String = "uid-owner"
) -> MiffDetails {
    MiffDetails(id: id, name: name, joinCode: code, ownerUid: owner)
}

func testMember(
    _ uid: String,
    role: MiffRole = .member,
    sharing: Bool = true
) -> MiffMember {
    MiffMember(
        uid: uid,
        firstName: "Prénom-\(uid)",
        lastName: "Nom",
        avatarJpeg: nil,
        role: role,
        sharingEnabled: sharing
    )
}

/// Ce que Firestore détient et ce qu'on lui a demandé, pour prouver l'ordre des lectures et ce qui a été écrit.
final class FakeMiffStore: MiffStore {
    /// `joinCodes` : code -> { miffId, miffName }.
    var codes: [String: [String: Any]] = [:]
    var memberships: [MembershipRef] = []
    var membershipsFailure: Error?
    var cachedMembershipsFailure: Error?
    /// Ce que répond `get miffs/{id}` par identifiant : un `MiffDetails`, ou une erreur (un refus des règles, un problème réseau).
    var miffs: [String: Any] = [:]
    var entitlement: EntitlementRead?
    var reachable = true
    var sharing: Bool?
    var profile: ProfileCopy? = testProfile
    /// Un élément par lot envoyé : nil = il passe, une erreur = il est refusé (rien n'est alors enregistré).
    var commitFailures: [Error?] = []
    /// Tourne quand un lot est envoyé, avant qu'il soit compté (pour simuler un lot parti dont la réponse se perd).
    var onCommit: () -> Void = {}

    /// Chaque appel, dans l'ordre (« code », « memberships:server », « miff:ID », « commit »...).
    private(set) var calls: [String] = []
    /// Un élément par lot qui est passé.
    private(set) var commitsAsLots: [[DocWrite]] = []

    func readJoinCode(_ code: String) async throws -> [String: Any]? {
        calls.append("code")
        return codes[code]
    }

    func readMemberships(uid: String, fromServer: Bool) async throws -> [MembershipRef] {
        calls.append(fromServer ? "memberships:server" : "memberships:cache")
        if fromServer, let failure = membershipsFailure {
            throw failure
        }
        if !fromServer, let failure = cachedMembershipsFailure {
            throw failure
        }
        return memberships
    }

    func readMiff(miffId: String, fromServer: Bool) async throws -> MiffRead {
        calls.append("miff:\(miffId)")
        guard let answer = miffs[miffId] else {
            return .missing
        }
        if let failure = answer as? Error {
            throw failure
        }
        if let details = answer as? MiffDetails {
            return .exists(details)
        }
        return .missing
    }

    func readEntitlement(uid: String) async throws -> EntitlementRead? {
        calls.append("entitlement")
        return entitlement
    }

    func readSharing(miffId: String, uid: String) async throws -> Bool? {
        calls.append("sharing")
        return sharing
    }

    func serverReachable(uid: String) async -> Bool {
        calls.append("reachable")
        return reachable
    }

    func commitWithProfile(uid: String, build: @escaping (ProfileCopy) -> [DocWrite]) async throws {
        calls.append("commit")
        guard let profile else {
            throw MissingProfileError()
        }
        try record(build(profile))
    }

    func commit(_ writes: [DocWrite]) async throws {
        calls.append("commit")
        try record(writes)
    }

    private func record(_ writes: [DocWrite]) throws {
        guard !writes.isEmpty else {
            throw InvalidWriteError()
        }
        let failure: Error? = commitFailures.isEmpty ? nil : commitFailures.removeFirst()
        onCommit()
        // Un lot refusé n'écrit RIEN : il n'est pas compté comme passé (atomicité).
        if let failure {
            throw failure
        }
        commitsAsLots.append(writes)
    }

    func clearLots() {
        commitsAsLots = []
    }

    func setCalls(_ newCalls: [String]) {
        calls = newCalls
    }
}

/// Un magasin qui renvoie tout au `FakeMiffStore`, sauf ce qu'un test remplace.
class MiffStoreByDelegation: MiffStore {
    let inner: FakeMiffStore

    init(_ inner: FakeMiffStore) {
        self.inner = inner
    }

    func readJoinCode(_ code: String) async throws -> [String: Any]? {
        try await inner.readJoinCode(code)
    }

    func readMemberships(uid: String, fromServer: Bool) async throws -> [MembershipRef] {
        try await inner.readMemberships(uid: uid, fromServer: fromServer)
    }

    func readMiff(miffId: String, fromServer: Bool) async throws -> MiffRead {
        try await inner.readMiff(miffId: miffId, fromServer: fromServer)
    }

    func readEntitlement(uid: String) async throws -> EntitlementRead? {
        try await inner.readEntitlement(uid: uid)
    }

    func readSharing(miffId: String, uid: String) async throws -> Bool? {
        try await inner.readSharing(miffId: miffId, uid: uid)
    }

    func serverReachable(uid: String) async -> Bool {
        await inner.serverReachable(uid: uid)
    }

    func commitWithProfile(uid: String, build: @escaping (ProfileCopy) -> [DocWrite]) async throws {
        try await inner.commitWithProfile(uid: uid, build: build)
    }

    func commit(_ writes: [DocWrite]) async throws {
        try await inner.commit(writes)
    }
}

final class FakeLocationProbe: LocationAccessProbe {
    var access: LocationAccess

    init(access: LocationAccess = .unknown) {
        self.access = access
    }

    func current() -> LocationAccess {
        access
    }
}

final class MemoryActiveMiffStore: ActiveMiffStore {
    var value: String?

    func chosen() -> String? {
        value
    }

    func choose(_ miffId: String) {
        value = miffId
    }

    func clear() {
        value = nil
    }
}

/// L'écoute en direct, pilotée par le test : `emit` pousse une observation, le dernier état est rejoué à qui s'abonne ensuite
/// (comme un flux avec repli). `subscriptions` compte les flux ouverts : zéro prouve qu'aucune écoute ne reste.
final class FakeMiffObserver: MiffObserver {
    private var continuations: [String: [AsyncStream<MiffObservation>.Continuation]] = [:]
    private var last: [String: MiffObservation] = [:]
    private(set) var subscriptions = 0

    func observe(miffId: String) -> AsyncStream<MiffObservation> {
        AsyncStream { continuation in
            self.continuations[miffId, default: []].append(continuation)
            self.subscriptions += 1
            if let previous = self.last[miffId] {
                continuation.yield(previous)
            }
            continuation.onTermination = { [weak self] _ in
                self?.subscriptions -= 1
            }
        }
    }

    func emit(_ observation: MiffObservation, miffId: String = testMiffId) {
        last[miffId] = observation
        for continuation in continuations[miffId] ?? [] {
            continuation.yield(observation)
        }
    }
}

/// Une porte que le test ouvre : tant qu'elle est fermée, `wait()` reste suspendu (pour arrêter une opération au milieu de sa
/// vérification et agir à ce moment-là). `arrivals` compte ceux qui sont arrivés devant elle.
final class TestGate {
    private let lock = NSLock()
    private var isOpen = false
    private var waiting: CheckedContinuation<Void, Never>?
    private var arrived = 0

    var arrivals: Int {
        lock.lock()
        defer { lock.unlock() }
        return arrived
    }

    func wait() async {
        lock.lock()
        arrived += 1
        lock.unlock()
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            lock.lock()
            if isOpen {
                lock.unlock()
                continuation.resume()
                return
            }
            waiting = continuation
            lock.unlock()
        }
    }

    func open() {
        lock.lock()
        isOpen = true
        let continuation = waiting
        waiting = nil
        lock.unlock()
        continuation?.resume()
    }
}

struct RenameCall: Equatable {
    let miffId: String
    let code: String
    let name: String
}

/// Le dépôt tel que les view models le voient.
final class FakeMiffRepository: MiffRepository {
    var createPreparation: CreatePreparation = .ready(CreateReady(premium: false, firstMiff: true))
    var createResult: CreateResult = .created(
        MiffCreation(miffId: testMiffId, joinCode: testCode, name: "Chez nous", firstMiff: true)
    )
    var joinPreparation: JoinPreparation = .ready(
        JoinReady(miffId: testMiffId, miffName: "Chez nous", code: testCode, premium: false, firstMiff: true)
    )
    var joinResult: JoinResult = .joined(miffId: testMiffId, firstMiff: true)
    var renameResult: MiffWriteResult = .done
    var sharingResult: MiffWriteResult = .done
    var myMiffsResult: MyMiffsLoad = .loaded(MyMiffs(miffs: [], lost: [], premium: false, fromCache: false))
    let observer = FakeMiffObserver()

    private(set) var calls: [String] = []
    private(set) var created: [String] = []
    private(set) var prepareJoinCodes: [String] = []
    private(set) var renamed: [RenameCall] = []
    private(set) var sharingChanges: [Bool] = []
    private(set) var cleaned: [LostMiff] = []
    var onCreate: () -> Void = {}
    var onJoin: () -> Void = {}
    var onMyMiffs: () -> Void = {}

    func prepareCreate(uid: String) async -> CreatePreparation {
        calls.append("prepareCreate")
        return createPreparation
    }

    func create(uid: String, name: String, preparation: CreateReady) async -> CreateResult {
        calls.append("create")
        created.append(name)
        onCreate()
        return createResult
    }

    func prepareJoin(uid: String, typedCode: String) async -> JoinPreparation {
        calls.append("prepareJoin")
        prepareJoinCodes.append(typedCode)
        return joinPreparation
    }

    func join(uid: String, preparation: JoinReady) async -> JoinResult {
        calls.append("join")
        onJoin()
        return joinResult
    }

    func rename(miffId: String, code: String, name: String) async -> MiffWriteResult {
        calls.append("rename")
        renamed.append(RenameCall(miffId: miffId, code: code, name: name))
        return renameResult
    }

    func setSharing(miffId: String, uid: String, enabled: Bool) async -> MiffWriteResult {
        calls.append("setSharing")
        sharingChanges.append(enabled)
        return sharingResult
    }

    func myMiffs(uid: String) async -> MyMiffsLoad {
        calls.append("myMiffs")
        onMyMiffs()
        return myMiffsResult
    }

    func cleanUpLost(uid: String, lost: LostMiff) async -> Bool {
        calls.append("cleanUp")
        cleaned.append(lost)
        return true
    }

    func observe(miffId: String) -> AsyncStream<MiffObservation> {
        observer.observe(miffId: miffId)
    }
}
