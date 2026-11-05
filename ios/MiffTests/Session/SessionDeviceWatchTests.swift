import XCTest
@testable import Miff

/// « Un compte, un seul appareil » côté gestionnaire de session, après la revue indépendante de la PR #79 : écoute qui se termine,
/// relecture à chaque retour du réseau, connexion interrompue, déconnexion forcée interrompue, stockage illisible, suppression
/// d'un compte qui exige la suppression de la session, fin de session dans sa propre tâche.
@MainActor
final class SessionDeviceWatchTests: XCTestCase {
    private func ready(_ destination: AppDestination) -> SessionState {
        .ready(destination)
    }

    private func launchedHarness(teardown: [SessionTeardown] = []) async -> SessionHarness {
        let harness = SessionHarness(teardown: teardown)
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

    // MARK: - Écoute terminée

    func testAListenerThatEndsOnItsOwnIsRestartedAndTheSessionIsReadAgain() async {
        let harness = await launchedHarness()
        harness.manager.deviceWatchRestartPauses = [1_000_000]
        let readsBefore = harness.remote.readCalls

        harness.remote.endListening()
        await harness.waitUntil { harness.remote.changesCalls >= 2 && harness.remote.readCalls > readsBefore }
        await harness.waitUntil { harness.device.currentDeviceId != nil }

        XCTAssertEqual(harness.remote.changesCalls, 2, "l'écoute est relancée")
        XCTAssertGreaterThan(harness.remote.readCalls, readsBefore, "la session est relue")
        XCTAssertNotNil(harness.device.currentDeviceId)
        XCTAssertEqual(harness.manager.state, ready(.feed))
    }

    func testAReplacementMissedWhileTheListenerWasDeadIsFoundWhenItRestarts() async {
        let harness = await launchedHarness()
        harness.manager.deviceWatchRestartPauses = [1_000_000]
        // L'écoute meurt, PUIS un autre téléphone se connecte : aucun événement n'arrive.
        harness.remote.endListening()
        harness.remote.serverId = "other-phone-0123456789"

        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertEqual(harness.remote.writtenIds.count, 1, "rien n'est réécrit")
    }

    func testTheRestartsAreBoundedAndNothingIsConfirmedAfterTheLastOne() async {
        let harness = await launchedHarness()
        harness.manager.deviceWatchRestartPauses = [1_000_000]
        harness.remote.endListening()
        await harness.waitUntil { harness.remote.changesCalls >= 2 }

        harness.remote.endListening()
        await harness.settle()

        XCTAssertEqual(harness.remote.changesCalls, 2, "une seule relance : la liste des pauses est épuisée")
        XCTAssertNil(harness.device.currentDeviceId, "plus rien ne garantit la session : aucune écriture ne part")
    }

    func testEveryReturnOfTheNetworkReadsTheSessionEvenWhenItWasConfirmed() async {
        let harness = await launchedHarness()
        XCTAssertEqual(harness.remote.readCalls, 1)

        harness.network.online = false
        await harness.settle()
        XCTAssertEqual(harness.remote.readCalls, 1, "la coupure elle-même ne lit rien")
        harness.network.online = true
        await harness.waitUntil { harness.remote.readCalls == 2 }

        XCTAssertEqual(harness.remote.readCalls, 2, "une lecture par retour du réseau")
        XCTAssertNotNil(harness.device.currentDeviceId)
    }

    func testAReplacementDuringACutIsFoundAtTheReturnOfTheNetworkEvenWhenConfirmedBefore() async {
        let harness = await launchedHarness()
        harness.network.online = false
        harness.remote.serverId = "other-phone-0123456789"

        harness.network.online = true
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertEqual(harness.remote.writtenIds.count, 1)
    }

    // MARK: - Connexion interrompue

    func testASignInKilledBeforeTheConfirmationIsResumedAtTheNextLaunchNotTakenForAReplacement() async {
        // Un candidat est resté sur disque ; le serveur a reçu l'écriture.
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.stored = "test-device-id-0001"
        harness.deviceStore.isCandidate = true
        harness.remote.serverId = "test-device-id-0001"

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertNil(harness.manager.consumeNotice())
        XCTAssertEqual(harness.remote.writtenIds, ["test-device-id-0001"], "le même identifiant")
        XCTAssertFalse(harness.deviceStore.isCandidate)
        XCTAssertEqual(harness.device.currentDeviceId, "test-device-id-0001")
    }

    func testASignInKilledWhileOfflineShowsTryAgainAndResumesWhenTheNetworkReturns() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.stored = "test-device-id-0001"
        harness.deviceStore.isCandidate = true
        harness.network.online = false

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, .lookupFailed)
        XCTAssertTrue(harness.remote.writtenIds.isEmpty, "hors ligne, on n'essaie même pas")
        XCTAssertFalse(harness.manager.deviceWatchers.isEmpty, "mais l'écoute et le suivi du réseau tournent")

