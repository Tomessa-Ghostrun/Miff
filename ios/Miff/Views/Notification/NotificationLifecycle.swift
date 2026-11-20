import SwiftUI
import UIKit

// CONFIDENTIALITÉ, pour ce fichier : il dit seulement aux notifications « il est peut-être temps de se remettre d'accord avec le système »
// (retour au premier plan, changement d'heure ou de fuseau, changement de langue). Il ne lit aucune position, n'envoie aucun événement
// et ne journalise rien. CNC-I : il dit aussi au journal des notifications du serveur « regarde s'il y a du neuf » (retour au premier plan, session qui
// devient utilisable) : la synchronisation décide seule si elle a le droit de lire (build, compte, délai d'une minute) ; quand rien n'est dû, elle ne
// coûte rien.

/// Les occasions qui viennent de l'écran : l'app passe au premier plan (les rappels sont recalculés d'après les dernières données connues),
/// l'heure ou le fuseau change (les délais sont recalculés), la langue change (le texte d'une notification est figé à la programmation).
struct NotificationLifecycle: ViewModifier {
    let reconciler: NotificationReconciler
    /// CNC-I : le journal du serveur, relu au retour au premier plan.
    let journal: NotificationJournalSync
    @ObservedObject var session: SessionObservable
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .onAppear {
                if scenePhase == .active {
                    reconcile()
                    syncJournal()
                }
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    reconcile()
                    syncJournal()
                }
            }
            .onChange(of: session.state) { _ in
                // La session peut devenir utilisable APRÈS le retour au premier plan (connexion, reprise) : on regarde de nouveau.
                syncJournal()
            }
            .onReceive(Self.timeChanged) { _ in
                reconcile()
            }
            .onReceive(Self.languageChanged) { _ in
                reconcile()
            }
            // Réglages > Langue : les notifications déjà programmées sont reprogrammées dans la langue choisie (leur texte est figé).
            .onReceive(Self.appLanguageChosen) { _ in
                reconcile()
            }
    }

    private static let timeChanged = NotificationCenter.default.publisher(
        for: UIApplication.significantTimeChangeNotification
    )
    private static let appLanguageChosen = NotificationCenter.default.publisher(for: AppLanguage.changed)
    private static let languageChanged = NotificationCenter.default.publisher(
        for: NSLocale.currentLocaleDidChangeNotification
    )

    private func reconcile() {
        Task { @MainActor in
            await reconciler.reconcile()
        }
    }

    private func syncJournal() {
        Task { @MainActor in
            await journal.sync(.foreground)
        }
    }
}

extension View {
    func notificationLifecycle(
        _ reconciler: NotificationReconciler,
        journal: NotificationJournalSync,
        session: SessionObservable
    ) -> some View {
        modifier(NotificationLifecycle(reconciler: reconciler, journal: journal, session: session))
    }
}
