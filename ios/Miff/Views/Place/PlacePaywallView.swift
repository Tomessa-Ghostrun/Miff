import SwiftUI

/// L'écran de l'offre, relié à l'analyse : seule l'ouverture et la fermeture sont comptées, avec un déclencheur fixe et rien d'autre
/// (critère LIM5, et seulement avec le consentement Analytics : `AnalyticsTracker` le contrôle).
///
/// C'est LE composant de tous les points d'entrée (4e lieu, zone de danger, 2e Miff) : `PaywallPresentation` le montre en plein écran
/// et en crée un NOUVEAU à chaque affichage, donc la croix différée de 3 secondes (`PaywallViewModel`) repart de zéro à chaque fois.
/// Les écrans de l'offre (`PaywallScreen`) ne savent rien de l'analyse.
///
/// ACHAT (SRV-8I) : quand l'achat est allumé dans ce build, `premium` est le coordinateur de l'app et l'affichage reçoit son pilote (`PaywallStoreDriver`), qui demande les prix,
/// lance l'achat et raconte son résultat à l'écran ; sinon (`premium` nil : tout build distribué tant que le PM ne l'allume pas) l'affichage reste « Miff+ arrive bientôt », sans
/// aucun pilote et sans aucun geste d'achat.
@MainActor
struct PlacePaywallView: View {
    let reason: PaywallReason
    let tracker: AnalyticsTracker
    let onClose: () -> Void
    /// PAY-PER-I : appelé UNE fois, à l'affichage effectif de l'offre (là où `paywall_shown` serait émis, avec ou sans consentement aux statistiques), quel que soit le point d'entrée :
    /// l'offre périodique remet alors son compteur à zéro et ne revient plus dans la session. Sans effet par défaut (aperçus, tests).
    let onShown: () -> Void

    @StateObject private var display: PlacePaywallDisplay
    @State private var tracking: PlacePaywallTracking?

    init(
        reason: PaywallReason,
        tracker: AnalyticsTracker,
        premium: PremiumCoordinator?,
        onShown: @escaping () -> Void = {},
        onClose: @escaping () -> Void
    ) {
        self.reason = reason
        self.tracker = tracker
        self.onShown = onShown
        self.onClose = onClose
        _display = StateObject(wrappedValue: PlacePaywallDisplay(reason: reason, premium: premium))
    }

    var body: some View {
        PaywallScreen(model: display.model, actions: display.driver?.actions ?? PaywallActions.none, onClose: onClose)
            // Les prix arrivent (achat allumé seulement) ; annulé quand l'écran disparaît. Le compte à rebours de la croix part de son côté, sans attendre les prix.
            .task {
                await display.driver?.start()
            }
            .onAppear {
                let current = tracking ?? PlacePaywallTracking(reason: reason)
                tracking = current
                if let event = current.appeared() {
                    tracker.track(event)
                    onShown()
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

/// UN affichage de l'offre : son modèle d'écran (la croix différée de 3 secondes, repartie de zéro à chaque affichage) et, quand l'achat est allumé, le pilote qui le branche au store.
@MainActor
final class PlacePaywallDisplay: ObservableObject {
    let model: PaywallViewModel
    let driver: PaywallStoreDriver?

    init(reason: PaywallReason, premium: PremiumCoordinator?) {
        let model = PaywallViewModel(reason: reason, announcer: SystemPaywallAnnouncer())
        self.model = model
        driver = premium.map { PaywallStoreDriver(model: model, premium: $0) }
    }
}

/// Une carte de l'offre (contenu unique, quel que soit le point d'entrée : décision du PM du 2026-11-12).
struct PlacePaywallFeature: View {
    let symbol: String
    let title: String
    let detail: String
    /// La limite de la formule gratuite, sur une troisième ligne en gras (« Limite de 3 lieux sinon »). Nil : aucune.
    var limit: String?

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
                if let limit {
                    Text(verbatim: limit)
                        .miffTextStyle(textBodyBold14)
                        .foregroundColor(colorTextDescription.color)
                        .fixedSize(horizontal: false, vertical: true)
                }
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
