import SwiftUI

/// Bouton primaire du design system : fond action/primary, ombre shadow/default, arrondi radius/md, texte body/large
/// (comme Android), état désactivé = fond à 30 %. Le texte peut passer à la ligne (grande taille de texte, traductions
/// longues). `fillsWidth` étire le bouton sur toute la largeur (écrans d'authentification et d'onboarding).
struct MiffPrimaryButtonStyle: ButtonStyle {
    var fillsWidth = false

    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        MiffPrimaryButtonBody(configuration: configuration, fillsWidth: fillsWidth)
    }
}

private struct MiffPrimaryButtonBody: View {
    @Environment(\.isEnabled) private var isEnabled
    let configuration: ButtonStyleConfiguration
    let fillsWidth: Bool

    var body: some View {
        configuration.label
            .miffTextStyle(textBodyLarge)
            .foregroundColor(colorTextOnAction.color)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: fillsWidth ? .infinity : nil, minHeight: Spacing.minTouchTarget)
            .background(background)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .padding(.vertical, Spacing.xs)
    }

    @ViewBuilder
    private var background: some View {
        if isEnabled {
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorActionPrimary.color)
                .miffShadow(shadowDefault)
        } else {
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorActionPrimaryTint30.color)
        }
    }
}

/// Bouton secondaire : fond blanc, texte bleu et trait bleu de 2 pt (l'« Annuler » des maquettes). Pour les actions qui ne
/// doivent pas ressembler à la chose principale à faire. Même hauteur, même arrondi et même retour à la ligne que le bouton
/// primaire ; désactivé = texte et trait à 30 %.
struct MiffSecondaryButtonStyle: ButtonStyle {
    var fillsWidth = false

    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        MiffSecondaryButtonBody(configuration: configuration, fillsWidth: fillsWidth)
    }
}

private struct MiffSecondaryButtonBody: View {
    @Environment(\.isEnabled) private var isEnabled
    let configuration: ButtonStyleConfiguration
    let fillsWidth: Bool

    private var tint: Color {
        (isEnabled ? colorActionPrimary : colorActionPrimaryTint30).color
    }

    var body: some View {
        configuration.label
            .miffTextStyle(textBodyLarge)
            .foregroundColor(tint)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: fillsWidth ? .infinity : nil, minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous).stroke(tint, lineWidth: strokeAccent)
            )
            .opacity(configuration.isPressed ? 0.85 : 1)
            .padding(.vertical, Spacing.xs)
    }
}

extension ButtonStyle where Self == MiffSecondaryButtonStyle {
    /// Le bouton secondaire sur toute la largeur.
    static var miffSecondaryFullWidth: MiffSecondaryButtonStyle {
        MiffSecondaryButtonStyle(fillsWidth: true)
    }
}

extension ButtonStyle where Self == MiffPrimaryButtonStyle {
    static var miffPrimary: MiffPrimaryButtonStyle {
        MiffPrimaryButtonStyle()
    }

    /// Le bouton primaire sur toute la largeur.
    static var miffPrimaryFullWidth: MiffPrimaryButtonStyle {
        MiffPrimaryButtonStyle(fillsWidth: true)
    }
}
