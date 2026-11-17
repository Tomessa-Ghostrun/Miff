import Combine
import Foundation

// CONFIDENTIALITÉ et COÛT, pour ce fichier (NOTIF-P1I, critères C1-2, C1-3, C1-7, C1-8) :
// - ce qui est gardé sur le téléphone est l'image de ce que le Feed a dernièrement MONTRÉ des autres membres, par Miff : un identifiant,
//   le mot de la présence, la CATÉGORIE du lieu (jamais son nom ni son adresse), deux heures et un drapeau. Aucune position, aucun nom de
//   lieu, aucun nom de personne, aucune photo. Jamais envoyée, jamais journalisée, dans un fichier privé hors des sauvegardes, effacée à
//   la déconnexion, à la suppression du compte et à la perte d'une Miff ;
// - la comparaison ne coûte RIEN côté serveur : elle se fait entre l'image de la dernière visite et les statuts que le Feed vient de lire
//   pour lui-même (0 lecture, 0 écriture de plus).

/// Ce qui est gardé entre deux visites : la dernière image de chaque Miff, et de quelle Miff était la dernière image.
struct LastVisitMemory: Equatable, CustomStringConvertible {
    var states: [String: VisitState] = [:]
    var lastMiffId: String?

    var description: String {
        "LastVisitMemory(redacted)"
    }
}

/// Où les images sont gardées (un double en mémoire dans les tests). Un seul compte à la fois sur le téléphone.
protocol LastVisitStoring: AnyObject {
    /// Les images de ce compte ; vides si le fichier est absent, illisible ou celui d'un autre compte.
    func load(uid: String) -> LastVisitMemory
    func save(_ memory: LastVisitMemory, uid: String)
    func clear()
}

/// Ce que dit le Feed à « Depuis ta dernière visite » : mon identifiant, l'image fraîche de la Miff affichée, et les prénoms de ses membres
/// par identifiant (mémoire seulement, pour les phrases ; le centre n'en garde aucun).
typealias FreshPictureHandler = @MainActor (String, VisitState, [String: String]) -> Void

/// « Depuis ta dernière visite » : à l'OUVERTURE de l'app (un démarrage à froid, ou un retour au premier plan après 30 minutes ou plus), la
/// PROCHAINE image fraîche du Feed est comparée à celle que le Feed montrait la dernière fois, et le groupe du centre est remplacé par ce qui
/// a changé (3 lignes au plus, voir `LastVisit`). Ensuite, et à chaque image fraîche, l'image gardée suit l'écran : ce qui a été vu n'est pas
/// redit.
@MainActor
final class LastVisitTracker {
    private let store: LastVisitStoring
    private let center: NotificationCenterService
    private let currentUid: () -> String?
    private let nowMillis: () -> Int
    private var memory = LastVisitMemory()
    private var memoryUid: String?
    /// Un démarrage à froid est une ouverture : la première image fraîche du processus est comparée.
    private var opened = true
    private var backgroundedAtMillis: Int?
    private var subscription: AnyCancellable?

    init(
        store: LastVisitStoring,
        center: NotificationCenterService,
        currentUid: @escaping () -> String?,
        nowMillis: @escaping () -> Int = NotificationClock.wallMillis
    ) {
        self.store = store
        self.center = center
        self.currentUid = currentUid
        self.nowMillis = nowMillis
    }

    /// L'app est passée en arrière-plan.
    func onBackground() {
        backgroundedAtMillis = nowMillis()
    }

    /// L'app revient devant : une comparaison est due si elle est restée loin 30 minutes ou plus (un aller-retour rapide n'est pas une
    /// ouverture).
    func onForeground() {
        let away = backgroundedAtMillis.map { nowMillis() - $0 }
        backgroundedAtMillis = nil
        if let away, LastVisit.isReopening(absenceMillis: away) {
            opened = true
        }
    }

    /// Le Feed montre une image FRAÎCHE de la Miff de `state` (confirmée par le serveur, en ligne, mon partage actif). Si l'app vient d'être
    /// ouverte, elle est comparée d'abord, puis gardée. La première visite sur ce téléphone, une Miff qui n'est pas celle de la dernière
    /// visite (changement de Miff active), une reconnexion ou un accès perdu ne donnent aucune ligne.
    func onFreshPicture(myUid: String, state: VisitState) {
        guard currentUid() == myUid else {
            return
        }
        let before = current(uid: myUid)
        if opened {
            opened = false
            let seen = before.lastMiffId == state.miffId ? before.states[state.miffId] : nil
            let lines = LastVisit.compare(seen: seen, current: state, nowMillis: nowMillis())
            center.replaceVisitLines(LastVisit.items(miffId: state.miffId, lines: lines))
        }
        // Un membre qui n'est plus dans la Miff ne garde aucune ligne.
        let present = Set(state.members.map { $0.uid })
        let miffId = state.miffId
        center.removeVisitLines { item in
            guard item.miffId == miffId, let uid = LastVisit.uid(of: item) else {
                return false
            }
            return !present.contains(uid)
        }
        if before.states[miffId] != state || before.lastMiffId != miffId {
            var after = before
            after.states[miffId] = state
            after.lastMiffId = miffId
            keep(after, uid: myUid)
        }
    }

    /// La liste des Miffs de la personne est connue (lue auprès du serveur) : les images et les lignes de toute autre Miff (quittée,
    /// supprimée, accès perdu) sont oubliées.
    func onMiffsKnown(_ miffIds: Set<String>) {
        guard let uid = currentUid() else {
            return
        }
        var after = current(uid: uid)
        let gone = after.states.keys.filter { !miffIds.contains($0) }
        let lastGone = after.lastMiffId.map { !miffIds.contains($0) } ?? false
        if !gone.isEmpty || lastGone {
            for id in gone {
                after.states[id] = nil
            }
            if lastGone {
                after.lastMiffId = nil
            }
            keep(after, uid: uid)
        }
        center.removeVisitLines { !miffIds.contains($0.miffId) }
    }

    /// Suit l'annuaire des Miffs : seule une liste confirmée par le serveur (jamais une copie du téléphone) décide de ce qui est oublié.
    func watch(_ directory: MiffDirectory) {
        subscription = directory.$state.sink { [weak self] state in
            if case .ready(let ready) = state, !ready.fromCache {
                self?.onMiffsKnown(Set(ready.miffs.map { $0.id }))
            }
        }
    }

    /// La déconnexion, la suppression du compte, la perte de session : plus rien ne reste, et la session suivante commence comme une
    /// première visite (critère C1-8).
    func purge() {
        memory = LastVisitMemory()
        memoryUid = nil
        store.clear()
        opened = true
        backgroundedAtMillis = nil
    }

    private func current(uid: String) -> LastVisitMemory {
        if memoryUid != uid {
            memory = store.load(uid: uid)
            memoryUid = uid
        }
        return memory
    }

    private func keep(_ after: LastVisitMemory, uid: String) {
        store.save(after, uid: uid)
        memory = after
        memoryUid = uid
    }
}
