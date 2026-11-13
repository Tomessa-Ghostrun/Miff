import SwiftUI

/// L'écran de l'offre, relié à l'analyse : seule l'ouverture et la fermeture sont comptées, avec un déclencheur fixe et rien d'autre
/// (critère LIM5, et seulement avec le consentement Analytics : `AnalyticsTracker` le contrôle).
///
/// C'est LE composant de tous les points d'entrée (4e lieu, zone de danger, 2e Miff) : `PaywallPresentation` le montre en plein écran
/// et en crée un NOUVEAU à chaque affichage, donc la croix différée de 3 secondes (`PaywallViewModel`) repart de zéro à chaque fois.
/// Les écrans de l'offre (`PaywallScreen`) ne savent rien de l'analyse.
@MainActor
struct PlacePaywallView: View {
    let reason: PaywallReason
    let tracker: AnalyticsTracker
    let onClose: () -> Void

    @StateObject private var model: PaywallViewModel
    @State private var tracking: PlacePaywallTracking?

    init(reason: PaywallReason, tracker: AnalyticsTracker, onClose: @escaping () -> Void) {
        self.reason = reason
        self.tracker = tracker
        self.onClose = onClose
        _model = StateObject(wrappedValue: PaywallViewModel(reason: reason, announcer: SystemPaywallAnnouncer()))
    }

    var body: some View {
        PaywallScreen(model: model, onClose: onClose)
            .onAppear {
                let current = tracking ?? PlacePaywallTracking(reason: reason)
                tracking = current
                if let event = current.appeared() {
                    tracker.track(event)
                }
            }
            // La croix et la fermeture de la feuille finissent toutes par la disparition de l'écran.
            .onDisappear {
                if let event = tracking?.disappeared() {
                    tracker.track(event)
                }
            }
    }
}

/// Une carte de l'offre (contenu unique, quel que soit le point d'entrée : décision du PM du 2026-11-12).
struct PlacePaywallFeature: View {
    let symbol: String
    let title: String
    let detail: String

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: 40, height: 40)
                .background(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint20.color)
                )
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: title)
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextPrimary.color)
                Text(verbatim: detail)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.md)
        .frame(minHeight: Spacing.minTouchTarget)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
        .accessibilityElement(children: .combine)
    }
}
