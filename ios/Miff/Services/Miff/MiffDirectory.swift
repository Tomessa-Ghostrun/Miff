import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : les noms des Miffs sont des données personnelles. Ils restent en mémoire pour la durée de la
// session (la seule chose gardée sur le disque est l'identifiant de la Miff choisie en dernier, qui n'est pas un secret : il
// ne sert à rien sans le compte) et ne sont jamais journalisés.

/// Un verrou à la file pour les fonctions asynchrones de l'annuaire : deux appels (un rafraîchissement, une perte d'accès
/// entendue par un écran) ne s'exécutent jamais en même temps, même quand l'un attend le réseau.
@MainActor
final class AsyncLock {
    private var locked = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func acquire() async {
        if !locked {
            locked = true
            return
        }
        await withCheckedContinuation { continuation in
            waiters.append(continuation)
        }
    }

    func release() {
        if waiters.isEmpty {
            locked = false
        } else {
            // Le verrou passe directement au suivant : il reste pris.
            waiters.removeFirst().resume()
        }
    }
}

/// Les Miffs de la personne, telles que l'accueil les montre.
struct DirectoryReady: Equatable, CustomStringConvertible {
    let miffs: [MiffSummary]
    /// La Miff montrée par l'accueil : la dernière choisie, sinon la première. nil quand il n'y en a aucune.
    let activeId: String?
    /// Un compte Premium : l'accueil propose son sélecteur, « + Rejoindre » et « + Créer ».
    let premium: Bool
    /// Ce qui est montré vient du cache du téléphone (pas de réponse du serveur) : une mention calme, aucune alarme.
    let fromCache: Bool

    var active: MiffSummary? {
        miffs.first { $0.id == activeId }
    }

    var description: String {
        "DirectoryReady(redacted)"
    }
}

enum DirectoryState: Equatable {
    case loading
    /// Rien n'a pu être lu, et rien n'est connu (hors ligne sur une installation neuve) : les écrans le disent calmement.
    case unavailable
    case ready(DirectoryReady)
}

/// Le message unique d'une perte d'accès (décision QM8). `name` est nil quand le téléphone ne le connaît plus.
struct LostNotice: Equatable, CustomStringConvertible {
    let miffId: String
    let name: String?

    var description: String {
        "LostNotice(redacted)"
    }
}

/// Les Miffs de la personne connectée, en mémoire pour la session : lues par l'accueil, rafraîchies après chaque création /
/// entrée / renommage, et l'UNIQUE endroit où une perte d'accès est traitée (ticket Miff §5.7) : le nettoyage (le lot
/// « Quitter », sinon l'index seul), la purge du cache, et le message unique.
///
/// Un problème de réseau n'est jamais une perte d'accès : le dépôt ne rapporte une perte que quand le serveur lui-même a
/// répondu « non ».
@MainActor
final class MiffDirectory: ObservableObject {
    @Published private(set) var state: DirectoryState = .loading
    /// Les messages à montrer (normalement aucun ou un) ; le premier est celui qui s'affiche.
    @Published private(set) var notices: [LostNotice] = []

    private let repository: MiffRepository
    private let currentUid: () -> String?
    private let activeStore: ActiveMiffStore
    private let cache: CachePurger
    private let lock = AsyncLock()

    /// Monte à chaque fin de session : une réponse attendue à travers elle appartient à la personne précédente.
    private var epoch = 0
    /// Pertes déjà annoncées dans cette session : jamais deux fois le même message (aucune boucle, critère D1).
    private var announced = Set<String>()

    init(
        repository: MiffRepository,
        currentUid: @escaping () -> String?,
        activeStore: ActiveMiffStore,
        cache: CachePurger
    ) {
        self.repository = repository
        self.currentUid = currentUid
        self.activeStore = activeStore
        self.cache = cache
    }

    /// Rien n'a encore été lu dans cette session (ou la dernière lecture a échoué) : à l'ouverture de l'accueil, une seule
    /// lecture par session, pas une à chaque retour sur l'écran.
    func refreshIfNeeded() async {
        await lock.acquire()
        defer {
            lock.release()
        }
        switch state {
        case .ready:
            return
        case .loading, .unavailable:
            await load()
        }
    }

