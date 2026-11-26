import Combine
import Foundation

/// L'ACHAT de Miff+ (ticket `docs/tickets/premium-achat-reel.md` §5, §6, §7, §8, §10), pour toute la vie de l'app (il ne dépend d'aucun écran : le résultat d'un paiement est pris
/// en compte même si l'offre a été fermée entre-temps, critère CR11). Miroir d'Android `PremiumCoordinator`.
///
/// Les règles, en bref :
///  1. L'app N'ACCORDE JAMAIS Miff+ elle-même. Un paiement n'est qu'une PREUVE envoyée au serveur (`synchroniserAchat`), qui la vérifie auprès d'Apple, la lie au compte connecté et
///     écrit `entitlements/{uid}` (le seul endroit d'où viennent les droits ; les règles interdisent à l'app de l'écrire).
///  2. Le seul identifiant donné à Apple est l'identifiant d'achat ALÉATOIRE du serveur (`preparerAchat`) : ni e-mail, ni identifiant de compte, aucune donnée personnelle.
///  3. Une transaction n'est « terminée » auprès du store qu'APRÈS l'accord du serveur : sinon le store la re-présente au prochain lancement (le filet de sécurité).
///  4. Une vérification par minute et par compte côté serveur : les appels sont espacés ici aussi (`syncSpacing`), un seul à la fois. Un achat qui n'a pas pu être vérifié (pas de
///     réseau) l'est de nouveau à l'ouverture de l'app (`reconcile`) : rien de payé n'est perdu.
///  5. « En attente » (Ask to Buy, paiement différé) n'accorde RIEN tant que le serveur ne dit pas que l'achat est payé.
///  6. Un achat qui appartient à un autre compte Miff (réponse « déjà lié ») est refusé calmement et n'est PAS renvoyé tout seul (seule une restauration explicite le refait).
///  7. « Restaurer mes achats » (`AppStore.sync`, qui demande l'identification Apple) n'est JAMAIS lancé tout seul : seulement sur le geste de la personne.
///
/// Ni jeton, ni identifiant, ni montant n'est journalisé ou envoyé dans un événement.
@MainActor
final class PremiumCoordinator: ObservableObject {
    /// Les états d'un abonnement qui allument Miff+ (ceux de `StoreState.grantsAccess`, écrits par le serveur).
    private static let grantingStates: Set<String> = ["trial", "active", "canceled", "grace"]
    /// Une vérification par minute côté serveur : un peu plus, pour les horloges.
    static let defaultSyncSpacing: TimeInterval = 61
    /// Le regard silencieux sur le store à l'ouverture de l'app : toutes les 5 minutes au plus.
    static let defaultReconcileSpacing: TimeInterval = 5 * 60

    @Published private(set) var session: PurchaseSession = .idle

    /// Dit chaque fois que Miff+ est CONFIRMÉ pour le compte (un achat, une restauration, un achat retrouvé en silence, une offre qui trouve le compte déjà abonné) : les écrans
    /// qui gardent une copie de la formule la relisent (voir `MiffDirectory.followPremiumGrants`). Rien n'est dit quand rien n'a changé pour la personne.
    let accessGranted = PassthroughSubject<Void, Never>()

    private let gateway: BillingGateway
    private let backend: PurchaseBackend
    private let subscriptions: SubscriptionRepository
    private let currentUid: () -> String?
    private let clock: PaywallClock
    private let syncSpacing: TimeInterval
    private let reconcileSpacing: TimeInterval

    private var busy = false
    private var serial = 0
    private var lastSyncAt: TimeInterval?
    private var lastReconcileAt: TimeInterval?
    private var reconcileUid: String?
    private let syncLock = AsyncLock()
    /// Gardés en mémoire seulement : les transactions que le serveur a refusées (autre compte, refus définitif) ne sont pas renvoyées par les chemins silencieux.
    private var refusedIds = Set<UInt64>()
    /// Les transactions déjà confirmées par le serveur dans cette exécution.
    private var handledIds = Set<UInt64>()
    /// Le travail en cours (achat ou restauration) : gardé pour que les tests l'attendent.
    private(set) var work: Task<Void, Never>?

    init(
        gateway: BillingGateway,
        backend: PurchaseBackend,
        subscriptions: SubscriptionRepository,
        currentUid: @escaping () -> String?,
        clock: PaywallClock = SystemPaywallClock(),
        syncSpacing: TimeInterval = PremiumCoordinator.defaultSyncSpacing,
        reconcileSpacing: TimeInterval = PremiumCoordinator.defaultReconcileSpacing
    ) {
        self.gateway = gateway
        self.backend = backend
        self.subscriptions = subscriptions
        self.currentUid = currentUid
        self.clock = clock
        self.syncSpacing = syncSpacing
        self.reconcileSpacing = reconcileSpacing
    }

    /// À appeler TOUT AU DÉBUT du lancement (IO2) : l'écoute des transactions qui arrivent hors d'un achat en cours (autre appareil, « Ask to Buy » approuvé, code, transaction
    /// restée non terminée) doit être en place avant le premier écran, car les transactions non terminées ne sont livrées qu'une fois.
    func startListening() {
        gateway.listen { [weak self] transaction in
            self?.handleUnsolicited(transaction)
        }
    }

