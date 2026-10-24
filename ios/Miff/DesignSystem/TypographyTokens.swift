import CoreText
import SwiftUI
import UIKit

// Styles de texte de design/design-system.md, section 2. Memes noms de code qu'Android (Type.kt) :
// textDisplayHero, textBodyRegular, textCaptionTag8... (token « body/regular-14 » devient textBodyRegular14).
//
// Dynamic Type : la taille ET l'interligne suivent la taille de texte choisie par l'utilisateur (ScaledMetric
// relatif a un style systeme proche). Les tailles ci-dessous sont celles de la maquette, en points.
//
// Ecart de plateforme (accepte, a signaler) : SwiftUI sur iOS 16 ne sait pas fixer une hauteur de ligne exacte.
// L'interligne de la maquette est reproduit par `lineSpacing` (espace entre deux lignes d'un texte sur plusieurs
// lignes) ; un texte sur une seule ligne garde la hauteur naturelle de sa police.
// Point ouvert de design (identique a Android) : la graisse des styles « display/* » n'est pas precisee ; on
// utilise Regular (400), comme l'indique le fichier d'etat Figma.

/// Police embarquee (nom PostScript). Les fichiers sont declares dans `UIAppFonts` (project.yml).
enum MiffFontFace: String, CaseIterable {
    /// Instance par defaut (Regular 400) de la police variable Baloo Thambi 2.
    case baloo = "BalooThambi2-Regular"
    case hankenRegular = "HankenGrotesk-Regular"
    case hankenBold = "HankenGrotesk-Bold"
    case hankenItalic = "HankenGrotesk-Italic"

    /// Vrai si la police est bien enregistree dans l'app (sinon on retombe sur la police systeme).
    var isAvailable: Bool {
        UIFont(name: rawValue, size: 12) != nil
    }

    func uiFont(size: CGFloat) -> UIFont {
        UIFont(name: rawValue, size: size) ?? UIFont.systemFont(ofSize: size)
    }

    /// La même police pour SwiftUI, à taille fixe (le logo ne suit pas Dynamic Type).
    func swiftUIFont(size: CGFloat) -> Font {
        Font(uiFont(size: size) as CTFont)
    }
}

struct TextStyleToken {
    let name: String
    let face: MiffFontFace
    /// Taille de la maquette, en points.
    let size: CGFloat
    /// Interligne de la maquette, en points.
    let lineHeight: CGFloat
    /// Style systeme dont on suit la courbe de Dynamic Type.
    let dynamicTypeStyle: Font.TextStyle
}

let textDisplayHero = TextStyleToken(
    name: "textDisplayHero", face: .baloo, size: 64, lineHeight: 72, dynamicTypeStyle: .largeTitle)
let textDisplayHeadline = TextStyleToken(
    name: "textDisplayHeadline", face: .baloo, size: 24, lineHeight: 30, dynamicTypeStyle: .title2)
let textDisplayTitle = TextStyleToken(
    name: "textDisplayTitle", face: .baloo, size: 20, lineHeight: 26, dynamicTypeStyle: .title3)
let textDisplayLabel = TextStyleToken(
    name: "textDisplayLabel", face: .baloo, size: 16, lineHeight: 22, dynamicTypeStyle: .body)

let textTitleBold24 = TextStyleToken(
    name: "textTitleBold24", face: .hankenBold, size: 24, lineHeight: 30, dynamicTypeStyle: .title2)
let textTitleBold20 = TextStyleToken(
    name: "textTitleBold20", face: .hankenBold, size: 20, lineHeight: 26, dynamicTypeStyle: .title3)
let textBodyLarge = TextStyleToken(
    name: "textBodyLarge", face: .hankenRegular, size: 20, lineHeight: 26, dynamicTypeStyle: .title3)
let textBodyRegular = TextStyleToken(
    name: "textBodyRegular", face: .hankenRegular, size: 16, lineHeight: 22, dynamicTypeStyle: .body)
let textBodyItalic = TextStyleToken(
    name: "textBodyItalic", face: .hankenItalic, size: 16, lineHeight: 22, dynamicTypeStyle: .body)
let textBodyBold16 = TextStyleToken(
    name: "textBodyBold16", face: .hankenBold, size: 16, lineHeight: 22, dynamicTypeStyle: .body)
let textBodyRegular14 = TextStyleToken(
    name: "textBodyRegular14", face: .hankenRegular, size: 14, lineHeight: 20, dynamicTypeStyle: .callout)
let textBodyBold14 = TextStyleToken(
    name: "textBodyBold14", face: .hankenBold, size: 14, lineHeight: 20, dynamicTypeStyle: .callout)
let textBodyCaption = TextStyleToken(
    name: "textBodyCaption", face: .hankenRegular, size: 12, lineHeight: 16, dynamicTypeStyle: .caption)
let textCaptionBold12 = TextStyleToken(
    name: "textCaptionBold12", face: .hankenBold, size: 12, lineHeight: 16, dynamicTypeStyle: .caption)
let textCaptionMicro10 = TextStyleToken(
    name: "textCaptionMicro10", face: .hankenRegular, size: 10, lineHeight: 14, dynamicTypeStyle: .caption2)
let textCaptionTag8 = TextStyleToken(
    name: "textCaptionTag8", face: .hankenRegular, size: 8, lineHeight: 10, dynamicTypeStyle: .caption2)

enum TextStyleTokens {
    /// Meme ordre que la galerie Android.
    static let all: [TextStyleToken] = [
        textDisplayHero, textDisplayHeadline, textDisplayTitle, textDisplayLabel,
        textTitleBold24, textTitleBold20, textBodyLarge, textBodyRegular, textBodyItalic, textBodyBold16,
        textBodyRegular14, textBodyBold14, textBodyCaption, textCaptionBold12, textCaptionMicro10, textCaptionTag8
    ]
}

/// Applique police, taille et interligne d'un style, en suivant Dynamic Type.
struct MiffTextStyleModifier: ViewModifier {
    private let token: TextStyleToken
    @ScaledMetric private var scaledSize: CGFloat
    @ScaledMetric private var scaledLineHeight: CGFloat

    init(token: TextStyleToken) {
        self.token = token
        _scaledSize = ScaledMetric(wrappedValue: token.size, relativeTo: token.dynamicTypeStyle)
        _scaledLineHeight = ScaledMetric(wrappedValue: token.lineHeight, relativeTo: token.dynamicTypeStyle)
    }

    private var font: UIFont {
        token.face.uiFont(size: scaledSize)
    }

    func body(content: Content) -> some View {
        content
            .font(Font(font as CTFont))
            .lineSpacing(scaledLineHeight - font.lineHeight)
    }
}

extension View {
    func miffTextStyle(_ token: TextStyleToken) -> some View {
        modifier(MiffTextStyleModifier(token: token))
    }

    /// Titre de marque : texte rempli par le degrade `gradientBrandTitle` (« Bienvenue », Paywall).
    func miffBrandTitleFill() -> some View {
        foregroundStyle(gradientBrandTitle)
    }
}
