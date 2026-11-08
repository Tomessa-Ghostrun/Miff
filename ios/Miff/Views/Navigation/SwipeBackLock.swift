import SwiftUI
import UIKit

/// Rend le retour par balayage depuis le bord de l'écran à l'écran qui le porte, et le verrouille pendant que `isLocked` est vrai
/// (écriture en cours : l'écran ne doit pas changer sous elle). SwiftUI n'a pas d'option pour cela sur iOS 16 : on retrouve la
/// navigation UIKit qui porte l'écran et on confie son geste à `SwipeBackGate`.
///
/// - `isLocked == false` suffit à réactiver le geste (les écrans masquent la barre du système, ce qui le coupait) ; poser ceci sur
///   chaque écran empilé est sans effet de plus ;
/// - le verrou ne vaut que tant que l'écran est visible : un écran verrouillé recouvert par un autre ne verrouille pas celui-ci,
///   et le verrou se retire dès que l'écran disparaît ;
/// - sans navigation (étape téléphone seule, aperçus), rien ne se passe ;
/// - les écrans décident par `SwipeBackScreen` ; les view models refusent de toute façon le retour pendant une écriture (par exemple
///   `ProfileViewModel.onBackToProfile`) : ceci évite en plus le va-et-vient visible de l'écran.
struct SwipeBackLock: UIViewControllerRepresentable {
    let isLocked: Bool

    init(isLocked: Bool = false) {
        self.isLocked = isLocked
    }

    func makeUIViewController(context: Context) -> LockController {
        LockController()
    }

    func updateUIViewController(_ controller: LockController, context: Context) {
        controller.isLocked = isLocked
        controller.apply()
    }

    final class LockController: UIViewController {
        var isLocked = false
        /// Vrai entre « l'écran va disparaître » et « l'écran va revenir » : pendant ce temps, aucun verrou.
        private var isLeaving = false
        private var gate: SwipeBackGate?

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            if parent == nil {
                release()
            } else {
                apply()
            }
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            isLeaving = false
            apply()
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            isLeaving = false
            apply()
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            // Un écran ajouté à un parent déjà affiché (l'étape suivante d'un même écran) ne reçoit pas toujours « apparu » : la
            // première mise en page, vue dans la fenêtre, le remplace.
            apply()
        }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            isLeaving = true
            release()
        }

        func apply() {
            guard let navigation = SwipeBackGate.findNavigation(containing: self) else {
                return
            }
            let current = SwipeBackGate.gate(for: navigation)
            current.install()
            // Le verrou ne vaut que pour un écran visible : un écran recouvert (sa vue n'est plus dans la fenêtre) ne verrouille pas
            // celui qui est par-dessus.
            let isVisible = !isLeaving && isViewLoaded && view.window != nil
            current.setLocked(isLocked && isVisible, owner: self)
            gate = current
        }

        private func release() {
            gate?.setLocked(false, owner: self)
        }
    }
}

extension View {
    /// Le retour par balayage depuis le bord de l'écran, rendu à cet écran (voir `SwipeBackLock`).
    func swipeBackEnabled() -> some View {
        background(SwipeBackLock())
    }

    /// Le retour par balayage de cet écran, selon la table `SwipeBackScreen`.
    func swipeBack(for screen: SwipeBackScreen) -> some View {
        background(SwipeBackLock(isLocked: screen.locksSwipeBack))
    }
}