    // MARK: - Les offres et l'état

    /// Les prix et les offres du store pour ce compte : rien n'est jamais écrit à la main.
    func loadOffers() async -> OffersResult {
        switch await gateway.loadCatalog() {
        case .loaded(let products):
            if let offers = MiffPlusOfferChoice.offers(from: products) {
                return .loaded(offers)
            }
            return .notAvailable
        case .notAvailable:
            return .notAvailable
        case .error:
            return .error
        }
    }

    /// L'état du Miff+ du compte pour les écrans (nil = illisible).
    func status() async -> SubscriptionStatus? {
        await subscriptions.load()
    }

    /// Miff+ est allumé pour ce compte (confirmé par le serveur) : prévient les écrans qui gardent une copie de la formule.
    func noteAccessGranted() {
        accessGranted.send()
    }

    /// Ouvre la gestion des abonnements du système : le moyen facile de résilier que les stores exigent (rien ne se résilie dans l'app).
    func openManagement() async {
        await gateway.openManagement()
    }

    // MARK: - Achat et restauration (un seul à la fois)

    /// Lance l'achat de la formule touchée. Ignoré tant qu'un autre travail est en cours. Le résultat arrive par `session`.
    func startPurchase(plan: PaywallPlan) {
        guard !busy else {
            return
        }
        busy = true
        session = .working(restoring: false)
        work = Task { [weak self] in
            guard let self else {
                return
            }
            let outcome = await self.runPurchase(plan: plan)
            self.finish(outcome: outcome, restore: nil)
        }
    }

    /// « Restaurer mes achats » : regarde ce que le store sait et le fait vérifier par le serveur. Le résultat arrive par `session`.
    func startRestore() {
        guard !busy else {
            return
        }
        busy = true
        session = .working(restoring: true)
        work = Task { [weak self] in
            guard let self else {
                return
            }
            let outcome = await self.runRestore()
            self.finish(outcome: nil, restore: outcome)
        }
    }

    private func finish(outcome: PurchaseOutcome?, restore: RestoreOutcome?) {
        serial += 1
        session = .finished(serial: serial, outcome: outcome, restore: restore)
        busy = false
        if outcome == .granted || restore == .found {
            noteAccessGranted()
        }
    }

    private func runPurchase(plan: PaywallPlan) async -> PurchaseOutcome {
        guard currentUid() != nil else {
            return .failed
        }
        // Un compte du store qui possède DÉJÀ Miff+ ne se réabonne jamais (Apple 3.1.2(b)) : la transaction en cours est vérifiée comme une nouvelle.
        if case .loaded(let owned) = await gateway.ownedTransactions(), let current = owned.first {
            return await verify(current)
        }
        let purchaseId: String
        switch await backend.preparePurchaseId() {
        case .ready(let id):
            purchaseId = id
        case .unavailable:
            return .network
        case .refused:
            return .failed
        }
        switch await gateway.purchase(productId: MiffPlusProducts.productId(for: plan), purchaseId: purchaseId) {
        case .purchased(let transaction):
            return await verify(transaction)
        case .pending:
            return .pending
        case .cancelled:
            return .cancelled
        case .alreadyOwned:
            return await adoptOwned()
        case .unavailable:
            return .unavailable
        case .network:
            return .network
        case .failed:
            return .failed
        }
    }

    /// « Déjà possédé » : le store a déjà cet abonnement pour le compte ; la transaction qu'il liste est vérifiée comme une nouvelle.
    private func adoptOwned() async -> PurchaseOutcome {
        guard case .loaded(let owned) = await gateway.ownedTransactions() else {
            return .network
        }
        if let current = owned.first {
            return await verify(current)
        }
        // « Déjà possédé » sans rien de listé : un abonnement SUSPENDU (le store ne le liste pas) : dit comme tel, jamais « l'achat n'a pas pu être finalisé ».
        if await serverStateIsOnHold() {
            return .onHold
        }
        return .failed
    }

    private func serverStateIsOnHold() async -> Bool {
        guard case .store(let subscription)? = await subscriptions.load() else {
            return false
        }
        return subscription.state == .onHold
    }

    private func verify(_ transaction: OwnedTransaction) async -> PurchaseOutcome {
        switch await syncSpaced(transaction.signed, patient: true) {
        case .synced(let state):
            // Le serveur a répondu : la transaction est livrée, le store peut l'oublier.
            await transaction.finish()
            return purchaseOutcome(ofState: state)
        case .alreadyLinked:
            return .otherAccount
        case .rateLimited, .unavailable:
            return .network
        case .rejected:
            return .failed
        }
    }

