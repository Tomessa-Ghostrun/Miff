import Foundation

// « Actualiser » (tirer pour actualiser, décision du PM du 2026-11-06) et « Localiser » : les deux seules lectures que fait le Feed à
// la demande de la personne. Aucune ne CRÉE ni n'ÉCRIT rien : le Feed ne lit ni n'écrit lui-même aucune position. Depuis le 2026-11-17
// (demande du PM), « Actualiser » demande en plus au SUIVI des lieux de revérifier MA présence (`onPullOwnPresence`) ; c'est le moteur
// du suivi, avec toutes ses gardes, qui mesure et écrit si besoin (voir `docs/tickets/feed-refresh-position.md`).

extension FeedViewModel {
    // MARK: - Actualiser

    /// UNE relecture forcée auprès du SERVEUR de ce qui alimente les cartes, les couleurs recalculées, les essais des statuts remis
    /// à zéro, et la sortie du mode « copie du cache » si le serveur répond. Bornée pour le plan Spark : au plus une lecture forcée
    /// toutes les 15 s (un geste plus rapproché se termine aussitôt, sans lire), jamais une boucle, aucune écriture ; hors connexion
    /// elle se termine tout de suite (la mention « Dernières informations de… » est déjà là) ; l'indicateur se termine au plus tard
    /// après 8 s, et si le serveur n'a pas répondu le Feed le dit avec sa mention honnête, jamais avec une phrase alarmante. Coût
    /// d'une actualisation : 9 + 2 x membres + lieux lectures (le suivi ajoute au plus une mesure ponctuelle de position par 30 s, et ses
    /// lectures et écritures habituelles seulement quand elles sont dues).
    func pullToRefresh() async {
        guard !refreshing, case .data(let miffId, _)? = piece else {
            return
        }
        let now = dependencies.clock()
        if let last = lastRefreshAtMillis, now - last >= 0, now - last < dependencies.timings.minRefreshIntervalMillis {
            // Trop tôt pour relire : rien n'est lu, mais le geste n'a pas l'air de ne rien faire : l'indicateur reste visible un instant.
            refreshing = true
            defer {
                refreshing = false
            }
            await holdIndicator(since: now)
            return
        }
        guard dependencies.network.isOnline() else {
            return
        }
        lastRefreshAtMillis = now
        refreshing = true
        defer {
            refreshing = false
        }
        // MA présence aussi : le suivi la revérifie en parallèle (mesure ponctuelle, limitée à une fois par 30 s, rien d'envoyé si rien
        // ne change). On ne l'attend PAS : l'indicateur ne dépend que de la lecture des proches ci-dessous, et mon propre statut revient
        // par l'écoute des statuts, comme n'importe quel autre. Hors connexion on n'arrive jamais ici (garde ci-dessus).
        dependencies.onPullOwnPresence()
        let readStatuses = statusView != .off
        let refresher = dependencies.services.refresher
        let answer: ServerRead
        do {
            answer = try await timedOperation(seconds: dependencies.timings.refreshTimeoutSeconds) {
                await refresher.refresh(miffId: miffId, readStatuses: readStatuses)
            }
        } catch {
            answer = .unreachable
        }
        applyRefresh(miffId: miffId, answer: answer, askedForStatuses: readStatuses)
        await holdIndicator(since: now)
    }

    /// L'indicateur d'« Actualiser » reste affiché AU MOINS `minRefreshDisplayMillis` depuis le début du geste (retour du PM, TestFlight 38 : il
    /// se terminait presque aussitôt et ne se voyait pas). Ce que la lecture a déjà rendu est déjà montré ; on ne fait qu'attendre le reste du
    /// délai, sans attendre la position (le suivi tourne à part). Un délai écoulé n'ajoute rien.
    private func holdIndicator(since start: Int) async {
        let elapsed = max(dependencies.clock() - start, 0)
        let remaining = dependencies.timings.minRefreshDisplayMillis - elapsed
        if remaining > 0 {
            await dependencies.sleep(remaining)
        }
    }

    private func applyRefresh(miffId: String, answer: ServerRead, askedForStatuses: Bool) {
        // La personne a pu choisir une autre Miff (Premium) pendant la lecture : ce qui a été dit de A n'est jamais mis sur B.
        guard case .data(let current, _)? = piece, current == miffId else {
            return
        }
        guard case .fresh(let fresh) = answer else {
            staleAfterRefresh = true
            return
        }
        staleAfterRefresh = false
        guard askedForStatuses else {
            return
        }
        let listening = statusView.isLoaded
        guard let fresh else {
            // Les règles attendent encore mon propre statut : « Partage de ton statut en cours… », et les essais repartent de zéro.
            statusView = .pending
            statusRestarts += 1
            reconsiderStatuses()
            return
        }
        // Ce que le serveur vient de dire, confirmé : le mode « copie du cache » est quitté, les couleurs recalculées dessus.
        statusView = .loaded(byUid: fresh, fromCache: false)
        if !listening {
            cacheGraceOver = true
            statusRestarts += 1
            reconsiderStatuses()
        }
    }

    // MARK: - Localiser

    /// Un toucher sur « Localiser {Prénom} » : UNE lecture auprès du serveur, puis la feuille (critère L1). Un second toucher pendant
    /// la lecture ne fait rien.
    func onLocate(uid: String) {
        if case .loading = locate {
            return
        }
        guard case .ready(let content) = uiState,
              let card = content.cards.first(where: { $0.member.uid == uid && $0.canLocate }) else {
            return
        }
        let firstName = card.member.callName
        let miffId = content.miffId
        locate = .loading(uid: uid)
        let positions = dependencies.services.positions
        let network = dependencies.network
        locateTask = Task {
            let online = network.isOnline()
            let raw: PositionLookup = online ? await positions.read(miffId: miffId, uid: uid) : .offline
            guard !Task.isCancelled else {
                return
            }
            // « Pas de connexion » seulement pour une vraie absence de réseau ; tout le reste d'inattendu est la sixième réponse.
            let lookup = Locate.reconcile(raw, networkOnline: online)
            let result = Locate.interpret(lookup, nowMillis: self.dependencies.clock(), zone: self.dependencies.zone())
            self.locate = .shown(uid: uid, firstName: firstName, result: result)
        }
    }

    /// La feuille a été fermée (bouton, retour, glissement, à côté) : l'écran ne garde rien de la position.
    func onLocateDismissed() {
        locateTask?.cancel()
        locateTask = nil
        locate = .idle
    }
}
