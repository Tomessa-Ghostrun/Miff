import Foundation

// CONFIDENTIALITÉ et COÛT, pour ce fichier (ticket LIEU-REQ-C) : les demandes (noms, adresses) et les personnes qui ont demandé (prénom,
// photo) restent en mémoire, pour ce view model seulement ; rien n'est journalisé ni envoyé comme événement. Le créateur est prévenu par UN
// écouteur sur UNE Miff (10 documents au plus), branché seulement pendant que l'app est devant (`watch`, annulé par l'hôte), et lit la fiche de
// chaque personne qui a demandé UNE fois par session.

/// Le créateur d'une Miff reçoit les demandes d'ajout d'un lieu, les accepte ou les refuse.
extension ProfileRequestsViewModel {
    /// Après un lancement à froid ou un changement de Miff active : la Miff que le Feed montre, et si la personne l'a créée (l'annuaire le
    /// sait déjà : aucune lecture). Décide `tracked` tant qu'aucune carte du carrousel n'a été montrée.
    func onActiveMiff(miffId: String?, creator: Bool) {
        activeCreatorMiff = creator ? miffId : nil
        if let miffId {
            if creator {
                creatorMiffs.insert(miffId)
            } else {
                creatorMiffs.remove(miffId)
            }
        }
        updateTracked()
        publish()
    }

    // MARK: - L'écouteur

    /// Écoute les demandes de `miffId` jusqu'à ce que la tâche soit annulée (l'hôte l'annule quand l'app quitte le premier plan). Chaque
    /// réponse remplace ce qui était connu ; ce qu'elle contient va à la liste et au point de l'onglet Profil.
    func watch(miffId: String) async {
        creatorMiffs.insert(miffId)
        drawn.insert(miffId)
        for await observation in repository.observe(miffId: miffId) {
            switch observation {
            case .data(let requests, _):
                onKnown(miffId: miffId, requests: requests, live: true)
            case .accessLost:
                // Retiré, ou la Miff n'existe plus : rien de cette Miff ne reste (la perte elle-même est dite UNE fois, par-dessus tout, par
                // l'hôte).
                book.forget(miffId: miffId)
            case .failed:
                break
            }
            publish()
        }
        book.liveStopped(miffId: miffId)
        publish()
    }

    func onKnown(miffId: String, requests: [PlaceRequest], live: Bool) {
        if live {
            book.live(miffId: miffId, requests: requests)
        } else {
            book.readingSucceeded(miffId: miffId, requests: requests)
        }
        resolveRequesters(miffId: miffId, requests: requests)
        purgeLater(miffId: miffId)
    }

    // MARK: - Qui a demandé

    /// Lit la fiche de chaque personne qui a demandé, UNE fois (une lecture ratée est retentée après une minute au plus tôt, jamais en boucle).
    private func resolveRequesters(miffId: String, requests: [PlaceRequest]) {
        let repository = self.repository
        var done = Set<String>()
        for request in requests {
            let uid = request.requestedBy
            guard done.insert(uid).inserted else {
                continue
            }
            let key = RequesterKey.key(miffId: miffId, uid: uid)
            let known = state.requesters[key]
            let retryDue = known == .unknown && nowMillis() - (unknownSince[key] ?? 0) >= Self.unknownRetryMillis
            if (known != nil && !retryDue) || resolving.contains(key) {
                continue
            }
            resolving.insert(key)
            Task { [weak self] in
                let answer = await repository.readRequester(miffId: miffId, uid: uid)
                self?.requesterResolved(key: key, miffId: miffId, answer: answer)
            }
        }
    }

    private func requesterResolved(key: String, miffId: String, answer: RequesterState) {
        resolving.remove(key)
        if let known = knownMiffIds, !known.contains(miffId) {
            return
        }
        if answer == .unknown {
            unknownSince[key] = nowMillis()
        }
        state.requesters[key] = answer
        if answer == .notMember {
            purgeLater(miffId: miffId)
        }
        publish()
    }

    static let unknownRetryMillis = 60_000

    // MARK: - Nettoyer

