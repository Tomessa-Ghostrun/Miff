import Combine
import XCTest
@testable import Miff

/// Le contrôleur de la suppression du compte (AUTH-5c, miroir de `AccountDeletionControllerTest` d'Android) : le marqueur est sur le disque
/// AVANT toute écriture, la procédure tourne jusqu'au bout même sans écran, se met en pause sans réseau et repart toute seule, s'arrête à
/// « Dernière vérification », renonce calmement après trois échecs, annule quand des Miffs apparaissent, et ne reprend jamais la
/// suppression d'un autre compte.
@MainActor
final class AccountDeletionControllerTests: XCTestCase {
    private let timeline = Timeline()
    private lazy var procedure = ScriptedProcedure(timeline: timeline)
    private lazy var marker = MemoryDeletionMarker(timeline: timeline)
    private lazy var session = RecordingDeletionSession(timeline: timeline)
    private let network = FakeNetwork()
    private var uid: String? = testAccountUid

    private func makeController() -> AccountDeletionController {
        let controller = AccountDeletionController(
            procedure: procedure,
            marker: marker,
            session: session,
            network: network,
            currentUid: { [weak self] in self?.uid }
        )
        controller.retryPauseNanos = 1_000_000
        return controller
    }

    private func finished(_ controller: AccountDeletionController) async {
        await waitUntil {
            switch controller.state {
            case .running:
                return false
            default:
                return true
            }
        }
        // Laisse au contrôleur le temps de rendre la main après son dernier état.
        try? await Task.sleep(nanoseconds: 20_000_000)
    }

    // MARK: - Le marqueur et l'ordre

    func testTheMarkerIsOnTheDiskBeforeTheAppStopsAnythingAndBeforeTheFirstWrite() async {
        let controller = makeController()

        controller.start(apple: .notNeeded)
        XCTAssertEqual(marker.mark, AccountDeletionMark(uid: testAccountUid), "écrit avant même le premier suspens")
        await finished(controller)

        XCTAssertEqual(Array(timeline.entries.prefix(3)), ["markerWrite", "begin", "run"])
    }

    func testWhenTheAccountIsGoneTheMarkerRecordsItBeforeTheCleaningThenTheEndScreenIsShown() async {
        let controller = makeController()
        let timeline = self.timeline
        let marker = self.marker
        session.onErase = { timeline.add("markerSeenDeleted:\(marker.mark?.accountDeleted == true)") }

        controller.start(apple: .notNeeded)
        await finished(controller)

        XCTAssertEqual(session.erased, [testAccountUid])
        let seen = timeline.entries.contains("markerSeenDeleted:true")
        XCTAssertTrue(seen, "Firebase a confirmé AVANT le nettoyage local")
        XCTAssertEqual(controller.state, .done(appleRevocation: .notNeeded))
    }

    func testWhatHappenedToTheAppleAuthorizationReachesTheEndScreen() async {
        let controller = makeController()

        controller.start(apple: .failed)
        await finished(controller)

        XCTAssertEqual(controller.state, .done(appleRevocation: .failed))
    }

    func testTheLatestAppleSheetWinsAndALaterPasswordDoesNotEraseIt() async {
        procedure.runs = [.needsRecentLogin, .completed]
        let controller = makeController()

        controller.start(apple: .revoked)
        await finished(controller)
        XCTAssertEqual(controller.state, .needsRecentLogin)
        controller.reauthenticated(apple: .failed)
        await finished(controller)

        // La dernière feuille Apple a redonné l'autorisation.
        XCTAssertEqual(controller.state, .done(appleRevocation: .failed))
    }

    // MARK: - Reprise et marqueur d'un autre compte

    func testResumeTakesUpTheDeletionOfTheSameAccountWithoutAskingAgain() async {
        marker.mark = AccountDeletionMark(uid: testAccountUid)
        let controller = makeController()

        controller.resume()
        await finished(controller)

        XCTAssertEqual(procedure.runCalls, 1)
        XCTAssertEqual(session.erased, [testAccountUid])
    }

    func testResumeNeverTakesUpTheDeletionOfAnotherAccount() async {
        marker.mark = AccountDeletionMark(uid: "someone-else")
        let controller = makeController()

        controller.resume()
        try? await Task.sleep(nanoseconds: 30_000_000)

        XCTAssertEqual(procedure.runCalls, 0)
        XCTAssertEqual(controller.state, .idle)
    }

    func testResumeWithoutAMarkerDoesNothing() async {
        let controller = makeController()

        controller.resume()
        try? await Task.sleep(nanoseconds: 30_000_000)

        XCTAssertEqual(procedure.runCalls, 0)
    }

    func testNothingStartsWithNobodySignedIn() async {
        uid = nil
        let controller = makeController()

        controller.start(apple: .notNeeded)

        XCTAssertNil(marker.mark)
        XCTAssertEqual(controller.state, .idle)
    }

    func testOneGestureIsOneOperation() async {
        procedure.runs = [.completed]
        let controller = makeController()

        controller.start(apple: .notNeeded)
        controller.start(apple: .notNeeded)
        controller.retry()
        await finished(controller)

        XCTAssertEqual(procedure.runCalls, 1)
        XCTAssertEqual(marker.writes.count, 2, "le marqueur de départ une fois, puis la confirmation de Firebase")
    }

    // MARK: - Pause, essais, arrêt

