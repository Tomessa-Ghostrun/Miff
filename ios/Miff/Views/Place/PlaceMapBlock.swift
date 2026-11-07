import SwiftUI

/// La carte d'un lieu avec son cercle, ou ce qui la remplace : avant le choix d'une adresse, un fond neutre qui dit quoi faire ;
/// carte indisponible, une phrase calme (« Ton lieu est bien placé, tu peux continuer ») et « Réessayer ». Jamais bloquant : le
/// lieu reste enregistrable sans la carte (l'adresse et le curseur suffisent, critère TRA2). La description lue par VoiceOver
/// donne le rayon et l'adresse.
struct PlaceMapBlock: View {
    let chosen: ResolvedAddress?
    let radiusM: Int
    let isDanger: Bool
    let height: CGFloat

    @State private var failed = false
    @State private var attempt = 0

    var body: some View {
        ZStack {
            if let chosen, !failed {
                PlaceMapView(
                    lat: chosen.lat,
                    lng: chosen.lng,
                    radiusM: radiusM,
                    isDanger: isDanger,
                    onLoadFailed: { failed = true }
                )
                .id(attempt)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: L10n.format("place_map_a11y", radiusM, chosen.label)))
                .accessibilityAddTraits(.isImage)
            } else {
                placeholder
            }
        }
        .frame(maxWidth: .infinity)
        // La carte a une hauteur fixe ; ce qui la remplace (texte, « Réessayer ») peut grandir avec la taille du texte.
        .frame(height: chosen != nil && !failed ? height : nil)
        .frame(minHeight: height)
        .clipShape(RoundedRectangle(cornerRadius: radiusMd, style: .continuous))
        .onChange(of: chosen) { _ in
            // Une autre adresse : on réessaie la carte.
            failed = false
        }
    }

    private var placeholder: some View {
        ZStack {
            colorBgPlaceholder.color.opacity(0.35)
            VStack(spacing: Spacing.sm) {
                Image(systemName: chosen == nil ? "mappin.and.ellipse" : "map")
                    .font(.system(size: 26, weight: .regular))
                    .foregroundColor(colorTextDescription.color)
                    .accessibilityHidden(true)
                Text(verbatim: L10n.string(chosen == nil ? "place_radius_pick_address" : "place_map_unavailable"))
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                if chosen != nil {
                    Button {
                        failed = false
                        attempt += 1
                    } label: {
                        Text("place_map_retry_action")
                            .miffTextStyle(textBodyBold14)
                            .foregroundColor(colorActionPrimary.color)
                            .frame(minHeight: Spacing.minTouchTarget)
                    }
                }
            }
            .padding(Spacing.md)
        }
    }
}
