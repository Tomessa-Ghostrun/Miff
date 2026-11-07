import Foundation
import UserNotifications

/// Les stockages locaux dont la suppression d'un compte efface le contenu POUR CE COMPTE.
struct AccountLocalStores {
    let notificationAsks: NotificationAskStore
    let pendingMiffDeletions: PendingDeletionStore
    let activeMiff: ActiveMiffStore
    let notifications: LocalNotificationsCleaner
    /// La copie locale du suivi des lieux (état « dedans / dehors », statuts en attente) : déjà effacée par l'arrêt du suivi, mais cet
    /// arrêt est borné à 5 s ; la suppression l'efface aussi DIRECTEMENT, pour ne pas en dépendre.
    let trackingLocal: TrackingLocalStore
    /// Le journal local de la détection (builds Debug et Internal seulement ; rien en publication) : même raison.
    let detectionJournal: DetectionJournal
}

/// Ce que la suppression d'un compte efface du téléphone POUR CE COMPTE (AUTH-5c, ticket §5.10), en plus de ce que la fin d'une session
/// arrête déjà (`SessionTeardown` : écoutes, suivi des lieux et régions désarmées, statuts en attente, annuaire des Miffs, brouillon du
/// profil). Tout stockage lié à un compte doit figurer dans l'une des deux listes ; `AccountLocalStorageGuardTests` échoue quand un
/// nouveau fichier de stockage local apparaît sans que cette liste ait été relue.
///
/// Gardés exprès (rien de personnel) : la langue, le fait d'avoir vu le carrousel, le repère d'installation.
///
/// Aucun élément du Trousseau n'est propre à l'app : la session Firebase et celle de Google Sign-In sont retirées par la déconnexion
/// (`FirebaseAuthGateway.signOut`), et l'identifiant de la session d'appareil vit dans un fichier, jamais dans le Trousseau.
enum AccountLocalData {
    static func erasers(_ stores: AccountLocalStores) -> [LocalDataEraser] {
        [
            // L'écran des notifications a été montré à ce compte : un drapeau par compte.
            { uid in stores.notificationAsks.forget(uid: uid) },
            // Les marqueurs de suppression d'une Miff de ce compte (la suppression du compte a tout supprimé, ils n'ont plus de sens).
            { uid in
                for miffId in stores.pendingMiffDeletions.ids(uid: uid) {
                    stores.pendingMiffDeletions.remove(uid: uid, miffId: miffId)
                }
            },
            // La Miff « active » choisie (Feed F8).
            { _ in stores.activeMiff.clear() },
            // Les notifications encore affichées ou planifiées par l'app.
            { _ in await stores.notifications.removeAll() },
            // La copie locale du suivi et le journal de détection, directement.
            { _ in stores.trackingLocal.clear() },
            { _ in stores.detectionJournal.clear() }
        ]
    }
}

/// Retire les notifications locales de l'app (planifiées et déjà affichées). Aujourd'hui l'app n'en planifie aucune : c'est le filet qui
/// garantit qu'aucune n'arrive pour un compte supprimé le jour où elles existeront.
protocol LocalNotificationsCleaner {
    func removeAll() async
}

struct SystemLocalNotificationsCleaner: LocalNotificationsCleaner {
    func removeAll() async {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }
}
