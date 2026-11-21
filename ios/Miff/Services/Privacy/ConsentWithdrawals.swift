import Foundation

/// L'unique endroit qui décide ce qui arrive à un RETRAIT du consentement « statistiques et rapports de plantage » (ticket ONB-3b,
/// décision du PM « durcir le consentement »). Même rôle et mêmes règles qu'Android (`ConsentWithdrawals`).
///
/// La règle : la personne qui a coupé la collecte n'est jamais collectée de nouveau parce qu'un réseau manquait.
/// - `withdraw` marque le retrait sur l'appareil D'ABORD (`PendingWithdrawals`, sur le disque), puis tente de l'enregistrer dans le
///   compte. Tant qu'il est marqué, `SessionManager` garde la collecte ÉTEINTE à chaque démarrage, quoi que dise encore le compte.
/// - Si le compte n'a pas pu être joint, une tâche de fond attend le réseau (sans rien bloquer) et enregistre le retrait dès qu'il
///   revient, avec une pause croissante entre deux essais. Elle s'arrête au premier « non » du serveur (règles, App Check) : jamais
///   de boucle. La collecte reste alors éteinte, l'écran continue de proposer « Enregistrer mon choix », et le démarrage suivant
///   essaie encore une fois.
/// - La marque appartient à UN compte : elle n'est enregistrée que pour ce compte, et seulement tant que ce compte est celui qui est
///   connecté. Après une déconnexion ou un changement de compte elle attend son compte ; elle n'est jamais appliquée à un autre.
/// - Accepter de nouveau avant que le retrait soit enregistré passe par `accept` : les deux opérations partagent une porte, et
///   l'écriture de l'acceptation n'annule la marque qu'une fois le serveur d'accord.
@MainActor
final class ConsentWithdrawals {
    /// Pauses entre deux essais de l'enregistrement en arrière-plan, en millisecondes (la dernière se répète).
    static let defaultDelaysMillis = [5_000, 15_000, 60_000, 300_000]

    private let pending: PendingWithdrawals
    private let consents: AnalyticsConsentRepository
    private let network: NetworkMonitor
    private let currentUid: () -> String?
    private let sleeper: (Int) async -> Void
    private let retryDelaysMillis: [Int]
    private let gate = AsyncGate()
    private var job: Task<Void, Never>?
    /// Numéro de la tâche de fond en cours : une tâche arrêtée ne doit jamais effacer la référence de la suivante.
    private var jobId = 0
    /// Comptes pour lesquels le serveur a dit « non » dans cette exécution : plus d'essai automatique avant le démarrage suivant.
    private var gaveUp = Set<String>()

    init(
        pending: PendingWithdrawals,
        consents: AnalyticsConsentRepository,
        network: NetworkMonitor,
        currentUid: @escaping () -> String?,
        sleep: @escaping (Int) async -> Void = { millis in
            try? await Task.sleep(nanoseconds: UInt64(millis) * 1_000_000)
        },
        retryDelaysMillis: [Int] = ConsentWithdrawals.defaultDelaysMillis
    ) {
        self.pending = pending
        self.consents = consents
        self.network = network
        self.currentUid = currentUid
        self.sleeper = sleep
        self.retryDelaysMillis = retryDelaysMillis
    }

    func isPending(uid: String) -> Bool {
        pending.isPending(uid: uid)
    }

    /// La personne a coupé la collecte : marquer, tenter d'enregistrer, et continuer en arrière-plan si besoin.
    func withdraw(uid: String) async -> ProfileWriteResult {
        pending.mark(uid: uid)
        gaveUp.remove(uid)
        return afterTry(uid: uid, result: await settle(uid: uid))
    }

    /// « Enregistrer mon choix » : un essai de plus, tout de suite.
    func retryNow(uid: String) async -> ProfileWriteResult {
        gaveUp.remove(uid)
        return afterTry(uid: uid, result: await settle(uid: uid))
    }

