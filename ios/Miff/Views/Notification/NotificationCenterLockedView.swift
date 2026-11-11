import SwiftUI

// Le centre de notifications quand elles sont refusées ou jamais demandées (comportement validé par le PM sur Android, repris ici) : l'écran
// tel qu'il serait avec des notifications derrière lui, FLOU, et par-dessus une carte calme avec UN seul bouton « Activer les notifications ».
// Rien de réel n'est dessiné derrière le flou : seulement des barres génériques, cachées à VoiceOver. Aucune position, aucun nom.

struct NotificationCenterLockedView: View {
    let lock: NotificationCenterLock
    let onEnable: () -> Void

    private var titleKey: String {
        lock == .off ? "notif_center_off_title" : "notif_state_notasked"
    }

    private var bodyKey: String {
        lock == .off ? "notif_center_off_body" : "notif_center_notasked_body"
    }

    var body: some View {
        ZStack {
            NotificationFakeRows()
                .blur(radius: 10)
                .opacity(0.7)
                .accessibilityHidden(true)
            card
                .padding(.horizontal, Spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private var card: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "bell")
                .font(.system(size: 40, weight: .light))
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            Text(verbatim: L10n.string(titleKey))
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextForm.color)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: L10n.string(bodyKey))
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Button(action: onEnable) {
                Text("notif_center_enable_action")
            }
            .buttonStyle(.miffPrimaryFullWidth)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .miffShadow(shadowDefault)
    }
}

/// Les fausses lignes derrière le flou : un point, trois barres, jamais un texte. Ce sont des repères visuels, pas des données.
private struct NotificationFakeRows: View {
    private let rowCount = 5

    var body: some View {
        VStack(spacing: Spacing.md) {
            ForEach(0..<rowCount, id: \.self) { index in
                HStack(alignment: .top, spacing: Spacing.sm) {
                    Circle()
                        .fill(colorBrandPrimary.color)
                        .frame(width: 10, height: 10)
                        .padding(.top, Spacing.xs)
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        bar(colorActionPrimaryTint30, height: Spacing.md, share: index.isMultiple(of: 2) ? 0.7 : 0.55)
                        bar(colorActionPrimaryTint20, height: Spacing.sm + Spacing.xs, share: 0.95)
                        bar(colorActionPrimaryTint10, height: Spacing.sm, share: 0.25)
                    }
                }
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.md)
        .frame(maxWidth: .infinity, alignment: .top)
    }

    private func bar(_ token: ColorToken, height: CGFloat, share: CGFloat) -> some View {
        GeometryReader { proxy in
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(token.color)
                .frame(width: proxy.size.width * share, height: height)
        }
        .frame(height: height)
    }
}
