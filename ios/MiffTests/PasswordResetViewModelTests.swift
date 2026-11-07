import XCTest
@testable import Miff

/// L'écran « Mot de passe oublié » : le MÊME texte que l'adresse ait un compte ou non (critère M2), une pause de renvoi par
/// adresse (M5), aucune requête pour une adresse mal formée ni hors connexion.
@MainActor
final class PasswordResetViewModelTests: XCTestCase {
    private final class FakeResetGateway: PasswordResetGateway {
        var outcome: PasswordResetOutcome = .accepted
        private(set) var sentTo: [String] = []

        func sendResetEmail(_ email: String) async -> PasswordResetOutcome {
            sentTo.append(email)
            return outcome
        }
    }

    private struct Setup {
        let viewModel: PasswordResetViewModel
        let gateway: FakeResetGateway
        let network: FakeNetwork
        let time: TestClock
    }

    private func make(online: Bool = true, outcome: PasswordResetOutcome = .accepted) -> Setup {
        let gateway = FakeResetGateway()
        gateway.outcome = outcome
        let network = FakeNetwork(online: online)
        let time = TestClock()
        time.holdsLongPauses = true
        // Rien ne reste suspendu une fois le test fini.
        addTeardownBlock {
            time.release()
        }
        let cooldown = ResendCooldown(nowMillis: { time.now })
        let viewModel = PasswordResetViewModel(
            gateway: gateway, network: network, cooldown: cooldown, clock: time.clock)
        return Setup(viewModel: viewModel, gateway: gateway, network: network, time: time)
    }

    private func settle(_ setup: Setup) async {
        for _ in 0..<200 where setup.viewModel.state.isLoading {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
    }

    func testAMalformedAddressMakesNoRequest() async {
        let setup = make()

        setup.viewModel.submit("pas-une-adresse")

        XCTAssertTrue(setup.gateway.sentTo.isEmpty)
        XCTAssertTrue(setup.viewModel.state.showFieldError)
        XCTAssertFalse(setup.viewModel.state.sent)
    }

    func testOfflineAnswersAtOnceAndKeepsWhatWasTyped() async {
        let setup = make(online: false)

        setup.viewModel.submit("alex@exemple.fr")

        XCTAssertTrue(setup.gateway.sentTo.isEmpty)
        XCTAssertEqual(setup.viewModel.state.message, .network)
        XCTAssertFalse(setup.viewModel.state.sent)
    }

    func testAnAcceptedRequestShowsTheConfirmationAndStartsThePause() async {
        let setup = make()

        setup.viewModel.submit("  Alex@Exemple.fr ")
        await settle(setup)

        XCTAssertEqual(setup.gateway.sentTo, ["Alex@Exemple.fr"])
        XCTAssertTrue(setup.viewModel.state.sent)
        XCTAssertEqual(setup.viewModel.state.sendCount, 1)
        XCTAssertTrue(setup.viewModel.state.resendLocked)
        XCTAssertNil(setup.viewModel.state.message)
        // Un « accepté » n'est jamais montré plus vite que le plancher de durée.
        XCTAssertEqual(setup.time.pauses.first, PasswordResetViewModel.minAnswerMillis)
    }

    func testTheSameWordsWhetherOrNotTheAddressHasAnAccount() async {
        // Adresse inconnue : le SDK répond par une erreur, interprétée comme « accepté » : même état, mêmes mots.
        let known = make()
        let unknown = make(outcome: interpretPasswordResetError(NSError(domain: AuthErrorNumber.domain, code: 17_011)))

        known.viewModel.submit("alex@exemple.fr")
        unknown.viewModel.submit("alex@exemple.fr")
        await settle(known)
        await settle(unknown)

        XCTAssertEqual(known.viewModel.state, unknown.viewModel.state)
        XCTAssertTrue(unknown.viewModel.state.sent)
    }

    func testTheResendButtonStaysPausedForTheSameAddressOnly() async {
        let setup = make()
        setup.viewModel.submit("alex@exemple.fr")
        await settle(setup)

        setup.viewModel.submit("alex@exemple.fr")
        XCTAssertEqual(setup.gateway.sentTo.count, 1)

        // Une faute de frappe corrigée ne fait pas attendre pour l'adresse erronée.
        setup.viewModel.onEmailChanged("alex@exemple.com")
        XCTAssertFalse(setup.viewModel.state.resendLocked)
        XCTAssertFalse(setup.viewModel.state.sent)
        // Retaper la première adresse ramène la confirmation et la pause.
        setup.viewModel.onEmailChanged("ALEX@exemple.fr")
        XCTAssertTrue(setup.viewModel.state.sent)
        XCTAssertTrue(setup.viewModel.state.resendLocked)
    }

    func testTheButtonIsReenabledWhenThePauseEnds() async {
        let setup = make()
        setup.viewModel.submit("alex@exemple.fr")
        await settle(setup)
        XCTAssertTrue(setup.viewModel.state.resendLocked)

        setup.time.advance(by: ResendCooldown.defaultDurationMillis + 1)
        // L'attente planifiée par le view model est suspendue sur l'horloge de test : on la libère.
        setup.time.release()
        for _ in 0..<100 where setup.viewModel.state.resendLocked {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertFalse(setup.viewModel.state.resendLocked)
    }

    func testAServerSideMalformedAddressGetsTheSameWordsAsTheLocalCheck() async {
        let setup = make(outcome: .emailRejected)

        setup.viewModel.submit("alex@exemple.fr")
        await settle(setup)

        XCTAssertTrue(setup.viewModel.state.emailRejected)
        XCTAssertFalse(setup.viewModel.state.sent)
    }

    func testAQuotaProblemDoesNotAdviseResettingAndStartsNoPause() async {
        let setup = make(outcome: .failed(.tooManyAttempts))

        setup.viewModel.submit("alex@exemple.fr")
        await settle(setup)

        XCTAssertEqual(setup.viewModel.state.message, .tooManyRequests)
        XCTAssertFalse(setup.viewModel.state.resendLocked)
        XCTAssertFalse(setup.viewModel.state.sent)
    }

    func testEditingTheAddressRemovesTheErrorsAndAStaleConfirmation() async {
        let setup = make(outcome: .failed(.network))
        setup.viewModel.submit("alex@exemple.fr")
        await settle(setup)
        XCTAssertEqual(setup.viewModel.state.message, .network)

        setup.viewModel.onEmailChanged("alex@exemple.com")

        XCTAssertNil(setup.viewModel.state.message)
        XCTAssertFalse(setup.viewModel.state.emailRejected)
    }
}
