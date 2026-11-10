import SwiftUI
import UIKit

// « Enregistrer la photo », dans le plein écran de la photo d'un proche, À CET ENDROIT SEULEMENT (décision du PM : pas sur l'aperçu de la carte, pas dans
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

/// Le bouton (contour blanc, au moins 46 pt, nom parlé « Enregistrer la photo ») et la confirmation calme « Photo enregistrée. » (3 s).
struct PhotoSaveButton: View {
    let image: UIImage
    @State private var sharing = false
    @State private var saved = false

    private static let minimumHeight = Spacing.minTouchTarget + Spacing.xs
    private static let confirmationSeconds: UInt64 = 3

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Button {
                sharing = true
            } label: {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "square.and.arrow.down")
                        .font(.system(size: 18))
                        .accessibilityHidden(true)
                    Text("feed_photo_download")
                        .miffTextStyle(textBodyBold14)
                }
                .foregroundColor(colorUpdateOnPhoto.color)
                .frame(maxWidth: .infinity, minHeight: Self.minimumHeight)
                .overlay(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .stroke(colorUpdateOnPhoto.color, lineWidth: strokeHairline * 2)
                )
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if saved {
                Text("feed_photo_download_done")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorUpdateOnPhoto.color)
                    .fixedSize(horizontal: false, vertical: true)
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
}
