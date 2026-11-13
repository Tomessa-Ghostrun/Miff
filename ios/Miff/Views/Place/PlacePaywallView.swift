import SwiftUI

/// L'écran de l'offre, relié à l'analyse : seule l'ouverture et la fermeture sont comptées, avec un déclencheur fixe et rien d'autre
/// (critère LIM5, et seulement avec le consentement Analytics : `AnalyticsTracker` le contrôle).
struct PlacePaywallView: View {
    let reason: PaywallReason
    let tracker: AnalyticsTracker
    let onClose: () -> Void

    @State private var tracking: PlacePaywallTracking?

    var body: some View {
        PlacePaywallContent(reason: reason, onClose: onClose)
            .onAppear {
                let current = tracking ?? PlacePaywallTracking(reason: reason)
                tracking = current
                if let event = current.appeared() {
                    tracker.track(event)
                }
            }
            // Croix, « Plus tard » et geste retour : tous finissent par la disparition de l'écran.
            .onDisappear {
                if let event = tracking?.disappeared() {
                    tracker.track(event)
                }
            }
    }
}

/// L'offre affichée quand un compte gratuit demande un 4e lieu, une zone de danger (maquettes 14 et 15) ou une 2e Miff (sélecteur du
/// Feed, décision du PM du 2026-11-08) : l'écran de l'offre avec une phrase calme en haut qui dit POURQUOI il s'affiche.
/// PROVISOIRE (décision QL15) : aucun achat n'existe encore, donc le bouton d'essai et les prix ne sont pas dessinés
/// (un prix n'est jamais écrit à la main, et un bouton qui ne fait rien serait un mensonge) ; « Miff+ arrive bientôt » dit où l'on en est. Rien n'est écrit ici : quitter l'écran ne change rien.
///
/// « Notifications intelligentes » n'est PAS listée : aucune notification n'est envoyée pour l'instant (risque R13 : ne jamais
/// promettre ce qui n'existe pas). Sans état propre.
struct PlacePaywallContent: View {
    let reason: PaywallReason
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(spacing: Spacing.md) {
                    PlacePaywallContext(reason: reason)
                    Text("place_paywall_headline")
                        .miffTextStyle(textTitleBold24)
                        .miffBrandTitleFill()
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .accessibilityAddTraits(.isHeader)
                    PlacePaywallFeature(
                        symbol: "mappin.and.ellipse",
                        title: L10n.string("place_paywall_feature_places"),
                        detail: L10n.string("place_paywall_feature_places_desc"),
                        highlighted: reason == .limit
                    )
                    PlacePaywallFeature(
                        symbol: "person.2",
                        title: L10n.string("place_paywall_feature_miffs"),
                        detail: L10n.string("place_paywall_feature_miffs_desc"),
                        highlighted: reason == .extraMiff
                    )
                    PlacePaywallFeature(
                        symbol: "exclamationmark.triangle",
                        title: L10n.string("place_paywall_feature_danger"),
                        detail: L10n.string("place_paywall_feature_danger_desc"),
                        highlighted: reason == .danger
                    )
                    Text("place_paywall_soon")
                        .miffTextStyle(textTitleBold20)
                        .foregroundColor(colorTextPrimary.color)
                        .multilineTextAlignment(.center)
                        .padding(.top, Spacing.sm)
                    Text(verbatim: L10n.string(reason.soonBodyKey))
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextDescription.color)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
            }
            Button(action: onClose) {
                Text("place_paywall_later")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
        }
        .background(gradientAuthBackground.ignoresSafeArea())
    }

    private var topBar: some View {
        ZStack {
            MiffWordmark()
                .frame(width: Spacing.logoWidth / 2)
            HStack {
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(colorBgSurface.color)
                        .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                        .background(Circle().fill(colorBgPlaceholder.color.opacity(0.7)))
                }
                .accessibilityLabel(Text(verbatim: L10n.string("place_paywall_close_a11y")))
                Spacer()
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
    }
}

/// La phrase calme du haut : pourquoi l'offre s'affiche (maquettes 14 et 15).
struct PlacePaywallContext: View {
    let reason: PaywallReason

    var body: some View {
        HStack(spacing: Spacing.md) {
            leading
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(verbatim: L10n.string(reason.titleKey))
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextPrimary.color)
                    .accessibilityAddTraits(.isHeader)
                Text(verbatim: L10n.string(reason.bodyKey))
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.md)
        // L'ombre est celle du FOND seulement : posée sur toute la carte, elle doublerait aussi le texte.
        .background(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorBgSurface.color)
                .miffShadow(shadowDefault)
        )
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var leading: some View {
        switch reason {
        case .limit:
            HStack(spacing: Spacing.xs) {
                // « Tu as atteint 3 lieux » : trois pastilles pleines (la limite gratuite est atteinte), comme sur Android.
                dot(filled: true)
                dot(filled: true)
                dot(filled: true)
            }
            .accessibilityHidden(true)
        case .danger:
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 22, weight: .regular))
                .foregroundColor(colorTextError.color)
                .frame(width: 40, height: 40)
                .background(Circle().fill(colorStatusDangerTint.color))
                .accessibilityHidden(true)
        case .extraMiff:
            // Une 2e Miff n'est pas un danger : le même pictogramme que la ligne « Nombre de Miffs illimité », en bleu calme.
            Image(systemName: "person.2")
                .font(.system(size: 22, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: 40, height: 40)
                .background(Circle().fill(colorActionPrimaryTint20.color))
                .accessibilityHidden(true)
        }
    }

    /// Une des trois pastilles de la limite : pleine pour un lieu qui existe, en pointillés pour celui qui manque.
    private func dot(filled: Bool) -> some View {
        Image(systemName: filled ? "mappin" : "plus")
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(filled ? colorBgSurface.color : colorTextDescription.color)
            .frame(width: 32, height: 32)
            .background(Circle().fill(filled ? colorActionPrimary.color : Color.clear))
            .overlay(Circle().stroke(filled ? Color.clear : colorBorderStrong.color, lineWidth: strokeHairline))
    }
}

/// Une ligne de l'offre ; la ligne qui explique l'écran a un contour bleu (accompagné du texte de l'encart : jamais la couleur
/// seule).
struct PlacePaywallFeature: View {
    let symbol: String
    let title: String
    let detail: String
    let highlighted: Bool

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
                .stroke(
                    (highlighted ? colorBorderAction : colorBorderDefault).color,
                    lineWidth: highlighted ? strokeAccent : strokeHairline
                )
        )
        .accessibilityElement(children: .combine)
    }
}
