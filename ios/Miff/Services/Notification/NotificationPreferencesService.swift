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
    /// Le compte dont les choix sont en mémoire (nil : rien n'est encore lu).
    private var loadedUid: String?

    init(store: NotificationPreferencesStore, currentUid: @escaping () -> String?) {
        self.store = store
        self.currentUid = currentUid
    }

    /// Relit les choix de la personne connectée (valeurs par défaut pour un compte qui n'en a jamais fait).
    func reload() {
        guard let uid = currentUid() else {
            preferences = NotificationPreferences()
            loadedUid = nil
            return
        }
        preferences = store.load(uid: uid)
        loadedUid = uid
    }

    /// Lit les choix de la personne connectée s'ils ne l'ont pas encore été (une fois par compte). Indispensable à un réveil en arrière-plan
    /// (géofence, retour du réseau) : aucun écran n'a appelé `reload()`, et les valeurs par défaut (tout actif) ne doivent JAMAIS passer
    /// avant un interrupteur coupé.
    func ensureLoaded() {
        if let uid = currentUid(), loadedUid != uid {
            reload()
        }
    }

    func update(_ transform: (NotificationPreferences) -> NotificationPreferences) {
        guard let uid = currentUid() else {
            return
        }
        ensureLoaded()
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
        loadedUid = nil
        store.forgetAll()
    }
}
