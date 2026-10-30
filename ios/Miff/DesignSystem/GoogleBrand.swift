import SwiftUI

// Règles de marque de Google pour le bouton « Continuer avec Google » (thème clair) : fond blanc, bordure #747775 de 1 pt,
// texte #1F1F1F, aucune ombre ajoutée. Ce sont des valeurs de la marque Google, PAS des tokens de Miff : elles
// vivent ici, avec le design system, et jamais dans un écran.
// À REMPLACER avant la fiche de l'App Store : le « G » ci-dessous est un redessin (les mêmes tracés que sur Android), pas
// le fichier officiel ; le remplacer par le visuel des directives de marque de Google
// (https://developers.google.com/identity/branding-guidelines), puis revérifier espacement, taille et libellé.
enum GoogleBrand {
    static let border = ColorToken(hex: 0x747775)
    static let text = ColorToken(hex: 0x1F1F1F)
    static let fill = ColorToken(hex: 0xFFFFFF)
    static let red = ColorToken(hex: 0xEA4335)
    static let blue = ColorToken(hex: 0x4285F4)
    static let yellow = ColorToken(hex: 0xFBBC05)
    static let green = ColorToken(hex: 0x34A853)

    /// Canevas du « G » : 48 x 48 unités.
    static let canvas = 48.0

    static let redPath = SVGPathParser.parse(
        "M24,9.5c3.54,0 6.71,1.22 9.21,3.6l6.85,-6.85C35.9,2.38 30.47,0 24,0 14.62,0 6.51,5.38 2.56,13.22"
            + "l7.98,6.19C12.43,13.72 17.74,9.5 24,9.5z"
    )
    static let bluePath = SVGPathParser.parse(
        "M46.98,24.55c0,-1.57 -0.15,-3.09 -0.38,-4.55H24v9.02h12.94c-0.58,2.96 -2.26,5.48 -4.78,7.18l7.73,6"
            + "c4.51,-4.18 7.09,-10.36 7.09,-17.65z"
    )
    static let yellowPath = SVGPathParser.parse(
        "M10.53,28.59c-0.48,-1.45 -0.76,-2.99 -0.76,-4.59s0.27,-3.14 0.76,-4.59l-7.98,-6.19C0.92,16.46 0,20.12 0,24"
            + "c0,3.88 0.92,7.54 2.56,10.78l7.97,-6.19z"
    )
    static let greenPath = SVGPathParser.parse(
        "M24,48c6.48,0 11.93,-2.13 15.89,-5.81l-7.73,-6c-2.15,1.45 -4.92,2.3 -8.16,2.3 -6.26,0 -11.57,-4.22"
            + " -13.47,-9.91l-7.98,6.19C6.51,42.62 14.62,48 24,48z"
    )
}

/// Le « G » multicolore, décoratif : le libellé du bouton le nomme.
struct GoogleGMark: View {
    var body: some View {
        ZStack {
            shape(GoogleBrand.redPath).fill(GoogleBrand.red.color)
            shape(GoogleBrand.bluePath).fill(GoogleBrand.blue.color)
            shape(GoogleBrand.yellowPath).fill(GoogleBrand.yellow.color)
            shape(GoogleBrand.greenPath).fill(GoogleBrand.green.color)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    private func shape(_ commands: [PathCommand]) -> VectorShape {
        VectorShape(commands: commands, width: GoogleBrand.canvas, height: GoogleBrand.canvas)
    }
}
