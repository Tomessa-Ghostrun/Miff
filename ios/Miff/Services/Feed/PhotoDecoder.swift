import Foundation
import ImageIO
import UIKit

// SÉCURITÉ, pour ce fichier (FEED-8) : les octets d'une photo ont été écrits par QUELQU'UN D'AUTRE. Avant de les décoder, la TAILLE EN PIXELS est lue
// dans l'en-tête (sans rien décoder) : ce qui n'est pas une image, ou qui est démesuré (une « bombe de décompression » tiendrait dans 150 Ko mais
// prendrait des centaines de Mo en mémoire), n'est jamais décodé. Aucune écriture : l'image reste en mémoire.

protocol PhotoDecoding {
    func image(from jpeg: Data) -> UIImage?
}

struct ImagePhotoDecoder: PhotoDecoding {
    /// Une photo d'update fait 1 024 px au plus sur son grand côté (~2 millions de pixels) : au-delà de 4 millions, ce n'est pas la nôtre.
    static let maxPixels = 4_000_000

    /// La taille est plausible : deux côtés positifs et pas plus de `maxPixels` pixels.
    static func isAcceptable(width: Int, height: Int) -> Bool {
        width > 0 && height > 0 && width <= maxPixels && height <= maxPixels && width * height <= maxPixels
    }

    func image(from jpeg: Data) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(jpeg as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.intValue,
              let height = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.intValue,
              Self.isAcceptable(width: width, height: height) else {
            return nil
        }
        return UIImage(data: jpeg)
    }
}
