import Foundation

/// Les règles de la coquille à onglets, en fonctions pures (testées sans interface) : quel onglet est vraiment affiché, et quand la
/// barre est visible.
enum MainShellRules {
    /// Une personne sans aucune Miff (état lu, liste vide) : elle n'a ni barre, ni Profil, ni Réglages (décision Q13 de l'onboarding).
    static func hasNoMiff(_ directory: DirectoryState) -> Bool {
        if case .ready(let ready) = directory {
            return ready.miffs.isEmpty
        }
        return false
    }

    /// L'état des Miffs est connu ET la personne en a au moins une.
    static func hasMiffs(_ directory: DirectoryState) -> Bool {
        if case .ready(let ready) = directory {
            return !ready.miffs.isEmpty
        }
        return false
    }

    /// L'onglet réellement montré : quand la dernière Miff disparaît (la personne la quitte ou la supprime depuis le Profil, ou perd
    /// l'accès), on retombe sur le Feed, qui montre « Pas encore de Miff » avec « Créer » et « Rejoindre », jamais sur un Profil sans
    /// barre pour en sortir.
    static func effectiveTab(selected: MainTab, hasNoMiff: Bool) -> MainTab {
        hasNoMiff ? .feed : selected
    }

    /// La barre n'est visible que sur un écran racine, sans clavier, et quand l'état des Miffs est lu avec au moins une Miff (pas de
    /// clignotement pendant le chargement ni hors connexion sans cache, pas de barre sans Miff).
    static func barVisible(keyboardVisible: Bool, pathIsEmpty: Bool, directory: DirectoryState) -> Bool {
        !keyboardVisible && pathIsEmpty && hasMiffs(directory)
    }
}
