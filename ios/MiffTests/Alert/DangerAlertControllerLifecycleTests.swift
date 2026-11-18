#if ALERT_DANGER
import XCTest
@testable import Miff

/// La machine d'états de MON alerte, de sa vie longue : les 7 jours, le relancement, les Miffs, l'ABANDON d'une alerte qui n'est pas partie (retour du PM : le pointeur n'est
/// effacé qu'après relecture du serveur), l'adoption par un nouveau téléphone et le retour au premier plan (AD23, AD24, AD42).
@MainActor
final class DangerAlertControllerLifecycleTests: DangerAlertControllerTestCase {
    // MARK: - Les 7 jours, le relancement, les Miffs

    func testAtSevenDaysTheAlertSimplyDisappearsWithoutAnyWrite() async {
        let controller = makeController()
        await activate(controller)
        clock += DangerAlertRules.durationMillis

        controller.tick()

        XCTAssertEqual(controller.state, .idle)
        XCTAssertEqual(store.endCalls, 0, "aucune écriture, aucune notification, aucun rappel (D19)")
        XCTAssertTrue(events.isEmpty)
    }

    func testAnActiveAlertComesBackAfterARelaunchAndAnExpiredOneDoesNot() {
        let expires = clock + 1_000
        local.save(
            DangerAlertPointer(
                miffId: miffA, phase: .active, armedAtMillis: clock, startedAtMillis: clock, expiresAtMillis: expires
            )
        )
        let back = makeController()
        back.restore()
        XCTAssertEqual(back.state, .active(miffId: miffA, startedAtMillis: clock, expiresAtMillis: expires, end: .idle))

        clock = expires + 1
        let gone = makeController()
        gone.restore()
        XCTAssertEqual(gone.state, .idle)
        XCTAssertNil(local.saved)
    }

    func testAnAlertOnItsWayContinuesAfterARelaunch() async {
        store.startResults = [started()]
        local.save(DangerAlertPointer(miffId: miffA, phase: .sending, armedAtMillis: clock))
        let controller = makeController()

        controller.restore()
        await eventually { self.isActive(controller) }
    }

    func testOnlyAConfirmedLossOfTheMiffEndsTheAlertNeverAList() async {
        let controller = makeController()
        await activate(controller)

        controller.onAlertRemoved(miffId: miffB)
        XCTAssertTrue(isActive(controller), "une autre Miff n'a aucun effet sur l'alerte")
        controller.onAlertRemoved(miffId: miffA)
        XCTAssertEqual(controller.state, .idle)
        XCTAssertNil(local.saved)
    }

    func testEndingTheSessionForgetsTheAlertInMemory() async {
        let controller = makeController()
        await activate(controller)

        controller.onSessionEnded()

        XCTAssertEqual(controller.state, .idle)
    }

    // MARK: - Abandonner une alerte qui n'est pas partie (retour du PM, revue de l'Android m1)

    private func stuckInQuota(_ controller: DangerAlertController) async {
        store.startResults = [.failed(.quotaReached)]
        _ = controller.trigger(miffId: miffA)
        await eventually { self.isStep(controller, .quota) }
    }

    func testCancellingFreesThePersonAtOnceButClearsThePointerOnlyAfterTheServerConfirmedNoLiveAlert() async {
        let controller = makeController()
        await stuckInQuota(controller)
        store.mineReads = [.value(nil)]

        controller.cancelPending()

        XCTAssertEqual(controller.state, .idle, "plus rien n'est montré : la personne est libre")
        XCTAssertEqual(local.saved?.phase, .abandoned, "le pointeur reste tant que le serveur n'a rien confirmé")
        await eventually { self.local.saved == nil }
        XCTAssertEqual(store.mineCalls, 1, "une relecture du serveur AVANT d'effacer")
    }

    func testCancellingAnAlertThatLandedMakesItActiveInsteadOfErasingIt() async throws {
        store.startResults = [.failed(.refused)]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)
        await eventually { self.isStep(controller, .refused) }
        store.mineReads = [.value(try liveDoc())]

        controller.cancelPending()

