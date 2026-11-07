import XCTest
@testable import Miff

/// Le jugement de la session de l'appareil : « session remplacée par un autre téléphone » et « session DISPARUE » (compte supprimé
/// ailleurs) sont deux cas distincts (SESS-3 + AUTH-5c, PR Android #100). Même déconnexion locale, deux notes différentes, et la raison
/// survit à un processus tué. Miroir de `SessionJudge` et de `DeviceSession` d'Android.
final class SessionJudgeTests: XCTestCase {
    private let mine = "test-device-id-0001"
    private let other = "other-phone-0123456789"

    func testTheSameIdentifierIsMine() {
        XCTAssertEqual(SessionJudge.judge(local: mine, remote: .active(deviceId: mine)), .mine)
    }

    func testAnotherIdentifierMeansAnotherPhoneTookThePlace() {
        XCTAssertEqual(SessionJudge.judge(local: mine, remote: .active(deviceId: other)), .replaced)
    }

    func testASessionWithoutAnyLocalIdentifierIsAnotherPhone() {
        XCTAssertEqual(SessionJudge.judge(local: nil, remote: .active(deviceId: other)), .replaced)
    }

    func testNoSessionAndNoLocalIdentifierIsAnOlderInstallation() {
        XCTAssertEqual(SessionJudge.judge(local: nil, remote: .absent), .writeNew)
    }

    func testNoSessionButALocalIdentifierMeansTheDocumentIsGone() {
        XCTAssertEqual(SessionJudge.judge(local: mine, remote: .absent), .sessionGone)
    }

    func testAMalformedDocumentIsNeverMine() {
        XCTAssertEqual(SessionJudge.judge(local: mine, remote: .active(deviceId: "")), .replaced)
    }
}

final class DeviceSessionGoneTests: XCTestCase {
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

    func testADisappearedDocumentRevokesThePhoneAndRemembersWhy() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = nil

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertTrue(session.isRevoked)
        XCTAssertTrue(session.replacedBecauseGone)
        XCTAssertTrue(store.isGone(), "la raison est sur le disque, avec le marqueur")
        XCTAssertEqual(remote.writtenIds.count, 1, "jamais une nouvelle session pour un compte qui disparaît")
    }

    func testAnotherPhoneTakingThePlaceIsNotGone() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.anotherPhoneSignsIn()

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertTrue(session.isRevoked)
        XCTAssertFalse(session.replacedBecauseGone)
        XCTAssertFalse(store.isGone())
    }

    func testTheReasonSurvivesAKilledProcess() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = nil
        _ = await session.check(uid: uid)

        let relaunched = makeSession()

        XCTAssertTrue(relaunched.isRevoked)
        XCTAssertTrue(relaunched.replacedBecauseGone)
    }

    func testANewSignInForgetsTheRevocationAndItsReason() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = nil
        _ = await session.check(uid: uid)

        let again = await session.begin(uid: uid)

        XCTAssertTrue(again)
        XCTAssertFalse(session.isRevoked)
        XCTAssertFalse(session.replacedBecauseGone)
        XCTAssertFalse(store.isGone())
    }

    func testTheLiveListenerJudgesADisappearedDocumentAsGoneToo() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)

        let verdict = await session.judge(.absent, uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertTrue(session.replacedBecauseGone)
    }

    // MARK: - Fermé pour la suppression du compte

    func testClosingForTheDeletionWritesTheMarkerButNeverClaimsTheSessionIsGone() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        let writes = remote.writtenIds.count

        session.closeForAccountDeletion()
        let checked = await session.check(uid: uid)
        let judged = await session.judge(.absent, uid: uid)

        XCTAssertTrue(store.isRevoked(), "marqueur sur le disque")
        XCTAssertNil(session.currentDeviceId, "aucun statut ni position ne peut partir")
        XCTAssertEqual(checked, .replaced)
        XCTAssertEqual(judged, .replaced)
        XCTAssertEqual(remote.writtenIds.count, writes, "aucune session réécrite pendant la suppression")
        XCTAssertFalse(session.replacedBecauseGone, "notre propre suppression n'est pas « supprimé ailleurs »")
    }

    func testAKilledProcessWokenInTheMiddleOfTheDeletionStaysClosed() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        session.closeForAccountDeletion()
        _ = await session.deleteForAccountDeletion(uid: uid)

        let woken = makeSession()
        let clearance = await woken.clearance(uid: uid)

        // Pas d'identifiant et pas de session ne se lit pas comme une ancienne installation.
        XCTAssertEqual(clearance, .replaced)
        XCTAssertEqual(remote.writtenIds.count, 1)
    }

    func testDeletingTheSessionForTheAccountRemovesItWhoeverHoldsIt() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.serverId = "other-phone-0123456789"

        let deleted = await session.deleteForAccountDeletion(uid: uid)

        XCTAssertTrue(deleted)
        XCTAssertNil(remote.serverId)
        XCTAssertEqual(remote.unconditionalDeletes, 1)
        XCTAssertNil(store.stored, "l'identifiant n'est oublié qu'après l'accusé")
    }

    func testWithoutAnAcknowledgementNothingIsForgottenAndThePhoneStaysClosed() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        remote.reachable = false

        let deleted = await session.deleteForAccountDeletion(uid: uid)

        XCTAssertFalse(deleted)
        XCTAssertEqual(store.stored, "test-device-id-0001")
        XCTAssertTrue(session.isRevoked)
        XCTAssertNil(session.currentDeviceId)
    }

    func testAnAlreadyAbsentSessionIsAcknowledged() async {
        let session = makeSession()
        remote.serverId = nil

        let deleted = await session.deleteForAccountDeletion(uid: uid)

        XCTAssertTrue(deleted)
    }
}