    private func runRestore() async -> RestoreOutcome {
        switch await gateway.syncWithStore() {
        case .done:
            break
        case .cancelled:
            return .cancelled
        case .network:
            return .network
        case .failed:
            return .failed
        }
        guard case .loaded(let owned) = await gateway.ownedTransactions() else {
            return .network
        }
        if let current = owned.first {
            // Une restauration explicite : même un achat refusé avant est renvoyé (la personne le demande).
            refusedIds.remove(current.id)
            return await restoreVerifying(current)
        }
        // Rien chez le store : le serveur connaît peut-être un abonnement (acheté sur un autre appareil ou chez l'autre store, ou suspendu, que la liste omet).
        guard let status = await subscriptions.load() else {
            return .network
        }
        if status.hasAccess {
            return .found
        }
        if case .store(let subscription) = status, subscription.state == .onHold {
            return .onHold
        }
        return .noneFound
    }

    private func restoreVerifying(_ transaction: OwnedTransaction) async -> RestoreOutcome {
        switch await syncSpaced(transaction.signed, patient: true) {
        case .synced(let state):
            await transaction.finish()
            if grantsAccess(state) {
                return .found
            }
            return state == "on_hold" ? .onHold : .noneFound
        case .alreadyLinked:
            return .otherAccount
        case .rateLimited, .unavailable:
            return .network
        case .rejected:
            return .failed
        }
    }

    // MARK: - Les chemins silencieux

    /// Appelé quand l'app s'ouvre (connecté) : regarde ce que le store détient et le fait vérifier par le serveur SEULEMENT si c'est utile : un achat existe ET le serveur ne
    /// l'accorde pas encore. C'est le filet sous une notification perdue, un paiement fait pendant que le téléphone était hors ligne, une réinstallation ou un nouveau
    /// téléphone (ticket §7, point 6). Espacé (`reconcileSpacing`) et silencieux : rien n'est montré, rien n'est réessayé en boucle, et JAMAIS d'identification Apple.
    func reconcile() async {
        guard let uid = currentUid() else {
            return
        }
        let now = clock.now()
        if reconcileUid != uid {
            reconcileUid = uid
            lastReconcileAt = nil
            refusedIds.removeAll()
        }
        if let last = lastReconcileAt, now - last < reconcileSpacing {
            return
        }
        lastReconcileAt = now
        guard !busy, case .loaded(let owned) = await gateway.ownedTransactions() else {
            return
        }
        guard let current = owned.first(where: { !refusedIds.contains($0.id) }) else {
            return
        }
        guard let status = await subscriptions.load(), !status.hasAccess else {
            return
        }
        await silentSync(current)
    }

    private func handleUnsolicited(_ transaction: OwnedTransaction) {
        // Pas de compte connecté : rien à faire ici ; la transaction reste non terminée (le store la re-présente) et `reconcile` la reprend après la connexion.
        guard currentUid() != nil, !busy, !refusedIds.contains(transaction.id) else {
            return
        }
        Task { [weak self] in
            await self?.silentSync(transaction)
        }
    }

    private func silentSync(_ transaction: OwnedTransaction) async {
        guard !handledIds.contains(transaction.id) else {
            return
        }
        // Un achat d'un autre compte, ou refusé pour de bon (produit inconnu, autre application...), n'est pas renvoyé tout seul : seule une restauration explicite le fait.
        let result = await syncSpaced(transaction.signed, patient: false)
        switch result {
        case .synced(let state):
            handledIds.insert(transaction.id)
            await transaction.finish()
            if grantsAccess(state) {
                noteAccessGranted()
            }
        case .alreadyLinked, .rejected:
            refusedIds.insert(transaction.id)
        case .rateLimited, .unavailable:
            break
        }
    }

    // MARK: - La vérification par le serveur, une à la fois, espacée

    /// UN appel à `synchroniserAchat` à la fois, au moins `syncSpacing` après le précédent (le serveur en accepte un par minute et par compte et compte chaque appel).
    /// `patient` : une réponse que la personne attend peut attendre son tour et réessayer une fois après « trop tôt » ; un chemin silencieux abandonne (il reviendra à
    /// la prochaine ouverture).
    private func syncSpaced(_ signed: String, patient: Bool) async -> SyncResult {
        await syncLock.acquire()
        defer {
            syncLock.release()
        }
        if let last = lastSyncAt {
            let wait = last + syncSpacing - clock.now()
            if wait > 0 {
                if !patient {
                    return .rateLimited
                }
                try? await clock.sleep(for: wait)
            }
        }
        // La minute du serveur commence quand il a RÉPONDU (une réponse, un refus, « trop tôt ») : un appel qui ne l'a pas atteint (pas de réseau) ne l'use pas.
        var result = await backend.sync(signedTransaction: signed)
        if result != .unavailable {
            lastSyncAt = clock.now()
        }
        if result == .rateLimited && patient {
            try? await clock.sleep(for: syncSpacing)
            result = await backend.sync(signedTransaction: signed)
            if result != .unavailable {
                lastSyncAt = clock.now()
            }
        }
        return result
    }

    private func purchaseOutcome(ofState state: String?) -> PurchaseOutcome {
        if state == "pending" {
            return .pending
        }
        if state == "on_hold" {
            return .onHold
        }
        return grantsAccess(state) ? .granted : .failed
    }

    private func grantsAccess(_ state: String?) -> Bool {
        guard let state else {
            return false
        }
        return Self.grantingStates.contains(state)
    }
}
