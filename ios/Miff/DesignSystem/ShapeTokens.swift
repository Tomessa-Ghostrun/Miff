import SwiftUI

// Arrondis, traits, ombres et degrades de design/design-system.md (sections 3 et 5).
// Noms de code Android (RadiusMd, ShadowDefault, GradientAuthBackground...) en lowerCamelCase :
// la convention Swift interdit une constante globale qui commence par une majuscule.
// La galerie du diagnostic affiche les noms Android d'origine.

let radiusMd: CGFloat = 10
let radiusSm: CGFloat = 5
let radiusFull: CGFloat = 9999

let strokeAvatarRing: CGFloat = 3
let strokeHairline: CGFloat = 1
let strokeAccent: CGFloat = 2
let strokeAccentThick: CGFloat = 2.5

/// Ombre du design system : decalage vertical, flou et couleur.
/// SwiftUI exprime l'ombre par un rayon de flou : on retient la moitie du flou de la maquette
/// (equivalence usuelle Figma vers SwiftUI). A valider sur capture (ecart accepte par le ticket).
struct ShadowToken {
    let offsetY: CGFloat
    let blur: CGFloat
    let color: ColorToken

    var radius: CGFloat {
        blur / 2
    }
}

/// shadow/default : 0 / 4 / 4 / noir 25 %. Boutons, cartes, pastilles.
let shadowDefault = ShadowToken(offsetY: 4, blur: 4, color: ColorToken(hex: 0x000000, alpha: 0.25))
/// shadow/subtle : 0 / 0 / 3 / noir 8 %. Boutons de connexion sociale.
let shadowSubtle = ShadowToken(offsetY: 0, blur: 3, color: ColorToken(hex: 0x000000, alpha: 0.08))

extension View {
    func miffShadow(_ token: ShadowToken) -> some View {
        shadow(color: token.color.color, radius: token.radius, x: 0, y: token.offsetY)
    }
}

// Le design system garde la fin du degrade du titre a #d1d52c jusqu'a retouche des maquettes.
private let gradientBrandTitleEnd = ColorToken(hex: 0xD1D52C)

/// gradient/brand-title : texte de titre, horizontal, bleu d'action vers jaune-vert.
let gradientBrandTitle = LinearGradient(
    colors: [colorActionPrimary.color, gradientBrandTitleEnd.color],
    startPoint: .leading,
    endPoint: .trailing
)

/// gradient/auth-background : vertical, couleur de fond (arret a 17 %) vers colorBgGradientEnd.
/// Point ouvert (identique a Android) : « a 17 % » est lu comme la position du premier arret de couleur
/// (style Figma), pas comme une opacite. A confirmer avec le responsable du design.
let gradientAuthBackground = LinearGradient(
    stops: [
        Gradient.Stop(color: colorBgCanvas.color, location: 0),
        Gradient.Stop(color: colorBgCanvas.color, location: 0.17),
        Gradient.Stop(color: colorBgGradientEnd.color, location: 1)
    ],
    startPoint: .top,
    endPoint: .bottom
)

/// Espacements de mise en page. PAS des tokens Figma : design-system.md (section 6) indique que les espacements
/// ne sont pas encore tokenises. Ces constantes evitent seulement de disperser des nombres bruts.
enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
    static let logoSize: CGFloat = 120
    /// Cible tactile minimale : 44 pt (recommandation d'Apple, a la place des 48 dp d'Android).
    static let minTouchTarget: CGFloat = 44
}
