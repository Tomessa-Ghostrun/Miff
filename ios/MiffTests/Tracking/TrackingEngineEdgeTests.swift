import XCTest
@testable import Miff

/// Les cas limites du moteur de suivi (LOC-4b) : hors connexion, disjoncteurs, garde-fou de fréquence, session remplacée, arrêt,
/// autorisation retirée, lieu modifié, 20 zones, retour du réseau. Mêmes scénarios qu'Android (LOC-2b).
final class TrackingEngineEdgeTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute
    private let hour = TrackingFixtures.hour

    private func millis(day: Int, hour: Int, minute: Int) -> Int {
        let parts = DateComponents(year: 2026, month: 11, day: day, hour: hour, minute: minute)
        let date = Calendar.current.date(from: parts) ?? Date(timeIntervalSince1970: 0)
        return Int(date.timeIntervalSince1970 * 1000)
    }

    private var pending: PendingStatus? {
        rig.local.state?.miffs[testMiffId]?.pending
    }

    // MARK: - Hors connexion (HOR1, STA3)

    func testOfflineKeepsTheLatestStateAndWritesItAtTheReturnWithTheRealTime() async throws {
        await rig.engine.refresh(.opened)
        rig.env.online = false
        let eventTime = rig.env.now - 30 * 1000

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: eventTime)

        XCTAssertTrue(rig.store.statusWrites().isEmpty)
        XCTAssertEqual(pending?.presence, "in_trusted")
        XCTAssertEqual(pending?.atMillis, eventTime)
        rig.env.online = true
        rig.env.now += hour
        await rig.engine.onNetworkBack()
        let write = try XCTUnwrap(rig.store.statusWrites().first)
        XCTAssertEqual(write.data["presenceAt"], .timestamp(millis: eventTime), "l'heure RÉELLE, pas celle du retour")
        XCTAssertNil(pending)
    }

    func testTheLatestOfSeveralOfflineStatesWins() async throws {
        await rig.engine.refresh(.opened)
        rig.env.online = false
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - 120 * 1000)
        let leaving = rig.env.now - 20 * 1000
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: leaving)

        rig.env.online = true
        await rig.engine.onNetworkBack()

        let writes = rig.store.statusWrites()
        XCTAssertEqual(writes.count, 1)
        XCTAssertEqual(writes.first?.data["presence"], .string("out"))
        XCTAssertEqual(writes.first?.data["presenceAt"], .timestamp(millis: leaving))
    }

    func testAnEventOlderThanTwentyFourHoursIsReplacedByUnknownAtTheTimeOfTheMoment() async throws {
        await rig.engine.refresh(.opened)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - 25 * hour)

        let writes = rig.store.statusWrites()
        XCTAssertEqual(writes.count, 1, "jamais l'état périmé, jamais de boucle")
        let write = try XCTUnwrap(writes.first)
        XCTAssertEqual(write.data["presence"], .string("unknown"))
        XCTAssertEqual(write.data["placeId"], .null)
        XCTAssertEqual(write.data["presenceAt"], .timestamp(millis: rig.env.now))
        XCTAssertTrue(rig.journal.kinds.contains(.statusGivenUp))
        XCTAssertNil(pending)
    }

    func testAStatusThatWaitedMoreThanTwentyFourHoursIsReplacedByUnknownNotKeptStale() async throws {
        await rig.engine.refresh(.opened)
        rig.env.online = false
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        rig.env.online = true
        rig.env.now += 25 * hour

        await rig.engine.onNetworkBack()

        let write = try XCTUnwrap(rig.store.statusWrites().first)
        XCTAssertEqual(write.data["presence"], .string("unknown"))
        XCTAssertEqual(write.data["presenceAt"], .timestamp(millis: rig.env.now))
        XCTAssertEqual(rig.local.state?.miffs[testMiffId]?.presence, "unknown")
    }

    func testTheEventTimeIsCappedAtTheClockOfThePhone() async throws {
        await rig.engine.refresh(.opened)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now + 3 * hour)

        let write = try XCTUnwrap(rig.store.statusWrites().first)
        XCTAssertEqual(write.data["presenceAt"], .timestamp(millis: rig.env.now))
    }

    func testTheReturnOfTheNetworkChecksTheConditionsAgainBeforeWriting() async {
        await rig.engine.refresh(.opened)
        rig.env.online = false
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        rig.env.online = true
        // Pendant la coupure, le texte accepté a été remplacé par un ancien (ou le partage arrêté ailleurs).
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: true, version: "2026-10-v2")

        await rig.engine.onNetworkBack()

        XCTAssertTrue(rig.store.statusWrites().isEmpty)
        XCTAssertTrue(rig.local.state?.miffs.isEmpty == true)
    }

    func testTheReturnOfTheNetworkWritesNothingWhenAlwaysWasWithdrawnAndErasesWhatWaited() async {
        await rig.engine.refresh(.opened)
        rig.env.online = false
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        rig.env.online = true
        rig.env.snapshot = PermissionSnapshot(location: .whenInUse, notifications: .authorized)

        await rig.engine.onNetworkBack()

        XCTAssertTrue(rig.store.statusWrites().isEmpty)
        XCTAssertNil(pending)
        XCTAssertNil(rig.local.state?.fix)
    }

    func testWithoutAnswerFromTheServerTheStatusKeepsWaitingAndNothingIsAssumed() async {
        await rig.engine.refresh(.opened)
        rig.env.online = false
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        rig.env.online = true
        rig.store.membershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)

        await rig.engine.onNetworkBack()

        XCTAssertTrue(rig.store.statusWrites().isEmpty)
        XCTAssertNotNil(pending)
    }

    func testNetworkBackWithNothingWaitingDoesNothing() async {
        await rig.engine.refresh(.opened)
        let reads = rig.store.readCount

        await rig.engine.onNetworkBack()

        XCTAssertEqual(rig.store.readCount, reads)
    }

    // MARK: - Disjoncteurs et garde-fou de fréquence (STA4, POS8)

    func testTheStatusBreakerKeepsTheLastStateWaitingAndItGoesAfterMidnight() async throws {
        rig.env.now = millis(day: 3, hour: 23, minute: 50)
        rig.putPositionHome()
        await rig.engine.refresh(.opened)
        var state = try XCTUnwrap(rig.local.state)
        state.breaker = BreakerState(
            dayKey: WriteBreaker.dayKey(millis: rig.env.now),
            statusWrites: TrackingLimits.maxStatusWritesPerDay,
            positionWrites: 0
        )
        rig.local.state = state
        let eventTime = rig.env.now

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: eventTime)

        XCTAssertTrue(rig.store.statusWrites().isEmpty)
        XCTAssertTrue(rig.journal.kinds.contains(.breakerStatus))
        XCTAssertNotNil(pending)
        rig.env.now = millis(day: 4, hour: 0, minute: 10)
        rig.putPositionHome()
        await rig.engine.refresh(.backgrounded)
        let write = try XCTUnwrap(rig.store.statusWrites().first)
        XCTAssertEqual(write.data["presenceAt"], .timestamp(millis: eventTime))
    }

    func testThePositionBreakerStopsPositionsAtOneHundredAndFifty() async throws {
        await rig.engine.refresh(.backgrounded)
        var state = try XCTUnwrap(rig.local.state)
        state.breaker = BreakerState(
            dayKey: WriteBreaker.dayKey(millis: rig.env.now),
            statusWrites: 0,
            positionWrites: TrackingLimits.maxPositionWritesPerDay
        )
        state.update(testMiffId) { $0.written = nil }
        rig.local.state = state
        let before = rig.store.positionWrites().count
        rig.env.now += 20 * minute
        rig.putPositionHome()

        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.store.positionWrites().count, before)
        XCTAssertTrue(rig.journal.kinds.contains(.breakerPosition))
    }

    func testThePositionGuardWritesAtMostEveryFifteenMinutesUnlessThePersonMoved() async {
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.store.positionWrites().count, 1)

        rig.env.now += 1 * minute
        rig.putPositionHome()
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.store.positionWrites().count, 1, "jamais deux fois en moins de deux minutes")

        rig.env.now += 4 * minute
        rig.putPositionHome()
        await rig.engine.refresh(.backgrounded)
        XCTAssertEqual(rig.store.positionWrites().count, 1, "même endroit, moins de quinze minutes")
        XCTAssertTrue(rig.journal.kinds.contains(.positionSkippedGuard))

        rig.env.now += 1 * minute
        rig.position.fix = TrackingFixtures.fixFar(at: rig.env.now)
        await rig.engine.refresh(.moved)
        XCTAssertEqual(rig.store.positionWrites().count, 2, "déplacement de plus de 250 m")

        rig.env.now += 16 * minute
        rig.position.fix = TrackingFixtures.fixFar(at: rig.env.now)
        await rig.engine.refresh(.backgrounded)
        XCTAssertEqual(rig.store.positionWrites().count, 3, "quinze minutes se sont écoulées")
    }
}
