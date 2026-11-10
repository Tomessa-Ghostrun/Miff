import XCTest
@testable import Miff

/// MODE VOYAGE, le moteur (VOY-1I, critères V1 à V11 de docs/tickets/mode-voyage.md, décision du PM du 2026-11-10) : le voyage est ÉCRIT
/// d'abord et le statut de présence s'arrête ENSUITE (un refus ne change rien) ; PENDANT un voyage la POSITION continue d'être publiée
/// mais aucune présence, zone, batterie ni accès n'est écrit dans le statut ; le voyage finit au premier réveil ou à la première ouverture
/// après la date, ou à la main ; un NOUVEL appareil adopte un voyage au lieu de l'écraser ; une Miff rejointe pendant un voyage l'apprend.
/// Doubles pour le téléphone, le système et Firestore : aucune vraie position, aucun réseau. Mêmes scénarios qu'Android.
final class TrackingEngineTravelTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute
    private let day = 24 * TrackingFixtures.hour
    /// Un voyage de deux jours : il finit à l'heure de départ des scénarios + 2 jours.
    private let period = TravelPeriod(
        untilMillis: TrackingFixtures.noon + 2 * 24 * TrackingFixtures.hour,
        untilDay: "2026-11-04"
    )

    private var statusPath: String {
        "miffs/\(testMiffId)/status/uid-1"
    }

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

    // MARK: - V1 : le voyage est écrit d'abord, le statut de présence s'arrête ensuite

    func testTheTripIsWrittenToEveryMiffBeforeTheStatusOfTheZonesStopsAndThePositionStaysShared() async throws {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await rig.engine.refresh(.opened)
        XCTAssertFalse(rig.geofences.armedSets.isEmpty)
        let before = rig.store.commits.count
        let disarmsBefore = rig.geofences.disarmCalls
        var disarmsAtTheFirstWrite = -1
        rig.store.beforeCommit = { write in
            if disarmsAtTheFirstWrite < 0 && write.path.contains("/status/") {
                disarmsAtTheFirstWrite = self.rig.geofences.disarmCalls
            }
        }

        let result = await rig.engine.setTravel(period)

        XCTAssertEqual(result, .done)
        // V1 : quand le premier statut du voyage est parti, les zones étaient encore là.
        XCTAssertEqual(disarmsAtTheFirstWrite, disarmsBefore)
        XCTAssertGreaterThan(rig.geofences.disarmCalls, disarmsBefore)
        // La position reste partagée : rien n'est supprimé, les signalements de déplacement restent demandés.
        XCTAssertEqual(rig.movement.stops, 0)
        XCTAssertTrue(rig.store.writes.allSatisfy { $0.kind != .delete })
        // Un lot par Miff : le statut du voyage, seul.
        let lots = Array(rig.store.commits.dropFirst(before))
        XCTAssertEqual(lots.count, 2)
        for lot in lots {
            XCTAssertEqual(lot.count, 1)
            let status = try XCTUnwrap(lot.first)
            XCTAssertEqual(status.kind, .update)
            XCTAssertTrue(status.path.hasSuffix("/status/uid-1"))
            XCTAssertEqual(status.data["presence"], .string("travel"))
            XCTAssertEqual(status.data["placeId"], .null)
            XCTAssertEqual(status.data["batteryPct"], .null)
            XCTAssertEqual(status.data["escalationDueAt"], .null)
            XCTAssertEqual(untilOf(status), .timestamp(millis: period.untilMillis))
            XCTAssertEqual(status.data["travelUntilDay"], .string("2026-11-04"))
            XCTAssertEqual(status.data["deviceId"], .string(testDeviceId))
            XCTAssertNotNil(status.data["presenceAt"])
        }
        // La position et son garde-fou restent ; ce qui était déduit des zones est oublié ; le voyage est gardé.
        let kept = try XCTUnwrap(rig.local.state)
        XCTAssertNotNil(kept.fix)
        XCTAssertTrue(kept.armed.isEmpty)
        for miff in kept.miffs.values {
            XCTAssertNotNil(miff.written)
            XCTAssertNil(miff.derived)
        }
        XCTAssertEqual(kept.travel?.period, period)
        XCTAssertEqual(kept.travel?.upToDateIn, [testMiffId, testOtherMiffId])
        XCTAssertEqual(rig.engine.travelPeriod(), period)
    }

    func testARefusedTripChangesNothingTheStatusOfTheZonesContinues() async {
        await rig.engine.refresh(.opened)
        let armed = rig.geofences.armedSets.count
        let disarms = rig.geofences.disarmCalls
        // Les règles ne sont pas encore déployées (ou la session est prise) : le serveur dit non.
        rig.store.failingPaths[statusPath] = firestoreError(FirestoreErrorNumber.permissionDenied)

        let result = await rig.engine.setTravel(period)

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(rig.geofences.armedSets.count, armed)
        XCTAssertEqual(rig.geofences.disarmCalls, disarms)
        XCTAssertEqual(rig.movement.stops, 0)
        XCTAssertNil(rig.local.state?.travel)
        XCTAssertNotNil(rig.local.state?.fix)
        XCTAssertNil(rig.engine.travelPeriod())
    }

    func testAPhoneThatWasReplacedCannotBeginATripNothingChanges() async {
        await rig.engine.refresh(.opened)
        let disarms = rig.geofences.disarmCalls
        rig.device.clearanceResult = .replaced

        let result = await rig.engine.setTravel(period)

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(rig.geofences.disarmCalls, disarms)
        XCTAssertNil(rig.local.state?.travel)
    }

    func testWithoutNetworkTheTripIsAcceptedHereAndToldAsSoonAsTheNetworkIsBack() async {
        await rig.engine.refresh(.opened)
        let before = rig.store.commits.count
        rig.env.online = false

        let result = await rig.engine.setTravel(period)

        XCTAssertEqual(result, .doneOffline)
        XCTAssertEqual(rig.store.commits.count, before)
        XCTAssertNotNil(rig.local.state?.travel)
        XCTAssertEqual(rig.local.state?.travel?.upToDateIn, Set<String>())

        rig.env.online = true
        await rig.engine.onNetworkBack()

        let status = rig.store.statusWrites().first
        XCTAssertEqual(rig.store.statusWrites().count, 1)
        XCTAssertEqual(status?.data["presence"], .string("travel"))
        XCTAssertEqual(status.flatMap { untilOf($0) }, .timestamp(millis: period.untilMillis))
        XCTAssertTrue(rig.store.writes.allSatisfy { $0.kind != .delete })
        XCTAssertEqual(rig.local.state?.travel?.upToDateIn, [testMiffId])
    }

    func testANetworkThatFailsDuringTheWriteAcceptsTheTripHereAndKeepsTheMiffToBeTold() async {
        await rig.engine.refresh(.opened)
        rig.store.failingPaths[statusPath] = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await rig.engine.setTravel(period)

        XCTAssertEqual(result, .doneOffline)
        XCTAssertEqual(rig.local.state?.travel?.upToDateIn, Set<String>())
        XCTAssertEqual(rig.local.state?.travel?.startedIn, Set<String>())
    }

    func testWithNoMiffWhereIShareTheTripIsKeptHereNothingIsWritten() async {
        rig.store.memberships = []

        let result = await rig.engine.setTravel(period)

        XCTAssertEqual(result, .done)
        XCTAssertTrue(rig.store.commits.isEmpty)
        XCTAssertEqual(rig.local.state?.travel?.period, period)

        // V11 : et il finit sans une écriture.
        let ended = await rig.engine.endTravel()
        XCTAssertTrue(ended)
        XCTAssertTrue(rig.store.commits.isEmpty)
        XCTAssertNil(rig.local.state?.travel)
    }

    // MARK: - V2 : pendant le voyage, la position reste partagée, le statut reste un voyage

    func testDuringATripNoStatusZoneBatteryOrAccessIsWrittenAndNoZoneIsArmed() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await startedTrip()
        let statuses = rig.store.statusWrites().count
        let armed = rig.geofences.armedSets.count
        rig.env.battery = 20
        rig.env.now += 20 * minute
        rig.putPositionHome()

        await rig.engine.refresh(.opened)
        rig.env.now += 20 * minute
        rig.putPositionHome()
        await rig.engine.refresh(.backgrounded)
        await rig.engine.refresh(.moved, supplied: TrackingFixtures.fixFar(at: rig.env.now))
        await rig.engine.refresh(.zoneEntered)
        await rig.engine.refresh(.rearm)
        await rig.engine.refresh(.sharingChanged)
        // L'accès à la localisation change (précision retirée) : rien n'est écrit dans le statut pour autant.
        rig.env.snapshot = PermissionSnapshot(location: .always, preciseLocation: false, notifications: .authorized)
        await rig.engine.refresh(.accessChanged)
        rig.env.snapshot = PermissionSnapshot(location: .always, notifications: .authorized)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: rig.env.now)
        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: true)
        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: false)
        await rig.engine.onNetworkBack()

        // V2 : le statut de chaque Miff reste le voyage : pas une écriture de plus (ni présence, ni arrivée, ni batterie, ni accès).
        XCTAssertEqual(rig.store.statusWrites().count, statuses)
        XCTAssertEqual(rig.geofences.armedSets.count, armed)
        XCTAssertTrue(rig.local.state?.miffs.values.allSatisfy { $0.derived == nil && $0.pending == nil } ?? false)
        XCTAssertNotNil(rig.local.state?.travel)
        XCTAssertTrue(rig.store.statusWrites().allSatisfy { $0.data["presence"] == .string("travel") })
    }

    func testDuringATripThePositionKeepsBeingPublishedWithTheGuardsOfAnyOtherTime() async throws {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await startedTrip()
        let before = rig.store.positionWrites().count
        let reads = rig.position.calls

        // 20 minutes plus tard, à l'ouverture : lue et écrite dans chaque Miff, une écriture chacune, seule.
        rig.env.now += 20 * minute
        rig.putPositionHome()
        await rig.engine.refresh(.opened)
        XCTAssertGreaterThan(rig.position.calls, reads)
        XCTAssertEqual(rig.store.positionWrites().count, before + 2)
        for lot in rig.store.commits.suffix(2) {
            XCTAssertEqual(lot.count, 1)
            XCTAssertTrue(try XCTUnwrap(lot.first).path.hasSuffix("/positions/uid-1"))
        }
        // Le garde-fou de fréquence tient toujours : 5 minutes plus tard, sans bouger, rien de plus.
        rig.env.now += 5 * minute
        rig.putPositionHome()
        await rig.engine.refresh(.backgrounded)
        XCTAssertEqual(rig.store.positionWrites().count, before + 2)
        // Un déplacement signalé par le système (plus de 250 m) : écrit.
        rig.env.now += 5 * minute
        await rig.engine.refresh(.moved, supplied: TrackingFixtures.fixFar(at: rig.env.now))
        XCTAssertEqual(rig.store.positionWrites().count, before + 4)
        // Toujours aucun statut autre que le voyage.
        XCTAssertTrue(rig.store.statusWrites().allSatisfy { $0.data["presence"] == .string("travel") })
    }

    func testDuringATripWithoutAlwaysNoPositionIsReadOrWritten() async {
        await startedTrip()
        let reads = rig.position.calls
        let writes = rig.store.positionWrites().count
        rig.env.snapshot = PermissionSnapshot(location: .whenInUse, notifications: .authorized)
        rig.env.now += 20 * minute
        rig.putPositionHome()

        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.position.calls, reads)
        XCTAssertEqual(rig.store.positionWrites().count, writes)
        XCTAssertGreaterThanOrEqual(rig.movement.stops, 1)
        XCTAssertNil(rig.local.state?.fix)
        XCTAssertNotNil(rig.local.state?.travel)
    }

    func testARestartDuringATripArmsNoZoneKeepsTheReportsOfAMove() async {
        await startedTrip()
        let armed = rig.geofences.armedSets.count
        let starts = rig.movement.starts

        await rig.engine.refresh(.rearm)

        XCTAssertEqual(rig.geofences.armedSets.count, armed)
        XCTAssertGreaterThan(rig.movement.starts, starts)
        XCTAssertNotNil(rig.local.state?.travel)
    }

    // MARK: - V6 : la fin bouge

    func testMovingTheEndWritesTheNewEndInEveryMiffWithoutTouchingTheStartOfTheTrip() async throws {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await startedTrip()
        let before = rig.store.commits.count
        let later = TravelPeriod(untilMillis: period.untilMillis + 3 * day, untilDay: "2026-11-07")

        let result = await rig.engine.setTravel(later)

        XCTAssertEqual(result, .done)
        let lots = Array(rig.store.commits.dropFirst(before))
        XCTAssertEqual(lots.count, 2)
        for lot in lots {
            XCTAssertEqual(lot.count, 1)
            let write = try XCTUnwrap(lot.first)
            XCTAssertTrue(write.path.hasSuffix("/status/uid-1"))
            XCTAssertEqual(untilOf(write), .timestamp(millis: later.untilMillis))
            XCTAssertEqual(write.data["travelUntilDay"], .string("2026-11-07"))
            XCTAssertNil(write.data["presence"])
            XCTAssertNil(write.data["presenceAt"])
        }
        XCTAssertEqual(rig.local.state?.travel?.period, later)
        XCTAssertEqual(rig.engine.travelPeriod(), later)
        // Le début du voyage ne bouge pas.
        XCTAssertEqual(rig.local.state?.travel?.sinceMillis, TrackingFixtures.noon)
    }

    func testAShorterEndIsAllowed() async {
        await startedTrip()
        let shorter = TravelPeriod(untilMillis: TrackingFixtures.noon + day, untilDay: "2026-11-03")
        let result = await rig.engine.setTravel(shorter)
        XCTAssertEqual(result, .done)
        XCTAssertEqual(rig.engine.travelPeriod(), shorter)
    }

    func testAnEndThatIsAlreadyPastIsNeverWritten() async {
        await rig.engine.refresh(.opened)
        let before = rig.store.commits.count
        let past = TravelPeriod(untilMillis: rig.env.now - minute, untilDay: "2026-11-03")

        let result = await rig.engine.setTravel(past)

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(rig.store.commits.count, before)
        XCTAssertNil(rig.local.state?.travel)
    }

    func testAnEndMoreThan90DaysAheadIsNeverWritten() async {
        await rig.engine.refresh(.opened)
        let before = rig.store.commits.count
        let far = TravelPeriod(untilMillis: rig.env.now + 92 * day, untilDay: "2027-02-03")

        let result = await rig.engine.setTravel(far)

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(rig.store.commits.count, before)
        XCTAssertNil(rig.local.state?.travel)
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
