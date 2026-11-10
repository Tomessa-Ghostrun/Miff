import XCTest
@testable import Miff

/// La position unique du pont (`LocationTracking.current(within:)`) : plusieurs attentes en même temps, échec passager du système,
/// position ancienne, délai et repli (recette TestFlight build 24 : « Position introuvable » alors que la personne partage).
@MainActor
final class LocationTrackingWaitTests: XCTestCase {
    private struct Rig {
        let tracking: LocationTracking
        let manager: FakeLocationManager
        let sink: RecordingEventSink
    }

    private func make(wait: Double = 0.05, retry: Double = 0.01) -> Rig {
        let manager = FakeLocationManager()
        let sink = RecordingEventSink()
        let tracking = LocationTracking(
            manager: manager, clock: { TrackingFixtures.noon }, waitSeconds: wait, retrySeconds: retry
        )
        tracking.attach(sink)
        return Rig(tracking: tracking, manager: manager, sink: sink)
    }

    func testASecondRequestDoesNotCancelTheFirstOneAndOneFixAnswersBoth() async {
        let rig = make(wait: 5)
        let engine = Task { await rig.tracking.current() }
        await waitUntil { rig.manager.positionAsks == 1 }
        let update = Task { await rig.tracking.current(within: 5) }
        await waitUntil { rig.manager.positionAsks == 2 }
        let fix = TrackingFixtures.fixHome(at: TrackingFixtures.noon)

        rig.manager.emit(.fix(fix))

        let engineAnswer = await engine.value
        let updateAnswer = await update.value
        XCTAssertEqual(engineAnswer, fix)
        // Avant, la demande du moteur faisait répondre « introuvable » à la publication en cours.
        XCTAssertEqual(updateAnswer, fix)
        XCTAssertTrue(rig.sink.moves.isEmpty)
    }

    func testATemporaryFailureOfTheSystemIsRetriedWhileSomeoneWaitsAndThenAnswered() async {
        let rig = make(wait: 5)
        let asking = Task { await rig.tracking.current(within: 5) }
        await waitUntil { rig.manager.positionAsks == 1 }

        rig.manager.emit(.fixUnavailable)
        await waitUntil { rig.manager.positionAsks == 2 }
        let fix = TrackingFixtures.fixHome(at: TrackingFixtures.noon)
        rig.manager.emit(.fix(fix))

        let answer = await asking.value
        XCTAssertEqual(answer, fix)
        XCTAssertEqual(rig.manager.positionAsks, 2, "une seule nouvelle demande par échec passager")
    }

    func testATemporaryFailureWithNobodyWaitingAsksNothing() async {
        let rig = make()

        rig.manager.emit(.fixUnavailable)
        try? await Task.sleep(nanoseconds: 60_000_000)

        XCTAssertEqual(rig.manager.positionAsks, 0)
    }

    func testAnOldPositionOfTheSystemDoesNotAnswerAndAnotherOneIsAsked() async {
        let rig = make(wait: 5)
        let asking = Task { await rig.tracking.current(within: 5) }
        await waitUntil { rig.manager.positionAsks == 1 }

        rig.manager.emit(.fix(TrackingFixtures.fixHome(at: TrackingFixtures.noon - 11 * TrackingFixtures.minute)))
        await waitUntil { rig.manager.positionAsks == 2 }
        let fresh = TrackingFixtures.fixHome(at: TrackingFixtures.noon)
        rig.manager.emit(.fix(fresh))

        let answer = await asking.value
        XCTAssertEqual(answer, fresh)
        // Ni une réponse, ni un déplacement.
        XCTAssertTrue(rig.sink.moves.isEmpty)
    }

    func testWhenTheWaitOfTheUpdateEndsTheRecentLastKnownPositionIsTheFallbackAndAnOldOneIsNot() async {
        let rig = make(wait: 0.05)
        rig.manager.systemFix = TrackingFixtures.fixHome(at: TrackingFixtures.noon - TrackingFixtures.minute)
        let recent = await rig.tracking.current(within: 0.05)
        XCTAssertNotNil(recent, "le délai ne doit pas faire perdre une position de moins de deux minutes")

        rig.manager.systemFix = TrackingFixtures.fixHome(at: TrackingFixtures.noon - 5 * TrackingFixtures.minute)
        let old = await rig.tracking.current(within: 0.05)
        XCTAssertNil(old)
    }

