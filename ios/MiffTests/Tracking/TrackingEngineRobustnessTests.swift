import XCTest
@testable import Miff

/// Les défauts trouvés par la revue indépendante de LOC-4b (PR #98) : lieux relus tout de suite, sortie suivie d'un « dehors », entrée
/// vue deux fois, changement significatif redemandé à chaque processus, ouverture sans réenregistrement, états redemandés seulement
/// quand la position contredit ce que l'on croit.
final class TrackingEngineRobustnessTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute

    private func opened(_ rig: TrackingRig) async {
        await rig.engine.refresh(.opened)
    }

    // MARK: - Les lieux changent : pris en compte tout de suite (étape A de la recette)

    func testAPlaceCreatedMovedResizedOrDeletedIsTakenIntoAccountWithoutWaitingAnHour() async {
        let lat = TrackingFixtures.homeLat
        let lng = TrackingFixtures.homeLng
        let moved = PlaceFact(id: "p1", lat: lat + 0.01, lng: lng, radiusM: 100, type: .trusted)
        let resized = PlaceFact(id: "p1", lat: lat, lng: lng, radiusM: 300, type: .trusted)
        let variants: [[PlaceFact]] = [[TrackingFixtures.home, TrackingFixtures.work], [moved], [resized], []]

        for places in variants {
            let fresh = TrackingRig()
            await opened(fresh)
            fresh.store.places[testMiffId] = places
            fresh.env.now += 5 * minute
            fresh.putPositionHome()

            await fresh.engine.refresh(.sharingChanged)

            XCTAssertEqual(
                Set(fresh.geofences.lastArmed.map { $0.key }),
                Set(places.map { $0.key }),
                "\(places.count) lieu(x)"
            )
        }
    }

    func testAnOrdinaryOpeningDoesNotRereadThePlacesBeforeAnHourAndTheReadCostsThreeReads() async {
        await opened(rig)
        rig.store.places[testMiffId] = [TrackingFixtures.work]
        rig.env.now += 5 * minute
        rig.putPositionHome()

        await opened(rig)
        XCTAssertEqual(rig.geofences.lastArmed.map { $0.placeId }, ["p1"], "une ouverture ne relit pas avant une heure")

        let before = rig.store.readCount
        await rig.engine.refresh(.sharingChanged)
        XCTAssertEqual(rig.store.readCount - before, 3, "Miffs, consentement, lieux : une fois, sans boucle")
        XCTAssertEqual(rig.geofences.lastArmed.map { $0.placeId }, ["p2"])
    }

    // MARK: - Une sortie, puis un « dehors »

    func testAnOutsideRightAfterAnExitIsNotAMissedExit() async {
        await opened(rig)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: rig.env.now)

        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: false)

        let values = rig.store.statusWrites().map { write -> String? in
            if case .string(let text)? = write.data["presence"] {
                return text
            }
            return nil
        }
        XCTAssertEqual(values, ["in_trusted", "out"], "un départ n'écrit jamais unknown avant out")
    }

    func testAnEntryAnnouncedTwiceWritesOnceAndReadsThePositionOnce() async {
        await opened(rig)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        let calls = rig.position.calls
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertEqual(rig.store.statusWrites().count, 1)
        XCTAssertEqual(rig.position.calls, calls, "la seconde entrée ne relit rien")
    }

    // MARK: - Ouverture sans réenregistrement, changement significatif redemandé

    func testAnOrdinaryOpeningRegistersNothingAgainAndAsksNoStateOnceTheArrivalIsKnown() async {
        await opened(rig)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        rig.env.now += 20 * minute
        rig.putPositionHome()

        await opened(rig)

        XCTAssertEqual(Set(rig.geofences.reregisterFlags), [false])
        XCTAssertEqual(rig.journal.kinds.filter { $0 == .armed }.count, 1, "le journal ne note que le vrai changement")
        XCTAssertEqual(rig.movement.starts, 1)
        XCTAssertEqual(rig.geofences.requestedStates.count, 1, "seulement la première fois (arrivée jamais vue)")
    }

    func testARearmRegistersEverythingAgain() async {
        await opened(rig)

        await rig.engine.refresh(.rearm)

        XCTAssertEqual(rig.geofences.reregisterFlags.last, true)
    }

    func testAProcessRelaunchedByTheSystemAsksForTheSignificantChangesAgain() async {
        await opened(rig)
        let relaunched = TrackingRig()
        relaunched.local.state = rig.local.state
        XCTAssertEqual(relaunched.local.state?.updatesArmed, true, "la copie locale dit que c'était déjà fait")

        await relaunched.engine.refresh(.moved, supplied: TrackingFixtures.fixFar(at: relaunched.env.now))
        await relaunched.engine.refresh(.backgrounded)

        XCTAssertEqual(relaunched.movement.starts, 1, "une fois par processus, pas à chaque passage")
    }

    func testAProcessRelaunchedByAMereExitAsksForTheSignificantChangesRightAway() async {
        await opened(rig)
        let relaunched = TrackingRig()
        relaunched.local.state = rig.local.state

        await relaunched.engine.onTransition(.exit, zoneKey: relaunched.homeKey, atMillis: relaunched.env.now)
        XCTAssertEqual(relaunched.movement.starts, 1, "dès la première relance, sans attendre une passe de rafraîchissement")
        await relaunched.engine.onTransition(.enter, zoneKey: relaunched.homeKey, atMillis: relaunched.env.now)

        XCTAssertEqual(relaunched.movement.starts, 1, "une seule fois par processus")
    }

    func testStoppingTheMovementMakesTheNextArmingAskAgain() async {
        await opened(rig)
        rig.env.snapshot = PermissionSnapshot(location: .whenInUse, notifications: .authorized)
        await opened(rig)
        rig.env.snapshot = PermissionSnapshot(location: .always, notifications: .authorized)
        rig.env.now += minute
        rig.putPositionHome()

        await opened(rig)

        XCTAssertEqual(rig.movement.starts, 2)
    }

    // MARK: - États redemandés seulement quand la position contredit ce que l'on croit

    func testTheStateIsAskedAgainForAZoneWhereWeBelieveToBeButThePositionSaysNoAndForAZoneWeMissed() async {
        await opened(rig)
        XCTAssertEqual(rig.geofences.requestedStates, [[rig.homeKey]], "dedans et jamais arrivé : arrivée manquée")
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        rig.env.now += 5 * minute
        rig.putPositionHome()
        await opened(rig)
        XCTAssertEqual(rig.geofences.requestedStates.count, 1, "position et croyance d'accord : rien à redemander")

        rig.env.now += 5 * minute
        rig.putPositionFar()
        await opened(rig)

        XCTAssertEqual(rig.geofences.requestedStates.last, [rig.homeKey], "on croit être dedans, la position dit non")
    }
}

