import XCTest
@testable import Miff

/// Les limites des écritures automatiques (critères POS8, STA3, STA4) : l'heure d'un événement, le disjoncteur, le garde-fou de fréquence.
final class TrackingLimitsTests: XCTestCase {
    private let now = TrackingFixtures.noon
    private let minute = TrackingFixtures.minute
    private let hour = TrackingFixtures.hour

    func testTheValuesAreTheOnesOfAndroid() {
        XCTAssertEqual(TrackingLimits.maxStatusWritesPerDay, 60)
        XCTAssertEqual(TrackingLimits.maxPositionWritesPerDay, 150)
        XCTAssertEqual(TrackingLimits.positionMinIntervalMillis, 15 * minute)
        XCTAssertEqual(TrackingLimits.positionMinDistanceMeters, 250)
        XCTAssertEqual(TrackingLimits.positionFloorIntervalMillis, 2 * minute)
        XCTAssertEqual(TrackingLimits.fixMaxAgeMillis, 10 * minute)
    }

    func testTheEventTimeIsCappedAtTheClockOfThePhone() {
        XCTAssertEqual(EventTime.forWrite(eventMillis: now - 5 * minute, nowMillis: now), now - 5 * minute)
        XCTAssertEqual(EventTime.forWrite(eventMillis: now + 3 * hour, nowMillis: now), now)
    }

    func testAnEventOfMoreThanTwentyFourHoursIsGivenUp() {
        XCTAssertNotNil(EventTime.forWrite(eventMillis: now - 23 * hour, nowMillis: now))
        XCTAssertNotNil(EventTime.forWrite(eventMillis: now - 24 * hour + 6 * minute, nowMillis: now))
        XCTAssertNil(EventTime.forWrite(eventMillis: now - 24 * hour + 4 * minute, nowMillis: now))
        XCTAssertNil(EventTime.forWrite(eventMillis: now - 30 * hour, nowMillis: now))
    }

    func testTheBreakerStopsStatusesAtSixtyAndPositionsAtOneHundredAndFifty() {
        let day = WriteBreaker.dayKey(millis: now)
        var state: BreakerState?
        for _ in 0..<59 {
            state = WriteBreaker.recordStatus(state, dayKey: day)
        }
        XCTAssertTrue(WriteBreaker.canWriteStatus(state, dayKey: day))
        state = WriteBreaker.recordStatus(state, dayKey: day)
        XCTAssertFalse(WriteBreaker.canWriteStatus(state, dayKey: day))
        // Les positions ont leur propre compteur.
        XCTAssertTrue(WriteBreaker.canWritePosition(state, dayKey: day))
        for _ in 0..<150 {
            state = WriteBreaker.recordPosition(state, dayKey: day)
        }
        XCTAssertFalse(WriteBreaker.canWritePosition(state, dayKey: day))
    }

    func testTheBreakerStartsOverTheNextDayOfThePhone() {
        let today = WriteBreaker.dayKey(millis: now)
        let tomorrow = WriteBreaker.dayKey(millis: now + 24 * hour)
        var state: BreakerState?
        for _ in 0..<60 {
            state = WriteBreaker.recordStatus(state, dayKey: today)
        }

        XCTAssertNotEqual(today, tomorrow)
        XCTAssertFalse(WriteBreaker.canWriteStatus(state, dayKey: today))
        XCTAssertTrue(WriteBreaker.canWriteStatus(state, dayKey: tomorrow))
        XCTAssertEqual(WriteBreaker.recordStatus(state, dayKey: tomorrow).statusWrites, 1)
    }

    func testThePositionIsWrittenWhenNothingWasWrittenYet() {
        XCTAssertTrue(PositionThrottle.shouldWrite(last: nil, lat: 48.0, lng: 2.0, nowMillis: now))
    }

    func testNeverTwiceInLessThanTwoMinutesEvenIfFarAway() {
        let last = WrittenPosition(lat: 48.0, lng: 2.0, atMillis: now)

        XCTAssertFalse(PositionThrottle.shouldWrite(last: last, lat: 49.0, lng: 2.0, nowMillis: now + minute))
    }

