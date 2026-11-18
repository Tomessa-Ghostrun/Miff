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
    /// « Depuis ta dernière visite » : la dernière image des proches que le Feed a montrée (identifiants, catégories et heures).
    let lastVisit: LastVisitStoring

    /// Fin de session (déconnexion, suppression du compte, session prise par un autre appareil) : les choix et le centre partent.
    func eraseSession() {
        preferences.forgetAll()
        center.clear()
        plan.clear()
        lastVisit.clear()
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
            plan: NotificationPlanFileStore(),
            lastVisit: LastVisitFileStore()
        )
    }
}

/// Les demandes de changement d'onglet venues d'ailleurs que de la barre (le toucher sur une ligne du centre ou sur une notification). Une
/// demande porte un numéro : demander deux fois le même onglet est bien deux demandes.
struct TabRequest: Equatable {
    let serial: Int
    let tab: MainTab
}

/// NOTIF-P1I : amener la carte du membre `uid` de la Miff `miffId` à l'écran du Feed. Des identifiants seulement, jamais un nom.
struct FeedFocus: Equatable, CustomStringConvertible {
    let miffId: String
    let uid: String

    var description: String {
        "FeedFocus(redacted)"
    }
}

@MainActor
final class TabRequests: ObservableObject {
    @Published private(set) var latest: TabRequest?
    /// Le membre dont le Feed doit amener la carte à l'écran (un toucher sur une ligne de « Depuis ta dernière visite »). Le Feed l'oublie
    /// dès qu'il l'a pris (`consumeFocus`), et la déconnexion l'efface.
    @Published private(set) var pendingFocus: FeedFocus?
    private var counter = 0
    private var handled = 0

    func requestFocus(_ target: FeedFocus) {
        pendingFocus = target
    }

    /// Le Feed a pris la demande de `target` (ou n'a trouvé aucune carte) ; une demande plus récente pour un autre membre est gardée.
    func consumeFocus(_ target: FeedFocus) {
        if pendingFocus == target {
            pendingFocus = nil
        }
    }

    func clearFocus() {
        pendingFocus = nil
    }

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
        case .feed, .feedCard:
            return .feed
        case .profile, .placeRequests:
            return .profile
        }
    }
}
