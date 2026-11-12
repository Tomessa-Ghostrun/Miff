import Foundation

/// Les choix de Réglages > Notifications tels que l'app les tient en mémoire. Un changement est gardé aussitôt (sans bouton
/// « Enregistrer ») et annoncé à `onChange`, d'où le planificateur (phase B) reconcilie ses rappels dans la seconde.
@MainActor
final class NotificationPreferencesService: ObservableObject {
    @Published private(set) var preferences = NotificationPreferences()

    /// Appelé après chaque changement, avec les nouveaux choix.
    var onChange: ((NotificationPreferences) -> Void)?

    private let store: NotificationPreferencesStore
    private let currentUid: () -> String?

    init(store: NotificationPreferencesStore, currentUid: @escaping () -> String?) {
        self.store = store
        self.currentUid = currentUid
    }

    /// Relit les choix de la personne connectée (valeurs par défaut pour un compte qui n'en a jamais fait).
    func reload() {
        guard let uid = currentUid() else {
            preferences = NotificationPreferences()
            return
        }
        preferences = store.load(uid: uid)
    }

    func update(_ transform: (NotificationPreferences) -> NotificationPreferences) {
        guard let uid = currentUid() else {
            return
        }
        let updated = transform(preferences)
        guard updated != preferences else {
            return
        }
        preferences = updated
        store.save(updated, uid: uid)
        onChange?(updated)
    }

    /// Fin de session : les choix de tous les comptes de ce téléphone sont oubliés (le compte suivant repart des valeurs par défaut).
    func forget() {
        preferences = NotificationPreferences()
        store.forgetAll()
    }
}
