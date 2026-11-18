#if ALERT_DANGER
import Combine
import Foundation

// MON alerte, du côté de l'auteur (ticket docs/tickets/alerte-danger.md, section 6, la machine d'états ; miroir d'`AlertController.kt`
// d'Android). UNE alerte à la fois (D14), liée à UNE Miff (la Miff d'origine, D9).
//
// La vérité est le document du serveur ; le téléphone garde un POINTEUR (`DangerAlertLocalStore` : l'identifiant de la Miff, la phase, trois
// instants) pour que l'alerte survive à un relancement et que l'app écoute la bonne Miff pendant que le Feed en montre une autre.
//
// Ce que cette classe garantit (chaque point a un test) :
// - RIEN ne part avant que les 5 secondes soient tenues (l'écran appelle `trigger` à cet instant, jamais avant) ;
// - une alerte qui ne peut pas partir (pas de réseau, pas de réponse) est gardée EN ATTENTE, visiblement, et réessayée au retour du réseau ;
//   après 15 minutes elle ne part qu'une fois la personne reconfirmée (`needsConfirmation`) : une alerte envoyée des heures trop tard
//   alarmerait le cercle pour rien ;
// - une fin qui ne peut pas partir garde l'alerte ACTIVE (« Fin en attente d'envoi ») : l'alerte n'est jamais considérée terminée localement
//   tant que le serveur ne l'a pas acceptée (sinon le cercle resterait alerté pendant que la personne croit que c'est fini) ;
// - une seconde alerte n'est jamais démarrée pendant qu'une est en cours, dans aucune Miff (D14) ;
// - UNE mesure de position est demandée UNE fois au départ (`onSending`), jamais en boucle, et l'alerte part même si elle échoue (la position est
//   un bonus) ;
// - à 7 jours l'alerte disparaît simplement : aucune écriture, aucune notification, aucun rappel (D19) ;
// - rien n'est journalisé, rien n'est envoyé comme événement : cette classe n'écrit jamais un nom, une position ni un identifiant ailleurs que dans
//   le document d'alerte du serveur.

/// Où en est l'envoi d'une alerte.
enum DangerSendStep: Equatable {
    case sending
    case waitingNetwork
    case needsConfirmation
    /// Le serveur a dit non et rien ne l'explique (horloge, règles pas à jour) : « Réessayer ».
    case refused
    /// La limite de 3 alertes par jour est atteinte (décision du PM) : aucun nouvel essai n'est proposé, les numéros d'urgence passent d'abord.
    case quota
}

enum DangerEndStep: Equatable {
    case idle
    case ending
    case waitingNetwork
}

/// L'état de MON alerte.
enum MyDangerAlert: Equatable, CustomStringConvertible {
    case idle
    /// Des 5 secondes à l'accusé du serveur.
    case sending(miffId: String, armedAtMillis: Int, step: DangerSendStep)
    case active(miffId: String, startedAtMillis: Int, expiresAtMillis: Int, end: DangerEndStep)

    /// La Miff d'origine, ou nil quand il n'y a pas d'alerte.
    var miffId: String? {
        switch self {
        case .idle:
            return nil
        case .sending(let miffId, _, _), .active(let miffId, _, _, _):
            return miffId
        }
    }

    var description: String {
        "MyDangerAlert(redacted)"
    }
}

/// Ce que les écrans disent UNE fois, hors de leur dessin.
enum DangerAlertEvent: Equatable {
    /// La fin a été acceptée par le serveur : « Alerte terminée… » (une ligne calme, une fois).
    case ended(miffId: String)
    /// La fin a été refusée (autre chose qu'un problème de réseau) : « La fin de l'alerte n'est pas partie… ».
    case endNotSent
    /// L'alerte n'a pas pu démarrer à cause de ce que le serveur dit de moi et de cette Miff (partage arrêté, plus membre).
    case notSent(miffId: String, failure: DangerAlertFailure)
}

enum DangerTriggerResult: Equatable {
    case started
    /// Une alerte est déjà en cours (dans une Miff quelconque) : la phrase calme « Une alerte est déjà en cours pour la Miff {nom} ».
    case alreadyActive(miffId: String)
    case notSignedIn
}

@MainActor
final class DangerAlertController: ObservableObject {
    /// Les pauses entre deux essais quand le réseau est là mais que le serveur ne répond pas : bornées, jamais une boucle serrée (les 15
    /// minutes y mettent fin de toute façon).
    static let defaultRetryPausesMillis = [5_000, 15_000, 30_000, 60_000, 120_000]

    @Published private(set) var state: MyDangerAlert = .idle

