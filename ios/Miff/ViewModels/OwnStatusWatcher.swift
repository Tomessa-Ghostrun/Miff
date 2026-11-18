import Foundation

// CONFIDENTIALITÉ, COÛT et ARCHITECTURE, pour ce fichier : MON statut dans la Miff affichée, rien d'un autre membre, en mémoire pour la session.
//
// D19 : le Feed est l'écouteur de ma fiche tant qu'il est à l'écran (`FeedViewModel` donne MON statut à `MyStatusStore`). Quand l'écran
// principal montre un AUTRE onglet (Profil, Update), le Feed n'écoute plus : sans ce suivi, le compte à rebours du Profil resterait sur l'ancien
// repère après une update publiée (CD-24) ou affirmerait encore « Tu es en dehors de tes lieux de confiance » après l'arrivée dans un lieu
// (CD-22). Ce suivi lit donc MON SEUL statut (un document) et le donne au même `MyStatusStore`. Il ne tourne JAMAIS en même temps que celui du
// Feed (l'hôte l'arrête quand le Feed est l'onglet en usage) : aucune lecture en double, une seule source à la fois.
// Aucune règle Firestore n'est changée : la lecture est celle que mes règles autorisent déjà pour le Feed.

@MainActor
final class OwnStatusWatcher {
    private let observer: OwnStatusObserver
    private let currentUid: () -> String?
    /// Mon accord est attendu pour cette Miff (« Pas maintenant ») : rien n'est partagé, le Feed ne donne alors aucun statut non plus.
    private let isWaitingAgreement: (String) -> Bool
    private let store: MyStatusStore

    init(
        observer: OwnStatusObserver,
        currentUid: @escaping () -> String?,
        isWaitingAgreement: @escaping (String) -> Bool,
        store: MyStatusStore
    ) {
        self.observer = observer
        self.currentUid = currentUid
        self.isWaitingAgreement = isWaitingAgreement
        self.store = store
    }

    /// Écoute MON statut dans `miffId` tant que l'appelant reste (la tâche est annulée au changement d'onglet, d'état de l'app ou de Miff).
    func watch(miffId: String?) async {
        guard let miffId, let uid = currentUid() else {
            return
        }
        for await feed in observer.observe(miffId: miffId, uid: uid) {
            guard !Task.isCancelled else {
                return
            }
            apply(feed, miffId: miffId)
        }
    }

    private func apply(_ feed: OwnStatusFeed, miffId: String) {
        switch feed {
        case .data(let status):
            // Comme le Feed : pas de statut quand mon accord est attendu ; un document absent est « pas de statut ».
            let facts: MyStatusFacts? = isWaitingAgreement(miffId) ? nil : status.map { MyStatusFacts($0) }
            store.update(facts, sharingStopped: store.sharingStopped)
        case .ended:
            // Les règles ne laissent lire que mon statut publié avec mon partage actif : une fin veut dire « plus de statut lisible » (le
            // Feed dit la même chose, nil). « Partage arrêté » reste ce que le Feed a dit en dernier (il le calcule avec les membres).
            store.update(nil, sharingStopped: store.sharingStopped)
        }
    }
}
