import AuthenticationServices
import CryptoKit
import FirebaseCore
import Foundation
import GoogleSignIn
import Security
import UIKit

/// La fenêtre du dessus, celle sur laquelle les feuilles système (Google, Apple) se présentent.
@MainActor
enum PresentationAnchor {
    static func keyWindow() -> UIWindow? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let active = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        return active?.windows.first { $0.isKeyWindow } ?? active?.windows.first
    }

    static func topViewController() -> UIViewController? {
        var top = keyWindow()?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

/// « Continuer avec Google » et « Continuer avec Apple » : les fenêtres du système. Les jetons ne vont qu'à Firebase
/// Authentication ; ils ne sont ni stockés ni journalisés. Un exemplaire par appel : aucun état partagé entre deux
/// demandes.
@MainActor
final class SystemSocialCredentialSource: SocialCredentialSource {
    func requestGoogle() async -> SocialTokenResult {
        // Le client OAuth vient du fichier de configuration Firebase (recréé en CI depuis le secret, jamais dans le dépôt).
        // Sans lui (configuration générée avant l'activation de Google), la connexion ne peut même pas démarrer.
        guard FirebaseBootstrap.isConfigured,
              let clientID = FirebaseApp.app()?.options.clientID, !clientID.isEmpty,
              let presenter = PresentationAnchor.topViewController() else {
            return .unavailable
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        // Appel fait ici, sur le fil principal (une feuille se présente) ; le résultat revient par continuation.
        let outcome: Result<GIDSignInResult, Error> = await withCheckedContinuation { continuation in
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                if let result {
                    continuation.resume(returning: .success(result))
                } else {
                    continuation.resume(returning: .failure(error ?? CocoaError(.userCancelled)))
                }
            }
        }
        switch outcome {
        case .success(let result):
            guard let idToken = result.user.idToken?.tokenString else {
                return .failed(.unknown)
            }
            return .credential(.google(idToken: idToken, accessToken: result.user.accessToken.tokenString))
        case .failure(let error):
            return SocialSignInErrors.googleResult(for: error)
        }
    }

    func requestApple() async -> SocialTokenResult {
        await AppleAuthorizationRunner().run()
    }
}

/// Une demande « Continuer avec Apple » : feuille système, jeton à usage unique (nonce), nom donné UNE SEULE FOIS par
/// Apple (à la première connexion).
final class AppleAuthorizationRunner: NSObject, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<SocialTokenResult, Never>?
    private var rawNonce = ""
    private var controller: ASAuthorizationController?
    private var anchor: ASPresentationAnchor?

    @MainActor
    func run() async -> SocialTokenResult {
        let nonce = NonceGenerator.make { count in
            var bytes = [UInt8](repeating: 0, count: count)
            let status = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
            return status == errSecSuccess ? bytes : []
        }
        guard nonce.count == 32, let window = PresentationAnchor.keyWindow() else {
            return .failed(.unknown)
        }
        rawNonce = nonce
        anchor = window
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
        let authorization = ASAuthorizationController(authorizationRequests: [request])
        authorization.delegate = self
        authorization.presentationContextProvider = self
        controller = authorization
        return await withCheckedContinuation { continuation in
            lock.lock()
            self.continuation = continuation
            lock.unlock()
            authorization.performRequests()
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        anchor ?? ASPresentationAnchor()
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard let apple = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = apple.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            finish(.failed(.unknown))
            return
        }
        var name: PersonNameParts?
        if let fullName = apple.fullName, fullName.givenName != nil || fullName.familyName != nil {
            name = PersonNameParts(givenName: fullName.givenName, familyName: fullName.familyName)
        }
        finish(.credential(.apple(idToken: idToken, rawNonce: rawNonce, name: name)))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish(SocialSignInErrors.appleResult(for: error))
    }

    private func finish(_ result: SocialTokenResult) {
        lock.lock()
        let pending = continuation
        continuation = nil
        lock.unlock()
        pending?.resume(returning: result)
        rawNonce = ""
    }

    /// Empreinte SHA-256 en hexadécimal : c'est ce qu'Apple reçoit ; la chaîne d'origine va à Firebase.
    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
