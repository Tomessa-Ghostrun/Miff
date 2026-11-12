import Foundation

// CONFIDENTIALITÉ, pour ce fichier : le centre LIT l'état de l'autorisation du téléphone et les entrées locales. Il ne lit aucune
// position, n'écrit rien dans Firestore, n'envoie aucun événement et ne journalise rien.

/// Pourquoi le centre est verrouillé (ticket §7.2 ; comportement validé par le PM sur Android, repris tel quel) : les notifications ne peuvent pas arriver.
/// Le centre s'ouvre alors FLOU, avec une seule carte calme et un seul bouton. Jamais culpabilisant.
enum NotificationCenterLock: Equatable {
    /// Le téléphone les a coupées : le bouton mène à l'explication calme, qui ouvre les Réglages d'iOS.
    case off
    /// Jamais demandées : le bouton déclenche la fenêtre native d'iOS (jamais de fenêtre « maison » avant).
    case notAsked
}

@MainActor
final class NotificationCenterViewModel: ObservableObject {
    @Published private(set) var authorization: NotificationAuthorization?
    @Published var deleteAllAsked = false

    let service: NotificationCenterService
    /// Les prénoms des demandeurs (mémoire seulement) : les mots d'une ligne de demande se font à l'affichage.
    let directory: RequesterDirectory
    private let permissions: DevicePermissions

    init(service: NotificationCenterService, permissions: DevicePermissions, directory: RequesterDirectory) {
        self.service = service
        self.permissions = permissions
        self.directory = directory
    }

    /// Verrouillé (flou + une carte + un bouton) quand le téléphone refuse les notifications ou qu'elles n'ont jamais été demandées.
    var lock: NotificationCenterLock? {
        switch authorization {
        case .denied:
            return .off
        case .notDetermined:
            return .notAsked
        case .authorized, .none:
            return nil
        }
    }

    /// Le centre montre ses vraies lignes (ou son état vide) : seulement quand les notifications sont autorisées. Tant que l'état n'est pas
    /// lu, on ne montre RIEN (jamais une vraie ligne qui disparaîtrait ensuite derrière le flou).
    var showsContent: Bool {
        authorization == .authorized
    }

    func onAppear() async {
        service.load()
        authorization = await permissions.snapshot().notifications
    }

    /// La personne quitte l'écran : ce qu'elle vient de voir est lu (la pastille de la cloche s'éteint). Derrière le flou, rien n'a été vu :
    /// rien n'est marqué comme lu.
    func onDisappear() {
        if showsContent {
            service.markAllRead()
        }
    }

    /// Toucher une ligne : elle est lue, et l'on dit où aller. Jamais une erreur, même si la Miff n'existe plus.
    /// Une ligne de demande mène au Profil, sur la Miff concernée.
    func open(_ item: NotificationItem) -> NotificationDestination {
        ids(of: item).forEach { service.markRead(id: $0) }
        return item.type == .n8 ? .placeRequests(miffId: item.miffId) : item.type.destination
    }

    func delete(_ item: NotificationItem) {
        ids(of: item).forEach { service.delete(id: $0) }
    }

    /// Les lignes du centre qu'une ligne dessinée représente : les demandes NON LUES d'une même personne dans une même Miff ne font
    /// qu'une ligne (« Léa a proposé 3 lieux ») ; toute autre ligne n'est qu'elle-même.
    func ids(of item: NotificationItem) -> [String] {
        guard item.type == .n8 else {
            return [item.id]
        }
        return PlaceRequestLines.entries(of: service.history.items).first { $0.ids.first == item.id }?.ids ?? [item.id]
    }

    /// Le prénom de la personne derrière une ligne de demande, tel que l'app le sait maintenant.
    func requesterName(of item: NotificationItem) -> String? {
        PlaceRequestLines.requester(of: item).flatMap { directory.firstName(miffId: item.miffId, uid: $0) }
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

    /// « Activer les notifications » : jamais demandées = la fenêtre NATIVE d'iOS tout de suite (la personne vient de dire qu'elle les veut) ;
    /// refusées = iOS n'affichera plus sa fenêtre, donc l'explication calme (`openHelp`), qui mène aux Réglages d'iOS.
    func enable(openHelp: () -> Void) async {
        switch authorization {
        case .notDetermined:
            await permissions.requestNotifications()
            authorization = await permissions.snapshot().notifications
        case .denied:
            openHelp()
        case .authorized, .none:
            break
        }
    }
}
