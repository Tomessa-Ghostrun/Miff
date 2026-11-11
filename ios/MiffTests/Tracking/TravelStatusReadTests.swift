import FirebaseFirestore
import XCTest
@testable import Miff

/// MODE VOYAGE : ce qu'un nouvel appareil lit de MON statut pour adopter un voyage. Le document a pu être écrit par un client modifié :
/// ce qui n'est pas un voyage que l'on puisse garder (pas de fin, jour qui n'existe pas) n'est pas gardé. Du Swift pur : aucun réseau.
final class TravelStatusReadTests: XCTestCase {
    private func timestamp(_ millis: Int) -> Timestamp {
        Timestamp(date: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    private let until = 1_795_000_000_000
    private let since = 1_794_000_000_000

    private func fields(
        presence: String = "travel",
        day: Any? = "2026-11-15",
        end: Any? = nil
    ) -> [String: Any] {
        var data: [String: Any] = ["presence": presence, "presenceAt": timestamp(since)]
        data["travelUntil"] = end ?? timestamp(until)
        if let day {
            data["travelUntilDay"] = day
        }
        return data
    }

    func testATripIsReadWithItsEndItsDayAndItsStart() {
        let read = FirestoreMiffStore.travel(fromStatus: fields())

        XCTAssertEqual(read?.period, TravelPeriod(untilMillis: until, untilDay: "2026-11-15"))
        XCTAssertEqual(read?.sinceMillis, since)
    }

    func testAStatusThatIsNotATripIsNotOne() {
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: fields(presence: "out")))
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: fields(presence: "unknown")))
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: [:]))
    }

    func testATripWithoutAUsableEndOrDayIsNotKept() {
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: fields(day: nil)))
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: fields(day: "2026-02-30")))
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: fields(day: "demain")))
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: fields(day: 20_261_115)))
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: fields(end: "bientôt")))
        var noEnd = fields()
        noEnd["travelUntil"] = nil
        XCTAssertNil(FirestoreMiffStore.travel(fromStatus: noEnd))
    }

    func testTheStartIsFacultative() {
        var data = fields()
        data["presenceAt"] = nil

        let read = FirestoreMiffStore.travel(fromStatus: data)

        XCTAssertNotNil(read)
        XCTAssertNil(read?.sinceMillis)
    }
}
