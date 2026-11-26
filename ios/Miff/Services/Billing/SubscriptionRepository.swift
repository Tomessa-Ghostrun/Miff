import Foundation

/// La lecture de `entitlements/{uid}` derrière un protocole, pour que la logique se teste avec un double.
protocol SubscriptionStore {
    /// Le document, ou nil s'il n'y en a pas. Lève quand le serveur est injoignable ou refuse.
    func read(uid: String) async throws -> EntitlementDocument?
}

/// Ce que l'app sait du Miff+ du compte connecté (tickets ABO-1 et `premium-achat-reel.md` §8) : UNE lecture de `entitlements/{uid}` chaque fois qu'on le demande (un document,
/// quota gratuit), aucun écouteur, aucune boucle d'essais. On le demande à l'ouverture des Réglages et de l'offre, après un achat ou une restauration, et à l'ouverture de l'app.
final class SubscriptionRepository {
    /// Un téléphone sans réseau peut attendre avant d'échouer : passé ce délai, l'écran n'est pas retenu.
    static let readTimeout: TimeInterval = 8

    private let store: SubscriptionStore
    private let currentUid: () -> String?
    private let nowMillis: () -> Int
    private let timeout: TimeInterval

    init(
        store: SubscriptionStore,
        currentUid: @escaping () -> String?,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        timeout: TimeInterval = SubscriptionRepository.readTimeout
    ) {
        self.store = store
        self.currentUid = currentUid
        self.nowMillis = nowMillis
        self.timeout = timeout
    }

    /// L'état, ou nil quand il n'a pas pu être lu : hors ligne, refusé, trop lent, déconnecté, ou un autre compte le temps de la réponse. Ce n'est PAS la même chose que
    /// `.free` (pas de document : le serveur l'a dit) : une lecture ratée n'est jamais prise pour « gratuit », pour qu'un écran ne dise jamais à un abonné qu'il n'a rien.
    func load() async -> SubscriptionStatus? {
        guard let uid = currentUid() else {
            return nil
        }
        let store = self.store
        let document: EntitlementDocument?
        do {
            document = try await timedOperation(seconds: timeout) { try await store.read(uid: uid) }
        } catch {
            return nil
        }
        guard currentUid() == uid else {
            return nil
        }
        return interpretSubscription(document, nowMillis: nowMillis())
    }
}
