// Quelques assertions citent des champs entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import FirebaseFirestore
import XCTest
@testable import Miff

/// FEED-ORDRE-PHOTO : la LECTURE de `photoAt` (cas T13, T14 et T17 du ticket). Ce que le Feed lit a été écrit par quelqu'un d'autre : une clé
/// absente (statuts d'avant cette clé, ancienne version de l'app), nulle ou qui n'est pas un horodatage est une date ABSENTE, jamais un
/// plantage ni un refus ; la date est tronquée (jamais arrondie) à la milliseconde, comme `Timestamp.toDate().time` d'Android.
final class PhotoDateMappingTests: XCTestCase {
    private let seconds: Int64 = 1_794_063_600

    private func fields(_ photoAt: Any?) -> [String: Any] {
        var base: [String: Any] = ["presence": "in_trusted", "placeId": "p1", "hasPhoto": true, "batteryPct": 80]
        if let photoAt {
            base["photoAt"] = photoAt
        }
        return base
    }

    func testPhotoAtIsReadInMilliseconds() {
        let status = FeedFirestoreMapping.status(fields: fields(Timestamp(seconds: seconds, nanoseconds: 250_000_000)))

        XCTAssertEqual(status.photoAtMillis, Int(seconds) * 1000 + 250)
    }

    func testT13AStatusWithoutThePhotoAtKeyIsReadAndHasNoDate() {
        let status = FeedFirestoreMapping.status(fields: fields(nil))

        XCTAssertNil(status.photoAtMillis)
        XCTAssertEqual(status.presence, .inTrusted, "le reste du statut est lu comme avant")
        XCTAssertTrue(status.hasPhoto)
        XCTAssertEqual(status.batteryPct, 80)
    }

    func testT14AnUnreadableValueIsNoDateAndNeverCrashes() {
        let unreadable: [Any] = [NSNull(), "hier", 12_345, 1.5, true, ["seconds": 1], [Int]()]
        for value in unreadable {
            let status = FeedFirestoreMapping.status(fields: fields(value))

            XCTAssertNil(status.photoAtMillis, "\(value)")
            XCTAssertEqual(status.presence, .inTrusted, "\(value) : le reste est lu")
        }
    }

    func testT17TwoDatesThatOnlyDifferByTheMicrosecondsAreEqualAtTheMillisecond() {
        let low = FeedFirestoreMapping.status(fields: fields(Timestamp(seconds: seconds, nanoseconds: 123_000_000)))
        let high = FeedFirestoreMapping.status(fields: fields(Timestamp(seconds: seconds, nanoseconds: 123_999_000)))

        XCTAssertEqual(low.photoAtMillis, high.photoAtMillis, "tronquée, jamais arrondie")
        XCTAssertEqual(high.photoAtMillis, Int(seconds) * 1000 + 123)
        let next = FeedFirestoreMapping.status(fields: fields(Timestamp(seconds: seconds, nanoseconds: 124_000_000)))
        XCTAssertEqual(next.photoAtMillis, Int(seconds) * 1000 + 124, "la milliseconde suivante est bien une autre date")
    }

    func testTheOtherTimesAreReadTheSameExactWay() {
        let status = FeedFirestoreMapping.status(fields: [
            "presenceAt": Timestamp(seconds: seconds, nanoseconds: 999_999_999),
            "lastUpdateAt": Timestamp(seconds: seconds, nanoseconds: 0)
        ])

        XCTAssertEqual(status.presenceAtMillis, Int(seconds) * 1000 + 999)
        XCTAssertEqual(status.lastUpdateAtMillis, Int(seconds) * 1000)
    }

    func testThePhotoAtIsKeptWhenThePhotoIsGoneAndItIsNotDerivedFromAnythingElse() {
        var gone = fields(Timestamp(seconds: seconds, nanoseconds: 0))
        gone["hasPhoto"] = false
        var never = fields(nil)
        never["lastUpdateAt"] = Timestamp(seconds: seconds + 60, nanoseconds: 0)
        never["presenceAt"] = Timestamp(seconds: seconds + 120, nanoseconds: 0)

        XCTAssertEqual(FeedFirestoreMapping.status(fields: gone).photoAtMillis, Int(seconds) * 1000)
        XCTAssertFalse(FeedFirestoreMapping.status(fields: gone).hasPhoto)
        XCTAssertNil(FeedFirestoreMapping.status(fields: never).photoAtMillis, "jamais une date déduite de lastUpdateAt ou presenceAt")
    }

    func testAStatusFromTheServerKeepsThePhotoAtThroughTheCleaning() {
        let status = MemberStatus.fromServer(RawStatus(presence: "out", hasPhoto: true, photoAtMillis: 42))

        XCTAssertEqual(status.photoAtMillis, 42)
        XCTAssertEqual("\(status)", "MemberStatus(redacted)", "un statut ne montre rien de lui-même")
    }
}
// swiftlint:enable line_length
