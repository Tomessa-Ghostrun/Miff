import XCTest
@testable import Miff

/// BATT-3 (`docs/dev/optimisation-batterie.md`, O2) : à l'OUVERTURE de l'app et à sa SORTIE, la dernière position du système est
/// réutilisée si elle a MOINS de deux minutes (au lieu d'un nouveau `requestLocation`). JAMAIS à l'entrée dans une zone, ni pour la
/// mesure de la photo de l'Update, ni pour une position mesurée avant la dernière déconnexion. La précision demandée (100 m) ne change pas.
final class PositionReuseEngineTests: XCTestCase {
    private let rig = TrackingRig()
    private let second = 1000

    private func putRecentSystemFix(secondsOld: Int) {
        rig.position.recentSystemFix = TrackingFixtures.fixHome(at: rig.env.now - secondsOld * second)
    }

    // MARK: - Ouverture et sortie : réutilisation

    func testOpeningReusesTheRecentSystemPositionInsteadOfAskingForAnother() async {
        putRecentSystemFix(secondsOld: 30)

        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.position.reusingCalls, 1)
        XCTAssertEqual(rig.position.freshCalls, 0, "aucune mesure demandée au système")
        XCTAssertEqual(rig.journal.kinds.filter { $0 == .positionReused }.count, 1)
        XCTAssertEqual(rig.store.positionWrites().count, 1, "la position réutilisée sert comme avant")
    }

    func testGoingToTheBackgroundReusesTheRecentSystemPositionToo() async {
        putRecentSystemFix(secondsOld: 90)

        await rig.engine.refresh(.backgrounded)

        XCTAssertEqual(rig.position.reusingCalls, 1)
        XCTAssertEqual(rig.position.freshCalls, 0)
        XCTAssertTrue(rig.journal.kinds.contains(.positionReused))
    }

    func testWithoutARecentSystemPositionOpeningMeasuresAsBefore() async {
        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.position.freshCalls, 1)
        XCTAssertFalse(rig.journal.kinds.contains(.positionReused))
        XCTAssertEqual(rig.store.positionWrites().count, 1)
    }

    // MARK: - Les autres occasions : toujours une mesure fraîche

    func testOnlyOpeningAndLeavingMayReuseThePosition() {
        let reasons: [TrackingEngine.Reason: Bool] = [
            .opened: true, .backgrounded: true,
            .moved: false, .zoneEntered: false, .rearm: false,
            .sharingChanged: false, .networkBack: false, .accessChanged: false,
        ]

        for (reason, expected) in reasons {
            XCTAssertEqual(TrackingEngine.mayReuseSystemFix(for: reason), expected, "\(reason)")
        }
    }

    func testEnteringAZoneAlwaysReadsAFreshPositionEvenWhenTheSystemOneIsRecent() async {
        putRecentSystemFix(secondsOld: 10)
        await rig.engine.refresh(.opened)
        XCTAssertEqual(rig.position.freshCalls, 0)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertEqual(rig.position.freshCalls, 1, "l'entrée relit la position : la personne vient de bouger")
        XCTAssertEqual(rig.position.reusingCalls, 1, "aucune réutilisation de plus")
        XCTAssertEqual(rig.journal.kinds.filter { $0 == .positionReused }.count, 1)
    }

    func testARearmAndAChangeOfAccessAlwaysReadAFreshPosition() async {
        putRecentSystemFix(secondsOld: 10)
        await rig.engine.refresh(.opened)

        await rig.engine.refresh(.rearm)
        await rig.engine.refresh(.accessChanged)
        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.position.freshCalls, 3)
        XCTAssertEqual(rig.position.reusingCalls, 1)
    }

    // MARK: - Ni la photo ni un autre compte

    func testThePhotoMeasureNeverReusesTheSystemPosition() async {
        putRecentSystemFix(secondsOld: 5)
        let snapshot = PermissionSnapshot(
            location: .always,
            servicesEnabled: true,
            preciseLocation: true,
            notifications: .authorized
        )
        let photo = TrackingPhotoPositionSource(position: rig.position, permissions: { snapshot })
        rig.putPositionHome()

        let result = await photo.measure()

        XCTAssertEqual(result, .found(TrackingFixtures.fixHome(at: rig.env.now)))
        XCTAssertEqual(rig.position.freshCalls, 1, "mesure fraîche à « Publier » (moins de 10 min exigée)")
        XCTAssertEqual(rig.position.reusingCalls, 0)
    }

    func testOnlyTheEngineAsksForTheReusablePositionAndThePhotoFileNeverDoes() throws {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Tracking
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
        let files = try XCTUnwrap(FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil))
        var callers: [String] = []
        for case let file as URL in files where file.pathExtension == "swift" {
            let code = try String(contentsOf: file, encoding: .utf8)
                .split(separator: "\n")
                .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
                .joined(separator: "\n")
            if code.contains("currentReusingRecent()") {
                callers.append(file.lastPathComponent)
            }
        }

        XCTAssertEqual(
            callers.sorted(),
            ["LocationTracking.swift", "TrackingEngine+Refresh.swift", "TrackingPorts.swift"],
            "définition, mise en oeuvre et UN seul appelant : le moteur (ouverture et sortie)"
        )
        XCTAssertFalse(callers.contains("PhotoPositionSource.swift"))
    }

    func testTheConstantsAreTheDecidedOnes() {
        XCTAssertEqual(TrackingLimits.reusableFixMaxAgeMillis, 2 * TrackingFixtures.minute)
        XCTAssertEqual(TrackingLimits.reusableFixMaxAccuracyMeters, 100.0, "précision demandée inchangée")
        XCTAssertEqual(TrackingLimits.fixMaxAgeMillis, 10 * TrackingFixtures.minute, "règle des 10 minutes inchangée")
    }
}

