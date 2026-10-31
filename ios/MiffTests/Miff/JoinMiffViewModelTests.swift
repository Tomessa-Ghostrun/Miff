import XCTest
@testable import Miff

/// Le view model « Rejoindre » (ticket Miff, critères J, K, H) : l'ordre des vérifications, le consentement explicite avant
/// l'écriture, la pause après 5 essais. Aucun Firestore : un dépôt double.
@MainActor
final class JoinMiffViewModelTests: MiffFlowTestCase {
    private let time = TestClock()

    private func join() -> JoinMiffViewModel {
        let clock = time
        // Une longue pause (celle d'une minute) reste suspendue jusqu'à `release()` : on peut observer l'état pendant elle.
        clock.holdsLongPauses = true
        // Rien ne reste suspendu une fois le test fini.
        addTeardownBlock {
            clock.release()
        }
        let limiter = JoinAttemptLimiter(nowMillis: { clock.now })
        return JoinMiffViewModel(context: context(), limiter: limiter, clock: time.clock)
    }

    func testTheButtonNeeds12ValidCharactersAndALookAlikeIsAnsweredLocally() async {
        let viewModel = join()

        viewModel.onCodeChanged("xtzr sp36 798")
        XCTAssertFalse(viewModel.state.canContinue)
        viewModel.onCodeChanged("xtzr sp36 7980")
        XCTAssertTrue(viewModel.state.foreignCharacter, "un zéro n'est pas dans l'alphabet")
        XCTAssertFalse(viewModel.state.canContinue)
        viewModel.onContinue()
        try? await Task.sleep(nanoseconds: 30_000_000)

        XCTAssertTrue(repo.calls.isEmpty, "aucune lecture réseau pour un code qui ne peut pas exister (critère J2)")
        XCTAssertEqual(viewModel.state.problem, .foreignCharacter)
        viewModel.onCodeChanged("xtzr sp36 7988")
        XCTAssertEqual(viewModel.state.code, "XTZRSP367988")
        XCTAssertTrue(viewModel.state.canContinue)
        XCTAssertNil(viewModel.state.problem)
    }

    func testAGoodCodeShowsTheConsentAndWritesNothing() async {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)

        viewModel.onContinue()
        await waitUntil { viewModel.state.step == .confirm }

