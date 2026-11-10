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
}
