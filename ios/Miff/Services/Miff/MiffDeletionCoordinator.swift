import Foundation

// SÉCURITÉ, pour ce fichier : seuls des identifiants (Miff, compte) passent ici ; jamais un nom, jamais un code.

/// Ce que le coordinateur fait savoir au reste de l'app.
struct DeletionHooks {
    /// Avant la première écriture : le créateur ne doit jamais apprendre « tu n'as plus accès » pour la Miff qu'il supprime.
    let onStarted: @MainActor (String) -> Void
    /// Après la disparition de la Miff : purge du cache local, rafraîchissement de la liste des Miffs.
    let onDeleted: @MainActor (String) async -> Void
}

/// Fait tourner les suppressions de Miffs aussi longtemps que l'app vit (critère S3 : l'opération continue quand la personne
/// quitte l'écran), les met en pause sans réseau et les reprend quand il revient (S7), et les reprend au lancement suivant à
/// partir du marqueur local (S4). Le marqueur n'est retiré qu'une fois la Miff disparue. Miroir de `MiffDeletionCoordinator`
/// d'Android.
@MainActor
final class MiffDeletionCoordinator: DeletionController {
    static let maxOtherFailures = 3
    static let defaultRetryPauseNanos: UInt64 = 5_000_000_000
    static let maxPauseNanos: UInt64 = 60_000_000_000

    /// Pause avant un nouvel essai (elle double à chaque échec, jusqu'à une minute). Réglable par les tests.
    var retryPauseNanos = MiffDeletionCoordinator.defaultRetryPauseNanos
    /// Du temps de plus quand l'app passe en arrière-plan en pleine suppression.
    var background: BackgroundExecution = NoBackgroundExecution()

    private let deletion: MiffDeleter
    private let pending: PendingDeletionStore
    private let network: NetworkMonitor
    private let currentUid: () -> String?
    private let hooks: DeletionHooks

    private var states: [String: DeletionState] = [:]
    /// Les suppressions en cours, chacune avec le jeton de SON lancement : un lancement annulé ne retire jamais l'entrée d'un
    /// relancement du même compte.
    private var running: [String: UUID] = [:]
    private var jobs: [Task<Void, Never>] = []
    private let stateBroadcast = Broadcaster<[String: DeletionState]>(replay: true, initial: [:])
    private let noticeBroadcast = Broadcaster<DeletionNotice>(replay: false)

    init(
        deletion: MiffDeleter,
        pending: PendingDeletionStore,
        network: NetworkMonitor,
        currentUid: @escaping () -> String?,
        hooks: DeletionHooks
    ) {
        self.deletion = deletion
        self.pending = pending
        self.network = network
        self.currentUid = currentUid
        self.hooks = hooks
    }

    // MARK: - Ce que l'écran de la Miff demande

    func stateStream() -> AsyncStream<[String: DeletionState]> {
        stateBroadcast.stream()
    }

    /// Les annonces de la suppression pour l'écran d'accueil : reprise au lancement, fin pendant qu'on est ailleurs. Seul ce
    /// qui arrive APRÈS l'ouverture du flux est reçu.
    func notices() -> AsyncStream<DeletionNotice> {
        noticeBroadcast.stream()
    }

    func start(miffId: String) -> DeletionState? {
        // Un identifiant qui n'a pas la forme d'un identifiant de Miff n'est jamais écrit sur le disque.
        guard MiffId.isValid(miffId), let uid = currentUid() else {
            return nil
        }
        // Le marqueur est sur le disque AVANT la première écriture (une app tuée aussitôt reprend quand même).
        pending.add(uid: uid, miffId: miffId)
        _ = launch(uid: uid, miffId: miffId, resumed: false)
        return states[miffId]
    }

    func retry(miffId: String) {
        guard let uid = currentUid() else {
            return
        }
        _ = launch(uid: uid, miffId: miffId, resumed: false)
    }

    /// Au lancement : toute suppression qui n'était pas finie est reprise. Rend les identifiants repris (la personne en est
    /// informée avec calme, par une banderole courte).
    @discardableResult
    func resumePending() -> [String] {
        guard let uid = currentUid() else {
            return []
        }
        let ids = pending.ids(uid: uid).sorted().filter { MiffId.isValid($0) }
        let launched = ids.filter { launch(uid: uid, miffId: $0, resumed: true) }
        if !launched.isEmpty {
            noticeBroadcast.send(.resumed)
        }
        return launched
    }

    /// Fin de session : plus rien ne tourne pour la personne qui s'en va, et la suivante ne retrouve pas ses états. Le marqueur
    /// reste : la suppression reprendra quand le même compte reviendra.
    ///
    /// On ATTEND la fin de chaque suppression annulée (l'appel du serveur en cours se termine en 20 s au plus) avant de rendre la
    /// main : la déconnexion purge ensuite le cache, et plus rien ne doit le repeupler après cela.
    func stop() async {
        let stopping = jobs
        jobs.removeAll()
        running.removeAll()
        for job in stopping {
            job.cancel()
        }
        for job in stopping {
            await job.value
        }
        states.removeAll()
        stateBroadcast.send(states)
    }

    // MARK: - La suppression

    private func launch(uid: String, miffId: String, resumed: Bool) -> Bool {
        guard running[miffId] == nil else {
            return false
        }
        let token = UUID()
        running[miffId] = token
        hooks.onStarted(miffId)
        set(miffId, .running(resumed: resumed))
        let job = Task {
            await self.drive(uid: uid, miffId: miffId, resumed: resumed, token: token)
        }
        jobs.append(job)
        return true
    }

    private func drive(uid: String, miffId: String, resumed: Bool, token: UUID) async {
        defer {
            if running[miffId] == token {
                running[miffId] = nil
            }
        }
        var otherFailures = 0
        var pause = retryPauseNanos
        while !Task.isCancelled, currentUid() == uid {
            set(miffId, .running(resumed: resumed))
            let backgroundToken = background.begin()
            let result = await deletion.run(uid: uid, miffId: miffId)
            background.end(backgroundToken)
            // Annulée (fin de session) : ni état, ni annonce, ni purge pour une session qui n'existe plus.
            if Task.isCancelled {
                return
            }
            switch result {
            case .deleted:
                await finish(uid: uid, miffId: miffId)
                return
            case .notOwner:
                // Pas la Miff de ce compte : rien à faire, et le marqueur ne sert à rien.
                pending.remove(uid: uid, miffId: miffId)
                states[miffId] = nil
                stateBroadcast.send(states)
                return
            case .paused(let failure):
                if failure == .offline {
                    set(miffId, .waitingForNetwork)
                    await waitForNetwork()
                } else {
                    otherFailures += 1
                    if otherFailures >= Self.maxOtherFailures {
                        set(miffId, .stuck)
                        return
                    }
                }
                try? await Task.sleep(nanoseconds: pause)
                pause = min(pause * 2, Self.maxPauseNanos)
            }
        }
        if !Task.isCancelled {
            // Un autre compte s'est connecté entre-temps : rien n'est plus à dire pour celui-ci (le marqueur reste).
            states[miffId] = nil
            stateBroadcast.send(states)
        }
    }

    private func finish(uid: String, miffId: String) async {
        pending.remove(uid: uid, miffId: miffId)
        set(miffId, .done)
        await hooks.onDeleted(miffId)
        noticeBroadcast.send(.completed(miffId: miffId))
    }

    private func waitForNetwork() async {
        for await online in network.onlineChanges() where online {
            return
        }
    }

    private func set(_ miffId: String, _ state: DeletionState) {
        states[miffId] = state
        stateBroadcast.send(states)
    }
}
