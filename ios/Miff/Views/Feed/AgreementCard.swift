import SwiftUI

// Maquette 23 (LOC-2b), A et B. Rien ici ne lit ni n'écrit quoi que ce soit : la carte dit seulement ce qui est vrai (rien n'est partagé, rien
// n'est détecté, pour cette Miff) et propose l'unique chemin de retour vers l'accord.

/// « En attente de ton accord » : un pictogramme de pause ET des mots (jamais la couleur seule), la phrase qui dit la vérité sans la mettre sur la
/// personne, et un seul bouton secondaire « Revoir et accepter ». Pas de bouton de fermeture : elle part avec l'accord ou avec le départ de la Miff.
struct AgreementCard: View {
    let message: String
    let onReview: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: "pause.fill")
                    .font(.system(size: 16))
                    .foregroundColor(colorActionPrimary.color)
                    .frame(width: Spacing.xl, height: Spacing.xl)
                    .background(
                        RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                            .fill(colorActionPrimaryTint10.color)
                    )
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("miff_waiting_title")
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextPrimary.color)
                        .accessibilityAddTraits(.isHeader)
                    Text(verbatim: message)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button(action: onReview) {
                Text("miff_waiting_review")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
    }
}
