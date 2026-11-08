import Foundation

/// Les écrans empilés dans une navigation et ce qu'ils disent du retour par balayage depuis le bord de l'écran (le geste standard
/// d'iOS). Une seule table pour tous : un écran n'est quittable par geste que s'il l'est aussi par sa flèche de retour.
///
/// Un écran qui ne doit PAS être quitté par geste : une opération est en cours (écriture du profil, création d'une Miff, publication
/// ou suppression d'un lieu, ré-authentification de la suppression du compte), ou bien l'écran n'a pas de flèche de retour parce que
/// son seul chemin est un bouton (« Continuer », « Annuler »). L'Update est un onglet de la coquille, jamais empilé : à profondeur 1
/// le geste ne démarre pas. Si un écran de publication y est un jour empilé, il posera `.swipeBack(for:)` avec un cas ici.
enum SwipeBackScreen: Equatable {
    case signUp
    case signIn
    case passwordReset
    case profilePhone(isWriting: Bool)
    case createMiffName(isSaving: Bool)
    /// « Ta Miff est prête » : pas de flèche de retour, « Continuer » est la sortie.
    case createMiffReady
    case joinMiffCode
    /// La confirmation « Rejoindre et partager » : « Annuler » est la sortie.
    case joinMiffConfirm
    case miff
    case placeDetail
    case placeForm(isBusy: Bool)
    case placePaywall
    /// Liste des Réglages, Mes informations, Mon compte, Confidentialité, aide à la détection.
    case settings
    case deleteAccount(isBusy: Bool)

    var allowsSwipeBack: Bool {
        switch self {
        case .signUp, .signIn, .passwordReset, .joinMiffCode, .miff, .placeDetail, .placePaywall, .settings:
            return true
        case .profilePhone(let isWriting):
            return !isWriting
        case .createMiffName(let isSaving):
            return !isSaving
        case .placeForm(let isBusy):
            return !isBusy
        case .deleteAccount(let isBusy):
            return !isBusy
        case .createMiffReady, .joinMiffConfirm:
            return false
        }
    }

    /// Le contraire, pour `SwipeBackLock(isLocked:)`.
    var locksSwipeBack: Bool {
        !allowsSwipeBack
    }
}

/// La décision du geste de retour de la navigation (appelée au début de chaque balayage).
enum SwipeBackPolicy {
    /// Le geste ne commence que s'il y a un écran où revenir (sinon, sur l'écran racine, il fige l'interface), qu'aucun écran n'a posé
    /// de verrou, et qu'aucune transition n'est déjà en cours (un balayage au milieu d'une animation corrompt la pile).
    static func gestureMayBegin(stackDepth: Int, lockCount: Int, isTransitioning: Bool) -> Bool {
        stackDepth > 1 && lockCount == 0 && !isTransitioning
    }
}
