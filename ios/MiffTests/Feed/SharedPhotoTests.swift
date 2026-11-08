// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La photo d'update d'un proche (FEED-8) : ce qui vient du réseau est NETTOYÉ avant d'être utilisé (quelqu'un d'autre l'a écrit), une photo expirée
// n'est jamais montrée, un lieu n'existe qu'entier, et la fenêtre de lecture est de 23 h (une photo vit 23 h : au-delà, rien n'est lu).
// Miroir de `SharedPhotoTest.kt` et de `FeedBuilderTest.kt` d'Android.
final class SharedPhotoTests: XCTestCase {
    private func raw(
        jpeg: Data? = Data(repeating: 7, count: 1_000),
        created: Int? = feedNow - feedMinutes(20),
        expires: Int? = feedNow + feedHours(22),
        lat: Double? = 48.85661,
        lng: Double? = 2.35222,
        measuredAt: Int? = feedNow - feedMinutes(22)
    ) -> RawPhoto {
        RawPhoto(
            jpeg: jpeg, createdAtMillis: created, expiresAtMillis: expires, lat: lat, lng: lng, measuredAtMillis: measuredAt
        )
    }

    // MARK: - Nettoyage

    func testAValidDocumentGivesAPhotoWithItsPlaceAndTheTimeOfTheMeasure() throws {
        let photo = try XCTUnwrap(SharedPhoto.fromServer(raw(), nowMillis: feedNow))

        XCTAssertEqual(photo.jpeg.count, 1_000)
        XCTAssertEqual(photo.place?.measuredAtMillis, feedNow - feedMinutes(22))
        XCTAssertNotEqual(photo.place?.measuredAtMillis, photo.createdAtMillis, "l'heure du lieu est la sienne")
    }

    func testAnEmptyOrOversizedOrMissingJpegIsNotAPhoto() {
        XCTAssertNil(SharedPhoto.fromServer(raw(jpeg: Data()), nowMillis: feedNow))
        XCTAssertNil(SharedPhoto.fromServer(raw(jpeg: nil), nowMillis: feedNow))
        XCTAssertNil(SharedPhoto.fromServer(raw(jpeg: Data(count: 150_001)), nowMillis: feedNow))
        XCTAssertNotNil(SharedPhoto.fromServer(raw(jpeg: Data(count: 150_000)), nowMillis: feedNow))
    }

    func testAMissingTimeIsNotAPhoto() {
        XCTAssertNil(SharedPhoto.fromServer(raw(created: nil), nowMillis: feedNow))
        XCTAssertNil(SharedPhoto.fromServer(raw(expires: nil), nowMillis: feedNow))
    }

    func testAPhotoPastItsExpiryIsNeverShownEvenWhenTheServerStillSendsIt() {
        XCTAssertNil(SharedPhoto.fromServer(raw(expires: feedNow), nowMillis: feedNow), "à l'instant exact : expirée")
        XCTAssertNil(SharedPhoto.fromServer(raw(expires: feedNow - 1), nowMillis: feedNow))
        XCTAssertNotNil(SharedPhoto.fromServer(raw(expires: feedNow + 1), nowMillis: feedNow))
    }

    func testAPlaceIsKeptOnlyWhole() throws {
        for incomplete in [raw(lat: nil), raw(lng: nil), raw(measuredAt: nil)] {
            let photo = try XCTUnwrap(SharedPhoto.fromServer(incomplete, nowMillis: feedNow))
            XCTAssertNil(photo.place, "jamais la moitié d'un lieu : la photo est montrée sans lieu")
        }
    }

    func testAPlaceOutOfRangeOrNotANumberIsDropped() throws {
        for invalid in [raw(lat: 91), raw(lat: -91), raw(lng: 181), raw(lng: -181), raw(lat: .nan), raw(lng: .infinity)] {
            let photo = try XCTUnwrap(SharedPhoto.fromServer(invalid, nowMillis: feedNow))
            XCTAssertNil(photo.place)
        }
    }

    // MARK: - Expiration

    func testIsExpiredIsTrueAtTheInstantOfExpiryAndAfter() {
        let photo = PhotoFixtures.photo(createdAgo: 0, lifetime: feedHours(23))

        XCTAssertFalse(photo.isExpired(nowMillis: feedNow + feedHours(23) - 1))
        XCTAssertTrue(photo.isExpired(nowMillis: feedNow + feedHours(23)))
        XCTAssertTrue(photo.isExpired(nowMillis: feedNow + feedHours(30)))
    }

    // MARK: - La fenêtre de lecture de 23 h

    func testNothingIsReadWithoutAPhotoWithoutAnUpdateOrWhenNotVisible() {
        XCTAssertEqual(slot(hasPhoto: false), .none)
        XCTAssertEqual(slot(update: nil), .none)
        XCTAssertEqual(slot(visible: false), .none)
    }

    func testAPhotoIsReadForTheLast23HoursOnly() {
        XCTAssertEqual(slot(update: feedNow - feedMinutes(5)), .candidate(lastUpdateAtMillis: feedNow - feedMinutes(5)))
        let justInside = feedNow - (23 * FeedMillis.hour - 1)
        XCTAssertEqual(slot(update: justInside), .candidate(lastUpdateAtMillis: justInside))
        XCTAssertEqual(slot(update: feedNow - 23 * FeedMillis.hour), .none, "à 23 h exactement : plus rien n'est lu")
        XCTAssertEqual(slot(update: feedNow - feedHours(30)), .none)
    }

    func testAnUpdateInTheFutureOfAWrongClockCountsAsNow() {
        let future = feedNow + feedHours(5)

        XCTAssertEqual(slot(update: future), .candidate(lastUpdateAtMillis: future))
    }

    private func slot(hasPhoto: Bool = true, update: Int? = feedNow - feedMinutes(10), visible: Bool = true) -> PhotoSlot {
        PhotoSlot.of(hasPhoto: hasPhoto, lastUpdateAtMillis: update, visible: visible, nowMillis: feedNow)
    }

    // MARK: - Confidentialité

    func testNothingOfAPhotoOrItsPlaceShowsInADescription() {
        let photo = PhotoFixtures.photo()
        let place = PhotoPlace(lat: 48.85661, lng: 2.35222, measuredAtMillis: feedNow)
        let texts = [
            "\(photo)", String(describing: photo), "\(place)", "\(PhotoLookup.found(photo))", "\(PhotoFetch.photo(photo))",
            "\(PhotoSlotState.shown(photo))", "\(PhotoFixtures.request())"
        ]

        for text in texts {
            XCTAssertTrue(text.contains("redacted"), text)
            XCTAssertFalse(text.contains("48.85"), text)
            XCTAssertFalse(text.contains("2.35"), text)
        }
    }
}
