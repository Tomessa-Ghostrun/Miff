import Foundation

/// Les messages que voient les personnes sur les écrans d'authentification (ticket §7.3). Un texte par message, aucun
/// code technique. La correspondance avec les clés du catalogue est dans les vues (`AuthMessage.textKey`).
enum AuthMessage: Equatable {
    /// Mauvais e-mail ou mot de passe, ou adresse inconnue : UN message pour les deux (critères C4 et C6).
    case invalidCredentials
    /// Connexion par e-mail seulement : le texte renvoie vers « Mot de passe oublié ? » (ticket §7.3).
    case tooManyAttempts
    /// Partout ailleurs (inscription, Google, Apple, e-mail de réinitialisation) : même idée, sans conseiller de
    /// réinitialiser un mot de passe.
    case tooManyRequests
    case emailInUse
    case otherMethod
    case network
    case unknown
    case accountUnavailable
}

extension AuthFailure {
    func toMessage() -> AuthMessage {
        switch self {
        case .invalidCredentials:
            return .invalidCredentials
        case .network:
            return .network
        case .tooManyAttempts:
            return .tooManyAttempts
        case .accountDisabled:
            return .accountUnavailable
        case .emailAlreadyInUse:
            return .emailInUse
        case .linkedToOtherMethod:
            return .otherMethod
        case .recentLoginRequired, .unknown:
            return .unknown
        }
    }
}

extension AuthMessage {
    /// Pour les écrans où « réinitialise ton mot de passe » n'aurait aucun sens comme conseil (inscription, Google,
    /// Apple, e-mail de réinitialisation).
    func withoutResetAdvice() -> AuthMessage {
        self == .tooManyAttempts ? .tooManyRequests : self
    }
}

extension SocialTokenResult {
    /// Quoi dire après la fenêtre système. La fermer ne dit RIEN (critère S7) : nil. Un jeton n'est pas un message non
    /// plus (l'appelant continue avec lui).
    var message: AuthMessage? {
        switch self {
        case .cancelled, .credential:
            return nil
        case .unavailable:
            return .unknown
        case .failed(let failure):
            return failure.toMessage()
        }
    }
}

/// Problème de champ, calculé d'après le texte (voir `AuthForm`).
func emailFieldHasError(_ email: String) -> Bool {
    !AuthForm.normalizeEmail(email).isEmpty && !AuthForm.isEmailFormatValid(email)
}
