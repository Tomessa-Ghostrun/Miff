import Foundation

/// Comment s'est terminée la suppression d'une Miff (une exécution de `MiffDeletion`).
enum MiffDeletionResult: Equatable {
    /// La Miff a disparu (supprimée par cette exécution, ou par une précédente dont la réponse s'était perdue).
    case deleted
    /// La personne n'est pas le créateur de cette Miff : rien n'a été fait (seul le créateur supprime une Miff).
    case notOwner
    /// Arrêtée avant la fin (pas de réseau, le serveur a dit non) : rien n'est perdu, relancer reprend où elle s'était arrêtée.
    case paused(MiffFailure)
}

/// Où en est la suppression d'UNE Miff, telle que les écrans la disent (toujours calme : jamais un décompte de ce qui reste).
enum DeletionState: Equatable {
    /// En cours. `resumed` : elle a été commencée par un lancement précédent de l'app.
    case running(resumed: Bool)
    /// En pause faute de réseau : elle repart toute seule quand le réseau revient.
    case waitingForNetwork
    /// Arrêtée après plusieurs essais pour une autre raison : la personne peut réessayer, et le prochain lancement réessaie.
    case stuck
    case done
}

/// Ce que la suppression dit à l'écran qui n'est pas celui de la Miff (banderole courte, l'équivalent du message système
/// d'Android).
enum DeletionNotice: Equatable {
    /// « On reprend la suppression de ta Miff… » : une suppression non terminée est reprise au lancement.
    case resumed
    /// « La Miff a été supprimée. » : une suppression se termine pendant que la personne est ailleurs. L'identifiant est celui
    /// de la Miff (jamais son nom).
    case completed(miffId: String)
}
