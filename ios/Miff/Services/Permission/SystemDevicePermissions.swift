import CoreLocation
import UIKit
import UserNotifications

// CONFIDENTIALITÉ, pour ce fichier (le SEUL de l'app qui importe CoreLocation, avec `SystemLocationAccessProbe`) : il LIT L'ÉTAT
// des autorisations (`authorizationStatus`, `accuracyAuthorization`, `locationServicesEnabled`) et affiche les fenêtres du système
// APRÈS un toucher de la personne. Il ne lit AUCUNE position : ni coordonnée, ni `startUpdatingLocation`, ni `requestLocation`,
// ni surveillance de zone ou de changement significatif (c'est LOC-4b ; PermissionPrivacyTests lit les sources). Aucun journal,
// aucun événement (critère AUT7, décisions (g) et (h) du PM).

/// Les traductions des types du système vers ceux de l'app, en fonctions pures (testées sans rien demander au système).
enum SystemPermissionMapping {
    static func location(_ status: CLAuthorizationStatus) -> LocationAuthorization {
        switch status {
        case .notDetermined:
            return .notDetermined
        case .restricted:
            return .restricted
        case .denied:
            return .denied
        case .authorizedWhenInUse:
            return .whenInUse
        case .authorizedAlways:
            return .always
        @unknown default:
            // Un état que cette version ne connaît pas n'est jamais pris pour « Toujours » : la personne est renvoyée aux Réglages.
            return .denied
        }
    }

    static func notifications(_ status: UNAuthorizationStatus) -> NotificationAuthorization {
        switch status {
        case .notDetermined:
            return .notDetermined
        case .denied:
            return .denied
        case .authorized, .provisional, .ephemeral:
            return .authorized
        @unknown default:
            return .denied
        }
    }

    /// Ce que le premier statut écrit (`locationAccess`) : `background` = « Toujours » + exacte ; `foreground` = « pendant
    /// l'utilisation », « une fois » ou position approximative ; `denied` = refusée ou restreinte ; `unknown` = jamais demandée.
    static func locationAccess(location: LocationAuthorization, precise: Bool) -> LocationAccess {
        switch location {
        case .notDetermined:
            return .unknown
        case .restricted, .denied:
            return .denied
        case .whenInUse:
            return .foreground
        case .always:
            return precise ? .background : .foreground
        }
    }
}

/// Reçoit « l'autorisation a changé » du système. Un objet à part (et non la classe de l'app) pour que le protocole de CoreLocation
/// n'impose aucune isolation à l'acteur principal ; le gestionnaire est créé sur le fil principal, donc l'appel y arrive.
private final class AuthorizationDelegate: NSObject, CLLocationManagerDelegate {
    var onChange: (() -> Void)?

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        onChange?()
    }
}

@MainActor
final class SystemDevicePermissions: DevicePermissions {
    private let manager: CLLocationManager
    private let delegateProxy: AuthorizationDelegate
    var onLocationChange: (() -> Void)?

    init() {
        let proxy = AuthorizationDelegate()
        let created = CLLocationManager()
        created.delegate = proxy
        manager = created
        delegateProxy = proxy
        proxy.onChange = { [weak self] in
            Task { @MainActor in
                self?.onLocationChange?()
            }
        }
    }

    func snapshot() async -> PermissionSnapshot {
        let status = manager.authorizationStatus
        let precise = manager.accuracyAuthorization == .fullAccuracy
        // `locationServicesEnabled()` peut bloquer : jamais sur le fil principal.
        let services = await Task.detached(priority: .utility) {
            CLLocationManager.locationServicesEnabled()
        }.value
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        return PermissionSnapshot(
            location: SystemPermissionMapping.location(status),
            servicesEnabled: services,
            preciseLocation: precise,
            notifications: SystemPermissionMapping.notifications(settings.authorizationStatus)
        )
    }

    func requestLocationAuthorization() {
        // Seulement la fenêtre d'autorisation, et seulement si le système peut encore l'afficher : aucune position n'est lue.
        guard manager.authorizationStatus == .notDetermined else {
            return
        }
        manager.requestAlwaysAuthorization()
    }

    func requestNotifications() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        UIApplication.shared.open(url)
    }
}

/// La permission de localisation de l'appareil pour le premier statut (ticket Miff §5.4, étape 7) : l'ÉTAT lu au moment où le
/// statut est écrit (jamais une position). Tant que la personne n'a jamais été interrogée, `unknown` : l'app n'affirme jamais un
/// refus qu'elle ne peut pas distinguer d'un « pas encore demandé ».
struct SystemLocationAccessProbe: LocationAccessProbe {
    func current() -> LocationAccess {
        let manager = CLLocationManager()
        return SystemPermissionMapping.locationAccess(
            location: SystemPermissionMapping.location(manager.authorizationStatus),
            precise: manager.accuracyAuthorization == .fullAccuracy
        )
    }
}
