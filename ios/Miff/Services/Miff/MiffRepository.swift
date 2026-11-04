import Foundation

// SÉCURITÉ, pour ce fichier : codes, noms et profils sont des secrets ou des données personnelles. Ils ne sont jamais
// journalisés, jamais dans un événement Analytics, jamais dans un message d'erreur. Chaque échec sort d'ici sous la forme
// d'une `MiffFailure` (sans message, sans code).

/// Tout ce que les tickets Miff font avec Firestore, tel que les écrans en ont besoin (ticket Miff §5.4, §5.7, §5.13) :
/// l'ordre des vérifications pour entrer, les lots, le nettoyage après une perte d'accès, la vue en direct d'une Miff.
/// La logique est du Swift pur : Firestore est derrière `MiffStore`, remplaçable par un double dans les tests. La lecture de
/// ce que la personne tient est dans `MiffStateReader`.
final class FirestoreMiffRepository: MiffRepository {
    static let maxCodeDraws = 3
    static let freeKey = "free"

    private let store: MiffStore
    private let observer: MiffObserver
    private let location: LocationAccessProbe
    /// Vide le cache du téléphone après un nettoyage dont personne n'est averti (voir `cleanUpSilently`).
    private let cache: CachePurger?
    private let rules: MiffRepositoryRules
    private let reader: MiffStateReader

    init(
        store: MiffStore,
        observer: MiffObserver,
        location: LocationAccessProbe,
        cache: CachePurger?,
        rules: MiffRepositoryRules
    ) {
        self.store = store
        self.observer = observer
        self.location = location
        self.cache = cache
        self.rules = rules
        reader = MiffStateReader(store: store, rules: rules)
    }

    // MARK: - Créer

    func prepareCreate(uid: String) async -> CreatePreparation {
        let state: HeldState
        switch await reader.read(uid: uid, allowCache: false) {
        case .value(let loaded):
            state = loaded
        case .failed(let failure):
            return .failed(failure)
        }
        // Un index obsolète (retiré, ou Miff supprimée) est nettoyé AVANT l'écriture, sinon les règles la refuseraient pour rien.
        await cleanUpSilently(uid: uid, lost: state.lost)
        if !state.premium && !state.valid.isEmpty {
            return .needsPremium
        }
        return .ready(CreateReady(premium: state.premium, firstMiff: state.valid.isEmpty))
    }

    func create(uid: String, name: String, preparation: CreateReady) async -> CreateResult {
        guard MiffName.isValid(name) else {
            return .failed(.unknown)
        }
        let code: String
        switch await freeCode() {
        case .free(let drawn):
            code = drawn
        case .failed(let failure):
            return .failed(failure)
        }
        let miffId = rules.newMiffId()
        let access = location.current()
        let key = membershipKey(premium: preparation.premium, miffId: miffId)
        let store = self.store
        let result = await write {
            try await store.commitWithProfile(uid: uid) { profile in
                let entry = MiffEntry(uid: uid, miffId: miffId, membershipKey: key, profile: profile, access: access)
                return MiffWrites.create(entry: entry, code: code, name: name)
            }
        }
        let creation = MiffCreation(
            miffId: miffId,
            joinCode: code,
            name: MiffName.stored(name),
            firstMiff: preparation.firstMiff
        )
        switch result {
        case .done:
            return .created(creation)
        case .failed(let failure):
            // Un lot parti dont la réponse s'est perdue ne s'affiche pas comme un échec (un appui = une Miff, critère H3).
            if failure.mayHaveLanded, await reader.landed(miffId) {
                return .created(creation)
            }
            return .failed(failure)
        }
    }

    private enum CodeDraw {
        case free(String)
        case failed(MiffFailure)
    }

    /// Un code que personne n'utilise : une lecture par essai, trois essais (critère C7). Une collision est de l'ordre de
    /// 10^-13 : jamais visible.
    private func freeCode() async -> CodeDraw {
        let store = self.store
        for _ in 0..<Self.maxCodeDraws {
            let code = rules.newCode()
            guard JoinCode.isValid(code) else {
                return .failed(.unknown)
            }
            let read = await guarded { try await store.readJoinCode(code) }
            switch read {
            case .value(let found):
                if found == nil {
                    return .free(code)
                }
            case .failed(let failure, _):
                return .failed(failure)
            }
        }
        return .failed(.unknown)
    }

