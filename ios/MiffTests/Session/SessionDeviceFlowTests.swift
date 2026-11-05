import XCTest
@testable import Miff

/// « Un compte, un seul appareil » côté gestionnaire de session (SESS-3, miroir des tests d'Android SESS-2) : connexion,
/// remplacement par un autre téléphone, déconnexion volontaire, boucle impossible, hors ligne puis retour du réseau, première
/// installation, réinstallation avec une session existante, ancienne installation sans session.
@MainActor
final class SessionDeviceFlowTests: XCTestCase {
    private func ready(_ destination: AppDestination) -> SessionState {
        .ready(destination)
    }

    /// Un téléphone déjà connecté, dont le compte est complet, ouvert à froid : sa session est écrite au lancement.
    private func launchedHarness(teardown: [SessionTeardown] = []) async -> SessionHarness {
        let harness = SessionHarness(teardown: teardown)
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()
        return harness
    }

    // MARK: - Connexion

    func testSigningInWithEmailOpensTheDeviceSessionBeforeTheAccountIsRead() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        let timeline = harness.timeline
        harness.accounts.onLoad = { timeline.add("accountLoad") }

        let failure = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertNil(failure)
        XCTAssertEqual(harness.remote.writtenIds.count, 1)
        let identifier = harness.remote.writtenIds.first ?? ""
        XCTAssertTrue(DeviceId.isValid(identifier))
        XCTAssertEqual(harness.deviceStore.stored, identifier)
        XCTAssertEqual(harness.device.currentDeviceId, identifier)
        let written = timeline.entries.firstIndex(of: "sessionWrite") ?? Int.max
        let read = timeline.entries.firstIndex(of: "accountLoad") ?? Int.min
        XCTAssertLessThan(written, read, "la session est confirmée AVANT toute lecture du compte")
        XCTAssertEqual(harness.manager.state, ready(.feed))
    }

    func testSigningInWithGoogleAndAppleOpensTheDeviceSessionToo() async {
        let google = SessionHarness()
        google.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await google.manager.startAndResolve()
        _ = await google.manager.signIn(with: .google(idToken: "id", accessToken: "acc"), signUpConsent: nil)

        let apple = SessionHarness()
        apple.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await apple.manager.startAndResolve()
        _ = await apple.manager.signIn(with: .apple(idToken: "id", rawNonce: "nonce", name: nil), signUpConsent: nil)

        XCTAssertEqual(google.remote.writtenIds.count, 1)
        XCTAssertEqual(google.device.currentDeviceId, google.remote.writtenIds.first)
        XCTAssertEqual(apple.remote.writtenIds.count, 1)
        XCTAssertEqual(apple.device.currentDeviceId, apple.remote.writtenIds.first)
    }

    func testSigningUpWithEmailOpensTheDeviceSessionBeforeTheProfileStep() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()

        let failure = await harness.manager.createAccountWithEmail(
            email: "alex@exemple.fr", password: "motdepasse", consent: .implicit)

        XCTAssertNil(failure)
        XCTAssertEqual(harness.remote.writtenIds.count, 1)
        XCTAssertEqual(harness.device.currentDeviceId, harness.remote.writtenIds.first)
        XCTAssertEqual(harness.manager.state, ready(.profile))
    }

    func testEverySignInGetsANewIdentifier() async {
        let harness = await launchedHarness()
        let first = harness.device.currentDeviceId
        await harness.manager.signOut()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertNotNil(first)
        XCTAssertNotNil(harness.device.currentDeviceId)
        XCTAssertNotEqual(harness.device.currentDeviceId, first)
        XCTAssertEqual(harness.remote.writtenIds.count, 2)
    }

    func testASessionTheServerDidNotConfirmShowsTryAgainAndStartsNothing() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.remote.reachable = false

        let failure = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertNil(failure, "la connexion elle-même a réussi")
        XCTAssertEqual(harness.manager.state, .lookupFailed)
        XCTAssertNil(harness.device.currentDeviceId, "aucune écriture de statut ne peut partir")
        XCTAssertNil(harness.deviceStore.stored)
        XCTAssertEqual(harness.accounts.loadCalls, 0, "le compte n'est pas lu tant que la session n'est pas ouverte")

        harness.remote.reachable = true
        harness.manager.retry()
        await harness.waitUntil { harness.manager.state == .ready(.feed) }

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.remote.writtenIds.count, 1)
        XCTAssertNotNil(harness.device.currentDeviceId)
    }

    func testTryAgainAfterAMissingAcknowledgementNeverSignsOutTheNewcomerAsReplaced() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        harness.remote.serverId = "other-phone-0123456789"
        harness.remote.reachable = false
        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")
        harness.remote.reachable = true
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        harness.manager.retry()
        await harness.waitUntil { harness.manager.state == .ready(.feed) }

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertNil(harness.manager.consumeNotice())
        XCTAssertEqual(harness.remote.serverId, harness.device.currentDeviceId, "la dernière connexion gagne")
    }

    // MARK: - Remplacement par un autre téléphone

    func testAnotherPhoneSigningInSignsThisOneOutKindlyAndStopsEverything() async {
        var stopped = 0
        let harness = await launchedHarness(teardown: [{ stopped += 1 }])
        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertNotNil(harness.device.currentDeviceId)

        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertNil(harness.gateway.currentUid)
        XCTAssertEqual(stopped, 1, "tout suivi s'arrête")
        XCTAssertEqual(harness.cache.purgeCalls, 1, "le cache est purgé")
        XCTAssertNil(harness.device.currentDeviceId, "plus aucune écriture de statut ni de position ne peut partir")
        XCTAssertNil(harness.deviceStore.stored)
        XCTAssertFalse(harness.privacy.analyticsEnabled)
        XCTAssertNil(harness.manager.accountSnapshot)
    }

    func testAForcedSignOutNeverWritesNorDeletesTheSession() async {
        let harness = await launchedHarness()
        let writes = harness.remote.writtenIds.count

        harness.remote.anotherPhoneSignsIn("other-phone-0123456789")
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }
        await harness.settle()

        XCTAssertEqual(harness.remote.writtenIds.count, writes, "pas de boucle : rien n'est réécrit")
        XCTAssertEqual(harness.remote.deleteCalls, 0, "la session du nouvel appareil n'est jamais supprimée")
        XCTAssertEqual(harness.remote.serverId, "other-phone-0123456789")
    }

    func testTheEndOfSessionIsDoneOnceEvenWhenTheLocalSignOutIsAnnounced() async {
        let harness = await launchedHarness()

        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }
        await harness.settle()

        XCTAssertEqual(harness.gateway.signOutCalls, 1)
        XCTAssertEqual(harness.cache.purgeCalls, 1)
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertNil(harness.manager.consumeNotice(), "le message n'est montré qu'une fois")
    }

    func testTheSessionDisappearingFromTheServerEndsTheSessionToo() async {
        let harness = await launchedHarness()

        harness.remote.sessionDisappears()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertEqual(harness.remote.writtenIds.count, 1, "session disparue : pas réécrite")
    }

    func testAnEchoOfMyOwnSessionChangesNothing() async {
        let harness = await launchedHarness()
        let mine = harness.deviceStore.stored ?? ""

        harness.remote.anotherPhoneSignsIn(mine)
        await harness.settle()

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.gateway.signOutCalls, 0)
        XCTAssertEqual(harness.device.currentDeviceId, mine)
    }

    func testSignInAgainAfterAForcedSignOutTakesThePlaceBack() async {
        let harness = await launchedHarness()
        harness.remote.anotherPhoneSignsIn("other-phone-0123456789")
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.remote.writtenIds.count, 2)
        XCTAssertEqual(harness.remote.serverId, harness.device.currentDeviceId)
        XCTAssertNotEqual(harness.remote.serverId, "other-phone-0123456789")
    }

    func testTheCalmNoteIsKeptUntilTheSignInScreenHasShownIt() async {
        let harness = await launchedHarness()

        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertTrue(harness.flags.value(forKey: SessionManager.keyReplacedNoticePending, default: false))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertFalse(harness.flags.value(forKey: SessionManager.keyReplacedNoticePending, default: false))
    }

    func testANewSignInDropsANoteThatWasNeverShown() async {
        let harness = await launchedHarness()
        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertFalse(harness.flags.value(forKey: SessionManager.keyReplacedNoticePending, default: false))
    }

    func testAReplacedSessionIsNeverPresentedAsALostMiffAccess() async {
        let harness = await launchedHarness()

        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        // Un message à part (« autre téléphone »), jamais celui d'un compte indisponible ni d'un accès perdu.
        let notice = harness.manager.consumeNotice()
        XCTAssertEqual(notice, .sessionReplaced)
        XCTAssertNotEqual(notice, .accountUnavailable)
        XCTAssertNotEqual(notice, .accountNotCreated)
    }

    // MARK: - Déconnexion volontaire

    func testSigningOutDeletesOnlyMySessionAndLeavesNoMessage() async {
        let harness = await launchedHarness()

        await harness.manager.signOut()

        XCTAssertNil(harness.remote.serverId)
        XCTAssertEqual(harness.remote.deletedCount, 1)
        XCTAssertNil(harness.deviceStore.stored)
        XCTAssertNil(harness.device.currentDeviceId)
        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertNil(harness.manager.consumeNotice())
        // Sa propre suppression n'est pas lue comme un remplacement : une seule fin de session.
        await harness.settle()
        XCTAssertEqual(harness.cache.purgeCalls, 1)
        XCTAssertEqual(harness.gateway.signOutCalls, 1)
    }

    func testSigningOutNeverDeletesTheSessionOfAnotherPhone() async {
        let harness = await launchedHarness()
        // Un autre téléphone vient de se connecter, mais ce téléphone-ci ne l'a pas encore appris.
        harness.remote.serverId = "other-phone-0123456789"

        await harness.manager.signOut()

        XCTAssertEqual(harness.remote.serverId, "other-phone-0123456789")
        XCTAssertEqual(harness.remote.deletedCount, 0)
        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertNil(harness.manager.consumeNotice())
    }

    func testSigningOutOfflineStillSignsOutAndLeavesTheSessionForTheNextSignInToReplace() async {
        let harness = await launchedHarness()
        harness.remote.reachable = false
        harness.network.online = false

        await harness.manager.signOut()

        XCTAssertEqual(harness.remote.deleteCalls, 0, "hors ligne, on n'attend même pas un délai")
        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertNil(harness.gateway.currentUid)
        XCTAssertNil(harness.deviceStore.stored)
        XCTAssertNotNil(harness.remote.serverId, "rien n'est supprimé sans la réponse du serveur")
    }
}
