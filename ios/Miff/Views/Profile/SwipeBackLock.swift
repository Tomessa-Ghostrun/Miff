import SwiftUI
import UIKit

/// Désactive le balayage « retour » depuis le bord de l'écran pendant que `isLocked` est vrai (écriture du profil en
/// cours : l'écran ne doit pas changer sous elle). SwiftUI n'a pas d'option pour cela sur iOS 16 : on retrouve la
/// navigation UIKit qui porte l'écran et on coupe son geste. Sans navigation (étape téléphone seule), rien ne se passe ;
/// quand l'écran disparaît, le geste est toujours rendu. Le view model refuse de toute façon le retour pendant l'écriture
/// (`ProfileViewModel.onBackToProfile`) : ceci évite en plus le va-et-vient visible de l'écran.
struct SwipeBackLock: UIViewControllerRepresentable {
    let isLocked: Bool

    func makeUIViewController(context: Context) -> LockController {
        LockController()
    }

    func updateUIViewController(_ controller: LockController, context: Context) {
        controller.isLocked = isLocked
        controller.apply()
    }

    final class LockController: UIViewController {
        var isLocked = false

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            apply()
        }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            navigationController?.interactivePopGestureRecognizer?.isEnabled = true
        }

        func apply() {
            navigationController?.interactivePopGestureRecognizer?.isEnabled = !isLocked
        }
    }
}