        XCTAssertEqual(viewModel.state.step, .confirm)
        XCTAssertEqual(viewModel.state.miffName, "Chez nous")
        XCTAssertEqual(repo.calls, ["prepareJoin"])
        XCTAssertTrue(recorder.entered.isEmpty && sink.events.isEmpty)
    }

    func testCancelOnTheConsentWritesNothingAndGoesBackToTheCode() async {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)
        viewModel.onContinue()
        await waitUntil { viewModel.state.step == .confirm }

        viewModel.onCancelConfirm()

        XCTAssertEqual(viewModel.state.step, .code)
        XCTAssertFalse(repo.calls.contains("join"))
    }

    func testOnlyTheButtonOfTheConsentWritesOnceAndTheEventsFollowTheServer() async {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)
        viewModel.onContinue()
        await waitUntil { viewModel.state.step == .confirm }

        viewModel.onConfirm()
        viewModel.onConfirm()
        await waitUntil { viewModel.state.joinedMiffId != nil }

        XCTAssertEqual(repo.calls.filter { $0 == "join" }.count, 1)
        XCTAssertEqual(viewModel.state.joinedMiffId, testMiffId)
        XCTAssertEqual(recorder.entered, [.joined])
        XCTAssertEqual(sink.names, ["miff_joined"])
        XCTAssertEqual(recorder.directoryChanges, 1)
        XCTAssertFalse(viewModel.state.joining)
    }

    func testWrongRenewedOrDeletedCodeShowTheSameMessage() async {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)
        repo.joinPreparation = .notFound
        viewModel.onContinue()
        await waitUntil { !viewModel.state.checking }
        let fromAWrongCode = viewModel.state.problem

        // Le code était bon à la lecture mais renouvelé avant la confirmation : retour au code, même message.
        repo.joinPreparation = .ready(
            JoinReady(miffId: testMiffId, miffName: "Chez nous", code: testCode, premium: false, firstMiff: true)
        )
        repo.joinResult = .notFound
        viewModel.onCodeChanged(testCode)
        viewModel.onContinue()
        await waitUntil { viewModel.state.step == .confirm }
        viewModel.onConfirm()
        await waitUntil { viewModel.state.step == .code }

        XCTAssertEqual(fromAWrongCode, .notFound)
        XCTAssertEqual(viewModel.state.problem, .notFound)
        XCTAssertEqual(viewModel.state.step, .code)
    }

    func testAfterFiveWrongCodesAPauseOfOneMinuteThenItIsOver() async {
        repo.joinPreparation = .notFound
        let viewModel = join()
        for _ in 0..<5 {
            viewModel.onCodeChanged(testCode)
            viewModel.onContinue()
            await waitUntil { !viewModel.state.checking }
        }

        XCTAssertEqual(viewModel.state.problem, .paused)
        XCTAssertFalse(viewModel.state.canContinue)
        XCTAssertEqual(repo.calls.filter { $0 == "prepareJoin" }.count, 5)
        // Pendant la pause rien n'est lu.
        viewModel.onContinue()
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertEqual(repo.calls.filter { $0 == "prepareJoin" }.count, 5)

        time.advance(by: 61_000)
        time.release()
        await waitUntil { viewModel.state.problem == nil }

        XCTAssertNil(viewModel.state.problem)
        XCTAssertTrue(viewModel.state.canContinue)
    }

    func testAlreadyAMemberOffersToOpenTheMiffAndWritesNothing() async {
        repo.joinPreparation = .alreadyMember(miffId: testMiffId, miffName: "Chez nous")
        let viewModel = join()
        viewModel.onCodeChanged(testCode)

        viewModel.onContinue()
        await waitUntil { viewModel.state.alreadyMember != nil }

        XCTAssertEqual(viewModel.state.alreadyMember?.miffId, testMiffId)
        XCTAssertEqual(viewModel.state.step, .code)
        XCTAssertFalse(repo.calls.contains("join"))
    }

    func testOfflineIsAnsweredAtOnceOnTheCodeAndOnTheConsent() async {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)
        network.online = false

        viewModel.onContinue()
        XCTAssertEqual(viewModel.state.problem, .offline)
        XCTAssertTrue(repo.calls.isEmpty)

        network.online = true
        viewModel.onContinue()
        await waitUntil { viewModel.state.step == .confirm }
        network.online = false
        viewModel.onConfirm()

        XCTAssertEqual(viewModel.state.confirmMessage, .offline)
        XCTAssertFalse(repo.calls.contains("join"))
    }

    func testANetworkProblemDoesNotCountAsAWrongCode() async {
        repo.joinPreparation = .failed(.offline)
        let viewModel = join()
        for _ in 0..<6 {
            viewModel.onCodeChanged(testCode)
            viewModel.onContinue()
            await waitUntil { !viewModel.state.checking }
        }

        XCTAssertEqual(viewModel.state.problem, .offline)
        XCTAssertEqual(repo.calls.filter { $0 == "prepareJoin" }.count, 6)
    }

    func testAFreeAccountWithAMiffIsToldHonestlyAndItDoesNotCountAsAWrongCode() async {
        repo.joinPreparation = .needsPremium
        let viewModel = join()
        for _ in 0..<6 {
            viewModel.onCodeChanged(testCode)
            viewModel.onContinue()
            await waitUntil { !viewModel.state.checking }
        }

        XCTAssertEqual(viewModel.state.problem, .oneMiff)
        XCTAssertEqual(repo.calls.filter { $0 == "prepareJoin" }.count, 6)
    }

    func testConfirmWithoutAProfileSaysItHonestlyAndAsksTheRoutingAgain() async {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)
        viewModel.onContinue()
        await waitUntil { viewModel.state.step == .confirm }
        repo.joinResult = .failed(.noProfile)

        viewModel.onConfirm()
        await waitUntil { !viewModel.state.joining }

        XCTAssertEqual(viewModel.state.confirmMessage, .noProfile)
        XCTAssertEqual(recorder.profileMissing, 1)
    }

    func testAPastedCodeIsCleanedLikeATypedOne() {
        // Le code n'entre que par `onCodeChanged` (frappe ou bouton système « Coller ») : aucun accès au presse-papiers dans
        // le view model ni dans le dépôt. Le texte collé est nettoyé comme ce qui est tapé.
        let viewModel = join()

        viewModel.onCodeChanged("  xtzr-sp36-7988\n")

        XCTAssertEqual(viewModel.state.code, "XTZRSP367988")
        XCTAssertTrue(viewModel.state.canContinue)
    }

    func testTheCodeNeverShowsInTheDescriptionOfTheState() {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)

        XCTAssertFalse(String(describing: viewModel.state).contains("XTZR"))
    }
}
