import Foundation

// Le GESTE de maintien de l'alerte (ticket 7.2 et 7.4), en fonction pure du temps pour être testé sans écran. Le même geste déclenche une
// alerte (sur le bouton rond de l'Update) et la termine (sur la bannière). Miroir d'`AlertHold.kt` d'Android.
//
// Phases, depuis l'instant où le doigt se pose :
// - `pressing`, pendant `armMillis` : rien ne change à l'écran (un simple appui long n'est pas une alerte, et un appui court est une photo) ;
// - `holding`, pendant `holdMillis` : l'écran est en mode alerte, la barre rouge se remplit (`fraction` de 0 à 1) ;
// - `done` : les 5 secondes sont tenues, l'alerte part (ou prend fin).
// Relâcher avant `done` annule, SANS message.

enum DangerHoldPhase: Equatable {
    case pressing
    /// 0,0 (juste armé) à 1,0 (sur le point de partir).
    case holding(fraction: Double)
    case done
}

enum DangerAlertHold {
    static func phase(
        heldMillis: Int,
        armMillis: Int = DangerAlertRules.armMillis,
        holdMillis: Int = DangerAlertRules.holdMillis
    ) -> DangerHoldPhase {
        if heldMillis < armMillis {
            return .pressing
        }
        if heldMillis >= armMillis + holdMillis {
            return .done
        }
        let fraction = Double(heldMillis - armMillis) / Double(max(holdMillis, 1))
        return .holding(fraction: min(max(fraction, 0), 1))
    }
}

/// Ce que devient un toucher sur le bouton, décidé à chaque événement. `DangerHoldTracker` ne connaît ni l'écran ni l'horloge du système :
/// les tests lui donnent des instants.
enum DangerHoldOutcome: Equatable {
    /// Rien de nouveau à faire.
    case nothing
    /// Le toucher a été plus court que l'armement : c'est un APPUI (une photo, comme avant).
    case tap
    /// L'armement vient d'être atteint : l'écran passe en mode alerte.
    case armed
    /// Les 5 secondes sont tenues : l'alerte part.
    case done
    /// Un geste armé est annulé avant la fin (relâché, app en arrière-plan, appel entrant, annulation du système) : sans message. Le doigt qui sort du
    /// bouton n'annule PAS.
    case cancelled
}

/// L'état d'UN toucher, sans temporisateur : l'appelant lui donne des instants (le début, puis des mesures) et applique le résultat.
struct DangerHoldTracker {
    private(set) var startedAtMillis: Int?
    private(set) var armed = false
    private var finished = false
    let armMillis: Int
    let holdMillis: Int

    init(armMillis: Int = DangerAlertRules.armMillis, holdMillis: Int = DangerAlertRules.holdMillis) {
        self.armMillis = armMillis
        self.holdMillis = holdMillis
    }

    var isTouching: Bool {
        startedAtMillis != nil && !finished
    }

    /// La progression de la barre, de 0 à 1 ; 0 tant que l'écran n'est pas armé.
    func progress(nowMillis: Int) -> Double {
        guard armed, let startedAtMillis else {
            return 0
        }
        if case .holding(let fraction) = DangerAlertHold.phase(
            heldMillis: nowMillis - startedAtMillis, armMillis: armMillis, holdMillis: holdMillis
        ) {
            return fraction
        }
        return 1
    }

    /// Le doigt se pose.
    mutating func begin(atMillis: Int) {
        startedAtMillis = atMillis
        armed = false
        finished = false
    }

    /// Le temps passe (appelé souvent pendant le toucher) : l'armement puis la fin sont atteints à leur instant.
    mutating func advance(nowMillis: Int) -> DangerHoldOutcome {
        guard let startedAtMillis, !finished else {
            return .nothing
        }
        switch DangerAlertHold.phase(
            heldMillis: nowMillis - startedAtMillis, armMillis: armMillis, holdMillis: holdMillis
        ) {
        case .pressing:
            return .nothing
        case .holding:
            if armed {
                return .nothing
            }
            armed = true
            return .armed
        case .done:
            finished = true
            armed = false
            return .done
        }
    }

    /// Le doigt se relève : un appui court est une photo ; un geste armé est annulé ; un geste terminé n'a plus rien à dire.
    mutating func release(nowMillis: Int) -> DangerHoldOutcome {
        defer {
            reset()
        }
        guard let startedAtMillis, !finished else {
            return .nothing
        }
        if armed {
            return .cancelled
        }
        // Un toucher plus court que l'armement est un appui, même si aucun `advance` n'a été appelé entre-temps.
        return nowMillis - startedAtMillis < armMillis ? .tap : .cancelled
    }

    /// Tout ce qui annule un geste (app en arrière-plan, fenêtre qui perd le focus, annulation du système) : un geste armé est annulé sans message,
    /// un toucher pas encore armé n'est ni une photo ni une alerte.
    mutating func cancel() -> DangerHoldOutcome {
        defer {
            reset()
        }
        guard startedAtMillis != nil, !finished, armed else {
            return .nothing
        }
        return .cancelled
    }

    private mutating func reset() {
        startedAtMillis = nil
        armed = false
        finished = false
    }
}
