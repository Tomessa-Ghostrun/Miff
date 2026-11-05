import XCTest
@testable import Miff

/// `DeviceSession` : second essai d'écriture, révocation après un remplacement, autorisation d'écrire (SESS-3).
final class DeviceSessionRevocationTests: XCTestCase {
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

    func testTheSecondTryUsesTheSameIdentifierAndSavesAnUnansweredFirstWrite() async {
        remote.writesToFail = 1
        let session = makeSession()

        let done = await session.begin(uid: uid)

        XCTAssertTrue(done)
        XCTAssertEqual(remote.writeAttempts, 2)
        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001"], "le même identifiant aux deux essais")
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
    }

    func testTwoUnansweredWritesMeanNoSession() async {
        remote.writesToFail = 2
        let session = makeSession()

        let done = await session.begin(uid: uid)

        XCTAssertFalse(done)
        XCTAssertEqual(remote.writeAttempts, 2)
        XCTAssertNil(session.currentDeviceId)
        XCTAssertEqual(store.stored, "test-device-id-0001", "le candidat reste, réutilisé par l'essai suivant")
        XCTAssertTrue(store.isCandidate)
    }

    func testAReplacedPhoneStaysRevokedAndNeverWritesANewSessionUntilTheNextSignIn() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = nil
        let first = await session.check(uid: uid)
        let writes = remote.writtenIds.count

        // La session a disparu : sans la révocation, l'absence de session ET d'identifiant ferait réécrire une session.
        let second = await session.check(uid: uid)
        let third = await session.judge(.absent, uid: uid)

        XCTAssertEqual(first, .replaced)
        XCTAssertEqual(second, .replaced)
        XCTAssertEqual(third, .replaced)
        XCTAssertEqual(remote.writtenIds.count, writes)
        XCTAssertNil(session.currentDeviceId)

        let again = await session.begin(uid: uid)

        XCTAssertTrue(again)
        XCTAssertNotNil(session.currentDeviceId)
    }

    func testAForcedSignOutRevokesThePhoneToo() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)

        session.forget()
        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertEqual(remote.readCalls, 0, "révoqué : aucune lecture inutile")
        XCTAssertEqual(remote.writtenIds.count, 1)
    }

    func testSigningOutOfflineDoesNotEvenTryToDelete() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)

        await session.release(uid: uid, online: false)

        XCTAssertEqual(remote.deleteCalls, 0)
        XCTAssertEqual(remote.serverId, "test-device-id-0001")
        XCTAssertNil(store.stored)
    }

    func testClearanceGivesTheConfirmedIdentifierWithoutAnyRead() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)

        let clearance = await session.clearance(uid: uid)

        XCTAssertEqual(clearance, .cleared(deviceId: "test-device-id-0001"))
        XCTAssertEqual(remote.readCalls, 0)
    }

    func testClearanceAsksTheServerFirstWhenNothingIsConfirmedYet() async {
        store.stored = "test-device-id-0001"
        remote.serverId = "test-device-id-0001"
        let session = makeSession()

        let clearance = await session.clearance(uid: uid)

        XCTAssertEqual(clearance, .cleared(deviceId: "test-device-id-0001"))
        XCTAssertEqual(remote.readCalls, 1)
    }

    func testClearanceIsUnverifiedWhenTheServerCannotBeAskedAndNothingIsWritten() async {
        store.stored = "test-device-id-0001"
        remote.reachable = false
        let session = makeSession()

        let clearance = await session.clearance(uid: uid)

        XCTAssertEqual(clearance, .unverified)
        XCTAssertTrue(remote.writtenIds.isEmpty)
    }

    func testClearanceIsRefusedAndTheSignOutRequestedWhenAnotherPhoneHoldsTheSession() async {
        store.stored = "test-device-id-0001"
        remote.serverId = "other-phone-0123456789"
        let session = makeSession()
        let warned = TestBox(0)
        session.setReplacedHandler { warned.value += 1 }

        let clearance = await session.clearance(uid: uid)
        let again = await session.clearance(uid: uid)

        XCTAssertEqual(clearance, .replaced)
        XCTAssertEqual(again, .replaced)
        XCTAssertEqual(warned.value, 1, "la déconnexion forcée n'est demandée qu'une fois")
        XCTAssertTrue(remote.writtenIds.isEmpty)
    }

    func testTheTextFormOfAnIdentifierBearingValueNeverShowsTheIdentifier() {
        XCTAssertFalse("\(DeviceClearance.cleared(deviceId: testDeviceId))".contains(testDeviceId))
        XCTAssertFalse("\(RemoteSession.active(deviceId: testDeviceId))".contains(testDeviceId))
        XCTAssertFalse(String(describing: DeviceClearance.cleared(deviceId: testDeviceId)).contains(testDeviceId))
    }
}
