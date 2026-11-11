import SwiftUI
import UIKit

// « Enregistrer cette photo », dans le plein écran de la photo d'un proche, À CET ENDROIT SEULEMENT (décision du PM : pas sur l'aperçu de la carte, pas dans
// l'Update). Une icône « télécharger » seule, en haut à droite, en face de la croix : le toucher enregistre la photo DIRECTEMENT dans la galerie
// (autorisation d'AJOUT seul, demandée au premier toucher : `PhotoSaveViewModel`, `PhotosLibrarySaver`), sans feuille de partage.
// CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier :
// - rien n'est écrit sans ce toucher ; l'image enregistrée est ré-encodée depuis les pixels (aucun EXIF, aucun lieu) ; la galerie n'est jamais lue ;
// - un refus se dit calmement, avec « Ouvrir les réglages », jamais de reproche ; aucun journal, aucun événement ;
// - l'écran reste masqué dans la vignette du sélecteur d'applications (`hidesInAppSwitcher` du plein écran).
// CHANGEMENT DE POSTURE (à aligner dans les textes légaux, voir `docs/release/update-photo-declarations.md`) : la photo peut sortir de Miff si la personne le
// choisit (c'était déjà possible par capture d'écran), et une permission « ajout à la galerie » existe désormais.

/// Le bouton : UNE icône sans texte (disque sombre translucide de 40 pt dans une zone de 48 pt, comme la croix ; « Réduire la transparence » le rend
/// opaque). Nom parlé « Enregistrer cette photo sur ton téléphone ». Le résultat (enregistrée, refusée, impossible) est un message calme sous l'icône,
/// sans décaler la mise en page, dit UNE fois à VoiceOver (une annonce).
struct PhotoSaveButton: View {
    @StateObject private var model: PhotoSaveViewModel
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.dynamicTypeSize) private var textSize
    @Environment(\.openURL) private var openURL

    /// Largeur d'un message sur plusieurs lignes (refus, impossible) : il ne sort jamais de l'écran, même d'un iPhone SE ; 220 en grand texte.
    private static let wrappedWidth: CGFloat = 260
    private static let largeTextWidth: CGFloat = 220

    private var wrapWidth: CGFloat {
        textSize.isAccessibilitySize ? Self.largeTextWidth : Self.wrappedWidth
    }

    init(image: UIImage) {
        _model = StateObject(wrappedValue: PhotoSaveViewModel(saver: PhotosLibrarySaver(), image: image))
    }

    var body: some View {
        PhotoOverlayIconButton(glyph: .download, labelKey: "feed_photo_download_a11y") {
            Task { await model.save() }
        }
        .overlay(alignment: .topTrailing) {
            if let message = PhotoSaveMessage(state: model.state) {
                messageView(message)
            }
        }
        .task(id: model.state) {
            guard let message = PhotoSaveMessage(state: model.state) else {
                return
            }
            // Calme : dit une fois au lecteur d'écran, puis disparaît seul (plus longtemps quand il y a un bouton à atteindre).
            UIAccessibility.post(notification: .announcement, argument: message.text)
            try? await Task.sleep(nanoseconds: message.seconds * 1_000_000_000)
            model.dismissMessage()
        }
    }

    /// Posé sur la photo, sans rien décaler. Texte normal : sa largeur naturelle ; grand texte : plusieurs lignes dans une largeur fixe (jamais hors écran).
    private func messageView(_ message: PhotoSaveMessage) -> some View {
        let content = VStack(alignment: .trailing, spacing: Spacing.xs) {
            Text(verbatim: message.text)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorUpdateOnPhoto.color)
                .multilineTextAlignment(.trailing)
            if message.offersSettings {
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        openURL(url)
                    }
                } label: {
                    Text("update_action_open_settings")
                        .miffTextStyle(textBodyBold14)
                        .foregroundColor(colorUpdateOnPhoto.color)
                        .underline()
                        .frame(minHeight: Spacing.minTouchTarget)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .background(
            RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                .fill(updateScrimToken(reduceTransparency: reduceTransparency).color)
        )
        return Group {
            if textSize.isAccessibilitySize || message.wraps {
                content
                    .frame(width: wrapWidth, alignment: .trailing)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                content.fixedSize()
            }
        }
        .offset(y: UpdateLayout.roundButtonSize)
    }
}

/// Ce que dit le message calme sous l'icône (jamais « erreur », jamais de reproche).
struct PhotoSaveMessage: Equatable {
    let text: String
    /// « Ouvrir les réglages » : seulement pour un refus (une restriction de l'appareil ne se change pas).
    let offersSettings: Bool
    /// La durée d'affichage, en secondes.
    let seconds: UInt64

    /// Un message long (refus, impossible) passe sur plusieurs lignes dans une largeur fixe ; « Photo enregistrée. » reste sur une ligne.
    var wraps: Bool {
        offersSettings || seconds > 3
    }

    init?(state: PhotoSaveState) {
        switch state {
        case .idle, .saving:
            return nil
        case .saved:
            self.init(key: "feed_photo_download_done", offersSettings: false, seconds: 3)
        case .failed:
            self.init(key: "feed_photo_download_failed", offersSettings: false, seconds: 4)
        case .restricted:
            self.init(key: "ios_feed_photo_download_restricted", offersSettings: false, seconds: 5)
        case .denied:
            self.init(key: "ios_feed_photo_download_denied", offersSettings: true, seconds: 8)
        }
    }

    private init(key: String, offersSettings: Bool, seconds: UInt64) {
        text = L10n.string(key)
        self.offersSettings = offersSettings
        self.seconds = seconds
    }
}
