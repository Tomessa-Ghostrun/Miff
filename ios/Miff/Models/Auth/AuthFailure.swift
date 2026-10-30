import Foundation

/// Ce que la personne était en train de faire quand Firebase a répondu par une erreur. Cela change la formulation.
enum AuthAttempt: Equatable {
    /// Connexion par e-mail et mot de passe : le SEUL cas où « mauvais identifiants » est une réponse possible.
    case signInEmail
    /// Création d'un compte par e-mail et mot de passe.
    case signUpEmail
    /// Google ou Apple (jeton échangé avec Firebase), aussi utilisé pour se ré-authentifier.
    case social
    /// Suppression du compte tout juste créé (refus du panneau de consentement).
    case deleteAccount
}

/// Pourquoi une étape d'authentification n'a pas abouti, en termes que l'interface peut formuler avec bienveillance.
/// Aucun détail technique ne fuit.
enum AuthFailure: Equatable {
    /// Mauvais e-mail ou mot de passe. Couvre VOLONTAIREMENT aussi « aucun compte » (ticket §7.3, critères C4 et C6).
    case invalidCredentials
    case network
    case tooManyAttempts
    case accountDisabled
    /// Inscription avec une adresse qui a déjà un compte. N'est montré qu'à l'inscription (ticket R3).
    case emailAlreadyInUse
    /// L'adresse appartient à un compte créé avec un autre mode de connexion.
    case linkedToOtherMethod
    /// Firebase veut une connexion récente avant de supprimer le compte.
    case recentLoginRequired
    case unknown
}

/// Transforme n'importe quelle erreur des appels d'authentification en `AuthFailure`. N'expose jamais de code ni
/// de message.
///
/// Seule une connexion par e-mail peut répondre `invalidCredentials` : pour les autres tentatives, « identifiant
/// invalide » ne veut pas dire « mauvais mot de passe » et le dire induirait en erreur (un problème de jeton Google
/// n'est pas le mot de passe de la personne).
func interpretAuthError(_ error: Error, attempt: AuthAttempt) -> AuthFailure {
    let nsError = error as NSError
    if nsError.domain == AuthErrorNumber.domain {
        switch nsError.code {
        case AuthErrorNumber.requiresRecentLogin:
            return .recentLoginRequired
        case AuthErrorNumber.emailAlreadyInUse:
            return attempt == .signUpEmail ? .emailAlreadyInUse : .linkedToOtherMethod
        case AuthErrorNumber.accountExistsWithDifferentCredential, AuthErrorNumber.credentialAlreadyInUse:
            return .linkedToOtherMethod
        default:
            break
        }
    }
    switch interpretSignInError(error) {
    case .invalidCredentials:
        return attempt == .signInEmail ? .invalidCredentials : .unknown
    case .network:
        return .network
    case .tooManyAttempts:
        return .tooManyAttempts
    case .accountDisabled:
        return .accountDisabled
    case .unknown:
        return .unknown
    }
}

/// Résultat d'une connexion ou d'une inscription. Porte un identifiant d'utilisateur, jamais un mot de passe ni un jeton.
enum AuthOutcome: Equatable {
    case success(uid: String, isNewUser: Bool)
    case failure(AuthFailure)
}

/// Nom donné par Apple, UNE SEULE FOIS, à la première connexion : il ne sert qu'à pré-remplir l'étape profil.
struct PersonNameParts: Equatable {
    let givenName: String?
    let familyName: String?
}

/// Ce que la fenêtre système « Continuer avec Google » ou « Continuer avec Apple » a rendu.
/// Contient des jetons : jamais journalisé, jamais stocké, jamais affiché (la description est volontairement vide).
enum SocialCredential: Equatable {
    case google(idToken: String, accessToken: String)
    case apple(idToken: String, rawNonce: String, name: PersonNameParts?)
}

extension SocialCredential: CustomStringConvertible {
    var description: String {
        "SocialCredential(masqué)"
    }
}

enum SocialTokenResult: Equatable {
    case credential(SocialCredential)
    /// La personne a fermé la fenêtre : AUCUN message (critère S7).
    case cancelled
    /// La connexion ne peut même pas démarrer (pas de client OAuth dans la configuration...).
    case unavailable
    case failed(AuthFailure)
}
