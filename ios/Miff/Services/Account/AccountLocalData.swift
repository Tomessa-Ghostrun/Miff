import Foundation
import UserNotifications

/// Ce que la suppression d'un compte efface du téléphone POUR CE COMPTE (AUTH-5c, ticket §5.10), en plus de ce que la fin d'une session
/// arrête déjà (`SessionTeardown` : écoutes, suivi des lieux et régions désarmées, copie locale du suivi, journal de détection, statuts
/// en attente, annuaire des Miffs, carnets du profil). Tout stockage lié à un compte doit figurer dans l'une des deux listes ; un test
/// le vérifie pour la liste d'ici (`AccountLocalDataTests`).
///
/// Gardés exprès (rien de personnel) : la langue, le fait d'avoir vu le carrousel, le repère d'installation.
///
/// Aucun élément du Trousseau n'est propre à l'app : la session Firebase et celle de Google Sign-In sont retirées par la déconnexion
/// (`FirebaseAuthGateway.signOut`), et l'identifiant de la session d'appareil vit dans un fichier, jamais dans le Trousseau.
enum AccountLocalData {
    static func erasers(
        notificationAsks: NotificationAskStore,
        pendingMiffDeletions: PendingDeletionStore,
        activeMiff: ActiveMiffStore,
        notifications: LocalNotificationsCleaner
    ) -> [LocalDataEraser] {
        [
            // L'écran des notifications a été montré à ce compte : un drapeau par compte.
            { uid in notificationAsks.forget(uid: uid) },
            // Les marqueurs de suppression d'une Miff de ce compte (la suppression du compte a tout supprimé, ils n'ont plus de sens).
            { uid in
                for miffId in pendingMiffDeletions.ids(uid: uid) {
                    pendingMiffDeletions.remove(uid: uid, miffId: miffId)
                }
            },
            // La Miff « active » choisie (Feed F8).
            { _ in activeMiff.clear() },
            // Les notifications encore affichées ou planifiées par l'app.
            { _ in await notifications.removeAll() }
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
