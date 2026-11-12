import XCTest
@testable import Miff

// SRV-2I : le registraire du registre des appareils, avec des doubles en mémoire (aucun réseau, aucun Firebase, aucun système). Miroir de
// `PushRegistrarTest` d'Android. Les événements sont notés dans une chronologie commune pour PROUVER l'ordre de la sortie : l'invalidation du
// jeton d'abord.

private final class EventLog {
    private(set) var events: [String] = []

    func add(_ event: String) {
        events.append(event)
    }

    func count(_ event: String) -> Int {
        events.filter { $0 == event }.count
    }
}

private final class FakeRegistry: DeviceTokenRegistry {
    private let log: EventLog
    var upsertFails = false
    /// Une écriture qui dure ce temps (secondes) : pour prouver que la sortie n'attend jamais une écriture.
    var upsertDelaySeconds: Double = 0
    private(set) var upserts: [DeviceEntry] = []
    private(set) var upsertStarted = false

    init(log: EventLog) {
        self.log = log
    }

    func upsert(uid: String, deviceId: String, entry: DeviceEntry) async throws {
        upsertStarted = true
        log.add("upsert")
        if upsertDelaySeconds > 0 {
            try await Task.sleep(nanoseconds: UInt64(upsertDelaySeconds * 1_000_000_000))
        }
        if upsertFails {
            throw OperationTimedOut()
        }
        upserts.append(entry)
    }

    func deleteOthers(uid: String, keepDeviceId: String) async throws {
        log.add("deleteOthers")
    }

    func delete(uid: String, deviceId: String) async throws {
        log.add("delete")
    }
}

@MainActor
private final class FakeTokens: PushTokenSource {
    private let log: EventLog
    var token: String? = "token-1"

    init(log: EventLog) {
        self.log = log
    }

    func enableAndFetch() async -> String? {
        log.add("enable")
        return token
    }

    func disable() async {
        log.add("disable")
    }
}

private final class FakeLocal: PushLocalState {
    private var savedRecord: RegistrationRecord?
    private var on = false
    private var cleared: String?

    func record() -> RegistrationRecord? {
        savedRecord
    }

    func saveRecord(_ record: RegistrationRecord) {
        savedRecord = record
    }

    func serviceOn() -> Bool {
        on
    }

    func setServiceOn(_ value: Bool) {
        on = value
    }

    func othersClearedFor() -> String? {
        cleared
    }

    func setOthersClearedFor(_ deviceId: String?) {
        cleared = deviceId
    }

    func clear() {
        savedRecord = nil
        on = false
        cleared = nil
    }
}

/// Ce que le monde extérieur dit au registraire, modifiable pendant un essai.
private final class World {
    var uid: String? = "uid-1"
    var deviceId: String? = "device-AAAAAAAAAAAAAAAA"
    var consent = true
    var updates = true
    var online = true
    var zone = "Europe/Paris"
    var nowMillis = 1_800_000_000_000
}

@MainActor
final class PushRegistrarTests: XCTestCase {
    private let day = 24 * 60 * 60 * 1000

    private struct Rig {
        let registrar: PushRegistrar
        let registry: FakeRegistry
        let tokens: FakeTokens
        let local: FakeLocal
        let world: World
        let log: EventLog
    }

    private func makeRig(writeTimeout: TimeInterval = 8) -> Rig {
        let log = EventLog()
        let registry = FakeRegistry(log: log)
        let tokens = FakeTokens(log: log)
        let local = FakeLocal()
        let world = World()
        let registrar = PushRegistrar(
            PushRegistrarDependencies(
                registry: registry,
                local: local,
                tokens: tokens,
                currentUid: { world.uid },
                sessionDeviceId: { world.deviceId },
                consent: { _ in world.consent },
                notifyUpdates: { _ in world.updates },
                isOnline: { world.online },
                timeZoneId: { world.zone },
                appVersion: { "0.1.0 (1)" },
                nowMillis: { world.nowMillis },
                clearDelivered: { log.add("clearDelivered") },
                writeTimeoutSeconds: writeTimeout,
                deleteTimeoutSeconds: 4
            )
        )
        return Rig(registrar: registrar, registry: registry, tokens: tokens, local: local, world: world, log: log)
    }

    // MARK: - Rien avant l'accord (J1)

