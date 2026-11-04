import Foundation

// SÉCURITÉ, pour ce fichier : identifiants, noms et codes passent par ici et nulle part ailleurs (ni journal, ni événement
// Analytics, ni message). Tout échec sort d'ici sous la forme d'une `MiffFailure`.

/// Supprimer une Miff, SANS écran (critères S2, S4, S8) : appelée par l'écran de la Miff, par le lancement (reprise), et plus
/// tard par la suppression d'un compte (AUTH-5) et la fin d'un abonnement (ticket Premium). Miroir de `MiffDeletion` d'Android.
///
/// L'ordre est celui qu'exigent les règles (backend/data-model.md §8) : (1) chaque autre membre est retiré, un lot chacun, le
/// code étant renouvelé à chaque fois ; (2) les lieux, 5 par lot au plus ; (3) UN dernier lot : la Miff, sa pierre tombale, le
/// code, et le statut, la photo, la fiche et l'index du créateur. Elle est IDEMPOTENTE et REPRENABLE : elle commence toujours
/// par lire le serveur (qui est encore là, quels lieux restent, la Miff existe-t-elle encore), donc on peut la relancer après
/// n'importe quelle interruption (app tuée, réseau coupé, réponse perdue) : elle continue, ou constate que tout est fini.
/// Chaque étape est une transaction confirmée par le serveur : rien n'attend dans une file pour partir plus tard tout seul
/// (risque R4 du ticket).
final class MiffDeletion: MiffDeleter {
    /// Une Miff où des gens arrivent sans cesse ne peut pas être vidée pour toujours : après ce nombre de passages la
    /// suppression s'arrête et le dit.
    static let maxMemberPasses = 4

    private let store: MiffStore
    private let repository: MiffRepository
    private let timeoutSeconds: TimeInterval

    init(store: MiffStore, repository: MiffRepository, timeoutSeconds: TimeInterval = 20) {
        self.store = store
        self.repository = repository
        self.timeoutSeconds = timeoutSeconds
    }

    func run(uid: String, miffId: String) async -> MiffDeletionResult {
        guard MiffId.isValid(miffId) else {
            return .paused(.unknown)
        }
        // La Miff, auprès du SERVEUR. Refusée ou absente (et le serveur joignable) = elle a déjà disparu.
        let details: MiffDetails
        switch await readMiff(miffId, uid: uid) {
        case .found(let found):
            details = found
        case .gone:
            return await alreadyGone(uid: uid, miffId: miffId)
        case .paused(let failure):
            return .paused(failure)
        }
        if details.ownerUid != uid {
            return .notOwner
        }
        if let stopped = await removeOtherMembers(uid: uid, miffId: miffId) {
            return stopped
        }
        if let stopped = await deletePlaces(miffId: miffId) {
            return stopped
        }
        return await deleteMiff(uid: uid, miffId: miffId)
    }

    // MARK: - Étape 1 : les autres membres

    private enum Others {
        case nobody
        case people([String])
        case failed(MiffFailure)
    }

    /// Les autres membres, lus auprès du SERVEUR (jamais depuis un cache).
    private func readOthers(uid: String, miffId: String) async -> Others {
        let store = self.store
        let read = await guardedCall(timeoutSeconds: timeoutSeconds) { try await store.readMemberIds(miffId: miffId) }
        switch read {
        case .value(let ids):
            let others = ids.filter { $0 != uid }
            return others.isEmpty ? .nobody : .people(others)
        case .failed(let failure, _):
            return .failed(failure)
        }
    }

    /// La liste est relue après chaque passage : quelqu'un qui est entré entre-temps est retiré aussi. Rend nil quand il ne
    /// reste personne.
    private func removeOtherMembers(uid: String, miffId: String) async -> MiffDeletionResult? {
        for _ in 0..<Self.maxMemberPasses {
            switch await readOthers(uid: uid, miffId: miffId) {
            case .nobody:
                return nil
            case .failed(let failure):
                return .paused(failure)
            case .people(let others):
                for target in others {
                    if case .failed(let failure) = await repository.removeMember(miffId: miffId, targetUid: target) {
                        return .paused(failure)
                    }
                }
            }
        }
        // Dernière lecture : s'il reste encore des gens après tous les passages, on s'arrête et on le dit.
        switch await readOthers(uid: uid, miffId: miffId) {
        case .nobody:
            return nil
        case .failed(let failure):
            return .paused(failure)
        case .people:
            return .paused(.unknown)
        }
    }

