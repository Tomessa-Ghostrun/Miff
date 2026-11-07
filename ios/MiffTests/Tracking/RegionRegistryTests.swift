@testable import Miff
import XCTest

/// Les régions créées à l'instant, gardées le temps que la liste du système les montre (LOC-4b).
final class RegionRegistryTests: XCTestCase {
    private let start = Date(timeIntervalSince1970: 1_000_000)

    func testARegionJustCreatedIsFoundAndItsKeyIsHeld() {
        var registry = RegionRegistry<String>()

        registry.remember("zone", key: "k1", at: start)

        XCTAssertEqual(registry.region(for: "k1"), "zone")
        XCTAssertEqual(registry.keys, ["k1"])
        XCTAssertNil(registry.region(for: "k2"))
    }

    func testForgettingARegionRemovesIt() {
        var registry = RegionRegistry<String>()
        registry.remember("zone", key: "k1", at: start)

        registry.forget(key: "k1")

        XCTAssertNil(registry.region(for: "k1"))
        XCTAssertTrue(registry.keys.isEmpty)
    }

    func testRememberingTheSameKeyAgainReplacesTheRegion() {
        var registry = RegionRegistry<String>()
        registry.remember("avant", key: "k1", at: start)

        registry.remember("après", key: "k1", at: start)

        XCTAssertEqual(registry.region(for: "k1"), "après")
        XCTAssertEqual(registry.keys.count, 1)
    }

    func testWhatTheSystemNowShowsIsLetGoAndTheRestIsKeptWhileRecent() {
        var registry = RegionRegistry<String>()
        registry.remember("a", key: "k1", at: start)
        registry.remember("b", key: "k2", at: start)

        registry.prune(visible: ["k1"], now: start.addingTimeInterval(5), maxAge: 60)

        XCTAssertEqual(registry.keys, ["k2"])
    }

    func testARegionTheSystemNeverShowedIsLetGoAfterTheDelay() {
        var registry = RegionRegistry<String>()
        registry.remember("a", key: "k1", at: start)

        registry.prune(visible: [], now: start.addingTimeInterval(60), maxAge: 60)
        XCTAssertEqual(registry.keys, ["k1"], "à la limite exacte, encore gardée")
        registry.prune(visible: [], now: start.addingTimeInterval(61), maxAge: 60)

        XCTAssertTrue(registry.keys.isEmpty, "jamais une région que le système a abandonnée")
    }
}