/// Le pont (`LocationTracking`) : la limite de deux minutes, la précision, et la position d'un autre compte.
@MainActor
final class PositionReuseBridgeTests: XCTestCase {
    private let minute = TrackingFixtures.minute
    private let noon = TrackingFixtures.noon

    private final class Clock {
        var now = TrackingFixtures.noon
    }

    private struct Rig {
        let tracking: LocationTracking
        let manager: FakeLocationManager
        let clock: Clock
    }

    private func make() -> Rig {
        let manager = FakeLocationManager()
        let clock = Clock()
        let tracking = LocationTracking(manager: manager, clock: { clock.now }, waitSeconds: 0.05)
        return Rig(tracking: tracking, manager: manager, clock: clock)
    }

    func testAPositionOfLessThanTwoMinutesIsReusedWithoutAskingTheSystem() async {
        let rig = make()
        let known = TrackingFixtures.fixHome(at: noon - 2 * minute + 1000)
        rig.manager.systemFix = known

        let reading = await rig.tracking.currentReusingRecent()

        XCTAssertEqual(reading, PositionReading(fix: known, reused: true))
        XCTAssertEqual(rig.manager.positionAsks, 0)
    }

    func testAPositionOfTwoMinutesOrMoreIsReadAgain() async {
        let rig = make()
        rig.manager.systemFix = TrackingFixtures.fixHome(at: noon - 2 * minute)
        let fresh = TrackingFixtures.fixFar(at: noon)

        let asking = Task { await rig.tracking.currentReusingRecent() }
        await waitUntil { rig.manager.positionAsks == 1 }
        rig.manager.emit(.fix(fresh))
        let reading = await asking.value

        XCTAssertEqual(reading, PositionReading(fix: fresh, reused: false), "2 min pile : relue")
        XCTAssertEqual(rig.manager.positionAsks, 1)
    }

    func testNoKnownPositionMeansARequest() async {
        let rig = make()

        let asking = Task { await rig.tracking.currentReusingRecent() }
        await waitUntil { rig.manager.positionAsks == 1 }
        let reading = await asking.value

        XCTAssertEqual(rig.manager.positionAsks, 1)
        XCTAssertFalse(reading.reused)
    }