    /// Ce que le contrôleur dit une fois (fin acceptée, fin refusée, alerte non partie). Posé par l'hôte des écrans.
    var onEvent: ((DangerAlertEvent) -> Void)?

    let store: DangerAlertStore
    let device: ActiveDevice
    let network: NetworkMonitor
    let local: DangerAlertLocalStore
    let currentUid: () -> String?
    let nowMillis: () -> Int
    /// UNE mesure de la position, demandée au départ (jamais attendue, jamais répétée) : le suivi la publie sous ses propres garde-fous.
    let onSending: () -> Void
    let sendTimeoutSeconds: Double
    let retryPausesMillis: [Int]
    let sleep: (Int) async -> Void
    var job: Task<Void, Never>?
    /// La vérification, auprès du serveur, d'une alerte ABANDONNÉE (voir `cancelPending`).
    var verifyJob: Task<Void, Never>?
    /// Combien d'essais cette vérification fait (le pointeur reste, et la prochaine ouverture de l'app recommence).
    static let maxVerifyAttempts = 8

    init(
        store: DangerAlertStore,
        device: ActiveDevice,
        network: NetworkMonitor,
        local: DangerAlertLocalStore,
        currentUid: @escaping () -> String?,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        onSending: @escaping () -> Void = {},
        sendTimeoutSeconds: Double = DangerAlertRules.sendTimeoutSeconds,
        retryPausesMillis: [Int] = DangerAlertController.defaultRetryPausesMillis,
        sleep: @escaping (Int) async -> Void = AppClock.live.pause
    ) {
        self.store = store
        self.device = device
        self.network = network
        self.local = local
        self.currentUid = currentUid
        self.nowMillis = nowMillis
        self.onSending = onSending
        self.sendTimeoutSeconds = sendTimeoutSeconds
        self.retryPausesMillis = retryPausesMillis.isEmpty ? Self.defaultRetryPausesMillis : retryPausesMillis
        self.sleep = sleep
    }

    // MARK: - Ce que fait l'app

    /// Reprend ce que dit le pointeur (un relancement de l'app) : une alerte en route continue, une alerte active revient. Idempotent.
    func restore() {
        guard state == .idle, let pointer = local.pointer() else {
            return
        }
        switch pointer.phase {
        case .sending:
            state = .sending(miffId: pointer.miffId, armedAtMillis: pointer.armedAtMillis, step: .waitingNetwork)
            launchSend(miffId: pointer.miffId, armedAt: pointer.armedAtMillis, confirmed: pointer.staleConfirmed)
        case .abandoned:
            // Fermée par la personne, mais le serveur n'a pas encore confirmé qu'aucune alerte vivante n'y est : la vérification reprend.
            launchVerifyAbandoned(miffId: pointer.miffId)
        case .active:
            guard let expires = pointer.expiresAtMillis,
                  let started = pointer.startedAtMillis,
                  expires > nowMillis() else {
                local.clearPointer()
                return
            }
            state = .active(miffId: pointer.miffId, startedAtMillis: started, expiresAtMillis: expires, end: .idle)
        }
    }

    /// Les 5 secondes ont été tenues dans la Miff `miffId` (celle que le Feed montre) : l'alerte part.
    func trigger(miffId: String) -> DangerTriggerResult {
        guard currentUid() != nil, MiffId.isValid(miffId) else {
            return .notSignedIn
        }
        // Une alerte qui attend seulement une décision (refusée, limite du jour, plus vieille que 15 min) n'en interdit pas une nouvelle : elle est abandonnée, et
        // le nouveau départ adopte l'ancienne si elle a bien abouti. Une alerte en route ou en cours l'interdit (D14).
        if let current = state.miffId, !isWaitingForDecision {
            return .alreadyActive(miffId: current)
        }
        verifyJob?.cancel()
        let now = nowMillis()
        local.save(DangerAlertPointer(miffId: miffId, phase: .sending, armedAtMillis: now))
        state = .sending(miffId: miffId, armedAtMillis: now, step: .sending)
        launchSend(miffId: miffId, armedAt: now, confirmed: false)
        // La position est un bonus : l'alerte n'attend jamais cette demande et n'échoue jamais à cause d'elle.
        onSending()
        return .started
    }

    /// « Réessayer » après un refus (pas après la limite du jour : il serait refusé de nouveau).
    func retry() {
        guard case .sending(let miffId, let armedAt, let step) = state, step != .sending, step != .quota else {
            return
        }
        launchSend(miffId: miffId, armedAt: armedAt, confirmed: local.pointer()?.staleConfirmed == true)
    }