    /// Les demandes que personne ne traitera (plus de 30 jours, ou faites par quelqu'un qui a quitté la Miff) sont supprimées par le créateur,
    /// 4 par lot, une fois chacune. Supprimer ne se défait pas : une demande n'est supprimée pour son âge que si elle a plus de 30 jours de
    /// PLUS que la plus récente, dont la date est celle du serveur (un téléphone dont l'horloge avance masque tout, ce qui se défait, mais ne
    /// supprime jamais une demande que le serveur a datée récemment).
    private func purgeLater(miffId: String) {
        guard creatorMiffs.contains(miffId), let known = book.known(miffId: miffId) else {
            return
        }
        let now = nowMillis()
        let newest = known.compactMap { $0.createdAtMillis }.max()
        let targets = known.filter { request in
            guard !purging.contains(request.id) else {
                return false
            }
            if state.requester(of: request) == .notMember {
                return true
            }
            guard request.isExpired(nowMillis: now), let at = request.createdAtMillis, let newest else {
                return false
            }
            return newest - at > PlaceRequestLimits.expiryMillis
        }
        guard !targets.isEmpty else {
            return
        }
        let ids = targets.map { $0.id }
        purging.formUnion(ids)
        let repository = self.repository
        let remaining = max(known.count - ids.count, 0)
        Task { [weak self] in
            await repository.purge(miffId: miffId, requestIds: ids, remaining: remaining)
            self?.purgeEnded(miffId: miffId, ids: ids)
        }
    }

    private func purgeEnded(miffId: String, ids: [String]) {
        // Que le lot soit passé ou non, elles restent masquées ; un échec est retenté avec la prochaine réponse de l'écouteur.
        purging.subtract(ids)
        for id in ids {
            book.remove(miffId: miffId, requestId: id)
        }
        publish()
    }

    // MARK: - La liste est vue

    /// La liste de `miffId` a été dessinée avec ses demandes : le point de l'onglet Profil s'éteint, et la ligne du centre le ferait aussi.
    func onListSeen(miffId: String) {
        guard case .ready(let requests)? = state.views[miffId], !requests.isEmpty else {
            return
        }
        for request in requests {
            seen.insert(seenKey(miffId, request))
        }
        publish()
    }

    // MARK: - Répondre

    /// « Accepter » (critères C3, C5 à C8) : la formule gratuite est appliquée AVANT d'écrire ; la demande reste en attente chaque fois que
    /// l'offre s'ouvre.
    func onAccept(_ request: PlaceRequest) {
        guard let uid = myUid(), startAnswer(request) else {
            return
        }
        let repository = self.repository
        let count = knownCount(request.miffId)
        Task { [weak self] in
            let result = await repository.accept(uid: uid, request: request, knownCount: count)
            self?.acceptEnded(request, result: result)
        }
    }

    private func acceptEnded(_ request: PlaceRequest, result: RequestAcceptResult) {
        switch result {
        case .done:
            book.remove(miffId: request.miffId, requestId: request.id)
            notice = .accepted(placeName: request.name)
            onAccepted(request.miffId)
        case .needsPremium(let reason):
            paywall = reason
        case .subscriptionUnknown:
            notice = .subscriptionUnknown
        case .gone:
            book.remove(miffId: request.miffId, requestId: request.id)
            notice = .gone
        case .failed(let failure):
            notice = .failed(offline: failure == .offline)
        }
        endAnswer()
    }

    /// « Refuser » (critère C4) : toujours possible, quelle que soit la formule ; rien n'est écrit pour la personne qui a demandé.
    func onDecline(_ request: PlaceRequest) {
        guard startAnswer(request) else {
            return
        }
        let repository = self.repository
        let count = knownCount(request.miffId)
        Task { [weak self] in
            let result = await repository.decline(request, knownCount: count)
            self?.declineEnded(request, result: result)
        }
    }

    private func declineEnded(_ request: PlaceRequest, result: RequestDeclineResult) {
        switch result {
        case .done:
            book.remove(miffId: request.miffId, requestId: request.id)
            notice = .declined
        case .failed(let failure):
            notice = .failed(offline: failure == .offline)
        }
        endAnswer()
    }

    /// Le nombre de demandes que le créateur voit maintenant : le compteur est écrit à ce nombre réel moins celle qu'il traite.
    private func knownCount(_ miffId: String) -> Int {
        book.known(miffId: miffId)?.count ?? 1
    }

    /// L'hôte a ouvert l'offre : le drapeau à usage unique redescend.
    func consumePaywall() {
        if paywall != nil {
            paywall = nil
        }
    }
}
