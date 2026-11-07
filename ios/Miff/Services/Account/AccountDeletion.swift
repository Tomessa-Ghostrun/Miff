import Foundation

// SÉCURITÉ, pour ce fichier : rien de ce qui identifie une personne (identifiant, e-mail, nom) n'est journalisé, envoyé comme événement
// ou mis dans un message d'erreur. Tout échec sort d'ici sous la forme d'une valeur de `DeletionRun` ou de `Inventory`. RIEN n'est appelé
// avant que la personne se soit ré-authentifiée (les écrans ne lancent le contrôleur qu'après une ré-authentification réussie).

/// La suppression d'un compte (ticket AUTH-5, §5.5, étapes 2 à 8), avec ou sans Miffs, en UNE procédure. Miroir d'Android `AccountDeletion`.
///
/// Ordre, chaque étape IDEMPOTENTE et décidée d'après ce que dit le SERVEUR, jamais d'après un compteur gardé sur le téléphone :
/// 1. les Miffs, lues sur le serveur ; puis, AVANT le premier lot, la session « un compte, un seul appareil » (SESS-3,
///    `users/{uid}/session/active`) est supprimée avec l'accusé du serveur (décision du PM du 2026-11-06, qui INVERSE l'ordre du ticket
///    §5.13) : les autres téléphones du compte la voient disparaître et se déconnectent aussitôt, au lieu de voir les Miffs partir une à
///    une. Sans risque : les règles ne regardent jamais la session pour une suppression, et cet appareil est fermé (rien n'est écrit,
///    aucune session ouverte). Les index périmés sont nettoyés, puis les Miffs REJOINTES quittées (lot « Quitter », aucun changement de
///    code), puis les Miffs CRÉÉES supprimées l'une après l'autre. Les index sont recomptés : une Miff apparue entre-temps (autre
///    téléphone du compte) lance un autre passage, `maxPasses` au plus, puis la suppression est ANNULÉE (`miffsRemain`) plutôt que de
///    laisser un compte sans sa garde ;
/// 2. les appareils (jetons de notification) un par un, puis la session ENCORE (idempotent, accusé) : un autre téléphone a pu se connecter
///    entre-temps et en écrire une nouvelle, qui serait orpheline derrière un compte supprimé ;
/// 3. vérification finale : `memberships` relu, toujours vide, sinon retour aux Miffs ;
/// 4. le profil `users/{uid}` : le DERNIER document, pour que sa présence dise, sur le serveur, qu'il reste du travail ;
/// 5. les écritures en attente partent (elles seraient refusées sans compte), puis le compte d'authentification est supprimé : EN DERNIER.
///
/// L'IMPASSE (décisions du PM des 2026-10-31 et 2026-11-05) : le compte d'authentification et le profil ne sont JAMAIS supprimés tant
/// qu'une Miff créée par la personne existe. Une étape qui échoue durablement (le contrôleur essaie trois fois) laisse le marqueur et le
/// compte tels quels : écran calme avec le contact et « Réessayer », le lancement suivant reprend. Rien n'est transféré.
///
/// Une étape refusée n'est pas toujours un échec : le compte a peut-être déjà disparu (console, autre téléphone), ce qui est le but.
/// `AuthGateway.checkSession` le dit : disparu = succès (critère SC28). L'appelant arrête les écoutes et le suivi et écrit le marqueur
/// local AVANT le premier appel (étape 0 et critère SC15).
final class AccountDeletion: AccountDeletionProcedure {
    /// Passages sur les Miffs (et tours, après la vérification finale) tant que du nouveau apparaît : puis la suppression est annulée.
    static let maxPasses = 3
    /// Plus de personnes que cela à retirer des Miffs créées : l'attente est dite plus longue (maquette D3).
    static let longWaitRemovals = 3

    private let store: AccountDataStore
    private let gateway: AuthGateway
    private let cache: CachePurger
    private let session: AccountSession
    private let miffs: AccountMiffs
    private let timeoutSeconds: TimeInterval

