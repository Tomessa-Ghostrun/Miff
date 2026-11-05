import XCTest
@testable import Miff

/// `DeviceSession` : ce qui arrive quand le disque ou le processus flanchent, et quand plusieurs opérations se chevauchent (SESS-3,
/// revue indépendante de la PR #79).
final class DeviceSessionRobustnessTests: XCTestCase {
    private let uid = "uid-1"
    private let timeline = Timeline()
    private lazy var remote = FakeDeviceSessionRemote(timeline: timeline)
    private let store = MemoryDeviceIdStore()
    private let counter = TestBox(0)

    private func makeSession() -> DeviceSession {
        let counter = self.counter
        return DeviceSession(
            remote: remote,
            store: store,
            makeIdentifier: {
                counter.value += 1
                return String(format: "test-device-id-%04d", counter.value)
            }
        )
    }

    // MARK: - Le candidat : enregistré AVANT l'écriture

    func testTheIdentifierIsKeptAsACandidateBeforeAnythingIsWrittenOnTheServer() async {
        let seenWhenWriting = TestBox<StoredDevice?>(nil)
        let store = self.store
        remote.onWrite = { seenWhenWriting.value = store.read() }
        let session = makeSession()

        _ = await session.begin(uid: uid)

        XCTAssertEqual(seenWhenWriting.value, .candidate("test-device-id-0001"))
        XCTAssertEqual(store.read(), .confirmed("test-device-id-0001"))
    }

    func testAProcessKilledAfterTheServerWroteButBeforeTheConfirmationIsNeverTakenForAReplacement() async {
        // Le serveur a reçu l'écriture, mais l'enregistrement de la confirmation a échoué (ou le processus a été tué).
        store.confirmFails = true
        let first = makeSession()
        let started = await first.begin(uid: uid)
        store.confirmFails = false

        // Relance : un nouvel objet, le même disque, le même serveur.
        let relaunched = makeSession()
        XCTAssertTrue(relaunched.hasPendingBegin)
        let verdict = await relaunched.check(uid: uid)

        XCTAssertFalse(started)
        XCTAssertEqual(verdict, .confirmed, "jamais « remplacé » : la connexion inachevée est reprise")
        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001", "test-device-id-0001"], "le même identifiant")
        XCTAssertEqual(store.read(), .confirmed("test-device-id-0001"))
        XCTAssertEqual(relaunched.currentDeviceId, "test-device-id-0001")
    }

    func testACandidateIsNeverJudgedAsAnActiveSessionNorReplacedNorOverwritten() async {
        store.stored = "test-device-id-0001"
        store.isCandidate = true
        remote.serverId = "other-phone-0123456789"
        let session = makeSession()

        let other = await session.judge(.active(deviceId: "other-phone-0123456789"), uid: uid)
        let absent = await session.judge(.absent, uid: uid)

        XCTAssertEqual(other, .unverified)
        XCTAssertEqual(absent, .unverified)
        XCTAssertFalse(session.isRevoked)
        XCTAssertTrue(remote.writtenIds.isEmpty, "un écouteur ne reprend jamais une connexion")
    }

    func testTheCandidateIsReusedSoALateWriteOfAnEarlierTryCannotOverwriteAMoreRecentSession() async {
        remote.reachable = false
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.reachable = true

        _ = await session.begin(uid: uid)

        XCTAssertEqual(counter.value, 1, "un seul identifiant tiré pour toute la connexion")
        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001"])
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
    }

