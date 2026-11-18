#if DEBUG || INTERNAL
import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : il montre l'état local de la fenêtre d'avis (des dates et l'issue de la dernière apparition, rien de
// personnel) et permet de la provoquer ou de la remettre à zéro. Compilé dans les builds Debug et Internal seulement (le PM la voit sur
// l'iPhone, via TestFlight, sans attendre 7 ou 90 jours). Aucune écriture hors de l'appareil, aucun envoi. Miroir de `ReviewDebugCard.kt`
// d'Android.

/// La carte « Fenêtre d'avis (essai) » du diagnostic : « afficher la fenêtre au prochain retour dans l'app » (elle ignore alors les 7, 30 et
/// 90 jours, mais ne recouvre jamais l'Update, le clavier ni un message de l'app) et « remettre à zéro l'état de la fenêtre ».
struct ReviewDebugCard: View {
    let services: ReviewPromptServices

    @State private var summary = ""
    @State private var armed = false
    @State private var resetDone = false

    var body: some View {
        SectionCard("ios_diag_review_title", section: .review) {
            #if REVIEW_PROMPT
            DiagLine(verbatim: summary)
            Button {
                services.store.setForceNext(true)
                armed = true
                resetDone = false
            } label: {
                Text("ios_diag_review_force")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            if armed {
                DiagLine("ios_diag_review_force_armed", .description)
            }
            Button {
                services.store.write(ReviewState())
                services.store.setForceNext(false)
                armed = false
                resetDone = true
                refresh()
            } label: {
                Text("ios_diag_review_reset")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            if resetDone {
                DiagLine("ios_diag_review_reset_done", .description)
            }
            #endif
        }
        .onAppear {
            refresh()
        }
    }

    private func refresh() {
        #if REVIEW_PROMPT
        summary = Self.describe(services.store.read())
        armed = services.store.forceNext()
        #endif
    }

    #if REVIEW_PROMPT
    /// Texte de diagnostic, en français seulement (l'écran est pour l'équipe, comme sur Android).
    static func describe(_ state: ReviewState) -> String {
        func date(_ millis: Int?) -> String {
            guard let millis else {
                return "jamais"
            }
            return Date(timeIntervalSince1970: Double(millis) / 1000).formatted(date: .abbreviated, time: .standard)
        }
        let form = ReviewFeedbackForm.openableAddress(ReviewFeedbackForm.configuredAddress) != nil
            ? "renseigné (ouvert dans le navigateur)"
            : "non renseigné (écran « Merci de nous le dire ! »)"
        let outcome = state.lastOutcome?.rawValue ?? "aucune"
        return "première Miff vue : \(date(state.firstMiffSeenAtMillis)) ; dernière apparition : "
            + "\(date(state.lastShownAtMillis)) ; issue : \(outcome) ; formulaire externe : \(form)"
    }
    #endif
}
#endif
