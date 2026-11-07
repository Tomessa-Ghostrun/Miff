import XCTest
@testable import Miff

/// La batterie rafraîchie à l'ouverture et à la sortie de l'app (décision du PM, 2026-11-07) : les déclencheurs et les limites
/// (15 minutes, 5 points, 3 heures). Mêmes scénarios qu'Android.
final class BatteryRefreshTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute
    private let hour = TrackingFixtures.hour

    /// Les écritures qui ne portent QUE la batterie (le rafraîchissement), à part des statuts d'arrivée et de départ.
    private func batteryWrites() -> [DocWrite] {
        let keys: Set<String> = ["batteryPct", "updatedAt", "deviceId"]
        return rig.store.statusWrites().filter { Set($0.data.keys) == keys }
    }

    /// Un passage : l'heure avance, le niveau de la batterie change, la personne est toujours chez elle.
    private func pass(_ reason: TrackingEngine.Reason, after elapsed: Int = 0, level: Int?) async {
        rig.env.now += elapsed
        rig.env.battery = level
        rig.putPositionHome()
        await rig.engine.refresh(reason)
    }

    // MARK: - Les déclencheurs

    func testOpeningTheAppWritesTheBatteryAlone() async throws {
        await pass(.opened, level: 80)

        let write = try XCTUnwrap(batteryWrites().first)
        XCTAssertEqual(batteryWrites().count, 1)
        XCTAssertEqual(write.path, "miffs/\(testMiffId)/status/uid-1")
        XCTAssertEqual(write.kind, .update)
        XCTAssertEqual(write.data["batteryPct"], .integer(80))
        XCTAssertEqual(write.data["updatedAt"], .serverTimestamp)
        XCTAssertEqual(write.data["deviceId"], .string(testDeviceId))
        let line = rig.journal.recorded.first { $0.kind == .batteryRefreshed }
        XCTAssertEqual(line?.batteryPct, 80)
        XCTAssertNil(line?.placeId)
    }

    func testLeavingTheAppWritesTheBatteryToo() async throws {
        await pass(.backgrounded, level: 64)

        let write = try XCTUnwrap(batteryWrites().first)
        XCTAssertEqual(write.data["batteryPct"], .integer(64))
        XCTAssertTrue(rig.journal.kinds.contains(.batteryRefreshed))
    }

    func testTheOtherOccasionsNeverRefreshTheBattery() async {
        let others: [TrackingEngine.Reason] = [.moved, .zoneEntered, .rearm, .sharingChanged, .accessChanged]
        for reason in others {
            await pass(reason, level: 80)
        }
        await rig.engine.onNetworkBack()

        XCTAssertTrue(batteryWrites().isEmpty)
    }

    func testEachMiffGetsItsOwnSingleDocumentWrite() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])

        await pass(.opened, level: 80)

        let paths = Set(batteryWrites().map { $0.path })
        XCTAssertEqual(paths, ["miffs/\(testMiffId)/status/uid-1", "miffs/\(testOtherMiffId)/status/uid-1"])
        XCTAssertTrue(rig.store.commits.allSatisfy { $0.count == 1 }, "un lot par Miff")
    }

    func testTheBatteryWriteCarriesNoPositionAndNothingOfThePresence() async throws {
        await pass(.opened, level: 80)

        let write = try XCTUnwrap(batteryWrites().first)
        for kept in ["presence", "placeId", "presenceAt", "lastUpdateAt", "updateText", "hasPhoto", "locationAccess"] {
            XCTAssertNil(write.data[kept], "\(kept) reste tel que la base l'a")
        }
        for forbidden in ["lat", "lng", "measuredAt", "address", "position"] {
            XCTAssertNil(write.data[forbidden], forbidden)
        }
    }

    // MARK: - Les limites

    func testNotBeforeFifteenMinutesWhateverTheLevel() async {
        await pass(.opened, level: 80)
        await pass(.backgrounded, after: 14 * minute, level: 50)
        XCTAssertEqual(batteryWrites().count, 1)

        await pass(.opened, after: 1 * minute, level: 50)

        XCTAssertEqual(batteryWrites().count, 2)
        XCTAssertEqual(batteryWrites().last?.data["batteryPct"], .integer(50))
    }

    func testNotUnderFivePointsFromTheLastValueWritten() async {
        await pass(.opened, level: 80)
        await pass(.backgrounded, after: 20 * minute, level: 77)
        await pass(.opened, after: 1 * minute, level: 76)
        XCTAssertEqual(batteryWrites().count, 1, "4 points : rien")
        XCTAssertTrue(rig.journal.kinds.contains(.batteryRefreshSkipped))

        await pass(.backgrounded, after: 1 * minute, level: 75)
        XCTAssertEqual(batteryWrites().count, 2, "5 points : écrit")

        await pass(.opened, after: 20 * minute, level: 80)
        XCTAssertEqual(batteryWrites().count, 3, "la recharge compte aussi")
    }

    func testTheSameLevelIsRewrittenOnlyAfterMoreThanThreeHours() async {
        await pass(.opened, level: 80)

        await pass(.backgrounded, after: 3 * hour, level: 80)
        XCTAssertEqual(batteryWrites().count, 1, "3 h pile : pas encore")

        await pass(.opened, after: 1 * minute, level: 80)
        XCTAssertEqual(batteryWrites().count, 2, "plus de 3 h : écrit, même niveau")
    }

    func testAnArrivalCarriesTheFreshBatteryAndCountsAsTheLastWrite() async throws {
        await pass(.opened, level: nil)
        rig.env.battery = 70

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        let arrival = try XCTUnwrap(rig.store.statusWrites().first)
        XCTAssertEqual(arrival.data["presence"], .string("in_trusted"))
        XCTAssertEqual(arrival.data["batteryPct"], .integer(70))
        await pass(.backgrounded, after: 5 * minute, level: 40)
        XCTAssertTrue(batteryWrites().isEmpty, "l'arrivée vient d'écrire la batterie : 15 minutes d'attente")

        await pass(.opened, after: 11 * minute, level: 40)

        XCTAssertEqual(batteryWrites().count, 1)
        XCTAssertEqual(batteryWrites().first?.data["batteryPct"], .integer(40))
    }

    func testAnUnknownBatteryWritesNothingAndNeverErasesTheKnownOne() async {
        await pass(.opened, level: nil)
        await pass(.backgrounded, after: 20 * minute, level: 130)

        XCTAssertTrue(batteryWrites().isEmpty)
        XCTAssertTrue(rig.store.statusWrites().isEmpty)
        XCTAssertTrue(rig.journal.kinds.contains(.batteryRefreshSkipped))
    }

    func testOfflineWritesNothingAndTheNextOccasionDoes() async {
        await pass(.opened, level: nil)
        rig.env.online = false
        await pass(.backgrounded, after: 1 * minute, level: 80)
        XCTAssertTrue(batteryWrites().isEmpty)

        rig.env.online = true
        await pass(.opened, after: 1 * minute, level: 80)

        XCTAssertEqual(batteryWrites().count, 1)
    }

    func testAFailedTryIsNotRepeatedBeforeFifteenMinutes() async {
        rig.position.fix = nil
        rig.store.commitFailure = firestoreError(FirestoreErrorNumber.permissionDenied)
        rig.env.battery = 80
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.store.commitAttempts, 1)

        rig.env.now += 5 * minute
        await rig.engine.refresh(.backgrounded)
        XCTAssertEqual(rig.store.commitAttempts, 1, "pas de boucle sur un refus")

        rig.env.now += 10 * minute
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.store.commitAttempts, 2)
        XCTAssertTrue(batteryWrites().isEmpty)
    }
}
