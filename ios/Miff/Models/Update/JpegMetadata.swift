import Foundation

// RISQUE R6, critère V3 : le JPEG qui quitte le téléphone ne doit porter AUCUNE métadonnée (ni EXIF, ni GPS, ni date, ni modèle de
// téléphone, ni miniature, ni XMP). Il est ré-encodé DEPUIS LES PIXELS (`PhotoEncoder`), ce qui n'écrit déjà aucun bloc EXIF ; ce fichier
// est la CEINTURE ET LES BRETELLES : il lit la structure du fichier (les segments avant les données de l'image) et retire tout
// segment de métadonnées (APP1 à APP15 : EXIF, XMP, profils ; COM : commentaires), ne gardant que ce qu'il faut pour décoder (APP0 JFIF,
// tables, cadre, balayage). Du Swift pur, testé sur des octets.

enum JpegMetadata {
    private static let startOfImage: UInt8 = 0xD8
    private static let endOfImage: UInt8 = 0xD9
    private static let startOfScan: UInt8 = 0xDA
    private static let comment: UInt8 = 0xFE

    /// Un segment d'en-tête : son marqueur et ses octets (marqueur et longueur compris).
    struct Segment: Equatable {
        let marker: UInt8
        let range: Range<Int>
    }

    /// Les segments d'un JPEG jusqu'au début des données de l'image (le balayage, exclu), ou nil si ce n'est pas un JPEG bien formé.
    static func headerSegments(_ bytes: [UInt8]) -> [Segment]? {
        guard bytes.count > 4, bytes[0] == 0xFF, bytes[1] == startOfImage else {
            return nil
        }
        var segments: [Segment] = []
        var index = 2
        while index + 1 < bytes.count {
            guard bytes[index] == 0xFF else {
                return nil
            }
            let marker = bytes[index + 1]
            if marker == 0xFF {
                // Octet de remplissage.
                index += 1
                continue
            }
            if marker == startOfScan || marker == endOfImage {
                return segments
            }
            guard index + 3 < bytes.count else {
                return nil
            }
            let length = Int(bytes[index + 2]) << 8 | Int(bytes[index + 3])
            guard length >= 2, index + 2 + length <= bytes.count else {
                return nil
            }
            segments.append(Segment(marker: marker, range: index..<(index + 2 + length)))
            index += 2 + length
        }
        return nil
    }

    /// Ce marqueur porte-t-il des métadonnées ? APP1 (EXIF, XMP) à APP15, et les commentaires.
    static func isMetadata(_ marker: UInt8) -> Bool {
        (0xE1...0xEF).contains(marker) || marker == comment
    }

    /// Le JPEG contient-il un segment de métadonnées ? Un fichier qui n'est pas un JPEG bien formé est dit « avec » (par prudence).
    static func containsMetadata(_ data: Data) -> Bool {
        guard let segments = headerSegments([UInt8](data)) else {
            return true
        }
        return segments.contains { isMetadata($0.marker) }
    }

    /// Le même JPEG sans aucun segment de métadonnées ; nil si ce n'est pas un JPEG bien formé.
    static func stripped(_ data: Data) -> Data? {
        let bytes = [UInt8](data)
        guard let segments = headerSegments(bytes) else {
            return nil
        }
        let lastHeaderEnd = segments.last?.range.upperBound ?? 2
        var output: [UInt8] = [0xFF, startOfImage]
        for segment in segments where !isMetadata(segment.marker) {
            output.append(contentsOf: bytes[segment.range])
        }
        output.append(contentsOf: bytes[lastHeaderEnd...])
        return Data(output)
    }
}