    func testWhenTheWaitOfTheEngineEndsThereIsNoFallbackBecauseAPositionIsNeverReusedAtAZoneEntry() async {
        let rig = make(wait: 0.05)
        rig.manager.systemFix = TrackingFixtures.fixHome(at: TrackingFixtures.noon - 10_000)

        let answer = await rig.tracking.current()

        XCTAssertNil(answer, "BATT-3 : à l'entrée dans une zone, jamais la position connue du système")
    }

    func testAPositionMeasuredBeforeTheLastDisconnectionIsNotTheFallbackOfAnExpiredWait() async {
        let rig = make(wait: 0.05)
        rig.manager.systemFix = TrackingFixtures.fixHome(at: TrackingFixtures.noon - 30_000)

        await rig.tracking.disarmAll()
        let answer = await rig.tracking.current(within: 0.05)

        XCTAssertNil(answer, "elle peut être celle de l'autre compte")
    }

    func testAFailureOfTheSystemAnswersEveryWaitWithTheRecentKnownPositionIfAny() async {
        let rig = make(wait: 5)
        let known = TrackingFixtures.fixHome(at: TrackingFixtures.noon - TrackingFixtures.minute)
        rig.manager.systemFix = known
        let engine = Task { await rig.tracking.current() }
        await waitUntil { rig.manager.positionAsks == 1 }
        let update = Task { await rig.tracking.current(within: 5) }
        await waitUntil { rig.manager.positionAsks == 2 }

        rig.manager.emit(.fixFailed)

        let engineAnswer = await engine.value
        let updateAnswer = await update.value
        XCTAssertEqual(engineAnswer, known)
        XCTAssertEqual(updateAnswer, known)
    }

    func testATemporaryFailureAnswersTheEngineAtOnceAsBeforeWhileTheUpdateKeepsWaiting() async {
        let rig = make(wait: 5)
        let engine = Task { await rig.tracking.current() }
        await waitUntil { rig.manager.positionAsks == 1 }
        let update = Task { await rig.tracking.current(within: 5) }
        await waitUntil { rig.manager.positionAsks == 2 }

        rig.manager.emit(.fixUnavailable)
        let engineAnswer = await engine.value
        XCTAssertNil(engineAnswer, "aucune position connue du système : comme avant")
        await waitUntil { rig.manager.positionAsks == 3 }
        let fix = TrackingFixtures.fixHome(at: TrackingFixtures.noon)
        rig.manager.emit(.fix(fix))

        let updateAnswer = await update.value
        XCTAssertEqual(updateAnswer, fix)
    }

    func testTheRetryStopsWhenTheWaitsHaveEnded() async {
        let rig = make(wait: 0.05, retry: 0.01)
        let waiting = Task { await rig.tracking.current(within: 0.05) }
        await waitUntil { rig.manager.positionAsks == 1 }
        rig.manager.emit(.fixUnavailable)
        await waitUntil { rig.manager.positionAsks >= 2 }

        let answer = await waiting.value
        XCTAssertNil(answer)
        let asks = rig.manager.positionAsks
        rig.manager.emit(.fixUnavailable)
        try? await Task.sleep(nanoseconds: 80_000_000)

        XCTAssertEqual(rig.manager.positionAsks, asks, "plus personne n'attend : aucune nouvelle demande")
    }

    func testAWaitThatEndsDoesNotEndTheOthers() async {
        let rig = make(wait: 5)
        let patient = Task { await rig.tracking.current(within: 5) }
        await waitUntil { rig.manager.positionAsks == 1 }
        let impatient = await rig.tracking.current(within: 0.05)
        XCTAssertNil(impatient)

        let fix = TrackingFixtures.fixHome(at: TrackingFixtures.noon)
        rig.manager.emit(.fix(fix))

        let answer = await patient.value
        XCTAssertEqual(answer, fix)
    }
}
