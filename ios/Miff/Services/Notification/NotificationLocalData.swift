import Foundation

/// Tout ce que les notifications gardent sur le téléphone, avec ce qui l'efface (le garde-fou `AccountLocalStorageGuardTests` exige que
/// chaque stockage local soit relu pour la suppression du compte). Rien de ceci ne contient une position, une adresse, un nom de lieu ni
/// une photo.
struct NotificationLocalData {
    /// Les choix de Réglages > Notifications.
    let preferences: NotificationPreferencesStore
    /// Le centre de notifications.
    let center: NotificationCenterStoring
    /// La carte « deuxième chance ».
    let secondChance: NotificationSecondChanceStore

    /// Fin de session (déconnexion, suppression du compte, session prise par un autre appareil) : les choix et le centre partent.
    func eraseSession() {
        preferences.forgetAll()
        center.clear()
    }

    /// Suppression du compte : la fin de session, plus la carte « deuxième chance » de ce compte.
    func eraseAccount(uid: String) {
        eraseSession()
        secondChance.forget(uid: uid)
    }

    static func live() -> NotificationLocalData {
        NotificationLocalData(
            preferences: UserDefaultsNotificationPreferencesStore(),
            center: NotificationCenterFileStore(),
            secondChance: UserDefaultsNotificationSecondChanceStore()
        )
    }
}

/// Les demandes de changement d'onglet venues d'ailleurs que de la barre (le toucher sur une ligne du centre de notifications). Une
/// demande porte un numéro : demander deux fois le même onglet est bien deux demandes.
struct TabRequest: Equatable {
    let serial: Int
    let tab: MainTab
}

@MainActor
final class TabRequests: ObservableObject {
    @Published private(set) var latest: TabRequest?
    private var counter = 0

    func request(_ tab: MainTab) {
        counter += 1
        latest = TabRequest(serial: counter, tab: tab)
    }
}

/// Où mène une notification (pur, testé).
enum NotificationNavigation {
    /// L'onglet à montrer. Sans le bouton central (builds destinés à des testeurs extérieurs), l'écran Update n'existe pas : le Feed
    /// l'est à la place, jamais une erreur.
    static func tab(for destination: NotificationDestination, showsUpdateButton: Bool) -> MainTab {
        switch destination {
        case .update:
            return showsUpdateButton ? .update : .feed
        case .feed:
            return .feed
        }
    }
}
