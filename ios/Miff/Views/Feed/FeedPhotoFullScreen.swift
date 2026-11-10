import SwiftUI
import UIKit

// La photo en plein écran (maquette 09, ticket §5.7, décision D24 du PM) : fond noir, la photo COMPLÈTE (jamais recadrée), « Photo de {Prénom} ·
// {heure} », le bouton de fermeture, le zoom au pincement (1x à 4x) et au double toucher, et « Voir où c'était » (le lieu de la photo, donné à Plans,
// COORDONNÉES SEULES, sans nom) avec l'heure PROPRE à la mesure du lieu. Fermeture : le bouton, le geste d'échappement de VoiceOver, ou un glissement
// vers le bas (iOS ne le donne pas à un plein écran : il est refait ici).
// « Enregistrer la photo » (ici SEULEMENT, décision du PM) confie la photo à la feuille de partage du système, sans permission (`PhotoSaveButton`).
// CONFIDENTIALITÉ : la photo et son lieu ne sont que dans la mémoire de l'écran, jamais un fichier de l'app, un journal ou un événement, jamais
// d'écriture directe dans Photos (aucun appel à `UIImageWriteToSavedPhotosAlbum`) ; l'écran est masqué dans la vignette du sélecteur d'applications
// (`hidesInAppSwitcher`) comme le reste du Feed. Rien n'est conservé à la fermeture.

/// La hauteur de ce que dit le pied (titre, lieu, bouton) : elle borne le pied quand le texte est grand ou l'écran en paysage.
private struct FooterHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct PhotoFullScreen: View {
    let opened: OpenedPhoto
    let texts: FeedTexts
    let nowMillis: Int
    let zone: TimeZone
    /// Donne les coordonnées à Plans ; dit ensuite (vrai/faux) si une application les a prises.
    let onOpenMap: (PositionFix, @escaping (Bool) -> Void) -> Void
    let onDismiss: () -> Void

    @State private var zoom: CGFloat = 1
    @State private var zoomBase: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var offsetBase: CGSize = .zero
    @State private var mapUnavailable = false
    @State private var photoArea: CGSize = .zero
    @State private var footerHeight: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let maxZoom: CGFloat = 4
    private static let doubleTapZoom: CGFloat = 2
    private static let dismissDistance: CGFloat = 120
    /// Le pied ne dépasse jamais 40 % de la hauteur : au-delà (grand texte, paysage), il défile et la photo garde sa place.
    private static let footerMaxShare: CGFloat = 0.4

    private var title: String {
        let moment = Moment.of(atMillis: opened.photo.createdAtMillis, nowMillis: nowMillis, zone: zone)
        return L10n.format("update_photo_title", opened.firstName, texts.since(moment))
    }

    var body: some View {
        GeometryReader { screen in
            ZStack {
                Color.black.ignoresSafeArea()
                VStack(spacing: 0) {
                    HStack {
                        RoundOverlayButton(systemName: "xmark", labelKey: "update_photo_close_a11y", onTap: onDismiss)
                        Spacer()
                    }
                    .padding(.horizontal, Spacing.sm)
                    photo
                    scrollingFooter(maxHeight: screen.size.height * Self.footerMaxShare)
                }
            }
            .frame(width: screen.size.width, height: screen.size.height)
        }
        .preferredColorScheme(.dark)
        .accessibilityAction(.escape, onDismiss)
        .hidesInAppSwitcher()
    }

    // MARK: - La photo complète, zoomable

