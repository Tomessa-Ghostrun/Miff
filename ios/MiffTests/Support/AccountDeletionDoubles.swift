import Foundation
@testable import Miff

// Doubles en mémoire de la suppression d'un compte (AUTH-5c). Aucun ne touche au réseau ni à Firebase ; les identifiants sont des
// valeurs de démonstration. Du Swift pur.

let testAccountUid = "uid-1"
let testJoinedKey = "joined-key-00001"
let testOwnedKey = "owned-key-000001"
let testJoinedMiffId = "JoinedMiffId0000001"
let testOwnedMiffId = "OwnedMiffId00000001"

func testRef(key: String = testJoinedKey, miffId: String = testJoinedMiffId) -> MembershipRef {
    MembershipRef(key: key, miffId: miffId)
}

func testOwned(id: String = testOwnedMiffId, name: String = "Chez nous", others: Int = 2) -> AccountOwnedMiff {
    AccountOwnedMiff(miffId: id, name: name, otherMembers: others)
}

func testInventory(
    joined: [MembershipRef] = [],
    owned: [AccountOwnedMiff] = [],
    stale: [MembershipRef] = []
) -> MiffsInventory {
    MiffsInventory(joined: joined, owned: owned, stale: stale)
}

/// Le marqueur local en mémoire : chaque écriture et chaque effacement sont notés dans la chronologie partagée.
final class MemoryDeletionMarker: AccountDeletionMarker {
    var mark: AccountDeletionMark?
    private(set) var writes: [AccountDeletionMark] = []
    private(set) var clears = 0
    private let timeline: Timeline

    init(timeline: Timeline = Timeline(), mark: AccountDeletionMark? = nil) {
        self.timeline = timeline
        self.mark = mark
    }

    func read() -> AccountDeletionMark? {
        mark
    }

    /// Le disque refuse d'écrire le marqueur (disque plein, stockage refusé).
    var writeFails = false

    func write(_ newMark: AccountDeletionMark) -> Bool {
        if writeFails {
            return false
        }
        mark = newMark
        writes.append(newMark)
        timeline.add("markerWrite")
        return true
    }

    func clear() {
        mark = nil
        clears += 1
        timeline.add("markerClear")
    }
}

/// `users/{uid}/memberships`, les appareils et le profil. Chaque appel est noté dans la chronologie ; `failures` dit quelle opération
/// échoue (avec quelle erreur de Firestore).
final class FakeAccountDataStore: AccountDataStore {
    private let timeline: Timeline
    /// Ce que répond le décompte des index à chaque appel (le dernier se répète).
    var counts: [Int] = [0]
    var devices: [String] = []
    /// L'erreur d'une opération, par nom : « count », « devices », « device », « profile ».
    var failures: [String: Error] = [:]
    private(set) var deletedDevices: [String] = []
    private(set) var profileDeleted = false

    init(timeline: Timeline = Timeline()) {
        self.timeline = timeline
    }

    func membershipCount(uid: String) async throws -> Int {
        timeline.add("count")
        if let failure = failures["count"] {
            throw failure
        }
        return counts.count > 1 ? counts.removeFirst() : (counts.first ?? 0)
    }

    func deviceIds(uid: String) async throws -> [String] {
        timeline.add("devices")
        if let failure = failures["devices"] {
            throw failure
        }
        return devices
    }

    func deleteDevice(uid: String, deviceId: String) async throws {
        timeline.add("device:\(deviceId)")
        if let failure = failures["device"] {
            throw failure
        }
        deletedDevices.append(deviceId)
    }

    func deleteProfile(uid: String) async throws {
        timeline.add("profile")
        if let failure = failures["profile"] {
            throw failure
        }
        profileDeleted = true
    }
}

/// Les Miffs de la suppression : l'inventaire (une réponse par appel, la dernière se répète) et le résultat de chaque Miff.
final class FakeAccountMiffs: AccountMiffs {
    private let timeline: Timeline
    var inventories: [MiffsRead] = [.read(testInventory())]
    /// Le résultat d'une étape par nom (« leave:ID », « drop:KEY », « deleteOwned:ID »). Absent = fait.
    var steps: [String: MiffStep] = [:]
    private(set) var inventoryCalls = 0

