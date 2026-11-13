import XCTest
@testable import Miff

/// REPRISE du partage d'une Miff, les cas limites (revue de la PR #190) : le drapeau de reprise n'est consommé que quand la détection peut
/// tourner et que la Miff est éligible ; plusieurs Miffs dans la même zone ; position périmée ; statut qui ne peut pas partir ; mode voyage.
final class TrackingEngineResumeEdgeTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute
    private let period = TravelPeriod(
        untilMillis: TrackingFixtures.noon + 2 * 24 * TrackingFixtures.hour,
        untilDay: "2026-11-04"
    )

    private func presenceWrites() -> [DocWrite] {
        rig.store.statusWrites().filter { $0.data["presence"] != nil }
    }

    private func arrivedHome() async {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - 60 * 1000)
        rig.putPositionHome()
    }

    // MARK: - Le drapeau n'est pas perdu

    func testResumingWithoutAlwaysThenGrantingItRecomputesAndWritesThePresence() async {
        await arrivedHome()
        let before = presenceWrites().count
        let granted = rig.env.snapshot
        rig.env.snapshot = PermissionSnapshot(location: .whenInUse, notifications: .authorized)

        await rig.engine.markResumed(testMiffId)
        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(presenceWrites().count, before)
        XCTAssertEqual(rig.local.state?.resumedMiffs, [testMiffId])

        rig.env.snapshot = granted
        rig.putPositionHome()
        await rig.engine.refresh(.accessChanged)

        XCTAssertEqual(presenceWrites().count, before + 1)
        XCTAssertEqual(presenceWrites().last?.data["presence"], .string("in_trusted"))
        XCTAssertNil(rig.local.state?.resumedMiffs)
    }

    func testResumingWhileTheMiffIsNotEligibleYetKeepsTheFlagUntilItIs() async {
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: true, version: "2026-10-v2")
        await rig.engine.markResumed(testMiffId)

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.local.state?.resumedMiffs, [testMiffId], "même après l'effacement du suivi")
        XCTAssertTrue(presenceWrites().isEmpty)

        rig.store.consents[testMiffId] = SharingConsentRead(enabled: true, version: MiffSharingConsent.version)
        rig.putPositionHome()
        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(presenceWrites().last?.data["presence"], .string("in_trusted"))
        XCTAssertNil(rig.local.state?.resumedMiffs)
    }

    func testAFailedReadOfTheServerRightAfterAResumeDoesNotForgetIt() async {
        rig.store.consentFailure = firestoreError(FirestoreErrorNumber.unavailable)
        await rig.engine.markResumed(testMiffId)

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.local.state?.resumedMiffs, [testMiffId])
    }

    // MARK: - Plusieurs Miffs, position périmée, statut qui ne part pas

    func testOnlyTheResumedMiffIsWrittenAgainWhenTwoMiffsShareTheSameZone() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await arrivedHome()
        let before = presenceWrites().count
        XCTAssertEqual(before, 2)

        await rig.engine.markResumed(testMiffId)
        await rig.engine.refresh(.sharingChanged)

        let added = presenceWrites().dropFirst(before)
        XCTAssertEqual(added.count, 1)
        XCTAssertEqual(added.first?.path, "miffs/\(testMiffId)/status/uid-1")
    }

    func testAPositionOlderThanTenMinutesNeverMakesAnOut() async {
        await arrivedHome()
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: rig.env.now - 30 * 1000)
        let before = presenceWrites().count
        rig.env.now += 12 * minute
        rig.position.fix = TrackingFixtures.fixFar(at: rig.env.now - 15 * minute)

        await rig.engine.markResumed(testMiffId)
        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(presenceWrites().count, before, "ni position utilisable, ni zone connue : rien n'est inventé")
    }

    func testAStatusThatCannotLeaveStaysPendingAndGoesWhenTheNetworkIsBack() async {
        await arrivedHome()
        let before = presenceWrites().count
        rig.env.online = false

        await rig.engine.markResumed(testMiffId)
        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(presenceWrites().count, before)
        XCTAssertNotNil(rig.local.state?.miffs[testMiffId]?.pending)

        rig.env.online = true
        await rig.engine.onNetworkBack()

        XCTAssertEqual(presenceWrites().count, before + 1)
        XCTAssertNil(rig.local.state?.miffs[testMiffId]?.pending)
    }

    func testATrippedBreakerKeepsTheRecomputedStatusPending() async {
        await arrivedHome()
        let before = presenceWrites().count
        let day = WriteBreaker.dayKey(millis: rig.env.now)
        rig.local.state?.breaker = BreakerState(
            dayKey: day, statusWrites: TrackingLimits.maxStatusWritesPerDay, positionWrites: 0
        )

        await rig.engine.markResumed(testMiffId)
        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(presenceWrites().count, before)
        XCTAssertNotNil(rig.local.state?.miffs[testMiffId]?.pending)
    }

    // MARK: - Mode voyage

    func testDuringATripTheResumeFlagWaitsAndNothingIsWritten() async {
        await rig.engine.refresh(.opened)
        let started = await rig.engine.setTravel(period)
        XCTAssertEqual(started, .done)
        let before = presenceWrites().count

        await rig.engine.markResumed(testMiffId)
        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(presenceWrites().count, before)
        XCTAssertEqual(rig.local.state?.resumedMiffs, [testMiffId])
    }

    func testAResumeDuringATripDoesNotLoseTheNeutralStatusThatClearsTheEndedTrip() async throws {
        await rig.engine.refresh(.opened)
        _ = await rig.engine.setTravel(period)
        let writesBefore = rig.store.statusWrites().count
        await rig.engine.markResumed(testMiffId)
        rig.env.online = false
        rig.env.now = period.untilMillis + minute
        rig.putPositionHome()

        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.local.state?.miffs[testMiffId]?.clearTravel, true)
        XCTAssertNotNil(rig.local.state?.miffs[testMiffId]?.pending)

        rig.env.online = true
        await rig.engine.onNetworkBack()

        let written = try XCTUnwrap(rig.store.statusWrites().dropFirst(writesBefore).first)
        // Les clés du voyage terminé sont effacées dans cette écriture.
        XCTAssertEqual(written.data["travelUntil"], .null)
        XCTAssertEqual(rig.local.state?.miffs[testMiffId]?.clearTravel, false)
    }
}