    /// Lit les Miffs auprès du serveur (le cache hors ligne), nettoie celles qui sont perdues, et publie le résultat.
    func refresh() async {
        await lock.acquire()
        defer {
            lock.release()
        }
        await load()
    }

    private func load() async {
        guard let uid = currentUid() else {
            return
        }
        let startedIn = epoch
        let result = await repository.myMiffs(uid: uid)
        guard epoch == startedIn, currentUid() == uid else {
            return
        }
        switch result {
        case .unavailable:
            if case .ready = state {
                return
            }
            state = .unavailable
        case .loaded(let loaded):
            publish(loaded)
            if !loaded.lost.isEmpty {
                for item in loaded.lost {
                    announce(LostNotice(miffId: item.ref.miffId, name: item.name))
                }
                await cleanUpAndPurge(uid: uid, startedIn: startedIn, lost: loaded.lost)
            }
        }
    }

    /// L'écran de la Miff a vu son écoute refusée (ou la Miff disparaître). Le message n'est donné QUE quand le serveur
    /// lui-même dit que l'accès est perdu (le même contrôle qu'à l'ouverture de l'accueil) : un refus entendu quand le serveur
    /// est injoignable, ou qu'il ne confirme pas, n'est pas un retrait et personne n'en est informé. Rend vrai quand la perte
    /// est confirmée (nettoyage fait, message en file), faux sinon (l'écran propose « Réessayer »).
    func onAccessLost(miffId: String, name: String?) async -> Bool {
        await lock.acquire()
        defer {
            lock.release()
        }
        guard let uid = currentUid() else {
            return false
        }
        let startedIn = epoch
        var known: String?
        if case .ready(let ready) = state {
            known = ready.miffs.first { $0.id == miffId }?.name
        }
        // La clé de l'index est relue auprès du serveur : c'est « free » ou l'identifiant de la Miff.
        let result = await repository.myMiffs(uid: uid)
        guard epoch == startedIn, currentUid() == uid, case .loaded(let loaded) = result else {
            return false
        }
        let lost = loaded.lost.filter { $0.ref.miffId == miffId }
        publish(loaded)
        guard let first = lost.first else {
            return false
        }
        announce(LostNotice(miffId: miffId, name: name ?? first.name ?? known))
        await cleanUpAndPurge(uid: uid, startedIn: startedIn, lost: lost)
        return true
    }

    func dismissNotice() {
        notices = Array(notices.dropFirst())
    }

    /// La personne a choisi une autre Miff dans le sélecteur (Premium).
    func select(miffId: String) {
        guard case .ready(let ready) = state, ready.miffs.contains(where: { $0.id == miffId }) else {
            return
        }
        activeStore.choose(miffId)
        state = .ready(
            DirectoryReady(miffs: ready.miffs, activeId: miffId, premium: ready.premium, fromCache: ready.fromCache)
        )
    }

    /// Fin de session : l'annuaire oublie les Miffs, les messages et le choix.
    func stop() async {
        epoch += 1
        announced.removeAll()
        notices = []
        state = .loading
        activeStore.clear()
    }

    private func publish(_ loaded: MyMiffs) {
        let wanted = activeStore.chosen()
        let active = loaded.miffs.first { $0.id == wanted }?.id ?? loaded.miffs.first?.id
        state = .ready(
            DirectoryReady(miffs: loaded.miffs, activeId: active, premium: loaded.premium, fromCache: loaded.fromCache)
        )
    }

    /// Le lot « Quitter », sinon l'index seul ; puis tout le cache s'en va (rien de cette Miff ne doit rester lisible hors
    /// ligne).
    private func cleanUpAndPurge(uid: String, startedIn: Int, lost: [LostMiff]) async {
        cache.markPurgePending()
        for item in lost {
            _ = await repository.cleanUpLost(uid: uid, lost: item)
        }
        guard epoch == startedIn else {
            return
        }
        _ = await cache.purge()
    }

    private func announce(_ notice: LostNotice) {
        guard announced.insert(notice.miffId).inserted else {
            return
        }
        notices.append(notice)
    }
}