    init(timeline: Timeline = Timeline()) {
        self.timeline = timeline
    }

    func inventory(uid: String) async -> MiffsRead {
        inventoryCalls += 1
        timeline.add("inventory")
        return inventories.count > 1 ? inventories.removeFirst() : (inventories.first ?? .read(testInventory()))
    }

    func dropStale(uid: String, ref: MembershipRef) async -> MiffStep {
        timeline.add("drop:\(ref.key)")
        return steps["drop:\(ref.key)"] ?? .done
    }

    func leave(uid: String, ref: MembershipRef) async -> MiffStep {
        timeline.add("leave:\(ref.miffId)")
        return steps["leave:\(ref.miffId)"] ?? .done
    }

    func deleteOwned(uid: String, miffId: String) async -> MiffStep {
        timeline.add("deleteOwned:\(miffId)")
        return steps["deleteOwned:\(miffId)"] ?? .done
    }
}

/// La suppression de la session d'appareil du compte : `failures` dit combien d'appels lèvent une erreur (pas d'accusé) avant de passer.
final class FakeAccountSession: AccountSession {
    private let timeline: Timeline
    var failures = 0
    var failure: Error = SessionDeletionUnconfirmed()
    private(set) var calls = 0

    init(timeline: Timeline = Timeline()) {
        self.timeline = timeline
    }

    func deleteSession(uid: String) async throws {
        calls += 1
        timeline.add("session")
        if failures > 0 {
            failures -= 1
            throw failure
        }
    }
}

/// Une procédure dont les résultats sont donnés par le test, un par exécution (le dernier se répète). Chaque exécution peut annoncer
/// des étapes à l'écran de progression.
final class ScriptedProcedure: AccountDeletionProcedure {
    private let timeline: Timeline
    var runs: [DeletionRun] = [.completed]
    var stepsPerRun: [[DeletionStep]] = []
    var inventoryAnswer: Inventory = .loaded(.none)
    private(set) var runCalls = 0
    private(set) var inventoryCalls = 0

    init(timeline: Timeline = Timeline()) {
        self.timeline = timeline
    }

    func inventory(uid: String) async -> Inventory {
        inventoryCalls += 1
        return inventoryAnswer
    }

    func run(uid: String, onStep: @escaping DeletionStepReporter) async -> DeletionRun {
        runCalls += 1
        timeline.add("run")
        let index = runCalls - 1
        if index < stepsPerRun.count {
            for step in stepsPerRun[index] {
                await onStep(step)
            }
        }
        return runs.count > 1 ? runs.removeFirst() : (runs.first ?? .completed)
    }
}

/// Ce que le contrôleur demande à la session de l'app : chaque appel noté dans la chronologie.
@MainActor
final class RecordingDeletionSession: AccountDeletionSession {
    private let timeline: Timeline
    private(set) var begun = 0
    private(set) var erased: [String] = []
    private(set) var cancelled = 0
    private(set) var keptMarker = 0
    /// Appelé quand le compte est effacé (pour observer le marqueur à cet instant).
    var onErase: () -> Void = {}

    init(timeline: Timeline = Timeline()) {
        self.timeline = timeline
    }

    func beginAccountDeletion() async {
        begun += 1
        timeline.add("begin")
    }

    func eraseAfterAccountDeletion(uid: String) async {
        erased.append(uid)
        timeline.add("erase")
        onErase()
    }

    func cancelAccountDeletion() async {
        cancelled += 1
        timeline.add("cancel")
    }

    func endSessionKeepingDeletionMarker() async {
        keptMarker += 1
        timeline.add("keepMarker")
    }
}

/// Le retrait de l'autorisation Apple : rend ce qu'on lui dit, et note les codes reçus SANS les garder dans une chaîne d'échec.
final class FakeAppleRevoker: AppleTokenRevoker {
    var result = true
    private(set) var codes: [String] = []

