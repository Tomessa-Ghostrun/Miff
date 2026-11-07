import FirebaseAuth
import Foundation

/// `sendPasswordResetEmail` de Firebase Authentication, SANS adresse d'action dans le code : l'adresse de la page web où
/// l'on choisit le nouveau mot de passe (AUTH-W, `/reset-password`) se règle dans la console Firebase (Authentication >
/// Modèles > Réinitialisation du mot de passe > personnaliser l'URL d'action) une fois cette page déployée. Rien ne la
/// concerne dans l'app.
///
/// La langue de l'e-mail suit la langue du téléphone (`useAppLanguage`) : le lien de l'e-mail porte alors `lang=fr` ou
/// `lang=en`, que la page web utilise. L'adresse va du formulaire à Firebase et nulle part ailleurs : jamais stockée,
/// jamais journalisée.
final class FirebasePasswordResetGateway: PasswordResetGateway {
    func sendResetEmail(_ email: String) async -> PasswordResetOutcome {
        guard FirebaseBootstrap.isConfigured else {
            return .failed(.unknown)
        }
        let auth = Auth.auth()
        auth.useAppLanguage()
        do {
            try await auth.sendPasswordReset(withEmail: email)
            return .accepted
        } catch {
            return interpretPasswordResetError(error)
        }
    }
}
