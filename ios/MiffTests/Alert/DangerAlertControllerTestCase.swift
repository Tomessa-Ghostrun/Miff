#if ALERT_DANGER
import XCTest
@testable import Miff

/// Les fixtures communes des tests de la machine d'états de MON alerte (voir `DangerAlertControllerTests` et `DangerAlertControllerLifecycleTests`).
@MainActor
class DangerAlertControllerTestCase: XCTestCase {
    let miffA = "aaaaaaaaaaaaaaaaaaaa"
    let miffB = "bbbbbbbbbbbbbbbbbbbb"
    var clock = 1_794_063_600_000
    let store = FakeDangerStore()
    let device = FakeActiveDevice()
    let network = FakeDangerNetwork()
    let local = MemoryDangerLocalStore()
    var sleeps: [Int] = []
    var signedIn: String? = "me"
    var events: [DangerAlertEvent] = []
    var positionAsks = 0

    func makeController() -> DangerAlertController {
        let controller = DangerAlertController(
            store: store,
            device: device,
            network: network,
            local: local,
            currentUid: { [unowned self] in self.signedIn },
            nowMillis: { [unowned self] in self.clock },
            onSending: { [unowned self] in self.positionAsks += 1 },
            sendTimeoutSeconds: 5,
            retryPausesMillis: [10],
            sleep: { [unowned self] millis in
                self.sleeps.append(millis)
                try? await Task.sleep(nanoseconds: 1_000_000)
            }
        )
        controller.onEvent = { [unowned self] event in self.events.append(event) }
        return controller
    }

    func started() -> DangerStartResult {
        .started(startedAtMillis: clock, expiresAtMillis: DangerAlertRules.expiresAtMillis(deviceNowMillis: clock))
    }

    func liveDoc() throws -> DangerAlertDoc {
        try XCTUnwrap(
            DangerAlertDoc.parse(
                uid: "me", status: "active", startedAtMillis: clock, expiresAtMillis: clock + 100_000, seq: 1
            )
        )
    }

    func isActive(_ controller: DangerAlertController) -> Bool {
        if case .active = controller.state {
            return true
        }
        return false
    }

    func isStep(_ controller: DangerAlertController, _ step: DangerSendStep) -> Bool {
        if case .sending(_, _, let current) = controller.state {
            return current == step
        }
        return false
    }

    func activate(_ controller: DangerAlertController) async {
        store.startResults = [started()]
        _ = controller.trigger(miffId: miffA)
        await eventually { self.isActive(controller) }
    }

}
#endif