    // MARK: - Rejoindre

    func prepareJoin(uid: String, typedCode: String) async -> JoinPreparation {
        let input = JoinCode.analyze(typedCode)
        guard input.isComplete else {
            return .notFound
        }
        let code = input.code
        let store = self.store
        // Étape 3 : une lecture du code. « Introuvable » est un seul message, quelle que soit la cause.
        let read = await guarded { try await store.readJoinCode(code) }
        let found: [String: Any]?
        switch read {
        case .value(let value):
            found = value
        case .failed(let failure, _):
            return .failed(failure)
        }
        guard let target = Self.joinTarget(from: found) else {
            return .notFound
        }
        // Étape 4 : un index obsolète est nettoyé avant toute écriture. Étape 5 : déjà dans cette Miff ?
        let state: HeldState
        switch await reader.read(uid: uid, allowCache: false) {
        case .value(let loaded):
            state = loaded
        case .failed(let failure):
            return .failed(failure)
        }
        await cleanUpSilently(uid: uid, lost: state.lost)
        // Comparé sur le CHAMP miffId des index, jamais sur l'identifiant du document (« free »).
        if state.valid.contains(where: { $0.miffId == target.miffId }) {
            return .alreadyMember(miffId: target.miffId, miffName: target.miffName)
        }
        if !state.premium && !state.valid.isEmpty {
            return .needsPremium
        }
        let ready = JoinReady(
            miffId: target.miffId,
            miffName: target.miffName,
            code: code,
            premium: state.premium,
            firstMiff: state.valid.isEmpty
        )
        return .ready(ready)
    }

    /// Ce que dit un document `joinCodes/{code}` s'il a la forme que les règles exigent ; nil sinon. Le nom a été écrit par le
    /// détenteur du code, avant tout consentement du lecteur : il est nettoyé comme tout nom reçu d'un autre téléphone, et un
    /// nom dont il ne reste rien de visible reçoit la réponse d'un code inconnu.
    private static func joinTarget(from found: [String: Any]?) -> (miffId: String, miffName: String)? {
        guard let found,
              let miffId = found["miffId"] as? String,
              MiffId.isValid(miffId),
              let rawName = found["miffName"] as? String,
              let miffName = MiffName.fromServer(rawName) else {
            return nil
        }
        return (miffId, miffName)
    }

    func join(uid: String, preparation: JoinReady) async -> JoinResult {
        let access = location.current()
        let key = membershipKey(premium: preparation.premium, miffId: preparation.miffId)
        let store = self.store
        let result = await write {
            try await store.commitWithProfile(uid: uid) { profile in
                let entry = MiffEntry(
                    uid: uid,
                    miffId: preparation.miffId,
                    membershipKey: key,
                    profile: profile,
                    access: access
                )
                return MiffWrites.join(entry: entry, code: preparation.code)
            }
        }
        let joined = JoinResult.joined(miffId: preparation.miffId, firstMiff: preparation.firstMiff)
        switch result {
        case .done:
            return joined
        case .failed(let failure):
            if failure.mayHaveLanded, await reader.landed(preparation.miffId) {
                return joined
            }
            // Les règles ont dit « non » : le code a été renouvelé, ou la Miff supprimée, depuis qu'il a été lu. Même message.
            if failure == .refused {
                return .notFound
            }
            return .failed(failure)
        }
    }

    // MARK: - Renommer, partager

    func rename(miffId: String, code: String, name: String) async -> MiffWriteResult {
        guard MiffId.isValid(miffId), MiffName.isValid(name), JoinCode.isValid(code) else {
            return .failed(.unknown)
        }
        let store = self.store
        let writes = MiffWrites.rename(miffId: miffId, code: code, name: name)
        let result = await write { try await store.commit(writes) }
        // Un lot dont la réponse s'est perdue est vérifié auprès du serveur avant d'être appelé un échec.
        if case .failed(let failure) = result, failure.mayHaveLanded {
            let read = await guarded { try await store.readMiff(miffId: miffId, fromServer: true) }
            if case .value(.exists(let details)) = read, details.name == MiffName.stored(name) {
                return .done
            }
        }
        return result
    }