    func testAPositionLessPreciseThanTheOneWeAskForIsNotReused() async {
        let rig = make()
        rig.manager.systemFix = TrackingFixtures.fixHome(at: noon - 10_000)
        rig.manager.systemFixAccuracy = 500

        let asking = Task { await rig.tracking.currentReusingRecent() }
        await waitUntil { rig.manager.positionAsks == 1 }
        let reading = await asking.value

        XCTAssertFalse(reading.reused)
        XCTAssertEqual(rig.manager.positionAsks, 1)
    }

    func testAPositionFromTheFutureIsNotReused() async {
        let rig = make()
        rig.manager.systemFix = TrackingFixtures.fixHome(at: noon + 30_000)

        let asking = Task { await rig.tracking.currentReusingRecent() }
        await waitUntil { rig.manager.positionAsks == 1 }
        let reading = await asking.value

        XCTAssertFalse(reading.reused)
    }

    func testTheCurrentPositionIsAlwaysAFreshRequestEvenWhenTheSystemOneIsRecent() async {
        let rig = make()
        rig.manager.systemFix = TrackingFixtures.fixHome(at: noon - 5000)
        let fresh = TrackingFixtures.fixHome(at: noon)

        let asking = Task { await rig.tracking.current() }
        await waitUntil { rig.manager.positionAsks == 1 }
        rig.manager.emit(.fix(fresh))
        let answer = await asking.value

        XCTAssertEqual(answer, fresh)
        XCTAssertEqual(rig.manager.positionAsks, 1, "photo, entrée dans une zone : toujours une mesure")
    }

    func testAPositionMeasuredBeforeTheLastDisconnectionIsNeverReused() async {
        let rig = make()
        rig.manager.systemFix = TrackingFixtures.fixHome(at: noon - 20_000)

        await rig.tracking.disarmAll()
        let asking = Task { await rig.tracking.currentReusingRecent() }
        await waitUntil { rig.manager.positionAsks == 1 }
        let reading = await asking.value

        XCTAssertFalse(reading.reused, "elle peut être celle de l'autre compte")
        XCTAssertEqual(rig.manager.positionAsks, 1)
    }

    func testAPositionMeasuredAfterTheDisconnectionIsReusedAgain() async {
        let rig = make()
        await rig.tracking.disarmAll()
        rig.clock.now += 30_000
        let known = TrackingFixtures.fixHome(at: rig.clock.now - 5000)
        rig.manager.systemFix = known

        let reading = await rig.tracking.currentReusingRecent()

        XCTAssertEqual(reading, PositionReading(fix: known, reused: true))
        XCTAssertEqual(rig.manager.positionAsks, 0)
    }
}

/// Moteur et pont ensemble : le compte A ouvre l'app (position réutilisée), se déconnecte, le compte B ouvre l'app juste après avec la
/// même position système (celle de A, vieille de moins de 2 minutes) : elle n'est PAS réutilisée, une mesure est demandée.
@MainActor
final class PositionReuseAcrossAccountsTests: XCTestCase {
    private final class Generation {
        var value = 0
    }

    func testAPositionOfTheFirstAccountIsNotReusedForTheNextOneRightAfterTheDisconnection() async {
        let base = TrackingRig()
        let manager = FakeLocationManager()
        let tracking = LocationTracking(manager: manager, clock: { base.env.now }, waitSeconds: 0.05)
        let generation = Generation()
        let engine = TrackingEngine(
            TrackingDependencies(
                env: base.env,
                store: base.store,
                local: base.local,
                position: tracking,
                geofences: tracking,
                movement: tracking,
                device: base.device,
                journal: base.journal,
                timeoutSeconds: 2,
                generation: { generation.value }
            )
        )
        manager.systemFix = TrackingFixtures.fixHome(at: base.env.now - 30_000)

        await engine.refresh(.opened)
        XCTAssertEqual(manager.positionAsks, 0, "compte A : la position récente du système est réutilisée")
        XCTAssertTrue(base.journal.kinds.contains(.positionReused))

        await engine.stop()
        generation.value += 1
        base.env.currentUid = "uid-2"
        base.env.now += 10_000
        await engine.refresh(.opened)

        XCTAssertEqual(manager.positionAsks, 1, "compte B : la position d'avant la déconnexion n'est pas réutilisée")
        XCTAssertFalse(base.journal.kinds.contains(.positionReused))
    }
}
