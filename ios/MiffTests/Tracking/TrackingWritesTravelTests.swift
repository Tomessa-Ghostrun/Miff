import XCTest
@testable import Miff

/// MODE VOYAGE, les écritures du statut (VOY-1I) : le contrat avec `backend/firestore.rules` (`validTravel`) et avec Android
/// (`TrackingWrites.kt`). Un voyage n'a ni lieu, ni batterie, ni échéance ; ses deux clés (fin, jour) vont ensemble ; une présence ordinaire
/// qui met fin à un voyage les efface dans la MÊME écriture ; chaque écriture porte l'identifiant de session.
final class TrackingWritesTravelTests: XCTestCase {
    private let uid = "uid-1"
    private let at = TrackingFixtures.noon
    private let period = TravelPeriod(untilMillis: TrackingFixtures.noon + 3 * 24 * 3_600_000, untilDay: "2026-11-05")

    func testTheStartOfATripSaysNoPlaceNoBatteryNoEscalationAndTheTwoKeysOfTheTrip() throws {
        let write = try XCTUnwrap(TrackingWrites.travelStart(
            miffId: testMiffId, uid: uid, period: period, presenceAtMillis: at, deviceId: testDeviceId
        ))

        XCTAssertEqual(write.path, "miffs/\(testMiffId)/status/\(uid)")
        // Un UPDATE : il ne crée jamais un statut.
        XCTAssertEqual(write.kind, .update)
        XCTAssertEqual(write.data["presence"], .string("travel"))
        XCTAssertEqual(write.data["placeId"], .null)
        XCTAssertEqual(write.data["batteryPct"], .null)
        XCTAssertEqual(write.data["escalationDueAt"], .null)
        XCTAssertEqual(write.data["presenceAt"], .timestamp(millis: at))
        XCTAssertEqual(write.data["travelUntil"], .timestamp(millis: period.untilMillis))
        XCTAssertEqual(write.data["travelUntilDay"], .string("2026-11-05"))
        XCTAssertEqual(write.data["updatedAt"], .serverTimestamp)
        XCTAssertEqual(write.data["deviceId"], .string(testDeviceId))
        // Ni la photo, ni le texte, ni l'accès, ni la dernière update ne sont touchés.
        XCTAssertEqual(
            Set(write.data.keys),
            [
                "presence", "placeId", "presenceAt", "batteryPct", "escalationDueAt", "travelUntil", "travelUntilDay",
                "updatedAt", "deviceId"
            ]
        )
    }

    func testChangingTheEndMovesOnlyTheTwoKeysOfTheTrip() throws {
        let write = try XCTUnwrap(TrackingWrites.travelDate(
            miffId: testMiffId, uid: uid, period: period, deviceId: testDeviceId
        ))

        XCTAssertEqual(write.kind, .update)
        XCTAssertEqual(Set(write.data.keys), ["travelUntil", "travelUntilDay", "updatedAt", "deviceId"])
        XCTAssertEqual(write.data["travelUntil"], .timestamp(millis: period.untilMillis))
        XCTAssertEqual(write.data["travelUntilDay"], .string("2026-11-05"))
    }

    func testAStatusThatEndsATripClearsItsTwoKeysInTheSameWriteAndAnotherOneDoesNotMentionThem() throws {
        let fact = PresenceMachine.unknown(atMillis: at)
        let clearing = StatusPayload(
            fact: fact, presenceAtMillis: at, batteryPct: 60, access: .background, clearTravel: true
        )
        let plain = StatusPayload(fact: fact, presenceAtMillis: at, batteryPct: 60, access: .background)

        let ending = try XCTUnwrap(
            TrackingWrites.statusUpdate(miffId: testMiffId, uid: uid, payload: clearing, deviceId: testDeviceId)
        )
        let ordinary = try XCTUnwrap(
            TrackingWrites.statusUpdate(miffId: testMiffId, uid: uid, payload: plain, deviceId: testDeviceId)
        )

        XCTAssertEqual(ending.data["presence"], .string("unknown"))
        XCTAssertEqual(ending.data["travelUntil"], .null)
        XCTAssertEqual(ending.data["travelUntilDay"], .null)
        XCTAssertNil(ordinary.data["travelUntil"])
        XCTAssertNil(ordinary.data["travelUntilDay"])
    }

    func testNothingIsBuiltWithoutAWellFormedIdentifierADayThatExistsOrACleanPath() {
        for deviceId in ["short", "bad id with spaces", ""] {
            XCTAssertNil(TrackingWrites.travelStart(
                miffId: testMiffId, uid: uid, period: period, presenceAtMillis: at, deviceId: deviceId
            ))
            XCTAssertNil(TrackingWrites.travelDate(miffId: testMiffId, uid: uid, period: period, deviceId: deviceId))
        }
        XCTAssertNil(TrackingWrites.travelStart(
            miffId: "not-a-miff", uid: uid, period: period, presenceAtMillis: at, deviceId: testDeviceId
        ))
        XCTAssertNil(TrackingWrites.travelDate(miffId: testMiffId, uid: "a/b", period: period, deviceId: testDeviceId))
        let garbage = TravelPeriod(untilMillis: period.untilMillis, untilDay: "demain")
        XCTAssertNil(TrackingWrites.travelDate(miffId: testMiffId, uid: uid, period: garbage, deviceId: testDeviceId))
        XCTAssertNil(TrackingWrites.travelStart(
            miffId: testMiffId, uid: uid, period: garbage, presenceAtMillis: at, deviceId: testDeviceId
        ))
    }

    func testBothWritesCarryTheDeviceIdentifierAndPassTheGuard() throws {
        let writes = try [
            XCTUnwrap(TrackingWrites.travelStart(
                miffId: testMiffId, uid: uid, period: period, presenceAtMillis: at, deviceId: testDeviceId
            )),
            XCTUnwrap(TrackingWrites.travelDate(miffId: testMiffId, uid: uid, period: period, deviceId: testDeviceId))
        ]

        for write in writes {
            XCTAssertTrue(SessionWriteGuard.requiresDeviceId(write), write.path)
            XCTAssertTrue(SessionWriteGuard.carriesDeviceId(write), write.path)
        }
        XCTAssertTrue(SessionWriteGuard.isSendable(writes))
    }
}
