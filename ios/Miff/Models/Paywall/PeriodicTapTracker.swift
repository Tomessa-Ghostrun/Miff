import Foundation

/// Reconnaît UN appui dans la suite des événements d'un doigt (logique pure : la vue UIKit ne fait que lui passer des nombres). Un appui = un seul doigt, posé et relevé au même
/// endroit (à `slop` points près). Un défilement ou un balayage (le doigt a parcouru plus que `slop`) et un geste à deux doigts ne sont JAMAIS un appui (critère PP8).
///
/// Écart avec Android, voulu et listé dans la PR : Android exige en plus qu'un bouton, une carte ou un onglet ait pris l'appui. iOS ne peut pas le savoir (SwiftUI ne dit pas
/// quelle vue a pris un toucher) : un appui dans le vide compte donc comme premier appui.
struct PeriodicTapTracker {
    /// Le déplacement toléré, en points (celui que le système tolère pour un appui).
    static let slop = 10.0

    private var startX = 0.0
    private var startY = 0.0
    private var candidate = false

    /// Un doigt se pose. `fingers` : le nombre de doigts sur l'écran, celui-ci compris.
    mutating func began(fingers: Int, x: Double, y: Double) {
        candidate = fingers == 1
        startX = x
        startY = y
    }

    /// Le doigt bouge : au-delà de `slop`, ce n'est plus un appui.
    mutating func moved(fingers: Int, x: Double, y: Double) {
        if candidate && (fingers != 1 || distance(x: x, y: y) > Self.slop) {
            candidate = false
        }
    }

    /// Le doigt se relève. `fingers` : le nombre de doigts sur l'écran juste avant, celui-ci compris. Vrai si c'était un appui.
    mutating func ended(fingers: Int, x: Double, y: Double) -> Bool {
        let tap = candidate && fingers == 1 && distance(x: x, y: y) <= Self.slop
        candidate = false
        return tap
    }

    /// Le système a annulé le toucher (un appel, un geste du système) : ce n'est pas un appui.
    mutating func cancelled() {
        candidate = false
    }

    private func distance(x: Double, y: Double) -> Double {
        let dx = x - startX
        let dy = y - startY
        return (dx * dx + dy * dy).squareRoot()
    }
}