    func testWithoutNetworkItPausesAndCarriesOnByItselfWhenTheNetworkIsBack() async {
        network.online = false
        procedure.runs = [.offline, .completed]
        let controller = makeController()

        controller.start(apple: .notNeeded)
        await waitUntil { controller.state == .waitingForNetwork }
        XCTAssertEqual(controller.state, .waitingForNetwork)
        XCTAssertEqual(procedure.runCalls, 1)
        network.online = true
        await finished(controller)

        XCTAssertEqual(procedure.runCalls, 2)
        XCTAssertEqual(controller.state, .done(appleRevocation: .notNeeded))
    }

    func testAPhoneThatSaysItIsOnlineButCarriesNothingStopsCalmlyAfterSixTries() async {
        procedure.runs = [.offline]
        let controller = makeController()

        controller.start(apple: .notNeeded)
        await waitUntil { controller.state == .stuck }

        XCTAssertEqual(controller.state, .stuck)
        XCTAssertEqual(procedure.runCalls, AccountDeletionController.maxOnlineFailures)
        XCTAssertNotNil(marker.mark, "le marqueur reste : le lancement suivant reprend")
        XCTAssertTrue(session.erased.isEmpty)
    }

    func testThreeFailuresStopCalmlyAndRetryTakesItUpAgain() async {
        procedure.runs = [.failed, .failed, .failed, .completed]
        let controller = makeController()

        controller.start(apple: .notNeeded)
        await waitUntil { controller.state == .stuck }
        XCTAssertEqual(controller.state, .stuck)
        XCTAssertEqual(procedure.runCalls, 3)
        controller.retry()
        await finished(controller)

        XCTAssertEqual(procedure.runCalls, 4)
        XCTAssertEqual(controller.state, .done(appleRevocation: .notNeeded))
    }

    func testARunThatFoundFewerMiffsThanBeforeCountsTheTriesAgainFromZero() async {
        procedure.runs = [.failed, .failed, .failed, .failed, .completed]
        procedure.stepsPerRun = [
            [.miff(index: 1, total: 4, longWait: false)],
            [.miff(index: 1, total: 3, longWait: false)],
            [.miff(index: 1, total: 2, longWait: false)],
            [.miff(index: 1, total: 1, longWait: false)]
        ]
        let controller = makeController()

        controller.start(apple: .notNeeded)
        await finished(controller)

        XCTAssertEqual(procedure.runCalls, 5, "une longue suppression ralentie çà et là n'est jamais abandonnée")
        XCTAssertEqual(controller.state, .done(appleRevocation: .notNeeded))
    }

    // MARK: - Dernière vérification, annulation, session fermée

    func testTheLastCheckIsAskedAndTheLastStepIsTriedAgainOnceItIsDone() async {
        procedure.runs = [.needsRecentLogin, .completed]
        let controller = makeController()

        controller.start(apple: .notNeeded)
        await waitUntil { controller.state == .needsRecentLogin }
        XCTAssertEqual(controller.state, .needsRecentLogin)
        XCTAssertNotNil(marker.mark)
        controller.reauthenticated(apple: .notNeeded)
        await finished(controller)

        XCTAssertEqual(procedure.runCalls, 2)
        XCTAssertEqual(controller.state, .done(appleRevocation: .notNeeded))
    }

    func testMiffsThatKeepAppearingCancelTheDeletionAndEraseTheMarker() async {
        procedure.runs = [.miffsRemain]
        let controller = makeController()

        controller.start(apple: .notNeeded)
        await finished(controller)

        XCTAssertNil(marker.mark)
        XCTAssertEqual(session.cancelled, 1)
        XCTAssertTrue(session.erased.isEmpty)
        XCTAssertEqual(controller.state, .idle)
    }

    func testAClosedSessionIsNeverSaidDeletedAndKeepsTheMarker() async {
        procedure.runs = [.sessionExpired]
        let controller = makeController()

        controller.start(apple: .notNeeded)
        await waitUntil { controller.state == .needsSignIn }

        XCTAssertEqual(controller.state, .needsSignIn)
        XCTAssertNotNil(marker.mark)
        XCTAssertTrue(session.erased.isEmpty)
        controller.signInAgain()
        await waitUntil { self.session.keptMarker == 1 }
        XCTAssertEqual(session.keptMarker, 1)
        XCTAssertNotNil(marker.mark, "se reconnecter avec le même compte reprend la suppression")
    }

    func testAcknowledgingTheEndScreenGoesBackToIdle() async {
        let controller = makeController()
        controller.start(apple: .notNeeded)
        await finished(controller)

        controller.acknowledge()

        XCTAssertEqual(controller.state, .idle)
    }

    func testTheStepOfTheProgressScreenIsShownWhileItRuns() async {
        procedure.stepsPerRun = [[.miff(index: 1, total: 2, longWait: false), .profile]]
        procedure.runs = [.failed, .completed]
        let controller = makeController()
        var seen: [DeletionStep] = []
        let observation = controller.$state.sink { state in
            if case .running(_, _, let step) = state {
                seen.append(step)
            }
        }

        controller.start(apple: .notNeeded)
        await finished(controller)
        observation.cancel()

        XCTAssertTrue(seen.contains(.miff(index: 1, total: 2, longWait: false)))
        XCTAssertTrue(seen.contains(.profile))
    }
}
