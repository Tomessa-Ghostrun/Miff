import SwiftUI

/// Bouton primaire du design system : fond action/primary, ombre shadow/default, arrondi radius/md,
/// etat desactive = fond a 30 %. Le texte peut passer a la ligne (grande taille de texte, traductions longues).
struct MiffPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        MiffPrimaryButtonBody(configuration: configuration)
    }
}

private struct MiffPrimaryButtonBody: View {
    @Environment(\.isEnabled) private var isEnabled
    let configuration: ButtonStyleConfiguration

    var body: some View {
        configuration.label
            .miffTextStyle(textBodyBold16)
            .foregroundColor(colorTextOnAction.color)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: Spacing.minTouchTarget)
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

extension ButtonStyle where Self == MiffPrimaryButtonStyle {
    static var miffPrimary: MiffPrimaryButtonStyle {
        MiffPrimaryButtonStyle()
    }
}
