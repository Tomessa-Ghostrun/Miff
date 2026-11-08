import SwiftUI

// Ce que les notifications posent dans le Feed : la cloche de l'en-tête (un simple point quand il y a du non lu, jamais un chiffre) et
// la carte « deuxième chance » (non modale, une seule fois).

/// La cloche de l'en-tête du Feed. Le point dit « il y a du nouveau » sans chiffre (calme) ; VoiceOver, lui, annonce le nombre.
struct NotificationBell: View {
    let unreadCount: Int
    let action: () -> Void

    private var spokenLabel: String {
        unreadCount > 0
            ? L10n.plural("notif_bell_unread_a11y", count: unreadCount)
            : L10n.string("notif_bell_a11y")
    }

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "bell")
                    .font(.system(size: 20, weight: .regular))
                    .foregroundColor(colorActionPrimary.color)
                    .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                if unreadCount > 0 {
                    Circle()
                        .fill(colorBrandPrimary.color)
                        .overlay(Circle().stroke(colorBgCanvas.color, lineWidth: strokeAccent))
                        .frame(width: 12, height: 12)
                        .padding(.top, Spacing.sm)
                        .padding(.trailing, Spacing.sm)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: spokenLabel))
    }
}

/// « Un rappel si tu oublies de donner de tes nouvelles ? » : une carte discrète en haut du Feed, avec « Activer » et « Pas maintenant ».
/// Pas de bouton de fermeture à part : l'un ou l'autre la fait partir, et elle ne revient pas.
struct NotificationSecondChanceCard: View {
    let onActivate: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: "bell")
                    .font(.system(size: 16))
                    .foregroundColor(colorActionPrimary.color)
                    .frame(width: Spacing.xl, height: Spacing.xl)
                    .background(
                        RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                            .fill(colorActionPrimaryTint10.color)
                    )
                    .accessibilityHidden(true)
                Text("notif_second_chance_title")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextPrimary.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
            }
            Button(action: onActivate) {
                Text("notif_second_chance_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
            Button(action: onDismiss) {
                Text("notif_second_chance_later")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorActionPrimary.color)
                    .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            }
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
    }
}
