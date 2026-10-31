import XCTest
@testable import Miff

/// Le view model « Créer » (ticket Miff, critères C, H) : « Nouvelle Miff » puis « Ta Miff est prête ». Aucun Firestore : un
/// dépôt double.
@MainActor
final class CreateMiffViewModelTests: MiffFlowTestCase {
    private func create() -> CreateMiffViewModel {
        CreateMiffViewModel(context: context())
    }

    func testTheButtonIsGreyBelowTwoCharactersActiveFromTwo() {
        let viewModel = create()
        XCTAssertFalse(viewModel.state.canSubmit)

        viewModel.onNameChanged("a")
        XCTAssertFalse(viewModel.state.canSubmit)
        viewModel.onNameChanged("  a ")
        XCTAssertFalse(viewModel.state.canSubmit, "les espaces des deux bouts ne comptent pas")
        viewModel.onNameChanged("ab")
        XCTAssertTrue(viewModel.state.canSubmit)
    }

    func testAtFortyCharactersTheLimitIsSaidAndNothingMoreIsKept() {
        let viewModel = create()

        viewModel.onNameChanged(String(repeating: "a", count: 45))

        XCTAssertEqual(viewModel.state.name.count, 40)
        XCTAssertTrue(viewModel.state.limitReached)
    }

    func testSuccessShowsReadyOnlyAfterTheServerCountsTheEventsAndNothingElseIsSent() async {
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")
        var stepWhenWriting: CreateStep?
        repo.onCreate = { stepWhenWriting = viewModel.state.step }

        viewModel.onSubmit()
        XCTAssertTrue(viewModel.state.saving, "le bouton est verrouillé tout de suite")
        await waitUntil { viewModel.state.step == .ready }

        XCTAssertEqual(stepWhenWriting, .name, "toujours à l'étape du nom tant que le serveur n'a pas répondu")
        XCTAssertEqual(viewModel.state.created?.joinCode, testCode)
        XCTAssertFalse(viewModel.state.saving)
        XCTAssertEqual(recorder.entered, [.created])
        XCTAssertEqual(recorder.directoryChanges, 1)
        XCTAssertEqual(sink.names, ["miff_created"])
        XCTAssertEqual(sink.events.first?.params["is_first_miff"] as? Bool, true)
        // Ni nom, ni code, ni identifiant dans l'événement (critère C9).
        let values = sink.events.first?.params.values.compactMap { $0 as? String } ?? []
        XCTAssertFalse(values.contains(testCode))
        XCTAssertFalse(values.contains("Chez nous"))
    }

    func testADoubleTapMakesOneMiff() async {
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()
        viewModel.onSubmit()
        await waitUntil { viewModel.state.step == .ready }

        XCTAssertEqual(repo.created.count, 1)
    }

    func testOfflineSaysSoAtOnceAndTriesNothing() {
        network.online = false
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()

        XCTAssertEqual(viewModel.state.message, .offline)
        XCTAssertTrue(repo.calls.isEmpty, "rien n'est parti, rien n'attend")
        XCTAssertFalse(viewModel.state.saving)
        XCTAssertEqual(viewModel.state.step, .name)
    }

    func testAFailureIsCalmKeepsTheNameAndLetsTheButtonBeUsedAgain() async {
        repo.createResult = .failed(.refused)
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()
        await waitUntil { !viewModel.state.saving }

        XCTAssertEqual(viewModel.state.message, .generic)
        XCTAssertEqual(viewModel.state.name, "Chez nous")
        XCTAssertTrue(viewModel.state.canSubmit)
        // Aucun événement ni fin d'onboarding sur un échec.
        XCTAssertTrue(sink.events.isEmpty)
        XCTAssertTrue(recorder.entered.isEmpty)
    }

    func testASecondMiffIsNotTheFirstOne() async {
        repo.createPreparation = .ready(CreateReady(premium: true, firstMiff: false))
        repo.createResult = .created(
            MiffCreation(miffId: testMiffId, joinCode: testCode, name: "Chez nous", firstMiff: false)
        )
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()
        await waitUntil { viewModel.state.step == .ready }

        XCTAssertEqual(sink.events.first?.params["is_first_miff"] as? Bool, false)
    }

    func testNeedingPremiumSaysNothingAboutPremiumJustTheHonestOneMiffSentence() async {
        repo.createPreparation = .needsPremium
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()
        await waitUntil { !viewModel.state.saving }

        XCTAssertEqual(viewModel.state.message, .oneMiff)
        XCTAssertFalse(repo.calls.contains("create"))
    }

    func testWithoutAProfileSaysItHonestlyAndAsksTheRoutingAgain() async {
        repo.createResult = .failed(.noProfile)
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()
        await waitUntil { !viewModel.state.saving }

        XCTAssertEqual(viewModel.state.message, .noProfile)
        XCTAssertEqual(recorder.profileMissing, 1)
    }

    func testNothingIsTypedOnceTheMiffIsCreated() async {
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")
        viewModel.onSubmit()
        await waitUntil { viewModel.state.step == .ready }

        viewModel.onNameChanged("Autre nom")

        XCTAssertEqual(viewModel.state.name, "Chez nous")
    }
}
