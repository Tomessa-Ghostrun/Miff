import Combine
import Foundation

// CNC-I (ticket docs/tickets/centre-notifications-complet.md §6, point 6) : quand la liste des Miffs de la personne est CONFIRMÉE par le serveur, les lignes du
// centre qui parlent d'un proche d'une Miff qui n'est plus la sienne (quittée, supprimée, accès perdu) disparaissent de l'écran aussitôt, et les visages
// gardés en mémoire pour cette Miff s'en vont avec elles. Ce que le serveur garde de ces lignes (le journal) est supprimé par sa règle d'expiration au plus
// tard. Même principe que « Depuis ta dernière visite » (`LastVisitTracker.watch`). Rien n'est lu ni écrit sur le serveur.

@MainActor
final class LovedOneLinesWatcher {
    private let center: NotificationCenterService
    private let faces: CenterFaces
    private var subscription: AnyCancellable?

    init(center: NotificationCenterService, faces: CenterFaces) {
        self.center = center
        self.faces = faces
    }

    /// La liste des Miffs de la personne est connue : toute autre Miff est oubliée.
    func onMiffsKnown(_ miffIds: Set<String>) {
        center.removeLovedOneLines { !miffIds.contains($0.miffId) }
        faces.retainOnly(miffIds: miffIds)
    }

    /// Suit l'annuaire des Miffs : seule une liste confirmée par le serveur (jamais une copie du téléphone) décide de ce qui est oublié.
    func watch(_ directory: MiffDirectory) {
        subscription = directory.$state.sink { [weak self] state in
            if case .ready(let ready) = state, !ready.fromCache {
                self?.onMiffsKnown(Set(ready.miffs.map { $0.id }))
            }
        }
    }
}
