#if DEBUG || INTERNAL
import FirebaseAppCheck
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import Foundation

// Tout ce que l'écran de diagnostic demande à Firebase. LECTURE SEULE : rien ici n'écrit dans Firestore.

enum Test2Result: Equatable {
    case success(profileExists: Bool, offer: OfferState)
    case signInFailed(SignInFailure)
    /// Connecté, mais les règles ont refusé la lecture des documents de l'utilisateur lui-même.
    case readDenied
    case readFailed
}

enum AppCheckOutcome: Equatable {
    case tokenObtained
    case noToken
}

protocol DiagnosticRepository {
    var firebaseProjectId: String? { get }
    func probeRulesUnauthenticated() async -> RulesProbeOutcome
    func signInAndReadOwnDocuments(email: String, password: String) async -> Test2Result
    func fetchAppCheckToken() async -> AppCheckOutcome
}

/// Garantit qu'une opération n'est « reprise » qu'une fois : par son résultat ou par le délai, le premier arrivé.
private final class OnceGate {
    private let lock = NSLock()
    private var claimed = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if claimed {
            return false
        }
        claimed = true
        return true
    }
}

/// Exécute `operation` avec un délai maximal. Les appels Firebase ne sont pas toujours interruptibles : on ne
/// les attend donc pas au-delà du délai (ils se terminent seuls en arrière-plan).
func withTimeout<T>(seconds: TimeInterval, operation: @escaping () async throws -> T) async throws -> T {
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<T, Error>) in
        let gate = OnceGate()
        let work = Task {
            do {
                let value = try await operation()
                if gate.claim() {
                    continuation.resume(returning: value)
                }
            } catch {
                if gate.claim() {
                    continuation.resume(throwing: error)
                }
            }
        }
        Task {
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            if gate.claim() {
                work.cancel()
                continuation.resume(throwing: OperationTimeoutError())
            }
        }
    }
}

final class FirebaseDiagnosticRepository: DiagnosticRepository {
    /// Identifiant qui a la forme de celui d'une Miff (20 caractères alphanumériques) mais n'existe pas.
    private static let probeMiffId = "DIAGNOSTICPROBE00000"
    private static let networkTimeout: TimeInterval = 15

    var firebaseProjectId: String? {
        FirebaseBootstrap.projectId
    }

    func probeRulesUnauthenticated() async -> RulesProbeOutcome {
        guard FirebaseBootstrap.isConfigured else {
            return .inconclusive
        }
        // Le test 1 doit être réellement anonyme : on quitte d'abord toute session précédente.
        try? Auth.auth().signOut()
        do {
            _ = try await withTimeout(seconds: Self.networkTimeout) {
                try await Firestore.firestore()
                    .collection("miffs")
                    .document(Self.probeMiffId)
                    .getDocument(source: .server)
            }
            return .rulesOpen
        } catch {
            return interpretRulesProbeFailure(error)
        }
    }

    func signInAndReadOwnDocuments(email: String, password: String) async -> Test2Result {
        guard FirebaseBootstrap.isConfigured else {
            return .readFailed
        }
        let result = await signInThenRead(email: email, password: password)
        // Quoi qu'il arrive (succès, erreur, délai), la session du compte de test n'est pas conservée.
        try? Auth.auth().signOut()
        return result
    }

    func fetchAppCheckToken() async -> AppCheckOutcome {
        guard FirebaseBootstrap.isConfigured else {
            return .noToken
        }
        do {
            // La valeur du jeton n'est jamais lue ni affichée : on constate seulement qu'il a été obtenu.
            _ = try await withTimeout(seconds: Self.networkTimeout) {
                try await AppCheck.appCheck().token(forcingRefresh: false)
            }
            return .tokenObtained
        } catch {
            return .noToken
        }
    }

    // MARK: - Test 2

    private struct OwnDocuments {
        let profileExists: Bool
        let offer: OfferState
    }

    private func signInThenRead(email: String, password: String) async -> Test2Result {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let uid: String
        do {
            uid = try await withTimeout(seconds: Self.networkTimeout) {
                try await Auth.auth().signIn(withEmail: trimmedEmail, password: password).user.uid
            }
        } catch is OperationTimeoutError {
            signOutLateSignIn()
            return .signInFailed(.network)
        } catch {
            return .signInFailed(interpretSignInError(error))
        }

        do {
            let documents = try await withTimeout(seconds: Self.networkTimeout) {
                try await Self.readOwnDocuments(uid: uid)
            }
            return .success(profileExists: documents.profileExists, offer: documents.offer)
        } catch {
            // Un refus des règles sur ses propres documents est un résultat à part : il faut le voir.
            return interpretRulesProbeFailure(error) == .rulesActive ? .readDenied : .readFailed
        }
    }

    /// Lit `users/{uid}` et `entitlements/{uid}`. Un document absent est un résultat normal.
    private static func readOwnDocuments(uid: String) async throws -> OwnDocuments {
        let database = Firestore.firestore()
        let profile = try await database.collection("users").document(uid).getDocument(source: .server)
        let entitlement = try await database.collection("entitlements").document(uid).getDocument(source: .server)
        let data = entitlement.data()
        let offer = interpretEntitlement(
            documentExists: entitlement.exists,
            active: data?["active"] as? Bool,
            expiresAt: (data?["expiresAt"] as? Timestamp)?.dateValue(),
            now: Date()
        )
        return OwnDocuments(profileExists: profile.exists, offer: offer)
    }

    /// Une connexion qui a dépassé notre délai peut quand même aboutir plus tard et ouvrir une session.
    /// Cet écouteur la ferme dès qu'elle apparaît, puis se retire.
    private func signOutLateSignIn() {
        var handle: AuthStateDidChangeListenerHandle?
        handle = Auth.auth().addStateDidChangeListener { auth, user in
            guard user != nil else {
                return
            }
            if let handle {
                auth.removeStateDidChangeListener(handle)
            }
            try? auth.signOut()
        }
    }
}
#endif
