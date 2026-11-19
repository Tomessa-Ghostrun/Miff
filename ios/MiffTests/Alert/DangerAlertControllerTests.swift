#if ALERT_DANGER
import XCTest
@testable import Miff

/// La machine d'états de MON alerte, du départ à la fin (ticket 6, AD1 à AD4, AD21, AD22, AD44, AD47, AD48) : rien ne part avant les 5 secondes, une alerte qui ne peut pas partir est
/// gardée en attente puis reconfirmée après 15 minutes, une fin qui ne part pas garde l'alerte active, chaque refus du serveur est bien distingué, une seule alerte à la fois.
/// Miroir d'`AlertControllerTest` d'Android (les cas des tests sont le contrat).
@MainActor
final class DangerAlertControllerTests: DangerAlertControllerTestCase {
    // MARK: - Le départ

    func testTriggeringStartsInTheChosenMiffAndBecomesActiveOnceTheServerAccepted() async {
        store.startResults = [started()]
        let controller = makeController()

        XCTAssertEqual(controller.trigger(miffId: miffA), .started)
        await eventually { self.isActive(controller) }

        XCTAssertEqual(controller.state.miffId, miffA)
        XCTAssertEqual(store.lastStartMiffId, miffA)
        XCTAssertEqual(store.startCalls, 1)
        XCTAssertEqual(local.saved?.phase, .active)
        XCTAssertEqual(positionAsks, 1, "UNE seule mesure de position, au départ")
    }

    func testNothingIsWrittenBeforeTheTrigger() async {
        let controller = makeController()
        controller.restore()
        controller.tick()
        await controller.verifyActive()

        XCTAssertEqual(store.startCalls, 0)
        XCTAssertEqual(controller.state, .idle)
        XCTAssertEqual(positionAsks, 0)
    }

    func testASecondAlertIsNeverStartedInAnyMiff() async {
        let controller = makeController()
        await activate(controller)

        XCTAssertEqual(controller.trigger(miffId: miffB), .alreadyActive(miffId: miffA))
        XCTAssertEqual(store.startCalls, 1)
        XCTAssertEqual(positionAsks, 1)
    }

    func testTriggeringWithoutASessionOrWithABadMiffDoesNothing() {
        let controller = makeController()
        signedIn = nil

        XCTAssertEqual(controller.trigger(miffId: miffA), .notSignedIn)
        signedIn = "me"
        XCTAssertEqual(controller.trigger(miffId: "bad id"), .notSignedIn)
        XCTAssertEqual(controller.state, .idle)
        XCTAssertNil(local.saved)
    }

    func testAnAlertAlreadyLiveOnTheServerIsAdoptedNotWrittenAgain() async throws {
        store.startResults = [.adopted(try liveDoc())]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { self.isActive(controller) }
    }

    func testAnAlertBeingWrittenCannotBeCancelled() {
        store.startResults = [started()]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        controller.cancelPending()

        XCTAssertTrue(isStep(controller, .sending))
        controller.onSessionEnded()
    }

    // MARK: - Sans réseau

    func testWithoutNetworkTheAlertWaitsThenLeavesWhenTheNetworkReturns() async {
        network.set(online: false)
        store.startResults = [started()]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)
        await eventually { self.isStep(controller, .waitingNetwork) }

