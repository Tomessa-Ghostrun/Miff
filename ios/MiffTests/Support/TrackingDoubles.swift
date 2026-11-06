import Foundation
@testable import Miff

// Doubles en mémoire du suivi des lieux (LOC-4b). Aucun ne touche au système, au réseau ni à Firebase ; les coordonnées sont de
// démonstration (autour de Paris). Du Swift pur.

/// Un lieu de démonstration et des points « dedans » / « dehors » par rapport à lui.
enum TrackingFixtures {
    static let homeLat = 48.85661
    static let homeLng = 2.35222
    /// Environ 1,1 km au nord : loin de tout cercle de 100 m.
    static let farLat = 48.86661

    static let home = PlaceFact(id: "p1", lat: homeLat, lng: homeLng, radiusM: 100, type: .trusted)
    static let work = PlaceFact(id: "p2", lat: 48.87661, lng: 2.36222, radiusM: 100, type: .trusted)
    static let danger = PlaceFact(id: "r1", lat: homeLat, lng: homeLng, radiusM: 200, type: .risk)

    /// L'heure de départ des scénarios (un mardi, en milieu de journée du téléphone).
    static let noon: Int = {
        let parts = DateComponents(year: 2026, month: 11, day: 3, hour: 12, minute: 0)
        let date = Calendar.current.date(from: parts) ?? Date(timeIntervalSince1970: 1_793_000_000)
        return Int(date.timeIntervalSince1970 * 1000)
    }()

    static let minute = 60 * 1000
    static let hour = 60 * minute

    /// Une position chez soi, mesurée à `at`.
    static func fixHome(at: Int) -> Fix {
        Fix(lat: homeLat, lng: homeLng, measuredAtMillis: at)
    }

    /// Une position à environ 1,1 km de chez soi, mesurée à `at`.
    static func fixFar(at: Int) -> Fix {
        Fix(lat: farLat, lng: homeLng, measuredAtMillis: at)
    }
}

final class FakeTrackingEnvironment: TrackingEnvironment {
    var currentUid: String? = "uid-1"
    var snapshot = PermissionSnapshot(
        location: .always,
        servicesEnabled: true,
        preciseLocation: true,
        notifications: .authorized
    )
    var battery: Int? = 80
    var online = true
    var now = TrackingFixtures.noon
    var conditions = SystemConditions()

    func uid() -> String? {
        currentUid
    }

    func permissions() async -> PermissionSnapshot {
        snapshot
    }

    func batteryPct() async -> Int? {
        battery
    }

    func isOnline() -> Bool {
        online
    }

    func nowMillis() -> Int {
        now
    }

    func systemConditions() async -> SystemConditions {
        conditions
    }
}

final class FakeTrackingStore: TrackingStore {
    var memberships: [MembershipRef] = []
    var consents: [String: SharingConsentRead] = [:]
    var places: [String: [PlaceFact]] = [:]
    var membershipsFailure: Error?
    var consentFailure: Error?
    var placesFailure: Error?
    /// Les écritures qui échouent : toutes, ou celles d'un chemin donné.
    var commitFailure: Error?
    var failingPaths: [String: Error] = [:]
    private(set) var commits: [[DocWrite]] = []
    private(set) var readCount = 0

    func memberships(uid: String) async throws -> [MembershipRef] {
        readCount += 1
        if let membershipsFailure {
            throw membershipsFailure
        }
        return memberships
    }

    func consent(miffId: String, uid: String) async throws -> SharingConsentRead? {
        readCount += 1
        if let consentFailure {
            throw consentFailure
        }
        return consents[miffId]
    }

    func places(miffId: String) async throws -> [PlaceFact] {
        readCount += 1
        if let placesFailure {
            throw placesFailure
        }
        return places[miffId] ?? []
    }

    func commit(_ writes: [DocWrite]) async throws {
        if let commitFailure {
            throw commitFailure
        }
        for write in writes {
            if let failure = failingPaths[write.path] {
                throw failure
            }
        }
        commits.append(writes)
    }

    /// Toutes les écritures reçues, à plat.
    var writes: [DocWrite] {
        commits.flatMap { $0 }
    }

    func statusWrites() -> [DocWrite] {
        writes.filter { $0.path.contains("/status/") }
    }

    func positionWrites() -> [DocWrite] {
        writes.filter { $0.path.contains("/positions/") }
    }
}

final class MemoryTrackingLocalStore: TrackingLocalStore {
    var state: LocalState?
    private(set) var saves = 0
    private(set) var clears = 0

    func load() -> LocalState? {
        state
    }

    func save(_ state: LocalState) {
        saves += 1
        self.state = state
    }

    func clear() {
        clears += 1
        state = nil
    }
}

final class FakePositionSource: PositionSource {
    var fix: Fix?
    private(set) var calls = 0

    func current() async -> Fix? {
        calls += 1
        return fix
    }
}

final class FakeGeofences: GeofenceArming {
    var accepts = true
    private(set) var armedSets: [[TrackedZone]] = []
    private(set) var reregisterFlags: [Bool] = []
    private(set) var disarmCalls = 0
    private(set) var requestedStates: [[String]] = []

    func arm(desired: [TrackedZone], reregister: Bool) async -> Bool {
        armedSets.append(desired)
        reregisterFlags.append(reregister)
        return accepts
    }

    func requestStates(keys: [String]) async {
        requestedStates.append(keys)
    }

    func disarmAll() async {
        disarmCalls += 1
    }

    var lastArmed: [TrackedZone] {
        armedSets.last ?? []
    }
}

final class FakeMovement: MovementUpdates {
    var accepts = true
    private(set) var starts = 0
    private(set) var stops = 0

