import ImageIO
import UIKit
import XCTest
@testable import Miff

/// La photo de profil est ré-encodée DEPUIS LES PIXELS : aucune métadonnée (position, date, appareil), 20 000 octets au plus
/// (critères PR3 et PR4). Un fichier qui n'est pas une image ne plante pas l'app.
final class ImageAvatarCodecTests: XCTestCase {
    private let codec = ImageAvatarCodec()

    /// Un JPEG de démonstration (dégradé et formes) qui PORTE une position GPS, un appareil et une orientation.
    private func sourcePhoto(width: Int = 800, height: Int = 600, orientation: Int = 1) throws -> Data {
        // Un pixel par point : sans cela le rendu serait multiplie par l'echelle de l'ecran du simulateur.
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        let image = renderer.image { context in
            for index in 0..<height {
                UIColor(hue: CGFloat(index) / CGFloat(height), saturation: 0.8, brightness: 0.9, alpha: 1).setFill()
                context.fill(CGRect(x: 0, y: index, width: width, height: 1))
            }
            UIColor.black.setFill()
            context.fill(CGRect(x: 40, y: 40, width: 200, height: 120))
        }
        let cgImage = try XCTUnwrap(image.cgImage)
        let output = NSMutableData()
        let jpegType = "public.jpeg" as CFString
        let destination = try XCTUnwrap(CGImageDestinationCreateWithData(output as CFMutableData, jpegType, 1, nil))
        let properties: [CFString: Any] = [
            kCGImagePropertyOrientation: orientation,
            kCGImagePropertyGPSDictionary: [
                kCGImagePropertyGPSLatitude: 48.8566,
                kCGImagePropertyGPSLatitudeRef: "N",
                kCGImagePropertyGPSLongitude: 2.3522,
                kCGImagePropertyGPSLongitudeRef: "E"
            ],
            kCGImagePropertyTIFFDictionary: [
                kCGImagePropertyTIFFMake: "Appareil de test",
                kCGImagePropertyTIFFModel: "Modele secret"
            ]
        ]
        CGImageDestinationAddImage(destination, cgImage, properties as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        return output as Data
    }

    /// Décode une photo (échoue le test si le codec la refuse).
    private func decode(_ data: Data) async throws -> any DecodedPhoto {
        guard let photo = await codec.decode(data) else {
            XCTFail("la photo aurait dû être décodée")
            throw CocoaError(.fileReadCorruptFile)
        }
        return photo
    }

    /// Les metadonnees d'un fichier image, lues par le systeme.
    private func metadata(of data: Data) throws -> [CFString: Any] {
        let source = try XCTUnwrap(CGImageSourceCreateWithData(data as CFData, nil))
        return try XCTUnwrap(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
    }

    private func contains(_ data: Data, _ text: String) -> Bool {
        data.range(of: Data(text.utf8)) != nil
    }

    func testTheSourcePhotoReallyCarriesMetadata() throws {
        // Garde-fou du test : si la source n'avait pas de métadonnées, le test suivant ne prouverait rien.
        let source = try sourcePhoto()

        XCTAssertTrue(contains(source, "Exif"))
        XCTAssertTrue(contains(source, "Appareil de test"))
    }

    func testTheStoredAvatarHasNoMetadataAtAllAndFitsTheLimit() async throws {
        let photo = try await decode(try sourcePhoto())
        let crop = CropMath.toCrop(width: photo.width, height: photo.height, zoom: 1, center: (400, 300))

        let encoded = await codec.encode(photo, crop: crop)
        let avatar = try XCTUnwrap(encoded)

        XCTAssertLessThanOrEqual(avatar.count, AvatarCompression.maxBytes)
        XCTAssertFalse(contains(avatar, "Appareil de test"), "marque de l'appareil dans le fichier")
        XCTAssertFalse(contains(avatar, "Modele secret"), "modele de l'appareil dans le fichier")
        XCTAssertFalse(contains(avatar, "GPS"), "bloc de position dans le fichier")
        // Ni position, ni appareil, ni date, relus tels que le systeme les lirait.
        let properties = try metadata(of: avatar)
        XCTAssertNil(properties[kCGImagePropertyGPSDictionary], "position")
        let tiff = properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
        XCTAssertNil(tiff?[kCGImagePropertyTIFFMake], "marque de l'appareil")
        XCTAssertNil(tiff?[kCGImagePropertyTIFFModel], "modele de l'appareil")
        XCTAssertNil(tiff?[kCGImagePropertyTIFFDateTime], "date")
        let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any]
        XCTAssertNil(exif?[kCGImagePropertyExifDateTimeOriginal], "date de prise de vue")
        // C'est bien un JPEG carré de 160 pixels (ou moins si la limite l'a imposé).
        let decoded = try XCTUnwrap(UIImage(data: avatar))
        XCTAssertEqual(Int(decoded.size.width * decoded.scale), Int(decoded.size.height * decoded.scale))
        XCTAssertLessThanOrEqual(Int(decoded.size.width * decoded.scale), 160)
        XCTAssertEqual(Array(avatar.prefix(2)), [0xFF, 0xD8])
    }

    func testTheOrientationOfTheSourceStraightensThePixels() async throws {
        // 800 x 600 avec l'orientation « tourné de 90 degrés » : une fois redressée, la photo est en 600 x 800.
        let photo = try await decode(try sourcePhoto(width: 800, height: 600, orientation: 6))

        XCTAssertEqual(photo.width, 600)
        XCTAssertEqual(photo.height, 800)
    }

    func testAHugePhotoIsDownscaledForTheCropButASmallOneIsNotEnlarged() async throws {
        let big = try await decode(try sourcePhoto(width: 3_000, height: 2_000))
        let small = try await decode(try sourcePhoto(width: 400, height: 300))

        XCTAssertLessThanOrEqual(max(big.width, big.height), ImageAvatarCodec.workingSide)
        XCTAssertEqual(small.width, 400)
        XCTAssertEqual(small.height, 300)
    }

    func testSomethingThatIsNotAnImageGivesNothingInsteadOfCrashing() async {
        let decodedText = await codec.decode(Data("ceci n'est pas une image".utf8))
        let decodedEmpty = await codec.decode(Data())

        XCTAssertNil(decodedText)
        XCTAssertNil(decodedEmpty)
    }

    func testACropOutsideThePhotoIsKeptInside() async throws {
        let photo = try await decode(try sourcePhoto())

        let avatar = await codec.encode(photo, crop: SquareCrop(left: 10_000, top: -50, size: 10_000))

        XCTAssertNotNil(avatar)
    }
}
