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
        NotificationCenterViewModel(
            service: notifications.center, permissions: devicePermissions, directory: notifications.requesters
        )
    }

    /// Le nom d'une Miff pour une ligne du centre : seulement quand la personne en a plusieurs (sinon nil, le nom n'ajouterait rien).
    func notificationMiffName(_ miffId: String) -> String? {
        guard case .ready(let ready) = miffDirectory.state, ready.miffs.count > 1 else {
            return nil
        }
        return ready.miffs.first { $0.id == miffId }?.name
    }
}

extension AppEnvironment {
    /// Les noms dont le centre a besoin pour écrire ses lignes (à l'affichage seulement, rien n'en est gardé dans son fichier).
    func notificationCenterNames() -> NotificationCenterNames {
        NotificationCenterNames(
            miff: { [self] in notificationMiffName($0) },
            requestMiff: { [self] in miffName(of: $0) },
            requesters: notifications.requesters
        )
    }

    /// Le nom d'une Miff quel que soit le nombre de Miffs du compte (la phrase d'une demande dit toujours pour quelle Miff).
    private func miffName(of miffId: String) -> String? {
        guard case .ready(let ready) = miffDirectory.state else {
            return nil
        }
        return ready.miffs.first { $0.id == miffId }?.name
    }
}
