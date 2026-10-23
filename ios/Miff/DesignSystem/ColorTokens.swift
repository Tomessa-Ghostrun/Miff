import SwiftUI

// Couleurs de design/design-system.md, section 1.
// Les noms de code sont IDENTIQUES a ceux d'Android (android/.../ui/theme/Color.kt) : colorBrandPrimary,
// colorActionPrimary, colorStatusSafe... Les noms que le design system ne cite pas suivent le meme schema
// (token "bg/canvas" devient colorBgCanvas). Ne jamais ecrire de valeur hexadecimale dans un ecran.

/// Couleur du design system : composantes sRGB 0 a 255 et opacite 0 a 1.
struct ColorToken: Equatable {
    let red: Int
    let green: Int
    let blue: Int
    let alpha: Double

    /// `hex` au format 0xRRGGBB.
    init(hex: UInt32, alpha: Double = 1) {
        red = Int((hex >> 16) & 0xFF)
        green = Int((hex >> 8) & 0xFF)
        blue = Int(hex & 0xFF)
        self.alpha = alpha
    }

    var color: Color {
        Color(.sRGB, red: Double(red) / 255, green: Double(green) / 255, blue: Double(blue) / 255, opacity: alpha)
    }

    /// Meme couleur avec une autre opacite (les teintes du design system sont des opacites de la couleur de base).
    func withAlpha(_ newAlpha: Double) -> ColorToken {
        ColorToken(hex: UInt32(red << 16 | green << 8 | blue), alpha: newAlpha)
    }

    /// « #RRGGBB » (l'opacite est affichee a part).
    var hexString: String {
        String(format: "#%02X%02X%02X", red, green, blue)
    }

    var alphaPercent: Int {
        Int((alpha * 100).rounded())
    }

    /// Luminance relative WCAG 2.x (opacite ignoree).
    var relativeLuminance: Double {
        func channel(_ value: Int) -> Double {
            let scaled = Double(value) / 255
            return scaled <= 0.03928 ? scaled / 12.92 : pow((scaled + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    /// Rapport de contraste WCAG entre deux couleurs opaques (1 a 21).
    func contrastRatio(with other: ColorToken) -> Double {
        let lighter = max(relativeLuminance, other.relativeLuminance)
        let darker = min(relativeLuminance, other.relativeLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }
}

// MARK: - Marque et actions

let colorBrandPrimary = ColorToken(hex: 0xD1D828)
let colorActionPrimary = ColorToken(hex: 0x043CD1)
let colorActionPrimaryTint5 = colorActionPrimary.withAlpha(0.05)
let colorActionPrimaryTint10 = colorActionPrimary.withAlpha(0.10)
let colorActionPrimaryTint20 = colorActionPrimary.withAlpha(0.20)
let colorActionPrimaryTint30 = colorActionPrimary.withAlpha(0.30)
let colorTextOnAction = ColorToken(hex: 0xFFFFFF)

// MARK: - Statuts (coeur du produit). Le statut est toujours porte AUSSI par un libelle, jamais par la couleur seule.

let colorStatusSafe = ColorToken(hex: 0xD1D828)
let colorStatusSafeTint = colorStatusSafe.withAlpha(0.30)
let colorStatusWarning = ColorToken(hex: 0xFFB650)
let colorStatusWarningTint = colorStatusWarning.withAlpha(0.30)
let colorStatusDanger = ColorToken(hex: 0xFF5650)
let colorStatusDangerTint = colorStatusDanger.withAlpha(0.30)

// MARK: - Fonds

let colorBgCanvas = ColorToken(hex: 0xF9F9F7)
let colorBgSurface = ColorToken(hex: 0xFFFFFF)
let colorBgPlaceholder = ColorToken(hex: 0xD9D9D9)
let colorBgOverlayHeader = colorBgCanvas.withAlpha(0.60)
let colorBgGradientEnd = ColorToken(hex: 0xEDEFB9)

// MARK: - Textes

let colorTextPrimary = ColorToken(hex: 0x000000)
let colorTextForm = ColorToken(hex: 0x222222)
let colorTextPlaceholder = colorTextForm.withAlpha(0.50)
let colorTextDescription = colorTextPrimary.withAlpha(0.70)
let colorTextMuted = colorTextPrimary.withAlpha(0.50)
let colorTextSecondary = ColorToken(hex: 0x7D7C7C)

// « text/error » est un rouge plus sombre que le rouge de statut (meme teinte) pour que le TEXTE d'erreur atteigne
// le contraste WCAG AA de 4,5 pour 1 sur tous les fonds de l'app (decision du 2026-10-22, voir design-system.md).
// colorStatusDanger (#FF5650) reste reserve aux anneaux, pastilles et fonds, jamais au texte courant.
// Verifie par ColorTokensTests.
let colorTextError = ColorToken(hex: 0xD60700)

// MARK: - Bordures

let colorBorderDefault = ColorToken(hex: 0xEFEFEF)
let colorBorderStrong = colorTextPrimary.withAlpha(0.50)
let colorBorderSubtle = colorTextPrimary.withAlpha(0.30)
let colorBorderAction = colorActionPrimary

/// Un token avec son nom de code (galerie du diagnostic et tests).
struct NamedToken<Value>: Identifiable {
    let name: String
    let value: Value

    var id: String {
        name
    }

    init(_ name: String, _ value: Value) {
        self.name = name
        self.value = value
    }
}

// MARK: - Liste complete (galerie du diagnostic et tests)

enum ColorTokens {
    /// Meme ordre et memes noms que la galerie Android.
    static let all: [NamedToken<ColorToken>] = [
        NamedToken("colorBrandPrimary", colorBrandPrimary),
        NamedToken("colorActionPrimary", colorActionPrimary),
        NamedToken("colorActionPrimaryTint5", colorActionPrimaryTint5),
        NamedToken("colorActionPrimaryTint10", colorActionPrimaryTint10),
        NamedToken("colorActionPrimaryTint20", colorActionPrimaryTint20),
        NamedToken("colorActionPrimaryTint30", colorActionPrimaryTint30),
        NamedToken("colorTextOnAction", colorTextOnAction),
        NamedToken("colorStatusSafe", colorStatusSafe),
        NamedToken("colorStatusSafeTint", colorStatusSafeTint),
        NamedToken("colorStatusWarning", colorStatusWarning),
        NamedToken("colorStatusWarningTint", colorStatusWarningTint),
        NamedToken("colorStatusDanger", colorStatusDanger),
        NamedToken("colorStatusDangerTint", colorStatusDangerTint),
        NamedToken("colorBgCanvas", colorBgCanvas),
        NamedToken("colorBgSurface", colorBgSurface),
        NamedToken("colorBgPlaceholder", colorBgPlaceholder),
        NamedToken("colorBgOverlayHeader", colorBgOverlayHeader),
        NamedToken("colorBgGradientEnd", colorBgGradientEnd),
        NamedToken("colorTextPrimary", colorTextPrimary),
        NamedToken("colorTextForm", colorTextForm),
        NamedToken("colorTextPlaceholder", colorTextPlaceholder),
        NamedToken("colorTextDescription", colorTextDescription),
        NamedToken("colorTextMuted", colorTextMuted),
        NamedToken("colorTextSecondary", colorTextSecondary),
        NamedToken("colorTextError", colorTextError),
        NamedToken("colorBorderDefault", colorBorderDefault),
        NamedToken("colorBorderStrong", colorBorderStrong),
        NamedToken("colorBorderSubtle", colorBorderSubtle),
        NamedToken("colorBorderAction", colorBorderAction)
    ]
}