    func testAfterTwoMinutesOnlyIfMovedTwoHundredAndFiftyMetresOrAfterFifteenMinutes() {
        let last = WrittenPosition(lat: 48.0, lng: 2.0, atMillis: now)
        let near = 48.0 + 100 / 111_000.0
        let far = 48.0 + 300 / 111_000.0

        XCTAssertFalse(PositionThrottle.shouldWrite(last: last, lat: near, lng: 2.0, nowMillis: now + 5 * minute))
        XCTAssertTrue(PositionThrottle.shouldWrite(last: last, lat: far, lng: 2.0, nowMillis: now + 5 * minute))
        XCTAssertTrue(PositionThrottle.shouldWrite(last: last, lat: near, lng: 2.0, nowMillis: now + 15 * minute))
        XCTAssertFalse(PositionThrottle.shouldWrite(last: last, lat: near, lng: 2.0, nowMillis: now + 14 * minute))
    }

    func testAClockThatWentBackWritesAnyway() {
        let last = WrittenPosition(lat: 48.0, lng: 2.0, atMillis: now)

        XCTAssertTrue(PositionThrottle.shouldWrite(last: last, lat: 48.0, lng: 2.0, nowMillis: now - hour))
    }
}

/// Les écritures du suivi sont le contrat avec les règles Firestore : statut sans coordonnée, dernière position à 5 décimales seule,
/// `deviceId` dans CHAQUE écriture, mises à jour partielles comprises (SESS-1, contrat I-1).
final class TrackingWritesTests: XCTestCase {
    private let uid = "uid-1"
    private let at = TrackingFixtures.noon

    private func payload(_ fact: StatusFact, battery: Int? = 61) -> StatusPayload {
        StatusPayload(fact: fact, presenceAtMillis: fact.presenceAtMillis, batteryPct: battery, access: .background)
    }

    private func status(_ fact: StatusFact, battery: Int? = 61) throws -> DocWrite {
        try XCTUnwrap(TrackingWrites.statusUpdate(
            miffId: testMiffId,
            uid: uid,
            payload: payload(fact, battery: battery),
            deviceId: testDeviceId
        ))
    }

    func testAnArrivalInATrustedZoneWritesTheFactsAndMovesTheLastUpdate() throws {
        let arrival = StatusFact(presence: .inTrusted, placeId: "p1", presenceAtMillis: at, setLastUpdate: true)
        let write = try status(arrival)

        XCTAssertEqual(write.path, "miffs/\(testMiffId)/status/\(uid)")
        XCTAssertEqual(write.kind, .update)
        XCTAssertEqual(write.data["presence"], .string("in_trusted"))
        XCTAssertEqual(write.data["placeId"], .string("p1"))
        XCTAssertEqual(write.data["presenceAt"], .timestamp(millis: at))
        XCTAssertEqual(write.data["lastUpdateAt"], .timestamp(millis: at))
        XCTAssertEqual(write.data["batteryPct"], .integer(61))
        XCTAssertEqual(write.data["locationAccess"], .string("background"))
        XCTAssertEqual(write.data["escalationDueAt"], .null)
        XCTAssertEqual(write.data["updatedAt"], .serverTimestamp)
    }

    func testADepartureNeverSendsTheLastUpdateNorTheTextNorThePhotoFlag() throws {
        let write = try status(StatusFact(presence: .out, placeId: "p1", presenceAtMillis: at, setLastUpdate: false))

        XCTAssertNil(write.data["lastUpdateAt"])
        XCTAssertNil(write.data["updateText"])
        XCTAssertNil(write.data["hasPhoto"])
        let expected: Set<String> = [
            "presence", "placeId", "presenceAt", "batteryPct", "locationAccess", "escalationDueAt", "updatedAt",
            "deviceId"
        ]
        XCTAssertEqual(Set(write.data.keys), expected)
    }

