import Foundation

/// Pourquoi une connexion n'a pas abouti, en termes que l'interface peut formuler avec bienveillance.
/// Aucun détail technique ne fuit vers l'utilisateur.
enum SignInFailure: Equatable {
    /// Mauvais e-mail ou mot de passe. Couvre VOLONTAIREMENT aussi « aucun compte avec cet e-mail », pour ne pas
    /// révéler quels comptes existent.
    case invalidCredentials
    case network
    case tooManyAttempts
    case accountDisabled
    case unknown
}

/// Codes d'erreur du SDK Firebase Authentication (domaine « FIRAuthErrorDomain »), valeurs stables de l'API
/// publique Objective-C. Écrits en clair pour que la logique d'interprétation reste testable sans le SDK.
enum AuthErrorNumber {
    static let domain = "FIRAuthErrorDomain"
    static let invalidCredential = 17004
    static let userDisabled = 17005
    static let emailAlreadyInUse = 17007
    static let invalidEmail = 17008
    static let wrongPassword = 17009
    static let tooManyRequests = 17010
    static let userNotFound = 17011
    static let accountExistsWithDifferentCredential = 17012
    static let requiresRecentLogin = 17014
    static let networkError = 17020
    static let credentialAlreadyInUse = 17025
}

func interpretSignInError(_ error: Error) -> SignInFailure {
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain {
        return .network
    }
    guard nsError.domain == AuthErrorNumber.domain else {
        return .unknown
    }
    switch nsError.code {
    // « mauvais mot de passe » et « compte inconnu » donnent exactement la même réponse.
    case AuthErrorNumber.invalidCredential, AuthErrorNumber.wrongPassword,
         AuthErrorNumber.userNotFound, AuthErrorNumber.invalidEmail:
        return .invalidCredentials
    case AuthErrorNumber.userDisabled:
        return .accountDisabled
    case AuthErrorNumber.tooManyRequests:
        return .tooManyAttempts
    case AuthErrorNumber.networkError:
        return .network
    default:
        return .unknown
    }
}

/// Problème détecté localement avant de contacter Firebase (aucune requête pour une saisie manifestement vide
/// ou mal formée).
enum CredentialsInputProblem: Equatable {
    case emailEmpty
    case emailInvalid
    case passwordEmpty
    case tooLong
}

enum CredentialsInput {
    private static let maxEmailLength = 254
    private static let maxPasswordLength = 1024

    /// Volontairement large (Firebase est le vrai validateur) : quelque chose@quelque-chose.tld, sans espace.
    private static let emailShape = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"

    /// Même contrôle de forme que `check`, utilisable seul (formulaires d'inscription et de connexion).
    static func looksLikeEmail(_ email: String) -> Bool {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.range(of: emailShape, options: .regularExpression) != nil
    }

    static func check(email: String, password: String) -> CredentialsInputProblem? {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return .emailEmpty
        }
        if password.isEmpty {
            return .passwordEmpty
        }
        if email.count > maxEmailLength || password.count > maxPasswordLength {
            return .tooLong
        }
        if trimmed.range(of: emailShape, options: .regularExpression) == nil {
            return .emailInvalid
        }
        return nil
    }
}
