import SwiftUI

/// Écran d'accueil minimal (ce qu'affiche le build Release). Le logo est PROVISOIRE, voir ios/README.md.
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
            MiffProvisionalLogo()
            Text("home_title")
                .miffTextStyle(textDisplayHeadline)
                .foregroundColor(colorTextPrimary.color)
                .accessibilityAddTraits(.isHeader)
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

/// Logo provisoire : un « M » blanc et un coeur jaune-vert sur fond bleu (hypothèse du ticket, en attendant les
/// fichiers du vrai logo). Dessiné en SwiftUI pour ne pas ajouter d'image ; taille fixe (ne suit pas Dynamic Type).
struct MiffProvisionalLogo: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorActionPrimary.color)
            Text(verbatim: "M")
                .font(MiffFontFace.baloo.swiftUIFont(size: 64))
                .foregroundColor(colorTextOnAction.color)
                .offset(y: -Spacing.md)
            Image(systemName: "heart.fill")
                .font(.system(size: 28))
                .foregroundColor(colorBrandPrimary.color)
                .offset(y: Spacing.xl - Spacing.xs)
        }
        .frame(width: Spacing.logoSize, height: Spacing.logoSize)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("home_logo_a11y")
    }
}
