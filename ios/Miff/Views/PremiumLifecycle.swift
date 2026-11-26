import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : il dit seulement au coordinateur de l'achat « regarde si un achat attend sa vérification ». Il ne lit aucune position, n'envoie aucun
// événement et ne journalise rien. Le coordinateur décide seul s'il a quelque chose à faire (compte connecté, achat présent chez le store, serveur qui ne l'accorde pas
// encore, au plus une fois toutes les 5 minutes) ; quand rien n'a changé, il ne coûte rien. Il ne demande JAMAIS l'identification Apple (la restauration est un geste de la
// personne).

/// Les occasions de regarder si un achat attend : l'app passe au premier plan, et la session devient utilisable (connexion, reprise). Sans effet quand l'achat est éteint.
struct PremiumLifecycle: ViewModifier {
    let premium: PremiumServices
    @ObservedObject var session: SessionObservable
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .onAppear {
                if scenePhase == .active {
                    look()
                }
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    look()
                }
            }
            .onChange(of: session.state) { _ in
                look()
            }
    }

    private func look() {
        guard let coordinator = premium.coordinator, case .ready = session.state else {
            return
        }
        Task {
            await coordinator.reconcile()
        }
    }
}

extension View {
    func premiumLifecycle(_ premium: PremiumServices, session: SessionObservable) -> some View {
        modifier(PremiumLifecycle(premium: premium, session: session))
    }
}