/// Le pont entre le système et le moteur (LOC-4b) avec un faux gestionnaire de localisation : doublons, état demandé, autorisation,
/// position unique abandonnée, zones enregistrées.
@MainActor
final class LocationTrackingTests: XCTestCase {
    private let home = TrackingFixtures.home
    private let work = TrackingFixtures.work

    private func zone(_ place: PlaceFact) -> TrackedZone {
        TrackedZone(
            key: place.key,
            placeId: place.id,
            lat: place.lat,
            lng: place.lng,
            radiusM: place.radiusM,
            type: place.type,
            miffIds: [testMiffId]
        )
    }

    private struct Rig {
        let tracking: LocationTracking
        let manager: FakeLocationManager
        let sink: RecordingEventSink
    }

    private func make(wait: Double = 0.05) -> Rig {
        let manager = FakeLocationManager()
        let sink = RecordingEventSink()
        let tracking = LocationTracking(manager: manager, clock: { TrackingFixtures.noon }, waitSeconds: wait)
        tracking.attach(sink)
        return Rig(tracking: tracking, manager: manager, sink: sink)
    }

    // MARK: - Événements de zone

    func testARegionEventIsForwardedWithTheKindAndTheZoneAndAForeignIdentifierIsIgnored() {
        let rig = make()

        rig.manager.emit(.region(.enter, home.key))
        rig.manager.emit(.region(.exit, home.key))
        rig.manager.emit(.region(.enter, "pas-une-cle"))

        XCTAssertEqual(rig.sink.transitions, ["enter p1", "exit p1"])
    }

