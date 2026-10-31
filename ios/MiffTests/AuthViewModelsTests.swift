// Les aides de test rendent (view model, session, horloge) ensemble.
// swiftlint:disable large_tuple
import XCTest
@testable import Miff

/// Les view models de l'inscription, de la connexion et du panneau de consentement (mêmes cas qu'Android).
@MainActor
final class SignUpViewModelTests: XCTestCase {
    private func make(
        online: Bool = true
    ) -> (viewModel: SignUpViewModel, harness: SessionHarness, source: FakeSocialSource) {
        let harness = SessionHarness()
        let viewModel = SignUpViewModel(session: harness.manager, network: FakeNetwork(online: online))
        return (viewModel, harness, FakeSocialSource())
    }

    func testAMalformedFormMakesNoRequestAndShowsTheFieldErrors() async {
        let (viewModel, harness, _) = make()
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "pas-une-adresse", password: "motdepasse")
        viewModel.submitEmail(email: "alex@exemple.fr", password: "court")

        XCTAssertEqual(harness.gateway.createCalls, 0)
        XCTAssertTrue(viewModel.state.showFieldErrors)
        XCTAssertFalse(viewModel.state.isLoading)
        XCTAssertNil(viewModel.state.message)
    }

    func testOfflineAnswersAtOnceWithoutARequest() async {
        let (viewModel, harness, _) = make(online: false)
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertEqual(viewModel.state.message, .network)
        XCTAssertEqual(harness.gateway.createCalls, 0)
    }

    func testAValidFormCreatesTheAccountWithTheImplicitAcceptance() async {
        let (viewModel, harness, _) = make()
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "  alex@exemple.fr ", password: "motdepasse")
        XCTAssertTrue(viewModel.state.isLoading)
        await harness.waitUntil { harness.gateway.createCalls == 1 && !viewModel.state.isLoading }

        XCTAssertEqual(harness.gateway.createCalls, 1)
        XCTAssertEqual(harness.manager.state, .ready(.profile))
        XCTAssertNil(viewModel.state.message)
        XCTAssertEqual(harness.pending.stored?.analyticsAccepted, true)
    }

    func testAnAddressAlreadyUsedGivesAKindMessageWithTheWayToSignIn() async {
        let (viewModel, harness, _) = make()
        harness.gateway.createResult = .failure(.emailAlreadyInUse)
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "alex@exemple.fr", password: "motdepasse")
        await harness.waitUntil { !viewModel.state.isLoading && viewModel.state.message != nil }

        XCTAssertEqual(viewModel.state.message, .emailInUse)
    }

    func testTooManyAttemptsAtSignUpDoesNotAdviseResettingAPassword() async {
        let (viewModel, harness, _) = make()
        harness.gateway.createResult = .failure(.tooManyAttempts)
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "alex@exemple.fr", password: "motdepasse")
        await harness.waitUntil { !viewModel.state.isLoading && viewModel.state.message != nil }

        XCTAssertEqual(viewModel.state.message, .tooManyRequests)
    }

    func testClosingTheGoogleDialogSaysNothingAndKeepsTheFields() async {
        let (viewModel, harness, source) = make()
        source.google = .cancelled
        await harness.manager.startAndResolve()

        viewModel.submitGoogle(source: source)
        await harness.waitUntil { source.googleCalls == 1 && !viewModel.state.isLoading }

        XCTAssertNil(viewModel.state.message)
        XCTAssertEqual(harness.gateway.socialCalls, 0)
    }

    func testGoogleFromSignUpIsAnImplicitAcceptance() async {
        let (viewModel, harness, source) = make()
        await harness.manager.startAndResolve()

        viewModel.submitGoogle(source: source)
        await harness.waitUntil { harness.manager.state == .ready(.profile) }

        XCTAssertEqual(harness.manager.state, .ready(.profile))
        XCTAssertEqual(harness.pending.stored?.termsVersion, ConsentVersions.terms)
    }

    func testAppleFromSignUpIsAnImplicitAcceptanceToo() async {
        let (viewModel, harness, source) = make()
        await harness.manager.startAndResolve()

        viewModel.submitApple(source: source)
        await harness.waitUntil { harness.manager.state == .ready(.profile) }

        XCTAssertEqual(source.appleCalls, 1)
        XCTAssertEqual(harness.manager.state, .ready(.profile))
    }

    func testAGoogleDialogThatCannotStartGivesTheNeutralMessage() async {
        let (viewModel, harness, source) = make()
        source.google = .unavailable
        await harness.manager.startAndResolve()

        viewModel.submitGoogle(source: source)
        await harness.waitUntil { !viewModel.state.isLoading && viewModel.state.message != nil }

        XCTAssertEqual(viewModel.state.message, .unknown)
    }

    func testNothingStartsWhileARequestIsAlreadyRunning() async {
        let (viewModel, harness, _) = make()
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "alex@exemple.fr", password: "motdepasse")
        viewModel.submitEmail(email: "alex@exemple.fr", password: "motdepasse")
        await harness.waitUntil { !viewModel.state.isLoading }

        XCTAssertEqual(harness.gateway.createCalls, 1)
    }
}

@MainActor
final class SignInViewModelTests: XCTestCase {
    private func make(
        online: Bool = true
    ) -> (viewModel: SignInViewModel, harness: SessionHarness, time: TestClock) {
        let harness = SessionHarness()
        let time = TestClock()
        let network = FakeNetwork(online: online)
        let viewModel = SignInViewModel(session: harness.manager, network: network, clock: time.clock)
        return (viewModel, harness, time)
    }

