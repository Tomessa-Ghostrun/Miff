import UIKit

/// Du temps de plus (quelques dizaines de secondes, accordées par le système) quand l'app passe en arrière-plan pendant une
/// étape de la suppression d'une Miff : une étape en cours se termine plutôt que d'être coupée net (ticket §8, colonne iOS :
/// « tâche d'arrière-plan courte autorisée, puis reprise au lancement »). Passé ce délai le système suspend l'app ; la
/// suppression reprend alors à l'ouverture suivante (marqueur local) : rien ne tourne en cachette plus longtemps.
@MainActor
final class UIKitBackgroundExecution: BackgroundExecution {
    private final class Handle {
        var identifier = UIBackgroundTaskIdentifier.invalid
    }

    private var handles: [Int: Handle] = [:]

    func begin() -> Int {
        let handle = Handle()
        handle.identifier = UIApplication.shared.beginBackgroundTask(withName: "miff-deletion") {
            // Le temps accordé est écoulé : la tâche est rendue (le système suspendrait l'app sinon).
            if handle.identifier != .invalid {
                UIApplication.shared.endBackgroundTask(handle.identifier)
                handle.identifier = .invalid
            }
        }
        let token = handle.identifier.rawValue
        handles[token] = handle
        return token
    }

    func end(_ token: Int) {
        guard let handle = handles.removeValue(forKey: token), handle.identifier != .invalid else {
            return
        }
        UIApplication.shared.endBackgroundTask(handle.identifier)
        handle.identifier = .invalid
    }
}