    /// La personne accepte de nouveau : enregistré dans le compte ; seulement alors un retrait en attente est annulé.
    func accept(uid: String) async -> ProfileWriteResult {
        await gate.run { () -> ProfileWriteResult in
            let result = await consents.set(uid: uid, enable: true)
            if result == .done {
                pending.clear(uid: uid)
            }
            return result
        }
    }

    /// Le compte `uid` vient d'être lu (connexion ou démarrage) : si un retrait l'attend, il est enregistré en arrière-plan.
    func resume(uid: String) {
        if pending.isPending(uid: uid) && !gaveUp.contains(uid) {
            startBackground(uid: uid, pauseFirst: false)
        }
    }

    /// Fin de session : la tâche de fond s'arrête ; la marque reste et attend son compte.
    func stop() async {
        job?.cancel()
        job = nil
        jobId += 1
        gaveUp.removeAll()
    }

    // MARK: - Intérieur

    /// Ce qui suit un essai fait à l'instant : attendre puis réessayer si le compte n'a pas pu être joint, s'arrêter si le serveur
    /// a dit non.
    private func afterTry(uid: String, result: ProfileWriteResult) -> ProfileWriteResult {
        switch result {
        case .offline:
            startBackground(uid: uid, pauseFirst: true)
        case .refused, .failed:
            gaveUp.insert(uid)
        case .done:
            break
        }
        return result
    }

    /// Un essai. `.done` = enregistré (ou rien à enregistrer) et la marque est partie.
    private func settle(uid: String) async -> ProfileWriteResult {
        await gate.run { () -> ProfileWriteResult in
            guard pending.isPending(uid: uid) else {
                return .done
            }
            // L'écriture se fait au nom du compte connecté : jamais au nom d'un autre.
            guard currentUid() == uid else {
                return .offline
            }
            // Sans réseau, pas de requête condamnée d'avance (sa réponse ne viendrait qu'après une longue attente).
            guard network.isOnline() else {
                return .offline
            }
            let result = await consents.set(uid: uid, enable: false)
            if result == .done {
                pending.clear(uid: uid)
            }
            return result
        }
    }

    private func startBackground(uid: String, pauseFirst: Bool) {
        guard job == nil else {
            return
        }
        jobId += 1
        let id = jobId
        job = Task { [weak self] in
            await self?.runBackground(uid: uid, pauseFirst: pauseFirst)
            self?.backgroundFinished(id)
        }
    }

    private func backgroundFinished(_ id: Int) {
        if jobId == id {
            job = nil
        }
    }

    private func runBackground(uid: String, pauseFirst: Bool) async {
        var attempt = 0
        // Un essai vient d'échouer : pas de nouvel essai avant la première pause.
        if pauseFirst {
            await sleeper(pause(for: attempt))
            attempt += 1
        }
        while !Task.isCancelled && pending.isPending(uid: uid) && currentUid() == uid {
            // Sans réseau il n'y a rien à tenter : on l'attend (aucune interrogation périodique).
            if !network.isOnline() {
                let back = await waitForNetwork()
                if !back {
                    await sleeper(pause(for: attempt))
                }
                if Task.isCancelled {
                    return
                }
            }
            switch await settle(uid: uid) {
            case .done:
                return
            case .offline:
                attempt += 1
                await sleeper(pause(for: attempt - 1))
            case .refused, .failed:
                // Le serveur a dit non : la collecte reste éteinte, pas de boucle.
                gaveUp.insert(uid)
                return
            }
        }
    }

    /// Vrai quand le réseau est revenu ; faux si le flux s'est terminé sans qu'il revienne (ou si la tâche est annulée).
    private func waitForNetwork() async -> Bool {
        for await online in network.onlineChanges() where online {
            return true
        }
        return false
    }

    private func pause(for attempt: Int) -> Int {
        retryDelaysMillis[min(attempt, retryDelaysMillis.count - 1)]
    }
}
