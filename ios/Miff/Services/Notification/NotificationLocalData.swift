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
    /// L'instantané des faits de couleur et le registre des rappels.
    let plan: NotificationPlanStoring

    /// Fin de session (déconnexion, suppression du compte, session prise par un autre appareil) : les choix et le centre partent.
    func eraseSession() {
        preferences.forgetAll()
        center.clear()
        plan.clear()
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
            secondChance: UserDefaultsNotificationSecondChanceStore(),
            plan: NotificationPlanFileStore()
        )
    }
}

/// Les demandes de changement d'onglet venues d'ailleurs que de la barre (le toucher sur une ligne du centre ou sur une notification). Une
/// demande porte un numéro : demander deux fois le même onglet est bien deux demandes.
struct TabRequest: Equatable {
    let serial: Int
    let tab: MainTab
}

@MainActor
final class TabRequests: ObservableObject {
    @Published private(set) var latest: TabRequest?
    private var counter = 0
    private var handled = 0

    func request(_ tab: MainTab) {
        counter += 1
        latest = TabRequest(serial: counter, tab: tab)
    }

    /// La demande que la coquille n'a pas encore traitée, une seule fois (un toucher sur une notification peut arriver avant que la
    /// coquille existe : app lancée par la notification).
    func takePending() -> TabRequest? {
        guard let latest, latest.serial > handled else {
            return nil
        }
        handled = latest.serial
        return latest
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
        case .profile, .placeRequests:
            return .profile
        }
    }
}
