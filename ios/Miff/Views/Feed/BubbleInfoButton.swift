import SwiftUI

// Le petit bouton « i » au bas de la bulle de la Miff (décision du PM du 2026-11-13, elle remplace « toucher la bulle ») : il ouvre l'écran
// « Les couleurs de ta Miff ». Discret : un glyphe de 22 pt, SANS fond ni contour, dans la couleur du texte posé sur la teinte de la bulle
// (`StatusLevel.onTint`, celle qui atteint le contraste AA sur chaque teinte : vert, orange, rouge, gris), donc il suit la page d'un carrousel.
// La zone de toucher fait 44 pt (`BubbleLayout.infoTouchSize`) même si le glyphe est petit ; elle est posée dans la bande de la marge du bas
// de la bulle, où le contenu n'entre jamais (voir `BubbleLayout.infoButtonFitsBottomBand`) : elle ne recouvre ni le texte, ni « Contacter »,
// ni « Reprendre le partage », ni les pastilles du carrousel (elles sont SOUS la bulle). Le glyphe ne bouge pas avec le balayage : chaque page
// le dessine au même endroit de sa bulle. Le reste de la bulle ne répond à aucun toucher.
struct BubbleInfoButton: View {
    let level: StatusLevel
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "info.circle")
                .font(.system(size: BubbleLayout.infoGlyphSize, weight: .regular))
                .foregroundColor(level.onTint)
                .frame(width: BubbleLayout.infoTouchSize, height: BubbleLayout.infoTouchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.trailing, BubbleLayout.infoTrailingInset)
        .padding(.bottom, BubbleLayout.infoBottomInset)
        // Le libellé est parlé tel quel, jamais « i » : « Comprendre les couleurs ».
        .accessibilityLabel(Text("feed_colors_bubble_action_a11y"))
    }
}
