import CoreLocation
import Foundation

// CONFIDENTIALITÉ ET SÉCURITÉ, pour ce fichier : c'est LE SEUL endroit de l'app où l'on demande une position au système et où l'on
// surveille des régions (avec `SystemDevicePermissions`, qui ne fait que lire l'ÉTAT des autorisations).
// - Une position n'est demandée que par `LocationTracking`, APRÈS que le moteur a vérifié l'accord de la personne, « Toujours » et la
//   position exacte (AUT7).
// - LES RAPPELS D'ENTRÉE ET DE SORTIE DE RÉGION (`didEnterRegion`, `didExitRegion`, `didDetermineState`) NE LISENT AUCUNE POSITION : ils
//   ne prennent que l'identifiant de la région (une clé de zone) et la nature de l'événement (critère VIE1 ; `PermissionPrivacyTests`
//   lit les sources).
// - Une coordonnée devient tout de suite un `Fix` (dont la `description` ne montre rien) ; aucune n'est journalisée ni copiée.
// - Aucun mode d'arrière-plan « location » (guideline 2.5.4) : la surveillance de régions et le changement de position significatif
//   relancent l'app sans lui, et on ne fait aucune mise à jour CONTINUE de la position.
// - Aucun journal, aucun événement Analytics.

/// Reçoit les rappels du système. Un objet à part, non isolé (comme dans `SystemDevicePermissions`), pour que le protocole de
/// CoreLocation n'impose aucune isolation à la classe de l'app ; les rappels arrivent sur le fil où le gestionnaire a été créé (le fil
/// principal) et sont transmis à `SystemLocationManager`.
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
        let latest = AuthorizationState(
            location: SystemPermissionMapping.location(manager.authorizationStatus),
            fullAccuracy: manager.accuracyAuthorization == .fullAccuracy
        )
        handler?(.authorization(latest))
    }
}

/// Le gestionnaire de localisation du système. Créé au démarrage de l'app, y compris quand le système la relance en arrière-plan pour
/// un événement de zone : le gestionnaire et son délégué doivent exister tout de suite pour que l'événement en attente soit livré.
@MainActor
final class SystemLocationManager: LocationManaging {
    var onEvent: (@MainActor (LocationEvent) -> Void)?
    private let manager: CLLocationManager
    private let bridge = LocationBridge()
    /// Les régions qu'on vient de créer : `monitoredRegions` peut ne pas les contenir juste après `startMonitoring` (voir
    /// `RegionRegistry`).
    private var created = RegionRegistry<CLRegion>()

    init() {
        manager = CLLocationManager()
        manager.delegate = bridge
        // Une précision de cent mètres suffit pour choisir les zones et pour la dernière position connue (décision du PM : précision
        // normale, 5 décimales), et ménage la batterie.
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        bridge.handler = { [weak self] event in
            Task { @MainActor in
                self?.onEvent?(event)
            }
        }
    }

    var heldRegionKeys: Set<String> {
        let visible = Set(manager.monitoredRegions.map { $0.identifier })
        created.prune(visible: visible, now: Date(), maxAge: TrackingLimits.createdRegionSeconds)
        // Une région créée à l'instant compte comme surveillée (et sera retirée si la liste ne la veut plus).
        return visible.union(created.keys)
    }

    var maximumRegionRadius: Double {
        manager.maximumRegionMonitoringDistance
    }

    var systemFix: Fix? {
        guard let last = manager.location, last.horizontalAccuracy >= 0 else {
            return nil
        }
        let millis = Int(last.timestamp.timeIntervalSince1970 * 1000)
        return Fix(lat: last.coordinate.latitude, lng: last.coordinate.longitude, measuredAtMillis: millis)
    }

    var regionsAvailable: Bool {
        CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self)
    }

    var significantChangesAvailable: Bool {
        CLLocationManager.significantLocationChangeMonitoringAvailable()
    }

    func watch(_ zone: TrackedZone, radius: Double) {
        let center = CLLocationCoordinate2D(latitude: zone.lat, longitude: zone.lng)
        let region = CLCircularRegion(center: center, radius: radius, identifier: zone.key)
        region.notifyOnEntry = true
        region.notifyOnExit = true
        created.remember(region, key: zone.key, at: Date())
        manager.startMonitoring(for: region)
    }

    func unwatch(key: String) {
        if let region = created.region(for: key) {
            manager.stopMonitoring(for: region)
        }
        created.forget(key: key)
        for region in manager.monitoredRegions where region.identifier == key {
            manager.stopMonitoring(for: region)
        }
    }

    func askRegionState(key: String) {
        // La région qu'on vient de créer d'abord (la liste du système peut ne pas l'avoir encore), sinon celle que le système garde.
        let region = created.region(for: key) ?? manager.monitoredRegions.first(where: { $0.identifier == key })
        if let region {
            manager.requestState(for: region)
        }
    }

    func askPosition() {
        manager.requestLocation()
    }

    func beginSignificantChanges() {
        manager.startMonitoringSignificantLocationChanges()
    }

    func endSignificantChanges() {
        manager.stopMonitoringSignificantLocationChanges()
    }
}
