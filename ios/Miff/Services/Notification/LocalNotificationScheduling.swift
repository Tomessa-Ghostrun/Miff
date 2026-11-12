import Foundation
import UserNotifications

// Les notifications LOCALES du système (UNUserNotificationCenter). CONFIDENTIALITÉ : une demande ne porte que le texte fixe de son type
// (aucun prénom, aucun lieu, aucune position), l'identifiant de la Miff (pour le regroupement) et la clé de l'événement. Pas de
// pastille sur l'icône, pas de Time Sensitive, pas de notification critique. Rien n'est journalisé.

/// Une demande à programmer.
struct LocalNotificationRequest: Equatable {
    let identifier: String
    let title: String
    let body: String
    /// Le regroupement par Miff dans le tiroir de notifications.
    let threadIdentifier: String
    let categoryIdentifier: String
    let userInfo: [String: String]
    /// Dans combien de secondes (au moins 1) : le déclencheur est un INTERVALLE, recalculé à chaque réconciliation.
    let delaySeconds: TimeInterval
    /// Nuit calme : livrée sans son ni vibration (`.passive`).
    let quiet: Bool
}

/// Ce que le réconciliateur demande au système (un double dans les tests). Seules les demandes de Miff (`NotificationIdentifier.prefix`)
/// sont vues ou touchées.
protocol LocalNotificationScheduling: AnyObject {
    func pendingIdentifiers() async -> Set<String>
    func deliveredIdentifiers() async -> Set<String>
    /// Programme la demande ; un identifiant déjà utilisé est REMPLACÉ.
    func add(_ request: LocalNotificationRequest) async
    func removePending(_ identifiers: [String]) async
    func removeDelivered(_ identifiers: [String]) async
}

enum NotificationCategory {
    static let prefix = "miff.notif.category."

    static func identifier(for type: NotificationType) -> String {
        "\(prefix)\(type.rawValue)"
    }
}

final class SystemLocalNotificationScheduler: LocalNotificationScheduling {
    private var center: UNUserNotificationCenter {
        UNUserNotificationCenter.current()
    }

    /// Une catégorie par type (sans bouton d'action en phase 0) : le toucher sait de quelle notification il s'agit.
    func registerCategories() {
        let categories = NotificationType.allCases.map {
            UNNotificationCategory(
                identifier: NotificationCategory.identifier(for: $0), actions: [], intentIdentifiers: [], options: []
            )
        }
        center.setNotificationCategories(Set(categories))
    }

    func pendingIdentifiers() async -> Set<String> {
        let requests = await center.pendingNotificationRequests()
        return Set(requests.map { $0.identifier }.filter { $0.hasPrefix(NotificationIdentifier.prefix) })
    }

    func deliveredIdentifiers() async -> Set<String> {
        let delivered = await center.deliveredNotifications()
        return Set(delivered.map { $0.request.identifier }.filter { $0.hasPrefix(NotificationIdentifier.prefix) })
    }

    func add(_ request: LocalNotificationRequest) async {
        let content = UNMutableNotificationContent()
        content.title = request.title
        content.body = request.body
        content.threadIdentifier = request.threadIdentifier
        content.categoryIdentifier = request.categoryIdentifier
        content.userInfo = request.userInfo
        if request.quiet {
            content.interruptionLevel = .passive
        } else {
            content.sound = .default
            content.interruptionLevel = .active
        }
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, request.delaySeconds), repeats: false)
        let system = UNNotificationRequest(identifier: request.identifier, content: content, trigger: trigger)
        _ = try? await center.add(system)
    }

    func removePending(_ identifiers: [String]) async {
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
    }

    func removeDelivered(_ identifiers: [String]) async {
        center.removeDeliveredNotifications(withIdentifiers: identifiers)
    }
}

/// L'état de l'autorisation, lu seul (un double dans les tests).
protocol NotificationAuthorizationReading {
    func authorization() async -> NotificationAuthorization
}

struct SystemNotificationAuthorizationReader: NotificationAuthorizationReading {
    func authorization() async -> NotificationAuthorization {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        return SystemPermissionMapping.notifications(settings.authorizationStatus)
    }
}
