#if DEBUG
import SwiftUI

/// Les aperçus du compte à rebours pour les captures de la CI (`-MiffPreview feed-countdown-full`, `feed-countdown-last`,
/// `profile-countdown-half`) : MON statut est écrit ici (démonstration, jamais de vraies données), l'horloge est FIXE (l'aperçu ne bouge pas)
/// et le geste « ouvrir l'Update » ne fait rien. Compilé en configuration Debug SEULEMENT.
enum CountdownPreviewSupport {
    /// L'instant figé des aperçus (le même que celui des autres aperçus du Feed).
    static let now = 1_794_063_600_000

    /// Le temps restant montré par chaque aperçu : plein (2 h), à moitié (1 h), dernière demi-heure (22 min).
    static func remainingMillis(for name: String) -> Int? {
        if name.hasSuffix("-full") {
            return StatusThresholds.orangeAfterMillis
        }
        if name.hasSuffix("-half") {
            return StatusThresholds.orangeAfterMillis / 2
        }
        if name.hasSuffix("-last") {
            return 22 * 60 * 1000
        }
        return nil
    }

    /// MON statut : dehors d'un lieu de confiance, dont le repère est tel qu'il reste `remaining` avant l'orange.
    static func myFacts(for name: String) -> MyStatusFacts? {
        guard let remaining = remainingMillis(for: name) else {
            return nil
        }
        return MyStatusFacts(
            presence: .out,
            presenceAtMillis: now - (StatusThresholds.orangeAfterMillis - remaining),
            lastUpdateAtMillis: nil,
            hasPhoto: false
        )
    }

    @MainActor
    static func context(for name: String) -> CountdownContext? {
        guard let facts = myFacts(for: name) else {
            return nil
        }
        let store = MyStatusStore()
        store.update(facts)
        return CountdownContext(status: store, clock: { now }, openUpdate: {})
    }
}
#endif
