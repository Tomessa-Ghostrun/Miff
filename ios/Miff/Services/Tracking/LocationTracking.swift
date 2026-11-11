import Foundation

// CONFIDENTIALITÉ, pour ce fichier : la logique du pont entre le système et le moteur. Elle ne touche pas à CoreLocation (voir
// `SystemLocationManager.swift`). Les événements de zone ne portent que l'identifiant de la zone ; une position devient un `Fix` (dont
// la `description` ne montre rien) et n'est ni journalisée, ni copiée.

/// La surveillance de régions, le changement de position significatif et la position unique, d'après le gestionnaire de localisation.
/// Créé au démarrage de l'app (voir `TrackingServices`).
@MainActor
final class LocationTracking: PositionSource, GeofenceArming, MovementUpdates {
    private let manager: LocationManaging
    private let clock: () -> Int
    private let waitSeconds: Double
    private var sink: TrackingEventSink?
    private var pendingFix: CheckedContinuation<Fix?, Never>?
    private var pendingToken = 0
    /// Les zones dont l'état a été DEMANDÉ et n'est pas encore arrivé. Le système répond aussi à chaque franchissement : une réponse
    /// qu'on n'a pas demandée est ignorée (la sortie ou l'entrée arrive par son propre rappel), sinon un « dehors » pourrait passer
    /// avant la sortie et s'écrire comme une sortie manquée.
    private var requestedStates = Set<String>()
    private var knownAuthorization: AuthorizationState?
    /// BATT-3 : une position du système mesurée avant ou à cet instant n'est jamais réutilisée. Avancé à chaque `disarmAll()`
    /// (déconnexion, suppression du compte, plus de partage) : la dernière position du téléphone peut être celle d'un autre compte.
    private var noReuseUpToMillis = Int.min

    init(
        manager: LocationManaging,
        clock: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        waitSeconds: Double = TrackingLimits.positionWaitSeconds - 0.5
    ) {
        self.manager = manager
        self.clock = clock
        self.waitSeconds = waitSeconds
        manager.onEvent = { [weak self] event in
            self?.handle(event)
        }
    }

    /// Qui reçoit les signalements. À brancher tout de suite après la création.
    func attach(_ sink: TrackingEventSink) {
        self.sink = sink
    }

    private func handle(_ event: LocationEvent) {
        switch event {
        case .fix(let fix):
            handleFix(fix)
        case .fixFailed:
            resolvePending(with: recentSystemFix())
        case .region(let kind, let key):
            if ZoneKey.isValid(key) {
                sink?.zoneTransition(kind, zoneKey: key)
            }
        case .regionState(let key, let isInside):
            if ZoneKey.isValid(key), requestedStates.remove(key) != nil {
                sink?.zoneStateKnown(zoneKey: key, isInside: isInside)
            }
        case .monitoringFailed:
            sink?.monitoringFailed()
        case .authorization(let latest):
            let changed = knownAuthorization != nil && knownAuthorization != latest
            knownAuthorization = latest
            if changed {
                sink?.accessChanged()
            }
        }
    }

    /// Une position qui arrive : la réponse à `current()` si quelqu'un l'attend ENCORE, sinon un signalement de déplacement
    /// significatif (jamais perdue, jamais absorbée par une demande abandonnée).
    private func handleFix(_ fix: Fix) {
        if pendingFix != nil {
            resolvePending(with: fix)
        } else {
            sink?.significantMove(fix)
        }
    }

    private func resolvePending(with fix: Fix?) {
        guard let waiting = pendingFix else {
            return
        }
        pendingFix = nil
        waiting.resume(returning: fix)
    }

    /// La dernière position que le système connaît, seulement si elle a moins de deux minutes (un repli, jamais une vieille position).
    private func recentSystemFix() -> Fix? {
        guard let fix = manager.systemFix else {
            return nil
        }
        let age = clock() - fix.measuredAtMillis
        return age >= 0 && age <= TrackingLimits.systemFixMaxAgeMillis ? fix : nil
    }

    private func expire(_ token: Int) {
        if token == pendingToken {
            resolvePending(with: nil)
        }
    }

    // MARK: - PositionSource

    func current() async -> Fix? {
        // Une demande restée sans réponse est libérée avant d'en faire une nouvelle.
        resolvePending(with: nil)
        pendingToken += 1
        let token = pendingToken
        let seconds = waitSeconds
        // Le délai est tenu ICI : passé ce délai la demande est abandonnée, et une position qui arrive ensuite est un signalement
        // de déplacement, pas la réponse de personne.
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            self?.expire(token)
        }
        return await withCheckedContinuation { (continuation: CheckedContinuation<Fix?, Never>) in
            pendingFix = continuation
            manager.askPosition()
        }
    }

    func currentReusingRecent() async -> PositionReading {
        if let recent = reusableSystemFix() {
            return PositionReading(fix: recent, reused: true)
        }
        return PositionReading(fix: await current(), reused: false)
    }

    /// La dernière position du système si elle a MOINS de deux minutes, est assez précise (100 m, comme la demande) et a été mesurée
    /// APRÈS la dernière déconnexion. Une position du futur (horloge) n'est pas réutilisée.
    private func reusableSystemFix() -> Fix? {
        guard let fix = manager.systemFix(maxAccuracyMeters: TrackingLimits.reusableFixMaxAccuracyMeters) else {
            return nil
        }
        let age = clock() - fix.measuredAtMillis
        guard age >= 0, age < TrackingLimits.reusableFixMaxAgeMillis, fix.measuredAtMillis > noReuseUpToMillis else {
            return nil
        }
        return fix
    }

    // MARK: - MovementUpdates

    func start() async -> Bool {
        guard manager.significantChangesAvailable else {
            return false
        }
        manager.beginSignificantChanges()
        return true
    }

    func stop() async {
        manager.endSignificantChanges()
    }

    // MARK: - GeofenceArming

    func arm(desired: [TrackedZone], reregister: Bool) async -> Bool {
        guard manager.regionsAvailable else {
            return false
        }
        let wanted = Set(desired.map { $0.key })
        let held = manager.heldRegionKeys
        // Ce que le système détient et que la liste ne veut plus est retiré : le système fait foi de ce qu'il surveille.
        for key in held.subtracting(wanted) {
            manager.unwatch(key: key)
            requestedStates.remove(key)
        }
        let limit = manager.maximumRegionRadius
        for zone in desired where reregister || !held.contains(zone.key) {
            manager.watch(zone, radius: limit > 0 ? min(Double(zone.radiusM), limit) : Double(zone.radiusM))
            // Dedans à l'armement = une arrivée ; dehors = rien n'est inventé (DET3).
            requestedStates.insert(zone.key)
            manager.askRegionState(key: zone.key)
        }
        return true
    }

    func requestStates(keys: [String]) async {
        let held = manager.heldRegionKeys
        for key in keys where held.contains(key) {
            requestedStates.insert(key)
            manager.askRegionState(key: key)
        }
    }

    func disarmAll() async {
        noReuseUpToMillis = clock()
        for key in manager.heldRegionKeys {
            manager.unwatch(key: key)
        }
        requestedStates.removeAll()
    }
}