        XCTAssertEqual(store.startCalls, 0, "rien n'est écrit sans réseau")
        network.set(online: true)
        await eventually { self.isActive(controller) }
        XCTAssertEqual(store.startCalls, 1)
    }

    func testAfterFifteenMinutesTheAlertAsksToBeConfirmedBeforeLeaving() async {
        network.set(online: false)
        store.startResults = [started()]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)
        await eventually { self.isStep(controller, .waitingNetwork) }
        clock += DangerAlertRules.staleSendMillis + 1
        network.set(online: true)

        await eventually { self.isStep(controller, .needsConfirmation) }
        XCTAssertEqual(store.startCalls, 0, "rien ne part avant la réponse de la personne")
        controller.confirmStale()
        await eventually { self.isActive(controller) }
        XCTAssertEqual(store.startCalls, 1)
    }

    func testAServerThatDoesNotAnswerKeepsTheAlertWaitingAndRetries() async {
        store.startResults = [.failed(.offline), started()]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { self.isActive(controller) }
        XCTAssertEqual(store.startCalls, 2)
        XCTAssertFalse(sleeps.isEmpty)
    }

    // MARK: - Chaque refus, bien distingué

    func testTheDailyLimitIsToldCalmlyWithoutRetryAndNothingElseIsTried() async {
        store.startResults = [.failed(.quotaReached)]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { self.isStep(controller, .quota) }
        XCTAssertEqual(store.startCalls, 1)
        controller.retry()
        XCTAssertEqual(store.startCalls, 1, "pas de « Réessayer » : il serait refusé de nouveau")
        XCTAssertEqual(device.refusalChecks, 0)
    }

    func testARefusalReadsTheSessionAndOnlyThenSaysCalmly() async {
        store.startResults = [.failed(.refused), started()]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { self.isStep(controller, .refused) }
        XCTAssertEqual(device.refusalChecks, 1)
        controller.retry()
        await eventually { self.isActive(controller) }
    }

    func testARefusalBecauseThePhoneWasReplacedDropsTheAlertWithoutAMessage() async {
        store.startResults = [.failed(.refused)]
        device.afterRefusalResult = .replaced
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { controller.state == .idle }
        XCTAssertNil(local.saved)
        XCTAssertTrue(events.isEmpty)
    }

    func testStoppedSharingOrANonMemberDropsTheAlertAndSaysIt() async {
        store.startResults = [.failed(.sharingStopped)]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { controller.state == .idle && !self.events.isEmpty }
        XCTAssertEqual(events, [.notSent(miffId: miffA, failure: .sharingStopped)])
        XCTAssertNil(local.saved)
    }

    func testARearmingTooSoonWaitsTheSecondsLeftWithoutAnyRefusalShown() async {
        store.startResults = [.tooSoon(waitMillis: 7_000), started()]
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { self.isActive(controller) }
        XCTAssertTrue(sleeps.contains(7_000))
        XCTAssertTrue(events.isEmpty)
    }

    func testAReplacedPhoneWritesNothing() async {
        device.clearanceResult = .replaced
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { controller.state == .idle }
        XCTAssertEqual(store.startCalls, 0)
    }

    func testAnUnverifiedSessionWaitsAndWritesNothingYet() async {
        device.clearanceResult = .unverified
        let controller = makeController()
        _ = controller.trigger(miffId: miffA)

        await eventually { !self.sleeps.isEmpty }
        XCTAssertEqual(store.startCalls, 0)
        controller.onSessionEnded()
    }

    // MARK: - La fin

    func testEndingWritesInTheOriginMiffAndTheBannerGoesOnlyAfterTheServerAccepted() async {
        let controller = makeController()
        await activate(controller)
        store.endResults = [.ended]

        controller.end()
        await eventually { controller.state == .idle }

        XCTAssertEqual(store.endCalls, 1)
        XCTAssertEqual(events, [.ended(miffId: miffA)])
        XCTAssertNil(local.saved)
    }

    func testAnEndThatCannotLeaveKeepsTheAlertActiveAndWaiting() async {
        let controller = makeController()
        await activate(controller)
        store.endResults = [.failed(.offline)]

        controller.end()
        await eventually { self.store.endCalls >= 2 }
        XCTAssertTrue(isActive(controller), "jamais terminée localement avant l'accord du serveur")

        store.endResults = [.ended]
        await eventually { controller.state == .idle }
        XCTAssertEqual(events, [.ended(miffId: miffA)])
    }

    func testARefusedEndKeepsTheAlertAndTellsCalmly() async {
        let controller = makeController()
        await activate(controller)
        store.endResults = [.failed(.refused)]

        controller.end()
        await eventually { self.events == [.endNotSent] }
        XCTAssertTrue(isActive(controller))
    }
}
#endif
