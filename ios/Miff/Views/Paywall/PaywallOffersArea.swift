import SwiftUI

/// Le message calme au-dessus des boutons (ticket §5.2) : jamais de rouge, de point d'exclamation ni de code d'erreur.
enum PaywallMessage: Equatable {
    case working
    case pending
    case failed
    case restoreNone

    var text: String {
        switch self {
        case .working:
            return L10n.string("paywall_working")
        case .pending:
            return L10n.string("paywall_pending")
        case .failed:
            return L10n.string("paywall_error_calm")
        case .restoreNone:
            return L10n.format("paywall_restore_none", L10n.string("ios_paywall_store_name"))
        }
    }
}

/// Les boutons d'achat, dans l'une des deux dispositions des maquettes. Les prix viennent du store (`PaywallOffers`).
struct PaywallOffersArea: View {
    let offers: PaywallOffers
    let layout: PaywallLayout
    /// La formule dont l'achat est en cours (son bouton montre un indicateur).
    let purchasing: PaywallPlan?
    let message: PaywallMessage?
    /// Un achat, une restauration ou un paiement en attente : tous les boutons sont inactifs (aucun double achat).
    let busy: Bool
    let actions: PaywallActions
    let onShowAllOffers: () -> Void

    var body: some View {
        VStack(spacing: Spacing.xs) {
            if let message {
                Text(verbatim: message.text)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            switch layout {
            case .trial:
                trialLayout
            case .plans:
                plansLayout
            }
            if message == .failed || message == .restoreNone {
                retryLink
            }
        }
    }

    /// « Réessayer » après une erreur calme ou une restauration sans résultat (ticket §5.2) : relit les offres du store.
    private var retryLink: some View {
        Button(action: actions.onRetry) {
            Text("paywall_retry")
                .miffTextStyle(textBodyBold14)
                .underline()
                .foregroundColor(colorActionPrimary.color)
                .frame(minHeight: Spacing.minTouchTarget)
        }
    }

    private var annualPrice: String {
        PaywallPriceFormat.string(offers.annual)
    }

    private var monthlyEquivalent: String {
        PaywallPriceFormat.string(PaywallPriceFormat.monthlyEquivalent(ofAnnual: offers.annual))
    }

    // MARK: - Maquette « essai gratuit »

    /// Le bouton d'essai, sa bulle « Annulable à tout moment » et, dessous, le PRIX FACTURÉ (« Puis 59,88 € par an ») en gras : c'est la
    /// ligne de prix la plus visible ; l'équivalent mensuel est plus petit (règle d'Apple et de Google, ticket E2).
    private var trialLayout: some View {
        VStack(spacing: Spacing.xs) {
            withBubble("paywall_cancel_anytime") {
                planButton(.annual) {
                    Text("paywall_cta_trial")
                }
            }
            Text(verbatim: L10n.format("paywall_trial_then", annualPrice))
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextPrimary.color)
                .multilineTextAlignment(.center)
            Text(verbatim: L10n.format("paywall_monthly_equivalent", monthlyEquivalent))
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
            Button(action: onShowAllOffers) {
                Text("paywall_see_all_offers")
                    .miffTextStyle(textBodyBold14)
                    .underline()
                    .foregroundColor(colorActionPrimary.color)
                    .frame(minHeight: Spacing.minTouchTarget)
            }
            .disabled(busy)
        }
    }

    // MARK: - Maquette « annuel / sans engagement »

    /// L'annuel (prix annuel d'abord, équivalent mensuel en dessous, plus petit, et la bulle d'économie) et le mensuel (« Sans engagement »).
    private var plansLayout: some View {
        VStack(spacing: Spacing.xs) {
            withBubble("paywall_annual_savings") {
                planButton(.annual) {
                    VStack(spacing: 0) {
                        Text(verbatim: L10n.format("paywall_plan_annual_title", annualPrice))
                        Text(verbatim: L10n.format("paywall_monthly_equivalent", monthlyEquivalent))
                            .miffTextStyle(textBodyRegular14)
                    }
                }
            }
            planButton(.monthly) {
                VStack(spacing: 0) {
                    Text(verbatim: L10n.format("paywall_plan_monthly_title", PaywallPriceFormat.string(offers.monthly)))
                    Text("paywall_no_commitment")
                        .miffTextStyle(textBodyRegular14)
                }
            }
        }
    }

    /// La bulle est posée AU-DESSUS du bouton, alignée à sa fin, et ne le recouvre que de 4 pt (dans la marge du bouton) : elle ne
    /// couvre jamais son texte, même aux grandes tailles de texte.
    private func withBubble<Content: View>(_ key: String, @ViewBuilder button: () -> Content) -> some View {
        VStack(alignment: .trailing, spacing: -Spacing.xs) {
            PaywallBubble(key: key)
                .zIndex(1)
            button()
        }
    }

    /// Le bouton d'une formule : l'annuel en bouton plein, le mensuel en bouton à contour (maquette), un indicateur pendant l'achat.
    @ViewBuilder
    private func planButton<Content: View>(_ plan: PaywallPlan, @ViewBuilder label: () -> Content) -> some View {
        let purchase = { actions.onPurchase(plan) }
        let button = Button(action: purchase) {
            if purchasing == plan {
                ProgressView()
                    .tint(plan == .annual ? colorTextOnAction.color : colorActionPrimary.color)
            } else {
                label()
            }
        }
        .disabled(busy)
        if plan == .annual {
            button.buttonStyle(.miffPrimaryFullWidth)
        } else {
            button.buttonStyle(.miffSecondaryFullWidth)
        }
    }
}
