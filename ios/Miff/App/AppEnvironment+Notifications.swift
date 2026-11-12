import Foundation

extension AppEnvironment {
    /// Réglages > Notifications (NOTIF-2I) : l'état de l'autorisation du téléphone (lu, jamais demandé sans un toucher) et les choix.
    func makeNotificationSettingsViewModel() -> NotificationSettingsViewModel {
        let reconciler = notifications.reconciler
        return NotificationSettingsViewModel(
            permissions: devicePermissions,
            service: notifications.preferences,
            counters: { reconciler.followUpCounters() }
        )
    }

    /// Le centre de notifications (NOTIF-6I).
    func makeNotificationCenterViewModel() -> NotificationCenterViewModel {
        NotificationCenterViewModel(service: notifications.center, permissions: devicePermissions)
    }

    /// Le nom d'une Miff pour une ligne du centre : seulement quand la personne en a plusieurs (sinon nil, le nom n'ajouterait rien).
    func notificationMiffName(_ miffId: String) -> String? {
        guard case .ready(let ready) = miffDirectory.state, ready.miffs.count > 1 else {
            return nil
        }
        return ready.miffs.first { $0.id == miffId }?.name
    }
}
