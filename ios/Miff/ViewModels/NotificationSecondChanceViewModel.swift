import Foundation

// CONFIDENTIALITÉ, pour ce fichier : la carte LIT l'état de l'autorisation du téléphone et garde deux drapeaux sur CET appareil. Elle
// ne lit aucune position, n'écrit rien dans Firestore, n'envoie aucun événement et ne journalise rien.

/// La carte « deuxième chance » du Feed (ticket §8.1, étape 3). Un seul objet pour toute l'app : il est donné au Feed, qui lui dit quand
/// sa propre update a été vue publiée.
@MainActor
final class NotificationSecondChanceViewModel: ObservableObject {
    @Published private(set) var isDue = false

    private let permissions: DevicePermissions
    private let store: NotificationSecondChanceStore
    private let currentUid: () -> String?

    init(
        permissions: DevicePermissions,
        store: NotificationSecondChanceStore,
        currentUid: @escaping () -> String?
    ) {
        self.permissions = permissions
        self.store = store
        self.currentUid = currentUid
    }

    /// Relit l'état du téléphone et l'avancement de la carte.
    func refresh() async {
        guard let uid = currentUid() else {
            isDue = false
            return
        }
        let authorization = await permissions.snapshot().notifications
        guard currentUid() == uid else {
            return
        }
        isDue = SecondChanceRule.isDue(authorization: authorization, state: store.state(uid: uid))
    }

    /// Le Feed a vu une update de cette personne publiée (sa photo existe) : la carte peut apparaître, une fois.
    func noteFirstUpdate() {
        guard let uid = currentUid() else {
            return
        }
        var state = store.state(uid: uid)
        guard !state.firstUpdateSeen else {
            return
        }
        state.firstUpdateSeen = true
        store.save(state, uid: uid)
        Task {
            await refresh()
        }
    }

    /// « Activer » : la fenêtre du système, directement (la carte a déjà expliqué pourquoi). Quelle que soit la réponse, la carte est
    /// répondue et ne revient pas.
    func activate() async {
        await permissions.requestNotifications()
        answer()
    }

    /// « Pas maintenant » : la carte part et ne revient pas.
    func dismiss() {
        answer()
    }

    private func answer() {
        isDue = false
        guard let uid = currentUid() else {
            return
        }
        var state = store.state(uid: uid)
        state.answered = true
        store.save(state, uid: uid)
    }
}
