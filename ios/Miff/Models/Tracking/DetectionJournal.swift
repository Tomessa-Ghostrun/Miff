import Foundation

// CONFIDENTIALITÉ, pour ce fichier : le journal local de la détection est un OUTIL de la recette du PM (critère VIE2) : le TYPE d'événement,
// l'identifiant d'un lieu, l'heure et la batterie. AUCUNE coordonnée, AUCUNE adresse, AUCUN nom, aucun texte libre (les seules choses
// qu'on peut y écrire sont les valeurs de `JournalKind`). Il n'existe que dans les builds Debug et Internal (TestFlight) : le build de
// publication reçoit `NoDetectionJournal`, qui ne fait rien, et le fichier qui écrit sur le disque n'y est même pas compilé
// (`FileDetectionJournal.swift`, sous `#if DEBUG || INTERNAL`). Miroir de `DetectionJournal.kt` d'Android (LOC-2b), plus les types
// propres à iOS (état des régions, Actualisation en arrière-plan, mode économie d'énergie).

/// Tout ce que le journal peut dire. Une liste FERMÉE exprès : un texte libre pourrait porter un lieu ou une position un jour.
enum JournalKind: String, CaseIterable {
    case armed
    case disarmed
    case transitionEnter
    case transitionExit
    case regionInside
    case regionOutside
    case positionRead
    case positionUnavailable
    case statusWritten
    case statusWaiting
    case statusGivenUp
    case statusRefused
    case positionWritten
    case positionSkippedGuard
    case breakerStatus
    case breakerPosition
    case presenceUnknown
    case notEligible
    case wakeNetwork
    case wakeRearm
    case wakeSignificantMove
    case backgroundRefreshOff
    case lowPowerMode
    case monitoringFailed
    case batteryRefreshed
    case batteryRefreshSkipped
    /// BATT-3 : la dernière position du système (moins de 2 min) a servi à l'ouverture ou à la sortie, sans nouvelle demande.
    case positionReused
    /// REPRISE du partage d'une Miff : la présence a été recalculée (zone actuelle) et sera réécrite, au lieu de rester grise.
    case statusAfterResume
    /// Diagnostic de « Position introuvable » à la publication d'une photo (recette build 24) : CODES de cause seulement.
    /// Le suivi n'a pas écrit ma position : hors ligne, au moment de l'écriture.
    case positionSkippedOffline
    /// Le suivi n'a pas écrit ma position : la session de l'appareil (SESS-3) n'était pas confirmée.
    case positionSkippedSession
    /// Le serveur a refusé l'écriture de ma position (consentement, partage, règles, App Check).
    case positionRefused
    /// L'Update a constaté que ma position n'est pas publiée dans au moins une Miff (copie locale du suivi sans trace d'écriture).
    case updatePositionNotPublished
    /// ... et le suivi ne connaît même pas cette Miff (partage ou consentement de version 3 absents de sa copie locale).
    case updateTrackingUnawareOfMiff
    /// L'Update s'est arrêté sur l'autorisation de la position (pas « Toujours », position approximative, localisation éteinte).
    case updatePermissionBlocked
    /// La mesure du toucher de « Publier » n'a rien donné d'utilisable (pas de signal, ou position de plus de 10 minutes).
    case updateMeasureFailed
    /// L'Update a remis sa mesure au suivi et celui-ci a publié ma position (ma photo peut alors partir).
    case updateHandOverWorked
    /// ... ou n'a pu la publier nulle part : voir les lignes du suivi juste avant (`notEligible`, `positionRefused`, `positionSkipped...`).
    case updateHandOverFailed
    /// ... ou la remise a dépassé son délai (le suivi était occupé) et rien n'était publié à la relecture : réessayer plus tard suffit.
    case updateHandOverTimedOut
    /// DIAGNOSTIC de l'échec d'une publication de photo (codes de classe seulement) : l'Update a répondu « pas de connexion » parce que...
    /// ... le téléphone dit n'avoir aucun réseau,
    case updateOfflineNoNetwork
    /// ... la session de l'appareil n'a pas pu être vérifiée auprès du serveur,
    case updateOfflineSessionUnverified
    /// ... aucun identifiant de session confirmé n'a pu être obtenu,
    case updateOfflineNoClearance
    /// ... ou la transaction a échoué avec cette classe d'erreur :
    case updateErrorUnavailable
    case updateErrorCancelled
    case updateErrorQuota
    case updateErrorTimedOut
    case updateErrorNetworkUrl
    case updateErrorRefused
    case updateErrorOther
}

/// Une ligne du journal telle qu'elle est gardée et montrée.
struct JournalLine: Equatable {
    let atMillis: Int
    let kind: JournalKind
    let placeId: String?
    let batteryPct: Int?
}

protocol DetectionJournal {
    func record(kind: JournalKind, placeId: String?, batteryPct: Int?)

    /// Les lignes gardées, de la plus ancienne à la plus récente.
    func lines() -> [JournalLine]

    /// Efface tout (déconnexion, suppression du compte, perte d'accès).
    func clear()
}

extension DetectionJournal {
    func record(_ kind: JournalKind, placeId: String? = nil, batteryPct: Int? = nil) {
        record(kind: kind, placeId: placeId, batteryPct: batteryPct)
    }
}

/// Le journal du build de publication : il ne garde rien et n'a rien à montrer.
struct NoDetectionJournal: DetectionJournal {
    func record(kind: JournalKind, placeId: String?, batteryPct: Int?) {}

    func lines() -> [JournalLine] {
        []
    }

    func clear() {}
}
