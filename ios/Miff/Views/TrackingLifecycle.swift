import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : il ne fait que dire au suivi des lieux « l'app est passée au premier plan / en arrière-plan ».
// Il ne lit aucune position, n'envoie aucun événement et ne journalise rien. Le moteur décide seul s'il a le droit de faire quoi que
// ce soit (accord, « Toujours » et position exacte, partage sous le consentement de version 3).

/// Les deux occasions de ticket §6.5 qui viennent de l'écran : à l'ouverture et à la fermeture (passage en arrière-plan) de l'app, la
/// position est enregistrée et la liste des 20 lieux les plus proches est recalculée.
struct TrackingLifecycle: ViewModifier {
    let runtime: TrackingRuntime
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .onAppear {
                if scenePhase == .active {
                    Task { @MainActor in
                        runtime.appBecameActive()
                    }
                }
            }
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .active:
                    Task { @MainActor in
                        runtime.appBecameActive()
                    }
                case .background:
                    Task { @MainActor in
                        runtime.appWentToBackground()
                    }
                default:
                    break
                }
            }
    }
}

extension View {
    func trackingLifecycle(_ runtime: TrackingRuntime) -> some View {
        modifier(TrackingLifecycle(runtime: runtime))
    }
}
