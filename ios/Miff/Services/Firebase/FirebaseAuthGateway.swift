import FirebaseAuth
import FirebaseCore
import Foundation
import GoogleSignIn

/// `AuthGateway` branché sur Firebase Authentication. Le mot de passe va du formulaire à Firebase et nulle part
/// ailleurs : jamais stocké, jamais journalisé, jamais mis dans un message d'erreur de l'app.
///
/// Chaque appel garde `FirebaseBootstrap.isConfigured` : sans configuration Firebase (poste sans fichier de
/// configuration, tests unitaires) on ne touche pas au SDK, qui lèverait une exception.
final class FirebaseAuthGateway: AuthGateway {
    /// Nom donné par Apple à la première connexion (une seule fois) : gardé en mémoire SEULEMENT, pour pré-remplir
    /// l'étape profil. Jamais écrit sur le disque, jamais journalisé.
    private var appleName: PersonNameParts?

    var currentUid: String? {
        guard FirebaseBootstrap.isConfigured else {
            return nil
        }
        return Auth.auth().currentUser?.uid
    }

    func uidChanges() -> AsyncStream<String?> {
        AsyncStream { continuation in
            guard FirebaseBootstrap.isConfigured else {
                continuation.finish()
                return
            }
            // Un changement à la fois, sans répétition (Firebase annonce aussi l'état courant à l'inscription).
            var hasEmitted = false
            var lastUid: String?
            let handle = Auth.auth().addStateDidChangeListener { _, user in
                let uid = user?.uid
                if hasEmitted && uid == lastUid {
                    return
                }
                hasEmitted = true
                lastUid = uid
                continuation.yield(uid)
            }
            continuation.onTermination = { _ in
                Auth.auth().removeStateDidChangeListener(handle)
            }
        }
    }

    func signInWithEmail(email: String, password: String) async -> AuthOutcome {
        await run(.signInEmail) {
            try await Auth.auth().signIn(withEmail: email, password: password)
        }
    }

    func createAccountWithEmail(email: String, password: String) async -> AuthOutcome {
        await run(.signUpEmail) {
            try await Auth.auth().createUser(withEmail: email, password: password)
        }
    }

    func signIn(with credential: SocialCredential) async -> AuthOutcome {
        if case .apple(_, _, let name) = credential {
            appleName = name
        }
        let firebaseCredential = firebaseCredential(for: credential)
        return await run(.social) {
            try await Auth.auth().signIn(with: firebaseCredential)
        }
    }

    func reauthenticate(with credential: SocialCredential) async -> AuthFailure? {
        guard FirebaseBootstrap.isConfigured, let user = Auth.auth().currentUser else {
            return nil
        }
        let firebaseCredential = firebaseCredential(for: credential)
        do {
            _ = try await user.reauthenticate(with: firebaseCredential)
            return nil
        } catch {
            return interpretAuthError(error, attempt: .social)
        }
    }

    func deleteCurrentAccount() async -> AuthFailure? {
        guard FirebaseBootstrap.isConfigured, let user = Auth.auth().currentUser else {
            return nil
        }
        do {
            try await user.delete()
            return nil
        } catch {
            return interpretAuthError(error, attempt: .deleteAccount)
        }
    }

    func signInMethod() -> AnalyticsEvent.Method? {
        guard FirebaseBootstrap.isConfigured, let user = Auth.auth().currentUser else {
            return nil
        }
        let providers = user.providerData.map { $0.providerID }
        if providers.contains("google.com") {
            return .google
        }
        if providers.contains("apple.com") {
            return .apple
        }
        return .email
    }

    func displayName() -> String? {
        guard FirebaseBootstrap.isConfigured else {
            return nil
        }
        if let name = Auth.auth().currentUser?.displayName, !name.isEmpty {
            return name
        }
        let parts = [appleName?.givenName, appleName?.familyName].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }

    func signOut() {
        appleName = nil
        // Le SDK Google garde aussi sa propre session : sans ceci, le choix du compte Google ne serait plus proposé.
        GIDSignIn.sharedInstance.signOut()
        guard FirebaseBootstrap.isConfigured else {
            return
        }
        try? Auth.auth().signOut()
    }

    // Aucun délai de notre part exprès : une requête abandonnée par un délai peut encore aboutir plus tard et ouvrir une
    // session que personne ne regarde. Firebase échoue vite hors ligne ; les appelants font aller l'opération jusqu'au bout
    // (voir `SessionManager`).
    private func run(_ attempt: AuthAttempt, _ call: () async throws -> AuthDataResult) async -> AuthOutcome {
        guard FirebaseBootstrap.isConfigured else {
            return .failure(.unknown)
        }
        do {
            let result = try await call()
            return .success(uid: result.user.uid, isNewUser: result.additionalUserInfo?.isNewUser ?? false)
        } catch {
            return .failure(interpretAuthError(error, attempt: attempt))
        }
    }

    private func firebaseCredential(for credential: SocialCredential) -> AuthCredential {
        switch credential {
        case .google(let idToken, let accessToken):
            return GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)
        case .apple(let idToken, let rawNonce, let name):
            var components = PersonNameComponents()
            components.givenName = name?.givenName
            components.familyName = name?.familyName
            return OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: rawNonce, fullName: components)
        }
    }
}