    func testTheStateOfARegionIsOnlyForwardedWhenWeAskedForItAndOnlyOnce() async {
        let rig = make()
        rig.manager.held = [home.key]

        rig.manager.emit(.regionState(home.key, false))
        XCTAssertTrue(rig.sink.states.isEmpty, "une réponse non demandée (franchissement) est ignorée")

        await rig.tracking.requestStates(keys: [home.key])
        XCTAssertEqual(rig.manager.askedStates, [home.key])
        rig.manager.emit(.regionState(home.key, false))
        rig.manager.emit(.regionState(home.key, false))

        XCTAssertEqual(rig.sink.states, ["p1 false"])
    }

    func testTheStateOfAZoneWeDoNotHoldIsNotAsked() async {
        let rig = make()

        await rig.tracking.requestStates(keys: [home.key])

        XCTAssertTrue(rig.manager.askedStates.isEmpty)
    }

    // MARK: - Autorisation

    func testOnlyARealChangeOfTheAuthorizationIsSignalled() {
        let rig = make()
        let always = AuthorizationState(location: .always, fullAccuracy: true)
        let reduced = AuthorizationState(location: .always, fullAccuracy: false)

        rig.manager.emit(.authorization(always))
        XCTAssertEqual(rig.sink.accessChanges, 0, "le premier rappel du système fixe seulement la référence")
        rig.manager.emit(.authorization(always))
        XCTAssertEqual(rig.sink.accessChanges, 0)
        rig.manager.emit(.authorization(reduced))
        XCTAssertEqual(rig.sink.accessChanges, 1)
        rig.manager.emit(.authorization(reduced))
        XCTAssertEqual(rig.sink.accessChanges, 1)
    }

    func testAFailureOfTheMonitoringIsForwarded() {
        let rig = make()

        rig.manager.emit(.monitoringFailed)

        XCTAssertEqual(rig.sink.failures, 1)
    }

    // MARK: - Position unique et changement significatif

    func testAPositionAskedByTheEngineIsTheAnswerAndNotASignificantMove() async {
        let rig = make()
        let fix = TrackingFixtures.fixHome(at: TrackingFixtures.noon)
        let asking = Task { await rig.tracking.current() }
        await waitUntil { rig.manager.positionAsks == 1 }

        rig.manager.emit(.fix(fix))
        let answer = await asking.value

        XCTAssertEqual(answer, fix)
        XCTAssertTrue(rig.sink.moves.isEmpty)
    }

    func testAPositionNobodyWaitsForIsASignificantMove() {
        let rig = make()
        let fix = TrackingFixtures.fixFar(at: TrackingFixtures.noon)

        rig.manager.emit(.fix(fix))

        XCTAssertEqual(rig.sink.moves, [fix])
    }

    func testAPositionThatArrivesAfterTheWaitIsNotSwallowedByTheAbandonedRequestNorLost() async {
        let rig = make()

        let answer = await rig.tracking.current()
        XCTAssertNil(answer, "le délai est tenu par le pont")
        let late = TrackingFixtures.fixHome(at: TrackingFixtures.noon)
        rig.manager.emit(.fix(late))

        XCTAssertEqual(rig.sink.moves, [late], "elle est transmise au moteur comme un déplacement")
    }

    func testWhenTheSystemFailsTheRecentLastKnownPositionIsTheFallbackAndAnOldOneIsNot() async {
        let rig = make(wait: 5)
        rig.manager.systemFix = TrackingFixtures.fixHome(at: TrackingFixtures.noon - TrackingFixtures.minute)
        let asking = Task { await rig.tracking.current() }
        await waitUntil { rig.manager.positionAsks == 1 }
        rig.manager.emit(.fixFailed)
        let recent = await asking.value
        XCTAssertNotNil(recent)

        rig.manager.systemFix = TrackingFixtures.fixHome(at: TrackingFixtures.noon - 5 * TrackingFixtures.minute)
        let second = Task { await rig.tracking.current() }
        await waitUntil { rig.manager.positionAsks == 2 }
        rig.manager.emit(.fixFailed)
        let old = await second.value
        XCTAssertNil(old)
    }

    func testTheSignificantChangesAreStartedAndStoppedAndRefusedWhenUnavailable() async {
        let rig = make()

        let started = await rig.tracking.start()
        await rig.tracking.stop()
        rig.manager.significantChangesAvailable = false
        let refused = await rig.tracking.start()

        XCTAssertTrue(started)
        XCTAssertFalse(refused)
        XCTAssertEqual(rig.manager.significantStarts, 1)
        XCTAssertEqual(rig.manager.significantStops, 1)
    }

    // MARK: - Zones