    func testAStatusCarriesNoCoordinateAndNoAddress() throws {
        let writes = try [
            status(StatusFact(presence: .inTrusted, placeId: "p1", presenceAtMillis: at, setLastUpdate: true)),
            status(StatusFact(presence: .unknown, placeId: nil, presenceAtMillis: at, setLastUpdate: false))
        ]
        let keys = Set(writes.flatMap { $0.data.keys })

        for forbidden in ["lat", "lng", "latitude", "longitude", "address", "coordinate", "position", "measuredAt"] {
            XCTAssertFalse(keys.contains(forbidden), forbidden)
        }
        let unknown = StatusFact(presence: .unknown, placeId: nil, presenceAtMillis: at, setLastUpdate: false)
        XCTAssertEqual(try status(unknown).data["placeId"], .null)
    }

    func testTheBatteryIsNullWhenUnknownOrOutOfRange() throws {
        let fact = StatusFact(presence: .out, placeId: nil, presenceAtMillis: at, setLastUpdate: false)

        XCTAssertEqual(try status(fact, battery: nil).data["batteryPct"], .null)
        XCTAssertEqual(try status(fact, battery: 101).data["batteryPct"], .null)
        XCTAssertEqual(try status(fact, battery: -1).data["batteryPct"], .null)
        XCTAssertEqual(try status(fact, battery: 0).data["batteryPct"], .integer(0))
    }

    func testTheAccessUpdateTouchesNothingButTheBatteryAndTheAccess() throws {
        let write = try XCTUnwrap(TrackingWrites.accessUpdate(
            miffId: testMiffId,
            uid: uid,
            batteryPct: 40,
            access: .foreground,
            deviceId: testDeviceId
        ))

        XCTAssertEqual(write.kind, .update)
        XCTAssertEqual(Set(write.data.keys), ["batteryPct", "locationAccess", "updatedAt", "deviceId"])
        XCTAssertEqual(write.data["locationAccess"], .string("foreground"))
    }

    func testThePositionIsTheOnlyDocumentThatCarriesCoordinatesWithFiveDecimalsAndTheMeasureTime() throws {
        let point = WrittenPosition(lat: 48.856614, lng: 2.352228, atMillis: at - 4_000)

        let built = TrackingWrites.position(miffId: testMiffId, uid: uid, point: point, deviceId: testDeviceId)
        let write = try XCTUnwrap(built)

        XCTAssertEqual(write.path, "miffs/\(testMiffId)/positions/\(uid)")
        XCTAssertEqual(write.kind, .set)
        XCTAssertEqual(write.data["lat"], .double(48.85661))
        XCTAssertEqual(write.data["lng"], .double(2.35223))
        XCTAssertEqual(write.data["measuredAt"], .timestamp(millis: at - 4_000))
        XCTAssertEqual(Set(write.data.keys), ["lat", "lng", "measuredAt", "deviceId"])
    }

    func testEveryStatusAndPositionWriteCarriesTheDeviceIdentifierAndPassesTheGuard() throws {
        let writes = try [
            status(StatusFact(presence: .out, placeId: "p1", presenceAtMillis: at, setLastUpdate: false)),
            XCTUnwrap(TrackingWrites.accessUpdate(
                miffId: testMiffId, uid: uid, batteryPct: nil, access: .denied, deviceId: testDeviceId
            )),
            XCTUnwrap(TrackingWrites.position(
                miffId: testMiffId,
                uid: uid,
                point: WrittenPosition(lat: 1, lng: 2, atMillis: at),
                deviceId: testDeviceId
            ))
        ]

        for write in writes {
            XCTAssertEqual(write.data["deviceId"], .string(testDeviceId), write.path)
            XCTAssertTrue(SessionWriteGuard.requiresDeviceId(write), write.path)
            XCTAssertTrue(SessionWriteGuard.carriesDeviceId(write), write.path)
        }
        XCTAssertTrue(SessionWriteGuard.isSendable(writes))
    }

