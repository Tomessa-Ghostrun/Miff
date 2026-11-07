import Foundation

// « Mot de passe oublié » (ticket Authentification §3 C, §7.3, §7.4, §7.5 et critères M1 à M5).
//
// Règle centrale : ce que l'app DIT et FAIT ne dépend JAMAIS de l'existence d'un compte pour l'adresse saisie
// (aucune énumération des adresses). C'est décidé ici, en logique pure testée.

/// Ce qui s'est passé quand on a demandé l'envoi de l'e-mail de réinitialisation.
enum PasswordResetOutcome: Equatable {
    /// La demande a été prise en compte. Cela ne dit RIEN de l'existence d'un compte : adresse inconnue, compte
    /// désactivé ou vrai compte aboutissent ici, pour que l'écran ne puisse pas les distinguer.
    case accepted
    /// Le serveur trouve lui-même l'adresse mal formée (le contrôle local passe normalement en premier).
    case emailRejected
    /// Réseau, quota, problème inconnu : rien n'a été envoyé, et la personne en est informée avec douceur.
    case failed(AuthFailure)
}

/// Transforme n'importe quelle erreur de `sendPasswordResetEmail` en `PasswordResetOutcome`. N'expose jamais de code
/// ni de message.
///
/// « Aucun utilisateur » et « compte désactivé » sont `accepted` EXPRÈS : Firebase y répond par une erreur quand sa
/// protection contre l'énumération est éteinte, et montrer autre chose dirait à un inconnu quelles adresses ont un
/// compte (ticket §7.3, critères M2 et C6). Protection allumée, Firebase répond déjà « fait » dans les deux cas.
func interpretPasswordResetError(_ error: Error) -> PasswordResetOutcome {
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain {
        return .failed(.network)
    }
    guard nsError.domain == AuthErrorNumber.domain else {
        return .failed(.unknown)
    }
    switch nsError.code {
    case AuthErrorNumber.userNotFound, AuthErrorNumber.userDisabled:
        return .accepted
    case AuthErrorNumber.invalidEmail:
        return .emailRejected
    case AuthErrorNumber.tooManyRequests:
        return .failed(.tooManyAttempts)
    case AuthErrorNumber.networkError:
        return .failed(.network)
    default:
        return .failed(.unknown)
    }
}

/// Pause de confort entre deux envois de l'e-mail (ticket §7.4, critère M5 : environ 60 secondes). Ce N'EST PAS une
/// protection de sécurité (elle vit dans l'app : un client modifié la saute) : la vraie protection est le quota de
/// Firebase et App Check.
///
/// La pause est PAR ADRESSE : qui corrige une faute de frappe n'attend pas pour l'adresse erronée, alors que la même
/// adresse (quelles que soient la casse et les espaces) reste en pause. Seule la dernière adresse est retenue, en
/// mémoire, jamais écrite sur le disque ni journalisée. Elle est tenue par le conteneur de l'app pour que quitter
/// l'écran puis revenir ne donne pas un renvoi gratuit.
final class ResendCooldown {
    static let defaultDurationMillis = 60_000

    private let nowMillis: () -> Int
    private let durationMillis: Int
    private var address: String?
    private var startedAt = 0

    init(nowMillis: @escaping () -> Int, durationMillis: Int = ResendCooldown.defaultDurationMillis) {
        self.nowMillis = nowMillis
        self.durationMillis = durationMillis
    }

    /// 0 quand un envoi vers `email` est permis.
    func remainingMillis(for email: String) -> Int {
        let current = key(email)
        guard !current.isEmpty, current == address else {
            return 0
        }
        let remaining = startedAt + durationMillis - nowMillis()
        return min(max(remaining, 0), durationMillis)
    }

    func start(for email: String) {
        address = key(email)
        startedAt = nowMillis()
    }

    private func key(_ email: String) -> String {
        AuthForm.normalizeEmail(email).lowercased()
    }
}