    func testNothingIsRegisteredAndNoTokenIsAskedBeforeTheConsent() async {
        let rig = makeRig()
        rig.world.consent = false

        await rig.registrar.sync()

        XCTAssertEqual(rig.registry.upserts.count, 0)
        XCTAssertEqual(rig.log.count("enable"), 0, "le service de notification reste éteint : aucun jeton n'existe")
        XCTAssertFalse(rig.local.serviceOn())
    }

    func testAFreshTokenGivenWithoutConsentIsNeverWritten() async {
        let rig = makeRig()
        rig.world.consent = false

        await rig.registrar.sync(freshToken: "token-x")

        XCTAssertEqual(rig.registry.upserts.count, 0)
    }

    func testNothingIsWrittenWithoutASignedInAccountOrAConfirmedSession() async {
        let rig = makeRig()
        rig.world.uid = nil
        await rig.registrar.sync()
        rig.world.uid = "uid-1"
        rig.world.deviceId = nil
        await rig.registrar.sync()
        rig.world.deviceId = "court"
        await rig.registrar.sync()

        XCTAssertEqual(rig.log.events, [])
    }

    // MARK: - Écrire une fois, puis seulement ce qui change

    func testTheDocumentIsWrittenOnceWithTheWholeEntry() async throws {
        let rig = makeRig()

        await rig.registrar.sync()
        await rig.registrar.sync()

        XCTAssertEqual(rig.registry.upserts.count, 1)
        let entry = try XCTUnwrap(rig.registry.upserts.first)
        XCTAssertEqual(entry.token, "token-1")
        XCTAssertEqual(entry.timeZoneId, "Europe/Paris")
        XCTAssertTrue(entry.notifyUpdates)
        XCTAssertEqual(entry.appVersion, "0.1.0 (1)")
        XCTAssertTrue(rig.local.serviceOn())
        XCTAssertEqual(rig.local.record()?.tokenFingerprint, TokenFingerprint.of("token-1"))
        XCTAssertNotEqual(rig.local.record()?.tokenFingerprint, "token-1", "le jeton lui-même n'est jamais gardé")
    }

    func testTheSwitchIsWrittenAtOnceAndTheTokenStaysThere() async {
        let rig = makeRig()
        await rig.registrar.sync()

        rig.world.updates = false
        await rig.registrar.sync()

        XCTAssertEqual(rig.registry.upserts.count, 2)
        XCTAssertEqual(rig.registry.upserts.last?.notifyUpdates, false)
        XCTAssertEqual(rig.registry.upserts.last?.token, "token-1")
    }

    func testANewTokenFromTheSystemIsWrittenAtOnce() async {
        let rig = makeRig()
        await rig.registrar.sync()

        await rig.registrar.sync(freshToken: "token-2")

        XCTAssertEqual(rig.registry.upserts.count, 2)
        XCTAssertEqual(rig.registry.upserts.last?.token, "token-2")
    }

    func testAMovedTimeZoneWaitsADayAndAStaleDocumentIsRefreshedAfterThirtyDays() async {
        let rig = makeRig()
        await rig.registrar.sync()

        rig.world.zone = "America/New_York"
        rig.world.nowMillis += day / 2
        await rig.registrar.sync()
        XCTAssertEqual(rig.registry.upserts.count, 1)

        rig.world.nowMillis += day
        await rig.registrar.sync()
        XCTAssertEqual(rig.registry.upserts.count, 2)

        rig.world.nowMillis += 29 * day
        await rig.registrar.sync()
        XCTAssertEqual(rig.registry.upserts.count, 2)
        rig.world.nowMillis += day
        await rig.registrar.sync()
        XCTAssertEqual(rig.registry.upserts.count, 3)
    }

    func testOfflineNothingIsAttemptedAndNothingIsFlaggedAsFailed() async {
        let rig = makeRig()
        rig.world.online = false
        await rig.registrar.sync()
        XCTAssertEqual(rig.registry.upserts.count, 0)

        rig.world.online = true
        await rig.registrar.sync()
        XCTAssertEqual(rig.registry.upserts.count, 1, "au retour du réseau, tout de suite")
    }

