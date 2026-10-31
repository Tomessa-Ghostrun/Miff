import FirebaseFirestore
import Foundation

/// Les deux lectures peu coûteuses qui décident où s'ouvre l'app (ticket §5). Lecture seule.
final class FirestoreAccountRepository: AccountRepository {
    private static let readTimeoutSeconds: TimeInterval = 15

    func load(uid: String) async -> AccountLoad {
        guard FirebaseBootstrap.isConfigured else {
            return .unavailable
        }
        do {
            return try await timedOperation(seconds: Self.readTimeoutSeconds) {
                try await Self.read(uid: uid)
            }
        } catch {
            // Hors ligne, refus, lenteur : la session est gardée, la personne peut réessayer.
            return .unavailable
        }
    }

    private static func read(uid: String) async throws -> AccountLoad {
        // L'instance Firestore est résolue ICI, jamais gardée : elle est remplacée après une purge du cache.
        let user = Firestore.firestore().collection("users").document(uid)
        let profile = try await user.getDocument()
        let exists = profile.exists
        // Les adhésions ne sont lues qu'une fois le profil présent : deux lectures au plus (ticket §6.2, RT7).
        var hasMembership = false
        if exists {
            let memberships = try await user.collection("memberships").limit(to: 1).getDocuments()
            hasMembership = !memberships.isEmpty
        }
        let data = profile.data()
        let consent = data?["analyticsConsent"] as? [String: Any]
        return .loaded(
            AccountSnapshot.fromProfileFields(
                profileExists: exists,
                hasMembership: hasMembership,
                analyticsConsentEnabled: (consent?["enabled"] as? Bool) == true,
                phone: data?["phone"],
                termsVersion: data?["termsVersion"]
            )
        )
    }
}

/// Purge du cache local de Firestore (ticket §7.6, contrat de backend/data-model.md §8 « Purge du cache local »).
final class FirestoreCachePurger: CachePurger {
    static let keyPurgePending = "firestore_purge_pending"
    private static let flushTimeoutSeconds: TimeInterval = 5

    private let flags: BooleanStore

    init(flags: BooleanStore) {
        self.flags = flags
    }

    func flushPendingWrites() async {
        guard FirebaseBootstrap.isConfigured else {
            return
        }
        // Au mieux : la déconnexion doit continuer quoi qu'il arrive.
        _ = try? await timedOperation(seconds: Self.flushTimeoutSeconds) {
            try await Firestore.firestore().waitForPendingWrites()
        }
    }

    func purge() async -> Bool {
        flags.set(true, forKey: Self.keyPurgePending)
        guard FirebaseBootstrap.isConfigured else {
            // Rien à purger : Firestore n'a jamais été démarré.
            flags.set(false, forKey: Self.keyPurgePending)
            return true
        }
        do {
            // La MÊME instance pour les deux appels : `clearPersistence()` n'est permis que sur un client arrêté.
            let client = Firestore.firestore()
            try await client.terminate()
            try await client.clearPersistence()
            flags.set(false, forKey: Self.keyPurgePending)
            return true
        } catch {
            return false
        }
    }

    func markPurgePending() {
        flags.set(true, forKey: Self.keyPurgePending)
    }

    func purgeIfPending() async {
        if flags.value(forKey: Self.keyPurgePending, default: false) {
            _ = await purge()
        }
    }
}
