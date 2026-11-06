import XCTest
@testable import Miff

/// Revue de contre-vérification de la PR #79 : l'appareil est FERMÉ dès que sa session est supprimée (jamais réécrite entre la
/// suppression et la déconnexion), et la reprise AUTOMATIQUE d'une connexion non confirmée lit le serveur d'abord (la dernière
/// connexion réelle gagne). Seul « Réessayer » ou une nouvelle connexion reprend la place.
final class DeviceSessionClosingTests: XCTestCase {
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

    private func keepACandidate(_ identifier: String = "test-device-id-0001") {
        store.stored = identifier
        store.isCandidate = true
    }

    // MARK: - Fermé après la suppression de la session

    func testAfterReleaseEveryVerdictIsReplacedAndNothingIsWritten() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        let warned = TestBox(0)
        session.setReplacedHandler { warned.value += 1 }

        await session.release(uid: uid)
        let checked = await session.check(uid: uid)
        let judged = await session.judge(.absent, uid: uid)
        let refused = await session.checkAfterRefusal(uid: uid)
        let clearance = await session.clearance(uid: uid)

        XCTAssertEqual(checked, .replaced)
        XCTAssertEqual(judged, .replaced)
        XCTAssertEqual(refused, .replaced)
        XCTAssertEqual(clearance, .replaced)
        XCTAssertEqual(remote.writtenIds.count, 1, "aucune session réécrite après la suppression")
        XCTAssertNil(remote.serverId)
        XCTAssertNil(session.currentDeviceId)
        XCTAssertTrue(session.isRevoked)
        XCTAssertTrue(store.revoked, "le marqueur est sur disque dès la fin de release")
    }

    func testANewConnectionAfterReleaseReopensTheDevice() async {
        let session = makeSession()
        _ = await session.begin(uid: uid)
        await session.release(uid: uid)

        let began = await session.begin(uid: uid)

        XCTAssertTrue(began)
        XCTAssertFalse(session.isRevoked)
        XCTAssertNotNil(session.currentDeviceId)
    }

    // MARK: - Reprise automatique d'un candidat : le serveur d'abord

    func testAnAutomaticResumeWithAnotherDevicesSessionMeansReplacedAndWritesNothing() async {
        keepACandidate()
        remote.serverId = "other-phone-0123456789"
        let session = makeSession()

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .replaced)
        XCTAssertTrue(remote.writtenIds.isEmpty, "la dernière connexion réelle (l'autre appareil) gagne")
        XCTAssertTrue(session.isRevoked)
        XCTAssertNil(session.currentDeviceId)
    }

    func testAnAutomaticResumeWithNoSessionWritesTheCandidate() async {
        keepACandidate()
        let session = makeSession()

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .confirmed)
        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001"])
        XCTAssertEqual(store.read(), .confirmed("test-device-id-0001"))
    }

    func testAnAutomaticResumeWhoseWriteLandedOnlyConfirmsLocally() async {
        keepACandidate()
        remote.serverId = "test-device-id-0001"
        let session = makeSession()

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .confirmed)
        XCTAssertTrue(remote.writtenIds.isEmpty, "rien à réécrire")
        XCTAssertEqual(store.read(), .confirmed("test-device-id-0001"))
        XCTAssertEqual(session.currentDeviceId, "test-device-id-0001")
    }

    func testAnAutomaticResumeWithoutAnAnswerStaysPending() async {
        keepACandidate()
        remote.reachable = false
        let session = makeSession()

        let verdict = await session.check(uid: uid)

        XCTAssertEqual(verdict, .unverified)
        XCTAssertTrue(session.hasPendingBegin)
        XCTAssertTrue(remote.writtenIds.isEmpty)
    }

    func testAnExplicitBeginTakesThePlaceBackWhateverTheServerHolds() async {
        keepACandidate()
        remote.serverId = "other-phone-0123456789"
        let session = makeSession()

        let began = await session.begin(uid: uid)

        XCTAssertTrue(began)
        XCTAssertEqual(remote.serverId, "test-device-id-0001")
        XCTAssertFalse(session.isRevoked)
    }

    func testACandidateLeftByAnotherAccountIsForgottenByClearLocal() async {
        keepACandidate("old-account-candidate-01")
        let session = makeSession()

        session.clearLocal()
        _ = await session.begin(uid: uid)

        XCTAssertEqual(remote.writtenIds, ["test-device-id-0001"], "identifiant neuf, jamais celui d'un autre compte")
    }
}

@MainActor
final class SessionManagerClosingTests: XCTestCase {
    private func ready(_ destination: AppDestination) -> SessionState {
        .ready(destination)
    }

    private func launchedHarness() async -> SessionHarness {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()
        return harness
    }