    func testAnEmptyOrMalformedFormMakesNoRequest() async {
        let (viewModel, harness, _) = make()
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "alex@", password: "x")
        viewModel.submitEmail(email: "alex@exemple.fr", password: "")

        XCTAssertEqual(harness.gateway.signInCalls, 0)
        XCTAssertTrue(viewModel.state.showFieldErrors)
    }

    func testWrongCredentialsGiveOneMessageAndAFloorOnTheDuration() async {
        let (viewModel, harness, time) = make()
        harness.gateway.signInResult = .failure(.invalidCredentials)
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "alex@exemple.fr", password: "mauvais")
        await harness.waitUntil { !viewModel.state.isLoading && viewModel.state.message != nil }

        XCTAssertEqual(viewModel.state.message, .invalidCredentials)
        // Une mauvaise réponse n'est jamais montrée plus vite que le plancher (adresse inconnue = même durée).
        XCTAssertEqual(time.pauses, [SignInViewModel.minWrongCredentialsMillis])
    }

    func testFiveWrongAnswersInARowStartACalmPause() async {
        let (viewModel, harness, _) = make()
        harness.gateway.signInResult = .failure(.invalidCredentials)
        await harness.manager.startAndResolve()

        for _ in 0..<5 {
            viewModel.submitEmail(email: "alex@exemple.fr", password: "mauvais")
            await harness.waitUntil { !viewModel.state.isLoading }
        }
        XCTAssertEqual(viewModel.state.message, .tooManyAttempts)

        // Pendant la pause, aucune requête ne part.
        let calls = harness.gateway.signInCalls
        viewModel.submitEmail(email: "alex@exemple.fr", password: "mauvais")
        XCTAssertEqual(harness.gateway.signInCalls, calls)
        XCTAssertEqual(viewModel.state.message, .tooManyAttempts)
    }

    func testOfflineAnswersAtOnce() async {
        let (viewModel, harness, _) = make(online: false)
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertEqual(viewModel.state.message, .network)
        XCTAssertEqual(harness.gateway.signInCalls, 0)
    }

    func testASuccessfulSignInMovesTheSession() async {
        let (viewModel, harness, _) = make()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        viewModel.submitEmail(email: "alex@exemple.fr", password: "motdepasse")
        await harness.waitUntil { harness.manager.state == .ready(.feed) }

        XCTAssertEqual(harness.manager.state, .ready(.feed))
        XCTAssertNil(viewModel.state.message)
    }

    func testAnAccountNotCreatedNoteIsReassuringAndShownOnce() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        await harness.manager.startAndResolve()
        _ = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        let viewModel = SignInViewModel(session: harness.manager, network: FakeNetwork(), clock: TestClock().clock)
        let second = SignInViewModel(session: harness.manager, network: FakeNetwork(), clock: TestClock().clock)

        XCTAssertTrue(viewModel.state.showAccountNotCreatedNote)
        XCTAssertNil(viewModel.state.message)
        XCTAssertFalse(second.state.showAccountNotCreatedNote)
    }

    func testAnAccountThatBecameUnavailableIsExplainedKindly() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true)
        await harness.manager.startAndResolve()
        harness.gateway.setSignedIn(nil)
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        let viewModel = SignInViewModel(session: harness.manager, network: FakeNetwork(), clock: TestClock().clock)

        XCTAssertEqual(viewModel.state.message, .accountUnavailable)
    }

    func testGoogleAndAppleFromSignInGiveANewAccountThePanel() async {
        let (viewModel, harness, _) = make()
        await harness.manager.startAndResolve()

        viewModel.submitGoogle(source: FakeSocialSource())
        await harness.waitUntil { harness.manager.state == .ready(.consentPanel) }

        XCTAssertEqual(harness.manager.state, .ready(.consentPanel))
        XCTAssertNil(harness.pending.stored)
    }
}

@MainActor
final class ConsentPanelViewModelTests: XCTestCase {
    private func openPanel() async -> (viewModel: ConsentPanelViewModel, harness: SessionHarness) {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        _ = await harness.manager.signIn(with: .google(idToken: "i", accessToken: "a"), signUpConsent: nil)
        return (ConsentPanelViewModel(session: harness.manager), harness)
    }

    func testContinueAcceptsAndMovesOnToTheProfile() async {
        let (viewModel, harness) = await openPanel()

        viewModel.accept()

        XCTAssertEqual(harness.manager.state, .ready(.profile))
    }

    func testCancelDeletesTheAccountAndComesBackToSignIn() async {
        let (viewModel, harness) = await openPanel()

        viewModel.refuse(source: FakeSocialSource())
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, .ready(.signIn))
        XCTAssertEqual(harness.gateway.deleteCalls, 1)
        XCTAssertFalse(viewModel.state.isLoading)
    }

    func testACancelThatCannotDeleteKeepsThePanelWithAKindMessage() async {
        let (viewModel, harness) = await openPanel()
        harness.gateway.deleteResults = [.network]

        viewModel.refuse(source: FakeSocialSource())
        await harness.waitUntil { viewModel.state.message != nil }

        XCTAssertEqual(viewModel.state.message, .network)
        XCTAssertEqual(harness.manager.state, .ready(.consentPanel))
        viewModel.reset()
        XCTAssertNil(viewModel.state.message)
    }
}
// swiftlint:enable large_tuple
