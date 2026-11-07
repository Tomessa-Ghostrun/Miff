import Foundation

// CONFIDENTIALITÉ, pour ce fichier : MON statut dans la Miff affichée (présence, heures, `hasPhoto`), en mémoire pour la session. Aucune
// coordonnée, aucun nom. Rien n'est journalisé ni envoyé comme événement.
//
// D19 : le bouton central, l'anneau de ma photo et la bulle d'incitation suivent la Miff AFFICHÉE dans le Feed. UN SEUL écouteur de mon
// statut doit les nourrir tous. Tant que le Feed iOS (FEED-5) n'est pas livré, c'est `follow` qui l'écoute (un document : 1 lecture au début,
// puis une par changement, soit quelques-unes par jour) ; quand le Feed arrive, il appellera `update(_:)` avec SON écouteur et `follow` ne
// sera plus lancé (le signal est écrit dans `ios/README.md`).

/// L'écoute de MON statut dans une Miff, sans Firestore dans l'interface : un double dans les tests.
protocol MyStatusObserving {
    /// Mon statut dans `miffId`, à chaque changement ; nil quand il n'existe pas (partage arrêté, pas encore écrit). Le flux s'arrête quand
    /// on l'abandonne.
    func observe(miffId: String, uid: String) -> AsyncStream<MyStatusFacts?>
}

@MainActor
final class MyStatusStore: ObservableObject {
    @Published private(set) var facts: MyStatusFacts?

    private let observer: MyStatusObserving
    private let currentUid: () -> String?

    init(observer: MyStatusObserving, currentUid: @escaping () -> String?) {
        self.observer = observer
        self.currentUid = currentUid
    }

    /// Le Feed (ou tout propriétaire de l'écouteur unique) donne MON statut.
    func update(_ facts: MyStatusFacts?) {
        self.facts = facts
    }

    /// Écoute mon statut dans la Miff affichée jusqu'à l'annulation de la tâche (le changement de Miff relance l'écoute). Sans Miff
    /// affichée ou sans compte, il n'y a rien à suivre.
    func follow(miffId: String?) async {
        guard let miffId, let uid = currentUid() else {
            facts = nil
            return
        }
        // La Miff a changé : le statut de l'autre n'est jamais montré le temps de la lecture.
        facts = nil
        for await latest in observer.observe(miffId: miffId, uid: uid) {
            if Task.isCancelled {
                return
            }
            facts = latest
        }
    }

    /// Fin de session : plus rien en mémoire.
    func stop() {
        facts = nil
    }
}
