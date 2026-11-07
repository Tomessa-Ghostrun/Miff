// Quelques assertions citent des champs entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import FirebaseFirestore
import XCTest
@testable import Miff

// Ce que le Feed lit sur le réseau a été écrit par QUELQU'UN D'AUTRE : tout est vérifié avant d'être utilisé (statut, position,
// numéro, identifiants). Du Swift pur : aucun réseau.
final class FeedMappingTests: XCTestCase {
    private func timestamp(_ millis: Int) -> Timestamp {
        Timestamp(date: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    func testAStatusIsReadFromItsFields() {
        let fields: [String: Any] = [
            "presence": "out",
            "placeId": "p1",
            "presenceAt": timestamp(feedNow - feedMinutes(70)),
            "lastUpdateAt": timestamp(feedNow - feedMinutes(80)),
            "hasPhoto": true,
            "batteryPct": 85,
            "locationAccess": "foreground",
            "updatedAt": timestamp(feedNow - feedMinutes(5))
        ]

        let status = FeedFirestoreMapping.status(fields: fields)

        XCTAssertEqual(status.presence, .out)
        XCTAssertEqual(status.placeId, "p1")
        XCTAssertEqual(status.presenceAtMillis, feedNow - feedMinutes(70))
        XCTAssertEqual(status.referenceMillis, feedNow - feedMinutes(70), "le plus récent du départ et de l'update")
        XCTAssertTrue(status.hasPhoto)
        XCTAssertEqual(status.batteryPct, 85)
        XCTAssertEqual(status.locationAccess, .foreground)
        XCTAssertEqual(status.updatedAtMillis, feedNow - feedMinutes(5))
    }

    func testAnythingUnexpectedIsDroppedNeverGuessed() {
        let fields: [String: Any] = [
            "presence": "teleported",
            "placeId": "../../users/x",
            "presenceAt": "yesterday",
            "batteryPct": 250,
            "locationAccess": "always-and-forever"
        ]

        let status = FeedFirestoreMapping.status(fields: fields)

        XCTAssertNil(status.presence, "une présence inconnue est neutre")
        XCTAssertNil(status.placeId)
        XCTAssertNil(status.presenceAtMillis)
        XCTAssertNil(status.batteryPct)
        XCTAssertNil(status.locationAccess)
        XCTAssertFalse(status.hasPhoto)
        XCTAssertEqual(StatusColor.level(
            MemberStatusFacts(sharingEnabled: true, presence: status.presence, presenceAtMillis: nil, lastUpdateAtMillis: nil),
            nowMillis: feedNow
        ), .neutral)
    }

    func testTheBatteryBoundsAreZeroAndAHundred() {
        func battery(_ value: Double) -> Int? {
            MemberStatus.fromServer(RawStatus(batteryPct: value)).batteryPct
        }

        XCTAssertEqual(battery(0), 0)
        XCTAssertEqual(battery(100), 100)
        XCTAssertNil(battery(-1))
        XCTAssertNil(battery(100.5))
        XCTAssertNil(battery(.nan))
        XCTAssertNil(battery(.infinity))
    }

    func testAPositionNeedsItsTwoCoordinatesInRangeAndItsTimeOfMeasure() {
        let good: [String: Any] = ["lat": 48.85661, "lng": 2.35222, "measuredAt": timestamp(feedNow)]

        XCTAssertEqual(
            FeedFirestoreMapping.position(fields: good),
            .found(lat: 48.85661, lng: 2.35222, measuredAtMillis: feedNow)
        )
        XCTAssertEqual(FeedFirestoreMapping.position(fields: nil), .notFound)
        XCTAssertEqual(FeedFirestoreMapping.position(fields: ["lat": 1.0, "lng": 2.0]), .notFound, "pas d'heure de mesure")
        XCTAssertEqual(FeedFirestoreMapping.position(fields: ["lat": 91.0, "lng": 2.0, "measuredAt": timestamp(feedNow)]), .notFound)
        XCTAssertEqual(FeedFirestoreMapping.position(fields: ["lat": 1.0, "lng": 181.0, "measuredAt": timestamp(feedNow)]), .notFound)
        XCTAssertEqual(FeedFirestoreMapping.position(fields: ["lat": "1", "lng": 2.0, "measuredAt": timestamp(feedNow)]), .notFound)
    }

    func testAFailedReadBecomesACalmAnswerWithoutReadingTheMessage() {
        let denied = NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.permissionDenied)
        let unavailable = NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.unavailable)
        let quota = NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.resourceExhausted)
        let late = NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.deadlineExceeded)
        let unauthenticated = NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.unauthenticated)
        let other = NSError(domain: "SomethingElse", code: 7)

        XCTAssertEqual(FeedFirestoreMapping.lookup(forError: denied), .denied)
        XCTAssertEqual(FeedFirestoreMapping.lookup(forError: unavailable), .offline)
        XCTAssertEqual(FeedFirestoreMapping.lookup(forError: quota), .failed, "jamais « quota » à l'écran")
        XCTAssertEqual(FeedFirestoreMapping.lookup(forError: late), .failed)
        XCTAssertEqual(FeedFirestoreMapping.lookup(forError: unauthenticated), .failed)
        XCTAssertEqual(FeedFirestoreMapping.lookup(forError: other), .failed, "pas le code 7 d'un autre domaine")
    }

    func testAnIdentifierIsCheckedBeforeItBecomesAPathAndANumberBeforeItBecomesAMember() {
        XCTAssertTrue(FeedFirestoreMapping.isUid("AbC123xyZ9"))
        XCTAssertFalse(FeedFirestoreMapping.isUid(""))
        XCTAssertFalse(FeedFirestoreMapping.isUid("a/b"))
        XCTAssertFalse(FeedFirestoreMapping.isUid("a b"))
        XCTAssertFalse(FeedFirestoreMapping.isUid(String(repeating: "a", count: 129)))
        XCTAssertEqual(MiffMember.phoneFromServer("+33612345678"), "+33612345678")
        XCTAssertNil(MiffMember.phoneFromServer("06 12 34 56 78"))
        XCTAssertNil(MiffMember.phoneFromServer("sms:+33612345678"))
        XCTAssertNil(MiffMember.phoneFromServer(12_345))
        XCTAssertNil(MiffMember.phoneFromServer(nil))
    }

    func testTheContentOfAStatusOrAPositionNeverShowsInADescription() {
        let status = feedStatus(.out, placeId: "p1")
        let content = buildFeed(feedInput(members: [feedMember("a", first: "Thomas"), feedMember("me")], statuses: ["a": status]))

        for text in ["\(status)", "\(content)", "\(content.cards)", "\(String(describing: content.bubble))"] {
            XCTAssertFalse(text.contains("Thomas"), text)
            XCTAssertFalse(text.contains("33612345678"), text)
            XCTAssertFalse(text.contains(testCode), text)
        }
    }
}
// swiftlint:enable line_length
