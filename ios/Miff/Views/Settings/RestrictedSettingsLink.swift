import SwiftUI

/// L'entrée DISCRÈTE vers les Réglages d'un compte sans Miff (décision du PM du 2026-11-07) : un lien de texte, de même poids que « Se
/// déconnecter », placé sous les deux façons de commencer. Elle ouvre des Réglages RESTREINTS (Mon compte et Confidentialité), où l'on peut
/// aussi supprimer son compte. Zone de toucher d'au moins 44 pt ; VoiceOver lit « Réglages », bouton.
struct RestrictedSettingsLink: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.xs) {
                Image(systemName: "gearshape")
                    .accessibilityHidden(true)
                Text("settings_title")
            }
            .miffTextStyle(textBodyBold16)
            .foregroundColor(colorActionPrimary.color)
            .frame(minWidth: Spacing.minTouchTarget, minHeight: Spacing.minTouchTarget)
        }
        .accessibilityAddTraits(.isButton)
    }
}
