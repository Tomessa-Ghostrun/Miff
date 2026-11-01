import XCTest
@testable import Miff

/// L'onboarding n'est complet qu'une fois une Miff créée ou rejointe (CLAUDE.md, ticket Miff §6) : la session dit quand.
@MainActor
final class SessionMiffTests: XCTestCase {
    private func makeHarness(analyticsConsent: Bool = true, hasMembership: Bool = false) async -> SessionHarness {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(
            profileExists: true,
            hasMembership: hasMembership,
            analyticsConsent: analyticsConsent
        )
        await harness.manager.startAndResolve()
        return harness
    }

    func testWithoutAMiffTheAppStaysOnTheChoiceUntilTheMiffScreensSayOtherwise() async {
        let harness = await makeHarness()
        XCTAssertEqual(harness.manager.state, .ready(.miffChoice))

        // Créer une Miff ne déplace pas l'app toute seule : l'écran « Ta Miff est prête » doit encore être vu.
        harness.manager.onMiffEntered(via: .created)
        XCTAssertEqual(harness.manager.state, .ready(.miffChoice))
        XCTAssertEqual(harness.manager.accountSnapshot?.hasMembership, true)

        harness.manager.continueAfterMiff()
        XCTAssertEqual(harness.manager.state, .ready(.feed))
    }

    func testOnboardingCompletedIsCountedOnceWithHowItEndedAndOnlyFromTheChoiceStep() async {
        let harness = await makeHarness()

        harness.manager.onMiffEntered(via: .joined)

        XCTAssertEqual(harness.sink.names, ["onboarding_completed"])
        XCTAssertEqual(harness.sink.events.first?.params["via"] as? String, "joined")
        harness.manager.continueAfterMiff()
        // Une deuxième Miff créée plus tard, depuis l'accueil, n'est pas la fin de l'onboarding.
        harness.manager.onMiffEntered(via: .created)
        XCTAssertEqual(harness.sink.events.count, 1)
    }

    func testWithoutTheStatisticsConsentNothingIsSent() async {
        let harness = await makeHarness(analyticsConsent: false)

        harness.manager.onMiffEntered(via: .created)

        XCTAssertTrue(harness.sink.events.isEmpty)
    }

    func testContinueAfterMiffDoesNothingOutsideTheChoiceStep() async {
        let harness = await makeHarness(hasMembership: true)
        XCTAssertEqual(harness.manager.state, .ready(.feed))

        harness.manager.continueAfterMiff()
        harness.manager.continueAfterMiff()

        XCTAssertEqual(harness.manager.state, .ready(.feed))
    }

    func testAnAccountWithAMiffNeverSeesTheChoiceAgain() async {
        let harness = await makeHarness(hasMembership: true)

        XCTAssertEqual(harness.manager.state, .ready(.feed))
        XCTAssertEqual(harness.manager.accountSnapshot?.hasMembership, true)
    }

    func testTheMiffDirectoryIsOneOfTheEndsOfASession() async {
        // Une déconnexion arrête tout ce qui a été enregistré comme arrêt de session : l'annuaire des Miffs en fait partie
        // (voir `AppEnvironment`) et oublie les Miffs, le message et le choix de la personne.
        let repo = FakeMiffRepository()
        let active = MemoryActiveMiffStore()
        let timeline = Timeline()
        let uid = TestBox<String?>("uid-1")
        let directory = MiffDirectory(
            repository: repo,
            currentUid: { uid.value },
            activeStore: active,
            cache: FakeCachePurger(timeline: timeline)
        )
        repo.myMiffsResult = .loaded(
            MyMiffs(miffs: [MiffSummary(id: testMiffId, name: "Chez nous")], lost: [], premium: false, fromCache: false)
        )
        let harness = SessionHarness(teardown: [{ await directory.stop() }])
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true)
        await harness.manager.startAndResolve()
        await directory.refresh()
        directory.select(miffId: testMiffId)
        XCTAssertEqual(active.value, testMiffId)

        await harness.manager.signOut()

        XCTAssertEqual(directory.state, .loading)
        XCTAssertNil(active.value)
        XCTAssertTrue(directory.notices.isEmpty)
    }
}
