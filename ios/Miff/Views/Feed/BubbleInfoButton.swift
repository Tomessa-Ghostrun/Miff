import SwiftUI

// Le petit bouton « i » de la bulle de la Miff (décision du PM du 2026-11-13) : il ouvre l'écran « Les couleurs de ta Miff ». Il est EN DEHORS de
// la bulle (voir `BubbleLayout`) : dans le coin bas-droit de sa boîte, où la tache ne va pas, jamais dans son mouvement. Son bas est celui de la
// bulle, sa droite celle des cartes du Feed. Un disque de la COULEUR DE LA BULLE (la teinte du niveau, celle de la page d'un carrousel) avec un
// « i » dans la couleur du texte posé sur cette teinte (`StatusLevel.onTint`, contraste AA : au moins 4,5 pour 1 sur chaque teinte). La zone de
// toucher fait 44 pt même si le disque en fait 36 ; elle tient dans la marge du bas de la bulle, où le contenu n'entre jamais : elle ne recouvre
// ni le texte, ni « Contacter », ni « Reprendre le partage », ni les pastilles du carrousel (elles sont SOUS la bulle). Le reste de la bulle ne
// répond à aucun toucher.
struct BubbleInfoButton: View {
    let level: StatusLevel
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "info")
                .font(.system(size: BubbleLayout.infoGlyphSize, weight: .semibold))
                .foregroundColor(level.onTint)
                .frame(width: BubbleLayout.infoDiscSize, height: BubbleLayout.infoDiscSize)
                .background(Circle().fill(level.tint))
                // Le disque est collé au coin (bas et droite) ; la zone de toucher s'étend vers le haut et la gauche.
                .frame(
                    width: BubbleLayout.infoTouchSize, height: BubbleLayout.infoTouchSize, alignment: .bottomTrailing
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // Le libellé est parlé tel quel, jamais « i » : « Comprendre les couleurs » (bouton).
        .accessibilityLabel(Text("feed_colors_bubble_action_a11y"))
    }
}
