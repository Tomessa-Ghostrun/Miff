import SwiftUI

/// La confirmation après un abonnement (décision du PM du 2026-11-25 : « Bienvenue sur Miff+ ! », plus chaleureuse et moins vide que « Tu as Miff+. Merci ! ») : le logo de Miff
/// avec un « + » sur un halo, la bienvenue, une ligne, et ce qui est maintenant débloqué (les deux limites de la formule gratuite, lieux et Miffs, et les zones de danger).
/// Écran NON designé : écart listé dans la PR. Les confettis sont dessinés sur tout l'écran par `PaywallConfetti` (voir `PaywallScreen`), pas ici.
struct PaywallSubscribedContent: View {
    private static let haloSize: CGFloat = 144
    private static let logoSize: CGFloat = 96

    var body: some View {
        VStack(spacing: Spacing.md) {
            Spacer().frame(height: Spacing.md)
            logo
            Text("paywall_subscribed")
                .miffTextStyle(textTitleBold24)
                .miffBrandTitleFill()
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .accessibilityAddTraits(.isHeader)
            Text("paywall_subscribed_detail")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            unlocked
        }
        .frame(maxWidth: .infinity)
    }

    /// Le logo de Miff sur un halo de la couleur de la marque, avec un badge « + » (le nom de l'offre) : décoratif pour VoiceOver (le titre le dit).
    private var logo: some View {
        ZStack {
            Circle()
                .fill(colorStatusSafeTint.color)
                .frame(width: Self.haloSize, height: Self.haloSize)
            MiffLogoAvatar(size: Self.logoSize)
            Image(systemName: "plus")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(colorTextPrimary.color)
                .frame(width: Spacing.xl, height: Spacing.xl)
                .background(Circle().fill(colorBrandPrimary.color))
                .overlay(Circle().stroke(colorTextPrimary.color, lineWidth: strokeHairline))
                .offset(x: Self.haloSize / 2 - Spacing.lg, y: Self.haloSize / 2 - Spacing.lg)
        }
        .frame(width: Self.haloSize, height: Self.haloSize)
        .accessibilityHidden(true)
    }

    /// Ce qui est débloqué, dans les mêmes mots que les cartes de l'offre. Déclarer (ou accepter) une zone de danger est réservé au Miff+ DU CRÉATEUR de la Miff ; un membre
    /// peut toujours en demander une.
    private var unlocked: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            unlockedRow("place_paywall_feature_places")
            unlockedRow("place_paywall_feature_miffs")
            unlockedRow("paywall_unlocked_danger")
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
    }

    private func unlockedRow(_ key: String) -> some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: "checkmark")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.xl, height: Spacing.xl)
                .background(Circle().fill(colorActionPrimaryTint20.color))
                .accessibilityHidden(true)
            Text(verbatim: L10n.string(key))
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextPrimary.color)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}