    func revoke(authorizationCode: String) async -> Bool {
        codes.append(authorizationCode)
        return result
    }
}

/// Un « serveur » avec de vrais documents : chaque suppression retire vraiment quelque chose, chaque opération est numérotée, et on
/// peut en couper UNE (pas de réseau) pour prouver qu'une reprise termine toujours, dans le bon ordre, sans rien supprimer deux fois
/// ni supprimer le compte avant le profil. Implémente à la fois les index / appareils / profil, les Miffs et la session d'appareil.
final class AccountWorld: AccountDataStore, AccountMiffs, AccountSession {
    private let timeline: Timeline
    private(set) var joined: [MembershipRef]
    private(set) var owned: [AccountOwnedMiff]
    private(set) var memberships: Set<String>
    private(set) var devices: [String]
    private(set) var profileExists = true
    private(set) var sessionExists = true
    /// Les opérations tentées jusqu'ici (coupées comprises) ; `cutAt` : le numéro (à partir de 1) de celle qui échoue faute de réseau.
    private(set) var calls = 0
    var cutAt = -1

    init(timeline: Timeline, joined: [MembershipRef], owned: [AccountOwnedMiff], devices: [String]) {
        self.timeline = timeline
        self.joined = joined
        self.owned = owned
        self.devices = devices
        // Plus un index périmé (sa Miff a disparu) : il part en silence.
        memberships = Set(joined.map { $0.key } + owned.map { "key-\($0.miffId)" } + ["stale-key-0000001"])
    }

    /// Faux = cette opération est coupée.
    private func pass() -> Bool {
        calls += 1
        return calls != cutAt
    }

    private func cut() -> Error {
        firestoreError(FirestoreErrorNumber.unavailable)
    }

    func membershipCount(uid: String) async throws -> Int {
        guard pass() else {
            throw cut()
        }
        return memberships.count
    }

    func deviceIds(uid: String) async throws -> [String] {
        guard pass() else {
            throw cut()
        }
        return devices
    }

    func deleteDevice(uid: String, deviceId: String) async throws {
        guard pass() else {
            throw cut()
        }
        devices.removeAll { $0 == deviceId }
    }

    func deleteProfile(uid: String) async throws {
        guard pass() else {
            throw cut()
        }
        profileExists = false
        timeline.add("profile")
    }

    func deleteSession(uid: String) async throws {
        guard pass() else {
            throw OperationTimedOut()
        }
        sessionExists = false
    }

    func inventory(uid: String) async -> MiffsRead {
        guard pass() else {
            return .failed(.offline)
        }
        let ownedKeys = Set(owned.map { "key-\($0.miffId)" })
        let stale = memberships.subtracting(joined.map { $0.key }).subtracting(ownedKeys)
        let staleRefs = stale.sorted().map { MembershipRef(key: $0, miffId: "Stale-\($0)") }
        return .read(MiffsInventory(joined: joined, owned: owned, stale: staleRefs))
    }

    func dropStale(uid: String, ref: MembershipRef) async -> MiffStep {
        guard pass() else {
            return .failed(.offline)
        }
        memberships.remove(ref.key)
        return .done
    }

    func leave(uid: String, ref: MembershipRef) async -> MiffStep {
        guard pass() else {
            return .failed(.offline)
        }
        joined.removeAll { $0.key == ref.key }
        memberships.remove(ref.key)
        return .done
    }

    func deleteOwned(uid: String, miffId: String) async -> MiffStep {
        guard pass() else {
            return .failed(.offline)
        }
        owned.removeAll { $0.miffId == miffId }
        memberships.remove("key-\(miffId)")
        return .done
    }

    /// Tout ce que le compte détient sur le serveur a disparu.
    var isEmpty: Bool {
        joined.isEmpty && owned.isEmpty && memberships.isEmpty && devices.isEmpty && !profileExists && !sessionExists
    }
}
