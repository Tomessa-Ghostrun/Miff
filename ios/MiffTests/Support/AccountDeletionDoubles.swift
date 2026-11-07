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

func testOwned(id: String = testOwnedMiffId, name: String = "Chez nous", others: Int = 2) -> OwnedMiff {
    OwnedMiff(miffId: id, name: name, otherMembers: others)
}

func testInventory(
    joined: [MembershipRef] = [],
    owned: [OwnedMiff] = [],
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

    func write(_ newMark: AccountDeletionMark) {
        mark = newMark
        writes.append(newMark)
        timeline.add("markerWrite")
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
