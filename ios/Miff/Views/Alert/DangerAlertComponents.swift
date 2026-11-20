#if ALERT_DANGER
import SwiftUI
import UIKit

// Les morceaux de l'alerte posés sur d'autres écrans (docs/tickets/alerte-danger.md 7.1, 7.2, 7.4 et 13 ; miroir d'`AlertComponents.kt` d'Android) : la bulle
// bleue de l'onboarding, la feuille « En savoir plus », le mode rouge de l'Update pendant que le doigt tient, les confirmations de la voie accessible.
// TON : factuel et calme. Aucun « ! », aucun « urgent ». L'état n'est jamais porté par la seule couleur : les mots sont toujours là.

// MARK: - La bulle bleue de l'onboarding (maquette 01)

/// « Touche pour donner de tes nouvelles / Maintiens pour déclarer une alerte » (maquette 01, au tutoiement : écart E1). Non bloquante, sans bouton hormis
/// le discret « En savoir plus », elle se retire d'elle-même après environ 6 secondes et au premier toucher ailleurs (l'appelant la retire). Annoncée
/// poliment une seule fois au lecteur d'écran.
struct DangerHintBubble: View {
    let onMore: () -> Void
    let onGone: () -> Void
    var visibleSeconds = DangerAlertRules.hintSeconds

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            VStack(alignment: .leading, spacing: 0) {
                line("alert_hint_tap")
                line("alert_hint_hold")
            }
            .accessibilityElement(children: .combine)
            Button(action: onMore) {
                Text("alert_hint_more")
                    .underline()
                    .miffTextStyle(textBodyBold14)
                    .foregroundColor(colorOnAlert.color)
                    .frame(minHeight: Spacing.minTouchTarget, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm + Spacing.xs)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous).fill(colorActionPrimary.withAlpha(0.92).color)
        )
        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(colorActionPrimary.color, lineWidth: 2))
        .task {
            UIAccessibility.post(
                notification: .announcement,
                argument: L10n.string("alert_hint_tap") + ". " + L10n.string("alert_hint_hold")
            )
            try? await Task.sleep(nanoseconds: UInt64(visibleSeconds * 1_000_000_000))
            if !Task.isCancelled {
                onGone()
            }
        }
    }

    private func line(_ key: String) -> some View {
        Text(verbatim: "• " + L10n.string(key))
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorOnAlert.color)
    }
}

// MARK: - Le mode rouge de l'Update pendant le maintien (maquette 02)

/// L'Update en mode alerte : un voile rouge qui monte du bas comme une barre, et la carte « Je suis en danger » avec sa phrase (maquette 02). Aucun chiffre
/// (un décompte pèse plus), aucun son. `miffName` : la Miff où l'alerte ira, montrée AVANT qu'elle parte, seulement avec plusieurs Miffs (E13).
struct DangerArmedOverlay: View {
    let progress: Double
    let miffName: String?

    private static let titleShape = RoundedRectangle(cornerRadius: 10, style: .continuous)

    var body: some View {
        ZStack {
            colorAlertFill.withAlpha(0.18).color
            GeometryReader { proxy in
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    colorAlertFill.withAlpha(0.50).color
                        .frame(height: proxy.size.height * CGFloat(min(max(progress, 0), 1)))
                }
            }
            VStack(spacing: Spacing.sm) {
                Text("alert_hold_title")
                    .miffTextStyle(textTitleBold24)
                    .foregroundColor(colorOnAlert.color)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(Spacing.md)
                    .background(Self.titleShape.fill(colorAlertBanner.withAlpha(0.85).color))
                    .overlay(Self.titleShape.stroke(colorAlertFill.color, lineWidth: 2))
                // Du blanc sur un voile sombre (la maquette l'écrit en rouge sur la photo, illisible sur une photo claire : écart listé dans la PR).
                scrimmed(Text("alert_hold_hint").miffTextStyle(textBodyRegular14))
                if let miffName {
                    scrimmed(Text(verbatim: L10n.format("alert_hold_miff", miffName)).miffTextStyle(textBodyBold14))
                }
            }
            .padding(.horizontal, Spacing.lg)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        // Une région polie : le lecteur d'écran le dit une fois quand l'écran s'arme.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: L10n.string("alert_hold_title") + ". " + L10n.string("alert_hold_hint")))
    }

    private func scrimmed(_ text: some View) -> some View {
        text
            .foregroundColor(colorOnAlert.color)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, Spacing.xs)
            .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(colorAlertOnPhotoScrim.color))
    }
}

// MARK: - Les fenêtres : « En savoir plus » et la confirmation en deux étapes de la voie accessible (ticket 13)

extension View {
    /// Ce que fait l'alerte, ce qu'elle ne fait pas, où en est la position (ticket 7.1, 14.2). Un seul bouton : « Compris ».
    func dangerInfoAlert(isPresented: Binding<Bool>) -> some View {
        alert(
            Text("alert_info_title"), isPresented: isPresented,
            actions: { Button { } label: { Text("alert_info_close") } },
            message: {
                Text(
                    verbatim: [
                        L10n.string("alert_info_body"), L10n.string("alert_info_position"),
                        L10n.string("alert_not_emergency_note")
                    ].joined(separator: "\n\n")
                )
            }
        )
    }

    /// « Déclarer une alerte à tes proches ? » → « Envoyer l'alerte » / « Annuler » : la seule voie quand un lecteur d'écran ou un contacteur est actif
    /// (le maintien n'est pas praticable), AD5.
    func dangerStartConfirm(
        isPresented: Binding<Bool>,
        miffName: String?,
        onConfirm: @escaping () -> Void
    ) -> some View {
        alert(
            Text("alert_confirm_start_title"), isPresented: isPresented,
            actions: {
                Button { onConfirm() } label: { Text("alert_confirm_start_action") }
                Button(role: .cancel) { } label: { Text("alert_confirm_cancel") }
            },
            message: {
                Text(
                    verbatim: miffName.map { L10n.format("alert_confirm_start_body_miff", $0) }
                        ?? L10n.string("alert_confirm_start_body")
                )
            }
        )
    }

    /// La même chose pour terminer l'alerte (bannière).
    func dangerEndConfirm(isPresented: Binding<Bool>, miffName: String?, onConfirm: @escaping () -> Void) -> some View {
        alert(
            Text("alert_confirm_end_title"), isPresented: isPresented,
            actions: {
                Button { onConfirm() } label: { Text("alert_confirm_end_action") }
                Button(role: .cancel) { } label: { Text("alert_confirm_end_keep") }
            },
            message: {
                // Un nom de Miff pas encore connu (après un relancement) : « Tes proches… », jamais un blanc.
                Text(
                    verbatim: miffName.map { L10n.format("alert_confirm_end_body", $0) }
                        ?? L10n.string("alert_confirm_end_body_no_name")
                )
            }
        )
    }
}
#endif
