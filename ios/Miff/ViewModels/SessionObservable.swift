import Foundation
#if canImport(Combine)
import Combine
#endif

/// Le reflet observable de l'état de la session, pour l'interface. `SessionManager` n'est pas un `ObservableObject` (sa
/// logique reste du Swift pur, testé sans interface) : ce petit objet s'abonne à ses changements d'état et les publie.
@MainActor
final class SessionObservable: ObservableObject {
    @Published private(set) var state: SessionState
    /// Une suppression de compte a été ANNULÉE parce qu'une Miff est apparue pendant qu'elle tournait (AUTH-5c) : un message calme est
    /// montré par-dessus l'app jusqu'à ce qu'il soit compris.
    @Published private(set) var deletionBlocked = false

    init(manager: SessionManager) {
        state = manager.state
        manager.onStateChange = { [weak self] newState in
            self?.state = newState
        }
        manager.onDeletionBlockedChange = { [weak self] blocked in
            self?.deletionBlocked = blocked
        }
    }
}