        harness.network.online = true
        await harness.waitUntil { harness.manager.state == .ready(.feed) }

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.remote.writtenIds, ["test-device-id-0001"])
        XCTAssertNotNil(harness.device.currentDeviceId)
    }

    func testTheWatchAndTheNetworkFollowingStartEvenWhenTheFirstWriteFails() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.remote.reachable = false

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertEqual(harness.manager.state, .lookupFailed)
        XCTAssertFalse(harness.manager.deviceWatchers.isEmpty)

        // Le serveur répond de nouveau, sans que la personne touche « Réessayer » : le retour du réseau reprend la connexion.
        harness.remote.reachable = true
        harness.network.online = false
        harness.network.online = true
        await harness.waitUntil { harness.manager.state == .ready(.feed) }

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertNotNil(harness.device.currentDeviceId)
    }

    // MARK: - Déconnexion forcée interrompue

    func testAForcedSignOutKilledBetweenForgetAndSignOutIsFinishedAtTheNextLaunchWithoutWritingASession() async {
        // Le marqueur est sur disque, l'identifiant oublié, mais l'utilisateur est encore connecté à Firebase, et le serveur n'a
        // plus de session : sans le marqueur, l'appareil croirait être une « ancienne installation » et en écrirait une.
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.revoked = true

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
        XCTAssertEqual(harness.accounts.loadCalls, 0)
        XCTAssertNil(harness.gateway.currentUid)
    }

    func testTheInterruptedForcedSignOutIsFinishedEvenOffline() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.deviceStore.revoked = true
        harness.network.online = false

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertEqual(harness.remote.readCalls, 0)
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
    }

    func testTheForcedSignOutPutsTheMarkerOnDiskBeforeTheTeardownAndThePurge() async {
        let storeBox = TestBox<MemoryDeviceIdStore?>(nil)
        let markerSeen = TestBox(false)
        let harness = await launchedHarness(teardown: [{ markerSeen.value = storeBox.value?.revoked ?? false }])
        storeBox.value = harness.deviceStore

        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertTrue(markerSeen.value, "posé AVANT l'arrêt du suivi et la purge")
        XCTAssertTrue(harness.deviceStore.revoked, "et conservé jusqu'à la prochaine connexion")
    }

    func testTheForcedSignOutRunsInItsOwnTaskNotInTheOneItCancels() async {
        let cancelledDuringTeardown = TestBox(false)
        let harness = await launchedHarness(teardown: [{ cancelledDuringTeardown.value = Task.isCancelled }])

        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertFalse(cancelledDuringTeardown.value, "l'arrêt du suivi ne doit jamais être coupé net")
    }

    // MARK: - Stockage illisible

    func testAnUnreadableStoreAtLaunchLogsNobodyOutAndWritesNothing() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.unreadable = true
        harness.remote.serverId = "other-phone-0123456789"

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
        XCTAssertEqual(harness.remote.deleteCalls, 0)
        XCTAssertNil(harness.device.currentDeviceId, "mais aucune écriture de statut ne peut partir")
        XCTAssertNil(harness.manager.consumeNotice())
    }

    // MARK: - Suppression de compte : la session d'abord

    func testRefusingThePanelStopsBeforeTheAccountIsDeletedWhenTheSessionDeletionIsNotConfirmed() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.remote.reachable = false

        let result = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        XCTAssertEqual(result, .failed(.network))
        XCTAssertEqual(harness.gateway.deleteCalls, 0, "compte NON supprimé sans accusé de la session")
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
        XCTAssertNotNil(harness.device.currentDeviceId, "la session est toujours celle de cet appareil")
        XCTAssertFalse(harness.manager.deviceWatchers.isEmpty, "l'écoute a repris")
        XCTAssertNotNil(harness.remote.serverId)
    }

    func testRefusingThePanelAgainOnceTheServerAnswersGoesThroughToTheEnd() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.remote.reachable = false
        _ = await harness.manager.refuseConsentPanel(source: FakeSocialSource())
        harness.remote.reachable = true

        let result = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        XCTAssertEqual(result, .done)
        XCTAssertNil(harness.remote.serverId)
        XCTAssertEqual(harness.gateway.deleteCalls, 1)
        XCTAssertEqual(harness.manager.state, ready(.signIn))
    }

    func testAFailedRestoreAfterAKeptAccountIsRetriedByTheNetworkAndKeepsTheCandidateOnDisk() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.gateway.deleteResults = [.network]
        harness.remote.writesToFail = 2

        let result = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        XCTAssertEqual(result, .failed(.network))
        XCTAssertNil(harness.device.currentDeviceId, "la session n'a pas pu être réécrite : aucune écriture ne part")
        XCTAssertTrue(harness.deviceStore.isCandidate, "le drapeau est persistant : un candidat est resté sur disque")
        XCTAssertTrue(harness.manager.pendingDeviceBegin)
        XCTAssertFalse(harness.manager.deviceWatchers.isEmpty, "l'écoute et le suivi du réseau tournent malgré l'échec")

        harness.network.online = false
        harness.network.online = true
        await harness.waitUntil { harness.device.currentDeviceId != nil }

        XCTAssertNotNil(harness.device.currentDeviceId)
        XCTAssertFalse(harness.deviceStore.isCandidate)
        XCTAssertFalse(harness.manager.pendingDeviceBegin)
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
    }
}
