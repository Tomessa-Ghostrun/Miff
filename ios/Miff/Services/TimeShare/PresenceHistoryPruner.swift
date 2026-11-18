import Combine
import Foundation

// CONFIDENTIALITÉ, pour ce fichier : quand la liste des Miffs de la personne est CONFIRMÉE par le serveur (jamais une copie du cache du
// téléphone), le journal de présence de toute autre Miff (quittée, retirée, supprimée, accès perdu) est effacé (critère TS-5). Même critère que
// « Depuis ta dernière visite » (`LastVisitTracker.watch`). Aucune lecture ni écriture Firestore, aucun événement.

@MainActor
final class PresenceHistoryPruner {
    private let history: PresenceHistory
    private var subscription: AnyCancellable?

    init(history: PresenceHistory, directory: MiffDirectory) {
        self.history = history
        subscription = directory.$state.sink { [weak self] state in
            if case .ready(let ready) = state, !ready.fromCache {
                self?.history.retainOnly(miffIds: Set(ready.miffs.map { $0.id }))
            }
        }
    }
}