    func testAFailedWriteIsNotRetriedBeforeTenMinutesUnlessSomethingChanged() async {
        let rig = makeRig()
        rig.registry.upsertFails = true

        await rig.registrar.sync()
        await rig.registrar.sync()
        XCTAssertEqual(rig.log.count("upsert"), 1)

        rig.world.updates = false
        await rig.registrar.sync()
        XCTAssertEqual(rig.log.count("upsert"), 2, "un changement s'essaie tout de suite")

        rig.world.nowMillis += 11 * 60 * 1000
        rig.registry.upsertFails = false
        await rig.registrar.sync()
        XCTAssertEqual(rig.registry.upserts.count, 1)
        XCTAssertNotNil(rig.local.record())
    }

    // MARK: - Un compte, un appareil

    func testTheOtherDocumentsAreDeletedOncePerSessionWithOrWithoutConsent() async {
        let rig = makeRig()
        rig.world.consent = false
        await rig.registrar.sync()
        await rig.registrar.sync()
        XCTAssertEqual(rig.log.count("deleteOthers"), 1)

        rig.world.consent = true
        await rig.registrar.sync()
        XCTAssertEqual(rig.log.count("deleteOthers"), 1)

        rig.world.deviceId = "device-BBBBBBBBBBBBBBBB"
        await rig.registrar.sync()
        XCTAssertEqual(rig.log.count("deleteOthers"), 2, "une nouvelle session recommence")
        XCTAssertEqual(rig.registry.upserts.count, 2, "et le document de la nouvelle session est écrit")
    }

    // MARK: - L'autorisation retirée

    func testWithdrawnPermissionInvalidatesTheTokenFirstThenDeletesTheDocument() async {
        let rig = makeRig()
        await rig.registrar.sync()
        rig.world.consent = false

        await rig.registrar.sync()

        let events = rig.log.events
        let disable = events.lastIndex(of: "disable")
        let delete = events.lastIndex(of: "delete")
        XCTAssertNotNil(disable)
        XCTAssertNotNil(delete)
        XCTAssertLessThan(disable ?? 0, delete ?? 0, "le jeton est invalidé AVANT la suppression du document")
        XCTAssertNil(rig.local.record())
        XCTAssertFalse(rig.local.serviceOn())
    }

    // MARK: - La sortie

    func testSigningOutInvalidatesTheTokenFirstThenClearsTheTrayThenDeletesTheDocument() async {
        let rig = makeRig()
        await rig.registrar.sync()

        await rig.registrar.forget()

        let tail = Array(rig.log.events.suffix(3))
        XCTAssertEqual(tail, ["disable", "clearDelivered", "delete"])
        XCTAssertNil(rig.local.record())
        XCTAssertFalse(rig.local.serviceOn())
    }

    func testSigningOutOfflineStillInvalidatesTheTokenAndNeverWaitsForTheNetwork() async {
        let rig = makeRig()
        await rig.registrar.sync()
        rig.world.online = false

        await rig.registrar.forget()

        XCTAssertEqual(rig.log.count("disable"), 1)
        XCTAssertEqual(rig.log.count("delete"), 0)
        XCTAssertNil(rig.local.record())
    }

    func testSigningOutDoesNotWaitForAWriteInProgress() async {
        let rig = makeRig(writeTimeout: 30)
        rig.registry.upsertDelaySeconds = 2
        let syncing = Task { await rig.registrar.sync() }
        await waitUntil { rig.registry.upsertStarted }

        let started = Date()
        await rig.registrar.forget()
        let elapsed = Date().timeIntervalSince(started)

        XCTAssertLessThan(elapsed, 1.5, "la sortie n'attend pas l'écriture en cours")
        XCTAssertEqual(rig.log.count("disable"), 1)
        await syncing.value
        XCTAssertNil(rig.local.record(), "une écriture qui finit après la sortie ne laisse rien en mémoire")
    }

    func testAServiceLeftOnWithNobodySignedInIsSwitchedOffAtTheNextLook() async {
        let rig = makeRig()
        await rig.registrar.sync()
        rig.world.uid = nil

        await rig.registrar.sync()

        XCTAssertEqual(rig.log.count("disable"), 1)
        XCTAssertFalse(rig.local.serviceOn())
        XCTAssertNil(rig.local.record())
    }

    func testNothingHappensWithNobodySignedInAndNothingOn() async {
        let rig = makeRig()
        rig.world.uid = nil

        await rig.registrar.sync()

        XCTAssertEqual(rig.log.events, [])
    }
}
