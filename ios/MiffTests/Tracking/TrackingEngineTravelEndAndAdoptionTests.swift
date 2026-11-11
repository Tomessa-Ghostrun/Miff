import XCTest
@testable import Miff

/// MODE VOYAGE, le moteur, suite (VOY-1I, critères V4, V5, V7 et V8 de docs/tickets/mode-voyage.md) : la FIN d'un voyage (au premier réveil,
/// à l'ouverture, à la main, hors connexion), un NOUVEL appareil adopte un voyage au lieu de
/// l'écraser, une Miff rejointe pendant un voyage l'apprend, la déconnexion efface le voyage du téléphone, et un statut qui garde les clés
/// d'un voyage terminé les efface dans sa prochaine écriture (jamais de mise à jour partielle à côté d'elles). Mêmes scénarios qu'Android.
final class TrackingEngineTravelEndAndAdoptionTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute
    private let day = 24 * TrackingFixtures.hour
    /// Un voyage de deux jours : il finit à l'heure de départ des scénarios + 2 jours.
    private let period = TravelPeriod(
        untilMillis: TrackingFixtures.noon + 2 * 24 * TrackingFixtures.hour,
        untilDay: "2026-11-04"
    )

    private func untilOf(_ write: DocWrite) -> FieldValueSpec? {
        write.data["travelUntil"]
    }

    /// Le suivi tourne (la personne est chez elle, la position est publiée), puis le voyage commence.
    private func startedTrip() async {
        await rig.engine.refresh(.opened)
        XCTAssertFalse(rig.geofences.armedSets.isEmpty)
        let result = await rig.engine.setTravel(period)
        XCTAssertEqual(result, .done)
    }

    // MARK: - V4, V5 : la fin

    func testTheEndOfTheTripAtTheFirstWakeUpAfterTheDateTellsUnknownFirstThenArmsTheTrackingAgain() async throws {
        await startedTrip()
        let writesBefore = rig.store.statusWrites().count
        let armedBefore = rig.geofences.armedSets.count
        var armedAtTheEndWrite = -1
        rig.store.beforeCommit = { write in
            if armedAtTheEndWrite < 0 && write.path.contains("/status/") {
                armedAtTheEndWrite = self.rig.geofences.armedSets.count
            }
        }
        // Le premier réveil après la date (un déplacement significatif, app fermée).
        rig.env.now = period.untilMillis + 2 * minute
        rig.putPositionHome()

        await rig.engine.refresh(.moved, supplied: TrackingFixtures.fixHome(at: rig.env.now))

        let first = try XCTUnwrap(rig.store.statusWrites().dropFirst(writesBefore).first)
        XCTAssertEqual(first.data["presence"], .string("unknown"))
        XCTAssertEqual(first.data["placeId"], .null)
        // Les deux clés du voyage sont effacées dans la MÊME écriture.
        XCTAssertEqual(first.data["travelUntil"], .null)
        XCTAssertEqual(first.data["travelUntilDay"], .null)
        // V4 : l'état neutre est parti AVANT que les zones reviennent.
        XCTAssertEqual(armedAtTheEndWrite, armedBefore)
        XCTAssertGreaterThan(rig.geofences.armedSets.count, armedBefore)
        XCTAssertNil(rig.local.state?.travel)
        XCTAssertNil(rig.engine.travelPeriod())
        XCTAssertEqual(rig.local.state?.miffs[testMiffId]?.clearTravel, false)

        // La personne est chez elle : l'arrivée habituelle suit, et elle ne porte aucune clé du voyage (déjà effacées).
        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: true)
        let last = try XCTUnwrap(rig.store.statusWrites().last)
        XCTAssertEqual(last.data["presence"], .string("in_trusted"))
        XCTAssertNil(last.data["travelUntil"])
        XCTAssertNil(last.data["travelUntilDay"])
    }

    func testTheEndIsFoundAtTheOpeningWhenTheAppWasNotWokenBefore() async {
        await startedTrip()
        let writesBefore = rig.store.statusWrites().count
        rig.env.now = period.untilMillis + 5 * day
        rig.putPositionHome()

        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.store.statusWrites().dropFirst(writesBefore).first?.data["presence"], .string("unknown"))
        XCTAssertNil(rig.local.state?.travel)
        XCTAssertGreaterThan(rig.geofences.armedSets.count, 1)
    }

    func testBeforeTheEndNothingEndsWhateverTheWakeUp() async {
        await startedTrip()
        let writesBefore = rig.store.statusWrites().count
        rig.env.now = period.untilMillis - minute
        rig.putPositionHome()

        await rig.engine.refresh(.opened)
        await rig.engine.refresh(.moved, supplied: TrackingFixtures.fixHome(at: rig.env.now))

        XCTAssertEqual(rig.store.statusWrites().count, writesBefore)
        XCTAssertNotNil(rig.local.state?.travel)
    }

    func testTurningTheTripOffResumesAtOnceAndASecondTapFindsNothingToEnd() async {
        await startedTrip()
        let writesBefore = rig.store.statusWrites().count
        rig.env.now += 10 * minute
        rig.putPositionHome()

        let ended = await rig.engine.endTravel()

        XCTAssertTrue(ended)
        XCTAssertEqual(rig.store.statusWrites().dropFirst(writesBefore).first?.data["presence"], .string("unknown"))
        XCTAssertNil(rig.local.state?.travel)
        XCTAssertGreaterThan(rig.geofences.armedSets.count, 1)
        let again = await rig.engine.endTravel()
        XCTAssertFalse(again)
    }

    func testTheEndThatCannotLeaveWaitsForTheNetworkAndTheTrackingResumesAnyway() async throws {
        await startedTrip()
        let writesBefore = rig.store.statusWrites().count
        rig.env.online = false
        rig.env.now = period.untilMillis + minute
        rig.putPositionHome()

        await rig.engine.refresh(.opened)

        // L'état local est libre et les zones sont de retour, mais l'état neutre reste à dire.
        XCTAssertEqual(rig.store.statusWrites().count, writesBefore)
        XCTAssertNil(rig.local.state?.travel)
        XCTAssertGreaterThan(rig.geofences.armedSets.count, 1)
        XCTAssertEqual(rig.local.state?.miffs[testMiffId]?.clearTravel, true)
        XCTAssertNotNil(rig.local.state?.miffs[testMiffId]?.pending)

        rig.env.online = true
        await rig.engine.onNetworkBack()

        // Le premier statut qui part efface les clés du voyage.
        let written = try XCTUnwrap(rig.store.statusWrites().dropFirst(writesBefore).first)
        XCTAssertEqual(written.data["travelUntil"], .null)
        XCTAssertEqual(written.data["travelUntilDay"], .null)
        XCTAssertEqual(rig.local.state?.miffs[testMiffId]?.clearTravel, false)
    }

    /// Un statut qui garde les clés d'un voyage terminé : une mise à jour PARTIELLE (batterie, accès) serait refusée par les règles.
    private func keepKeysOfAnEndedTrip() async {
        await rig.engine.refresh(.opened)
        var state = rig.local.state ?? LocalState(uid: "uid-1")
        state.update(testMiffId) {
            $0.clearTravel = true
            $0.pending = nil
        }
        rig.local.state = state
    }

    func testAStatusThatStillCarriesTheKeysOfAnEndedTripGetsNoBatteryRefresh() async {
        await keepKeysOfAnEndedTrip()
        rig.env.battery = 40
        rig.env.now += 20 * minute
        rig.putPositionHome()
        let before = rig.store.statusWrites().count

        await rig.engine.refresh(.opened)
        await rig.engine.refresh(.backgrounded)

        XCTAssertEqual(rig.store.statusWrites().count, before)
    }

    func testAStatusThatStillCarriesTheKeysOfAnEndedTripGetsNoAccessUpdate() async {
        await keepKeysOfAnEndedTrip()
        // Un accès qui a changé : sans le garde, une mise à jour partielle serait écrite (puis refusée par les règles).
        rig.env.snapshot = PermissionSnapshot(location: .always, preciseLocation: false, notifications: .authorized)
        let before = rig.store.statusWrites().count

        await rig.engine.refresh(.backgrounded)
        await rig.engine.refresh(.accessChanged)

        XCTAssertEqual(rig.store.statusWrites().count, before)
    }

    // MARK: - V7 : un nouvel appareil adopte le voyage

    func testANewPhoneAdoptsAnOngoingTripBeforeAnythingIsWrittenOverIt() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        let since = TrackingFixtures.noon - day
        rig.store.ownTravels[testMiffId] = TravelRead(period: period, sinceMillis: since)
        rig.store.ownTravels[testOtherMiffId] = TravelRead(period: period, sinceMillis: since)
        XCTAssertNil(rig.local.state)

        await rig.engine.refresh(.opened)

        XCTAssertTrue(rig.geofences.armedSets.isEmpty)
        // Le voyage est adopté tel quel : aucun statut n'est écrit par-dessus (la position, elle, continue d'être partagée).
        XCTAssertTrue(rig.store.statusWrites().isEmpty)
        XCTAssertGreaterThan(rig.position.calls, 0)
        XCTAssertEqual(rig.store.positionWrites().count, 2)
        let kept = rig.local.state?.travel
        XCTAssertEqual(kept?.period, period)
        XCTAssertEqual(kept?.sinceMillis, since)
        XCTAssertEqual(kept?.upToDateIn, [testMiffId, testOtherMiffId])
        XCTAssertEqual(rig.engine.travelPeriod(), period)
    }

    func testANewPhoneAdoptsATripThatIsOverAndEndsIt() async throws {
        let over = TravelPeriod(untilMillis: TrackingFixtures.noon - day, untilDay: "2026-11-02")
        rig.store.ownTravels[testMiffId] = TravelRead(period: over, sinceMillis: TrackingFixtures.noon - 5 * day)

        await rig.engine.refresh(.opened)

        let first = try XCTUnwrap(rig.store.statusWrites().first)
        XCTAssertEqual(first.data["presence"], .string("unknown"))
        XCTAssertEqual(first.data["travelUntil"], .null)
        XCTAssertEqual(first.data["travelUntilDay"], .null)
        XCTAssertNil(rig.local.state?.travel)
        XCTAssertFalse(rig.geofences.armedSets.isEmpty)
    }

    func testANewPhoneWithNoTripStartsNormallyAndReadsMyStatusOnlyOnce() async {
        await rig.engine.refresh(.opened)
        XCTAssertFalse(rig.geofences.armedSets.isEmpty)
        XCTAssertEqual(rig.store.ownTravelReads, 1)

        rig.env.now += 2 * minute
        rig.putPositionHome()
        await rig.engine.refresh(.opened)
        await rig.engine.refresh(.backgrounded)

        XCTAssertEqual(rig.store.ownTravelReads, 1)
    }

    func testANewPhoneThatCannotReadMyStatusArmsNothingAndAsksAgainAtTheNextWakeUp() async {
        rig.store.ownTravelFailure = firestoreError(FirestoreErrorNumber.unavailable)

        await rig.engine.refresh(.opened)

        // Le voyage ne doit jamais être écrasé : rien n'est armé, lu ni gardé sur une supposition.
        XCTAssertEqual(rig.position.calls, 0)
        XCTAssertTrue(rig.geofences.armedSets.isEmpty)
        XCTAssertNil(rig.local.state)
        XCTAssertTrue(rig.store.commits.isEmpty)

        rig.store.ownTravelFailure = nil
        await rig.engine.refresh(.opened)
        XCTAssertFalse(rig.geofences.armedSets.isEmpty)
    }

    // MARK: - V8 : une Miff rejointe pendant le voyage

    func testAMiffJoinedDuringTheTripLearnsOfItAndTheOthersAreNotWrittenAgain() async {
        await startedTrip()
        let before = rig.store.statusWrites().count
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])

        await rig.engine.refresh(.sharingChanged)

        let writes = Array(rig.store.statusWrites().dropFirst(before))
        XCTAssertEqual(writes.count, 1)
        XCTAssertEqual(writes.first?.path, "miffs/\(testOtherMiffId)/status/uid-1")
        XCTAssertEqual(writes.first?.data["presence"], .string("travel"))
        XCTAssertEqual(writes.first.flatMap { untilOf($0) }, .timestamp(millis: period.untilMillis))
        XCTAssertEqual(rig.local.state?.travel?.upToDateIn, [testMiffId, testOtherMiffId])
        XCTAssertEqual(rig.geofences.armedSets.count, 1, "toujours aucune zone armée depuis le début du voyage")
    }

    func testAMiffWhereIStopSharingLeavesTheListOfThoseToTell() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await startedTrip()
        rig.store.consents[testOtherMiffId] = SharingConsentRead(enabled: false, version: MiffSharingConsent.version)

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(Set((rig.local.state?.miffs ?? [:]).keys), [testMiffId])
        XCTAssertEqual(rig.local.state?.travel?.upToDateIn, [testMiffId])
        XCTAssertEqual(rig.local.state?.travel?.startedIn, [testMiffId])
    }

    func testAMiffThatRefusesTheTripIsLookedAtAgainSoonAndTheOthersAreStillTold() async {
        await startedTrip()
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        let otherStatus = "miffs/\(testOtherMiffId)/status/uid-1"
        rig.store.failingPaths[otherStatus] = firestoreError(FirestoreErrorNumber.permissionDenied)

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(Set((rig.local.state?.miffs ?? [:]).keys), [testMiffId])
        XCTAssertEqual(rig.local.state?.travel?.upToDateIn, [testMiffId])
        XCTAssertNotNil(rig.local.state?.travel)
    }

    // MARK: - V10 : la déconnexion

    func testSigningOutErasesTheTripFromThePhone() async {
        await startedTrip()

        await rig.engine.stop()

        XCTAssertNil(rig.local.state)
        XCTAssertNil(rig.engine.travelPeriod())
    }

    func testTheTripIsKeptForTheAccountItBelongsToOnly() async {
        await startedTrip()
        rig.env.currentUid = "uid-2"
        XCTAssertNil(rig.engine.travelPeriod())
        rig.env.currentUid = "uid-1"
        XCTAssertEqual(rig.engine.travelPeriod(), period)
    }

    func testNobodySignedInCannotBeginATrip() async {
        rig.env.currentUid = nil
        let result = await rig.engine.setTravel(period)
        XCTAssertEqual(result, .unavailable)
        XCTAssertTrue(rig.store.commits.isEmpty)
        let ended = await rig.engine.endTravel()
        XCTAssertFalse(ended)
    }

    // MARK: - Une écriture de statut qui garde les clés d'un voyage terminé les efface

    func testAStatusWrittenWhileTheKeysOfAnEndedTripRemainClearsThemInTheSameWrite() async throws {
        await rig.engine.refresh(.opened)
        var state = try XCTUnwrap(rig.local.state)
        state.update(testMiffId) { $0.clearTravel = true }
        rig.local.state = state

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        let write = try XCTUnwrap(rig.store.statusWrites().last)
        XCTAssertEqual(write.data["presence"], .string("in_trusted"))
        XCTAssertEqual(write.data["travelUntil"], .null)
        XCTAssertEqual(write.data["travelUntilDay"], .null)
        XCTAssertEqual(rig.local.state?.miffs[testMiffId]?.clearTravel, false)
    }
}
