import SwiftUI

/// L'entrée vers les Réglages d'un compte sans Miff (décision du PM du 2026-11-07, alignée sur Android PR #116) : la roue crantée du
/// Profil (même disque blanc, même engrenage bleu), posée EN HAUT À DROITE de l'écran. Elle ouvre des Réglages RESTREINTS (Mon compte et
/// Confidentialité), où l'on peut aussi supprimer son compte. Cible d'au moins 44 pt ; VoiceOver lit « Réglages », bouton (même texte que
/// l'engrenage du Profil : aucune clé de plus).
struct RestrictedSettingsLink: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "gearshape")
                .font(.system(size: Spacing.sectionIcon, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .background(Circle().fill(colorBgSurface.color))
                .miffShadow(shadowDefault)
        }
        .accessibilityLabel(Text("profile_tab_settings_a11y"))
        .accessibilityAddTraits(.isButton)
    }
}

extension View {
    /// Pose la roue crantée des Réglages restreints en haut à droite, au-dessus du contenu.
    func restrictedSettingsGear(visible: Bool = true, action: @escaping () -> Void) -> some View {
        overlay(alignment: .topTrailing) {
            if visible {
                RestrictedSettingsLink(action: action)
                    .padding(.top, Spacing.sm)
                    .padding(.trailing, Spacing.lg)
            }
        }
    }
}
