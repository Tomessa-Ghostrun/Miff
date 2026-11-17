import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : il dit seulement à « Depuis ta dernière visite » quand l'app part en arrière-plan et quand elle revient
// devant (pour savoir si l'absence a duré 30 minutes ou plus). Il ne lit aucune position, n'envoie aucun événement et ne journalise rien.

/// Le cycle de vie de la scène : `.background` note l'heure du départ, `.active` dit que l'app est de retour. Un simple passage par
/// `.inactive` (centre de contrôle, sélecteur d'applications, fenêtre du système) n'est pas une absence.
struct LastVisitLifecycle: ViewModifier {
    let visit: LastVisitTracker
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .background:
                    visit.onBackground()
                case .active:
                    visit.onForeground()
                default:
                    break
                }
            }
    }
}

extension View {
    func lastVisitLifecycle(_ visit: LastVisitTracker) -> some View {
        modifier(LastVisitLifecycle(visit: visit))
    }
}
