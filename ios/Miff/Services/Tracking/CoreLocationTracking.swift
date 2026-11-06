import CoreLocation
import Foundation

// CONFIDENTIALITÉ ET SÉCURITÉ, pour ce fichier : c'est LE SEUL endroit de l'app où l'on demande une position au système et où l'on
// surveille des régions (avec `SystemDevicePermissions`, qui ne fait que lire l'ÉTAT des autorisations).
// - Une position n'est lue que par `current()` et par les signalements de changement significatif de `start()`, tous deux appelés
//   par le moteur APRÈS qu'il a vérifié l'accord de la personne, « Toujours » et la position exacte (AUT7).
// - LES RAPPELS D'ENTRÉE ET DE SORTIE DE RÉGION (`didEnterRegion`, `didExitRegion`, `didDetermineState`) NE LISENT AUCUNE POSITION : ils
//   ne prennent que l'identifiant de la région (une clé de zone) et la nature de l'événement (critère VIE1 ; `TrackingPrivacyTests`
//   lit les sources). Le moteur redemande lui-même une position, quand il en a besoin et le droit.
// - Une coordonnée devient tout de suite un `Fix` (dont la `description` ne montre rien) ; aucune n'est journalisée ni copiée.
// - Aucun mode d'arrière-plan « location » (guideline 2.5.4) : la surveillance de régions et le changement de position significatif
//   relancent l'app sans lui, et on ne fait aucune mise à jour CONTINUE de la position. Une fenêtre de relance a une dizaine de secondes.
// - Aucun journal (print, NSLog, os_log), aucun événement Analytics.

/// Ce que le système rend, une fois dépouillé de tout ce qui n'est pas nécessaire.
private enum LocationEvent {
    case fix(Fix)
    case fixFailed
    case region(TransitionKind, String)
    case regionState(String, Bool)
    case monitoringFailed
    case authorization(AuthorizationFingerprint)
}

/// L'autorisation telle que le système la décrit : sert à ne signaler que les VRAIS changements (le système rappelle aussi au démarrage).
private struct AuthorizationFingerprint: Equatable {
    let status: CLAuthorizationStatus
    let fullAccuracy: Bool
}

/// Reçoit les rappels du système. Un objet à part, non isolé (comme dans `SystemDevicePermissions`), pour que le protocole de
/// CoreLocation n'impose aucune isolation à la classe de l'app ; les rappels arrivent sur le fil où le gestionnaire a été créé (le fil
/// principal) et sont transmis à `CoreLocationTracking`.
private final class LocationBridge: NSObject, CLLocationManagerDelegate {
    var handler: ((LocationEvent) -> Void)?

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // Une mesure sans précision valide (négative) n'est pas une position.
        guard let last = locations.last, last.horizontalAccuracy >= 0 else {
            return
        }
        let millis = Int(last.timestamp.timeIntervalSince1970 * 1000)
        handler?(.fix(Fix(lat: last.coordinate.latitude, lng: last.coordinate.longitude, measuredAtMillis: millis)))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        handler?(.fixFailed)
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        handler?(.region(.enter, region.identifier))
    }

    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        handler?(.region(.exit, region.identifier))
    }

    func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        switch state {
        case .inside:
            handler?(.regionState(region.identifier, true))
        case .outside:
            handler?(.regionState(region.identifier, false))
        case .unknown:
            break
        @unknown default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?, withError error: Error) {
        handler?(.monitoringFailed)
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let fingerprint = AuthorizationFingerprint(
            status: manager.authorizationStatus,
            fullAccuracy: manager.accuracyAuthorization == .fullAccuracy
        )
        handler?(.authorization(fingerprint))
    }
}

/// La surveillance de régions, le changement de position significatif et la position unique, d'après le gestionnaire de localisation du
/// système. Créé au démarrage de l'app, y compris quand le système la relance en arrière-plan pour un événement de zone : le
/// gestionnaire et son délégué doivent exister tout de suite pour que l'événement en attente soit livré.
@MainActor
final class CoreLocationTracking: PositionSource, GeofenceArming, MovementUpdates {
    private let manager: CLLocationManager
    private let bridge = LocationBridge()
    private var sink: TrackingEventSink?
    private var pendingFix: CheckedContinuation<Fix?, Never>?
    private var knownAuthorization: AuthorizationFingerprint?

