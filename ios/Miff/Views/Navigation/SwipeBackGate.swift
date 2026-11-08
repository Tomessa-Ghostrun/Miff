import ObjectiveC
import UIKit

private var gateStorageKey: UInt8 = 0

/// Le gardien du geste de retour (balayage depuis le bord gauche de l'écran) d'UNE navigation.
///
/// Pourquoi : tous les écrans de l'app masquent la barre de navigation du système (`.toolbar(.hidden, for: .navigationBar)`) et
/// dessinent leur propre flèche de retour. Or UIKit refuse alors le geste natif : son délégué par défaut ne l'autorise que lorsque la
/// barre est visible. Le gardien prend la place de ce délégué, avec une règle explicite (`SwipeBackPolicy`) : le geste commence s'il
/// y a un écran où revenir, qu'aucun écran n'a posé de verrou et qu'aucune transition n'est en cours.
///
/// Les écrans qui ne se quittent pas par geste (opération en cours, pas de flèche de retour) posent un verrou par `SwipeBackLock`.
/// Un gardien est rattaché à sa navigation (il disparaît avec elle).
@MainActor
final class SwipeBackGate: NSObject, UIGestureRecognizerDelegate {
    private weak var navigation: UINavigationController?
    /// Les écrans qui verrouillent, retenus FAIBLEMENT : un écran désalloué sans avoir pu se déclarer libre ne bloque jamais le geste.
    private var lockers: [ObjectIdentifier: WeakOwner] = [:]

    init(navigation: UINavigationController) {
        self.navigation = navigation
        super.init()
    }

    private struct WeakOwner {
        weak var object: AnyObject?
    }

    /// Le nombre d'écrans qui verrouillent le geste en ce moment (ceux qui ont disparu ne comptent plus).
    var lockCount: Int {
        lockers = lockers.filter { $0.value.object != nil }
        return lockers.count
    }

    /// Le gardien de cette navigation (créé à la première demande).
    static func gate(for navigation: UINavigationController) -> SwipeBackGate {
        if let existing = objc_getAssociatedObject(navigation, &gateStorageKey) as? SwipeBackGate {
            return existing
        }
        let created = SwipeBackGate(navigation: navigation)
        objc_setAssociatedObject(navigation, &gateStorageKey, created, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        return created
    }

    /// La navigation qui porte cet écran : celle du contrôleur, ou la première trouvée en remontant les parents puis la vue.
    static func findNavigation(containing controller: UIViewController) -> UINavigationController? {
        if let direct = controller.navigationController {
            return direct
        }
        var parent = controller.parent
        while let current = parent {
            if let navigation = current as? UINavigationController {
                return navigation
            }
            parent = current.parent
        }
        var responder: UIResponder? = controller.isViewLoaded ? controller.view : nil
        while let current = responder {
            if let navigation = current as? UINavigationController {
                return navigation
            }
            responder = current.next
        }
        return nil
    }

    /// Prend la place du délégué du geste de retour et s'assure qu'il est actif.
    func install() {
        guard let recognizer = navigation?.interactivePopGestureRecognizer else {
            return
        }
        if recognizer.delegate !== self {
            recognizer.delegate = self
        }
        if !recognizer.isEnabled {
            recognizer.isEnabled = true
        }
    }

    /// Un écran pose (ou retire) son verrou. Le verrou est lié à l'écran lui-même, pas à son état passé.
    func setLocked(_ locked: Bool, owner: AnyObject) {
        let identifier = ObjectIdentifier(owner)
        if locked {
            lockers[identifier] = WeakOwner(object: owner)
        } else {
            lockers[identifier] = nil
        }
    }

    /// Le geste peut-il commencer maintenant ?
    func mayBegin() -> Bool {
        SwipeBackPolicy.gestureMayBegin(
            stackDepth: navigation?.viewControllers.count ?? 0,
            lockCount: lockCount,
            isTransitioning: navigation?.transitionCoordinator != nil
        )
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        mayBegin()
    }
}
