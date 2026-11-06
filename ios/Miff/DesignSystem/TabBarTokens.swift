import SwiftUI

// Jetons de la barre d'onglets en verre (design/tab-bar-liquid-glass.md, section 5 ; mêmes valeurs que `ui/theme/GlassTokens.kt`
// d'Android). Deux variantes choisies par l'ÉCRAN, jamais par un réglage : « claire » (Feed, Profil) et « sur photo » (le viseur
// de l'Update, plus tard). Le bouton central n'est jamais du verre : plaque blanche opaque, anneau, pastille de couleur seule.

/// Les couleurs d'une variante de la barre.
struct TabBarPalette: Equatable {
    /// Voile posé sur le matériau translucide (repli d'iOS 16 à 25).
    let tint: ColorToken
    /// Liseré de 1 pt autour de la barre.
    let border: ColorToken
    /// Fond opaque : « Réduire la transparence » et « Augmenter le contraste ».
    let opaque: ColorToken
    let iconActive: ColorToken
    let iconInactive: ColorToken
    /// Pastille de l'onglet actif (64 x 48).
    let pillActive: ColorToken
}

enum TabBarVariant: Equatable {
    case light
    case onPhoto

    var palette: TabBarPalette {
        switch self {
        case .light:
            return TabBarPalette(
                tint: ColorToken(hex: 0xFFFFFF, alpha: 0.80),
                border: ColorToken(hex: 0xFFFFFF, alpha: 0.55),
                opaque: ColorToken(hex: 0xFFFFFF),
                iconActive: colorActionPrimary,
                iconInactive: tabBarIconInactiveLight,
                pillActive: colorActionPrimaryTint20
            )
        case .onPhoto:
            return TabBarPalette(
                tint: ColorToken(hex: 0x000000, alpha: 0.60),
                border: ColorToken(hex: 0xFFFFFF, alpha: 0.18),
                opaque: ColorToken(hex: 0x222222),
                iconActive: ColorToken(hex: 0xFFFFFF),
                iconInactive: ColorToken(hex: 0xFFFFFF, alpha: 0.78),
                pillActive: ColorToken(hex: 0xFFFFFF, alpha: 0.24)
            )
        }
    }
}

/// Icônes inactives plus foncées que l'ancien gris `#7D7C7C` (2,59 : 1 au pire fond) : 4,36 : 1 au pire fond du verre (décision du PM,
/// 2026-11-06). C'est aussi le gris du bouton central quand le statut est inconnu.
let tabBarIconInactiveLight = ColorToken(hex: 0x595959)

/// Ombre douce de la barre (0 / 6 / 20, noir 18 %).
let tabBarShadowColor = ColorToken(hex: 0x000000, alpha: 0.18)
let tabBarShadowRadius: CGFloat = 10
let tabBarShadowOffsetY: CGFloat = 6

/// Ombre légère de la plaque du bouton central.
let tabBarPlateShadowColor = ColorToken(hex: 0x000000, alpha: 0.30)

/// Dégradé de l'en-tête du Profil : le vert de la marque à 30 % vers le fond de l'app (même approximation qu'Android, la maquette le
/// dessine en forme organique : à confirmer avec le responsable du design).
let gradientProfileHeader = LinearGradient(
    stops: [
        Gradient.Stop(color: colorBrandPrimary.withAlpha(0.30).color, location: 0),
        Gradient.Stop(color: colorBgCanvas.color, location: 0.45),
        Gradient.Stop(color: colorBgCanvas.color, location: 1)
    ],
    startPoint: .top,
    endPoint: .bottom
)
