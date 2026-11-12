import Foundation

// SÉCURITÉ, pour ce fichier : mêmes règles que `PlaceRequestRepository.swift`. Le prénom et la photo de la personne qui a demandé ne sont
// lus que pour le créateur, une fois par personne et par session (l'appelant), et ne sont jamais journalisés.

/// Le créateur d'une Miff reçoit les demandes d'ajout d'un lieu, les accepte ou les refuse (ticket LIEU-REQ-C ; miroir d'Android).
extension FirestorePlaceRequestRepository {
    // MARK: - Écouter et lire

    func observe(miffId: String) -> AsyncStream<RequestsObservation> {
        let source = store.observeAll(miffId: miffId)
        return AsyncStream { continuation in
            let task = Task {
                do {
                    for try await snapshot in source {
                        continuation.yield(.data(requests: snapshot.requests, fromCache: snapshot.fromCache))
                    }
                } catch {
                    // Seul un refus des règles dit « retiré » ; une coupure réseau ne termine jamais un écouteur ; tout autre cas :
                    // « quelque chose s'est mal passé », ce qui est montré reste tel quel.
                    continuation.yield(isAccessLostError(error) ? .accessLost : .failed)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }

    func readAll(miffId: String) async -> MineRead {
        let store = self.store
        switch await guarded({ try await store.readAll(miffId: miffId) }) {
        case .value(let found):
            return .loaded(found)
        case .failed(let failure, _):
            return .failed(failure)
        }
    }

    func readRequester(miffId: String, uid: String) async -> RequesterState {
        let store = self.store
        switch await guarded({ try await store.readRequester(miffId: miffId, uid: uid) }) {
        case .value(.found(let card)):
            return .known(card)
        case .value(.notMember):
            return .notMember
        case .failed:
            return .unknown
        }
    }

    // MARK: - Répondre

    func accept(uid: String, request: PlaceRequest, knownCount: Int) async -> RequestAcceptResult {
        // La formule, les Miffs et les lieux de la personne auprès du SERVEUR, comme le formulaire d'une création (une copie périmée
        // ouvrirait la mauvaise porte). Une formule inconnue n'est PAS « gratuit » : aucune offre sur un doute.
        let context: PlaceContext
        switch await placeRepository.prepare(uid: uid) {
        case .loaded(let loaded):
            context = loaded
        case .failed:
            return .subscriptionUnknown
        }
        guard context.ownedMiffs.contains(where: { $0.id == request.miffId }) else {
            return .gone
        }
        // Encore en attente ? (traitée sur un autre téléphone, reprise par son auteur) : le lot ne lit pas la demande, elle est lue ici.
        let store = self.store
        switch await guarded({ try await store.readOne(miffId: request.miffId, requestId: request.id) }) {
        case .value(let found):
            if found == nil {
                return .gone
            }
        case .failed(let failure, let lostAccess):
            return lostAccess ? .gone : .failed(failure)
        }
        // Les MÊMES limites qu'une création (ticket 5.4) : une zone de danger est Miff+, la formule gratuite a 3 lieux dans ses
        // emplacements fixes p1 à p3.
        let slot = PlaceQuota.forCreate(
            premium: context.premium,
            type: request.type,
            existingIdsByMiff: context.idsByMiff,
            distinctPlaces: context.distinctPlaces,
            newId: newPlaceId
        )
        let placeId: String
        switch slot {
        case .needsPremium(let reason):
            return .needsPremium(reason)
        case .use(let id):
            placeId = id
        }
        let lot = PlaceRequestWrites.accept(
            uid: uid,
            request: request,
            placeId: placeId,
            remaining: max(knownCount - 1, 0)
        )
        switch await guarded({ try await store.commit(lot) }) {
        case .value:
            onPlaceAdded()
            return .done(placeId: placeId)
        case .failed(let failure, _):
            return await afterFailedAccept(uid: uid, request: request, placeId: placeId, failure: failure)
        }
    }

    /// Le lot est atomique : si la demande a disparu maintenant, le lieu existe (la réponse s'est perdue). Refusé par la règle du lieu alors
    /// que la formule semblait bonne (elle a pris fin, ou un autre téléphone a rempli les emplacements gratuits ; contrat 2.3.3) : la
    /// formule et les lieux sont relus et l'offre s'ouvre, la demande reste en attente. Jamais une erreur brute.
    private func afterFailedAccept(
        uid: String,
        request: PlaceRequest,
        placeId: String,
        failure: MiffFailure
    ) async -> RequestAcceptResult {
        let store = self.store
        if failure == .offline || failure == .unknown {
            let left = await guarded({ try await store.readOne(miffId: request.miffId, requestId: request.id) })
            if case .value(let found) = left, found == nil {
                onPlaceAdded()
                return .done(placeId: placeId)
            }
        }
        if failure == .refused, case .loaded(let again) = await placeRepository.prepare(uid: uid) {
            let retry = PlaceQuota.forCreate(
                premium: again.premium,
                type: request.type,
                existingIdsByMiff: again.idsByMiff,
                distinctPlaces: again.distinctPlaces,
                newId: newPlaceId
            )
            if case .needsPremium(let reason) = retry {
                return .needsPremium(reason)
            }
        }
        return .failed(failure)
    }

    func decline(_ request: PlaceRequest, knownCount: Int) async -> RequestDeclineResult {
        let store = self.store
        let lot = PlaceRequestWrites.decline(
            miffId: request.miffId,
            requestId: request.id,
            remaining: max(knownCount - 1, 0)
        )
        let failure: MiffFailure
        switch await guarded({ try await store.commit(lot) }) {
        case .value:
            return .done
        case .failed(let found, _):
            failure = found
        }
        // Déjà partie (traitée sur un autre téléphone, reprise) : elle est partie, ce qui était demandé.
        let left = await guarded({ try await store.readOne(miffId: request.miffId, requestId: request.id) })
        if case .value(let found) = left, found == nil {
            return .done
        }
        return .failed(failure)
    }

    func purge(miffId: String, requestIds: [String], remaining: Int) async {
        let store = self.store
        var start = 0
        while start < requestIds.count {
            let end = min(start + PlaceRequestLimits.creatorDeletesPerLot, requestIds.count)
            let chunk = Array(requestIds[start..<end])
            let lot = PlaceRequestWrites.purge(miffId: miffId, requestIds: chunk, remaining: remaining)
            if case .failed = await guarded({ try await store.commit(lot) }) {
                return
            }
            start = end
        }
    }
}