    func start() async -> Bool {
        starts += 1
        return accepts
    }

    func stop() async {
        stops += 1
    }
}

final class FakeActiveDevice: ActiveDevice {
    var revoked = false
    var clearanceResult: DeviceClearance = .cleared(deviceId: testDeviceId)
    var afterRefusalResult: SessionCheck = .confirmed
    private(set) var clearanceCalls = 0
    private(set) var refusalChecks = 0

    var isRevoked: Bool {
        revoked
    }

    func clearance(uid: String) async -> DeviceClearance {
        clearanceCalls += 1
        return clearanceResult
    }

    func checkAfterRefusal(uid: String) async -> SessionCheck {
        refusalChecks += 1
        if afterRefusalResult == .replaced {
            // Comme la vraie session : un appareil remplacé est révoqué, les passages suivants n'écrivent plus rien.
            revoked = true
            clearanceResult = .replaced
        }
        return afterRefusalResult
    }
}

/// Le journal en mémoire : ce que le moteur y a écrit, dans l'ordre.
final class RecordingJournal: DetectionJournal {
    private(set) var recorded: [JournalLine] = []
    private(set) var clears = 0

    func record(kind: JournalKind, placeId: String?, batteryPct: Int?) {
        recorded.append(JournalLine(atMillis: 0, kind: kind, placeId: placeId, batteryPct: batteryPct))
    }

    func lines() -> [JournalLine] {
        recorded
    }

    func clear() {
        clears += 1
        recorded = []
    }

    var kinds: [JournalKind] {
        recorded.map { $0.kind }
    }
}

/// Tout le moteur avec ses doubles, prêt à l'emploi : une Miff, partage actif sous la version 3, un lieu de confiance chez soi.
final class TrackingRig {
    let env = FakeTrackingEnvironment()
    let store = FakeTrackingStore()
    let local = MemoryTrackingLocalStore()
    let position = FakePositionSource()
    let geofences = FakeGeofences()
    let movement = FakeMovement()
    let device = FakeActiveDevice()
    let journal = RecordingJournal()
    var generation = 0

    private(set) lazy var engine: TrackingEngine = {
        TrackingEngine(
            TrackingDependencies(
                env: env,
                store: store,
                local: local,
                position: position,
                geofences: geofences,
                movement: movement,
                device: device,
                journal: journal,
                timeoutSeconds: 2,
                generation: { [unowned self] in self.generation }
            )
        )
    }()

    init(places: [PlaceFact] = [TrackingFixtures.home]) {
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
        store.consents[testMiffId] = SharingConsentRead(enabled: true, version: MiffSharingConsent.version)
        store.places[testMiffId] = places
        position.fix = TrackingFixtures.fixHome(at: env.now)
    }

    /// Une seconde Miff, avec ses lieux (le même compte y partage aussi).
    func addMiff(_ miffId: String, places: [PlaceFact]) {
        store.memberships.append(MembershipRef(key: miffId, miffId: miffId))
        store.consents[miffId] = SharingConsentRead(enabled: true, version: MiffSharingConsent.version)
        store.places[miffId] = places
    }

    var homeKey: String {
        TrackingFixtures.home.key
    }

    /// Une position chez soi mesurée à l'heure du téléphone de test.
    func putPositionHome() {
        position.fix = TrackingFixtures.fixHome(at: env.now)
    }

    func putPositionFar() {
        position.fix = TrackingFixtures.fixFar(at: env.now)
    }
}

/// Le gestionnaire de localisation du système, en mémoire : ce que le suivi lui a demandé, et les événements que le test lui fait rendre.
@MainActor
final class FakeLocationManager: LocationManaging {
    var onEvent: (@MainActor (LocationEvent) -> Void)?
    var held: Set<String> = []
    var maximumRegionRadius = 100_000.0
    var systemFix: Fix?
    var regionsAvailable = true
    var significantChangesAvailable = true
    private(set) var watched: [(key: String, radius: Double)] = []
    private(set) var unwatched: [String] = []
    private(set) var askedStates: [String] = []
    private(set) var positionAsks = 0
    private(set) var significantStarts = 0
    private(set) var significantStops = 0

    var heldRegionKeys: Set<String> {
        held
    }

    func watch(_ zone: TrackedZone, radius: Double) {
        held.insert(zone.key)
        watched.append((key: zone.key, radius: radius))
    }

    func unwatch(key: String) {
        held.remove(key)
        unwatched.append(key)
    }

    func askRegionState(key: String) {
        askedStates.append(key)
    }

    func askPosition() {
        positionAsks += 1
    }

    func beginSignificantChanges() {
        significantStarts += 1
    }

    func endSignificantChanges() {
        significantStops += 1
    }

    /// Le système rend un événement.
    func emit(_ event: LocationEvent) {
        onEvent?(event)
    }
}

/// Ce que le pont a transmis au moteur, dans l'ordre.
@MainActor
final class RecordingSink: TrackingEventSink {
    private(set) var transitions: [String] = []
    private(set) var states: [String] = []
    private(set) var moves: [Fix] = []
    private(set) var accessChanges = 0
    private(set) var failures = 0

    func zoneTransition(_ kind: TransitionKind, zoneKey: String) {
        transitions.append("\(kind) \(ZoneKey.placeId(zoneKey))")
    }

    func zoneStateKnown(zoneKey: String, isInside: Bool) {
        states.append("\(ZoneKey.placeId(zoneKey)) \(isInside)")
    }

    func significantMove(_ fix: Fix) {
        moves.append(fix)
    }

    func accessChanged() {
        accessChanges += 1
    }

    func monitoringFailed() {
        failures += 1
    }
}
