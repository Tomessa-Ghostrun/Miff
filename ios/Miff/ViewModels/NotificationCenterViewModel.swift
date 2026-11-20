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

/// CNC-I : ce que le centre lit EN PLUS de ses lignes locales. Tout est facultatif : sans eux (les tests, les aperçus), le centre est celui d'avant, sans image
/// de personne ni lecture du serveur.
struct NotificationCenterExtras {
    /// Les visages que les lignes dessinent (la photo ou les initiales d'un proche, MA photo pour mes rappels), en mémoire seulement.
    var faces: CenterFaces?
    /// La lecture du journal du serveur, et la suppression des entrées avec les lignes (sans effet dans un build sans `NOTIF_JOURNAL`).
    var journal: NotificationJournalSync?
    /// Cette Miff est-elle une de celles du compte ? Une ligne qui parle d'un proche d'une Miff qui n'est plus la sienne (quittée, supprimée, accès perdu) n'est pas dessinée.
    var isMyMiff: (String) -> Bool = { _ in true }
}

@MainActor
final class NotificationCenterViewModel: ObservableObject {
    @Published private(set) var authorization: NotificationAuthorization?
    @Published var deleteAllAsked = false

    let service: NotificationCenterService
    /// Les prénoms des demandeurs (mémoire seulement) : les mots d'une ligne de demande se font à l'affichage.
    let directory: RequesterDirectory
    private let permissions: DevicePermissions
    private let faces: CenterFaces?
    private let journal: NotificationJournalSync?
    private let isMyMiff: (String) -> Bool

    init(
        service: NotificationCenterService,
        permissions: DevicePermissions,
        directory: RequesterDirectory,
        extras: NotificationCenterExtras = NotificationCenterExtras()
    ) {
        self.service = service
        self.permissions = permissions
        self.directory = directory
        faces = extras.faces
        journal = extras.journal
        isMyMiff = extras.isMyMiff
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
    /// rien n'est marqué comme lu, sauf le groupe « Depuis ta dernière visite », qui reste visible au-dessus du flou.
    func onDisappear() {
        if showsContent {
            service.markAllRead()
        } else if lock != nil {
            // Ce qui était visible au-dessus du flou : « Depuis ta dernière visite », et ce que les proches ont fait (CNC-7).
            (service.history.visitItems + shownLovedOneItems).forEach { service.markRead(id: $0.id) }
        }
    }

    // MARK: - CNC-I : les proches, leurs visages, le journal du serveur

    /// Cette ligne est-elle dessinée ? Une ligne qui parle d'un proche d'une Miff qui n'est plus celle du compte ne l'est pas (ticket §6, point 6).
    func isShown(_ item: NotificationItem) -> Bool {
        !item.type.aboutALovedOne || isMyMiff(item.miffId)
    }

    func shown(_ items: [NotificationItem]) -> [NotificationItem] {
        items.filter { isShown($0) }
    }

    /// Les lignes de proches qui restent visibles quand les notifications du téléphone sont coupées : c'est précisément le cas où le système n'avait
    /// rien montré (critère CNC-7).
    var shownLovedOneItems: [NotificationItem] {
        shown(service.history.lovedOneItems)
    }

    /// L'image ronde et le prénom d'une ligne, tirés de la mémoire MAINTENANT (le prénom et l'image viennent de la même fiche).
    func look(of item: NotificationItem) -> CenterLook {
        let faces = self.faces
        return CenterAvatars.look(
            subject: CenterAvatars.subject(of: item),
            lookup: { faces?.lookup(miffId: $0, uid: $1) ?? .pending },
            me: faces?.mine
        )
    }

    /// Les membres dont les lignes montrées ont besoin du visage (une douzaine au plus sont lus à chaque ouverture).
    func neededFaces() -> [FaceKey] {
        var keys: [FaceKey] = []
        for item in shown(service.history.items) {
            if case .person(let miffId, let uid) = CenterAvatars.subject(of: item) {
                let key = FaceKey(miffId: miffId, uid: uid)
                if !keys.contains(key) {
                    keys.append(key)
                }
            }
        }
        return keys
    }

    /// Lit les visages qui manquent à la mémoire. En arrière-plan : l'écran n'attend jamais.
    func ensureFaces(_ wanted: [FaceKey]) async {
        guard !wanted.isEmpty else {
            return
        }
        await faces?.ensure(wanted)
    }

    /// Lit le journal du serveur depuis le curseur et en amène les lignes (calme : jamais rien de montré, un échec laisse les lignes du téléphone).
    func syncJournal() async {
        await journal?.sync(.centerOpened)
    }

    /// Toucher une ligne : elle est lue, et l'on dit où aller. Jamais une erreur, même si la Miff n'existe plus.
    /// Une ligne de demande mène au Profil, sur la Miff concernée ; une ligne « Depuis ta dernière visite », au Feed, sur la carte du proche.
    func open(_ item: NotificationItem) -> NotificationDestination {
        ids(of: item).forEach { service.markRead(id: $0) }
        if item.type.aboutALovedOne {
            // Un proche : le Feed de sa Miff, sur sa carte quand on sait qui c'est (la clé de la ligne le dit).
            let uid = item.type == .n9 ? CircleNewsKeys.actorOf(item.id) : CircleAlertKeys.actorOf(item.id)
            guard let uid else {
                return .feed
            }
            return .feedCard(miffId: item.miffId, uid: uid)
        }
        if item.type == .c1 {
            guard let uid = LastVisit.uid(of: item) else {
                return .feed
            }
            return .feedCard(miffId: item.miffId, uid: uid)
        }
        return item.type == .n8 ? .placeRequests(miffId: item.miffId) : item.type.destination
    }

    /// Le prénom du proche d'une ligne « Depuis ta dernière visite », tel que l'app le sait maintenant (le centre n'en garde aucun).
    func visitName(of item: NotificationItem) -> String? {
        LastVisit.uid(of: item).flatMap { directory.firstName(miffId: item.miffId, uid: $0) }
    }

    func delete(_ item: NotificationItem) {
        let lines = ids(of: item)
        lines.forEach { service.delete(id: $0) }
        // CNC-15 : l'entrée du journal d'où vient la ligne est supprimée sur le serveur aussi (rien pour une ligne qui n'en a jamais eu).
        journal?.deleteOnServer(centerIds: lines)
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
        let lines = service.history.items.map { $0.id }
        service.deleteAll()
        // CNC-15 : les entrées de ces lignes, puis TOUT le journal du compte (y compris ce que ce téléphone n'a jamais vu), sont supprimés sur le serveur.
        journal?.deleteOnServer(centerIds: lines)
        let journal = self.journal
        Task { await journal?.deleteAllOnServer() }
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
