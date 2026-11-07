import Foundation

// Le mouvement continu de la bulle de la Miff (décision du PM, 2026-11-06 : très léger ; puis 2026-11-07 : amplitude plus visible
// et déformation organique). Une respiration d'ÉTIREMENT / ÉCRASEMENT, les deux axes en déphasage (quand la tache s'allonge en
// largeur elle s'écrase en hauteur, comme une goutte), et une très légère dérive verticale. Chaque réglage est une CONSTANTE
// NOMMÉE : changer l'amplitude ou la vitesse est une ligne. Un sinus pur (le plus doux des mouvements) : jamais de clignotement,
// de rebond ni de mouvement rapide (ton de l'app, CLAUDE.md : bienveillant, jamais alarmant, même en rouge).
//
// Ce que le mouvement ne coûte JAMAIS : avec « Réduire les animations » AUCUNE animation, la bulle est immobile ; rien hors écran
// ni en arrière-plan (l'animation n'existe que tant que la bulle est affichée et que l'app est au premier plan) ; aucune lecture
// du serveur, aucun recalcul de la couleur ; rien pour le lecteur d'écran.
enum BubbleMotion {
    /// Amplitude de l'étirement / écrasement autour de 1, de chaque côté : 5 % au départ (demande du PM du 2026-11-07). À baisser si la
    /// recette la trouve trop présente (1,2 % était la valeur de la première version).
    static let scaleAmplitude: Double = 0.05

    /// Déphasage de l'axe vertical par rapport à l'horizontal, en fraction de cycle : 0,5 = opposition (l'un s'allonge quand l'autre s'écrase).
    static let axisPhaseShift: Double = 0.5

    /// La dérive verticale, en points, de chaque côté.
    static let driftPoints: Double = 2

    /// Où dans son cycle la dérive commence, pour qu'elle soit déphasée de la respiration (un quart de cycle).
    static let driftPhaseShift: Double = 0.25

    /// Un cycle lent de la tache (le PM demande 5 à 8 s).
    static let blobPeriodSeconds: Double = 6.5

    /// L'étoile de la bulle respire sur un cycle un peu différent, aussi lent : les deux ne bougent jamais en cadence.
    static let starPeriodSeconds: Double = 7.5

    /// La dérive de l'étoile s'ajoute d'un point à celle de la tache.
    static let starDriftPoints: Double = 1

    /// Aucune animation quand la personne n'en veut pas, ni quand l'écran n'est pas visible.
    static func enabled(reduceMotion: Bool, active: Bool) -> Bool {
        active && !reduceMotion
    }

    /// Le sinus d'une phase en cycles : le plus doux des mouvements, aucun angle aux points de retour.
    static func wave(_ phase: Double) -> Double {
        sin(2 * Double.pi * phase)
    }

    /// La phase (un nombre de cycles, de 0 à 1 exclu) d'un instant pour une période donnée.
    static func phase(at seconds: Double, period: Double) -> Double {
        guard period > 0 else {
            return 0
        }
        let cycles = seconds / period
        return cycles - cycles.rounded(.down)
    }

    static func scaleX(_ phase: Double) -> Double {
        1 + scaleAmplitude * wave(phase)
    }

    static func scaleY(_ phase: Double) -> Double {
        1 + scaleAmplitude * wave(phase + axisPhaseShift)
    }

    static func drift(_ phase: Double) -> Double {
        driftPoints * wave(phase + driftPhaseShift)
    }

    static func starDrift(_ phase: Double) -> Double {
        starDriftPoints * wave(phase + driftPhaseShift)
    }
}
