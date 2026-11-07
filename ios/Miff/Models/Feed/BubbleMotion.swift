import Foundation

// Le mouvement de la bulle de la Miff : lent, continu et ORGANIQUE, comme une bulle de savon ou une goutte qui respire. Première
// décision du PM (2026-11-06, D40) : une respiration très légère ; révisée après son essai sur un téléphone réel (2026-11-07, D45) :
// trop discret, et la bulle doit se DÉFORMER un peu, pas seulement gonfler. La tache est donc écrasée et étirée sur ses deux axes en
// déphasage (jamais une échelle uniforme), s'incline d'une fraction de degré et dérive de quelques points à la verticale ; le texte
// et le bouton ne sont PAS déformés (seule la forme et son fond le sont : ils suivent seulement la dérive). Miroir de `BubbleMotion.kt`
// d'Android (mêmes constantes, mêmes formules).
//
// Chaque valeur est une CONSTANTE NOMMÉE : régler l'amplitude, la vitesse ou le déphasage est une ligne. Chaque mouvement est un sinus
// pur (le plus doux des mouvements). La forme ne fait que RÉTRÉCIR depuis sa taille pleine (son échelle reste entre 1 - 2 x amplitude
// et 1) : elle ne dépasse jamais la boîte où elle a été posée, la liste ne coupe donc aucun bord.
//
// Jamais (ton de l'app, CLAUDE.md : bienveillant, jamais alarmant, même en rouge) : un clignotement, un flash, un rebond, un mouvement
// rapide. Ce que le mouvement ne coûte JAMAIS : avec « Réduire les animations » AUCUNE animation, la bulle est immobile (pleine
// taille, sans inclinaison ni dérive) ; rien hors écran ni en arrière-plan (l'animation n'existe que tant que la bulle est affichée
// et que l'app est au premier plan) ; aucune lecture du serveur, aucun recalcul de la couleur ; rien pour le lecteur d'écran.
enum BubbleMotion {
    // MARK: - La forme de la tache (écrasée et étirée, en déphasage)

    /// Écrasement horizontal : la largeur va et vient entre 100 % et 92 % (de crête à crête 8 %, soit ±4 % autour de sa moyenne).
    static let squashX: Double = 0.04

    /// Écrasement vertical : la hauteur va et vient entre 100 % et 90 % (de crête à crête 10 %, soit ±5 % autour de sa moyenne).
    static let squashY: Double = 0.05

    /// L'axe vertical court un tiers de cycle derrière l'horizontal : quand elle est large elle devient basse, puis les deux
    /// s'adoucissent (c'est ce qui fait que la forme se déforme).
    static let yPhaseShift: Double = 0.33

    /// Une inclinaison de la forme (pas de son texte) d'une fraction de degré de chaque côté, sur sa propre phase : le contour ne
    /// « pulse » jamais sur place.
    static let tiltDegrees: Double = 0.8

    /// Où dans son cycle l'inclinaison commence.
    static let tiltPhaseShift: Double = 0.6

    // MARK: - La dérive

    /// Dérive verticale de toute la bulle (forme et contenu) : au plus 3,5 pt de chaque côté.
    static let driftPoints: Double = 3.5

    /// Où dans son cycle la dérive commence, pour qu'elle soit déphasée de l'écrasement (un quart de cycle).
    static let driftPhaseShift: Double = 0.25

    // MARK: - Le pictogramme (« l'étoile ») de la bulle

    /// Le pictogramme rond respire de ±5 % (un disque avec une icône : une échelle uniforme, rien à déformer).
    static let starScaleAmplitude: Double = 0.05

    /// L'étoile dérive de 0,5 pt de plus ou de moins PAR-DESSUS la dérive de la bulle : son total reste à 4 pt.
    static let starDriftPoints: Double = 0.5

    // MARK: - Les cycles

    /// Un cycle lent de la tache (6 s : entre les 5 et 8 s de D40, que D45 garde).
    static let blobPeriodSeconds: Double = 6

    /// L'étoile suit un cycle un peu différent, aussi lent : les deux ne bougent jamais en cadence.
    static let starPeriodSeconds: Double = 7

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

    /// Une échelle qui va entre 1 - 2 x `amplitude` et 1 (jamais au-dessus de 1), en suivant `wave`.
    private static func shrink(_ amplitude: Double, phase: Double) -> Double {
        1 - amplitude * (1 - wave(phase))
    }

    static func scaleX(_ phase: Double) -> Double {
        shrink(squashX, phase: phase)
    }

    static func scaleY(_ phase: Double) -> Double {
        shrink(squashY, phase: phase + yPhaseShift)
    }

    static func tilt(_ phase: Double) -> Double {
        tiltDegrees * wave(phase + tiltPhaseShift)
    }

    static func drift(_ phase: Double) -> Double {
        driftPoints * wave(phase + driftPhaseShift)
    }

    static func starScale(_ phase: Double) -> Double {
        1 + starScaleAmplitude * wave(phase)
    }

    static func starDrift(_ phase: Double) -> Double {
        starDriftPoints * wave(phase + driftPhaseShift)
    }
}
