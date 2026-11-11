import UIKit

// CONFIDENTIALITÉ, pour ce fichier (FEED-8) : ce qui est enregistré dans la galerie est un JPEG ré-encodé À PARTIR DES PIXELS : l'image est redessinée
// dans un bitmap neuf, puis encodée. Aucune métadonnée de la source ne peut passer (ni EXIF, ni GPS, ni TIFF, ni orientation, ni date de prise de
// vue : les octets reçus d'un autre téléphone sont inconnus de nous), et aucune coordonnée du lieu de la photo n'y est ajoutée. Tout reste en
// mémoire : aucune écriture de fichier.

enum PhotoExport {
    /// La qualité du JPEG enregistré : haute (la photo reçue est déjà un JPEG de 150 Ko au plus ; on ne veut pas la dégrader davantage).
    static let quality: CGFloat = 0.92

    static func cleanJpeg(from image: UIImage) -> Data? {
        guard image.size.width > 0, image.size.height > 0 else {
            return nil
        }
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        format.opaque = true
        let redrawn = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(at: .zero)
        }
        return redrawn.jpegData(compressionQuality: quality)
    }
}
