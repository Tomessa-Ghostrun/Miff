import SwiftUI

/// Le cadre des écrans dont le texte est long (le consentement de la version 3) : le texte défile, et le pied (le bouton,
/// « Annuler », la phrase d'erreur) reste ÉPINGLÉ en bas, toujours visible (maquettes 21 et 24, décision D1 du design). Le pied
/// monte avec le clavier quand il y en a un. Même fond et mêmes marges que `MiffFrame`.
struct MiffPinnedFrame<Content: View, Footer: View>: View {
    let title: String?
    let onBack: (() -> Void)?
    let content: Content
    let footer: Footer

    init(
        title: String?,
        onBack: (() -> Void)?,
        @ViewBuilder content: () -> Content,
        @ViewBuilder footer: () -> Footer
    ) {
        self.title = title
        self.onBack = onBack
        self.content = content()
        self.footer = footer()
    }

    var body: some View {
        MiffFrame(title: title, onBack: onBack) {
            content
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: Spacing.xs) {
                footer
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.sm)
            .padding(.bottom, Spacing.sm)
            .frame(maxWidth: .infinity)
            .background(colorBgCanvas.color.ignoresSafeArea(edges: .bottom))
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(colorBorderDefault.color)
                    .frame(height: strokeHairline)
            }
        }
    }
}
