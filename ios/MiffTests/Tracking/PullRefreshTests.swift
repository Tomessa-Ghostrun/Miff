// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// « Tirer pour actualiser » sur le Feed revérifie aussi MA présence (demande du PM du 2026-11-17, `docs/tickets/feed-refresh-position.md`) :
/// le moteur habituel mesure UNE position fraîche, redemande au système l'état des zones que cette position contredit, n'écrit un statut
/// que si la présence change, ne republie la dernière position que si le garde-fou de fréquence la laisse passer, et ne fait rien sans
/// accord, sans « Toujours » + position exacte, sans partage, ni hors connexion. Doubles pour le téléphone et le système : aucune vraie position.
final class PullRefreshEngineTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute

    /// L'app est ouverte chez soi : zones armées, position publiée, le système a dit « dedans » (une arrivée, un statut écrit).
    private func openedAtHome() async {
        await rig.engine.refresh(.opened)
        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: true)
        XCTAssertEqual(rig.store.statusWrites().count, 1)
        XCTAssertEqual(rig.store.positionWrites().count, 1)
    }

    private func presence(_ write: DocWrite?) -> FieldValueSpec? {
        write?.data["presence"]
    }

    // MARK: - La mesure

    func testPullingMeasuresAFreshPositionAndNeverReusesTheOneTheSystemKnows() async {
        rig.position.recentSystemFix = TrackingFixtures.fixHome(at: rig.env.now - 20_000)

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.position.freshCalls, 1, "une mesure ponctuelle demandée au système")
        XCTAssertEqual(rig.position.reusingCalls, 0, "jamais la position réutilisée de BATT-3")
        XCTAssertFalse(rig.journal.kinds.contains(.positionReused))
        XCTAssertTrue(rig.journal.kinds.contains(.positionRead))
        XCTAssertFalse(TrackingEngine.mayReuseSystemFix(for: .pulled))
    }

    func testPullingTwiceInARowAsksTheSystemTwiceTheLimitOfFrequencyIsTheRuntimesJob() async {
        await rig.engine.refresh(.pulled)
        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.position.freshCalls, 2)
    }

    func testPullingNeverRefreshesTheBattery() async {
        rig.env.battery = 80
        await rig.engine.refresh(.pulled)

        XCTAssertFalse(rig.journal.kinds.contains(.batteryRefreshed))
        XCTAssertFalse(rig.journal.kinds.contains(.batteryRefreshSkipped), "la batterie n'est pas du ressort de ce geste")
    }

    // MARK: - La présence est recalculée par le moteur habituel

    func testPullingAsksTheSystemAgainForAZoneTheNewPositionContradictsAndTheMissedExitIsSettledAtTheInstantOfTheAnswer() async {
        await openedAtHome()
        rig.env.now += 20 * minute
        rig.putPositionFar()
        let askedBefore = rig.geofences.requestedStates.count

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.geofences.requestedStates.count, askedBefore + 1)
        XCTAssertEqual(rig.geofences.requestedStates.last, [rig.homeKey])
        XCTAssertEqual(rig.store.statusWrites().count, 1, "rien n'est écrit tant que le système n'a pas répondu")

        // Le système répond « dehors » : la sortie manquée est réglée, à l'heure de la réponse (jamais une heure inventée).
        rig.env.now += 2000
        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: false)

        XCTAssertEqual(rig.store.statusWrites().count, 2)
        XCTAssertEqual(presence(rig.store.statusWrites().last), .string("unknown"))
        XCTAssertEqual(rig.store.statusWrites().last?.data["presenceAt"], .timestamp(millis: rig.env.now))
    }

    func testPullingFindsAMissedArrivalAndWritesItAtTheInstantTheSystemAnswers() async {
        await rig.engine.refresh(.opened)
        XCTAssertTrue(rig.store.statusWrites().isEmpty, "le système n'a pas encore répondu")
        rig.env.now += 20 * minute
        rig.putPositionHome()
        let askedBefore = rig.geofences.requestedStates.count

        await rig.engine.refresh(.pulled)
        XCTAssertEqual(rig.geofences.requestedStates.count, askedBefore + 1, "l'arrivée manquée est redemandée au système")

        rig.env.now += 3000
        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: true)

        XCTAssertEqual(rig.store.statusWrites().count, 1)
        XCTAssertEqual(presence(rig.store.statusWrites().first), .string("in_trusted"))
        XCTAssertEqual(rig.store.statusWrites().first?.data["presenceAt"], .timestamp(millis: rig.env.now))
        XCTAssertTrue(rig.journal.kinds.contains(.regionInside))
    }

    // MARK: - Aucune écriture inutile

    func testPullingWritesNothingWhenNothingChanged() async {
        await openedAtHome()
        rig.env.now += 1 * minute
        rig.putPositionHome()
        let attempts = rig.store.commitAttempts
        let asked = rig.geofences.requestedStates.count
        let measures = rig.position.freshCalls

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.position.freshCalls, measures + 1)
        XCTAssertEqual(rig.store.commitAttempts, attempts, "ni statut ni position : zéro écriture, zéro tentative")
        XCTAssertEqual(rig.geofences.requestedStates.count, asked, "la position confirme ce que l'on croit : rien à redemander")
        XCTAssertTrue(rig.journal.kinds.contains(.positionSkippedGuard))
    }

    func testPullingRepublishesThePositionOnlyOnceTheUsualGuardLetsItThrough() async {
        await openedAtHome()
        rig.env.now += 16 * minute
        rig.putPositionHome()
        let statuses = rig.store.statusWrites().count

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.store.positionWrites().count, 2, "plus de 15 minutes : le dernier signal est périmé")
        XCTAssertEqual(rig.store.statusWrites().count, statuses, "la présence n'a pas changé : aucun statut")
    }

    func testPullingAfterAWalkOfMoreThan250MetersRepublishesThePositionBeforeFifteenMinutes() async {
        await openedAtHome()
        rig.env.now += 3 * minute
        rig.putPositionFar()

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.store.positionWrites().count, 2)
    }

    func testTheGuardOfTwoMinutesAlsoHoldsForThePull() async {
        await openedAtHome()
        rig.env.now += 1 * minute
        rig.putPositionFar()

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.store.positionWrites().count, 1, "moins de deux minutes : jamais deux écritures de position")
    }

    // MARK: - Autorisations

    func testWithoutAlwaysAndThePreciseLocationPullingMeasuresAndWritesNothing() async {
        for snapshot in [
            PermissionSnapshot(location: .whenInUse, notifications: .authorized),
            PermissionSnapshot(location: .always, preciseLocation: false, notifications: .authorized),
            PermissionSnapshot(location: .always, servicesEnabled: false, notifications: .authorized),
            PermissionSnapshot(location: .denied, notifications: .authorized),
            PermissionSnapshot(location: .notDetermined, notifications: .authorized)
        ] {
            let fresh = TrackingRig()
            fresh.env.snapshot = snapshot

            await fresh.engine.refresh(.pulled)

            XCTAssertEqual(fresh.position.calls, 0, "aucune position lue : \(snapshot)")
            XCTAssertEqual(fresh.store.commitAttempts, 0, "rien d'envoyé : \(snapshot)")
            XCTAssertTrue(fresh.geofences.requestedStates.isEmpty)
        }
    }

    func testTheAccessRemovedAfterOpeningStopsTheNextPullAndErasesThePosition() async throws {
        await openedAtHome()
        rig.env.snapshot = PermissionSnapshot(location: .whenInUse, notifications: .authorized)
        rig.env.now += 20 * minute
        let measures = rig.position.calls

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.position.calls, measures)
        XCTAssertNil(try XCTUnwrap(rig.local.state).fix)
    }

    // MARK: - Partage, session, voyage

    func testWithTheSharingStoppedEverywherePullingDoesNothing() async {
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: false, version: MiffSharingConsent.version)

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.position.calls, 0)
        XCTAssertEqual(rig.store.commitAttempts, 0)
        XCTAssertTrue(rig.geofences.requestedStates.isEmpty)
    }

    func testWithoutTheSessionOfThisPhonePullingWritesNothing() async {
        await rig.engine.refresh(.opened)
        rig.device.clearanceResult = .replaced
        rig.env.now += 20 * minute
        rig.putPositionFar()
        let attempts = rig.store.commitAttempts

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.store.commitAttempts, attempts)
    }

    func testDuringATripPullingWritesNoStatusAndNeverArmsAZoneButKeepsThePositionAsOpeningDoes() async {
        await rig.engine.refresh(.opened)
        let trip = TravelPeriod(untilMillis: TrackingFixtures.noon + 2 * 24 * TrackingFixtures.hour, untilDay: "2026-11-04")
        _ = await rig.engine.setTravel(trip)
        let statuses = rig.store.statusWrites().count
        let positions = rig.store.positionWrites().count
        let asked = rig.geofences.requestedStates.count
        rig.env.now += 20 * minute
        rig.putPositionFar()

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.store.statusWrites().count, statuses, "aucune présence, aucune batterie pendant un voyage")
        XCTAssertEqual(rig.geofences.requestedStates.count, asked, "les zones ne sont pas surveillées pendant un voyage")
        XCTAssertEqual(rig.store.positionWrites().count, positions + 1, "la position reste partagée (décision du PM du 2026-11-10)")
    }

    // MARK: - Hors connexion et délai

    func testOfflinePullingNeverTriesToWriteAnythingAndKeepsTheLocalCopy() async {
        await openedAtHome()
        rig.env.online = false
        rig.env.now += 20 * minute
        rig.putPositionFar()
        let attempts = rig.store.commitAttempts

        await rig.engine.refresh(.pulled)

        XCTAssertEqual(rig.store.commitAttempts, attempts)
        XCTAssertTrue(rig.journal.kinds.contains(.positionSkippedOffline))
        XCTAssertNotNil(rig.local.state)
    }

    func testAPositionThatNeverComesEndsThePassAfterTheShortWaitWithoutWritingAnything() async {
        let slow = NeverAnsweringPosition()
        let engine = TrackingEngine(
            TrackingDependencies(
                env: rig.env,
                store: rig.store,
                local: rig.local,
                position: slow,
                geofences: rig.geofences,
                movement: rig.movement,
                device: rig.device,
                journal: rig.journal,
                timeoutSeconds: 2,
                generation: { 0 }
            )
        )
        let started = Date()

        await engine.refresh(.pulled)

        XCTAssertLessThan(Date().timeIntervalSince(started), TrackingLimits.positionWaitSeconds + 2)
        XCTAssertTrue(rig.journal.kinds.contains(.positionUnavailable))
        XCTAssertTrue(rig.store.positionWrites().isEmpty)
        XCTAssertEqual(slow.asked, 1)
    }
}