    /// « Envoyer » sur la question « Veux-tu toujours envoyer ton alerte ? ».
    func confirmStale() {
        guard case .sending(let miffId, let armedAt, let step) = state, step == .needsConfirmation else {
            return
        }
        if var pointer = local.pointer() {
            pointer.staleConfirmed = true
            local.save(pointer)
        }
        launchSend(miffId: miffId, armedAt: armedAt, confirmed: true)
    }

    /// Vrai quand l'alerte n'est pas active sur le serveur et n'attend plus qu'une décision de la personne (refusée, limite du jour, plus de 15 minutes).
    var isWaitingForDecision: Bool {
        if case .sending(_, _, let step) = state {
            return step == .refused || step == .quota || step == .needsConfirmation
        }
        return false
    }

    /// Une alerte est en route ou en cours : un maintien sur le bouton n'en démarre pas une seconde (D14). Faux pour une alerte qui n'attend qu'une décision.
    var blocksNewAlert: Bool {
        state != .idle && !isWaitingForDecision
    }

    /// « Annuler » ou la croix de la bannière : une alerte qui n'est pas partie (pas de réseau, refusée, limite du jour, plus vieille que 15 min) est
    /// ABANDONNÉE. Pas pendant qu'une écriture est en cours (elle pourrait encore aboutir). La personne est libre tout de suite (plus rien n'est montré, une
    /// nouvelle alerte peut partir, aucun crédit n'est dépensé ni rendu), mais le pointeur n'est effacé qu'APRÈS une relecture du serveur qui confirme
    /// qu'aucune alerte vivante n'existe (une écriture sans réponse a pu aboutir) : si une alerte vivante est là, elle devient ACTIVE ici (la bannière et la
    /// fin reprennent la main). Sans réseau, la vérification attend le réseau, et reprend au lancement suivant.
    func cancelPending() {
        guard case .sending(let miffId, let armedAt, let step) = state, step != .sending else {
            return
        }
        job?.cancel()
        job = nil
        local.save(DangerAlertPointer(miffId: miffId, phase: .abandoned, armedAtMillis: armedAt))
        state = .idle
        launchVerifyAbandoned(miffId: miffId)
    }

    private func launchVerifyAbandoned(miffId: String) {
        verifyJob?.cancel()
        verifyJob = Task { await self.verifyAbandoned(miffId: miffId) }
    }

    private func verifyAbandoned(miffId: String) async {
        var attempt = 0
        while attempt < Self.maxVerifyAttempts, !Task.isCancelled {
            guard let uid = currentUid() else {
                return
            }
            if !network.isOnline() {
                for await online in network.onlineChanges() where online {
                    break
                }
                continue
            }
            switch await store.readMine(uid: uid, miffId: miffId) {
            case .value(let found):
                // Une nouvelle alerte a pu partir entre-temps : elle possède le pointeur maintenant.
                guard !Task.isCancelled, state == .idle else {
                    return
                }
                let now = nowMillis()
                if let doc = found, doc.isLive(nowMillis: now) {
                    let started = doc.startedAtMillis ?? now
                    activate(miffId: miffId, startedAt: started, expiresAt: doc.expiresAtMillis, armedAt: started)
                } else {
                    local.clearPointer()
                }
                return
            case .unavailable:
                await sleep(pause(for: attempt))
                attempt += 1
            }
        }
    }

    /// La fin de l'alerte (5 s tenues sur la bannière, ou la confirmation de l'action d'accessibilité).
    func end() {
        guard case .active(let miffId, _, let expires, let step) = state, step != .ending else {
            return
        }
        if expires <= nowMillis() {
            // Déjà arrivée à sa date : rien à écrire (D19), l'alerte disparaît simplement.
            drop()
            return
        }
        setEnd(miffId: miffId, step: .ending)
        job?.cancel()
        job = Task { await self.endLoop(miffId: miffId) }
    }

    /// Chaque minute et au retour au premier plan : une alerte arrivée à sa date disparaît (rien d'écrit, rien de notifié, aucun rappel).
    func tick() {
        if case .active(_, _, let expires, _) = state, expires <= nowMillis() {
            drop()
        }
    }

    /// Les Miffs de la personne sont connues : une alerte dont la Miff n'est plus l'une d'elles (quittée, retirée, supprimée) part avec elle. Le
    /// serveur a supprimé le document dans le même lot (rien à écrire ici).
    func onMiffsKnown(_ miffIds: [String], authoritative: Bool = true) {
        // Une liste lue dans le cache du téléphone, ou passagère, ne termine JAMAIS une alerte (revue de l'Android, m8) : seule une liste confirmée par le serveur le fait.
        guard authoritative, let miffId = state.miffId, !miffIds.isEmpty, !miffIds.contains(miffId) else {
            return
        }
        drop()
    }

