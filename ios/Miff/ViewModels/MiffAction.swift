import Foundation

// SÉCURITÉ, pour ce fichier : le prénom d'un proche et le NOUVEAU code (rendu par un retrait) vivent en mémoire dans cette
// valeur, le temps de la fenêtre qui les montre, et nulle part ailleurs : jamais dans un journal ni un événement Analytics.

/// La question (ou la réponse) des fenêtres de MIFF-4 : retirer, renouveler, quitter, supprimer (écrans H, I, J, K du ticket).
enum MiffAction: Equatable, CustomStringConvertible {
    /// « Retirer {prénom} de la Miff ? » (le créateur).
    case remove(uid: String, firstName: String)
    /// « Créer un nouveau code ? » (le créateur).
    case renew
    /// « Quitter la Miff « X » ? » (un membre ; jamais le créateur).
    case leave
    /// « Supprimer la Miff « X » ? » (le créateur), avec le nombre des AUTRES membres pour la phrase.
    case delete(othersCount: Int)
    /// Après un retrait : la personne est sortie, et voici le NOUVEAU code, à partager avec qui le créateur veut.
    case removedDone(firstName: String, newCode: String)

    var description: String {
        "MiffAction(redacted)"
    }
}
