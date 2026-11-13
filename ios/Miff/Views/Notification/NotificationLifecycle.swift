import SwiftUI
import UIKit

// CONFIDENTIALITÉ, pour ce fichier : il dit seulement aux notifications « il est peut-être temps de se remettre d'accord avec le système »
// (retour au premier plan, changement d'heure ou de fuseau, changement de langue). Il ne lit aucune position, n'envoie aucun événement
// et ne journalise rien.

/// Les occasions qui viennent de l'écran : l'app passe au premier plan (les rappels sont recalculés d'après les dernières données connues),
/// l'heure ou le fuseau change (les délais sont recalculés), la langue change (le texte d'une notification est figé à la programmation).
struct NotificationLifecycle: ViewModifier {
    let reconciler: NotificationReconciler
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .onAppear {
                if scenePhase == .active {
                    reconcile()
                }
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    reconcile()
                }
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
}

extension View {
    func notificationLifecycle(_ reconciler: NotificationReconciler) -> some View {
        modifier(NotificationLifecycle(reconciler: reconciler))
    }
}
