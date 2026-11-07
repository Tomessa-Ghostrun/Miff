import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// CONFIDENTIALITÉ, pour ce fichier (risque R6, critère V3) : le JPEG qui quitte le téléphone est écrit DEPUIS LES PIXELS : il ne porte aucun
// bloc EXIF, donc ni position, ni date, ni modèle de téléphone, ni miniature ; `JpegMetadata.stripped` retire en plus tout segment de
// métadonnées qui s'y serait glissé. L'orientation du capteur est appliquée aux pixels (à la lecture) puis oubliée. La photo de la caméra
// est lue EN MÉMOIRE (`AVCapturePhoto.fileDataRepresentation`) : AUCUN fichier de la photo n'est créé par l'app et rien n'est écrit dans la
// photothèque. Limite : une fois publiée, la photo passe par le SDK Firestore, dont le cache local peut en garder une copie (chiffrée par la
// protection des données d'iOS, purgée à la déconnexion et à la suppression du compte). Rien ici ne journalise quoi que ce soit.

/// La fabrication de la photo : lecture (orientation appliquée), recadrage « cover », réduction, reflet de la caméra frontale (D37),
/// JPEG sous 150 000 octets. Les cas sont testés sur des images fabriquées (`PhotoEncoderTests`).
enum PhotoEncoder {
    /// Le côté long de l'image gardée en mémoire après la lecture : assez pour les 1 024 px de la photo, léger pour la mémoire.
    static let decodeLongSide = 2048

    /// L'image des octets donnés, REDRESSÉE (l'orientation du capteur est appliquée aux pixels) et réduite à `maxPixel` au plus.
    static func decodeUpright(_ data: Data, maxPixel: Int = decodeLongSide) -> CGImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else {
            return nil
        }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }

    /// Une photo prête à publier à partir de `source` (déjà redressée), coupée au ratio `aspect` (« cover », centré), retournée en reflet quand
    /// `mirror` (caméra frontale seulement, D37) et réduite jusqu'à peser 150 000 octets au plus ; nil quand elle ne peut pas être préparée
    /// (mémoire, un encodeur qui échoue, ou rien ne tient jamais).
    static func encode(_ source: CGImage, aspect: Double, mirror: Bool, nowMillis: Int) -> UpdatePhoto? {
        let width = source.width
        let height = source.height
        guard let crop = UpdatePhotoFormat.coverCrop(sourceWidth: width, sourceHeight: height, aspect: aspect),
              let cropped = source.cropping(
                  to: CGRect(x: crop.left, y: crop.top, width: crop.width, height: crop.height)
              ) else {
            return nil
        }
        var size = PixelSize(width: crop.width, height: crop.height)
        var rendered: [Int: CGImage] = [:]
        let jpeg = UpdatePhotoFormat.fit { side, quality in
            size = UpdatePhotoFormat.outputSize(cropWidth: crop.width, cropHeight: crop.height, longSide: side)
            if rendered[side] == nil {
                rendered[side] = render(cropped, size: size, mirror: mirror)
            }
            guard let image = rendered[side], let data = jpegData(image, quality: quality) else {
                return nil
            }
            return JpegMetadata.stripped(data)
        }
        return jpeg.map { UpdatePhoto(jpeg: $0, width: size.width, height: size.height, takenAtMillis: nowMillis) }
    }

    /// L'image redessinée à `size`, retournée en reflet (axe vertical) quand `mirror`. sRGB, sans transparence.
    static func render(_ image: CGImage, size: PixelSize, mirror: Bool) -> CGImage? {
        guard size.width > 0, size.height > 0, let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(
                  data: nil,
                  width: size.width,
                  height: size.height,
                  bitsPerComponent: 8,
                  bytesPerRow: 0,
                  space: space,
                  bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
              ) else {
            return nil
        }
        context.interpolationQuality = .high
        if mirror {
            context.translateBy(x: CGFloat(size.width), y: 0)
            context.scaleBy(x: -1, y: 1)
        }
        context.draw(image, in: CGRect(x: 0, y: 0, width: size.width, height: size.height))
        return context.makeImage()
    }

    /// Un JPEG nu de `image` : la qualité est la SEULE option (aucune métadonnée n'est donnée à l'encodeur).
    static func jpegData(_ image: CGImage, quality: Int) -> Data? {
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            output as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil
        ) else {
            return nil
        }
        let options: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: Double(quality) / 100]
        CGImageDestinationAddImage(destination, image, options as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            return nil
        }
        return output as Data
    }
}
