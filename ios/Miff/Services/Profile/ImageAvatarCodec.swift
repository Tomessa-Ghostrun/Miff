import CoreGraphics
import Foundation
import ImageIO

/// Une photo décodée en mémoire et déjà redressée (l'orientation est appliquée). Vit dans le view model, jamais sur le
/// disque.
final class DecodedCGImage: DecodedPhoto {
    let image: CGImage

    init(image: CGImage) {
        self.image = image
    }

    var width: Int {
        image.width
    }

    var height: Int {
        image.height
    }
}

/// Photo de profil (ticket Onboarding §6.3) : choisie dans le sélecteur de photos du système (`PhotosPicker`, aucune
/// permission : l'app ne reçoit que la photo choisie), recadrée en carré par la personne, puis RÉ-ENCODÉE depuis les
/// pixels.
///
/// La vie privée est le but de cette classe (risque R6, critère PR4) :
/// - le JPEG enregistré est écrit par `CGImageDestination` à partir d'une image redessinée : aucun bloc EXIF, donc ni
///   position, ni date, ni appareil ;
/// - l'orientation est lue dans la source UNIQUEMENT pour redresser les pixels, puis abandonnée avec tout le reste ;
/// - rien n'est écrit dans la photothèque ni ailleurs.
///
/// Robustesse : la photo est décodée avec une taille maximale (vignette), donc une photo énorme ou un fichier qui n'est
/// pas une image ne peut ni épuiser la mémoire ni faire planter l'app : cela donne simplement nil (« cette image ne peut
/// pas être utilisée »).
final class ImageAvatarCodec: AvatarCodec {
    /// Au-delà, la photo est refusée sans être décodée.
    static let maxSourcePixels = 100_000_000
    /// Côté de travail du recadrage : assez grand pour garder les détails d'un carré zoomé quatre fois.
    static let workingSide = 1280

    func decode(_ source: Data) async -> (any DecodedPhoto)? {
        await Task.detached(priority: .userInitiated) { () -> (any DecodedPhoto)? in
            ImageAvatarCodec.decodeSynchronously(source)
        }.value
    }

    func encode(_ photo: any DecodedPhoto, crop: SquareCrop) async -> Data? {
        guard let decoded = photo as? DecodedCGImage else {
            return nil
        }
        let image = decoded.image
        return await Task.detached(priority: .userInitiated) { () -> Data? in
            ImageAvatarCodec.encodeSynchronously(image, crop: crop)
        }.value
    }

    static func decodeSynchronously(_ data: Data) -> (any DecodedPhoto)? {
        let sourceOptions: [CFString: Any] = [kCGImageSourceShouldCache: false]
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions as CFDictionary),
              CGImageSourceGetCount(source) > 0,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int,
              width > 0, height > 0, width * height <= maxSourcePixels else {
            return nil
        }
        // Jamais plus grand que la photo elle-même (la vignette ne doit pas agrandir une petite image).
        let longSide = min(max(width, height), workingSide)
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            // Redresse les pixels selon l'orientation de la source (puis l'orientation est abandonnée).
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: longSide
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        return DecodedCGImage(image: image)
    }

    static func encodeSynchronously(_ image: CGImage, crop: SquareCrop) -> Data? {
        let left = min(max(crop.left, 0), image.width - 1)
        let top = min(max(crop.top, 0), image.height - 1)
        let size = min(max(crop.size, 1), min(image.width - left, image.height - top))
        guard let square = image.cropping(to: CGRect(x: left, y: top, width: size, height: size)) else {
            return nil
        }
        return AvatarCompression.fit { side, quality in
            jpeg(from: square, side: side, quality: quality)
        }
    }

    /// Redessine le carré à `side` x `side` sur fond blanc (un JPEG n'a pas de transparence) et l'encode : le résultat ne
    /// contient aucune métadonnée.
    private static func jpeg(from image: CGImage, side: Int, quality: Int) -> Data? {
        guard let context = CGContext(
            data: nil,
            width: side,
            height: side,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ) else {
            return nil
        }
        let frame = CGRect(x: 0, y: 0, width: side, height: side)
        context.interpolationQuality = .high
        context.setFillColor(red: 1, green: 1, blue: 1, alpha: 1)
        context.fill(frame)
        context.draw(image, in: frame)
        guard let scaled = context.makeImage() else {
            return nil
        }
        let output = NSMutableData()
        let jpegType = "public.jpeg" as CFString
        guard let destination = CGImageDestinationCreateWithData(output as CFMutableData, jpegType, 1, nil) else {
            return nil
        }
        let properties: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: Double(quality) / 100]
        CGImageDestinationAddImage(destination, scaled, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            return nil
        }
        return output as Data
    }
}
