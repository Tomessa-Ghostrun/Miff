import Foundation
#if canImport(Combine)
import Combine
#endif

/// Le reflet observable de l'état de la session, pour l'interface. `SessionManager` n'est pas un `ObservableObject` (sa
/// logique reste du Swift pur, testé sans interface) : ce petit objet s'abonne à ses changements d'état et les publie.
@MainActor
final class SessionObservable: ObservableObject {
    @Published private(set) var state: SessionState

    init(manager: SessionManager) {
        state = manager.state
        manager.onStateChange = { [weak self] newState in
            self?.state = newState
        }
    }
}
