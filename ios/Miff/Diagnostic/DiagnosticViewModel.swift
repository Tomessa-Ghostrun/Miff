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
    private let announce: (String) -> Void

    /// `announce` : annonce vocale (VoiceOver) du résultat d'un test, remplaçable dans les tests.
    init(
        repository: DiagnosticRepository,
        tracker: AnalyticsTracker,
        announce: @escaping (String) -> Void = VoiceOverAnnouncer.announce
    ) {
        self.repository = repository
        self.tracker = tracker
        self.announce = announce
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
        if let key = DiagnosticMessages.key(for: test1) {
            announce(L10n.string(key))
        }
    }

    /// Contrôle local de la saisie, SANS rien envoyer. Renvoie `false` (et affiche le problème) si la saisie est
    /// manifestement vide ou mal formée : l'écran garde alors le mot de passe saisi, pour que l'utilisateur corrige
    /// l'adresse sans tout retaper.
    func prepareTest2(email: String, password: String) -> Bool {
        guard let problem = CredentialsInput.check(email: email, password: password) else {
            return true
        }
        test2 = .inputProblem(problem)
        announceTest2()
        return false
    }

    func runTest2(email: String, password: String) async {
        guard test2 != .running, prepareTest2(email: email, password: password) else {
            return
        }
        test2 = .running
        let result = await repository.signInAndReadOwnDocuments(email: email, password: password)
        test2 = .done(result)
        announceTest2()
    }

    func checkAppCheck() async {
        guard appCheck != .running else {
            return
        }
        appCheck = .running
        let outcome = await repository.fetchAppCheckToken()
        appCheck = .done(outcome)
        if let key = DiagnosticMessages.key(for: appCheck) {
            announce(L10n.string(key))
        }
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
        announceEventFeedback()
    }

    /// Aide QA : tente d'envoyer un événement qui porte un paramètre évoquant une position. Doit toujours être refusé.
    func tryToSendPosition() {
        let result = tracker.track(AnalyticsContract.eventDiagnosticTest, params: ["lat": 0, "lng": 0])
        if case .refused = result {
            eventFeedback = .locationRefused
        } else {
            eventFeedback = .locationNotRefused
        }
        announceEventFeedback()
    }

    private func announceTest2() {
        if let key = DiagnosticMessages.key(for: test2) {
            announce(L10n.string(key))
        }
    }

    private func announceEventFeedback() {
        if let feedback = eventFeedback {
            announce(DiagnosticMessages.text(for: feedback))
        }
    }

    /// Provoque volontairement un plantage pour vérifier Crashlytics (bouton actif seulement si les rapports de
    /// plantage sont activés). Ne contient aucune donnée personnelle.
    func triggerTestCrash() {
        fatalError("Miff diagnostic: deliberate test crash")
    }
}
#endif
