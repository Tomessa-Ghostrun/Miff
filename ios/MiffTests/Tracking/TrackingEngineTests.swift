import XCTest
@testable import Miff

/// Le moteur de suivi des lieux avec le système remplacé par des doubles (LOC-4b) : ce qui est armé, lu et écrit, et ce qui ne l'est
/// JAMAIS (aucune position, aucune zone, aucune écriture hors des conditions du ticket). Mêmes scénarios qu'Android (LOC-2b).
final class TrackingEngineTests: XCTestCase {
    private let rig = TrackingRig()

    private func presence(of write: DocWrite) -> FieldValueSpec? {
        write.data["presence"]
    }

    // MARK: - Rien avant les conditions (AUT7)

    func testNothingIsReadArmedOrWrittenWithoutAlwaysAndTheExactPosition() async {
        for snapshot in [
            PermissionSnapshot(location: .whenInUse, notifications: .authorized),
            PermissionSnapshot(location: .always, preciseLocation: false, notifications: .authorized),
            PermissionSnapshot(location: .always, servicesEnabled: false, notifications: .authorized),
            PermissionSnapshot(location: .denied, notifications: .authorized),
            PermissionSnapshot(location: .notDetermined, notifications: .authorized)
        ] {
            let fresh = TrackingRig()
            fresh.env.snapshot = snapshot

            await fresh.engine.refresh(.opened)

            XCTAssertEqual(fresh.position.calls, 0, "\(snapshot)")
            XCTAssertTrue(fresh.geofences.armedSets.isEmpty, "\(snapshot)")
            XCTAssertEqual(fresh.movement.starts, 0, "\(snapshot)")
            XCTAssertTrue(fresh.store.writes.isEmpty, "\(snapshot)")
        }
    }

    func testNothingIsReadArmedOrWrittenWithoutTheConsentOfVersionThree() async {
        for version in ["2026-10-v1", "2026-10-v2", nil] as [String?] {
            let fresh = TrackingRig()
            fresh.store.consents[testMiffId] = SharingConsentRead(enabled: true, version: version)

            await fresh.engine.refresh(.opened)

            XCTAssertEqual(fresh.position.calls, 0, "\(String(describing: version))")
            XCTAssertTrue(fresh.geofences.armedSets.isEmpty)
            XCTAssertTrue(fresh.store.writes.isEmpty)
            XCTAssertTrue(fresh.journal.kinds.contains(.notEligible))
        }
    }

