import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : le nom d'un lieu est sensible. Il vit en mémoire seulement (jamais dans un état de restauration,
// un journal ou un fichier) et part avec la session.

/// Ce que la liste des lieux dit calmement après un geste, une fois : un lieu enregistré (« Le lieu « X » est enregistré. Tes
/// proches le prendront en compte dès qu'ils ouvriront Miff… », le nom seulement), ou un lieu qui n'existe plus.
enum PlaceNotice: Equatable, CustomStringConvertible {
    case saved(name: String)
    case gone

    var description: String {
        "PlaceNotice(redacted)"
    }
}

/// Le message montré sur la liste des lieux (maquette 04L : pas d'écran de réussite, le formulaire se ferme sur la liste). Il vit
/// en mémoire seulement et s'en va avec la session. L'écran qui le montre appelle `consume()` quand la personne le quitte.
@MainActor
final class PlaceNotices: ObservableObject {
    @Published private(set) var notice: PlaceNotice?

    func saved(name: String) {
        notice = .saved(name: name)
    }

    func gone() {
        notice = .gone
    }

    func consume() {
        notice = nil
    }

    /// Fin de session : plus rien n'est gardé.
    func stop() {
        notice = nil
    }
}
