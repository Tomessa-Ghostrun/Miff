import Foundation
import UserNotifications
#if canImport(Combine)
import Combine
#endif

/// L'autorisation des notifications lue dans le système (accordée, y compris provisoire ou éphémère). Lit seulement l'ÉTAT, ne demande rien.
struct SystemNotificationAuthorizationProbe: NotificationAuthorizationProbe {
    func isAuthorized() async -> Bool {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        return SystemPermissionMapping.notifications(settings.authorizationStatus) == .authorized
    }
}

/// L'interrupteur « Quand un proche donne des nouvelles » de la personne connectée (Réglages > Notifications, ticket §6) : actif par défaut,
/// gardé aussitôt (sans bouton « Enregistrer »), annoncé à `onChange` pour que le registre des appareils dise la nouvelle valeur
/// (`notify.updates`) dans la seconde. L'écran des Réglages > Notifications (NOTIF-P0) se branche ici : `isOn` pour l'état, `set(_:)` au toucher.
@MainActor
final class CircleNewsSwitch: ObservableObject {
    @Published private(set) var isOn = true

    /// Appelé après chaque changement RÉEL de valeur.
    var onChange: (() -> Void)?

    private let store: CircleNewsSwitchStore
    private let currentUid: () -> String?

    init(store: CircleNewsSwitchStore, currentUid: @escaping () -> String?) {
        self.store = store
        self.currentUid = currentUid
        reload()
    }

    /// Relit la valeur du compte connecté (actif pour un compte qui n'a jamais choisi, et pour personne).
    func reload() {
        isOn = currentUid().map { store.isOn(uid: $0) } ?? true
    }

    func set(_ value: Bool) {
        guard let uid = currentUid(), value != store.isOn(uid: uid) else {
            return
        }
        store.set(value, uid: uid)
        isOn = value
        onChange?()
    }
}
