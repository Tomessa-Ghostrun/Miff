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
