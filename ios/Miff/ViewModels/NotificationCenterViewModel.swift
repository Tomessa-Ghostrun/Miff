import Foundation

// CONFIDENTIALITÉ, pour ce fichier : le centre LIT l'état de l'autorisation du téléphone et les entrées locales. Il ne lit aucune
// position, n'écrit rien dans Firestore, n'envoie aucun événement et ne journalise rien.

/// Le bandeau du haut du centre (ticket §7.2) : visible seulement quand les notifications ne peuvent pas arriver. Jamais culpabilisant.
enum NotificationCenterBanner: Equatable {
    /// Le téléphone les a coupées : le bouton mène à l'aide, puis aux Réglages d'iOS.
    case off
    /// Jamais demandées : le bouton ouvre l'amorce.
    case notAsked
}

@MainActor
final class NotificationCenterViewModel: ObservableObject {
    @Published private(set) var authorization: NotificationAuthorization?
    @Published var primeVisible = false
    @Published var deleteAllAsked = false

    let service: NotificationCenterService
    private let permissions: DevicePermissions

    init(service: NotificationCenterService, permissions: DevicePermissions) {
        self.service = service
        self.permissions = permissions
    }

    var banner: NotificationCenterBanner? {
        switch authorization {
        case .denied:
            return .off
        case .notDetermined:
            return .notAsked
        case .authorized, .none:
            return nil
        }
    }

    func onAppear() async {
        service.load()
        authorization = await permissions.snapshot().notifications
    }

    /// La personne quitte l'écran : ce qu'elle vient de voir est lu (la pastille de la cloche s'éteint).
    func onDisappear() {
        service.markAllRead()
    }

    /// Toucher une ligne : elle est lue, et l'on dit où aller. Jamais une erreur, même si la Miff n'existe plus.
    func open(_ item: NotificationItem) -> NotificationDestination {
        service.markRead(id: item.id)
        return item.type.destination
    }

    func delete(_ item: NotificationItem) {
        service.delete(id: item.id)
    }

    func markAllRead() {
        service.markAllRead()
    }

    func askDeleteAll() {
        deleteAllAsked = true
    }

    func confirmDeleteAll() {
        deleteAllAsked = false
        service.deleteAll()
    }

    func bannerAction(openHelp: () -> Void) {
        switch banner {
        case .off:
            openHelp()
        case .notAsked:
            primeVisible = true
        case .none:
            break
        }
    }

    func primeAllowed() async {
        primeVisible = false
        await permissions.requestNotifications()
        authorization = await permissions.snapshot().notifications
    }

    func primeDeferred() {
        primeVisible = false
    }
}
