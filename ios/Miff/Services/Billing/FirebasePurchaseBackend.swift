#if PREMIUM_PURCHASE
import FirebaseFunctions
import Foundation

// SÉCURITÉ, pour ce fichier : les TROIS fonctions appelables de l'achat, dans la région du projet (europe-west9). La bibliothèque joint le jeton de connexion et le jeton App
// Check à chaque appel ; le serveur REFUSE un appel sans compte connecté, sans App Check, et un compte anonyme. Rien ici n'est un secret : aucune clé n'est dans l'app, et la
// transaction signée ne voyage que dans le corps de l'appel, en HTTPS (jamais dans une adresse, un journal ou un événement). Le serveur ne prend jamais une identité dans ce
// corps : le compte est celui qui est connecté. Ce fichier n'est compilé QUE sous la condition `PREMIUM_PURCHASE`.

/// Les fonctions du serveur de Miff qui traitent les achats (`backend/functions/src/preparerAchat.ts`, `synchroniserAchat.ts`, `supprimerDonneesAbonnement.ts`).
final class FirebasePurchaseBackend: PurchaseBackend, CustomStringConvertible {
    /// Le marqueur que `ci/check-binaries.sh` cherche dans un build de publication : il y est absent tant que l'achat n'est pas allumé.
    static let probe = "PREMIUMPURCHASEPROBE"
    /// La région des fonctions (`backend/functions/src/options.ts`).
    static let region = "europe-west9"
    static let prepare = "preparerAchat"
    static let synchronize = "synchroniserAchat"
    static let purge = "supprimerDonneesAbonnement"
    private static let timeout: TimeInterval = 30

    var description: String {
        Self.probe
    }

    func preparePurchaseId() async -> PrepareResult {
        do {
            let result = try await callable(Self.prepare).call()
            let data = result.data as? [String: Any]
            guard let id = data?["purchaseId"] as? String, Self.isPurchaseId(id) else {
                return .unavailable
            }
            return .ready(purchaseId: id)
        } catch {
            return Self.isRefusal(Self.code(of: error)) ? .refused : .unavailable
        }
    }

    func sync(signedTransaction: String) async -> SyncResult {
        do {
            let body: [String: Any] = ["store": "app_store", "signedTransaction": signedTransaction]
            let result = try await callable(Self.synchronize).call(body)
            let data = result.data as? [String: Any]
            return .synced(state: data?["state"] as? String)
        } catch {
            return Self.syncFailure(Self.code(of: error))
        }
    }

    func purgeSubscriptionData() async -> Bool {
        do {
            _ = try await callable(Self.purge).call()
            return true
        } catch {
            return false
        }
    }

    private func callable(_ name: String) -> HTTPSCallable {
        let callable = Functions.functions(region: Self.region).httpsCallable(name)
        callable.timeoutInterval = Self.timeout
        return callable
    }

    // MARK: - Lecture des réponses

    /// Ce que le serveur renvoie vient du réseau : seul un UUID est accepté (il est ensuite donné à Apple comme identifiant d'achat).
    static func isPurchaseId(_ value: String) -> Bool {
        UUID(uuidString: value) != nil && value.count == 36
    }

    static func code(of error: Error) -> FunctionsErrorCode? {
        let failure = error as NSError
        guard failure.domain == FunctionsErrorDomain else {
            return nil
        }
        return FunctionsErrorCode(rawValue: failure.code)
    }

    /// Pas connecté, pas d'App Check, pas de droit : réessayer ne changerait rien.
    static func isRefusal(_ code: FunctionsErrorCode?) -> Bool {
        code == .unauthenticated || code == .permissionDenied
    }

    /// Les codes de `backend/functions/src/achatErreurs.ts`, comme la bibliothèque les rapporte.
    static func syncFailure(_ code: FunctionsErrorCode?) -> SyncResult {
        switch code {
        case .alreadyExists?:
            return .alreadyLinked
        case .resourceExhausted?:
            return .rateLimited
        case .invalidArgument?, .notFound?, .failedPrecondition?:
            return .rejected
        default:
            return .unavailable
        }
    }
}
#endif
