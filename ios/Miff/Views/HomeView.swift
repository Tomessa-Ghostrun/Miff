import SwiftUI

/// Écran d'accueil minimal. Le logo est le mot-symbole OFFICIEL (design/assets/logo, validé par le PM le 2026-10-27),
/// dessiné en vectoriel ; sa description pour le lecteur d'écran est « Logo de Miff ».
struct HomeView: View {
    var body: some View {
        ViewThatFits(in: .vertical) {
            content
            ScrollView {
                content
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(gradientAuthBackground.ignoresSafeArea())
    }

    private var content: some View {
        VStack(spacing: Spacing.md) {
            // Le mot-symbole épelle « Miff » : il remplace l'ancien titre en texte et est l'en-tête de l'écran.
            MiffWordmark()
                .frame(width: Spacing.logoWidth)
            Text("home_tagline")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.lg)
        .frame(maxWidth: .infinity)
    }
}
