#if DEBUG || INTERNAL
import Combine
import Foundation

enum Test1State: Equatable {
    case idle
    case running
    case done(RulesProbeOutcome)
}

enum Test2State: Equatable {
    case idle
    case running
    case inputProblem(CredentialsInputProblem)
    case done(Test2Result)
}

enum AppCheckState: Equatable {
    case idle
    case running
    case done(AppCheckOutcome)
}

enum EventFeedback: Equatable {
    case sent
    case disabled
    case refused(RefusalReason)
    case locationRefused
    case locationNotRefused
}

/// État et actions de l'écran de diagnostic (MVVM). Même comportement que sur Android.
/// Le mot de passe est transmis tel quel au dépôt et n'est JAMAIS conservé dans l'état.
@MainActor
final class DiagnosticViewModel: ObservableObject {
    @Published private(set) var test1: Test1State = .idle
    @Published private(set) var test2: Test2State = .idle
    @Published private(set) var appCheck: AppCheckState = .idle
    @Published private(set) var eventFeedback: EventFeedback?

    private let repository: DiagnosticRepository
    private let tracker: AnalyticsTracker

    init(repository: DiagnosticRepository, tracker: AnalyticsTracker) {
        self.repository = repository
        self.tracker = tracker
    }

    var firebaseProjectId: String? {
        repository.firebaseProjectId
    }

    func runTest1() async {
        guard test1 != .running else {
            return
        }
        test1 = .running
        let outcome = await repository.probeRulesUnauthenticated()
        test1 = .done(outcome)
    }

    func runTest2(email: String, password: String) async {
        guard test2 != .running else {
            return
        }
        if let problem = CredentialsInput.check(email: email, password: password) {
            test2 = .inputProblem(problem)
            return
        }
        test2 = .running
        let result = await repository.signInAndReadOwnDocuments(email: email, password: password)
        test2 = .done(result)
    }

    func checkAppCheck() async {
        guard appCheck != .running else {
            return
        }
        appCheck = .running
        let outcome = await repository.fetchAppCheckToken()
        appCheck = .done(outcome)
    }

    func sendTestEvent() {
        switch tracker.track(.diagnosticTest) {
        case .sent:
            eventFeedback = .sent
        case .disabled:
            eventFeedback = .disabled
        case .refused(let reason, _):
            eventFeedback = .refused(reason)
        }
    }

    /// Aide QA : tente d'envoyer un événement qui porte un paramètre évoquant une position. Doit toujours être refusé.
    func tryToSendPosition() {
        let result = tracker.track(AnalyticsContract.eventDiagnosticTest, params: ["lat": 0, "lng": 0])
        if case .refused = result {
            eventFeedback = .locationRefused
        } else {
            eventFeedback = .locationNotRefused
        }
    }

    /// Provoque volontairement un plantage pour vérifier Crashlytics (bouton actif seulement si les rapports de
    /// plantage sont activés). Ne contient aucune donnée personnelle.
    func triggerTestCrash() {
        fatalError("Miff diagnostic: deliberate test crash")
    }
}
#endif
