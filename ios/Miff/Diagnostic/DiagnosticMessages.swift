#if DEBUG || INTERNAL
import Foundation
import UIKit

/// Clés de texte des résultats du diagnostic. Source unique pour l'affichage (vues) ET pour l'annonce vocale
/// (VoiceOver) : le résultat d'un test apparaît après un appui, il doit être lu sans que l'utilisateur ait à le chercher.
enum DiagnosticMessages {
    static func key(for state: Test1State) -> String? {
        guard case .done(let outcome) = state else {
            return nil
        }
        switch outcome {
        case .rulesActive: return "diag_test1_ok"
        case .rulesOpen: return "diag_test1_open"
        case .offline: return "diag_test1_offline"
        case .inconclusive: return "diag_test1_inconclusive"
        }
    }

    static func key(for state: Test2State) -> String? {
        switch state {
        case .idle, .running:
            return nil
        case .inputProblem(let problem):
            return inputProblemKey(problem)
        case .done(let result):
            switch result {
            case .success: return "diag_test2_success"
            case .signInFailed(let failure): return signInFailureKey(failure)
            case .readDenied: return "diag_test2_read_denied"
            case .readFailed: return "diag_test2_read_failed"
            }
        }
    }

    static func key(for state: AppCheckState) -> String? {
        guard case .done(let outcome) = state else {
            return nil
        }
        return outcome == .tokenObtained ? "diag_appcheck_ok" : "diag_appcheck_none"
    }

    static func key(for feedback: EventFeedback) -> String {
        switch feedback {
        case .sent: return "diag_event_sent"
        case .disabled: return "diag_event_disabled"
        case .refused: return "diag_event_refused"
        case .locationRefused: return "diag_event_location_refused"
        case .locationNotRefused: return "diag_event_location_unexpected"
        }
    }

    static func inputProblemKey(_ problem: CredentialsInputProblem) -> String {
        switch problem {
        case .emailEmpty: return "diag_input_email_empty"
        case .emailInvalid: return "diag_input_email_invalid"
        case .passwordEmpty: return "diag_input_password_empty"
        case .tooLong: return "diag_input_too_long"
        }
    }

    static func signInFailureKey(_ failure: SignInFailure) -> String {
        switch failure {
        case .invalidCredentials: return "diag_signin_invalid"
        case .network: return "diag_signin_network"
        case .tooManyAttempts: return "diag_signin_too_many"
        case .accountDisabled: return "diag_signin_disabled"
        case .unknown: return "diag_signin_unknown"
        }
    }

    /// Texte complet d'un retour d'événement (la raison du refus y est insérée).
    static func text(for feedback: EventFeedback) -> String {
        if case .refused(let reason) = feedback {
            return L10n.format(key(for: feedback), reason.identifier)
        }
        return L10n.string(key(for: feedback))
    }
}

/// Annonce vocale d'un texte (VoiceOver), sans effet si VoiceOver est éteint.
enum VoiceOverAnnouncer {
    static func announce(_ text: String) {
        UIAccessibility.post(notification: .announcement, argument: text)
    }
}
#endif
