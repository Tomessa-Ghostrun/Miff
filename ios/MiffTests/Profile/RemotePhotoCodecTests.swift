import CoreGraphics
import ImageIO
import XCTest
@testable import Miff

/// Le vrai codec sur des octets venus du réseau (la photo du compte Google) : formats permis seulement, dimensions déclarées plafonnées
/// AVANT le décodage, décodage en vignette, et du contenu hostile qui donne rien.
final class RemotePhotoCodecTests: XCTestCase {
    private func makeImage(width: Int, height: Int) -> CGImage? {
        let context = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        )
        context?.setFillColor(red: 0.2, green: 0.6, blue: 0.4, alpha: 1)
        context?.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return context?.makeImage()
    }

    private func encoded(width: Int, height: Int, type: String) throws -> Data {
        let image = try XCTUnwrap(makeImage(width: width, height: height))
        let output = NSMutableData()
        let made = CGImageDestinationCreateWithData(output as CFMutableData, type as CFString, 1, nil)
        let destination = try XCTUnwrap(made)
        CGImageDestinationAddImage(destination, image, nil)
        XCTAssertTrue(CGImageDestinationFinalize(destination), type)
        return output as Data
    }

    private func decoded(_ data: Data) -> (any DecodedPhoto)? {
        ImageAvatarCodec.decodeRemoteSynchronously(data)
    }

    /// Une somme de contrôle PNG (CRC-32).
    private func crc32(_ bytes: ArraySlice<UInt8>) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for byte in bytes {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB8_8320 : crc >> 1
            }
        }
        return ~crc
    }

    /// Un petit PNG dont l'en-tête (IHDR) DÉCLARE `width` x `height` : l'image réelle ne fait pas cette taille.
    private func pngDeclaring(width: UInt32, height: UInt32) throws -> Data {
        var bytes = [UInt8](try encoded(width: 1, height: 1, type: "public.png"))
        for (offset, value) in [(16, width), (20, height)] {
            for index in 0..<4 {
                bytes[offset + index] = UInt8((value >> UInt32(24 - 8 * index)) & 0xFF)
            }
        }
        let checksum = crc32(bytes[12..<29])
        for index in 0..<4 {
            bytes[29 + index] = UInt8((checksum >> UInt32(24 - 8 * index)) & 0xFF)
        }
        return Data(bytes)
    }

    // MARK: - Les formats permis

    func testJpegAndPngAreAccepted() throws {
        let png = decoded(try encoded(width: 120, height: 80, type: "public.png"))
        XCTAssertEqual(png?.width, 120)
        XCTAssertEqual(png?.height, 80)
        let jpeg = decoded(try encoded(width: 120, height: 80, type: "public.jpeg"))
        XCTAssertNotNil(jpeg)
    }

    func testGifAndTiffAreRefused() throws {
        XCTAssertNil(decoded(try encoded(width: 40, height: 40, type: "com.compuserve.gif")))
        XCTAssertNil(decoded(try encoded(width: 40, height: 40, type: "public.tiff")))
    }

    func testTheAllowedFormatsAreExactlyJpegPngHeicWebp() {
        XCTAssertEqual(
            ImageAvatarCodec.remoteTypes,
            ["public.jpeg", "public.png", "public.heic", "org.webmproject.webp"]
        )
    }

    // MARK: - Les dimensions

    func testALargeImageIsDecodedAsASmallThumbnailOnly() throws {
        let photo = decoded(try encoded(width: 2_000, height: 1_000, type: "public.png"))
        let side = max(photo?.width ?? .max, photo?.height ?? .max)
        XCTAssertLessThanOrEqual(side, ImageAvatarCodec.remoteThumbnailSide)
        XCTAssertGreaterThan(side, 0)
    }

    func testASideOverTheLimitIsRefusedEvenWhenTheImageIsTiny() throws {
        XCTAssertNil(decoded(try encoded(width: ImageAvatarCodec.remoteMaxSide + 1, height: 1, type: "public.png")))
        XCTAssertNil(decoded(try encoded(width: 1, height: ImageAvatarCodec.remoteMaxSide + 1, type: "public.png")))
        XCTAssertNotNil(decoded(try encoded(width: ImageAvatarCodec.remoteMaxSide, height: 1, type: "public.png")))
    }

    func testThePixelCountLimitIsExact() throws {
        XCTAssertEqual(ImageAvatarCodec.remoteMaxPixels, 16_000_000)
        XCTAssertNotNil(decoded(try encoded(width: 4_000, height: 4_000, type: "public.png")))
        XCTAssertNil(decoded(try encoded(width: 4_000, height: 4_001, type: "public.png")))
    }

    // MARK: - Le contenu hostile

    func testATinyPngThatDeclaresHugeDimensionsIsRefusedBeforeAnyDecoding() throws {
        XCTAssertNil(decoded(try pngDeclaring(width: 60_000, height: 60_000)))
        XCTAssertNil(decoded(try pngDeclaring(width: 4_000_000_000, height: 1)))
        XCTAssertNil(ImageAvatarCodec.decodeSynchronously(try pngDeclaring(width: 60_000, height: 60_000)))
    }

    func testTheDeclaredSizeTrickDoesNotBreakAnHonestSmallPng() throws {
        XCTAssertNotNil(decoded(try pngDeclaring(width: 1, height: 1)))
    }

    func testGarbageGivesNothing() {
        let hostile: [Data] = [
            Data(),
            Data(count: 4_096),
            Data("<html><body>not an image</body></html>".utf8),
            Data([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]),
            Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
            Data(repeating: 0x47, count: 10_000)
        ]
        for data in hostile {
            XCTAssertNil(decoded(data), "\(data.count) octets")
        }
    }

    func testTheAsyncEntryPointUsesTheStrictPath() async throws {
        let codec = ImageAvatarCodec()
        let gif = try encoded(width: 40, height: 40, type: "com.compuserve.gif")
        let refused = await codec.decodeRemote(gif)
        XCTAssertNil(refused)
        let png = try encoded(width: 40, height: 40, type: "public.png")
        let accepted = await codec.decodeRemote(png)
        XCTAssertNotNil(accepted)
    }

    // MARK: - Tout le chemin, avec le vrai codec

    func testTheWholePathGivesASmallJpegWithoutMetadata() async throws {
        let source = BytesSource(data: try encoded(width: 600, height: 400, type: "public.png"))
        let prefill = GooglePhotoPrefill(source: source, codec: ImageAvatarCodec(), timeout: 10)
        let jpeg = await prefill.load(URL(string: "https://lh3.googleusercontent.com/a/abc=s96-c"))
        let data = try XCTUnwrap(jpeg)
        XCTAssertLessThanOrEqual(data.count, AvatarCompression.maxBytes)
        XCTAssertEqual(Array(data.prefix(2)), [0xFF, 0xD8])
        let image = try XCTUnwrap(CGImageSourceCreateWithData(data as CFData, nil))
        let properties = CGImageSourceCopyPropertiesAtIndex(image, 0, nil) as? [CFString: Any]
        XCTAssertNil(properties?[kCGImagePropertyGPSDictionary])
        let tiff = properties?[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
        XCTAssertNil(tiff?[kCGImagePropertyTIFFMake])
        XCTAssertNil(tiff?[kCGImagePropertyTIFFModel])
    }

    func testTheWholePathRefusesAHostileAnswer() async throws {
        let hostile: [Data] = [
            try encoded(width: 40, height: 40, type: "com.compuserve.gif"),
            try pngDeclaring(width: 60_000, height: 60_000),
            Data("<html></html>".utf8)
        ]
        for data in hostile {
            let prefill = GooglePhotoPrefill(source: BytesSource(data: data), codec: ImageAvatarCodec(), timeout: 10)
            let jpeg = await prefill.load(URL(string: "https://lh3.googleusercontent.com/a/abc=s96-c"))
            XCTAssertNil(jpeg)
        }
    }
}

private struct BytesSource: ProviderPhotoSource {
    let data: Data

    func fetch(_ url: URL) async -> Data? {
        data
    }
}
