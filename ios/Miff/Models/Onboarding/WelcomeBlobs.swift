import Foundation

/// Les formes vert pale organiques derrière les illustrations du carrousel de bienvenue, TRACÉES sur les maquettes du PM
/// (« Welcoming screen », « -16 », « -17 » de design/exports/onboarding-original) : le vert pâle est isolé par sa couleur
/// dans les exports, les parties cachées par les illustrations sont prolongées en douceur, et le contour est simplifié
/// en quelques dizaines de points (données dans `WelcomeBlobData`, les mêmes qu'Android).
///
/// Coordonnées en unités des maquettes (320 de large, y mesuré depuis le haut de l'écran). Elles sont dessinées en courbes
/// lisses passant par ces points, donc nettes à toute taille (aucune image).
enum WelcomeBlobs {
    /// Largeur des maquettes, en unités : l'échelle d'un écran est sa largeur divisée par ce nombre.
    static let unitsWidth = 320.0
    /// Hauteur de la zone couverte par les formes (et par les illustrations), en unités.
    static let unitsHeight = 480.0
    static let pageCount = 3

    /// Les contours de la slide `page` (0 à 2), déjà lus ; vide si la page n'existe pas.
    static func outlines(page: Int) -> [[PathPoint]] {
        guard page >= 0, page < WelcomeBlobData.slides.count else {
            return []
        }
        return WelcomeBlobData.slides[page].map { parse($0) }
    }

    static func parse(_ outline: String) -> [PathPoint] {
        outline.split(separator: " ").compactMap { pair in
            let parts = pair.split(separator: ",")
            guard parts.count == 2, let valueX = Double(parts[0]), let valueY = Double(parts[1]) else {
                return nil
            }
            return PathPoint(px: valueX, py: valueY)
        }
    }
}
