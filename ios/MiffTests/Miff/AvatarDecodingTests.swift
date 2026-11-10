import UIKit
import XCTest
@testable import Miff

/// La photo d'un proche vient d'un AUTRE téléphone : le poids est borné par les règles, pas les dimensions. Une image de couleur unie
/// pèse très peu et peut pourtant être immense : elle ne doit jamais être décodée en pleine taille.
final class AvatarDecodingTests: XCTestCase {
    /// Un JPEG de couleur unie (très léger quelles que soient ses dimensions).
    private func jpeg(width: Int, height: Int) throws -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        let image = renderer.image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return try XCTUnwrap(image.jpegData(compressionQuality: 0.8))
    }

    func testAPlausiblePhotoIsReducedToTheSizeOfTheCircle() throws {
        let data = try jpeg(width: 512, height: 512)

        let image = try XCTUnwrap(AvatarDecoding.image(from: data, pixelSize: 84))

        XCTAssertEqual(image.size.width * image.scale, 84, accuracy: 1)
        XCTAssertLessThanOrEqual(image.size.height * image.scale, 84)
    }

    func testAPhotoThatDescribesAnImmenseImageIsRefusedWithoutBeingDecoded() throws {
        let data = try jpeg(width: AvatarDecoding.maxSide + 1, height: 64)
        // Léger : il passerait la borne en octets des règles.
        XCTAssertLessThanOrEqual(data.count, ProfileDocument.maxAvatarBytes)

        XCTAssertNil(AvatarDecoding.image(from: data, pixelSize: 84))
    }

    func testAPhotoAtTheLimitOfThePlausibleSizeIsAccepted() throws {
        let data = try jpeg(width: AvatarDecoding.maxSide, height: 32)

        XCTAssertNotNil(AvatarDecoding.image(from: data, pixelSize: 84))
    }

    func testAbsentEmptyOversizedOrUnreadableDataGivesInitialsNotACrash() {
        XCTAssertNil(AvatarDecoding.image(from: nil, pixelSize: 84))
        XCTAssertNil(AvatarDecoding.image(from: Data(), pixelSize: 84))
        XCTAssertNil(AvatarDecoding.image(from: Data(count: ProfileDocument.maxAvatarBytes + 1), pixelSize: 84))
        XCTAssertNil(AvatarDecoding.image(from: Data([1, 2, 3, 4]), pixelSize: 84))
    }

    func testTheSameDecodedImageIsReusedFromTheMemoryCacheUntilTheSessionEnds() throws {
        let data = try jpeg(width: 256, height: 256)
        AvatarDecoding.removeAll()

        let first = AvatarDecoding.image(from: data, pixelSize: 84)
        let second = AvatarDecoding.image(from: data, pixelSize: 84)

        XCTAssertTrue(first === second)
        AvatarDecoding.removeAll()
        XCTAssertFalse(try XCTUnwrap(first) === AvatarDecoding.image(from: data, pixelSize: 84))
    }
}
