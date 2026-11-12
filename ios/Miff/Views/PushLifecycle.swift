import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : il dit seulement au registre des appareils « regarde s'il y a quelque chose à écrire ». Il ne lit aucune
// position, n'envoie aucun événement et ne journalise rien. Le registraire décide seul s'il a le droit de faire quoi que ce soit (compte,
// session confirmée, accord de la personne, autorisation du système) ; quand rien n'a changé, il ne coûte rien.

/// Les occasions de regarder le registre des appareils : l'app passe au premier plan, et la session devient utilisable (connexion, reprise,
/// arrivée sur le Feed).
struct PushLifecycle: ViewModifier {
    let push: PushServices
    @ObservedObject var session: SessionObservable
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .onAppear {
                if scenePhase == .active {
                    push.sync()
                }
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    push.sync()
                }
            }
            .onChange(of: session.state) { _ in
                // La session peut devenir utilisable APRÈS le retour au premier plan (connexion, accord en attente) : on regarde de nouveau.
                push.sync()
            }
    }
}

extension View {
    func pushLifecycle(_ push: PushServices, session: SessionObservable) -> some View {
        modifier(PushLifecycle(push: push, session: session))
    }
}
