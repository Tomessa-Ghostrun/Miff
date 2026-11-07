import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
import XCTest
@testable import Miff

/// La fabrication de la photo (critères V2 et V3, décision D37 du PM) sur des images fabriquées : un JPEG de 150 000 octets au plus, sans AUCUNE
/// métadonnée (ni EXIF, ni GPS, ni XMP), la caméra frontale en REFLET et l'arrière JAMAIS retournée, l'orientation du capteur appliquée aux
/// pixels. Ces essais tournent sans caméra : la CI les exécute sur le simulateur.
final class PhotoEncoderTests: XCTestCase {
    private struct RGB {
        let red: Int
        let green: Int
        let blue: Int
    }

    private func makeContext(width: Int, height: Int) throws -> CGContext {
        let space = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        return try XCTUnwrap(
            CGContext(
                data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
            )
        )
    }

    /// Une image dont la moitié gauche est ROUGE et la moitié droite BLEUE : le reflet se voit à la couleur de chaque côté.
    private func halvesImage(width: Int, height: Int) throws -> CGImage {
        let context = try makeContext(width: width, height: height)
        context.setFillColor(red: 1, green: 0, blue: 0, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: width / 2, height: height))
        context.setFillColor(red: 0, green: 0, blue: 1, alpha: 1)
        context.fill(CGRect(x: width / 2, y: 0, width: width - width / 2, height: height))
        return try XCTUnwrap(context.makeImage())
    }

    /// Une image lisse avec un peu de texture : comme une photo type (un bruit pur ne se compresserait jamais).
    private func texturedImage(width: Int, height: Int) throws -> CGImage {
        let context = try makeContext(width: width, height: height)
        for row in 0..<height {
            for column in stride(from: 0, to: width, by: 4) {
                let red = 0.5 + 0.4 * sin(Double(column) / 37 + Double(row) / 91)
                let green = 0.5 + 0.4 * cos(Double(column) / 53 - Double(row) / 29)
                let blue = Double(row) / Double(height)
                context.setFillColor(red: red, green: green, blue: blue, alpha: 1)
                context.fill(CGRect(x: column, y: row, width: 4, height: 1))
            }
        }
        return try XCTUnwrap(context.makeImage())
    }

    private func pixel(_ image: CGImage, x: Int, y: Int) throws -> RGB {
        var data = [UInt8](repeating: 0, count: 4)
        let space = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let drawn = data.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: space,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else {
                return false
            }
            context.draw(
                image, in: CGRect(x: -x, y: -(image.height - 1 - y), width: image.width, height: image.height)
            )
            return true
        }
        XCTAssertTrue(drawn)
        return RGB(red: Int(data[0]), green: Int(data[1]), blue: Int(data[2]))
    }

    private func jpegDestination(_ output: NSMutableData) throws -> CGImageDestination {
        let type = UTType.jpeg.identifier as CFString
        return try XCTUnwrap(CGImageDestinationCreateWithData(output as CFMutableData, type, 1, nil))
    }

    private func decoded(_ photo: UpdatePhoto) throws -> CGImage {
        try XCTUnwrap(PhotoEncoder.decodeUpright(photo.jpeg))
    }

    // MARK: - Le JPEG : poids, forme, aucune métadonnée (V3)

    func testThePhotoIsABareJpegUnder150000BytesAtMost1024PixelsLongWithTheRatioOfTheViewfinder() throws {
        let source = try texturedImage(width: 1500, height: 2000)
        let photo = try XCTUnwrap(PhotoEncoder.encode(source, aspect: 0.45, mirror: false, nowMillis: 5))

        XCTAssertLessThanOrEqual(photo.jpeg.count, UpdatePhotoFormat.maxBytes)
        XCTAssertGreaterThan(photo.jpeg.count, 0)
        XCTAssertEqual(Array(photo.jpeg.prefix(2)), [0xFF, 0xD8], "un JPEG")
        XCTAssertLessThanOrEqual(max(photo.width, photo.height), 1024)
        XCTAssertEqual(Double(photo.width) / Double(photo.height), 0.45, accuracy: 0.01)
        XCTAssertEqual(photo.takenAtMillis, 5)
        // Relue, la photo a le même ratio (ImageIO peut la rendre à une autre taille : seul le ratio compte ici).
        let image = try decoded(photo)
        let readRatio = Double(image.width) / Double(image.height)
        XCTAssertEqual(readRatio, Double(photo.width) / Double(photo.height), accuracy: 0.01)
    }

    func testTheBytesCarryNoExifNoGpsNoXmpNoCommentNoThumbnail() throws {
        for (width, height) in [(1500, 2000), (1080, 1920), (800, 600)] {
            let photo = try XCTUnwrap(
                PhotoEncoder.encode(
                    texturedImage(width: width, height: height), aspect: 0.5, mirror: false, nowMillis: 0
                )
            )

            XCTAssertFalse(JpegMetadata.containsMetadata(photo.jpeg), "\(width) x \(height)")
            for marker in ["Exif", "GPS", "xap", "http://ns.adobe.com", "TIFF"] {
                XCTAssertNil(photo.jpeg.range(of: Data(marker.utf8)), "\(marker) dans \(width) x \(height)")
            }
        }
    }

    func testAnEncoderThatWroteMetadataIsStrippedAnyway() throws {
        // La ceinture et les bretelles : même si un encodeur glissait de l'EXIF, `JpegMetadata.stripped` le retire.
        let image = try texturedImage(width: 300, height: 400)
        let output = NSMutableData()
        let destination = try jpegDestination(output)
        let gps: [CFString: Any] = [
            kCGImagePropertyGPSLatitude: 48.85, kCGImagePropertyGPSLatitudeRef: "N",
            kCGImagePropertyGPSLongitude: 2.35, kCGImagePropertyGPSLongitudeRef: "E"
        ]
        let tiff: [CFString: Any] = [kCGImagePropertyTIFFModel: "SecretPhone", kCGImagePropertyTIFFMake: "Acme"]
        let exif: [CFString: Any] = [kCGImagePropertyExifDateTimeOriginal: "2026:11:07 10:00:00"]
        let properties: [CFString: Any] = [
            kCGImagePropertyGPSDictionary: gps,
            kCGImagePropertyTIFFDictionary: tiff,
            kCGImagePropertyExifDictionary: exif
        ]
        CGImageDestinationAddImage(destination, image, properties as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))

        let dirty = output as Data
        XCTAssertTrue(JpegMetadata.containsMetadata(dirty), "le témoin : l'encodeur a bien écrit des métadonnées")
        let clean = try XCTUnwrap(JpegMetadata.stripped(dirty))
        XCTAssertFalse(JpegMetadata.containsMetadata(clean))
        XCTAssertNil(clean.range(of: Data("SecretPhone".utf8)))
        XCTAssertNotNil(PhotoEncoder.decodeUpright(clean), "la photo reste lisible")
    }

    // MARK: - Le reflet (D37) : la frontale en miroir, l'arrière jamais

    func testTheFrontCameraIsPublishedAsAMirrorRedOnTheLeftBecomesBlueOnTheLeft() throws {
        let source = try halvesImage(width: 600, height: 800)
        let photo = try XCTUnwrap(PhotoEncoder.encode(source, aspect: 0.45, mirror: true, nowMillis: 0))
        let image = try decoded(photo)
        let left = try pixel(image, x: image.width / 4, y: image.height / 2)
        let right = try pixel(image, x: image.width * 3 / 4, y: image.height / 2)

        // La scène a du rouge à gauche ; en reflet, le rouge passe à droite, comme dans un miroir.
        XCTAssertGreaterThan(left.blue, 200)
        XCTAssertLessThan(left.red, 80)
        XCTAssertGreaterThan(right.red, 200)
        XCTAssertLessThan(right.blue, 80)
    }

    func testTheBackCameraIsNeverMirrored() throws {
        let source = try halvesImage(width: 600, height: 800)
        let photo = try XCTUnwrap(PhotoEncoder.encode(source, aspect: 0.45, mirror: false, nowMillis: 0))
        let image = try decoded(photo)
        let left = try pixel(image, x: image.width / 4, y: image.height / 2)
        let right = try pixel(image, x: image.width * 3 / 4, y: image.height / 2)

        XCTAssertGreaterThan(left.red, 200)
        XCTAssertLessThan(left.blue, 80)
        XCTAssertGreaterThan(right.blue, 200)
        XCTAssertLessThan(right.red, 80)
    }

    func testTheViewfinderOnlyMirrorsTheFrontLens() {
        XCTAssertTrue(PhotoMirror.shouldMirror(frontCamera: CameraLens.front == .front))
        XCTAssertFalse(PhotoMirror.shouldMirror(frontCamera: CameraLens.back == .front))
    }

    // MARK: - Ce que tu vois est ce qui part : le recadrage « cover »

    func testTheCropKeepsTheCentreOfTheSensorImage() throws {
        // Source 600 x 800 à découper au ratio 0,45 : 360 x 800, centrée (de x = 120 à x = 480). Un trait vert au milieu reste au milieu.
        let context = try makeContext(width: 600, height: 800)
        context.setFillColor(red: 0, green: 0, blue: 0, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: 600, height: 800))
        context.setFillColor(red: 0, green: 1, blue: 0, alpha: 1)
        context.fill(CGRect(x: 290, y: 0, width: 20, height: 800))
        let source = try XCTUnwrap(context.makeImage())
        let photo = try XCTUnwrap(PhotoEncoder.encode(source, aspect: 0.45, mirror: false, nowMillis: 0))
        let image = try decoded(photo)
        let centre = try pixel(image, x: image.width / 2, y: image.height / 2)
        let edge = try pixel(image, x: 4, y: image.height / 2)

        XCTAssertGreaterThan(centre.green, 150)
        XCTAssertLessThan(edge.green, 60)
    }

    // MARK: - L'orientation du capteur est appliquée aux pixels

    func testTheOrientationOfTheSensorIsAppliedToThePixelsAndThenForgotten() throws {
        // Une image large de 400 x 200 marquée « à tourner d'un quart de tour » (orientation 6) devient une image haute de 200 x 400.
        let image = try texturedImage(width: 400, height: 200)
        let output = NSMutableData()
        let destination = try jpegDestination(output)
        CGImageDestinationAddImage(destination, image, [kCGImagePropertyOrientation: 6] as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))

        let upright = try XCTUnwrap(PhotoEncoder.decodeUpright(output as Data))

        XCTAssertGreaterThan(upright.height, upright.width, "l'image est devenue haute")
        XCTAssertEqual(Double(upright.height) / Double(upright.width), 2.0, accuracy: 0.02)
    }

    func testADecodeNeverKeepsMoreThanTheMaximumOfPixels() throws {
        let image = try texturedImage(width: 3000, height: 1500)
        let output = NSMutableData()
        let destination = try jpegDestination(output)
        CGImageDestinationAddImage(destination, image, nil)
        XCTAssertTrue(CGImageDestinationFinalize(destination))

        let small = try XCTUnwrap(PhotoEncoder.decodeUpright(output as Data, maxPixel: 1000))

        XCTAssertLessThanOrEqual(max(small.width, small.height), 1000)
    }

    func testBytesThatAreNotAnImageGiveNoPhoto() {
        XCTAssertNil(PhotoEncoder.decodeUpright(Data([1, 2, 3, 4])))
        XCTAssertNil(PhotoEncoder.decodeUpright(Data()))
    }
}
