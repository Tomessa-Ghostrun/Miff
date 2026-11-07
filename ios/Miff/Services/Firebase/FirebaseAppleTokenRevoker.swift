import FirebaseAuth
import Foundation

/// `AppleTokenRevoker` branché sur le SDK Firebase : il appelle lui-même Apple (point d'accès de révocation), avec le code d'autorisation
/// que la feuille « Se connecter avec Apple » vient de donner. Aucun serveur à nous, aucune Cloud Function, AUCUNE clé dans l'app ni
/// dans le dépôt : la clé `.p8` vit dans la console Firebase (fournisseur Apple), saisie par le PM (voir
/// `docs/dev/apple-revocation-suppression-compte.md`). Sans cette configuration, Apple refuse : l'appel rend faux, la suppression du
/// compte continue (décision Q8a du PM) et la personne sait retirer l'autorisation elle-même.
///
/// SÉCURITÉ : le code d'autorisation est à usage unique et de courte durée. Il n'est ni stocké, ni journalisé, ni mis dans un message.
struct FirebaseAppleTokenRevoker: AppleTokenRevoker {
    /// Au-delà, on considère la révocation comme non faite (le code d'Apple vit quelques minutes) et la suppression continue.
    private static let timeoutSeconds: TimeInterval = 20

    func revoke(authorizationCode: String) async -> Bool {
        guard FirebaseBootstrap.isConfigured, !authorizationCode.isEmpty else {
            return false
        }
        do {
            return try await timedOperation(seconds: Self.timeoutSeconds) {
                await Self.callFirebase(authorizationCode)
            }
        } catch {
            return false
        }
    }

    private static func callFirebase(_ authorizationCode: String) async -> Bool {
        await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            Auth.auth().revokeToken(withAuthorizationCode: authorizationCode) { error in
                continuation.resume(returning: error == nil)
            }
        }
    }
}
