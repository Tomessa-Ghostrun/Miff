import XCTest
@testable import Miff

/// `DeviceSession` : le tableau de décision de « un compte, un seul appareil » (SESS-3), sans interface ni gestionnaire de session.
final class DeviceSessionTests: XCTestCase {
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

    // MARK: - Connexion

    func testSigningInWritesAFreshIdentifierThenKeepsItLocallyAndConfirmsIt() async {
        let session = makeSession()

        let done = await session.begin(uid: uid)

        XCTAssertTrue(done)
        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001"])
        XCTAssertEqual(store.stored, "test-device-id-0001")
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
        XCTAssertEqual(remote.serverId, "test-device-id-0001")
    }

    func testEverySignInGeneratesANewIdentifier() async {
        let session = makeSession()

        _ = await session.begin(uid: uid)
        _ = await session.begin(uid: uid)

        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001", "test-device-id-0002"])
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0002")
    }

    func testWithoutTheServersAcknowledgementNothingIsKeptAndNothingIsConfirmed() async {
        remote.reachable = false
        store.stored = "old-device-id-000000"
        let session = makeSession()

        let done = await session.begin(uid: uid)

        XCTAssertFalse(done)
        // L'ancien identifiant confirmé est remplacé par un CANDIDAT non confirmé, qui n'est jamais jugé comme une session active.
        XCTAssertEqual(store.stored, "test-device-id-0001")
        XCTAssertTrue(store.isCandidate)
        XCTAssertTrue(session.hasPendingBegin)
        XCTAssertNil(session.currentDeviceId)
        XCTAssertNil(remote.serverId)
    }

    func testAnIdentifierThatCannotBeKeptLocallyIsNotConfirmed() async {
        store.saveFails = true
        let session = makeSession()

        let done = await session.begin(uid: uid)

        XCTAssertFalse(done)
        XCTAssertNil(session.currentDeviceId)
        XCTAssertTrue(remote.writtenIds.isEmpty, "disque plein : rien n'est écrit sur le serveur")
        XCTAssertEqual(remote.writeAttempts, 0)
    }

    func testNoIdentifierMeansNoSessionAndNothingIsSent() async {
        let session = DeviceSession(remote: remote, store: store, makeIdentifier: { nil })

        let done = await session.begin(uid: uid)

        XCTAssertFalse(done)
        XCTAssertTrue(remote.writtenIds.isEmpty)
    }

    func testAMalformedIdentifierIsNeverSent() async {
        let session = DeviceSession(remote: remote, store: store, makeIdentifier: { "court" })

        let done = await session.begin(uid: uid)

        XCTAssertFalse(done)
        XCTAssertTrue(remote.writtenIds.isEmpty)
    }

    // MARK: - Tableau de décision

