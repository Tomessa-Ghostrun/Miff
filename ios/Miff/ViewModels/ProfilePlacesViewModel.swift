import Foundation
#if canImport(Combine)
import Combine
#endif

// CONFIDENTIALITÉ et COÛT, pour ce fichier : les lieux (noms, adresses) restent en mémoire, dans ce view model seulement (un par session :
// la personne suivante ne les rencontre jamais) ; rien n'est journalisé ni envoyé dans un événement. Les règles de lecture (ce qui est
// réutilisé, ce qui est lu, à quelle fréquence) sont celles de `MiffPlacesBook` ; la lecture unique est celle de `PlacesReader`. Aucun
// écouteur n'est jamais attaché ici.

/// La Miff sur laquelle le carrousel s'est arrêté, et ce que l'on sait de ses lieux. `selectedMiffId` est nil tant qu'une carte de Miff
/// n'a pas été montrée.
struct ProfilePlacesState: Equatable, CustomStringConvertible {
    var selectedMiffId: String?
    var view: MiffPlacesView = .loading

    var description: String {
        "ProfilePlacesState(redacted)"
    }
}

/// Les lieux sous le carrousel de l'onglet Profil (décision du PM, 2026-11-11) : la liste de la Miff dont la carte est regardée, pour
/// TOUS les membres de cette Miff (pas seulement son créateur).
///
/// - La Miff que le Feed a lue : ses lieux sont donnés par `seedFromFeed` et dessinés SANS lecture.
/// - Les autres : lues UNE fois quand leur carte apparaît (`onCardShown`), puis réutilisées 5 minutes.
/// - Le créateur ouvre un lieu ou le formulaire d'un nouveau (`onPlacesMayChange`) : les listes sont relues à leur prochaine apparition.
@MainActor
final class ProfilePlacesViewModel: ObservableObject {
    @Published private(set) var state = ProfilePlacesState()

    private var book: MiffPlacesBook
    private let reader: PlacesReader
    /// Les Miffs que la personne a maintenant, une fois connues : une lecture qui finit après le départ d'une Miff est jetée.
    private var knownMiffIds: Set<String>?
    /// Un créateur a ouvert un lieu ou un formulaire depuis la dernière fois que la liste a été remise à jour.
    private var changePending = false

    init(reader: PlacesReader, clock: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }) {
        self.reader = reader
        self.book = MiffPlacesBook(clock: clock)
    }

    /// Le Feed a lu les lieux de cette Miff (une copie du téléphone que le serveur n'a pas confirmée n'est pas donnée : une liste vide
    /// seulement parce que rien n'était encore gardé ne doit jamais être dessinée comme « aucun lieu »).
    func seedFromFeed(miffId: String, places: [Place]) {
        book.seedFromFeed(miffId: miffId, places: places)
        publish()
    }

    /// La carte de cette Miff est celle que la personne regarde : ses lieux sont montrés, lus si, et seulement si, le registre le dit.
    func onCardShown(miffId: String) {
        let view = book.view(miffId)
        if state.selectedMiffId != miffId || state.view != view {
            state = ProfilePlacesState(selectedMiffId: miffId, view: view)
        }
        guard book.needsReading(miffId) else {
            return
        }
        book.readingStarted(miffId)
        publish()
        let reader = self.reader
        Task { [weak self] in
            let answer = await reader.read(miffId: miffId)
            self?.finishReading(miffId: miffId, answer: answer)
        }
    }

    /// Le Profil revient à l'écran (au retour d'un lieu ou d'un formulaire) : la liste regardée est relue si elle est périmée.
    func onProfileAppeared() {
        changePending = false
        if let selected = state.selectedMiffId {
            onCardShown(miffId: selected)
        }
    }

    /// « Réessayer » : relit tout de suite (une lecture déjà en cours n'est pas doublée).
    func onRetry() {
        guard let selected = state.selectedMiffId else {
            return
        }
        book.invalidate(selected)
        onCardShown(miffId: selected)
    }

    /// Le créateur ouvre un lieu ou le formulaire d'un nouveau : quoi qu'il s'y passe, les listes sont relues à leur retour.
    func onPlacesMayChange() {
        book.invalidateAll()
        changePending = true
    }

    /// La pile de navigation est revenue à l'écran racine : si un créateur a touché un lieu depuis, la liste regardée est relue (au cas où
    /// l'onglet Profil n'annoncerait pas son retour). Sans changement en attente, rien n'est lu.
    func onReturnedToRoot() {
        if changePending {
            onProfileAppeared()
        }
    }

    /// Les Miffs de la personne maintenant : les lieux de toute autre sont oubliés.
    func retainOnly(_ miffIds: Set<String>) {
        knownMiffIds = miffIds
        book.retainOnly(miffIds)
        if let selected = state.selectedMiffId, !miffIds.contains(selected) {
            state = ProfilePlacesState()
        }
        publish()
    }

    private func finishReading(miffId: String, answer: PlacesRead) {
        if let known = knownMiffIds, !known.contains(miffId) {
            return
        }
        switch answer {
        case .loaded(let places):
            book.readingSucceeded(miffId, places: places)
        case .failed:
            book.readingFailed(miffId)
        }
        publish()
    }

    private func publish() {
        guard let selected = state.selectedMiffId else {
            return
        }
        let view = book.view(selected)
        if state.view != view {
            state = ProfilePlacesState(selectedMiffId: selected, view: view)
        }
    }
}
