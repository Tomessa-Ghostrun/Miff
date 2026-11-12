import CoreGraphics

// Où commence le contenu du Feed par rapport à l'en-tête. CAUSE RÉELLE du bug « le haut de la bulle est coupé par le bas de l'en-tête »
// (captures de la CI, PR #193) : la vue de défilement commence tout en haut de l'ÉCRAN (sous la barre d'état) alors que l'en-tête est posé
// DANS la zone sûre ; la marge haute de la liste valait seulement la hauteur de l'en-tête, sans la zone sûre : le contenu démarrait de
// la hauteur de la barre d'état (une cinquantaine de points) trop haut, sous l'en-tête. La marge est maintenant tirée de MESURES : le bas de
// l'en-tête au repos et l'endroit où le contenu de la liste commence réellement, dans le même repère (la fenêtre).
enum FeedTopInset {
    /// L'espace entre le bas de l'en-tête et le haut du contenu (la bulle). Retour du PM sur le TestFlight 32 : « un peu trop grand, à réduire de
    /// moitié ». Avant : 16 pt de marge + 14 pt d'espacement laissé après le point d'ancrage = 30 pt (une quarantaine de points à l'écran avec
    /// le contour de la bulle) ; maintenant 12 pt, sans espacement en plus : la bulle reste ENTIÈRE sous l'en-tête, jamais recouverte.
    static let gapBelowHeader: CGFloat = 12

    /// La marge haute à donner au contenu de la liste : ce qu'il faut pour que son haut tombe exactement au bas de l'en-tête (`headerBottom`),
    /// compte tenu de l'endroit où la vue de défilement place son contenu (`contentOrigin` : le haut de la vue + sa marge automatique).
    /// Sans mesure (pas encore lues), la hauteur de l'en-tête (`fallback`). Jamais négative.
    static func listInset(headerBottom: CGFloat?, contentOrigin: CGFloat?, fallback: CGFloat) -> CGFloat {
        guard let headerBottom, let contentOrigin else {
            return fallback
        }
        return max(0, headerBottom - contentOrigin)
    }

    /// Une mesure qui ne change que d'une fraction de point n'est pas une nouvelle mesure (évite une boucle de mise en page).
    static func isNewMeasure(_ new: CGFloat, old: CGFloat?) -> Bool {
        guard let old else {
            return true
        }
        return abs(new - old) >= 0.5
    }
}
