import Foundation
import UserNotifications

// SRV-4I : la réception de « {Prénom} a donné des nouvelles ». L'alerte est AFFICHÉE PAR LE SYSTÈME, sans code de l'app (pas d'extension de
// service en v1) : le serveur envoie des clés de texte (`notif_circle_update_title` et `_body`) et le prénom, que le système résout dans la
// langue du téléphone. Ce délégué ne s'occupe que de DEUX moments :
// - l'app est au PREMIER PLAN : aucune notification du système (le Feed se met déjà à jour tout seul) ; si la Miff est bien une des Miffs du
//   compte connecté, `onForeground` peut garder une ligne de centre silencieuse ;
// - un TOUCHER sur la notification : ouvre le Feed de la Miff concernée, `onTap` reçoit la cible VALIDÉE.
//
// SÉCURITÉ : la donnée du message vient du réseau. Elle passe par `CircleNewsParser` (type, forme des identifiants, auteur obligatoire) ; un
// message qui ne ressemble pas exactement à ce qu'on attend est ignoré sans trace. Jamais une URL, jamais un chemin : seulement des identifiants
// que `PushOpenRules` compare aux Miffs du compte connecté, côté app (appartenance inconnue, Miff étrangère, propre update, personne de
// connecté : ignoré). Rien n'est journalisé.

/// Reçoit les réponses du système pour les messages du serveur. Doit être retenu par l'app (le système n'en garde qu'une référence faible).
final class PushNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    /// Un message du serveur arrive pendant que l'app est ouverte (cible validée de forme, pas encore comparée aux Miffs du compte).
    var onForeground: ((CircleNewsTarget) -> Void)?
    /// La personne a touché la notification (cible validée de forme, pas encore comparée aux Miffs du compte).
    var onTap: ((CircleNewsTarget) -> Void)?
    /// Un autre délégué (les rappels locaux) à qui passer tout ce qui n'est PAS un message du serveur. Faible : il appartient à l'app.
    weak var next: UNUserNotificationCenterDelegate?

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let info = notification.request.content.userInfo
        if let target = CircleNewsParser.parse(info) {
            onForeground?(target)
            // App ouverte : aucune bannière, aucun son (la carte du Feed et la cloche parlent déjà).
            completionHandler([])
            return
        }
        if let next {
            next.userNotificationCenter?(center, willPresent: notification, withCompletionHandler: completionHandler)
            return
        }
        completionHandler([])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        if let target = CircleNewsParser.parse(info) {
            // Seulement le toucher « normal » de la notification : un geste de rejet ne rouvre rien.
            if response.actionIdentifier == UNNotificationDefaultActionIdentifier {
                onTap?(target)
            }
            completionHandler()
            return
        }
        if let next {
            next.userNotificationCenter?(center, didReceive: response, withCompletionHandler: completionHandler)
            return
        }
        completionHandler()
    }
}
