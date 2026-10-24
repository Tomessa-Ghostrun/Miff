#if DEBUG || INTERNAL
import XCTest
@testable import Miff

/// Comportement de l'écran de diagnostic (sans réseau : faux dépôt Firebase).
@MainActor
final class DiagnosticViewModelTests: XCTestCase {
    private final class FakeRepository: DiagnosticRepository {
        var projectId: String? = "miff-3d283"
        var probe: RulesProbeOutcome = .rulesActive
        var test2: Test2Result = .success(profileExists: false, offer: .classic)
        var appCheck: AppCheckOutcome = .noToken
        private(set) var signInCalls: [String] = []
        private(set) var probeCalls = 0

        var firebaseProjectId: String? {
            projectId
        }

        func probeRulesUnauthenticated() async -> RulesProbeOutcome {
            probeCalls += 1
            return probe
        }

        func signInAndReadOwnDocuments(email: String, password: String) async -> Test2Result {
            signInCalls.append(email)
            return test2
        }

        func fetchAppCheckToken() async -> AppCheckOutcome {
            appCheck
        }
    }

    /// Annonces vocales (VoiceOver) émises par le view model.
    private final class Announcements {
        var texts: [String] = []
    }

    private func makeViewModel(
        _ repository: FakeRepository = FakeRepository(),
        analyticsEnabled: Bool = false,
        sink: RecordingSink = RecordingSink(),
        announcements: Announcements = Announcements()
    ) -> DiagnosticViewModel {
        let tracker = AnalyticsTracker(sink: sink, isEnabled: { analyticsEnabled })
        return DiagnosticViewModel(
            repository: repository,
            tracker: tracker,
            announce: { announcements.texts.append($0) }
        )
    }

    func testTheResultOfEachTestIsAnnouncedToVoiceOver() async {
        let announcements = Announcements()
        let viewModel = makeViewModel(analyticsEnabled: true, announcements: announcements)

        await viewModel.runTest1()
        await viewModel.runTest2(email: "test@example.com", password: "secret")
        await viewModel.checkAppCheck()
        viewModel.sendTestEvent()

        XCTAssertEqual(announcements.texts.count, 4)
        XCTAssertEqual(announcements.texts.first, L10n.string("diag_test1_ok"))
        XCTAssertEqual(announcements.texts.last, L10n.string("diag_event_sent"))
    }

    func testNothingIsAnnouncedWhileATestIsRunningOrBeforeAnyTest() {
        let announcements = Announcements()
        _ = makeViewModel(announcements: announcements)

        XCTAssertTrue(announcements.texts.isEmpty)
    }

    func testInvalidInputIsReportedWithoutStartingTheTestAndIsAnnounced() {
        let announcements = Announcements()
        let repository = FakeRepository()
        let viewModel = makeViewModel(repository, announcements: announcements)

        XCTAssertFalse(viewModel.prepareTest2(email: "pas-un-email", password: "secret"))

        XCTAssertEqual(viewModel.test2, .inputProblem(.emailInvalid))
        XCTAssertEqual(announcements.texts, [L10n.string("diag_input_email_invalid")])
        XCTAssertTrue(repository.signInCalls.isEmpty)
        XCTAssertTrue(viewModel.prepareTest2(email: "a@b.co", password: "x"))
    }

    func testARefusedEventNamesItsReasonInSnakeCase() {
        let text = DiagnosticMessages.text(for: .refused(.unknownEvent))

        XCTAssertTrue(text.contains("unknown_event"), text)
        XCTAssertFalse(text.contains("unknownevent"), text)
    }

    func testTheProjectShownComesFromTheRepository() {
        let repository = FakeRepository()
        repository.projectId = "autre-projet"

        XCTAssertEqual(makeViewModel(repository).firebaseProjectId, "autre-projet")
    }

    func testTest1ReportsActiveRulesWhenTheReadIsRefused() async {
        let viewModel = makeViewModel()
        XCTAssertEqual(viewModel.test1, .idle)

        await viewModel.runTest1()

        XCTAssertEqual(viewModel.test1, .done(.rulesActive))
    }

    func testTest1FlagsOpenRulesWithoutAmbiguity() async {
        let repository = FakeRepository()
        repository.probe = .rulesOpen
        let viewModel = makeViewModel(repository)

        await viewModel.runTest1()

        XCTAssertEqual(viewModel.test1, .done(.rulesOpen))
    }

    func testTest2NeverContactsFirebaseWithObviouslyInvalidInput() async {
        let repository = FakeRepository()
        let viewModel = makeViewModel(repository)

        await viewModel.runTest2(email: "", password: "x")
        XCTAssertEqual(viewModel.test2, .inputProblem(.emailEmpty))

        await viewModel.runTest2(email: "pas-un-email", password: "x")
        XCTAssertEqual(viewModel.test2, .inputProblem(.emailInvalid))

        await viewModel.runTest2(email: "a@b.co", password: "")
        XCTAssertEqual(viewModel.test2, .inputProblem(.passwordEmpty))

        XCTAssertTrue(repository.signInCalls.isEmpty)
    }

    func testTest2ReadsTheDocumentsOfTheTestAccount() async {
        let repository = FakeRepository()
        repository.test2 = .success(profileExists: true, offer: .classic)
        let viewModel = makeViewModel(repository)

        await viewModel.runTest2(email: "test@example.com", password: "secret")

        XCTAssertEqual(viewModel.test2, .done(.success(profileExists: true, offer: .classic)))
        XCTAssertEqual(repository.signInCalls, ["test@example.com"])
    }

    func testTest2ShowsAKindFailureWhenTheSignInFails() async {
        let repository = FakeRepository()
        repository.test2 = .signInFailed(.invalidCredentials)
        let viewModel = makeViewModel(repository)

        await viewModel.runTest2(email: "test@example.com", password: "mauvais")

        XCTAssertEqual(viewModel.test2, .done(.signInFailed(.invalidCredentials)))
    }

    func testTheTestEventIsNotSentWhileAnalyticsIsOff() {
        let sink = RecordingSink()
        let viewModel = makeViewModel(analyticsEnabled: false, sink: sink)

        viewModel.sendTestEvent()

        XCTAssertEqual(viewModel.eventFeedback, .disabled)
        XCTAssertTrue(sink.logged.isEmpty)
    }

    func testTheTestEventIsSentWhenAnalyticsIsOn() {
        let sink = RecordingSink()
        let viewModel = makeViewModel(analyticsEnabled: true, sink: sink)

        viewModel.sendTestEvent()

        XCTAssertEqual(viewModel.eventFeedback, .sent)
        XCTAssertEqual(sink.logged, ["analytics_diagnostic_test"])
    }

    func testAnEventWithAPositionIsAlwaysRefused() {
        let sink = RecordingSink()
        let viewModel = makeViewModel(analyticsEnabled: true, sink: sink)

        viewModel.tryToSendPosition()

        XCTAssertEqual(viewModel.eventFeedback, .locationRefused)
        XCTAssertTrue(sink.logged.isEmpty)
    }

    func testAppCheckCheckReportsTheOutcome() async {
        let repository = FakeRepository()
        repository.appCheck = .tokenObtained
        let viewModel = makeViewModel(repository)

        await viewModel.checkAppCheck()

        XCTAssertEqual(viewModel.appCheck, .done(.tokenObtained))
    }
}
#endif