    private func openPanel(_ harness: SessionHarness) async {
        await harness.manager.startAndResolve()
        _ = await harness.manager.signIn(with: .google(idToken: "id", accessToken: "acc"), signUpConsent: nil)
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
    }

    func testAfterTheSessionOfAnAccountAboutToDisappearIsDeletedTheDeviceIsClosedUntilTheAccountIsKept() async {
        let harness = SessionHarness()
        await openPanel(harness)
        let writes = harness.remote.writtenIds.count

        let deleted = await harness.manager.deleteDeviceSessionBeforeAccountRemoval(uid: "uid-1")
        let checked = await harness.device.check(uid: "uid-1")
        let judged = await harness.device.judge(.absent, uid: "uid-1")

        XCTAssertTrue(deleted)
        XCTAssertEqual(checked, .replaced, "pendant la réauthentification, aucune vérification ne réécrit une session")
        XCTAssertEqual(judged, .replaced)
        XCTAssertEqual(harness.remote.writtenIds.count, writes)
        XCTAssertTrue(harness.device.isRevoked)

        // Le compte est gardé finalement : la session est réécrite et l'appareil rouvert.
        await harness.manager.restoreDeviceSession()

        XCTAssertFalse(harness.device.isRevoked)
        XCTAssertNotNil(harness.device.currentDeviceId)
        XCTAssertEqual(harness.remote.serverId, harness.device.currentDeviceId)
    }

    func testKeepingTheAccountAfterARefusedPanelReopensTheDevice() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.gateway.deleteResults = [.network]

        let result = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        XCTAssertEqual(result, .failed(.network))
        XCTAssertFalse(harness.device.isRevoked)
        XCTAssertFalse(harness.deviceStore.revoked)
        XCTAssertNotNil(harness.device.currentDeviceId)
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
    }

    func testASignInWithoutAcknowledgementDoesNotChaseAnotherPhoneWhenTheNetworkReturns() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.remote.reachable = false
        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")
        XCTAssertEqual(harness.manager.state, .lookupFailed)

        // Pendant ce temps la personne se connecte sur un AUTRE téléphone : la dernière connexion réelle est celle-là.
        harness.remote.reachable = true
        harness.remote.serverId = "other-phone-0123456789"
        harness.network.online = false
        harness.network.online = true
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertTrue(harness.remote.writtenIds.isEmpty, "A n'a pas réécrit son candidat par-dessus B")
        XCTAssertEqual(harness.remote.serverId, "other-phone-0123456789")
    }

    func testAnInterruptedSignInResumedAtLaunchDoesNotChaseAnotherPhoneEither() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.stored = "test-device-id-0001"
        harness.deviceStore.isCandidate = true
        harness.remote.serverId = "other-phone-0123456789"

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
        XCTAssertNil(harness.deviceStore.stored, "le candidat abandonné est effacé")
    }

    func testTryAgainAfterAnInterruptedSignInTakesThePlaceBack() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.stored = "test-device-id-0001"
        harness.deviceStore.isCandidate = true
        harness.network.online = false
        await harness.manager.startAndResolve()
        XCTAssertEqual(harness.manager.state, .lookupFailed)
        // Le retour du réseau lirait d'abord (reprise automatique) : on l'écarte pour tester « Réessayer ».
        harness.manager.stopDeviceWatch()
        harness.network.online = true
        harness.remote.serverId = "other-phone-0123456789"

        // « Réessayer » est une reprise EXPLICITE : l'écriture prend la place.
        harness.manager.retry()
        await harness.waitUntil { harness.manager.state == .ready(.feed) }

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.remote.serverId, "test-device-id-0001")
    }

    func testACandidateLeftByAPreviousAccountIsNotReusedByTheNextSignIn() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-old")
        harness.deviceStore.stored = "old-account-candidate-01"
        harness.deviceStore.isCandidate = true
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.gateway.signInResult = .success(uid: "uid-new", isNewUser: false)

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertEqual(harness.remote.writtenIds, ["test-device-id-0001"])
        XCTAssertNotEqual(harness.device.currentDeviceId, "old-account-candidate-01")
    }

    func testTheRestartCounterOfTheListenerIsResetAfterAHealthyPeriod() async {
        let harness = await launchedHarness()
        harness.manager.deviceWatchRestartPauses = [1_000_000]
        harness.manager.deviceWatchHealthySeconds = 0

        harness.remote.endListening()
        await harness.waitUntil { harness.remote.changesCalls >= 2 }
        harness.remote.endListening()
        await harness.waitUntil { harness.remote.changesCalls >= 3 }

        XCTAssertEqual(harness.remote.changesCalls, 3, "une écoute saine remet le compteur à zéro")
    }
}
