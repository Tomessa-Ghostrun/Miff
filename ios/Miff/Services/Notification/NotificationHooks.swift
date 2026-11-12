import Foundation
import UserNotifications

// Les trois petits ponts entre les notifications et le reste de l'app. CONFIDENTIALITÉ : seulement des identifiants de Miff, des
// présences (`in_trusted`, `out`, `in_risk`, `unknown`) et des heures ; jamais une coordonnée, un nom de lieu ni une adresse.

/// Ce que le suivi des lieux dit aux notifications : un nouveau statut de MA présence (arrivée, départ, zone de danger) et MON voyage
/// (début, nouvelle date, fin). Jamais une coordonnée, un lieu ni un nom.
struct TrackingNotificationHooks {
    let onPresence: (String, StatusFact) -> Void
    /// `(compte, fin du voyage ou nil, fin précédente ou nil)`.
    let onTravel: (String, Int?, Int?) -> Void
}

/// La copie locale du suivi des lieux, vue par les notifications : à chaque sauvegarde, un NOUVEAU statut en attente d'une Miff (une arrivée,
/// un départ, une entrée en zone de danger détectés par ce téléphone) est annoncé à `onPresence`. Aucune modification du moteur de suivi :
/// il sauvegarde sa copie à chaque changement, c'est là que les notifications écoutent. Décorateur : tout le reste passe tel quel.
final class PresenceNotifyingLocalStore: TrackingLocalStore {
    private let wrapped: TrackingLocalStore
    private let onPresence: (String, StatusFact) -> Void
    /// MODE VOYAGE : `(compte, fin du voyage ou nil, fin précédente ou nil)` à chaque changement de MON voyage (début, nouvelle date, fin).
    private let onTravel: ((String, Int?, Int?) -> Void)?
    private let lock = NSLock()
    private var lastSeen: [String: StatusFact] = [:]
    private var lastTravel: Int?

    init(
        wrapping wrapped: TrackingLocalStore,
        onPresence: @escaping (String, StatusFact) -> Void,
        onTravel: ((String, Int?, Int?) -> Void)? = nil
    ) {
        self.wrapped = wrapped
        self.onPresence = onPresence
        self.onTravel = onTravel
    }

    func load() -> LocalState? {
        let state = wrapped.load()
        lock.lock()
        lastTravel = state?.travel?.untilMillis
        lock.unlock()
        return state
    }

    func save(_ state: LocalState) {
        wrapped.save(state)
        notifyTravel(state)
        for (miffId, miff) in state.miffs {
            let fact = miff.pending?.fact
            lock.lock()
            let changed = fact != nil && lastSeen[miffId] != fact
            lastSeen[miffId] = fact
            lock.unlock()
            if changed, let fact {
                onPresence(miffId, fact)
            }
        }
    }

    func clear() {
        wrapped.clear()
        lock.lock()
        lastSeen = [:]
        lastTravel = nil
        lock.unlock()
    }

    /// MODE VOYAGE : seule la fin du voyage est annoncée (jamais une position ni un lieu). Une première sauvegarde sans voyage ne dit rien.
    private func notifyTravel(_ state: LocalState) {
        let current = state.travel?.untilMillis
        lock.lock()
        let previous = lastTravel
        let changed = current != previous
        lastTravel = current
        lock.unlock()
        if changed {
            onTravel?(state.uid, current, previous)
        }
    }
}

/// Reçoit les réponses du système : une notification qui arrive au premier plan (rien n'est affiché, le centre garde une entrée silencieuse)
/// et un toucher sur une notification (ouvre l'écran Update ou le Feed). Doit être retenu par l'app (le système ne garde qu'une référence
/// faible).
final class NotificationResponseDelegate: NSObject, UNUserNotificationCenterDelegate {
    var onForeground: ((String) -> Void)?
    var onTap: ((String?, String?, String?) -> Void)?

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // App au premier plan : aucune notification du système (la bulle du Feed et l'anneau de l'avatar parlent déjà).
        onForeground?(notification.request.identifier)
        completionHandler([])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        onTap?(info["type"] as? String, info["key"] as? String, info["miffId"] as? String)
        completionHandler()
    }
}