    func testANewConnectionAfterAConfirmedOneDrawsANewIdentifier() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        _ = await session.begin(uid: uid)

        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001", "test-device-id-0002"])
    }

    // MARK: - Fichier illisible

    func testAnUnreadableStoreIsIndeterminateNotReplacedAndNotNoSession() async {
        store.unreadable = true
        remote.serverId = "other-phone-0123456789"
        let session = makeSession()

        let checked = await session.check(uid: uid)
        let judgedOther = await session.judge(.active(deviceId: "other-phone-0123456789"), uid: uid)
        let judgedAbsent = await session.judge(.absent, uid: uid)
        let clearance = await session.clearance(uid: uid)
        let began = await session.begin(uid: uid)
        let deleted = await session.deleteSession(uid: uid)

        XCTAssertEqual(checked, .unverified)
        XCTAssertEqual(judgedOther, .unverified)
        XCTAssertEqual(judgedAbsent, .unverified)
        XCTAssertEqual(clearance, .unverified)
        XCTAssertFalse(began)
        XCTAssertFalse(deleted)
        XCTAssertFalse(session.isRevoked, "personne n'est déconnecté")
        XCTAssertTrue(remote.writtenIds.isEmpty, "rien n'est écrit")
        XCTAssertEqual(remote.deleteCalls, 0, "rien n'est supprimé")
    }

    func testOnceTheStoreIsReadableAgainTheVerdictIsTheNormalOne() async {
        store.unreadable = true
        let session = makeSession()
        let first = await session.check(uid: uid)
        store.unreadable = false

        let second = await session.check(uid: uid)

        XCTAssertEqual(first, .unverified)
        XCTAssertEqual(second, .confirmed, "ancienne installation : une session est écrite")
    }

    // MARK: - Marqueur « révoqué » persistant

    func testARevokedMarkerLeftOnDiskMakesEveryVerdictReplacedAndNeverWritesANewSession() async {
        // Le processus est mort entre l'oubli de l'identifiant et la déconnexion : au lancement suivant le marqueur est encore là.
        store.revoked = true
        let relaunched = makeSession()

        let checked = await relaunched.check(uid: uid)
        let judged = await relaunched.judge(.absent, uid: uid)
        let clearance = await relaunched.clearance(uid: uid)

        XCTAssertEqual(checked, .replaced)
        XCTAssertEqual(judged, .replaced)
        XCTAssertEqual(clearance, .replaced)
        XCTAssertTrue(remote.writtenIds.isEmpty, "jamais « aucune session et aucun identifiant : j'en écris une »")
        XCTAssertEqual(remote.readCalls, 0)
        XCTAssertNil(relaunched.currentDeviceId)
    }

    func testForgettingPutsTheMarkerOnDiskBeforeAnythingElse() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)

        session.forget()

        XCTAssertTrue(store.revoked)
        XCTAssertNil(store.stored)
        XCTAssertNil(session.currentDeviceId)
    }

    func testANewConnectionRemovesTheMarker() async {
        store.revoked = true
        let session = makeSession()

        let began = await session.begin(uid: uid)

        XCTAssertTrue(began)
        XCTAssertFalse(store.revoked)
    }

    func testIfTheMarkerCannotBeRemovedNoNewSessionIsWritten() async {
        store.revoked = true
        store.revokedClearFails = true
        let session = makeSession()

        let began = await session.begin(uid: uid)

        XCTAssertFalse(began)
        XCTAssertTrue(remote.writtenIds.isEmpty)
    }

    // MARK: - Suppression avant celle d'un compte

    func testDeletingTheSessionIsConfirmedOnlyByTheServer() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.reachable = false

        let unreachable = await session.deleteSession(uid: uid)
        let offline = await session.deleteSession(uid: uid, online: false)

        XCTAssertFalse(unreachable)
        XCTAssertFalse(offline)
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001", "rien n'a changé : toujours cette session")
        XCTAssertEqual(store.stored, "test-device-id-0001")
        remote.reachable = true

        let reached = await session.deleteSession(uid: uid)

        XCTAssertTrue(reached)
        XCTAssertNil(remote.serverId)
    }

    func testThereIsNothingToDeleteWithoutAnIdentifierOrOnceReplaced() async {
        let withoutIdentifier = makeSession()
        let first = await withoutIdentifier.deleteSession(uid: uid)

        store.stored = "test-device-id-0001"
        store.revoked = true
        let second = await makeSession().deleteSession(uid: uid)

        XCTAssertTrue(first)
        XCTAssertTrue(second)
        XCTAssertEqual(remote.deleteCalls, 0)
    }

    // MARK: - Opérations simultanées

    func testTwoSimultaneousBeginsNeverOverlap() async {
        remote.writeDelayNanos = 30_000_000
        let session = makeSession()

        async let first = session.begin(uid: uid)
        async let second = session.begin(uid: uid)
        let results = await [first, second]

        XCTAssertEqual(results, [true, true])
        XCTAssertEqual(remote.maxConcurrentWrites, 1, "un seul begin à la fois : le second attend")
        XCTAssertEqual(remote.serverId, session.currentDeviceId, "la session confirmée est celle du serveur")
    }

    func testTwoSimultaneousChecksOfAnOldInstallationWriteOneSessionOnly() async {
        remote.writeDelayNanos = 30_000_000
        let session = makeSession()

        async let first = session.check(uid: uid)
        async let second = session.check(uid: uid)
        let results = await [first, second]

        XCTAssertEqual(results, [.confirmed, .confirmed])
        XCTAssertEqual(remote.writtenIds.count, 1, "la seconde vérification voit la session de la première")
    }

    func testARefusalCheckAndAClearanceRunningTogetherStaySerialized() async {
        store.stored = "test-device-id-0001"
        remote.serverId = "test-device-id-0001"
        let session = makeSession()

        async let refusal = session.checkAfterRefusal(uid: uid)
        async let clearance = session.clearance(uid: uid)
        let results = await (refusal, clearance)

        XCTAssertEqual(results.0, .confirmed)
        XCTAssertEqual(results.1, .cleared(deviceId: "test-device-id-0001"))
    }

    func testTheGateLetsCallersThroughOneAtATimeInOrder() async {
        let gate = AsyncGate()
        let order = TestBox<[Int]>([])

        async let one: Void = gate.run {
            try? await Task.sleep(nanoseconds: 20_000_000)
            order.value.append(1)
        }
        async let two: Void = gate.run {
            order.value.append(2)
        }
        _ = await [one, two]

        XCTAssertEqual(Set(order.value), [1, 2])
        XCTAssertEqual(order.value.count, 2)
    }

    // MARK: - Écoute terminée

    func testWhenTheListenerEndsNothingIsConfirmedAnymoreUntilTheNextSuccessfulCheck() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)

        session.markUnverified()

        XCTAssertNil(session.currentDeviceId)
        XCTAssertFalse(session.isRevoked, "ce n'est pas un remplacement")
        let verdict = await session.check(uid: uid)
        XCTAssertEqual(verdict, .confirmed)
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
    }
}
