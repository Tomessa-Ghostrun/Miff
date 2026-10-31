// Scénarios de test longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

/// Ce que les view models ont fait savoir à la session, à l'annuaire et au routage.
final class FlowRecorder {
    var entered: [AnalyticsEvent.OnboardingVia] = []
    var directoryChanges = 0
    var profileMissing = 0
}

/// Les view models « Créer » et « Rejoindre » (ticket Miff, critères C, J, K, H). Aucun Firestore : un dépôt double.
@MainActor
final class MiffFlowViewModelsTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let network = FakeNetwork()
    private let sink = EventLogSink()
    private let recorder = FlowRecorder()
    private let time = TestClock()

    private func context() -> MiffFlowContext {
        let log = recorder
        let enabled = AnalyticsTracker(sink: sink, isEnabled: { true })
        return MiffFlowContext(
            repository: repo,
            network: network,
            currentUid: { "uid-1" },
            tracker: enabled,
            onEntered: { via in log.entered.append(via) },
            onDirectoryChanged: { log.directoryChanges += 1 },
            onProfileMissing: { log.profileMissing += 1 }
        )
    }

    private func create() -> CreateMiffViewModel {
        CreateMiffViewModel(context: context())
    }

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

    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<300 where !condition() {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
    }

    // MARK: - B et C : créer

    func testCreateTheButtonIsGreyBelowTwoCharactersActiveFromTwo() {
        let viewModel = create()
        XCTAssertFalse(viewModel.state.canSubmit)

        viewModel.onNameChanged("a")
        XCTAssertFalse(viewModel.state.canSubmit)
        viewModel.onNameChanged("  a ")
        XCTAssertFalse(viewModel.state.canSubmit, "les espaces des deux bouts ne comptent pas")
        viewModel.onNameChanged("ab")
        XCTAssertTrue(viewModel.state.canSubmit)
    }

    func testCreateAtFortyCharactersTheLimitIsSaidAndNothingMoreIsKept() {
        let viewModel = create()

        viewModel.onNameChanged(String(repeating: "a", count: 45))

        XCTAssertEqual(viewModel.state.name.count, 40)
        XCTAssertTrue(viewModel.state.limitReached)
    }

    func testCreateSuccessShowsReadyOnlyAfterTheServerCountsTheEventsAndNothingElseIsSent() async {
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

    func testCreateADoubleTapMakesOneMiff() async {
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()
        viewModel.onSubmit()
        await waitUntil { viewModel.state.step == .ready }

        XCTAssertEqual(repo.created.count, 1)
    }

    func testCreateOfflineSaysSoAtOnceAndTriesNothing() {
        network.online = false
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()

        XCTAssertEqual(viewModel.state.message, .offline)
        XCTAssertTrue(repo.calls.isEmpty, "rien n'est parti, rien n'attend")
        XCTAssertFalse(viewModel.state.saving)
        XCTAssertEqual(viewModel.state.step, .name)
    }

    func testCreateAFailureIsCalmKeepsTheNameAndLetsTheButtonBeUsedAgain() async {
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

    func testCreateASecondMiffIsNotTheFirstOne() async {
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

    func testCreateNeedingPremiumSaysNothingAboutPremiumJustTheHonestOneMiffSentence() async {
        repo.createPreparation = .needsPremium
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()
        await waitUntil { !viewModel.state.saving }

        XCTAssertEqual(viewModel.state.message, .oneMiff)
        XCTAssertFalse(repo.calls.contains("create"))
    }

    func testCreateWithoutAProfileSaysItHonestlyAndAsksTheRoutingAgain() async {
        repo.createResult = .failed(.noProfile)
        let viewModel = create()
        viewModel.onNameChanged("Chez nous")

        viewModel.onSubmit()
        await waitUntil { !viewModel.state.saving }

        XCTAssertEqual(viewModel.state.message, .noProfile)
        XCTAssertEqual(recorder.profileMissing, 1)
    }

    // MARK: - D et E : rejoindre

    func testJoinTheButtonNeeds12ValidCharactersAndALookAlikeIsAnsweredLocally() async {
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

    func testJoinAGoodCodeShowsTheConsentAndWritesNothing() async {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)

        viewModel.onContinue()
        await waitUntil { viewModel.state.step == .confirm }

        XCTAssertEqual(viewModel.state.step, .confirm)
        XCTAssertEqual(viewModel.state.miffName, "Chez nous")
        XCTAssertEqual(repo.calls, ["prepareJoin"])
        XCTAssertTrue(recorder.entered.isEmpty && sink.events.isEmpty)
    }

    func testJoinCancelOnTheConsentWritesNothingAndGoesBackToTheCode() async {
        let viewModel = join()
        viewModel.onCodeChanged(testCode)
        viewModel.onContinue()
        await waitUntil { viewModel.state.step == .confirm }

        viewModel.onCancelConfirm()

        XCTAssertEqual(viewModel.state.step, .code)
        XCTAssertFalse(repo.calls.contains("join"))
    }

    func testJoinOnlyTheButtonOfTheConsentWritesOnceAndTheEventsFollowTheServer() async {
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

    func testJoinWrongRenewedOrDeletedCodeShowTheSameMessage() async {
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

    func testJoinAfterFiveWrongCodesAPauseOfOneMinuteThenItIsOver() async {
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

    func testJoinAlreadyAMemberOffersToOpenTheMiffAndWritesNothing() async {
        repo.joinPreparation = .alreadyMember(miffId: testMiffId, miffName: "Chez nous")
        let viewModel = join()
        viewModel.onCodeChanged(testCode)

        viewModel.onContinue()
        await waitUntil { viewModel.state.alreadyMember != nil }

        XCTAssertEqual(viewModel.state.alreadyMember?.miffId, testMiffId)
        XCTAssertEqual(viewModel.state.step, .code)
        XCTAssertFalse(repo.calls.contains("join"))
    }

    func testJoinOfflineIsAnsweredAtOnceOnTheCodeAndOnTheConsent() async {
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

    func testJoinANetworkProblemDoesNotCountAsAWrongCode() async {
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

    func testJoinAFreeAccountWithAMiffIsToldHonestlyAndItDoesNotCountAsAWrongCode() async {
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

    func testJoinConfirmWithoutAProfileSaysItHonestlyAndAsksTheRoutingAgain() async {
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

    func testJoinAPastedCodeIsCleanedLikeATypedOne() {
        // Le code n'entre que par `onCodeChanged` (frappe ou bouton système « Coller ») : aucun accès au presse-papiers dans
        // le view model ni dans le dépôt. Le texte collé est nettoyé comme ce qui est tapé.
        let viewModel = join()

        viewModel.onCodeChanged("  xtzr-sp36-7988\n")

        XCTAssertEqual(viewModel.state.code, "XTZRSP367988")
        XCTAssertTrue(viewModel.state.canContinue)
    }
}
