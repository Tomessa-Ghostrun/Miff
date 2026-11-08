import Foundation

// Le FORMAT de la photo d'une update (design/screens/update.md §6.1, décisions du PM du 2026-11-06 « viseur plein écran, aperçu un peu
// plus petit » ; ticket docs/tickets/update.md, D24). Du Swift pur : rien n'est lu sur le téléphone, pour que les mêmes nombres soient le
// contrat commun d'iOS et d'Android (les cas de `UpdatePhotoFormatTests` sont ceux d'`UpdatePhotoFormatTest.kt`).
//
// - La ZONE ENREGISTRÉE est exactement la zone VUE dans le viseur (« ce que tu vois est ce qui part ») : un recadrage « cover », centré,
//   de l'image du capteur.
// - Le RATIO est celui du viseur, BORNÉ entre 9:21 et 9:16 (un iPhone 11 Pro et un OPPO Find X5 Lite sont dans les bornes).
// - Le CÔTÉ LONG fait 1 024 px au plus ; le poids 150 000 octets au plus (plafond dur des règles `validPhoto`, mesuré sur les octets réels).

/// Un rectangle de pixels dans l'image source (déjà redressée), origine en haut à gauche.
struct PixelRect: Equatable {
    let left: Int
    let top: Int
    let width: Int
    let height: Int
}

struct PixelSize: Equatable {
    let width: Int
    let height: Int
}

enum UpdatePhotoFormat {
    /// Le plafond dur de `validPhoto` (backend/firestore.rules) : un JPEG plus gros est refusé avec tout le lot.
    static let maxBytes = 150_000

    /// La cible d'une photo type (ticket V7) ; l'échelle ci-dessous s'arrête au premier résultat qui tient dans `maxBytes`.
    static let targetBytes = 100_000

    /// Les côtés longs essayés, dans l'ordre (le premier est le maximum du format).
    static let longSides = [1024, 896, 768]

    /// Les qualités JPEG essayées pour chaque côté, de la meilleure à la plus basse (en pourcents).
    static let qualities = [80, 70, 60, 55]

    /// Largeur / hauteur du ratio le plus étroit gardé (9:21) et du plus large (9:16).
    static let minAspect = 9.0 / 21.0
    static let maxAspect = 9.0 / 16.0

    /// Le ratio (largeur / hauteur) d'une zone de `widthPx` x `heightPx`, borné entre 9:21 et 9:16. Une zone sans taille donne le plus large.
    static func boundedAspect(widthPx: Int, heightPx: Int) -> Double {
        guard widthPx > 0, heightPx > 0 else {
            return maxAspect
        }
        return min(max(Double(widthPx) / Double(heightPx), minAspect), maxAspect)
    }

    /// Le recadrage « cover » : le plus grand rectangle du ratio `aspect` (largeur / hauteur) qui tient dans la source, centré. Une
    /// source plus large que le ratio perd de la largeur, une plus haute perd de la hauteur. Toujours dans la source, d'au moins 1 pixel.
    /// nil pour une image sans taille.
    static func coverCrop(sourceWidth: Int, sourceHeight: Int, aspect: Double) -> PixelRect? {
        guard sourceWidth > 0, sourceHeight > 0 else {
            return nil
        }
        let safeAspect = aspect.isFinite && aspect > 0 ? aspect : maxAspect
        let sourceAspect = Double(sourceWidth) / Double(sourceHeight)
        let width: Int
        let height: Int
        if sourceAspect > safeAspect {
            width = clamp(Int((Double(sourceHeight) * safeAspect).rounded()), 1, sourceWidth)
            height = sourceHeight
        } else {
            width = sourceWidth
            height = clamp(Int((Double(sourceWidth) / safeAspect).rounded()), 1, sourceHeight)
        }
        let left = clamp((sourceWidth - width) / 2, 0, sourceWidth - width)
        let top = clamp((sourceHeight - height) / 2, 0, sourceHeight - height)
        return PixelRect(left: left, top: top, width: width, height: height)
    }

    /// La taille de l'image enregistrée : le recadrage réduit pour que son côté long soit `longSide` au plus (jamais agrandi), ratio gardé.
    static func outputSize(cropWidth: Int, cropHeight: Int, longSide: Int) -> PixelSize {
        let long = max(cropWidth, cropHeight)
        guard long > longSide, long > 0 else {
            return PixelSize(width: cropWidth, height: cropHeight)
        }
        let scale = Double(longSide) / Double(long)
        return PixelSize(
            width: max(1, Int((Double(cropWidth) * scale).rounded())),
            height: max(1, Int((Double(cropHeight) * scale).rounded()))
        )
    }

    /// Le premier résultat de l'échelle (côté, qualité) qui pèse `maxBytes` au plus, mesuré sur les octets réels ; nil quand `encode`
    /// échoue ou quand même le plus petit ne tient pas (l'écran dit alors « On n'a pas pu préparer cette photo », rien n'est écrit).
    static func fit(_ encode: (_ longSide: Int, _ quality: Int) -> Data?) -> Data? {
        for side in longSides {
            for quality in qualities {
                guard let bytes = encode(side, quality) else {
                    return nil
                }
                if !bytes.isEmpty && bytes.count <= maxBytes {
                    return bytes
                }
            }
        }
        return nil
    }

    /// La taille d'une image de `width` x `height` montrée aussi grande que possible dans un cadre, ratio gardé.
    static func fitInside(width: Int, height: Int, frameWidth: Int, frameHeight: Int) -> PixelSize {
        guard width > 0, height > 0 else {
            return PixelSize(width: 0, height: 0)
        }
        let scale = min(Double(frameWidth) / Double(width), Double(frameHeight) / Double(height))
        return PixelSize(width: Int((Double(width) * scale).rounded()), height: Int((Double(height) * scale).rounded()))
    }

    private static func clamp(_ value: Int, _ low: Int, _ high: Int) -> Int {
        min(max(value, low), high)
    }
}

/// Décision du PM du 2026-11-06 (D37) : la photo de la caméra FRONTALE est publiée « en reflet », comme le viseur (l'image miroir de la
/// scène) ; la caméra ARRIÈRE n'est jamais retournée. L'aperçu de validation, la photo publiée et celle que voient les proches sont donc
/// identiques au viseur.
enum PhotoMirror {
    static func shouldMirror(frontCamera: Bool) -> Bool {
        frontCamera
    }
}

/// Une photo prête à publier : les octets du JPEG (sans métadonnée), sa taille et le moment où elle a été prise. Elle vit dans la mémoire de
/// l'écran, JAMAIS dans un fichier de l'app, et ne montre rien d'elle dans `description`. Passé `staleMillis`, elle est oubliée (ticket V6).
struct UpdatePhoto: Equatable, CustomStringConvertible {
    let jpeg: Data
    let width: Int
    let height: Int
    let takenAtMillis: Int

    /// Une photo qui attend « Publier » depuis plus de 5 minutes date : « Cette photo date un peu : reprends-en une. »
    static let staleMillis = 5 * 60 * 1000

    func isStale(nowMillis: Int) -> Bool {
        nowMillis - takenAtMillis > Self.staleMillis
    }

    var description: String {
        "UpdatePhoto(redacted)"
    }
}