    func testArmingRemovesWhatIsHeldAndNotWantedAddsWhatIsMissingAndAsksTheirState() async {
        let rig = make()
        rig.manager.held = [work.key]

        let accepted = await rig.tracking.arm(desired: [zone(home)], reregister: false)

        XCTAssertTrue(accepted)
        XCTAssertEqual(rig.manager.unwatched, [work.key])
        XCTAssertEqual(rig.manager.watched.map { $0.key }, [home.key])
        XCTAssertEqual(rig.manager.askedStates, [home.key])
    }

    func testTheStateOfARegionJustCreatedIsAskedEvenIfTheSystemListDoesNotShowItYet() async {
        let rig = make()
        rig.manager.listLagsBehindWatch = true

        let accepted = await rig.tracking.arm(desired: [zone(home)], reregister: false)

        XCTAssertTrue(accepted)
        XCTAssertTrue(rig.manager.held.isEmpty, "le cas défavorable : la liste du système ne la montre pas encore")
        XCTAssertEqual(rig.manager.askedStates, [home.key], "la demande n'est jamais ignorée en silence")
        XCTAssertTrue(rig.manager.droppedAsks.isEmpty)
        rig.manager.emit(.regionState(home.key, true))
        XCTAssertEqual(rig.sink.states, ["p1 true"], "dedans à l'armement = une arrivée")
    }

    func testARegionNotYetInTheSystemListIsNotWatchedTwiceAndIsRemovedWhenNoLongerWanted() async {
        let rig = make()
        rig.manager.listLagsBehindWatch = true
        _ = await rig.tracking.arm(desired: [zone(home)], reregister: false)

        _ = await rig.tracking.arm(desired: [zone(home)], reregister: false)
        XCTAssertEqual(rig.manager.watched.count, 1, "elle compte comme surveillée tout de suite")

        _ = await rig.tracking.arm(desired: [zone(work)], reregister: false)
        XCTAssertEqual(rig.manager.unwatched, [home.key], "et le système n'en garde pas une que la liste ne veut plus")
    }

    func testDisarmingAlsoRemovesARegionTheSystemListDoesNotShowYet() async {
        let rig = make()
        rig.manager.listLagsBehindWatch = true
        _ = await rig.tracking.arm(desired: [zone(home)], reregister: false)

        await rig.tracking.disarmAll()

        XCTAssertEqual(rig.manager.unwatched, [home.key])
    }

    func testAskingTheStateAgainRightAfterArmingFindsTheNewRegionToo() async {
        let rig = make()
        rig.manager.listLagsBehindWatch = true
        _ = await rig.tracking.arm(desired: [zone(home)], reregister: false)

        await rig.tracking.requestStates(keys: [home.key])

        XCTAssertEqual(rig.manager.askedStates, [home.key, home.key])
        rig.manager.settle()
        XCTAssertEqual(rig.manager.held, [home.key])
    }

    func testArmingAgainTouchesNothingUnlessReregisteringEverything() async {
        let rig = make()
        _ = await rig.tracking.arm(desired: [zone(home), zone(work)], reregister: false)

        _ = await rig.tracking.arm(desired: [zone(home), zone(work)], reregister: false)
        XCTAssertEqual(rig.manager.watched.count, 2)
        XCTAssertEqual(rig.manager.askedStates.count, 2)

        _ = await rig.tracking.arm(desired: [zone(home), zone(work)], reregister: true)
        XCTAssertEqual(rig.manager.watched.count, 4)
        XCTAssertEqual(rig.manager.askedStates.count, 4)
    }

    func testTheRadiusIsCappedAtWhatTheSystemAccepts() async {
        let rig = make()
        rig.manager.maximumRegionRadius = 80

        _ = await rig.tracking.arm(desired: [zone(home)], reregister: false)

        XCTAssertEqual(rig.manager.watched.first?.radius, 80)
    }

    func testArmingIsRefusedWhenTheSystemCannotWatchRegionsAndDisarmingRemovesEverything() async {
        let rig = make()
        rig.manager.regionsAvailable = false
        let refused = await rig.tracking.arm(desired: [zone(home)], reregister: false)
        XCTAssertFalse(refused)
        XCTAssertTrue(rig.manager.watched.isEmpty)

        rig.manager.held = [home.key, work.key]
        await rig.tracking.disarmAll()

        XCTAssertEqual(Set(rig.manager.unwatched), [home.key, work.key])
    }
}
