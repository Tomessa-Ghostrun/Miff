import XCTest
@testable import Miff

/// Quelle adresse de photo Google on accepte de télécharger, et à quelle taille. Mêmes cas qu'Android (`GooglePhotoTest`).
final class GooglePhotoURLTests: XCTestCase {
    private func url(_ text: String) throws -> URL {
        try XCTUnwrap(URL(string: text))
    }

    private func selected(_ text: String) throws -> String? {
        GooglePhotoURL.select(try url(text))?.absoluteString
    }

    // MARK: - Domaine et protocole

    func testAGooglePictureAddressIsAccepted() throws {
        XCTAssertTrue(GooglePhotoURL.isAllowed(try url("https://lh3.googleusercontent.com/a/ACg8ocJabc=s96-c")))
        XCTAssertTrue(GooglePhotoURL.isAllowed(try url("https://googleusercontent.com/a/abc")))
        XCTAssertTrue(GooglePhotoURL.isAllowed(try url("https://LH3.GoogleUserContent.com/a/abc")))
        XCTAssertTrue(GooglePhotoURL.isAllowed(try url("https://lh3.googleusercontent.com:443/a/abc")))
    }

    func testAnythingElseThanHttpsOnGoogleUserContentIsRefused() throws {
        let refused = [
            "http://lh3.googleusercontent.com/a/abc",
            "ftp://lh3.googleusercontent.com/a/abc",
            "https://example.com/a/abc",
            "https://googleusercontent.com.evil.example/a/abc",
            "https://evilgoogleusercontent.com/a/abc",
            "https://lh3.googleusercontent.com@evil.example/a/abc",
            "https://user:secret@lh3.googleusercontent.com/a/abc",
            "https://lh3.googleusercontent.com:8443/a/abc",
            "https://lh3.googleusercontent.com./a/abc",
            "data:image/jpeg;base64,AAAA"
        ]
        for text in refused {
            XCTAssertFalse(GooglePhotoURL.isAllowed(try url(text)), text)
            XCTAssertNil(GooglePhotoURL.select(try url(text)), text)
        }
    }

    func testAnAbsentOrAbsurdlyLongAddressGivesNothing() throws {
        XCTAssertNil(GooglePhotoURL.select(nil))
        let long = "https://lh3.googleusercontent.com/a/" + String(repeating: "a", count: 2_100)
        XCTAssertNil(GooglePhotoURL.select(try url(long)))
    }

    // MARK: - La silhouette générique

    func testTheGenericSilhouetteIsRecognisedAndRefused() throws {
        let generic = [
            "https://lh3.googleusercontent.com/a/default-user=s96-c",
            "https://lh3.googleusercontent.com/-XdUIqdMkCWA/AAAAAAAAAAI/AAAAAAAAAAA/4252rscbv5M/photo.jpg"
        ]
        for text in generic {
            XCTAssertTrue(GooglePhotoURL.isGenericPicture(try url(text)), text)
            XCTAssertNil(GooglePhotoURL.select(try url(text)), text)
        }
    }

    func testARealPictureIsNotTakenForTheSilhouette() throws {
        XCTAssertFalse(GooglePhotoURL.isGenericPicture(try url("https://lh3.googleusercontent.com/a/ACg8ocJabc=s96-c")))
    }

    // MARK: - La taille demandée

    func testTheSizeSuffixIsReplacedByTheRequestedSize() throws {
        XCTAssertEqual(
            try selected("https://lh3.googleusercontent.com/a/ACg8ocJabc=s96-c"),
            "https://lh3.googleusercontent.com/a/ACg8ocJabc=s256-c"
        )
        XCTAssertEqual(
            try selected("https://lh3.googleusercontent.com/a/ACg8ocJabc=s96"),
            "https://lh3.googleusercontent.com/a/ACg8ocJabc=s256-c"
        )
        XCTAssertEqual(
            try selected("https://lh3.googleusercontent.com/a/ACg8ocJabc=w96-h96-c"),
            "https://lh3.googleusercontent.com/a/ACg8ocJabc=s256-c"
        )
    }

    func testAnAddressWithoutSizeGetsTheRequestedSize() throws {
        XCTAssertEqual(
            try selected("https://lh3.googleusercontent.com/a/ACg8ocJabc"),
            "https://lh3.googleusercontent.com/a/ACg8ocJabc=s256-c"
        )
    }

    func testTheOldStyleSizeSegmentIsReplaced() throws {
        XCTAssertEqual(
            try selected("https://lh3.googleusercontent.com/-aBcD/AAAAAAAAAAI/AAAAAAAAAAB/xyz/s96-c/photo.jpg"),
            "https://lh3.googleusercontent.com/-aBcD/AAAAAAAAAAI/AAAAAAAAAAB/xyz/s256-c/photo.jpg"
        )
    }

    func testAnAddressWithAQueryIsLeftAsItIsAndTheFragmentIsDropped() throws {
        XCTAssertEqual(
            try selected("https://lh3.googleusercontent.com/a/ACg8ocJabc?sz=50#top"),
            "https://lh3.googleusercontent.com/a/ACg8ocJabc?sz=50"
        )
    }

    func testTheRequestedSizeIsLargerThanTheStoredAvatarButStaysSmall() {
        XCTAssertGreaterThan(GooglePhotoURL.requestSide, AvatarCompression.sides[0])
        XCTAssertLessThanOrEqual(GooglePhotoURL.requestSide, 512)
    }
}
