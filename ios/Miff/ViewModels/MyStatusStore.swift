import Foundation

// CONFIDENTIALITÉ, pour ce fichier : MON statut dans la Miff affichée (présence, heures, `hasPhoto`), en mémoire pour la session. Aucune
// coordonnée, aucun nom. Rien n'est journalisé ni envoyé comme événement.
//
// D19 : le bouton central, l'anneau de ma photo et la bulle d'incitation suivent la Miff AFFICHÉE dans le Feed, et UN SEUL écouteur de ma
// fiche les nourrit tous : celui du Feed (`FeedViewModel`), qui appelle `update(_:sharingStopped:)` à chaque calcul. L'Update n'ouvre
// aucun écouteur ni aucune lecture pour cela. Le Feed n'écoute que pendant qu'il est à l'écran (comme sur Android) : sur l'Update et sur
// le Profil, la couleur est celle du dernier calcul, recalculée chaque minute avec l'horloge.

@MainActor
final class MyStatusStore: ObservableObject {
    @Published private(set) var facts: MyStatusFacts?
    /// Je ne partage plus dans la Miff affichée (état J de l'Update) : dit par le Feed, jamais lu ici.
    @Published private(set) var sharingStopped = false

    init() {}

    /// Le Feed (l'unique écouteur) donne MON statut ; nil quand je ne partage pas ou que mon accord est attendu.
    func update(_ facts: MyStatusFacts?, sharingStopped: Bool = false) {
        if self.facts != facts {
            self.facts = facts
        }
        if self.sharingStopped != sharingStopped {
            self.sharingStopped = sharingStopped
        }
    }

    /// Fin de session : plus rien en mémoire.
    func stop() {
        update(nil, sharingStopped: false)
    }
}