    init(
        store: AccountDataStore,
        gateway: AuthGateway,
        cache: CachePurger,
        session: AccountSession,
        miffs: AccountMiffs,
        timeoutSeconds: TimeInterval = 20
    ) {
        self.store = store
        self.gateway = gateway
        self.cache = cache
        self.session = session
        self.miffs = miffs
        self.timeoutSeconds = timeoutSeconds
    }

    func inventory(uid: String) async -> Inventory {
        switch await miffs.inventory(uid: uid) {
        case .read(let found):
            return .loaded(AccountMiffsSummary(joined: found.joined.count, owned: found.owned))
        case .failed(let failure):
            return failure == .offline ? .offline : .failed
        }
    }

    func run(uid: String, onStep: @escaping DeletionStepReporter) async -> DeletionRun {
        await runRound(uid: uid, onStep: onStep, number: 1)
    }

    private func runRound(uid: String, onStep: @escaping DeletionStepReporter, number: Int) async -> DeletionRun {
        // Le marqueur appartient à UN compte : un autre n'est jamais supprimé par un marqueur qui n'est pas le sien. Personne de connecté :
        // le compte existe peut-être encore (déconnexion forcée, jeton révoqué...) : jamais pris pour « supprimé ».
        guard let current = gateway.currentUid else {
            return .sessionExpired
        }
        guard current == uid else {
            return .failed
        }
        if let stopped = await clearMiffs(uid: uid, onStep: onStep, round: number) {
            return stopped
        }
        await onStep(.profile)
        if let stopped = await clearDevicesAndSession(uid: uid) {
            return stopped
        }
        // 3. Vérification finale : vide, sinon retour aux Miffs (une Miff apparue entre-temps), quelques tours au plus.
        switch await guarded({ try await self.store.membershipCount(uid: uid) }) {
        case .value(let left):
            if left > 0 {
                if number >= Self.maxPasses {
                    return .miffsRemain
                }
                return await runRound(uid: uid, onStep: onStep, number: number + 1)
            }
        case .failed(let failure, _):
            return await afterRefusal(failure)
        }
        return await deleteProfileThenAccount(uid: uid)
    }

    // MARK: - Étape 1 : les Miffs, et la session juste après l'inventaire

    /// Rend nil quand les Miffs sont toutes traitées et les index vides ; sinon, comment la procédure se termine.
    private func clearMiffs(uid: String, onStep: @escaping DeletionStepReporter, round: Int) async -> DeletionRun? {
        await onStep(.checking)
        // La session part juste après le premier inventaire (tour 1 seulement : le tour 2 l'a déjà fait), avant tout lot.
        var sessionDeletedEarly = round > 1
        for pass in 1...Self.maxPasses {
            let inventory: MiffsInventory
            switch await miffs.inventory(uid: uid) {
            case .read(let found):
                inventory = found
            case .failed(let failure):
                return await afterRefusal(failure)
            }
            if !sessionDeletedEarly {
                switch await guarded({ try await self.session.deleteSession(uid: uid) }) {
                case .value:
                    sessionDeletedEarly = true
                case .failed(let failure, _):
                    return await afterRefusal(failure)
                }
            }
            let worked = !inventory.isEmpty
            if let stopped = await leaveEverything(uid: uid, inventory: inventory, onStep: onStep) {
                return stopped
            }
            switch await guarded({ try await self.store.membershipCount(uid: uid) }) {
            case .value(let left):
                if left == 0 {
                    return nil
                }
                // Il reste quelque chose : une Miff apparue entre-temps a un autre passage. Après le dernier, rien n'est deviné : avec de
                // nouvelles Miffs la suppression est annulée, avec quelque chose qu'on ne sait pas nommer (aucun travail trouvé) c'est un
                // échec (l'arrêt calme).
                if pass >= Self.maxPasses {
                    return worked ? .miffsRemain : .failed
                }
            case .failed(let failure, _):
                return await afterRefusal(failure)
            }
        }
        return .failed
    }

