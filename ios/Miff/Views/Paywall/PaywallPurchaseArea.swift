import SwiftUI

/// Ce que l'écran d'offre peut demander au store. En production (« Miff+ arrive bientôt », avant SRV-8I) rien n'est branché et rien
/// n'est montré qui l'appellerait : les gestes n'existent que dans les états à offre, qu'aucun écran de production n'atteint encore.
struct PaywallActions {
    let onPurchase: (PaywallPlan) -> Void
    let onRestore: () -> Void
    let onRetry: () -> Void

    static let none = PaywallActions(onPurchase: { _ in }, onRestore: {}, onRetry: {})
}

/// La zone d'achat (fixée en bas de l'écran, sauf aux très grandes tailles de texte) : la ligne de prix la plus proche du bouton
/// reste visible à côté de lui. Ce qu'elle montre dépend de l'état (ticket §5.2).
struct PaywallPurchaseArea: View {
    @ObservedObject var model: PaywallViewModel
    let actions: PaywallActions
    let onClose: () -> Void

    var body: some View {
        switch model.state {
        case .comingSoon:
            PaywallComingSoon(reason: model.reason)
        case .loading:
            PaywallLoadingArea()
        case .unavailable:
            PaywallUnavailableArea(retry: actions.onRetry)
        case .offers(let offers):
            offersArea(offers, purchasing: nil, message: nil)
        case .purchasing(let offers, let plan):
            offersArea(offers, purchasing: plan, message: .working)
        case .pending(let offers):
            offersArea(offers, purchasing: nil, message: .pending)
        case .failed(let offers):
            offersArea(offers, purchasing: nil, message: .failed)
        case .restoring(let offers):
            offersArea(offers, purchasing: nil, message: .working)
        case .restoreNone(let offers):
            offersArea(offers, purchasing: nil, message: .restoreNone)
        case .subscribed:
            Button(action: onClose) {
                Text("paywall_continue")
            }
            .buttonStyle(.miffPrimaryFullWidth)
        }
    }

    private func offersArea(_ offers: PaywallOffers, purchasing: PaywallPlan?, message: PaywallMessage?) -> some View {
        PaywallOffersArea(
            offers: offers,
            layout: PaywallLayout.of(offers, showsAllOffers: model.showsAllOffers),
            purchasing: purchasing,
            message: message,
            busy: model.state.isBusy,
            actions: actions,
            onShowAllOffers: { model.showAllOffers() }
        )
    }
}

/// « Miff+ arrive bientôt » : la variante SANS achat, la seule visible en production. Aucun prix, aucun bouton d'essai, aucun bouton
/// qui ne fait rien : seulement la phrase qui dit où l'on en est et ce qui reste en place.
struct PaywallComingSoon: View {
    let reason: PaywallReason

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Text("place_paywall_soon")
                .miffTextStyle(textTitleBold20)
                .foregroundColor(colorTextPrimary.color)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: L10n.string(reason.soonBodyKey))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.sm)
        .accessibilityElement(children: .combine)
    }
}

/// Les prix arrivent : un repère de la hauteur finale du bouton (pas de saut de mise en page), et un texte. Aucun bouton d'achat.
struct PaywallLoadingArea: View {
    var body: some View {
        VStack(spacing: Spacing.sm) {
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorBgPlaceholder.color.opacity(0.5))
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget + Spacing.sm)
                .accessibilityHidden(true)
            Text("paywall_loading")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
        }
    }
}

/// Les prix ne sont pas disponibles : un message calme et « Réessayer ». Aucun prix, aucun bouton d'achat.
struct PaywallUnavailableArea: View {
    let retry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.sm) {
            Text("paywall_unavailable")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextPrimary.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Button(action: retry) {
                Text("paywall_retry")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        }
    }
}

/// L'étiquette jaune posée sur le coin du bouton (maquettes : « Annulable à tout moment », « Économisez 30 % » devenu « presque 30 %
/// d'économie »). Texte noir sur le jaune-vert de la marque (contraste élevé).
struct PaywallBubble: View {
    let key: String

    var body: some View {
        Text(verbatim: L10n.string(key))
            .miffTextStyle(textCaptionBold12)
            .foregroundColor(colorTextPrimary.color)
            .multilineTextAlignment(.trailing)
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, Spacing.xs)
            .background(RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(colorBrandPrimary.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                    .stroke(colorTextPrimary.color, lineWidth: strokeHairline)
            )
    }
}
