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
//
// LA DÉCISION est séparée du système : `routeWillPresent` et `routeResponse` ne reçoivent que des données (le contenu du message, l'identifiant de
// l'action) et rendent ce qu'il faut faire ; les deux méthodes du protocole du système ne font que l'appliquer. Un `UNNotification` ne se
// fabrique pas à la main : c'est ce qui rend le routage (message du serveur, ou chaîné au délégué suivant) testable.

/// Ce que le délégué fait d'une notification qui arrive pendant que l'app est ouverte.
enum PushPresentationRoute: Equatable {
    /// Un message du serveur : aucune bannière, aucun son.
    case suppress
    /// ALERTE « Je suis en danger » (`ALERT_DANGER`) : une alerte d'un proche est affichée MÊME app ouverte, bannière et son (une alerte ne doit pas passer
    /// inaperçue ; seuls les réglages du téléphone peuvent la taire).
    case present
    /// Autre chose (un rappel local) : à passer au délégué suivant, ou à ne pas afficher s'il n'y en a pas.
    case forward
}

/// Ce que le délégué fait d'une réponse de la personne à une notification.
enum PushResponseRoute: Equatable {
    /// Un message du serveur, touché normalement : ouvrir le Feed (la cible a été donnée à `onTap`).
    case open
    /// Un message du serveur, mais un geste qui n'ouvre rien (rejet).
    case ignore
    /// Autre chose : à passer au délégué suivant.
    case forward
}

/// Reçoit les réponses du système pour les messages du serveur. Doit être retenu par l'app (le système n'en garde qu'une référence faible).
/// UN SEUL délégué est posé sur le centre de notifications (`PushServices.start`) ; les rappels locaux (NOTIF-P0) passent par `next`.
final class PushNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    /// Un message du serveur arrive pendant que l'app est ouverte (cible validée de forme, pas encore comparée aux Miffs du compte).
    var onForeground: ((CircleNewsTarget) -> Void)?
    /// La personne a touché la notification (cible validée de forme, pas encore comparée aux Miffs du compte).
    var onTap: ((CircleNewsTarget) -> Void)?
    /// CNC-I : l'app voit passer un message du serveur (« a donné des nouvelles », début ou fin d'alerte) : au premier plan (`opened` faux : rien n'est
    /// affiché), ou au toucher sur la notification (`opened` vrai : le système l'avait affichée). La ligne du centre s'écrit ici ; l'hôte vérifie que la Miff est
    /// celle du compte avant (`PushArrivalRules`).
    var onArrival: ((PushArrival, _ opened: Bool) -> Void)?
    /// Un autre délégué (les rappels locaux) à qui passer tout ce qui n'est PAS un message du serveur. Faible : il appartient à l'app.
    weak var next: UNUserNotificationCenterDelegate?

    func routeWillPresent(userInfo: [AnyHashable: Any], deliveredAt: Date? = nil) -> PushPresentationRoute {
        guard let target = CircleNewsParser.parse(userInfo) else {
            #if ALERT_DANGER
            if CircleAlertParser.parse(userInfo) != nil {
                noteArrival(userInfo, opened: false, deliveredAt: deliveredAt)
                return .present
            }
            #endif
            return .forward
        }
        noteArrival(userInfo, opened: false, deliveredAt: deliveredAt)
        onForeground?(target)
        return .suppress
    }

    /// CNC-I : dit à l'hôte qu'un message du serveur est passé (la ligne du centre ; jamais une bannière : le système ou le Feed parlent déjà).
    /// `deliveredAt` : l'heure à laquelle le système a livré la notification (`notification.date`) : la ligne est datée de là, pas du toucher.
    private func noteArrival(_ userInfo: [AnyHashable: Any], opened: Bool, deliveredAt: Date?) {
        if var arrival = PushArrivalParser.parse(userInfo) {
            arrival.deliveredAtMillis = deliveredAt.map { Int($0.timeIntervalSince1970 * 1000) }
            onArrival?(arrival, opened)
        }
    }

    func routeResponse(
        userInfo: [AnyHashable: Any],
        actionIdentifier: String,
        deliveredAt: Date? = nil
    ) -> PushResponseRoute {
        guard let target = CircleNewsParser.parse(userInfo) else {
            #if ALERT_DANGER
            // ALERTE : un toucher sur « {Prénom} a déclaré une alerte » ouvre la Miff d'origine (la même demande d'ouverture que « a donné des nouvelles »,
            // comparée aux Miffs du compte avant d'ouvrir quoi que ce soit).
            if let alert = CircleAlertParser.parse(userInfo) {
                guard actionIdentifier == UNNotificationDefaultActionIdentifier else {
                    return .ignore
                }
                noteArrival(userInfo, opened: true, deliveredAt: deliveredAt)
                onTap?(alert.openTarget)
                return .open
            }
            #endif
            return .forward
        }
        // Seulement le toucher « normal » de la notification : un geste de rejet ne rouvre rien.
        guard actionIdentifier == UNNotificationDefaultActionIdentifier else {
            return .ignore
        }
        noteArrival(userInfo, opened: true, deliveredAt: deliveredAt)
        onTap?(target)
        return .open
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        switch routeWillPresent(userInfo: notification.request.content.userInfo, deliveredAt: notification.date) {
        case .suppress:
            // App ouverte : aucune bannière, aucun son (la carte du Feed et la cloche parlent déjà).
            completionHandler([])
        case .present:
            completionHandler([.banner, .list, .sound])
        case .forward:
            if let next {
                next.userNotificationCenter?(
                    center, willPresent: notification, withCompletionHandler: completionHandler
                )
            } else {
                completionHandler([])
            }
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let route = routeResponse(
            userInfo: response.notification.request.content.userInfo,
            actionIdentifier: response.actionIdentifier,
            deliveredAt: response.notification.date
        )
        switch route {
        case .open, .ignore:
            completionHandler()
        case .forward:
            if let next {
                next.userNotificationCenter?(center, didReceive: response, withCompletionHandler: completionHandler)
            } else {
                completionHandler()
            }
        }
    }
}