    // MARK: - Étape 2 : les lieux

    private func deletePlaces(miffId: String) async -> MiffDeletionResult? {
        let store = self.store
        let read = await guardedCall(timeoutSeconds: timeoutSeconds) { try await store.readPlaceIds(miffId: miffId) }
        let places: [String]
        switch read {
        case .value(let ids):
            places = ids
        case .failed(let failure, _):
            return .paused(failure)
        }
        var start = 0
        while start < places.count {
            let chunk = Array(places[start..<min(start + MiffWrites.placesPerLot, places.count)])
            let writes = MiffWrites.deletePlaces(miffId: miffId, placeIds: chunk)
            let deleted = await guardedCall(timeoutSeconds: timeoutSeconds) { try await store.commit(writes) }
            if case .failed(let failure, _) = deleted {
                return .paused(failure)
            }
            start += MiffWrites.placesPerLot
        }
        return nil
    }

    // MARK: - Étape 3 : le dernier lot

    private func deleteMiff(uid: String, miffId: String) async -> MiffDeletionResult {
        let store = self.store
        // La clé de l'index se trouve par son CHAMP miffId.
        let memberships = await guardedCall(timeoutSeconds: timeoutSeconds) {
            try await store.readMemberships(uid: uid, fromServer: true)
        }
        let key: String?
        switch memberships {
        case .value(let refs):
            key = refs.first { $0.miffId == miffId }?.key
        case .failed(let failure, _):
            return .paused(failure)
        }
        let last = await guardedCall(timeoutSeconds: timeoutSeconds) {
            try await store.commitWithMiff(miffId: miffId) { view in
                MiffWrites.finalDelete(
                    miffId: miffId,
                    uid: uid,
                    code: view.details.joinCode,
                    codeExists: view.codeDocumentExists,
                    membershipKey: key
                )
            }
        }
        switch last {
        case .value:
            return .deleted
        case .failed(let failure, _):
            // La réponse a pu se perdre alors que le lot est parti : si le serveur refuse maintenant la Miff, elle est supprimée.
            if case .gone = await readMiff(miffId, uid: uid) {
                return .deleted
            }
            return .paused(failure)
        }
    }

    // MARK: - Déjà disparue

    /// La Miff ne peut plus être lue et le serveur répond : il n'y a plus rien à supprimer. Si l'index du créateur est encore
    /// là (une exécution précédente n'a pas pu l'écrire), il est supprimé (les règles le permettent maintenant que la fiche a
    /// disparu).
    private func alreadyGone(uid: String, miffId: String) async -> MiffDeletionResult {
        let store = self.store
        let read = await guardedCall(timeoutSeconds: timeoutSeconds) {
            try await store.readMemberships(uid: uid, fromServer: true)
        }
        let refs: [MembershipRef]
        switch read {
        case .value(let found):
            refs = found
        case .failed(let failure, _):
            return .paused(failure)
        }
        for ref in refs where ref.miffId == miffId {
            let writes = MiffWrites.dropIndex(uid: uid, membershipKey: ref.key)
            let dropped = await guardedCall(timeoutSeconds: timeoutSeconds) { try await store.commit(writes) }
            if case .failed(let failure, _) = dropped {
                return .paused(failure)
            }
        }
        return .deleted
    }

    // MARK: - Lire la Miff

    private enum MiffReading {
        case found(MiffDetails)
        /// Supprimée, ou refusée alors que le serveur répond par ailleurs.
        case gone
        case paused(MiffFailure)
    }

    private func readMiff(_ miffId: String, uid: String) async -> MiffReading {
        let store = self.store
        let read = await guardedCall(timeoutSeconds: timeoutSeconds) {
            try await store.readMiff(miffId: miffId, fromServer: true)
        }
        switch read {
        case .value(.exists(let details)):
            return .found(details)
        case .value(.missing):
            return .gone
        case .failed(let failure, let lostAccess):
            // Un refus n'est « disparue » que si le serveur répond par ailleurs : un refus d'App Check refuserait tout.
            if lostAccess, await isReachable(uid: uid) {
                return .gone
            }
            return .paused(failure)
        }
    }

    private func isReachable(uid: String) async -> Bool {
        let store = self.store
        let read = await guardedCall(timeoutSeconds: timeoutSeconds) { await store.serverReachable(uid: uid) }
        if case .value(let reachable) = read {
            return reachable
        }
        return false
    }
}
