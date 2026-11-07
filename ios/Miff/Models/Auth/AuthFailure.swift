import Foundation

/// Ce que la personne était en train de faire quand Firebase a répondu par une erreur. Cela change la formulation.
enum AuthAttempt: Equatable {
    /// Connexion par e-mail et mot de passe : le SEUL cas où « mauvais identifiants » est une réponse possible.
    case signInEmail
    /// Création d'un compte par e-mail et mot de passe.
    case signUpEmail
    /// Google ou Apple (jeton échangé avec Firebase), aussi utilisé pour se ré-authentifier.
    case social
    /// Suppression d'un compte : celui tout juste créé (refus du panneau de consentement) ou le compte de la personne (AUTH-5c).
    case deleteAccount
    /// Prouver à nouveau qui on est avec le mot de passe, avant la suppression du compte (AUTH-5c). La personne est DÉJÀ connectée : un
    /// mauvais mot de passe est un simple « ce mot de passe ne correspond pas », pas une énumération de comptes.
    case reauthEmail
    /// Prouver à nouveau qui on est avec Google ou Apple (AUTH-5c).
    case reauthSocial
    /// Demander à Firebase si la session existe encore (AUTH-5c, reprise d'une suppression).
    case checkSession

    /// Les tentatives faites sur la session déjà ouverte : « utilisateur introuvable » y veut dire « le compte n'existe plus »
    /// (et non « mauvaise adresse »).
    var isAboutTheSession: Bool {
        self == .deleteAccount || self == .reauthEmail || self == .reauthSocial || self == .checkSession
    }
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
    /// Ré-authentification avec un compte Google ou Apple qui n'est pas celui de la session (AUTH-5c, critère SC11).
    case wrongAccount
    /// Firebase dit que le compte N'EXISTE PLUS (`userNotFound`, le SEUL code qui le prouve) : supprimé depuis la console ou un autre
    /// téléphone. Pour la suppression du compte, c'est « déjà fait » (critère SC28). Un jeton expiré ou révoqué n'est PAS cela.
    case accountGone
    /// La session ne peut plus servir (jeton expiré ou révoqué, mot de passe changé ailleurs) alors que le compte existe peut-être
    /// encore. JAMAIS un succès : rien ne dit « supprimé », la personne se reconnecte.
    case sessionExpired
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
        case AuthErrorNumber.userMismatch:
            return .wrongAccount
        default:
            break
        }
        if attempt.isAboutTheSession, let failure = sessionFailure(code: nsError.code) {
            return failure
        }
    }
    switch interpretSignInError(error) {
    case .invalidCredentials:
        return (attempt == .signInEmail || attempt == .reauthEmail) ? .invalidCredentials : .unknown
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

/// Sur la session ouverte : « utilisateur introuvable » est LE code qui prouve que le compte n'existe plus ; un jeton expiré ou
/// révoqué dit seulement « reconnecte-toi ».
private func sessionFailure(code: Int) -> AuthFailure? {
    switch code {
    case AuthErrorNumber.userNotFound:
        return .accountGone
    case AuthErrorNumber.userTokenExpired, AuthErrorNumber.invalidUserToken:
        return .sessionExpired
    default:
        return nil
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
    /// `authorizationCode` : code à usage unique donné par Apple en plus du jeton. Gardé seulement en mémoire, pour la
    /// révocation du jeton Apple à la suppression d'un compte (AUTH-5, à venir) ; jamais stocké ni journalisé.
    case apple(idToken: String, rawNonce: String, name: PersonNameParts?, authorizationCode: String? = nil)
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