    /// MON partage de cette Miff s'est arrêté, ou je l'ai quittée : le document est parti dans le même lot (les règles l'exigent), l'alerte aussi.
    func onAlertRemoved(miffId: String) {
        if state.miffId == miffId {
            drop()
        }
    }

    /// Fin de session ou déconnexion : rien de mon alerte ne reste en mémoire pour le compte suivant (le pointeur est effacé par
    /// `DangerAlertLocalStore.clearAccount`).
    func onSessionEnded() {
        job?.cancel()
        job = nil
        verifyJob?.cancel()
        verifyJob = nil
        state = .idle
    }

    // MARK: - Ce qui est lu auprès du serveur

    /// Le retour au premier plan PENDANT qu'une alerte est active : UNE lecture de mon document auprès du serveur dit si elle est toujours en
    /// cours. Disparue, terminée ou arrivée à sa date : la bannière part. Injoignable : rien ne change (jamais une bannière retirée sur un doute).
    func verifyActive() async {
        guard case .active(let miffId, _, _, let step) = state, step == .idle,
              let uid = currentUid(), network.isOnline() else {
            return
        }
        let read = await store.readMine(uid: uid, miffId: miffId)
        guard case .active(let againMiff, let startedAt, let expires, let againStep) = state,
              againMiff == miffId, againStep == .idle, case .value(let found) = read else {
            return
        }
        guard let doc = found, doc.isLive(nowMillis: nowMillis()) else {
            drop()
            return
        }
        if doc.expiresAtMillis != expires {
            activate(
                miffId: miffId, startedAt: doc.startedAtMillis ?? startedAt,
                expiresAt: doc.expiresAtMillis, armedAt: startedAt
            )
        }
    }

    /// Un NOUVEAU téléphone (session unique) : mon alerte de chaque Miff est lue UNE fois auprès du serveur (1 lecture par Miff, une fois par
    /// compte sur ce téléphone) et une alerte en cours est ADOPTÉE : la bannière, la fin. Rien n'est relu aux lancements suivants (le pointeur la
    /// tient), et rien quand une alerte est déjà connue ici.
    func adoptFromServer(miffIds: [String]) async {
        guard let uid = currentUid(), local.adoptedFor() != uid else {
            return
        }
        guard state == .idle else {
            local.markAdoptedFor(uid)
            return
        }
        var complete = true
        for miffId in miffIds {
            switch await store.readMine(uid: uid, miffId: miffId) {
            case .value(let found):
                let now = nowMillis()
                if let doc = found, doc.isLive(nowMillis: now), state == .idle {
                    let started = doc.startedAtMillis ?? now
                    activate(miffId: miffId, startedAt: started, expiresAt: doc.expiresAtMillis, armedAt: started)
                    local.markAdoptedFor(uid)
                    return
                }
            case .unavailable:
                complete = false
            }
        }
        if complete {
            local.markAdoptedFor(uid)
        }
    }

    // MARK: - Les transitions

    func launchSend(miffId: String, armedAt: Int, confirmed: Bool) {
        job?.cancel()
        job = Task { await self.sendLoop(miffId: miffId, armedAt: armedAt, confirmed: confirmed) }
    }

    func setStep(miffId: String, armedAt: Int, step: DangerSendStep) {
        state = .sending(miffId: miffId, armedAtMillis: armedAt, step: step)
    }

    func setEnd(miffId: String, step: DangerEndStep) {
        if case .active(let current, let started, let expires, _) = state, current == miffId {
            state = .active(miffId: miffId, startedAtMillis: started, expiresAtMillis: expires, end: step)
        }
    }

    func activate(miffId: String, startedAt: Int, expiresAt: Int, armedAt: Int) {
        local.save(
            DangerAlertPointer(
                miffId: miffId, phase: .active, armedAtMillis: armedAt,
                startedAtMillis: startedAt, expiresAtMillis: expiresAt
            )
        )
        state = .active(miffId: miffId, startedAtMillis: startedAt, expiresAtMillis: expiresAt, end: .idle)
    }

    func drop() {
        job?.cancel()
        job = nil
        local.clearPointer()
        state = .idle
    }

    func pause(for attempt: Int) -> Int {
        attempt < retryPausesMillis.count ? retryPausesMillis[attempt] : (retryPausesMillis.last ?? 5_000)
    }
}
#endif
