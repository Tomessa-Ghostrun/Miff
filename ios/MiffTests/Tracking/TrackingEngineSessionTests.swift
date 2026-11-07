import XCTest
@testable import Miff

/// Le moteur de suivi (LOC-4b) face à la session d'appareil, à l'arrêt, aux autorisations retirées, aux lieux modifiés et aux 20 zones.
/// Mêmes scénarios qu'Android (LOC-2b).
final class TrackingEngineSessionTests: XCTestCase {
    private let rig = TrackingRig()
    private let hour = TrackingFixtures.hour

    private var pending: PendingStatus? {
        rig.local.state?.miffs[testMiffId]?.pending
    }

    // MARK: - Un compte, un seul appareil (SESS-3)

    func testAReplacedPhoneWritesNothingAndKeepsWhatWaits() async {
        rig.device.clearanceResult = .replaced

        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertNotNil(pending)
    }

    func testASessionThatCannotBeVerifiedWritesNothingEither() async {
        rig.device.clearanceResult = .unverified

        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertNotNil(pending)
    }

    func testTheSessionIsAskedOncePerPassAndBeforeTheFirstWriteOnly() async {
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.device.clearanceCalls, 1)

        let idle = TrackingRig()
        idle.position.fix = nil
        await idle.engine.refresh(.opened)
        XCTAssertEqual(idle.device.clearanceCalls, 0, "un passage qui n'écrit rien ne lit pas la session")
    }

    func testARevokedPhoneStopsEverythingAndErasesTheLocalCopy() async {
        await rig.engine.refresh(.opened)
        rig.device.revoked = true

        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertNil(rig.local.state)
        XCTAssertTrue(rig.store.statusWrites().isEmpty)
        XCTAssertGreaterThanOrEqual(rig.geofences.disarmCalls, 1)
    }

    func testARefusedWriteMakesTheSessionBeReadAgainAndIsNotRetried() async {
        await rig.engine.refresh(.opened)
        rig.store.commitFailure = firestoreError(FirestoreErrorNumber.permissionDenied)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertGreaterThanOrEqual(rig.device.refusalChecks, 1)
        XCTAssertNil(pending, "un refus n'est pas rejoué en boucle")
        XCTAssertTrue(rig.journal.kinds.contains(.statusRefused))
    }

    func testARefusalThatTurnsOutToBeAReplacementStopsTheOtherWritesOfThePass() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await rig.engine.refresh(.opened)
        let denied = firestoreError(FirestoreErrorNumber.permissionDenied)
        rig.store.failingPaths["miffs/\(testMiffId)/status/uid-1"] = denied
        rig.device.afterRefusalResult = .replaced

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertTrue(rig.store.statusWrites().isEmpty, "l'autre Miff n'est pas écrite par un téléphone remplacé")
    }

    // MARK: - Arrêt, perte d'accès, autorisation retirée (DET4, VIE3)

    func testStopErasesEverythingAndStaysStoppedUntilTheSessionChanges() async {
        await rig.engine.refresh(.opened)
        XCTAssertNotNil(rig.local.state)

        await rig.engine.stop()

        XCTAssertNil(rig.local.state)
        XCTAssertGreaterThanOrEqual(rig.geofences.disarmCalls, 1)
        XCTAssertEqual(rig.journal.clears, 1)
        let calls = rig.position.calls
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        await rig.engine.onNetworkBack()
        XCTAssertEqual(rig.position.calls, calls)
        XCTAssertNil(rig.local.state)
        rig.generation = 1
        await rig.engine.refresh(.opened)
        XCTAssertGreaterThan(rig.position.calls, calls, "une nouvelle session reprend")
    }

    func testWithdrawingAlwaysWritesTheAccessOnceAndErasesWhatDependsOnThePosition() async throws {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        let calls = rig.position.calls
        rig.env.snapshot = PermissionSnapshot(location: .whenInUse, notifications: .authorized)

        await rig.engine.refresh(.opened)

        let access = try XCTUnwrap(rig.store.statusWrites().last)
        XCTAssertEqual(access.data["locationAccess"], .string("foreground"))
        XCTAssertEqual(Set(access.data.keys), ["batteryPct", "locationAccess", "updatedAt", "deviceId"])
        XCTAssertEqual(rig.position.calls, calls, "aucune position n'est lue sans « Toujours »")
        let state = try XCTUnwrap(rig.local.state)
        XCTAssertNil(state.fix)
        XCTAssertTrue(state.inside.isEmpty)
        XCTAssertTrue(state.armed.isEmpty)
        XCTAssertNil(state.miffs[testMiffId]?.derived)
        XCTAssertNil(state.miffs[testMiffId]?.pending)
        XCTAssertNil(state.miffs[testMiffId]?.written)
        let count = rig.store.writes.count
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.store.writes.count, count, "écrit une seule fois par changement")
    }

    func testTheLocationOfThePhoneSwitchedOffIsToldAsDenied() async throws {
        await rig.engine.refresh(.opened)
        rig.env.snapshot = PermissionSnapshot(location: .always, servicesEnabled: false, notifications: .authorized)

        await rig.engine.refresh(.opened)

        let access = try XCTUnwrap(rig.store.statusWrites().last)
        XCTAssertEqual(access.data["locationAccess"], .string("denied"))
    }

    func testLosingTheAccessToAMiffStopsWatchingItsPlaces() async {
        await rig.engine.refresh(.opened)
        rig.env.now += 2 * hour
        rig.store.consentFailure = firestoreError(FirestoreErrorNumber.permissionDenied)

        await rig.engine.refresh(.opened)

        XCTAssertTrue(rig.local.state?.miffs.isEmpty == true)
        XCTAssertGreaterThanOrEqual(rig.geofences.disarmCalls, 1)
        XCTAssertTrue(rig.journal.kinds.contains(.notEligible))
    }

    func testANetworkCutIsNeverALossOfAccess() async {
        await rig.engine.refresh(.opened)
        rig.env.now += 2 * hour
        rig.store.consentFailure = firestoreError(FirestoreErrorNumber.unavailable)

        await rig.engine.refresh(.opened)

        XCTAssertNotNil(rig.local.state?.miffs[testMiffId], "ce que le téléphone savait est gardé")
        XCTAssertEqual(rig.geofences.lastArmed.count, 1)
    }

    func testTheLocalCopyOfAnotherAccountIsErasedAndNeverUsed() async {
        var foreign = LocalState(uid: "someone-else")
        foreign.miffs[testMiffId] = LocalMiff(places: [LocalPlace(fact: TrackingFixtures.work)])
        rig.local.state = foreign

        await rig.engine.refresh(.opened)

        XCTAssertGreaterThanOrEqual(rig.local.clears, 1)
        XCTAssertEqual(rig.local.state?.uid, "uid-1")
        XCTAssertEqual(rig.geofences.lastArmed.map { $0.placeId }, ["p1"])
    }

    // MARK: - Un lieu modifié, supprimé (cas limite 6 du ticket)

    func testAPlaceMovedByItsCreatorWhileThePersonWasInsideBecomesUnknown() async throws {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        let moved = PlaceFact(
            id: "p1",
            lat: TrackingFixtures.homeLat + 0.01,
            lng: TrackingFixtures.homeLng,
            radiusM: 100,
            type: .trusted
        )
        rig.store.places[testMiffId] = [moved]
        rig.env.now += 2 * hour
        rig.putPositionHome()

        await rig.engine.refresh(.opened)

        let last = try XCTUnwrap(rig.store.statusWrites().last)
        XCTAssertEqual(last.data["presence"], .string("unknown"))
        XCTAssertEqual(rig.geofences.lastArmed.map { $0.key }, [moved.key], "la nouvelle zone remplace l'ancienne")
    }

    func testAPlaceDeletedWhileThePersonWasInsideBecomesUnknown() async throws {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        rig.store.places[testMiffId] = []
        rig.env.now += 2 * hour
        rig.putPositionHome()

        await rig.engine.refresh(.opened)

        let last = try XCTUnwrap(rig.store.statusWrites().last)
        XCTAssertEqual(last.data["presence"], .string("unknown"))
        XCTAssertTrue(rig.geofences.lastArmed.isEmpty)
    }

    // MARK: - Vingt zones au plus (IOS3, POS2)

    func testTwoMiffsOfElevenPlacesWatchTwentyZonesAndTheNearestOnes() async {
        let mine = (0..<11).map { index in
            PlaceFact(id: "a\(index)", lat: 48.0 + Double(index) * 0.01, lng: 2.0, radiusM: 100, type: .trusted)
        }
        let theirs = (0..<11).map { index in
            PlaceFact(id: "b\(index)", lat: 48.0, lng: 2.5 + Double(index) * 0.01, radiusM: 100, type: .trusted)
        }
        let crowded = TrackingRig(places: mine)
        crowded.addMiff(testOtherMiffId, places: theirs)
        crowded.position.fix = Fix(lat: 48.0, lng: 2.0, measuredAtMillis: crowded.env.now)

        await crowded.engine.refresh(.opened)

        let chosen = crowded.geofences.lastArmed
        XCTAssertEqual(chosen.count, 20)
        XCTAssertEqual(chosen.filter { $0.placeId.hasPrefix("a") }.count, 11)
        XCTAssertEqual(chosen.filter { $0.placeId.hasPrefix("b") }.count, 9)
    }

    func testTheJournalAndTheLocalCopyNeverHoldACoordinateOrAZoneKey() async {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: rig.env.now)

        for line in rig.journal.recorded {
            let text = "\(line)"
            XCTAssertTrue(line.placeId == nil || line.placeId == "p1", text)
            XCTAssertFalse(text.contains("48.85"), text)
            XCTAssertFalse(text.contains("2.35"), text)
            XCTAssertFalse(text.contains("|"), text)
        }
        XCTAssertFalse(rig.journal.recorded.isEmpty)
        XCTAssertFalse("\(String(describing: rig.local.state))".contains("48.85"))
    }
}