/// Un système qui ne répond jamais à la demande de position (GPS à froid, sous-sol) : le moteur n'attend que le délai court.
private final class NeverAnsweringPosition: PositionSource {
    private(set) var asked = 0

    func current() async -> Fix? {
        asked += 1
        try? await Task.sleep(nanoseconds: 20_000_000_000)
        return nil
    }

    func current(within seconds: Double) async -> Fix? {
        await current()
    }

    func currentReusingRecent() async -> PositionReading {
        PositionReading(fix: await current(), reused: false)
    }
}

/// Le pont entre le geste du Feed et le moteur : une revérification par 30 s au plus, ouverture de l'app comprise, rendue au système
/// comme tout travail du suivi (temps d'arrière-plan demandé puis rendu).
@MainActor
final class PullRefreshRuntimeTests: XCTestCase {
    private let rig = TrackingRig()
    private let background = FakeBackgroundExecution()
    private let clock = TestBox<Int>(TrackingFixtures.noon)

    private func runtime() -> TrackingRuntime {
        let box = clock
        return TrackingRuntime(
            engine: rig.engine, background: background, journal: rig.journal, beforeWork: {}, clock: { box.value }
        )
    }

    func testPullingRunsTheEngineOnceWithAFreshMeasureAndGivesTheBackgroundTimeBack() async {
        let runtime = runtime()

        runtime.feedPulled()
        await waitUntil { background.ended.count == 1 }

        XCTAssertEqual(background.begun, 1)
        XCTAssertEqual(background.ended, [1])
        XCTAssertEqual(rig.position.freshCalls, 1)
        XCTAssertEqual(rig.position.reusingCalls, 0)
        XCTAssertEqual(rig.store.positionWrites().count, 1)
        XCTAssertEqual(rig.journal.kinds.filter { $0 == .wakePull }.count, 1)
    }

