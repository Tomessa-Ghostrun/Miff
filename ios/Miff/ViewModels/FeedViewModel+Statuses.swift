import Foundation

// La lecture des statuts de la Miff affichée. Elle n'a lieu que si je partage ET que mon accord n'est pas attendu (sinon les règles
// refusent et rien n'est voulu : zéro lecture) ; l'écoute n'est attachée qu'une fois la Miff connue (pour que mon propre partage le
// soit), et rattachée quand elle s'est terminée sur un refus (mon propre statut n'est pas encore publié) : quelques essais espacés,
// puis elle attend le retour de l'écran, jamais une boucle.

/// Les minuteries d'UNE lecture des statuts : l'attente de la première réponse, et celle de la confirmation du serveur.
private final class StatusTimers {
    var firstAnswer: Task<Void, Never>?
    var cacheGrace: Task<Void, Never>?
    var attempt = 0

    func cancelAll() {
        firstAnswer?.cancel()
        cacheGrace?.cancel()
    }
}

extension FeedViewModel {
    /// Décide s'il faut lire les statuts, et (re)lance la lecture quand la décision change.
    func reconsiderStatuses() {
        guard let miffId = observedMiffId, case .data(let id, let snapshot)? = piece, id == miffId else {
            return
        }
        let mySharing = snapshot.members.first { $0.uid == dependencies.currentUid() }?.sharingEnabled ?? true
        let shouldRead = mySharing && !waiting.contains(miffId)
        let decision = StatusDecision(shouldRead: shouldRead, restart: statusRestarts)
        guard decision != lastStatusDecision else {
            return
        }
        lastStatusDecision = decision
        statusTask?.cancel()
        statusTask = nil
        guard shouldRead else {
            statusView = .off
            return
        }
        // Ce qui est déjà montré reste (un rattachement ne ramène ni le squelette, ni ne cache « Partage de ton statut en cours… ») :
        // seul un écran qui ne montrait rien des statuts attend.
        if statusView == .off {
            statusView = .waiting
        }
        let mine = generation
        statusTask = Task {
            await self.readStatuses(miffId, generation: mine)
        }
    }

    private func isCurrent(_ mine: Int) -> Bool {
        generation == mine && !Task.isCancelled
    }

    private func readStatuses(_ miffId: String, generation mine: Int) async {
        if !statusView.isLoaded {
            cacheGraceOver = false
        }
        let timers = StatusTimers()
        let sleep = dependencies.sleep
        let firstAnswerMillis = dependencies.timings.firstStatusAnswerMillis
        timers.firstAnswer = Task {
            await sleep(firstAnswerMillis)
            if !Task.isCancelled && self.statusView == .waiting {
                self.statusView = .unknown
            }
        }
        defer {
            timers.cancelAll()
        }
        let delays = dependencies.timings.statusRetryDelaysMillis
        while isCurrent(mine) {
            await listen(miffId, generation: mine, timers: timers)
            // L'écoute s'est terminée (un refus : les règles attendent mon statut, ou mon partage s'est arrêté ailleurs).
            guard isCurrent(mine) else {
                return
            }
            timers.firstAnswer?.cancel()
            statusView = .pending
            guard timers.attempt < delays.count else {
                return
            }
            let pause = delays[timers.attempt]
            timers.attempt += 1
            await sleep(pause)
        }
    }

    private func listen(_ miffId: String, generation mine: Int, timers: StatusTimers) async {
        for await feed in dependencies.services.statuses.observe(miffId: miffId) {
            guard isCurrent(mine) else {
                return
            }
            if case .data(let byUid, let fromCache) = feed {
                timers.firstAnswer?.cancel()
                noteAnswer(fromCache: fromCache, timers: timers)
                statusView = .loaded(byUid: byUid, fromCache: fromCache)
                if !fromCache {
                    dependencies.onStatusesLoaded(statusesEvent(miffId, byUid: byUid))
                }
            }
        }
    }

    /// Ce que les notifications reçoivent d'une réponse du serveur : les statuts et les membres de la Miff (seulement s'ils sont connus).
    private func statusesEvent(_ miffId: String, byUid: [String: MemberStatus]) -> FeedStatusesEvent {
        var members: [FeedStatusesEvent.Member] = []
        if case .data(let id, let snapshot)? = piece, id == miffId {
            members = snapshot.members.map { FeedStatusesEvent.Member(uid: $0.uid, sharing: $0.sharingEnabled) }
        }
        return FeedStatusesEvent(miffId: miffId, statuses: byUid, myUid: dependencies.currentUid(), members: members)
    }

    private func noteAnswer(fromCache: Bool, timers: StatusTimers) {
        guard !fromCache else {
            // Une copie du cache n'est PAS une réponse du serveur : elle ne rend pas les essais (une écoute qui se termine aussitôt
            // après sa réponse en cache ne doit pas être rattachée toutes les 10 s, pour toujours).
            if timers.cacheGrace == nil {
                let sleep = dependencies.sleep
                let graceMillis = dependencies.timings.cacheGraceMillis
                timers.cacheGrace = Task {
                    await sleep(graceMillis)
                    if !Task.isCancelled {
                        self.cacheGraceOver = true
                    }
                }
            }
            return
        }
        timers.attempt = 0
        timers.cacheGrace?.cancel()
        timers.cacheGrace = nil
        // Le serveur a parlé : plus rien n'est périmé.
        staleAfterRefresh = false
    }
}
