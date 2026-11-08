import XCTest
@testable import Miff

/// Des scénarios clés du moteur, rejoués avec une batterie connue (80) : le rafraîchissement de l'ouverture ne doit rien déborder
/// (file hors ligne, refus, arrêt de session). Chacun ajoute au plus UNE écriture de batterie, à l'ouverture.
final class BatteryRefreshScenarioTests: XCTestCase {
    private let rig = TrackingRig()
    private let hour = TrackingFixtures.hour

    private func batteryWrites() -> [DocWrite] {
        let keys: Set<String> = ["batteryPct", "updatedAt", "deviceId"]
        return rig.store.statusWrites().filter { Set($0.data.keys) == keys }
    }

    private func presenceWrites() -> [DocWrite] {
        rig.store.statusWrites().filter { $0.data["presence"] != nil }
    }

    func testTheOfflineQueueStillWritesOnlyTheLatestStateAtTheReturn() async throws {
        rig.env.battery = 80
        await rig.engine.refresh(.opened)
        rig.env.online = false
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - 120 * 1000)
        let leaving = rig.env.now - 20 * 1000
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: leaving)

        rig.env.online = true
        rig.env.now += hour
        await rig.engine.onNetworkBack()

        XCTAssertEqual(batteryWrites().count, 1, "le retour du réseau ne rafraîchit pas la batterie")
        let writes = presenceWrites()
        XCTAssertEqual(writes.count, 1)
        XCTAssertEqual(writes.first?.data["presence"], .string("out"))
        XCTAssertEqual(writes.first?.data["presenceAt"], .timestamp(millis: leaving))
        XCTAssertEqual(rig.store.statusWrites().count, 2)
        XCTAssertNil(rig.local.state?.miffs[testMiffId]?.pending)
    }

    func testARefusedArrivalIsStillNotRetriedAndTheBatteryDoesNotSneakIn() async {
        rig.env.battery = 80
        await rig.engine.refresh(.opened)
        rig.store.commitFailure = firestoreError(FirestoreErrorNumber.permissionDenied)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertGreaterThanOrEqual(rig.device.refusalChecks, 1)
        XCTAssertNil(rig.local.state?.miffs[testMiffId]?.pending, "un refus n'est pas rejoué en boucle")
        XCTAssertTrue(rig.journal.kinds.contains(.statusRefused))
        let attempts = rig.store.commitAttempts
        rig.env.now += hour
        rig.env.battery = 30
        await rig.engine.refresh(.backgrounded)
        XCTAssertEqual(rig.store.commitAttempts, attempts + 1, "une seule tentative, celle de la batterie")
        XCTAssertEqual(rig.store.statusWrites().count, 1, "seule la batterie de l'ouverture est partie")
    }

    func testTheEndOfASessionErasesTheBatteryHistoryAndANewSessionStartsOver() async {
        rig.env.battery = 80
        await rig.engine.refresh(.opened)
        XCTAssertEqual(batteryWrites().count, 1)

        await rig.engine.stop()
        XCTAssertNil(rig.local.state)
        rig.env.now += hour
        await rig.engine.refresh(.opened)
        XCTAssertEqual(batteryWrites().count, 1, "arrêt collant : rien ne repart")
        XCTAssertNil(rig.local.state)

        rig.generation = 1
        rig.putPositionHome()
        await rig.engine.refresh(.opened)

        XCTAssertEqual(batteryWrites().count, 2, "une nouvelle session repart de zéro")
        XCTAssertEqual(rig.local.state?.breaker?.batteryWrites, 1)
    }
}