    private var photo: some View {
        GeometryReader { area in
            Image(uiImage: opened.image)
                .resizable()
                .scaledToFit()
                .scaleEffect(zoom)
                .offset(offset)
                .frame(width: area.size.width, height: area.size.height)
                .clipped()
                .contentShape(Rectangle())
                .onAppear { photoArea = area.size }
                .onChange(of: area.size) { photoArea = $0 }
        }
        .onTapGesture(count: 2) { toggleZoom() }
        .gesture(pinch)
        .gesture(drag)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: title))
        .accessibilityAddTraits(.isImage)
        // Le zoom a une alternative pour VoiceOver (le pincement n'existe pas avec lui).
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment:
                setZoom(min(zoom * 2, Self.maxZoom))
            case .decrement:
                setZoom(max(zoom / 2, 1))
            @unknown default:
                break
            }
        }
    }

    /// `offset` ramené dans les bornes : la photo zoomée ne sort jamais de la zone (on ne peut pas la perdre).
    private func clamped(_ proposed: CGSize) -> CGSize {
        PhotoPan.clamp(proposed, imageSize: opened.image.size, area: photoArea, zoom: zoom)
    }

    private var pinch: some Gesture {
        MagnificationGesture()
            .onChanged { value in
                zoom = min(max(zoomBase * value, 1), Self.maxZoom)
                offset = clamped(offset)
            }
            .onEnded { _ in
                zoomBase = zoom
                offsetBase = clamped(offset)
                if zoom == 1 {
                    resetOffset()
                }
            }
    }

    /// Zoomée, la photo se déplace au doigt ; entière (1x), un glissement vers le bas ferme l'écran.
    private var drag: some Gesture {
        DragGesture()
            .onChanged { value in
                if zoom > 1 {
                    offset = clamped(
                        CGSize(
                            width: offsetBase.width + value.translation.width,
                            height: offsetBase.height + value.translation.height
                        )
                    )
                } else if value.translation.height > 0 {
                    offset = CGSize(width: 0, height: value.translation.height)
                }
            }
            .onEnded { value in
                if zoom > 1 {
                    offsetBase = offset
                } else if value.translation.height > Self.dismissDistance {
                    onDismiss()
                } else {
                    resetOffset()
                }
            }
    }

    private func toggleZoom() {
        setZoom(zoom > 1 ? 1 : Self.doubleTapZoom)
    }

    private func setZoom(_ value: CGFloat) {
        // « Réduire les animations » : le zoom change d'un coup.
        if reduceMotion {
            apply(zoom: value)
        } else {
            withAnimation(.easeInOut(duration: 0.2)) { apply(zoom: value) }
        }
    }

    private func apply(zoom value: CGFloat) {
        zoom = value
        zoomBase = value
        offset = clamped(offset)
        offsetBase = offset
    }

    private func resetOffset() {
        if reduceMotion {
            offset = .zero
        } else {
            withAnimation(.easeOut(duration: 0.2)) { offset = .zero }
        }
        offsetBase = .zero
    }

    // MARK: - Le pied : titre, lieu de la photo, « Voir où c'était »

    /// Le pied, défilant au-delà de `maxHeight` (grand texte, paysage) : la photo garde sa place et « Voir où c'était » reste atteignable.
    private func scrollingFooter(maxHeight: CGFloat) -> some View {
        ScrollView {
            footer
                .background(
                    GeometryReader { proxy in
                        Color.clear.preference(key: FooterHeightKey.self, value: proxy.size.height)
                    }
                )
        }
        .onPreferenceChange(FooterHeightKey.self) { footerHeight = $0 }
        .frame(height: min(max(footerHeight, 1), maxHeight))
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(verbatim: title)
                .miffTextStyle(textTitleBold20)
                .foregroundColor(colorUpdateOnPhoto.color)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if let place = opened.photo.place {
                placeSection(place)
            }
            // La photo est confiée à la feuille de partage du système (« Enregistrer l'image »), sans permission.
            PhotoSaveButton(image: opened.image)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func placeSection(_ place: PhotoPlace) -> some View {
        let measured = Moment.of(atMillis: place.measuredAtMillis, nowMillis: nowMillis, zone: zone)
        // L'heure de la MESURE du lieu, qui peut différer de quelques minutes de celle de la photo.
        Text(verbatim: L10n.format("update_photo_position", texts.at(measured)))
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorUpdateOnPhoto.color)
            .fixedSize(horizontal: false, vertical: true)
        if mapUnavailable {
            Text("feed_action_unavailable")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorUpdateOnPhoto.color)
                .fixedSize(horizontal: false, vertical: true)
        }
        Button {
            onOpenMap(PositionFix(lat: place.lat, lng: place.lng)) { accepted in
                mapUnavailable = !accepted
            }
        } label: {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "mappin.and.ellipse")
                    .font(.system(size: 18))
                    .accessibilityHidden(true)
                Text("update_photo_see_where")
                    .miffTextStyle(textBodyBold14)
            }
            .foregroundColor(colorUpdateOnPhoto.color)
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget + Spacing.xs)
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorUpdateOnPhoto.color, lineWidth: strokeHairline * 2)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
