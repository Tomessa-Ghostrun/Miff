import ImageIO
import UIKit

// SÉCURITÉ et MÉMOIRE, pour ce fichier : une photo de profil vient d'un AUTRE téléphone. Les règles bornent son poids (20 000 octets) mais pas
// ses dimensions : un petit fichier JPEG peut décrire une image immense, et la décoder en pleine taille coûterait des centaines de mégaoctets
// (une photo piégée ferait planter l'app). On lit donc les dimensions SANS décoder, on refuse l'implausible (côté > 2048 px), puis on ne
// décode que la version réduite à la taille du rond. Une photo refusée : les initiales, comme pour une photo absente.
// Les images réduites sont gardées en mémoire (jamais sur le disque) pour ne pas être décodées à chaque redessin ; ce cache s'efface avec
// la session (`removeAll`) et à chaque alerte mémoire (NSCache).

enum AvatarDecoding {
    /// Côté maximal plausible d'une photo de profil, en pixels (les photos de l'app font 512 px ou moins).
    static let maxSide = 2048

    private static let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        // De quoi garder les ronds visibles à l'écran, pas toute une bibliothèque de photos.
        cache.countLimit = 60
        return cache
    }()

    /// La photo réduite à `pixelSize` pixels de côté (au plus), ou nil si elle est absente, trop lourde, illisible ou implausible.
    static func image(from jpeg: Data?, pixelSize: CGFloat) -> UIImage? {
        guard let jpeg, !jpeg.isEmpty, jpeg.count <= ProfileDocument.maxAvatarBytes else {
            return nil
        }
        let pixels = max(Int(pixelSize.rounded(.up)), 1)
        let key = cacheKey(jpeg, pixelSize: pixelSize)
        if let cached = cache.object(forKey: key) {
            return cached
        }
        guard let image = decode(jpeg, maxPixels: pixels) else {
            return nil
        }
        cache.setObject(image, forKey: key)
        return image
    }

    /// La photo réduite SI elle est déjà en mémoire, sans rien décoder ni attendre (nil sinon). Un rond qui réapparaît (une carte qu'on
    /// revoit, une rangée reconstruite) montre ainsi sa photo dès la première image, sans repasser par les initiales (clignotement).
    static func cachedImage(from jpeg: Data?, pixelSize: CGFloat) -> UIImage? {
        guard let jpeg, !jpeg.isEmpty, jpeg.count <= ProfileDocument.maxAvatarBytes else {
            return nil
        }
        return cache.object(forKey: cacheKey(jpeg, pixelSize: pixelSize))
    }

    private static func cacheKey(_ jpeg: Data, pixelSize: CGFloat) -> NSString {
        let pixels = max(Int(pixelSize.rounded(.up)), 1)
        return "\(jpeg.count)-\(jpeg.hashValue)-\(pixels)" as NSString
    }

    /// Comme `image(from:pixelSize:)`, mais HORS du fil principal (lecture de l'en-tête, décodage de la version réduite, clé du cache) : un
    /// rond qui apparaît ne fait jamais saccader l'écran.
    static func loadImage(from jpeg: Data?, pixelSize: CGFloat) async -> UIImage? {
        guard let jpeg else {
            return nil
        }
        return await Task.detached(priority: .userInitiated) {
            image(from: jpeg, pixelSize: pixelSize)
        }.value
    }

    /// Oublie toutes les photos gardées : fin de session.
    static func removeAll() {
        cache.removeAllObjects()
    }

    /// Les dimensions sont lues dans l'en-tête du fichier (aucun décodage) ; seule la version réduite est décodée.
    static func decode(_ jpeg: Data, maxPixels: Int) -> UIImage? {
        let options = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(jpeg as CFData, options),
              CGImageSourceGetCount(source) == 1,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int,
              width > 0, height > 0, width <= maxSide, height <= maxSide else {
            return nil
        }
        let thumbnail: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixels
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbnail as CFDictionary) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }
}