    func testTwoPullsCloserThanThirtySecondsAreOneAndTheThirtiethSecondIsAllowed() async {
        let runtime = runtime()

        runtime.feedPulled()
        clock.value += TrackingLimits.pullMinIntervalMillis - 1
        runtime.feedPulled()
        await waitUntil { background.ended.count == 1 }
        XCTAssertEqual(background.begun, 1, "29,999 s : ignoré, sans bruit")

        clock.value += 1
        runtime.feedPulled()
        await waitUntil { background.ended.count == 2 }
        XCTAssertEqual(background.begun, 2)
        XCTAssertEqual(rig.journal.kinds.filter { $0 == .wakePull }.count, 2)
    }

    func testOpeningTheAppJustMeasuredSoAPullRightAfterDoesNotMeasureAgain() async {
        let runtime = runtime()

        runtime.appBecameActive()
        await waitUntil { background.ended.count == 1 }
        clock.value += 10_000
        runtime.feedPulled()
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(background.begun, 1)
        XCTAssertEqual(rig.position.calls, 1)
        XCTAssertFalse(rig.journal.kinds.contains(.wakePull))

        clock.value += 25_000
        runtime.feedPulled()
        await waitUntil { background.ended.count == 2 }
        XCTAssertEqual(background.begun, 2, "35 s après l'ouverture : permis")
    }

    func testAClockThatWentBackwardsNeverBlocksThePull() async {
        let runtime = runtime()

        runtime.feedPulled()
        clock.value -= 5 * 60 * 1000
        runtime.feedPulled()
        await waitUntil { background.ended.count == 2 }

        XCTAssertEqual(background.begun, 2)
    }

    func testPullsAreProcessedAfterThePreviousWorkInTheOrderOfArrival() async {
        await rig.engine.refresh(.opened)
        let runtime = runtime()

        runtime.zoneTransition(.enter, zoneKey: rig.homeKey)
        runtime.feedPulled()
        await waitUntil { background.ended.count == 2 }

        XCTAssertEqual(rig.store.statusWrites().count, 1, "l'arrivée d'abord, puis la revérification qui ne change rien")
        XCTAssertEqual(background.ended.count, 2)
    }

    func testWithoutAccessPullingThroughTheRuntimeReadsNoPositionAndSendsNothing() async {
        rig.env.snapshot = PermissionSnapshot(location: .whenInUse, notifications: .authorized)
        let runtime = runtime()

        runtime.feedPulled()
        await waitUntil { background.ended.count == 1 }

        XCTAssertEqual(rig.position.calls, 0)
        XCTAssertEqual(rig.store.commitAttempts, 0)
    }
}
