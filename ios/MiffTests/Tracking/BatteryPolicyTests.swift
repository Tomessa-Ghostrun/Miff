import XCTest
@testable import Miff

/// La règle de la batterie rafraîchie à l'ouverture et à la sortie (décision du PM, 2026-11-07) : 15 minutes, 5 points, 3 heures,
/// disjoncteur dédié. Du Swift pur. Mêmes valeurs qu'Android.
final class BatteryPolicyTests: XCTestCase {
    private let now = TrackingFixtures.noon
    private let minute = TrackingFixtures.minute
    private let hour = TrackingFixtures.hour

    private func history(_ pct: Int?, writtenAgo: Int?, triedAgo: Int? = nil) -> BatteryHistory {
        BatteryHistory(
            pct: pct,
            writtenAtMillis: writtenAgo.map { now - $0 },
            triedAtMillis: triedAgo.map { now - $0 }
        )
    }

    private func decide(_ history: BatteryHistory, _ current: Int?) -> BatteryDecision {
        BatteryRefreshPolicy.decide(history: history, current: current, nowMillis: now)
    }

    func testTheValuesAreTheOnesOfTheDecision() {
        XCTAssertEqual(TrackingLimits.batteryMinIntervalMillis, 15 * minute)
        XCTAssertEqual(TrackingLimits.batteryMinDeltaPoints, 5)
        XCTAssertEqual(TrackingLimits.batteryMaxAgeMillis, 3 * hour)
        XCTAssertEqual(TrackingLimits.maxBatteryWritesPerDay, 40)
    }

    func testNothingWrittenYetMeansWrite() {
        XCTAssertEqual(decide(BatteryHistory(), 80), .write)
    }

    func testAnUnknownOrImpossibleLevelIsNeverWritten() {
        XCTAssertEqual(decide(BatteryHistory(), nil), .skip(.unknown))
        XCTAssertEqual(decide(BatteryHistory(), 101), .skip(.unknown))
        XCTAssertEqual(decide(BatteryHistory(), -1), .skip(.unknown))
        XCTAssertEqual(decide(BatteryHistory(), 0), .write)
        XCTAssertEqual(decide(BatteryHistory(), 100), .write)
    }

    func testNeverBeforeFifteenMinutesWhateverTheLevel() {
        XCTAssertEqual(decide(history(80, writtenAgo: 14 * minute), 10), .skip(.tooSoon))
        XCTAssertEqual(decide(history(80, writtenAgo: 15 * minute), 10), .write)
    }

    func testAFailedTryAlsoWaitsFifteenMinutes() {
        XCTAssertEqual(decide(history(nil, writtenAgo: nil, triedAgo: 5 * minute), 80), .skip(.tooSoon))
        XCTAssertEqual(decide(history(nil, writtenAgo: nil, triedAgo: 15 * minute), 80), .write)
    }

    func testBelowFiveDecreasingOrIncreasingPointsNothingIsWritten() {
        XCTAssertEqual(decide(history(80, writtenAgo: 30 * minute), 76), .skip(.unchanged))
        XCTAssertEqual(decide(history(80, writtenAgo: 30 * minute), 84), .skip(.unchanged))
        XCTAssertEqual(decide(history(80, writtenAgo: 30 * minute), 80), .skip(.unchanged))
    }

    func testFivePointsOrMoreAreWrittenInBothDirections() {
        XCTAssertEqual(decide(history(80, writtenAgo: 30 * minute), 75), .write)
        XCTAssertEqual(decide(history(80, writtenAgo: 30 * minute), 85), .write)
    }

    func testTheSameLevelIsWrittenOnlyAfterMoreThanThreeHours() {
        XCTAssertEqual(decide(history(80, writtenAgo: 3 * hour), 80), .skip(.unchanged))
        XCTAssertEqual(decide(history(80, writtenAgo: 3 * hour + 1), 80), .write)
        XCTAssertEqual(decide(history(80, writtenAgo: 5 * hour), 80), .write)
    }

    func testAnUnknownPreviousLevelIsWrittenAfterFifteenMinutes() {
        XCTAssertEqual(decide(history(nil, writtenAgo: 20 * minute), 80), .write)
    }

    func testAClockThatWentBackWrites() {
        let ahead = BatteryHistory(pct: 80, writtenAtMillis: now + hour, triedAtMillis: nil)

        XCTAssertEqual(decide(ahead, 80), .write)
    }

    func testTheBreakerHasItsOwnCounterOfFortyAndStartsOverTheNextDay() {
        let today = WriteBreaker.dayKey(millis: now)
        let tomorrow = WriteBreaker.dayKey(millis: now + 24 * hour)
        var state: BreakerState?
        for _ in 0..<39 {
            state = WriteBreaker.recordBattery(state, dayKey: today)
        }
        XCTAssertTrue(WriteBreaker.canWriteBattery(state, dayKey: today))
        state = WriteBreaker.recordBattery(state, dayKey: today)

        XCTAssertFalse(WriteBreaker.canWriteBattery(state, dayKey: today))
        XCTAssertTrue(WriteBreaker.canWriteBattery(state, dayKey: tomorrow))
        XCTAssertTrue(WriteBreaker.canWriteStatus(state, dayKey: today), "compteur des statuts à part")
        XCTAssertEqual(state?.statusWrites, 0)
    }

    func testACopyWrittenBeforeThisFeatureStillLoads() throws {
        let json = #"{"dayKey":"2026-11-3","statusWrites":2,"positionWrites":5}"#

        let state = try JSONDecoder().decode(BreakerState.self, from: Data(json.utf8))

        XCTAssertNil(state.batteryWrites)
        XCTAssertTrue(WriteBreaker.canWriteBattery(state, dayKey: "2026-11-3"))
    }

    func testTheBatteryUpdateCarriesOnlyTheLevelTheTimeAndTheSession() throws {
        let write = try XCTUnwrap(TrackingWrites.batteryUpdate(
            miffId: testMiffId, uid: "uid-1", batteryPct: 42, deviceId: testDeviceId
        ))

        XCTAssertEqual(write.kind, .update)
        XCTAssertEqual(write.path, "miffs/\(testMiffId)/status/uid-1")
        XCTAssertEqual(Set(write.data.keys), ["batteryPct", "updatedAt", "deviceId"])
        XCTAssertEqual(write.data["batteryPct"], .integer(42))
        XCTAssertEqual(write.data["updatedAt"], .serverTimestamp)
        XCTAssertTrue(SessionWriteGuard.carriesDeviceId(write))
    }

    func testTheBatteryUpdateIsNotBuiltForAnImpossibleLevelOrABadIdentifier() {
        XCTAssertNil(build(level: 101))
        XCTAssertNil(build(level: -1))
        XCTAssertNil(build(device: "x"))
        XCTAssertNil(build(miff: "../x"))
        XCTAssertNil(build(uid: "a/b"))
        XCTAssertNotNil(build())
    }

    private func build(
        miff: String = testMiffId,
        uid: String = "uid-1",
        level: Int = 50,
        device: String = testDeviceId
    ) -> DocWrite? {
        TrackingWrites.batteryUpdate(miffId: miff, uid: uid, batteryPct: level, deviceId: device)
    }
}
