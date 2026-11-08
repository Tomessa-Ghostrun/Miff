import SwiftUI

/// « Notifications coupées » (ticket §8.4) : l'écran d'aide, calme, qui ne culpabilise pas. Il dit que c'est le choix de la personne,
/// les trois étapes dans les Réglages d'iOS, et propose d'y aller. Aucun interrupteur de l'app ne prétend contourner ce choix.
struct NotificationHelpView: View {
    let onBack: () -> Void

    var body: some View {
        MiffFrame(title: L10n.string("notif_help_title"), onBack: onBack, usesCanvas: true) {
            SettingsBody(text: L10n.string("notif_help_intro"))
            SettingsBody(text: L10n.string("notif_help_choice"))
            SettingsBody(text: L10n.string("notif_help_lead"))
            VStack(alignment: .leading, spacing: Spacing.sm) {
                HelpStep(number: 1, textKey: "notif_help_step_1")
                HelpStep(number: 2, textKey: "notif_help_step_2")
                HelpStep(number: 3, textKey: "notif_help_step_3")
            }
            Button {
                ExternalOpener.openSystemSettings()
            } label: {
                Text("notif_state_open_settings")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            SettingsBody(text: L10n.string("notif_help_footer"))
        }
    }
}

/// Une étape numérotée : un rond bleu pâle avec le numéro, puis le texte.
private struct HelpStep: View {
    let number: Int
    let textKey: String

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Text(verbatim: String(number))
                .miffTextStyle(textBodyBold14)
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.xl, height: Spacing.xl)
                .background(Circle().fill(colorActionPrimaryTint20.color))
            Text(verbatim: L10n.string(textKey))
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }
}
