import XCTest
@testable import Miff

/// MODE VOYAGE : ce que le suivi des lieux dit aux notifications de MON voyage (début, nouvelle date, fin), sans rien changer au moteur, et
/// où la fin du voyage est gardée (un compte et un instant, par compte, exclu des sauvegardes, effacé avec le reste).
final class TravelNotificationHookTests: XCTestCase {
    private final class MemoryTrackingStore: TrackingLocalStore {
        var state: LocalState?

        func load() -> LocalState? {
            state
        }

        func save(_ state: LocalState) {
            self.state = state
        }

        func clear() {
            state = nil
        }
    }

    private struct Heard: Equatable {
        let uid: String
        let until: Int?
        let previous: Int?
    }

    private func state(travelUntil: Int?) -> LocalState {
        var value = LocalState(uid: "me")
        value.travel = travelUntil.map { LocalTravel(untilMillis: $0, untilDay: "2026-11-09", sinceMillis: 1_000) }
        return value
    }

    private func makeStore(
        _ inner: MemoryTrackingStore,
        _ heard: @escaping (Heard) -> Void
    ) -> PresenceNotifyingLocalStore {
        PresenceNotifyingLocalStore(
            wrapping: inner,
            onPresence: { _, _ in },
            onTravel: { heard(Heard(uid: $0, until: $1, previous: $2)) }
        )
    }

    func testTheStartTheMovedDateAndTheEndOfMyTripAreAnnouncedOnceEach() {
        var heard: [Heard] = []
        let store = makeStore(MemoryTrackingStore()) { heard.append($0) }

        store.save(state(travelUntil: nil))
        store.save(state(travelUntil: 5_000))
        store.save(state(travelUntil: 5_000))
        store.save(state(travelUntil: 9_000))
        store.save(state(travelUntil: nil))
        store.save(state(travelUntil: nil))

        XCTAssertEqual(
            heard,
            [
                Heard(uid: "me", until: 5_000, previous: nil),
                Heard(uid: "me", until: 9_000, previous: 5_000),
                Heard(uid: "me", until: nil, previous: 9_000)
            ],
            "rien tant que rien ne change ; la fin précédente distingue une fin anticipée d'une fin naturelle"
        )
    }

    func testATripAlreadyActiveBeforeTheUpdateIsAnnouncedOnceAtTheFirstLoadThenOnlyItsChanges() {
        var heard: [Heard] = []
        let inner = MemoryTrackingStore()
        inner.state = state(travelUntil: 5_000)
        let store = makeStore(inner) { heard.append($0) }

        _ = store.load()
        _ = store.load()
        store.save(state(travelUntil: 5_000))
        store.save(state(travelUntil: nil))

        XCTAssertEqual(
            heard,
            [Heard(uid: "me", until: 5_000, previous: nil), Heard(uid: "me", until: nil, previous: 5_000)],
            "le voyage existant est annoncé UNE fois (N7 programmée, gardes appliquées), puis seulement ses changements"
        )
    }

    func testNothingIsAnnouncedAtLoadWhenThereIsNoTrip() {
        var heard: [Heard] = []
        let inner = MemoryTrackingStore()
        inner.state = state(travelUntil: nil)
        let store = makeStore(inner) { heard.append($0) }

        _ = store.load()

        XCTAssertTrue(heard.isEmpty)
    }

    func testClearingForgetsWhatWasSeenAndEverythingElsePassesThrough() {
        var heard: [Heard] = []
        let inner = MemoryTrackingStore()
        let store = makeStore(inner) { heard.append($0) }
        store.save(state(travelUntil: 5_000))

        store.clear()
        store.save(state(travelUntil: 5_000))

        XCTAssertEqual(heard.count, 2, "après un effacement (déconnexion), le même voyage est de nouveau annoncé")
        XCTAssertEqual(inner.state?.travel?.untilMillis, 5_000, "la copie locale est sauvegardée telle quelle")
    }

    func testTheEndOfTheTripIsKeptPerAccountExcludedFromBackupsAndErasedWithTheRest() throws {
        let directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("travel-end-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = NotificationPlanFileStore(directory: directory)

        store.saveTravelEnd(5_000, uid: "me")

        XCTAssertEqual(store.loadTravelEnd(uid: "me"), 5_000)
        XCTAssertNil(store.loadTravelEnd(uid: "other"), "le fichier d'un autre compte n'est jamais lu")
        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
        store.saveTravelEnd(nil, uid: "me")
        XCTAssertNil(store.loadTravelEnd(uid: "me"))
        store.saveTravelEnd(7_000, uid: "me")
        store.clear()
        XCTAssertNil(store.loadTravelEnd(uid: "me"))
    }
}
