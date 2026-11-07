import Foundation

/// Géométrie de l'animation de lancement, dans le canevas de 108 unités de l'icône (le « M + i » et le cœur de
/// `LogoPaths` utilisent le même). Les couleurs ne sont PAS ici : elles viennent des tokens du design system.
enum SplashArtwork {
    /// Taille du canevas, en unités.
    static let canvas = 108.0

    /// Taille à laquelle le canevas est dessiné, en points : celle de l'image de l'écran de lancement du système
    /// (`LaunchMark`), pour qu'il n'y ait aucun saut quand l'animation prend le relais.
    static let canvasPoints = 288.0

    /// Le milieu du cœur : le ballon gonfle autour de ce point.
    static let heartCenterX = 68.5
    static let heartCenterY = 36.3

    /// Demi-largeur du cœur au repos, en unités.
    static let heartHalfWidth = 8.6

    /// Les morceaux partent du bord du cœur éclaté (un peu à l'intérieur : le cœur n'est pas rond) et volent jusque-là.
    static let particleStartRadius = heartHalfWidth * SplashTimeline.maxScale * 0.85
    static let particleTravel = 17.0

    /// Rayon d'un morceau en pleine taille, en unités.
    static let particleRadius = 2.2
}