    init() {
        manager = CLLocationManager()
        manager.delegate = bridge
        // Une précision de cent mètres suffit pour choisir les zones et pour la dernière position connue (décision du PM : précision
        // normale, 5 décimales), et ménage la batterie.
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        bridge.handler = { [weak self] event in
            Task { @MainActor in
                self?.handle(event)
            }
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
            resolvePending(with: lastKnownFix())
        case .region(let kind, let key):
            if ZoneKey.isValid(key) {
                sink?.zoneTransition(kind, zoneKey: key)
            }
        case .regionState(let key, let isInside):
            if ZoneKey.isValid(key) {
                sink?.zoneStateKnown(zoneKey: key, isInside: isInside)
            }
        case .monitoringFailed:
            sink?.monitoringFailed()
        case .authorization(let fingerprint):
            let changed = knownAuthorization != nil && knownAuthorization != fingerprint
            knownAuthorization = fingerprint
            if changed {
                sink?.accessChanged()
            }
        }
    }

    /// Une position qui arrive : la réponse à `current()` si quelqu'un l'attend, sinon un signalement de déplacement significatif.
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
    private func lastKnownFix() -> Fix? {
        guard let last = manager.location, last.horizontalAccuracy >= 0 else {
            return nil
        }
        let age = Date().timeIntervalSince(last.timestamp) * 1000
        guard age >= 0, Int(age) <= TrackingLimits.systemFixMaxAgeMillis else {
            return nil
        }
        let millis = Int(last.timestamp.timeIntervalSince1970 * 1000)
        return Fix(lat: last.coordinate.latitude, lng: last.coordinate.longitude, measuredAtMillis: millis)
    }

    // MARK: - PositionSource

    func current() async -> Fix? {
        // Une demande restée sans réponse (le moteur a cessé d'attendre) est libérée avant d'en faire une nouvelle.
        resolvePending(with: nil)
        return await withCheckedContinuation { (continuation: CheckedContinuation<Fix?, Never>) in
            pendingFix = continuation
            manager.requestLocation()
        }
    }

    // MARK: - MovementUpdates

    func start() async -> Bool {
        guard CLLocationManager.significantLocationChangeMonitoringAvailable() else {
            return false
        }
        manager.startMonitoringSignificantLocationChanges()
        return true
    }

    func stop() async {
        manager.stopMonitoringSignificantLocationChanges()
    }

    // MARK: - GeofenceArming

    func arm(desired: [TrackedZone], reregister: Bool) async -> Bool {
        guard CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self) else {
            return false
        }
        let wanted = Set(desired.map { $0.key })
        let held = manager.monitoredRegions
        // Ce que le système détient et que la liste ne veut plus est retiré : le système fait foi de ce qu'il surveille.
        for region in held where !wanted.contains(region.identifier) {
            manager.stopMonitoring(for: region)
        }
        let heldKeys = Set(held.map { $0.identifier })
        let maxRadius = manager.maximumRegionMonitoringDistance
        for zone in desired where reregister || !heldKeys.contains(zone.key) {
            let region = makeRegion(zone, maxRadius: maxRadius)
            manager.startMonitoring(for: region)
            // Dedans à l'armement = une arrivée ; dehors = rien n'est inventé (DET3). Le système répond par `didDetermineState`.
            manager.requestState(for: region)
        }
        return true
    }

    func disarmAll() async {
        for region in manager.monitoredRegions {
            manager.stopMonitoring(for: region)
        }
    }

    private func makeRegion(_ zone: TrackedZone, maxRadius: CLLocationDistance) -> CLCircularRegion {
        let center = CLLocationCoordinate2D(latitude: zone.lat, longitude: zone.lng)
        let radius = maxRadius > 0 ? min(Double(zone.radiusM), maxRadius) : Double(zone.radiusM)
        let region = CLCircularRegion(center: center, radius: radius, identifier: zone.key)
        region.notifyOnEntry = true
        region.notifyOnExit = true
        return region
    }
}
