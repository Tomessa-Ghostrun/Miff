import Foundation

// La « deuxième chance » (ticket §8.1, étape 3) : après la première update publiée, si la fenêtre du système n'a jamais été montrée
// (la personne a choisi « Plus tard »), UNE carte discrète du Feed propose d'activer les rappels. Non modale, jamais relancée par une
// fenêtre, jamais plus d'une fois : elle part dès que la personne a répondu, quelle que soit la réponse. Si le téléphone a déjà
// refusé, rien n'est proposé (la personne a choisi ; l'état reste visible dans le centre et les Réglages).

struct SecondChanceState: Equatable {
    /// Une première update a été vue publiée par cette personne.
    var firstUpdateSeen = false
    /// La personne a répondu à la carte (Activer ou Pas maintenant).
    var answered = false
}

enum SecondChanceRule {
    static func isDue(authorization: NotificationAuthorization, state: SecondChanceState) -> Bool {
        authorization == .notDetermined && state.firstUpdateSeen && !state.answered
    }
}

/// Où la carte se souvient d'où elle en est (un double en mémoire dans les tests). Un jeu par compte.
protocol NotificationSecondChanceStore: AnyObject {
    func state(uid: String) -> SecondChanceState
    func save(_ state: SecondChanceState, uid: String)
    /// Oublie ce compte (suppression du compte).
    func forget(uid: String)
}
