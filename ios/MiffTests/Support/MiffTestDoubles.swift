import Foundation
@testable import Miff

// Doubles en mémoire pour les tests du flux Miff. Aucun ne touche au réseau ni à Firebase ; les noms, codes et profils sont
// des valeurs de démonstration. Du Swift pur.

let testMiffId = "AbCdEfGhIjKlMnOpQrSt"
let testOtherMiffId = "ZyXwVuTsRqPoNmLkJiHg"
let testCode = "XTZRSP367988"
/// L'identifiant de la session d'appareil de démonstration (SESS-3) : 16 à 64 caractères `A-Za-z0-9_-`.
let testDeviceId = "test-device-id-0001"

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

    // MARK: - MIFF-4

    /// Les identifiants des fiches de la Miff, tels que le SERVEUR les liste.
    var memberIds: [String] = []
    var membersFailure: Error?
    var placeIds: [String] = []
    var placesFailure: Error?
    /// Le document du code ACTUEL existe-t-il (ce que lit la transaction d'un lot) ?
    var codeDocumentExists = true
    /// Vrai : un lot qui passe change aussi ce que le double répond (un membre retiré disparaît de la liste, un code renouvelé
    /// remplace l'ancien, une Miff supprimée est refusée à tous), pour suivre une procédure entière.
    var simulate = false
    /// Le numéro (à partir de 0) du lot dont la RÉPONSE se perd : il passe, puis l'appel échoue comme si le réseau avait coupé.
    var loseAnswerAt = -1
    private var commitCount = 0

    func readMemberIds(miffId: String) async throws -> [String] {
        calls.append("memberIds")
        if let membersFailure {
            throw membersFailure
        }
        return memberIds
    }

    func readPlaceIds(miffId: String) async throws -> [String] {
        calls.append("placeIds")
        if let placesFailure {
            throw placesFailure
        }
        return placeIds
    }

    func commitWithMiff(miffId: String, build: @escaping (MiffTxView) -> [DocWrite]) async throws {
        calls.append("commitWithMiff")
        let answer = miffs[miffId]
        if let failure = answer as? Error {
            throw failure
        }
        guard let details = answer as? MiffDetails else {
            throw MiffGoneError()
        }
        try record(build(MiffTxView(details: details, codeDocumentExists: codeDocumentExists)))
    }

    private func record(_ writes: [DocWrite]) throws {
        // Comme le vrai magasin : un lot vide, ou un statut / une position sans l'identifiant de session (SESS-3), n'est jamais envoyé.
        guard SessionWriteGuard.isSendable(writes) else {
            throw InvalidWriteError()
        }
        let number = commitCount
        commitCount += 1
        let failure: Error? = commitFailures.isEmpty ? nil : commitFailures.removeFirst()
        onCommit()
        if number == loseAnswerAt {
            commitsAsLots.append(writes)
            applyEffects(writes)
            throw firestoreError(FirestoreErrorNumber.unavailable)
        }
        // Un lot refusé n'écrit RIEN : il n'est pas compté comme passé (atomicité).
        if let failure {
            throw failure
        }
        commitsAsLots.append(writes)
        applyEffects(writes)
    }

    private func applyEffects(_ writes: [DocWrite]) {
        guard simulate else {
            return
        }
        for write in writes {
            let parts = write.path.split(separator: "/").map(String.init)
            switch (parts.count, write.kind) {
            case (4, .delete):
                applyDeletion(parts)
            case (2, .update):
                renewCode(parts, write)
            case (2, .delete):
                // Une Miff supprimée est refusée à qui n'en est plus membre : ce que répond le serveur.
                miffs[parts[1]] = firestoreError(FirestoreErrorNumber.permissionDenied)
            default:
                break
            }
        }
    }

    private func applyDeletion(_ parts: [String]) {
        switch (parts[0], parts[2]) {
        case ("miffs", "members"):
            memberIds.removeAll { $0 == parts[3] }
        case ("miffs", "places"):
            placeIds.removeAll { $0 == parts[3] }
        case ("users", "memberships"):
            memberships.removeAll { $0.key == parts[3] }
        default:
            break
        }
    }

    private func renewCode(_ parts: [String], _ write: DocWrite) {
        guard let old = miffs[parts[1]] as? MiffDetails, case .string(let code)? = write.data["joinCode"] else {
            return
        }
        miffs[parts[1]] = MiffDetails(id: old.id, name: old.name, joinCode: code, ownerUid: old.ownerUid)
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

    func readMemberIds(miffId: String) async throws -> [String] {
        try await inner.readMemberIds(miffId: miffId)
    }

    func readPlaceIds(miffId: String) async throws -> [String] {
        try await inner.readPlaceIds(miffId: miffId)
    }

    func commitWithMiff(miffId: String, build: @escaping (MiffTxView) -> [DocWrite]) async throws {
        try await inner.commitWithMiff(miffId: miffId, build: build)
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
