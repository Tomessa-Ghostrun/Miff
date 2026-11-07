import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : l'adresse e-mail est affichée et envoyée à Firebase pour le message de réinitialisation, jamais copiée
// ailleurs (ni Firestore, ni événement Analytics, ni journal). Aucun mot de passe ne passe ici : le nouveau se choisit sur la page web.

/// Comment la personne se connecte : seul un compte avec mot de passe peut en changer un.
enum AccountSignIn: Equatable {
    case password
    case google
    case apple

    init(method: AnalyticsEvent.Method?) {
        switch method {
        case .google:
            self = .google
        case .apple:
            self = .apple
        case .email, .none:
            self = .password
        }
    }
}

enum PasswordEmailStatus: Equatable {
    case idle
    case sending
    case sent
}

struct AccountUiState: Equatable {
    /// Montrée, jamais copiée. nil quand le fournisseur n'en a donné aucune : la ligne est alors absente.
    var email: String?
    var signIn: AccountSignIn
    var confirmingSignOut = false
    var isSigningOut = false
    var passwordStatus: PasswordEmailStatus = .idle
    /// Problème de réseau, de quota ou inconnu à l'envoi : jamais rien sur le compte lui-même.
    var passwordMessage: AuthMessage?
    /// Le bouton est en pause (environ 60 s après un envoi), comme sur l'écran « Mot de passe oublié ».
    var passwordLocked = false

    /// Un mot de passe ne se change que sur un compte qui en a un.
    var canChangePassword: Bool {
        guard signIn == .password, let email else {
            return false
        }
        return !email.isEmpty
    }
}

/// Réglages > Mon compte (ticket Profil / Réglages, critères B1 à B4, décision Q5) : l'e-mail en lecture seule, « Changer mon mot de
/// passe » qui envoie l'e-mail de réinitialisation d'AUTH-4 (compte avec mot de passe seulement), « Se déconnecter » avec une
/// confirmation qui dit honnêtement ce que cela change pour les proches. La suppression du compte est un écran à part (AUTH-5c/d).
@MainActor
final class AccountViewModel: ObservableObject {
    @Published private(set) var state: AccountUiState

    private let signOutAction: () async -> Void
    private let passwordReset: PasswordResetGateway
    private let network: NetworkMonitor
    private let cooldown: ResendCooldown
    private var unlockTask: Task<Void, Never>?

    init(
        email: String?,
        signIn: AccountSignIn,
        signOut: @escaping () async -> Void,
        passwordReset: PasswordResetGateway,
        network: NetworkMonitor,
        cooldown: ResendCooldown
    ) {
        state = AccountUiState(email: email, signIn: signIn)
        signOutAction = signOut
        self.passwordReset = passwordReset
        self.network = network
        self.cooldown = cooldown
        refreshLock()
    }

    deinit {
        unlockTask?.cancel()
    }

    // MARK: - Déconnexion

    func requestSignOut() {
        guard !state.isSigningOut else {
            return
        }
        state.confirmingSignOut = true
    }

    func cancelSignOut() {
        guard !state.isSigningOut else {
            return
        }
        state.confirmingSignOut = false
    }

    func confirmSignOut() {
        guard !state.isSigningOut else {
            return
        }
        state.confirmingSignOut = false
        state.isSigningOut = true
        Task { [weak self, signOutAction] in
            await signOutAction()
            // Une déconnexion qui réussit remplace tout l'écran ; une qui échoue durablement (Trousseau illisible) laisse la personne
            // ici : le bouton redevient utilisable pour réessayer.
            self?.state.isSigningOut = false
        }
    }

    // MARK: - Mot de passe

    /// « Changer mon mot de passe » / « Renvoyer l'e-mail ».
    func changePassword() {
        guard state.canChangePassword, let email = state.email else {
            return
        }
        guard state.passwordStatus != .sending, !state.isSigningOut else {
            return
        }
        if cooldown.remainingMillis(for: email) > 0 {
            state.passwordLocked = true
            return
        }
        guard network.isOnline() else {
            // Message neutre : rien n'a été envoyé.
            state.passwordMessage = .network
            return
        }
        state.passwordStatus = .sending
        state.passwordMessage = nil
        Task { [weak self, passwordReset] in
            let outcome = await passwordReset.sendResetEmail(email)
            self?.finishSending(outcome, email: email)
        }
    }

    private func finishSending(_ outcome: PasswordResetOutcome, email: String) {
        switch outcome {
        case .accepted:
            cooldown.start(for: email)
            state.passwordStatus = .sent
            refreshLock()
        case .emailRejected:
            state.passwordStatus = .idle
            state.passwordMessage = .unknown
        case .failed(let failure):
            // Rien n'est parti : pas de pause, réessayer bientôt est possible.
            state.passwordStatus = .idle
            state.passwordMessage = failure.toMessage().withoutResetAdvice()
        }
    }

    /// Réactive le bouton quand la pause se termine (aucune horloge qui défile à l'écran).
    private func refreshLock() {
        guard let email = state.email else {
            return
        }
        unlockTask?.cancel()
        let remaining = cooldown.remainingMillis(for: email)
        state.passwordLocked = remaining > 0
        guard remaining > 0 else {
            return
        }
        unlockTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(remaining) * 1_000_000)
            guard !Task.isCancelled else {
                return
            }
            self?.refreshLock()
        }
    }
}