    func testNothingIsBuiltWithoutAWellFormedIdentifierOrWithABadPath() {
        let fact = StatusFact(presence: .out, placeId: nil, presenceAtMillis: at, setLastUpdate: false)
        let point = WrittenPosition(lat: 1, lng: 2, atMillis: at)

        let body = payload(fact)

        for deviceId in ["short", "bad id with spaces", ""] {
            XCTAssertNil(TrackingWrites.statusUpdate(miffId: testMiffId, uid: uid, payload: body, deviceId: deviceId))
            XCTAssertNil(TrackingWrites.accessUpdate(
                miffId: testMiffId, uid: uid, batteryPct: 1, access: .denied, deviceId: deviceId
            ))
            XCTAssertNil(TrackingWrites.position(miffId: testMiffId, uid: uid, point: point, deviceId: deviceId))
        }
        XCTAssertNil(TrackingWrites.statusUpdate(miffId: "not-a-miff", uid: uid, payload: body, deviceId: testDeviceId))
        XCTAssertNil(TrackingWrites.statusUpdate(miffId: testMiffId, uid: "a/b", payload: body, deviceId: testDeviceId))
    }

    func testAPositionOutOfRangeIsNeverBuilt() {
        for point in [
            WrittenPosition(lat: 91, lng: 0, atMillis: at),
            WrittenPosition(lat: 0, lng: 181, atMillis: at),
            WrittenPosition(lat: .nan, lng: 0, atMillis: at),
            WrittenPosition(lat: 0, lng: .infinity, atMillis: at)
        ] {
            XCTAssertNil(TrackingWrites.position(miffId: testMiffId, uid: uid, point: point, deviceId: testDeviceId))
        }
    }
}

final class LocationAccessRuleTests: XCTestCase {
    private func snapshot(
        _ location: LocationAuthorization,
        services: Bool = true,
        precise: Bool = true
    ) -> PermissionSnapshot {
        PermissionSnapshot(
            location: location,
            servicesEnabled: services,
            preciseLocation: precise,
            notifications: .authorized
        )
    }

    func testTheFourValuesOfTheStatus() {
        XCTAssertEqual(LocationAccessRule.of(snapshot(.always)), .background)
        XCTAssertEqual(LocationAccessRule.of(snapshot(.always, precise: false)), .foreground)
        XCTAssertEqual(LocationAccessRule.of(snapshot(.whenInUse)), .foreground)
        XCTAssertEqual(LocationAccessRule.of(snapshot(.denied)), .denied)
        XCTAssertEqual(LocationAccessRule.of(snapshot(.restricted)), .denied)
        XCTAssertEqual(LocationAccessRule.of(snapshot(.notDetermined)), .unknown)
    }

    func testTheLocationOfTheWholePhoneBeingOffIsDeniedWhateverTheAuthorization() {
        for authorization in [LocationAuthorization.always, .whenInUse, .notDetermined, .denied] {
            XCTAssertEqual(LocationAccessRule.of(snapshot(authorization, services: false)), .denied)
        }
    }

    func testItAgreesWithTheValueTheFirstStatusWritesAtTheEntryOfAMiff() {
        for authorization in [LocationAuthorization.always, .whenInUse, .notDetermined, .denied, .restricted] {
            for precise in [true, false] {
                XCTAssertEqual(
                    LocationAccessRule.of(snapshot(authorization, precise: precise)),
                    SystemPermissionMapping.locationAccess(location: authorization, precise: precise),
                    "\(authorization) \(precise)"
                )
            }
        }
    }

    func testTheBackgroundValueMeansExactlyTheCompleteLocation() {
        for authorization in [LocationAuthorization.always, .whenInUse, .notDetermined, .denied] {
            for precise in [true, false] {
                let current = snapshot(authorization, precise: precise)
                XCTAssertEqual(LocationAccessRule.of(current) == .background, current.locationComplete)
            }
        }
    }
}
