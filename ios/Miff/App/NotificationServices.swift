import Foundation

/// Les services des notifications (NOTIF-P0), construits d'un seul tenant. Rien ici n'ouvre Firestore, ne lit une position ni ne demande
/// quoi que ce soit au système : l'autorisation n'est demandée que par un toucher de la personne (amorce, Réglages, carte du Feed).
@MainActor
struct NotificationServices {
    /// Le centre de notifications local (cloche, liste).
    let center: NotificationCenterService
    /// Les choix de Réglages > Notifications.
    let preferences: NotificationPreferencesService
    /// Les changements d'onglet demandés par un toucher sur une ligne du centre.
    let tabRequests: TabRequests
    /// Tout ce que les notifications gardent sur le téléphone, avec ce qui l'efface.
    let localData: NotificationLocalData

    static func make(currentUid: @escaping () -> String?) -> NotificationServices {
        let data = NotificationLocalData.live()
        return NotificationServices(
            center: NotificationCenterService(store: data.center, currentUid: currentUid),
            preferences: NotificationPreferencesService(store: data.preferences, currentUid: currentUid),
            tabRequests: TabRequests(),
            localData: data
        )
    }

    /// Fin de session (déconnexion, suppression du compte, session prise par un autre appareil) : le centre et les choix sont vidés, et les
    /// notifications encore affichées ou en attente sont retirées pour que la personne suivante n'en voie aucune.
    var teardown: SessionTeardown {
        let center = self.center
        let preferences = self.preferences
        let data = self.localData
        return {
            await MainActor.run {
                center.purge()
                preferences.forget()
            }
            data.eraseSession()
            await SystemLocalNotificationsCleaner().removeAll()
        }
    }
}
