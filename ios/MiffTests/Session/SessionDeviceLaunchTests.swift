import XCTest
@testable import Miff

/// « Un compte, un seul appareil » au lancement et au retour du réseau (SESS-3, miroir des tests d'Android SESS-2) : lancement
/// avec sa session, ancienne installation sans session, réinstallation avec une session existante, première installation, hors
/// ligne puis retour du réseau, et le message de l'écran de connexion.
@MainActor
final class SessionDeviceLaunchTests: XCTestCase {
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

    // MARK: - Lancement

    func testALaunchWithMySessionInPlaceCostsOneReadAndWritesNothing() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.stored = "test-device-id-0001"
        harness.remote.serverId = "test-device-id-0001"

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.remote.readCalls, 1, "au plus une lecture de plus au lancement (SES7)")
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
        XCTAssertEqual(harness.device.currentDeviceId, "test-device-id-0001")
    }

    func testAnOldInstallationWithoutAnySessionWritesOne() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.remote.writtenIds.count, 1)
        XCTAssertEqual(harness.deviceStore.stored, harness.remote.writtenIds.first)
        XCTAssertEqual(harness.device.currentDeviceId, harness.remote.writtenIds.first)
    }

    func testAReinstallationWhoseSignInSurvivedFindsAnExistingSessionAndSignsOutKindly() async {
        // Le repère d'installation est présent (sauvegarde restaurée) mais l'identifiant local n'existe pas : la session du
        // serveur appartient à l'ancienne installation ou à un autre téléphone.
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.remote.serverId = "previous-install-00000"

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertEqual(harness.accounts.loadCalls, 0, "le compte n'est même pas lu")
        XCTAssertTrue(harness.remote.writtenIds.isEmpty, "la session de l'autre n'est pas réécrite")
        XCTAssertEqual(harness.remote.deleteCalls, 0)
        XCTAssertEqual(harness.remote.serverId, "previous-install-00000")
        XCTAssertNil(harness.device.currentDeviceId)
    }

    func testTheFirstLaunchOfAnInstallationClosesAnInheritedSignInWithoutTouchingTheSession() async {
        let harness = SessionHarness(freshInstall: true)
        harness.gateway.setSignedIn("uid-1")
        harness.remote.serverId = "previous-install-00000"

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertFalse(harness.remote.touched, "ni écriture ni suppression de la session de l'ancienne installation")
        XCTAssertEqual(harness.remote.readCalls, 0)
        XCTAssertEqual(harness.remote.serverId, "previous-install-00000")
    }

    func testALaunchWithAReplacedSessionNeverReadsTheAccountNorWrites() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.stored = "test-device-id-0001"
        harness.remote.serverId = "other-phone-0123456789"

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertEqual(harness.accounts.loadCalls, 0)
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
        XCTAssertNil(harness.deviceStore.stored)
    }

    // MARK: - Hors ligne, puis retour du réseau

    private func offlineLaunch(serverHolds identifier: String) async -> SessionHarness {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        harness.deviceStore.stored = "test-device-id-0001"
        harness.remote.serverId = identifier
        harness.remote.reachable = false
        harness.network.online = false
        await harness.manager.startAndResolve()
        return harness
    }

    func testOfflineNobodyIsSignedOutButNoWriteIsAllowedYet() async {
        let harness = await offlineLaunch(serverHolds: "other-phone-0123456789")
        await harness.settle()

        XCTAssertEqual(harness.manager.state, ready(.feed), "hors ligne, personne n'est déconnecté")
        XCTAssertNil(harness.device.currentDeviceId, "mais aucun statut ni position ne peut partir")
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
        XCTAssertEqual(harness.remote.deleteCalls, 0)
        XCTAssertEqual(harness.remote.readCalls, 0, "hors ligne : aucun délai d'attente au lancement")
    }

    func testANoteLeftByAForcedSignOutWhileTheAppWasClosedIsShownAtTheNextLaunch() async {
        // Une déconnexion forcée faite par un réveil en arrière-plan (zones, LOC-4b) : le message attend l'écran de connexion.
        let harness = SessionHarness()
        harness.flags.set(true, forKey: SessionManager.keyReplacedNoticePending)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertFalse(harness.flags.value(forKey: SessionManager.keyReplacedNoticePending, default: false))
        XCTAssertNil(harness.manager.consumeNotice(), "une seule fois")
    }

    func testBackOnlineTheSessionIsVerifiedAndConfirmedBeforeAnyWrite() async {
        let harness = await offlineLaunch(serverHolds: "test-device-id-0001")

        harness.remote.reachable = true
        harness.network.online = true
        await harness.waitUntil { harness.device.currentDeviceId != nil }

        XCTAssertEqual(harness.device.currentDeviceId, "test-device-id-0001")
        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
    }

    func testBackOnlineAReplacedPhoneIsSignedOutBeforeItWritesAnything() async {
        let harness = await offlineLaunch(serverHolds: "other-phone-0123456789")

        harness.remote.reachable = true
        harness.network.online = true
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .sessionReplaced)
        XCTAssertTrue(harness.remote.writtenIds.isEmpty, "rien n'a été écrit, ni avant ni après")
        XCTAssertEqual(harness.remote.deleteCalls, 0)
        XCTAssertNil(harness.device.currentDeviceId)
    }

    // MARK: - Panneau de consentement refusé

    private func openPanel(_ harness: SessionHarness) async {
        await harness.manager.startAndResolve()
        _ = await harness.manager.signIn(with: .google(idToken: "id", accessToken: "acc"), signUpConsent: nil)
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
    }

    func testRefusingThePanelDeletesTheDeviceSessionBeforeTheAccountDisappears() async {
        let harness = SessionHarness()
        await openPanel(harness)
        XCTAssertEqual(harness.remote.writtenIds.count, 1)

        let result = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        XCTAssertEqual(result, .done)
        XCTAssertNil(harness.remote.serverId, "aucune session orpheline pour un compte supprimé")
        let entries = harness.timeline.entries
        let sessionDeleted = entries.firstIndex(of: "sessionDelete") ?? Int.max
        let accountDeleted = entries.firstIndex(of: "delete") ?? Int.min
        XCTAssertLessThan(sessionDeleted, accountDeleted, "avant le compte : après, plus rien ne pourrait la supprimer")
        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .accountNotCreated)
    }

    func testRefusingThePanelWhenTheAccountIsKeptWritesTheSessionAgain() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.gateway.deleteResults = [.network]

        let result = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        XCTAssertEqual(result, .failed(.network))
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
        XCTAssertEqual(harness.remote.writtenIds.count, 2)
        XCTAssertNotNil(harness.device.currentDeviceId)
        XCTAssertEqual(harness.remote.serverId, harness.device.currentDeviceId)
    }

    func testCancellingTheFreshSignInOfARefusalWritesTheSessionAgainToo() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.gateway.deleteResults = [.recentLoginRequired]
        let source = FakeSocialSource()
        source.google = .cancelled

        let result = await harness.manager.refuseConsentPanel(source: source)

        XCTAssertEqual(result, .cancelled)
        XCTAssertEqual(harness.remote.writtenIds.count, 2)
        XCTAssertNotNil(harness.device.currentDeviceId)
    }

    // MARK: - Écran de connexion

    func testTheSignInScreenShowsTheCalmNoteOnceAndClearsItWhenSigningInAgain() async {
        let harness = await launchedHarness()
        harness.remote.anotherPhoneSignsIn()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        let viewModel = SignInViewModel(session: harness.manager, network: FakeNetwork(), clock: TestClock().clock)
        let second = SignInViewModel(session: harness.manager, network: FakeNetwork(), clock: TestClock().clock)

        XCTAssertTrue(viewModel.state.showSessionReplacedNote)
        XCTAssertNil(viewModel.state.message, "une information calme, pas une erreur")
        XCTAssertFalse(viewModel.state.showAccountNotCreatedNote)
        XCTAssertFalse(second.state.showSessionReplacedNote)
    }

    func testTheNoteTextsAreTheOnesOfTheTicketInFrenchAndEnglish() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Session
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Catalogue source introuvable (\(url.path)).")
        }
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: Any])
        let entry = try XCTUnwrap(strings["auth_session_replaced_notice"] as? [String: Any])
        let localizations = try XCTUnwrap(entry["localizations"] as? [String: Any])

        func text(_ language: String) -> String? {
            let unit = (localizations[language] as? [String: Any])?["stringUnit"] as? [String: Any]
            return unit?["value"] as? String
        }

        XCTAssertEqual(
            text("fr"),
            "Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci."
        )
        XCTAssertEqual(
            text("en"),
            "Your account is now being used on another phone. You've been signed out of this one."
        )
    }
}