    /// Les Miffs d'un passage, dans l'ordre du ticket (§5.5, étapes 3 et 4) : les index PÉRIMÉS (en silence), les Miffs REJOINTES, puis les
    /// Miffs CRÉÉES, strictement l'une après l'autre (chaque retrait lit le code que le précédent a écrit). Rend nil si tout est fait.
    private func leaveEverything(
        uid: String,
        inventory: MiffsInventory,
        onStep: @escaping DeletionStepReporter
    ) async -> DeletionRun? {
        for ref in inventory.stale {
            if case .failed(let failure) = await miffs.dropStale(uid: uid, ref: ref) {
                return await afterRefusal(failure)
            }
        }
        let total = inventory.visibleCount
        let longWait = inventory.removals > Self.longWaitRemovals
        var index = 0
        for ref in inventory.joined {
            index += 1
            await onStep(.miff(index: index, total: total, longWait: longWait))
            if case .failed(let failure) = await miffs.leave(uid: uid, ref: ref) {
                return await afterRefusal(failure)
            }
        }
        for owned in inventory.owned {
            index += 1
            await onStep(.miff(index: index, total: total, longWait: longWait))
            if case .failed(let failure) = await miffs.deleteOwned(uid: uid, miffId: owned.miffId) {
                return await afterRefusal(failure)
            }
        }
        return nil
    }

    // MARK: - Étape 2 : les appareils, puis la session encore

    private func clearDevicesAndSession(uid: String) async -> DeletionRun? {
        let devices: [String]
        switch await guarded({ try await self.store.deviceIds(uid: uid) }) {
        case .value(let found):
            devices = found
        case .failed(let failure, _):
            return await afterRefusal(failure)
        }
        for deviceId in devices {
            let deleted = await guarded { try await self.store.deleteDevice(uid: uid, deviceId: deviceId) }
            if case .failed(let failure, _) = deleted {
                return await afterRefusal(failure)
            }
        }
        // 2 bis. La session du compte ENCORE, AVANT le profil ; pas d'accusé = pas d'étape de plus (reprenable, idempotent).
        if case .failed(let failure, _) = await guarded({ try await self.session.deleteSession(uid: uid) }) {
            return await afterRefusal(failure)
        }
        return nil
    }

    // MARK: - Étapes 4 et 5 : le profil, puis le compte

    private func deleteProfileThenAccount(uid: String) async -> DeletionRun {
        if case .failed(let failure, _) = await guarded({ try await self.store.deleteProfile(uid: uid) }) {
            return await afterRefusal(failure)
        }
        // La purge est enregistrée AVANT que le compte parte : tuée entre les deux, l'app purge au lancement suivant avant tout accès.
        cache.markPurgePending()
        await cache.flushPendingWrites()
        // `deleteCurrentAccount` répond « fait » quand personne n'est connecté : ce ne serait pas une suppression.
        guard gateway.currentUid == uid else {
            return .sessionExpired
        }
        switch await gateway.deleteCurrentAccount() {
        case nil, .accountGone:
            return .completed
        case .sessionExpired:
            return .sessionExpired
        case .recentLoginRequired:
            return .needsRecentLogin
        case .network:
            return .offline
        default:
            return await afterRefusal(.unknown)
        }
    }

    // MARK: - Intérieur

    private func guarded<Value>(_ call: @escaping () async throws -> Value) async -> Guarded<Value> {
        await guardedCall(timeoutSeconds: timeoutSeconds, call)
    }

    /// Une étape a échoué : pas de réseau est une pause ; un compte qui n'existe plus est le succès voulu ; le reste est un échec.
    private func afterRefusal(_ failure: MiffFailure) async -> DeletionRun {
        if failure == .offline {
            return .offline
        }
        switch await gateway.checkSession() {
        case .gone:
            return .completed
        case .expired:
            return .sessionExpired
        case .valid, .unknown:
            return .failed
        }
    }
}
