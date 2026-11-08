import Foundation
import ImageIO
import UIKit

// SÉCURITÉ, pour ce fichier (FEED-8) : les octets d'une photo ont été écrits par QUELQU'UN D'AUTRE. Avant tout décodage :
// - la SIGNATURE d'un JPEG (`FF D8 FF`) ET le type que reconnaît le système (`public.jpeg`) sont exigés : un PNG, un GIF, un HEIC, un TIFF ou des octets
//   forgés sont « illisibles » (rien n'est dessiné), jamais décodés ;
// - la TAILLE EN PIXELS est lue dans l'en-tête (sans rien décoder) : ce qui est démesuré (une « bombe de décompression » tiendrait dans 150 Ko mais
//   prendrait des centaines de Mo en mémoire) n'est jamais décodé.
// L'aperçu de la carte est décodé EN RÉDUIT (1 024 px au plus sur le grand côté) ; la photo entière n'est décodée qu'à l'ouverture du plein écran.
// Aucune écriture : l'image reste en mémoire.

protocol PhotoDecoding {
    /// L'image entière (plein écran), ou nil quand ce n'est pas un JPEG plausible.
    func image(from jpeg: Data) -> UIImage?
    /// L'aperçu de la carte, réduit à 1 024 px au plus, ou nil quand ce n'est pas un JPEG plausible.
    func preview(from jpeg: Data) -> UIImage?
}

struct ImagePhotoDecoder: PhotoDecoding {
    /// Une photo d'update fait 1 024 px au plus sur son grand côté (~2 millions de pixels) : au-delà de 4 millions, ce n'est pas la nôtre.
    static let maxPixels = 4_000_000
    /// Le grand côté de l'aperçu d'une carte.
    static let previewMaxPixelSize = 1_024
    private static let jpegSignature: [UInt8] = [0xFF, 0xD8, 0xFF]
    private static let jpegTypeIdentifier = "public.jpeg"

    /// La taille est plausible : deux côtés positifs et pas plus de `maxPixels` pixels.
    static func isAcceptable(width: Int, height: Int) -> Bool {
        width > 0 && height > 0 && width <= maxPixels && height <= maxPixels && width * height <= maxPixels
    }

    /// Les trois premiers octets d'un JPEG.
    static func hasJpegSignature(_ data: Data) -> Bool {
        Array(data.prefix(jpegSignature.count)) == jpegSignature
    }

    func image(from jpeg: Data) -> UIImage? {
        guard verifiedSource(jpeg) != nil else {
            return nil
        }
        return UIImage(data: jpeg)
    }

    func preview(from jpeg: Data) -> UIImage? {
        guard let source = verifiedSource(jpeg) else {
            return nil
        }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: Self.previewMaxPixelSize
        ]
        guard let reduced = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        return UIImage(cgImage: reduced)
    }

    /// La source d'image, seulement si les octets sont un JPEG (signature ET type du système) de taille plausible.
    private func verifiedSource(_ jpeg: Data) -> CGImageSource? {
        guard Self.hasJpegSignature(jpeg),
              let source = CGImageSourceCreateWithData(jpeg as CFData, nil),
              (CGImageSourceGetType(source) as String?) == Self.jpegTypeIdentifier,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.intValue,
              let height = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.intValue,
              Self.isAcceptable(width: width, height: height) else {
            return nil
        }
        return source
    }
}
