import SwiftUI
import UIKit

// « Enregistrer cette photo », dans le plein écran de la photo d'un proche, À CET ENDROIT SEULEMENT (décision du PM : pas sur l'aperçu de la carte, pas dans
// l'Update). CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier :
// - la photo est CONFIÉE AU SYSTÈME par la feuille de partage (`UIActivityViewController`) : c'est le système qui propose « Enregistrer l'image »,
//   « Copier », AirDrop, etc. et qui écrit, si la personne le choisit. AUCUNE permission, AUCUNE clé `NSPhotoLibrary*` (le contrôle du binaire
//   les interdit toujours), AUCUN appel à `UIImageWriteToSavedPhotosAlbum` ni à `Photos` : l'app n'écrit JAMAIS la photo, ni dans un fichier ni dans
//   la photothèque. Rien n'est journalisé ni envoyé.
// - ce qui est donné à la feuille est un `UIImage` (des pixels) : aucune métadonnée, ni EXIF ni position, n'est jamais dans ce qui est enregistré
//   (les octets reçus d'un autre téléphone n'ont d'ailleurs jamais servi de fichier). Le lieu de la photo n'y est pas non plus.
// - la feuille est masquée dans la vignette du sélecteur d'applications comme le reste du Feed (`hidesInAppSwitcher`).
// CHANGEMENT DE POSTURE (à aligner dans les textes légaux, voir `docs/release/update-photo-declarations.md`) : la photo peut désormais sortir de Miff
// si la personne le choisit (c'était déjà possible par capture d'écran) ; elle n'est « jamais enregistrée sur le téléphone » QUE tant qu'on ne le demande pas.

/// La feuille de partage du système, avec UNE image. Dit, à sa fermeture, si la personne a choisi « Enregistrer l'image ».
struct PhotoShareSheet: UIViewControllerRepresentable {
    let image: UIImage
    let onFinish: (_ saved: Bool) -> Void

    /// Vrai seulement quand une action s'est terminée ET que c'était « Enregistrer l'image » : seul cas où l'on confirme (jamais pour une annulation).
    static func isSaved(completed: Bool, activityType: UIActivity.ActivityType?) -> Bool {
        completed && activityType == .saveToCameraRoll
    }

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [image], applicationActivities: nil)
        let finish = onFinish
        controller.completionWithItemsHandler = { activityType, completed, _, _ in
            finish(PhotoShareSheet.isSaved(completed: completed, activityType: activityType))
        }
        return controller
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Le bouton : UNE icône « télécharger », sans texte, en haut à droite du plein écran, en face de la croix (disque sombre translucide de 40 pt dans une
/// zone de 48 pt, comme la croix ; « Réduire la transparence » le rend opaque). Nom parlé « Enregistrer cette photo », et sa confirmation calme
/// « Photo enregistrée. » (3 s, sous l'icône, sans décaler la mise en page) est dite UNE fois à VoiceOver (une annonce, pas de valeur en plus).
struct PhotoSaveButton: View {
    let image: UIImage
    @State private var sharing = false
    @State private var saved = false
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.dynamicTypeSize) private var textSize

    private static let confirmationSeconds: UInt64 = 3
    /// En grand texte, la confirmation passe sur plusieurs lignes dans cette largeur : elle ne sort jamais de l'écran.
    private static let largeTextWidth: CGFloat = 220

    var body: some View {
        RoundOverlayButton(systemName: "square.and.arrow.down", labelKey: "feed_photo_download") {
            sharing = true
        }
        .overlay(alignment: .topTrailing) {
            if saved {
                confirmation
            }
        }
        .sheet(isPresented: $sharing) {
            PhotoShareSheet(image: image) { savedNow in
                sharing = false
                saved = savedNow
            }
            .hidesInAppSwitcher()
            .ignoresSafeArea()
            .presentationDetents([.medium, .large])
        }
        .task(id: saved) {
            guard saved else {
                return
            }
            // Calme : dit une fois au lecteur d'écran, puis disparaît seule.
            UIAccessibility.post(notification: .announcement, argument: L10n.string("feed_photo_download_done"))
            try? await Task.sleep(nanoseconds: Self.confirmationSeconds * 1_000_000_000)
            saved = false
        }
    }

    /// Posée sur la photo, sans rien décaler : le pied et la photo ne bougent pas. Texte normal : sa largeur naturelle ; grand texte : plusieurs lignes.
    private var confirmation: some View {
        let text = Text("feed_photo_download_done")
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorUpdateOnPhoto.color)
            .multilineTextAlignment(.trailing)
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, Spacing.xs)
            .background(
                RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                    .fill(updateScrimToken(reduceTransparency: reduceTransparency).color)
            )
        return Group {
            if textSize.isAccessibilitySize {
                text
                    .frame(width: Self.largeTextWidth, alignment: .trailing)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                text.fixedSize()
            }
        }
        .offset(y: UpdateLayout.roundButtonSize)
        .accessibilityHidden(true)
    }
}