        await eventually { self.isActive(controller) }
        XCTAssertEqual(local.saved?.phase, .active)
    }

    func testCancellingWithoutANetworkWaitsForTheNetworkBeforeErasingAnything() async {
        let controller = makeController()
        await stuckInQuota(controller)
        store.mineReads = [.value(nil)]
        network.set(online: false)

        controller.cancelPending()
        await eventually { self.local.saved?.phase == .abandoned }
        try? await Task.sleep(nanoseconds: 20_000_000)

        XCTAssertEqual(store.mineCalls, 0, "rien n'est lu sans réseau")
        XCTAssertEqual(local.saved?.phase, .abandoned)
        network.set(online: true)
        await eventually { self.local.saved == nil }
    }

    func testAnUnreachableServerKeepsThePointerAfterTheBoundedAttempts() async {
        let controller = makeController()
        await stuckInQuota(controller)
        store.mineReads = [.unavailable]

        controller.cancelPending()
        await eventually { self.store.mineCalls >= DangerAlertController.maxVerifyAttempts }
        try? await Task.sleep(nanoseconds: 20_000_000)

        XCTAssertEqual(store.mineCalls, DangerAlertController.maxVerifyAttempts)
        XCTAssertEqual(local.saved?.phase, .abandoned, "le prochain lancement de l'app recommence")
    }

    func testAnAbandonedPointerIsVerifiedAgainAtTheNextLaunch() async {
        local.save(DangerAlertPointer(miffId: miffA, phase: .abandoned, armedAtMillis: clock))
        store.mineReads = [.value(nil)]
        let controller = makeController()

        controller.restore()

        await eventually { self.local.saved == nil }
        XCTAssertEqual(controller.state, .idle)
    }

    func testAnAlertWaitingForADecisionDoesNotForbidANewOneButAnActiveOneDoes() async {
        let controller = makeController()
        await stuckInQuota(controller)

        XCTAssertFalse(controller.blocksNewAlert)
        store.startResults = [started()]
        XCTAssertEqual(controller.trigger(miffId: miffB), .started)
        await eventually { self.isActive(controller) }
        XCTAssertTrue(controller.blocksNewAlert)
        XCTAssertEqual(controller.trigger(miffId: miffA), .alreadyActive(miffId: miffB))
    }

    func testAnAlertOnItsWayForbidsANewOne() {
        let controller = makeController()
        store.startResults = [.failed(.offline)]
        _ = controller.trigger(miffId: miffA)

        XCTAssertTrue(controller.blocksNewAlert)
        XCTAssertEqual(controller.trigger(miffId: miffB), .alreadyActive(miffId: miffA))
        controller.onSessionEnded()
    }

    // MARK: - Un nouveau téléphone et le retour au premier plan

    func testANewPhoneReadsEachMiffOnceAndAdoptsALiveAlert() async throws {
        store.mineReads = [.value(nil), .value(try liveDoc())]
        let controller = makeController()

        await controller.adoptFromServer(miffIds: [miffB, miffA])

        XCTAssertEqual(store.mineCalls, 2)
        XCTAssertEqual(controller.state.miffId, miffA)
        XCTAssertEqual(local.adoptedFor(), "me")
        await controller.adoptFromServer(miffIds: [miffB, miffA])
        XCTAssertEqual(store.mineCalls, 2, "une seule lecture par Miff, une fois par compte")
    }

    func testAnUnreachableServerDoesNotMarkTheAdoptionDone() async {
        store.mineReads = [.unavailable]
        let controller = makeController()

        await controller.adoptFromServer(miffIds: [miffA])

        XCTAssertNil(local.adoptedFor())
        XCTAssertEqual(controller.state, .idle)
    }

    func testReturningToTheFrontOnAnEndedAlertRemovesTheBannerAndNoAnswerChangesNothing() async {
        let controller = makeController()
        await activate(controller)

        store.mineReads = [.unavailable]
        await controller.verifyActive()
        XCTAssertTrue(isActive(controller), "jamais une bannière retirée sur un doute")

        store.mineReads = [.value(nil)]
        await controller.verifyActive()
        XCTAssertEqual(controller.state, .idle)
    }
}
#endif
