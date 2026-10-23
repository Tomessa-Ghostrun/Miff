#if DEBUG || INTERNAL
import SwiftUI

// Affichage des résultats des tests (messages au ton neutre et bienveillant, jamais alarmistes ; les situations
// qui exigent une action humaine disent clairement « merci de prévenir l'équipe »).

struct Test1ResultView: View {
    let state: Test1State

    var body: some View {
        switch state {
        case .idle:
            EmptyView()
        case .running:
            DiagLine("diag_test1_running")
        case .done(.rulesActive):
            DiagLine("diag_test1_ok", .strong)
            DiagLine("diag_test1_ok_detail")
        case .done(.rulesOpen):
            DiagLine("diag_test1_open", .warning)
        case .done(.offline):
            DiagLine("diag_test1_offline")
        case .done(.inconclusive):
            DiagLine("diag_test1_inconclusive")
        }
    }
}

struct Test2ResultView: View {
    let state: Test2State

    var body: some View {
        switch state {
        case .idle:
            EmptyView()
        case .running:
            DiagLine("diag_test2_running")
        case .inputProblem(let problem):
            DiagLine(verbatim: L10n.string(Self.inputProblemKey(problem)), .warning)
        case .done(let result):
            resultView(result)
        }
    }

    @ViewBuilder
    private func resultView(_ result: Test2Result) -> some View {
        switch result {
        case .success(let profileExists, let offer):
            DiagLine("diag_test2_success", .strong)
            DiagLine(profileExists ? "diag_test2_profile_found" : "diag_test2_profile_missing")
            offerLine(offer)
        case .signInFailed(let failure):
            DiagLine(verbatim: L10n.string(Self.signInFailureKey(failure)))
        case .readDenied:
            DiagLine("diag_test2_read_denied", .warning)
        case .readFailed:
            DiagLine("diag_test2_read_failed")
        }
    }

    @ViewBuilder
    private func offerLine(_ offer: OfferState) -> some View {
        switch offer {
        case .classic:
            DiagLine("diag_test2_offer_classic")
        case .premium(let expiresAt):
            // Date formatée par le système, dans la langue de l'app.
            let date = expiresAt.formatted(date: .abbreviated, time: .omitted)
            DiagLine(verbatim: L10n.format("diag_test2_offer_premium", date))
        }
    }

    private static func inputProblemKey(_ problem: CredentialsInputProblem) -> String {
        switch problem {
        case .emailEmpty: return "diag_input_email_empty"
        case .emailInvalid: return "diag_input_email_invalid"
        case .passwordEmpty: return "diag_input_password_empty"
        case .tooLong: return "diag_input_too_long"
        }
    }

    private static func signInFailureKey(_ failure: SignInFailure) -> String {
        switch failure {
        case .invalidCredentials: return "diag_signin_invalid"
        case .network: return "diag_signin_network"
        case .tooManyAttempts: return "diag_signin_too_many"
        case .accountDisabled: return "diag_signin_disabled"
        case .unknown: return "diag_signin_unknown"
        }
    }
}

struct AppCheckResultView: View {
    let state: AppCheckState

    var body: some View {
        switch state {
        case .idle:
            EmptyView()
        case .running:
            DiagLine("diag_appcheck_running")
        case .done(.tokenObtained):
            DiagLine("diag_appcheck_ok", .strong)
        case .done(.noToken):
            DiagLine("diag_appcheck_none")
        }
    }
}

struct EventFeedbackView: View {
    let feedback: EventFeedback

    var body: some View {
        switch feedback {
        case .sent:
            DiagLine("diag_event_sent", .strong)
        case .disabled:
            DiagLine("diag_event_disabled")
        case .refused(let reason):
            DiagLine(verbatim: L10n.format("diag_event_refused", reason.rawValue.lowercased()))
        case .locationRefused:
            DiagLine("diag_event_location_refused", .strong)
        case .locationNotRefused:
            DiagLine("diag_event_location_unexpected", .warning)
        }
    }
}
#endif
