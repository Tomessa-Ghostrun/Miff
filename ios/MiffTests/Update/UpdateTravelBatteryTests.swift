import XCTest
@testable import Miff

/// MODE VOYAGE et photo d'update (VOY-1I) : le statut que la publication écrit n'a AUCUN niveau de batterie pendant un voyage (les règles
/// refusent `travel` avec une batterie), et en a un sinon. Le niveau vient de la copie locale du suivi, relue à chaque usage, par
/// `TravelStatusRules.battery` : la même fonction que celle que branche `UpdateServices.make`.
@MainActor
final class UpdateTravelBatteryTests: XCTestCase {
    private let rig = UpdateRig()
    private let local = MemoryTrackingLocalStore()

    /// Un publieur dont le niveau de batterie passe par la copie locale du suivi, comme dans `UpdateServices.make`.
    private func publisher() -> UpdatePublisher {
        var environment = rig.environment
        let local = self.local
        environment.battery = { TravelStatusRules.battery(80, local: local.load(), uid: updateUid) }
        var dependencies = UpdatePublisherDependencies(
            store: rig.store,
            device: rig.device,
            position: rig.position,
            network: rig.network,
            ledger: rig.ledger,
            environment: environment
        )
        dependencies.isPositionPublished = { _ in true }
        return UpdatePublisher(dependencies)
    }

    private func statusBattery() -> FieldValueSpec? {
        rig.store.writes.first { $0.path.contains("/status/") }?.data["batteryPct"]
    }

    private func travelState() -> LocalState {
        var state = LocalState(uid: updateUid)
        state.miffs[testMiffId] = LocalMiff()
        state.travel = LocalTravel(untilMillis: rig.now + 86_400_000, untilDay: "2026-11-04", sinceMillis: rig.now)
        return state
    }

    func testDuringATripThePhotoStatusCarriesNoBatteryLevel() async {
        local.state = travelState()

        let result = await publisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .published(sharedIn: 1))
        XCTAssertEqual(statusBattery(), .null)
    }

    func testOutsideATripThePhotoStatusCarriesTheBatteryLevel() async {
        var state = LocalState(uid: updateUid)
        state.miffs[testMiffId] = LocalMiff()
        local.state = state

        _ = await publisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(statusBattery(), .integer(80))
    }

    func testWhileTheKeysOfAnEndedTripAreNotCleanedYetThereIsNoBatteryLevelEither() async {
        var state = LocalState(uid: updateUid)
        state.miffs[testMiffId] = LocalMiff()
        state.update(testMiffId) { $0.clearTravel = true }
        local.state = state

        _ = await publisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(statusBattery(), .null)
    }

    /// Le branchement réel : `UpdateServices.make` donne au publieur ET au nettoyeur de photo un niveau qui passe par
    /// `TravelStatusRules.battery` (analyse des sources : sans ce branchement, une photo publiée en voyage serait refusée par les règles).
    func testTheRealWiringGoesThroughTheTravelRule() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Update
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/App/UpdateServices.swift")
        let source = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(source.contains("TravelStatusRules.battery("))
        let raw = "battery: { await system.batteryPct() }"
        XCTAssertFalse(source.contains(raw), "le niveau brut ne doit plus être branché tel quel")
    }
}
