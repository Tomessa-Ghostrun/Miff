#if DEBUG || INTERNAL
import SwiftUI

// BUILDS DE TEST SEULEMENT (Debug et Internal, donc le build TestFlight du PM : le diagnostic s'y ouvre par l'appui long de 1,5 s en haut à gauche de l'écran). Cette carte montre le
// compteur de l'offre Miff+ périodique (un entier) et l'état de la session, et le pose pour l'essayer sans ouvrir l'app des dizaines de fois (PAY-PER-I,
// docs/tickets/paywall-periodique.md). Rien n'est envoyé, rien n'est écrit sur le serveur ; le seuil lui-même (10) ne change JAMAIS : seul le compteur est posé. Texte de
// diagnostic, en français seulement, jamais traduit (l'écran de diagnostic est pour l'équipe). Miroir de `PeriodicPaywallDebugCard.kt` d'Android.

/// La carte « Offre périodique (essai) » du diagnostic : le compteur et la session, « compteur = seuil − 1 », « compteur = seuil », « compteur = 0 ».
struct PeriodicPaywallDebugCard: View {
    let services: PeriodicPaywallServices
    @ObservedObject private var beacon: PeriodicPaywallBeacon
    @State private var summary = ""

    init(services: PeriodicPaywallServices) {
        self.services = services
        _beacon = ObservedObject(wrappedValue: services.beacon)
    }

    private var threshold: Int {
        services.rules.thresholdForDiagnostic
    }

    var body: some View {
        SectionCard(verbatim: "Offre périodique (essai)", section: .periodicPaywall) {
            DiagLine(verbatim: summary)
            DiagLine(verbatim: Self.tapNote, .description)
            choice(Self.almostLabel(threshold: threshold), value: threshold - 1)
            choice(Self.dueLabel(threshold: threshold), value: threshold)
            choice("Compteur = 0", value: 0)
        }
        .onAppear { refresh() }
        .onChange(of: beacon.tick) { _ in refresh() }
    }

    private func choice(_ label: String, value: Int) -> some View {
        Button {
            services.rules.setCounterForDiagnostic(value)
            refresh()
        } label: {
            Text(verbatim: label)
        }
        .buttonStyle(.miffPrimaryFullWidth)
    }

    private func refresh() {
        summary = Self.describe(
            counter: services.rules.counterForDiagnostic(),
            threshold: threshold,
            session: services.rules.sessionForDiagnostic()
        )
    }

    /// Un appui dans le diagnostic compte comme le premier appui de la session : l'offre peut donc s'ouvrir environ 2 secondes après sa fermeture. C'est normal.
    static let tapNote = "Un appui dans cet écran compte comme ton premier appui de la session : "
        + "l'offre peut s'ouvrir environ 2 secondes après sa fermeture. "
        + "Elle ne vient jamais par-dessus une feuille, un menu, une alerte ou un message de l'app."

    static func almostLabel(threshold: Int) -> String {
        "Compteur = \(max(threshold - 1, 0)) (seuil moins un : la prochaine ouverture rend l'offre due)"
    }

    static func dueLabel(threshold: Int) -> String {
        "Compteur = \(threshold) (offre due, dès le prochain appui)"
    }

    /// Texte de diagnostic, en français seulement : le compteur, le seuil et l'état de la session (rien de personnel).
    static func describe(counter: Int, threshold: Int, session: PeriodicPaywallRules.SessionSnapshot) -> String {
        let place = session.appOpen ? "app ouverte" : "hors de l'app"
        let tap = session.firstTapDone ? "fait" : "pas encore"
        let shown = session.shownThisSession ? "oui" : "non"
        let excluded = session.excluded ? "oui" : "non"
        return "compteur : \(counter) / \(threshold) ; session : \(place) ; premier appui : \(tap) ; "
            + "offre déjà montrée : \(shown) ; session exclue (alerte) : \(excluded) ; "
            + "surcouches ouvertes : \(session.overlaysOpen)"
    }
}
#endif
