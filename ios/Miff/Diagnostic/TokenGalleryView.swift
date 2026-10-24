#if DEBUG || INTERNAL
import SwiftUI

/// Galerie des tokens du design system (couleurs avec leur nom de code, styles de texte, arrondis, ombres,
/// dégradés), pour comparaison visuelle avec Figma. Les noms de tokens sont des identifiants de code (les mêmes
/// qu'Android), pas des textes pour l'utilisateur.
struct TokenGalleryView: View {
    private static let swatchSize: CGFloat = 40
    private static let sampleBoxHeight: CGFloat = 56
    private static let sampleBoxWidth: CGFloat = 96

    var body: some View {
        SectionCard("diag_gallery_title", section: .gallery) {
            heading("diag_gallery_colors")
            ForEach(ColorTokens.all) { entry in
                ColorRow(name: entry.name, token: entry.value)
            }

            heading("diag_gallery_text")
            let sample = L10n.string("diag_gallery_sample")
            ForEach(TextStyleTokens.all, id: \.name) { token in
                textSample(token, sample: sample)
            }
            Text(verbatim: sample)
                .miffTextStyle(textTitleBold24)
                .miffBrandTitleFill()

            heading("diag_gallery_radii")
            radiiRow

            heading("diag_gallery_shadows")
            shadowsRow

            heading("diag_gallery_gradients")
            gradientSample(name: "GradientBrandTitle", gradient: gradientBrandTitle)
            gradientSample(name: "GradientAuthBackground", gradient: gradientAuthBackground)
        }
    }

    private func heading(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .miffTextStyle(textTitleBold20)
            .foregroundColor(colorTextPrimary.color)
            .padding(.top, Spacing.sm)
            .accessibilityAddTraits(.isHeader)
    }

    private func caption(_ text: String) -> some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyCaption)
            .foregroundColor(colorTextSecondary.color)
    }

    private func textSample(_ token: TextStyleToken, sample: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(verbatim: L10n.format("diag_gallery_style_meta", Int(token.size), Int(token.lineHeight)))
                .miffTextStyle(textCaptionBold12)
                .foregroundColor(colorTextSecondary.color)
            caption(token.name)
            Text(verbatim: sample)
                .miffTextStyle(token)
                .foregroundColor(colorTextPrimary.color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var radiiRow: some View {
        let radii: [NamedToken<CGFloat>] = [
            NamedToken("RadiusMd", radiusMd), NamedToken("RadiusSm", radiusSm), NamedToken("RadiusFull", radiusFull)
        ]
        return HStack(alignment: .top, spacing: Spacing.md) {
            ForEach(radii, id: \.name) { entry in
                VStack(spacing: 0) {
                    RoundedRectangle(cornerRadius: entry.value, style: .continuous)
                        .fill(colorActionPrimaryTint20.color)
                        .overlay(
                            RoundedRectangle(cornerRadius: entry.value, style: .continuous)
                                .stroke(colorBorderAction.color, lineWidth: strokeHairline)
                        )
                        .frame(width: Self.swatchSize + Spacing.sm, height: Self.swatchSize + Spacing.sm)
                    caption(entry.name)
                    if entry.value != radiusFull {
                        caption(L10n.format("diag_gallery_radius_meta", Int(entry.value)))
                    }
                }
            }
        }
    }

    private var shadowsRow: some View {
        let shadows: [NamedToken<ShadowToken>] = [
            NamedToken("ShadowDefault", shadowDefault), NamedToken("ShadowSubtle", shadowSubtle)
        ]
        return HStack(alignment: .top, spacing: Spacing.lg) {
            ForEach(shadows, id: \.name) { entry in
                VStack(spacing: Spacing.xs) {
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .fill(colorBgSurface.color)
                        .miffShadow(entry.value)
                        .frame(width: Self.sampleBoxWidth, height: Self.sampleBoxHeight)
                    caption(entry.name)
                }
            }
        }
        .padding(Spacing.sm)
    }

    private func gradientSample(name: String, gradient: LinearGradient) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            caption(name)
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(gradient)
                .overlay(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
                )
                .frame(height: Self.sampleBoxHeight)
        }
    }
}

/// Une couleur : pastille (sur fond de damier simplifié pour voir la transparence), nom de code, valeur.
private struct ColorRow: View {
    let name: String
    let token: ColorToken

    var body: some View {
        HStack(spacing: Spacing.md) {
            ZStack {
                RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(colorBgPlaceholder.color)
                RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(token.color)
            }
            .overlay(
                RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                    .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
            )
            .frame(width: 40, height: 40)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: name)
                    .miffTextStyle(textBodyBold14)
                    .foregroundColor(colorTextPrimary.color)
                Text(verbatim: valueLabel)
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextSecondary.color)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }

    /// « #RRGGBB » pour une couleur opaque, « #RRGGBB · NN % » sinon (opacité en pourcentage, comme dans le design system).
    private var valueLabel: String {
        token.alphaPercent == 100
            ? token.hexString
            : L10n.format("diag_gallery_color_alpha", token.hexString, token.alphaPercent)
    }
}
#endif