    func setSharing(miffId: String, uid: String, enabled: Bool) async -> MiffWriteResult {
        guard MiffId.isValid(miffId) else {
            return .failed(.unknown)
        }
        let store = self.store
        let writes: [DocWrite]
        if enabled {
            writes = MiffWrites.resumeSharing(miffId: miffId, uid: uid, access: location.current())
        } else {
            writes = MiffWrites.stopSharing(miffId: miffId, uid: uid)
        }
        let result = await write { try await store.commit(writes) }
        // Un refus peut aussi vouloir dire « déjà dans l'état demandé » : la fenêtre demande le contraire de ce que disait la
        // fiche à son ouverture, et la fiche a pu changer depuis (un autre téléphone) ; les règles refusent alors une écriture
        // sans effet (faux -> faux, vrai -> vrai). La fiche du serveur est relue, et l'état voulu compte comme un succès.
        if case .failed(let failure) = result, failure.mayHaveLanded || failure == .refused {
            let read = await guarded { try await store.readSharing(miffId: miffId, uid: uid) }
            if case .value(let current) = read, current == enabled {
                return .done
            }
        }
        return result
    }

    // MARK: - Les Miffs de la personne, perte d'accès

    func myMiffs(uid: String) async -> MyMiffsLoad {
        switch await reader.read(uid: uid, allowCache: true) {
        case .failed:
            return .unavailable
        case .value(let state):
            let miffs = MyMiffs(
                miffs: state.summaries,
                lost: state.lost,
                premium: state.premium,
                fromCache: state.fromCache
            )
            return .loaded(miffs)
        }
    }

    func cleanUpLost(uid: String, lost: LostMiff) async -> Bool {
        let store = self.store
        // Le nettoyage demande le serveur : sans lui rien n'est touché (le prochain démarrage le fera).
        let leaveWrites = MiffWrites.leave(miffId: lost.ref.miffId, uid: uid, membershipKey: lost.ref.key)
        let leave = await write { try await store.commit(leaveWrites) }
        if leave == .done {
            return true
        }
        // Retiré : la fiche a déjà disparu, le lot est refusé. Il ne reste que l'index, que les règles laissent supprimer.
        let dropWrites = MiffWrites.dropIndex(uid: uid, membershipKey: lost.ref.key)
        let indexOnly = await write { try await store.commit(dropWrites) }
        return indexOnly == .done
    }

    /// Index obsolètes trouvés en créant ou en rejoignant (la personne a été retirée d'une Miff, ou elle a été supprimée) :
    /// nettoyés sans message (la personne est occupée à autre chose), mais JAMAIS en laissant cette Miff lisible hors ligne :
    /// même purge du cache local que pour une perte d'accès (data-model.md §8, « Purge du cache local »).
    private func cleanUpSilently(uid: String, lost: [LostMiff]) async {
        guard !lost.isEmpty else {
            return
        }
        cache?.markPurgePending()
        for item in lost {
            _ = await cleanUpLost(uid: uid, lost: item)
        }
        _ = await cache?.purge()
    }

    func observe(miffId: String) -> AsyncStream<MiffObservation> {
        observer.observe(miffId: miffId)
    }

    // MARK: - Intérieur

    private func membershipKey(premium: Bool, miffId: String) -> String {
        premium ? miffId : Self.freeKey
    }

    private func write(_ action: @escaping () async throws -> Void) async -> MiffWriteResult {
        switch await guarded(action) {
        case .value:
            return .done
        case .failed(let failure, _):
            return .failed(failure)
        }
    }

    private func guarded<Value>(_ call: @escaping () async throws -> Value) async -> Guarded<Value> {
        await guardedCall(timeoutSeconds: rules.timeoutSeconds, call)
    }
}