/// Les deux notes de l'écran de connexion, vues du gestionnaire de session.
@MainActor
final class SessionGoneNoticeTests: XCTestCase {
    private func launchedHarness() async -> SessionHarness {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()
        return harness
    }

    func testADeletionSeenFromAnotherPhoneGivesTheCarefulNoteNotTheAnotherPhoneOne() async {
        let harness = await launchedHarness()

        harness.remote.sessionDisappears()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.consumeNotice(), .sessionGone)
        XCTAssertNil(harness.manager.consumeNotice(), "une seule fois")
        XCTAssertFalse(harness.flags.value(forKey: SessionManager.keyReplacedNoticePending, default: true))
        XCTAssertFalse(harness.flags.value(forKey: SessionManager.keyGoneNoticePending, default: true))
        XCTAssertEqual(harness.gateway.signOutCalls, 1)
    }

    func testAnotherPhoneStillGivesTheAnotherPhoneNote() async {
        let harness = await launchedHarness()

        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
    }

    func testTheCarefulNoteLeftWhileTheAppWasClosedIsShownAtTheNextLaunch() async {
        let harness = SessionHarness()
        harness.flags.set(true, forKey: SessionManager.keyReplacedNoticePending)
        harness.flags.set(true, forKey: SessionManager.keyGoneNoticePending)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.consumeNotice(), .sessionGone)
        XCTAssertFalse(harness.flags.value(forKey: SessionManager.keyGoneNoticePending, default: true))
    }

    func testAForcedSignOutMidDeletionNeverShowsTheAnotherPhoneNote() async {
        let marker = MemoryDeletionMarker(mark: AccountDeletionMark(uid: "uid-1"))
        let harness = SessionHarness(deletionMarker: marker)
        harness.gateway.setSignedIn("uid-1")
        await harness.manager.startAndResolve()
        XCTAssertEqual(harness.manager.state, .ready(.accountDeletion))

        await harness.manager.endBecauseReplaced()
        await harness.settle()

        // Pas de déconnexion forcée pendant la suppression.
        XCTAssertEqual(harness.manager.state, .ready(.accountDeletion))
        XCTAssertEqual(harness.gateway.signOutCalls, 0)
        XCTAssertNil(harness.manager.consumeNotice())
    }
}
