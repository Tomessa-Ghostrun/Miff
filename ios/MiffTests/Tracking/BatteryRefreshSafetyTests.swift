import XCTest
@testable import Miff

/// La batterie rafraîchie à l'ouverture et à la sortie (décision du PM, 2026-11-07) : ce qui ne s'écrit JAMAIS. Pas de création de
/// statut, pas sans consentement ni partage ni autorisations, pas pour une session remplacée, arrêtée ou un compte supprimé, et les
/// disjoncteurs.
final class BatteryRefreshSafetyTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute
    private let hour = TrackingFixtures.hour

    private func batteryWrites(_ target: TrackingRig? = nil) -> [DocWrite] {
        let keys: Set<String> = ["batteryPct", "updatedAt", "deviceId"]
        return (target ?? rig).store.statusWrites().filter { Set($0.data.keys) == keys }
    }

    private func open(_ target: TrackingRig? = nil, level: Int? = 80) async {
        let used = target ?? rig
        used.env.battery = level
        await used.engine.refresh(.opened)
    }

    // MARK: - Jamais de création

    func testAStatusIsNeverCreatedOnlyUpdated() async {
        await open()
        rig.env.now += 20 * minute
        rig.env.battery = 40
        await rig.engine.refresh(.backgrounded)

        XCTAssertEqual(batteryWrites().count, 2)
        XCTAssertTrue(rig.store.statusWrites().allSatisfy { $0.kind == .update })
    }

    func testAMissingStatusLeavesNothingCreatedAndIsNotRetriedAtOnce() async {
        rig.position.fix = nil
        // Le serveur répond « introuvable » : un update sur un document absent échoue, rien n'est créé.
        rig.store.commitFailure = firestoreError(5)

        await open()
        rig.env.now += 5 * minute
        await rig.engine.refresh(.backgrounded)

        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertEqual(rig.store.commitAttempts, 1)
    }

    func testAStatusWaitingToBeWrittenGoesBeforeTheBattery() async {
        await open(level: nil)
        rig.store.commitFailure = firestoreError(FirestoreErrorNumber.unavailable)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)
        let attempts = rig.store.commitAttempts
        rig.env.now += 20 * minute
        rig.env.battery = 80

        await rig.engine.refresh(.backgrounded)

        XCTAssertEqual(rig.store.commitAttempts, attempts + 1, "seul le statut en attente est retenté, pas la batterie")
        XCTAssertTrue(batteryWrites().isEmpty)
    }

    // MARK: - Pas sans consentement, partage ni autorisations

    func testNothingWithoutTheConsentOfVersionThree() async {
        for version in ["2026-10-v1", "2026-10-v2", nil] as [String?] {
            let fresh = TrackingRig()
            fresh.store.consents[testMiffId] = SharingConsentRead(enabled: true, version: version)

            await open(fresh)

            XCTAssertTrue(fresh.store.writes.isEmpty, "\(String(describing: version))")
        }
    }

    func testNothingWhenSharingIsStoppedOrThereIsNoCard() async {
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: false, version: MiffSharingConsent.version)
        await open()
        XCTAssertTrue(rig.store.writes.isEmpty)

        let noCard = TrackingRig()
        noCard.store.consents = [:]
        await open(noCard)
        XCTAssertTrue(noCard.store.writes.isEmpty)
    }

    func testNothingWithoutAlwaysAndTheExactPosition() async {
        for snapshot in [
            PermissionSnapshot(location: .whenInUse, notifications: .authorized),
            PermissionSnapshot(location: .always, preciseLocation: false, notifications: .authorized),
            PermissionSnapshot(location: .always, servicesEnabled: false, notifications: .authorized),
            PermissionSnapshot(location: .denied, notifications: .authorized)
        ] {
            let fresh = TrackingRig()
            fresh.env.snapshot = snapshot

            await open(fresh)
            fresh.env.now += hour
            await fresh.engine.refresh(.backgrounded)

            XCTAssertTrue(fresh.store.writes.isEmpty, "\(snapshot)")
        }
    }

    func testNothingWhenNobodyIsSignedInOrTheAccountIsGone() async {
        await open()
        XCTAssertEqual(batteryWrites().count, 1)
        rig.env.currentUid = nil
        rig.env.now += hour
        rig.env.battery = 20

        await rig.engine.refresh(.backgrounded)
        await rig.engine.refresh(.opened)

        XCTAssertEqual(batteryWrites().count, 1)
        XCTAssertNil(rig.local.state, "la copie locale est effacée avec la session")
    }

    func testNothingAfterTheStopOfADeletedAccountUntilTheSessionChanges() async {
        await open()
        await rig.engine.stop()
        rig.env.now += hour
        rig.env.battery = 20

        await rig.engine.refresh(.opened)
        await rig.engine.refresh(.backgrounded)

        XCTAssertEqual(batteryWrites().count, 1)
        XCTAssertNil(rig.local.state)
    }

    // MARK: - La session d'appareil (SESS-3)

    func testAReplacedOrUnverifiedPhoneWritesNoBattery() async {
        for result in [DeviceClearance.replaced, DeviceClearance.unverified] {
            let fresh = TrackingRig()
            fresh.device.clearanceResult = result

            await open(fresh)

            XCTAssertTrue(fresh.store.statusWrites().isEmpty, "\(result)")
        }
    }

    func testARevokedPhoneWritesNoBatteryAndErasesTheCopy() async {
        await open()
        rig.device.revoked = true
        rig.env.now += hour
        rig.env.battery = 20

        await rig.engine.refresh(.backgrounded)

        XCTAssertEqual(batteryWrites().count, 1)
        XCTAssertNil(rig.local.state)
    }

    func testARefusalThatIsAReplacementStopsTheBatteryOfTheOtherMiffs() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        let denied = firestoreError(FirestoreErrorNumber.permissionDenied)
        rig.store.failingPaths["miffs/\(testMiffId)/status/uid-1"] = denied
        rig.device.afterRefusalResult = .replaced

        await open()

        XCTAssertTrue(rig.store.statusWrites().isEmpty)
    }

    // MARK: - Les disjoncteurs

    func testTheDedicatedBreakerStopsTheBatteryAtFortyWithoutTouchingTheStatusCounter() async throws {
        await open()
        var state = try XCTUnwrap(rig.local.state)
        rig.env.now += 4 * hour
        state.breaker = BreakerState(
            dayKey: WriteBreaker.dayKey(millis: rig.env.now),
            statusWrites: 0,
            positionWrites: 0,
            batteryWrites: TrackingLimits.maxBatteryWritesPerDay
        )
        rig.local.state = state
        rig.env.battery = 20

        await rig.engine.refresh(.opened)

        XCTAssertEqual(batteryWrites().count, 1)
        XCTAssertTrue(rig.journal.kinds.contains(.breakerStatus))
        XCTAssertEqual(rig.local.state?.breaker?.statusWrites, 0)
    }

    func testTheStatusBreakerAlsoStopsTheBatteryWhenItIsTripped() async throws {
        await open()
        var state = try XCTUnwrap(rig.local.state)
        rig.env.now += 4 * hour
        state.breaker = BreakerState(
            dayKey: WriteBreaker.dayKey(millis: rig.env.now),
            statusWrites: TrackingLimits.maxStatusWritesPerDay,
            positionWrites: 0
        )
        rig.local.state = state
        rig.env.battery = 20

        await rig.engine.refresh(.opened)

        XCTAssertEqual(batteryWrites().count, 1)
    }

    func testEachRefreshIsCountedInTheDedicatedCounterOnly() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])

        await open()

        let breaker = rig.local.state?.breaker
        XCTAssertEqual(breaker?.batteryWrites, 2, "un par Miff")
        XCTAssertEqual(breaker?.statusWrites ?? 0, 0)
    }
}
