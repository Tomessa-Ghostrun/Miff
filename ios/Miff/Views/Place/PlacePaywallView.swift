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
                dot(filled: true)
                dot(filled: true)
                dot(filled: false)
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