    func testNothingIsDoneWhenSharingIsStoppedOrThereIsNoCard() async {
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: false, version: MiffSharingConsent.version)
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.position.calls, 0)

        let noCard = TrackingRig()
        noCard.store.consents = [:]
        await noCard.engine.refresh(.opened)
        XCTAssertEqual(noCard.position.calls, 0)
        XCTAssertTrue(noCard.store.writes.isEmpty)
    }

    func testNothingIsDoneWhenNobodyIsSignedIn() async {
        rig.env.currentUid = nil

        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertEqual(rig.position.calls, 0)
        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertGreaterThanOrEqual(rig.geofences.disarmCalls, 1)
    }

    // MARK: - À l'ouverture

    func testOpeningArmsTheZonesReadsThePositionAndWritesTheLastPositionOnly() async throws {
        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.geofences.lastArmed.map { $0.key }, [rig.homeKey])
        XCTAssertEqual(rig.geofences.reregisterFlags, [false], "rien à réenregistrer à une simple ouverture")
        XCTAssertEqual(rig.movement.starts, 1)
        XCTAssertEqual(rig.position.calls, 1)
        XCTAssertTrue(rig.store.statusWrites().isEmpty, "rien n'a changé : aucun statut")
        let positions = rig.store.positionWrites()
        XCTAssertEqual(positions.count, 1)
        let write = try XCTUnwrap(positions.first)
        XCTAssertEqual(write.path, "miffs/\(testMiffId)/positions/uid-1")
        XCTAssertEqual(write.data["lat"], .double(TrackingGeo.round5(TrackingFixtures.homeLat)))
        XCTAssertEqual(write.data["deviceId"], .string(testDeviceId))
        XCTAssertEqual(write.data["measuredAt"], .timestamp(millis: rig.env.now))
    }

    func testTheZonesAreArmedBeforeThePositionIsAsked() async {
        // La position tarde ou n'arrive pas : l'armement est déjà fait.
        rig.position.fix = nil

        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.geofences.lastArmed.count, 1)
        XCTAssertTrue(rig.store.positionWrites().isEmpty)
        XCTAssertTrue(rig.journal.kinds.contains(.positionUnavailable))
    }

    func testAPositionOlderThanTenMinutesIsNeverUsedOrWritten() async {
        rig.position.fix = TrackingFixtures.fixHome(at: rig.env.now - 11 * TrackingFixtures.minute)

        await rig.engine.refresh(.opened)

        XCTAssertTrue(rig.store.positionWrites().isEmpty)
    }

    func testAPositionSuppliedByTheSystemIsUsedWithoutAskingForAnother() async {
        let supplied = TrackingFixtures.fixFar(at: rig.env.now - 30 * 1000)

        await rig.engine.refresh(.moved, supplied: supplied)

        XCTAssertEqual(rig.position.calls, 0)
        XCTAssertEqual(rig.store.positionWrites().count, 1)
    }

    func testAnOldSuppliedPositionIsReplacedByAFreshOne() async {
        let old = TrackingFixtures.fixFar(at: rig.env.now - 20 * TrackingFixtures.minute)

        await rig.engine.refresh(.moved, supplied: old)

        XCTAssertEqual(rig.position.calls, 1)
    }

    // MARK: - Arrivées et départs (DET1, DET2)

    func testArrivalWritesInTrustedWithTheRealTimeOfTheEvent() async throws {
        await rig.engine.refresh(.opened)
        let eventTime = rig.env.now - 40 * 1000
        rig.env.battery = 80

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: eventTime)

        let write = try XCTUnwrap(rig.store.statusWrites().first)
        XCTAssertEqual(write.kind, .update)
        XCTAssertEqual(presence(of: write), .string("in_trusted"))
        XCTAssertEqual(write.data["placeId"], .string("p1"))
        XCTAssertEqual(write.data["presenceAt"], .timestamp(millis: eventTime))
        XCTAssertEqual(write.data["lastUpdateAt"], .timestamp(millis: eventTime))
        XCTAssertEqual(write.data["locationAccess"], .string("background"))
        XCTAssertEqual(write.data["batteryPct"], .integer(80))
    }

    func testDepartureWritesOutAndKeepsTheLastUpdate() async throws {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - 120 * 1000)
        let leaving = rig.env.now - 10 * 1000

        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: leaving)

        let writes = rig.store.statusWrites()
        XCTAssertEqual(writes.count, 2)
        let departure = try XCTUnwrap(writes.last)
        XCTAssertEqual(presence(of: departure), .string("out"))
        XCTAssertEqual(departure.data["placeId"], .string("p1"))
        XCTAssertEqual(departure.data["presenceAt"], .timestamp(millis: leaving))
        XCTAssertNil(departure.data["lastUpdateAt"], "R-LIEU-1 : jamais réécrit pour passer les règles")
    }

    func testEnteringTheZoneAgainWritesNothingNew() async {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertEqual(rig.store.statusWrites().count, 1)
    }

    func testAnEventForAZoneTheListDoesNotKnowIsIgnored() async {
        await rig.engine.refresh(.opened)
        let stranger = PlaceFact(id: "p9", lat: 10, lng: 10, radiusM: 100, type: .trusted)

        await rig.engine.onTransition(.enter, zoneKey: stranger.key, atMillis: rig.env.now)

        XCTAssertTrue(rig.store.statusWrites().isEmpty)
    }

    func testTheEventsOfTheZoneNeverReadThePositionButAnArrivalRecomputesTheList() async {
        await rig.engine.refresh(.opened)
        let before = rig.position.calls

        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: rig.env.now)
        XCTAssertEqual(rig.position.calls, before, "une sortie ne lit aucune position")

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        XCTAssertEqual(rig.position.calls, before + 1, "une entrée relit la position et recalcule les 20 zones")
    }

    func testDangerZoneWritesInRiskAndTrustedWinsWhenTheyOverlap() async {
        let overlap = TrackingRig(places: [TrackingFixtures.home, TrackingFixtures.danger])
        await overlap.engine.refresh(.opened)

        await overlap.engine.onTransition(.enter, zoneKey: TrackingFixtures.danger.key, atMillis: overlap.env.now)
        await overlap.engine.onTransition(.enter, zoneKey: TrackingFixtures.home.key, atMillis: overlap.env.now)
        await overlap.engine.onTransition(.exit, zoneKey: TrackingFixtures.home.key, atMillis: overlap.env.now)

        let values = overlap.store.statusWrites().map { write -> String? in
            if case .string(let text)? = write.data["presence"] {
                return text
            }
            return nil
        }
        XCTAssertEqual(values, ["in_risk", "in_trusted", "in_risk"])
    }

    func testDeviceAlreadyInsideAtArmingIsAnArrivalAndOutsideInventsNothing() async {
        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: false)
        XCTAssertTrue(rig.store.statusWrites().isEmpty, "dehors à l'armement : aucun « out » inventé")

        await rig.engine.refresh(.opened)
        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: true)

        XCTAssertEqual(rig.store.statusWrites().count, 1)
        XCTAssertEqual(presence(of: rig.store.statusWrites()[0]), .string("in_trusted"))
    }

    func testAMissedExitBecomesUnknownAndNeverOut() async throws {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: false)

        let last = try XCTUnwrap(rig.store.statusWrites().last)
        XCTAssertEqual(presence(of: last), .string("unknown"))
        XCTAssertEqual(last.data["placeId"], .null)
        XCTAssertNil(last.data["lastUpdateAt"])
    }

    // MARK: - Les deux Miffs et le même lieu (MIF3)

    func testTheSamePlaceInTwoMiffsIsOneZoneAndTwoStatuses() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.geofences.lastArmed.count, 1)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        let paths = Set(rig.store.statusWrites().map { $0.path })
        XCTAssertEqual(paths, ["miffs/\(testMiffId)/status/uid-1", "miffs/\(testOtherMiffId)/status/uid-1"])
        for commit in rig.store.commits where commit.contains(where: { $0.path.contains("/status/") }) {
            XCTAssertEqual(commit.count, 1, "un seul document par lot, jamais mêlé à un autre")
        }
    }

    func testOneMiffRefusedDoesNotStopTheOtherOne() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await rig.engine.refresh(.opened)
        let denied = firestoreError(FirestoreErrorNumber.permissionDenied)
        rig.store.failingPaths["miffs/\(testMiffId)/status/uid-1"] = denied

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertEqual(rig.store.statusWrites().map { $0.path }, ["miffs/\(testOtherMiffId)/status/uid-1"])
    }
}