    func testTheServerHoldsMySessionConfirmsIt() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .confirmed)
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
    }

    func testAnotherDevicesSessionMeansReplacedAndClosesTheWriteGate() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = "other-phone-0123456789"

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertNil(session.currentDeviceId, "plus aucun statut ni position ne peut partir")
    }

    func testASessionThatDisappearedWhileIHadAnIdentifierMeansReplaced() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = nil

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertNil(session.currentDeviceId)
    }

    func testASessionOnTheServerAndNoLocalIdentifierIsAReinstallationAndMeansReplaced() async {
        remote.serverId = "previous-install-00000"
        let session = makeSession()

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertTrue(remote.writtenIds.isEmpty, "on ne réécrit jamais la session d'une autre installation")
        XCTAssertEqual(remote.deleteCalls, 0)
        XCTAssertNil(session.currentDeviceId)
    }

    func testNoSessionAnywhereIsAnOldInstallationAndOneIsWritten() async {
        let session = makeSession()

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .confirmed)
        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001"])
        XCTAssertEqual(store.stored, "test-device-id-0001")
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
    }

    func testAnUnreachableServerNeverLogsAnyoneOutButNeverOpensTheWriteGate() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = "other-phone-0123456789"
        remote.reachable = false

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .unverified)
        XCTAssertEqual(store.stored, "test-device-id-0001", "hors ligne personne n'est déconnecté")
    }

    func testAnUnverifiedStartNeverGivesAnIdentifierToWrite() async {
        store.stored = "test-device-id-0001"
        remote.reachable = false
        let session = makeSession()

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .unverified)
        XCTAssertNil(session.currentDeviceId, "tant que le serveur n'a pas confirmé, aucune écriture ne part")
    }

    func testBackOnlineTheSessionIsConfirmedBeforeAnyWrite() async {
        store.stored = "test-device-id-0001"
        remote.serverId = "test-device-id-0001"
        remote.reachable = false
        let session = makeSession()
        let offline = await session.check(uid: uid)
        remote.reachable = true

        let online = await session.check(uid: uid)

        XCTAssertEqual(offline, .unverified)
        XCTAssertEqual(online, .confirmed)
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
    }

    func testAMalformedServerDocumentIsAnotherDevice() async {
        store.stored = "test-device-id-0001"
        let session = makeSession()

        let verdict = await session.judge(.active(deviceId: ""), uid: uid)

        XCTAssertEqual(verdict, .replaced)
    }

    // MARK: - Fin

    func testSigningOutDeletesMyOwnSessionAndForgetsTheIdentifier() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)

        await session.release(uid: uid)

        XCTAssertNil(remote.serverId)
        XCTAssertEqual(remote.deletedCount, 1)
        XCTAssertNil(store.stored)
        XCTAssertNil(session.currentDeviceId)
    }

    func testSigningOutNeverDeletesTheSessionOfAnotherDevice() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = "other-phone-0123456789"

        await session.release(uid: uid)

        XCTAssertEqual(remote.serverId, "other-phone-0123456789")
        XCTAssertEqual(remote.deletedCount, 0)
        XCTAssertNil(store.stored)
    }

    func testSigningOutOfflineDeletesNothingButStillForgetsTheIdentifier() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.reachable = false

        await session.release(uid: uid)

        XCTAssertEqual(remote.serverId, "test-device-id-0001")
        XCTAssertNil(store.stored)
        XCTAssertNil(session.currentDeviceId)
    }

    func testSigningOutWithoutAnyLocalIdentifierTouchesNothingOnTheServer() async {
        remote.serverId = "other-phone-0123456789"
        let session = makeSession()

        await session.release(uid: uid)

        XCTAssertEqual(remote.deleteCalls, 0)
        XCTAssertEqual(remote.serverId, "other-phone-0123456789")
    }

    func testAForcedSignOutForgetsLocallyAndNeverTouchesTheServer() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = "other-phone-0123456789"
        let writesBefore = remote.writtenIds.count

        session.forget()

        XCTAssertNil(store.stored)
        XCTAssertNil(session.currentDeviceId)
        XCTAssertEqual(remote.writtenIds.count, writesBefore)
        XCTAssertEqual(remote.deleteCalls, 0)
        XCTAssertEqual(remote.serverId, "other-phone-0123456789")
    }

    // MARK: - Après un refus d'écriture

    func testARefusalFollowedByAReplacedSessionWarnsTheSessionManager() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = "other-phone-0123456789"
        let warned = TestBox(0)
        session.setReplacedHandler { warned.value += 1 }

        let verdict = await session.checkAfterRefusal(uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertEqual(warned.value, 1)
    }

    func testARefusalWithMySessionStillInPlaceIsAnOrdinaryRefusal() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        let warned = TestBox(0)
        session.setReplacedHandler { warned.value += 1 }

        let verdict = await session.checkAfterRefusal(uid: uid)

        XCTAssertEqual(verdict, .confirmed)
        XCTAssertEqual(warned.value, 0)
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
    }
}
