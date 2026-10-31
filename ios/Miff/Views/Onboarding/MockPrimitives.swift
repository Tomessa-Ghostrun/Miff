import SwiftUI

// Les briques des trois illustrations du carrousel de bienvenue, RECONSTRUITES en vectoriel et en vrais textes à partir des
// maquettes du PM (design/exports/onboarding-original : « Welcoming screen », « -16 », « -17 »), comme sur Android : formes,
// icônes, ombres et textes sont dessinés par SwiftUI, seules les PHOTOS (profils, salon, fond de carte) sont des images.
//
// Tout est placé en « unités » des maquettes (le carrousel fait 320 unités de large ; une unité = largeur de l'écran / 320),
// tailles de texte comprises : les illustrations sont des aperçus DÉCORATIFS décrits par une phrase pour le lecteur
// d'écran, elles suivent donc la largeur de l'écran et non la taille de texte du système.

extension View {
    /// Place la vue au point (`x`, `y`) avec une taille de `w` x `h` unités, dans un conteneur aligné en haut à gauche.
    func placed(_ unit: CGFloat, x: Double, y: Double, w: Double, h: Double) -> some View {
        frame(width: unit * CGFloat(w), height: unit * CGFloat(h))
            .offset(x: unit * CGFloat(x), y: unit * CGFloat(y))
    }
}

/// Un cadre de `width` x `height` unités dont les enfants sont placés par `placed` et `MockText`.
struct MockCanvas<Content: View>: View {
    let unit: CGFloat
    let width: Double
    let height: Double
    let content: Content

    init(unit: CGFloat, width: Double, height: Double, @ViewBuilder content: () -> Content) {
        self.unit = unit
        self.width = width
        self.height = height
        self.content = content()
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            content
        }
        .frame(width: unit * CGFloat(width), height: unit * CGFloat(height), alignment: .topLeading)
    }
}

/// Le style d'un texte d'illustration.
struct MockTextStyle {
    var size: Double
    var color: ColorToken
    /// Largeur de la zone de texte, en unités.
    var width = 140.0
    var lines = 1
    /// Police d'affichage (Baloo Thambi 2 en gras, les titres des maquettes) ; sinon Hanken Grotesk.
    var display = false
    var bold = false
    var italic = false
    var centered = false
}

/// Un texte dont la PREMIÈRE ligne est centrée sur `y`, bord gauche en `x` (ou centré sur `x` si `style.centered`).
struct MockText: View {
    let text: String
    let unit: CGFloat
    let x: Double
    let y: Double
    let style: MockTextStyle

    private var font: Font {
        let points = unit * CGFloat(style.size)
        if style.display {
            return MiffFontFace.balooBoldFont(size: points)
        }
        if style.italic {
            return MiffFontFace.hankenItalic.swiftUIFont(size: points)
        }
        return (style.bold ? MiffFontFace.hankenBold : MiffFontFace.hankenRegular).swiftUIFont(size: points)
    }

    var body: some View {
        let lineHeight = style.size * 1.3
        let left = style.centered ? x - style.width / 2 : x
        Text(verbatim: text)
            .font(font)
            .foregroundColor(style.color.color)
            .multilineTextAlignment(style.centered ? .center : .leading)
            .lineLimit(style.lines)
            .fixedSize(horizontal: false, vertical: true)
            .frame(
                width: unit * CGFloat(style.width),
                height: unit * CGFloat(lineHeight * Double(style.lines)),
                alignment: style.centered ? .top : .topLeading
            )
            .offset(x: unit * CGFloat(left), y: unit * CGFloat(y - lineHeight * Double(style.lines) / 2))
            .accessibilityHidden(true)
    }
}

/// Le cadre d'un téléphone dessiné : fond canevas, fine bordure bleue, ombre douce, contenu coupé aux bords.
struct MockScreen<Content: View>: View {
    let unit: CGFloat
    let content: Content

    init(unit: CGFloat, @ViewBuilder content: () -> Content) {
        self.unit = unit
        self.content = content()
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: unit * 0.5, style: .continuous)
        MockCanvas(unit: unit, width: 155, height: 290) {
            content
        }
        .background(colorBgCanvas.color)
        .clipShape(shape)
        .overlay(shape.stroke(colorActionPrimaryTint20.color, lineWidth: max(unit, 1) * 0.8))
        .shadow(color: colorTextPrimary.withAlpha(0.25).color, radius: unit * 1.5, x: 0, y: unit)
        .placed(unit, x: 82, y: 125, w: 155, h: 290)
    }
}

/// Une photo des ressources, recadrée dans son cadre (pour en changer : remplacer le fichier, sans toucher au code).
struct MockPhoto: View {
    let name: String

    var body: some View {
        Image(name)
            .resizable()
            .scaledToFill()
            .accessibilityHidden(true)
    }
}

/// Les icônes des maquettes, dessinées en vectoriel dans un carré de dix unités.
enum MockIconKind {
    case triangleDown
    case sparkle
    case home
    case mortarboard
    case battery
    case pin
    case message
    case phone
    case back
    case check
    case pencil
    case personPin
    case target
    case eye
    case checkCircle
    case warning
}

/// Une icône de `size` unités dont le coin haut gauche est en (`x`, `y`).
struct MockIcon: View {
    let unit: CGFloat
    let x: Double
    let y: Double
    let size: Double
    let kind: MockIconKind
    let color: ColorToken

    var body: some View {
        Canvas { context, canvas in
            MockIconPainter.draw(kind, in: &context, side: min(canvas.width, canvas.height), color: color.color)
        }
        .placed(unit, x: x, y: y, w: size, h: size)
        .accessibilityHidden(true)
    }
}
