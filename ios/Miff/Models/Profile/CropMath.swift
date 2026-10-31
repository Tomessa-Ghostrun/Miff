import Foundation

/// Une zone carrée de la photo source, en pixels de la photo (déjà redressée) : la partie qui devient l'avatar.
struct SquareCrop: Equatable {
    let left: Int
    let top: Int
    let size: Int
}

/// Géométrie du recadrage carré (ticket Onboarding §6.3 : « recadrage carré obligatoire avant validation »). La
/// personne déplace et agrandit la photo sous un carré fixe ; l'état est un zoom et le CENTRE du carré en pixels de la
/// photo, pour qu'une rotation de l'écran ou un autre écran ne change rien.
///
/// Zoom 1 = le plus grand carré qui tient dans la photo (le petit côté) ; zoom 4 = un carré quatre fois plus petit.
enum CropMath {
    static let minZoom = 1.0
    static let maxZoom = 4.0

    static func clampZoom(_ zoom: Double) -> Double {
        zoom.isNaN ? minZoom : min(max(zoom, minZoom), maxZoom)
    }

    /// Côté du carré, en pixels de la photo, à ce zoom.
    static func sideAtZoom(width: Int, height: Int, zoom: Double) -> Double {
        Double(min(width, height)) / clampZoom(zoom)
    }

    /// Le centre est gardé dans la photo pour que le carré ne la quitte jamais.
    static func clampCenter(
        width: Int,
        height: Int,
        zoom: Double,
        center: (x: Double, y: Double)
    ) -> (x: Double, y: Double) {
        let half = sideAtZoom(width: width, height: height, zoom: zoom) / 2
        let posX = center.x.isNaN ? Double(width) / 2 : min(max(center.x, half), max(half, Double(width) - half))
        let posY = center.y.isNaN ? Double(height) / 2 : min(max(center.y, half), max(half, Double(height) - half))
        return (posX, posY)
    }

    /// La zone à découper dans la photo : toujours dans la photo, toujours un carré d'au moins 1 pixel.
    static func toCrop(
        width: Int,
        height: Int,
        zoom: Double,
        center: (x: Double, y: Double)
    ) -> SquareCrop {
        guard width > 0, height > 0 else {
            return SquareCrop(left: 0, top: 0, size: 1)
        }
        let clamped = clampCenter(width: width, height: height, zoom: zoom, center: center)
        let rawSide = Int(sideAtZoom(width: width, height: height, zoom: zoom).rounded())
        let side = min(max(rawSide, 1), min(width, height))
        let left = min(max(Int((clamped.x - Double(side) / 2).rounded()), 0), width - side)
        let top = min(max(Int((clamped.y - Double(side) / 2).rounded()), 0), height - side)
        return SquareCrop(left: left, top: top, size: side)
    }
}

/// Trouver un avatar qui tient dans `maxBytes` (data-model.md §2.1 : `avatar` fait 20 000 octets au plus ; ticket
/// Onboarding §6.3) : un JPEG de 160 x 160 dont la qualité baisse jusqu'à ce que ça tienne ; si jamais ça ne tient pas,
/// une image plus petite. L'encodeur est fourni de l'extérieur (celui d'iOS dans l'app, un faux dans les tests).
enum AvatarCompression {
    static let maxBytes = ProfileDocument.maxAvatarBytes

    /// Côté de l'image enregistrée, en pixels. Le premier est la cible du ticket.
    static let sides = [160, 128, 96]
    static let qualities = [80, 70, 60, 50, 40, 30]

    /// Premier résultat de `encode(côté, qualité)` qui tient ; nil si même le plus petit ne tient pas ou si l'encodeur
    /// échoue.
    static func fit(encode: (_ side: Int, _ quality: Int) -> Data?) -> Data? {
        for side in sides {
            for quality in qualities {
                guard let bytes = encode(side, quality) else {
                    return nil
                }
                if bytes.count <= maxBytes {
                    return bytes
                }
            }
        }
        return nil
    }
}

/// Une photo décodée et redressée, prête à être recadrée. Gardée par le view model : une rotation de l'écran la garde.
protocol DecodedPhoto {
    var width: Int { get }
    var height: Int { get }
}

/// Transforme une photo choisie par la personne en avatar enregistré. `decode` l'ouvre (nil : ce n'est pas une image,
/// illisible ou bien trop grande) ; `encode` découpe le carré et produit le JPEG de 20 000 octets au plus SANS AUCUNE
/// MÉTADONNÉE (ni position, ni date, ni appareil : l'image est ré-encodée depuis les pixels), ou nil si elle ne peut pas
/// tenir. Rien n'est écrit dans la photothèque ni ailleurs.
protocol AvatarCodec {
    func decode(_ source: Data) async -> (any DecodedPhoto)?
    func encode(_ photo: any DecodedPhoto, crop: SquareCrop) async -> Data?
}
