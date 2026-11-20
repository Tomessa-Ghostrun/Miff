import Foundation

// Le centre de notifications local, tel que l'app le tient en mémoire (ticket §7 et §20.4). Il est lu depuis le fichier à la première
// demande, tenu à jour à chaque entrée (une notification affichée ou silencieuse), et vidé à la fin de la session. La pastille de la
// cloche est un simple point : `hasUnread`.

@MainActor
final class NotificationCenterService: ObservableObject {
    @Published private(set) var history: NotificationHistory

    private let store: NotificationCenterStoring
    private let currentUid: () -> String?
    private let nowMillis: () -> Int
    private var loadedUid: String?

    init(
        store: NotificationCenterStoring,
        currentUid: @escaping () -> String?,
        nowMillis: @escaping () -> Int = NotificationClock.wallMillis
    ) {
        self.store = store
        self.currentUid = currentUid
        self.nowMillis = nowMillis
        history = NotificationHistory(nowMillis: nowMillis())
    }

    var hasUnread: Bool {
        history.unreadCount > 0
    }

    var unreadCount: Int {
        history.unreadCount
    }

    /// Lit les entrées de la personne connectée (une seule fois par compte) et applique la conservation (30 jours, 100 entrées).
    func load() {
        guard let uid = currentUid() else {
            return
        }
        if loadedUid == uid {
            prune(uid: uid)
            return
        }
        let now = nowMillis()
        history = NotificationHistory(items: store.load(uid: uid), nowMillis: now)
        loadedUid = uid
        store.save(history.items, uid: uid)
    }

    /// Inscrit une notification (affichée ou silencieuse). Une clé déjà inscrite n'est pas inscrite une seconde fois.
    func record(_ item: NotificationItem) {
        guard let uid = currentUid() else {
            return
        }
        load()
        let updated = history.adding(item, nowMillis: nowMillis())
        guard updated != history else {
            return
        }
        history = updated
        store.save(history.items, uid: uid)
    }

    /// CNC-I : plusieurs lignes d'un coup (la lecture du journal du serveur), UNE seule écriture du fichier. Une clé déjà inscrite n'est pas inscrite
    /// une seconde fois : un message vu par le téléphone ET relu depuis le journal ne fait qu'une ligne.
    func record(contentsOf items: [NotificationItem]) {
        guard let uid = currentUid(), !items.isEmpty else {
            return
        }
        load()
        let updated = history.adding(contentsOf: items, nowMillis: nowMillis())
        guard updated != history else {
            return
        }
        history = updated
        store.save(history.items, uid: uid)
    }

    func markRead(id: String) {
        apply { $0.markingRead(id: id, nowMillis: $1) }
    }

    func markAllRead() {
        apply { $0.markingAllRead(nowMillis: $1) }
    }

    func delete(id: String) {
        apply { $0.deleting(id: id, nowMillis: $1) }
    }

    func deleteAll() {
        apply { _, now in NotificationHistory(items: [], nowMillis: now) }
    }

    /// NOTIF-P1I, « Depuis ta dernière visite » : le groupe est REMPLACÉ par ces lignes (3 au plus), ou retiré s'il n'y en a aucune. Non lues,
    /// écrites sans aucune notification du système.
    func replaceVisitLines(_ lines: [NotificationItem]) {
        apply { $0.replacingVisit(with: lines, nowMillis: $1) }
    }

    /// NOTIF-P1I : retire les lignes du groupe dont `remove` dit oui (un membre qui a quitté la Miff, une Miff qui n'est plus là).
    func removeVisitLines(where remove: @escaping (NotificationItem) -> Bool) {
        apply { $0.removingVisit(where: remove, nowMillis: $1) }
    }

    /// CNC-I : retire les lignes de proches dont `remove` dit oui (une Miff quittée, supprimée ou dont l'accès est perdu).
    func removeLovedOneLines(where remove: @escaping (NotificationItem) -> Bool) {
        apply { $0.removingLovedOnes(where: remove, nowMillis: $1) }
    }

    /// Fin de session (déconnexion, suppression du compte, session prise par un autre appareil) : la mémoire et le fichier sont vidés.
    func purge() {
        history = NotificationHistory(nowMillis: nowMillis())
        loadedUid = nil
        store.clear()
    }

    private func apply(_ change: (NotificationHistory, Int) -> NotificationHistory) {
        guard let uid = currentUid() else {
            return
        }
        load()
        let updated = change(history, nowMillis())
        guard updated != history else {
            return
        }
        history = updated
        store.save(history.items, uid: uid)
    }

    private func prune(uid: String) {
        let pruned = history.pruned(nowMillis: nowMillis())
        if pruned != history {
            history = pruned
            store.save(history.items, uid: uid)
        }
    }
}
