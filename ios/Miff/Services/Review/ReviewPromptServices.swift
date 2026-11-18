import Foundation

/// Ce que l'environnement de l'app garde pour la fenêtre d'avis (`docs/tickets/pop-ins-avis.md`). En publication (Release), le drapeau de
/// compilation `REVIEW_PROMPT` n'existe pas : cette structure est VIDE, rien de la fenêtre n'est compilé, et aucun arrêt de session n'est
/// ajouté.
struct ReviewPromptServices {
    #if REVIEW_PROMPT
    /// Créé UNE fois pour l'app : les hôtes de l'écran sont recréés (retour de la porte des autorisations), pas lui.
    let coordinator: ReviewPromptCoordinator
    /// Le MÊME magasin que celui de la fenêtre : la carte du diagnostic (Debug et Internal) le lit et le remet à zéro.
    let store: ReviewStateStore
    #endif
    /// Fin de session (déconnexion, suppression du compte, session perdue) : l'état de la fenêtre est effacé, la personne suivante repart
    /// de zéro (comme Android).
    let teardowns: [SessionTeardown]

    @MainActor
    static func make() -> ReviewPromptServices {
        #if REVIEW_PROMPT
        // « Montre la fenêtre au prochain retour » (carte du diagnostic) : builds Debug et Internal seulement, jamais en publication.
        let store = ReviewStateFileStore()
        let coordinator = ReviewPromptCoordinator(
            store: store,
            debugForceAllowed: BuildFlavor.includesDiagnostic,
            observesForeground: true
        )
        let teardown: SessionTeardown = { @MainActor in coordinator.reset() }
        return ReviewPromptServices(coordinator: coordinator, store: store, teardowns: [teardown])
        #else
        return ReviewPromptServices(teardowns: [])
        #endif
    }
}
