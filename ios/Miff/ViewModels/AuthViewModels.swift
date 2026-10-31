import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour tous les view models de ce fichier :
// - le mot de passe est un paramètre d'appel de fonction : jamais un champ, jamais dans un état d'écran, jamais journalisé.
//   Il vit dans le champ de l'écran (non sauvegardé) et part vers Firebase ;
// - l'adresse e-mail n'est pas journalisée non plus.

struct SignUpUiState: Equatable {
    var isLoading = false
    /// Montrer les erreurs de format des champs (après un premier toucher sur le bouton principal).
    var showFieldErrors = false
    var message: AuthMessage?
}

/// Écran d'inscription (ticket §3 A). Il n'y a AUCUNE case à cocher (décision du PM du 2026-10-28, risque légal et
/// risque de refus des boutiques assumés par le PM) : la ligne au-dessus du bouton principal dit que toucher « Rejoindre »
/// (ou « Continuer avec Google » / « Continuer avec Apple ») accepte les conditions, la politique de confidentialité et
/// le partage de statistiques anonymes. Le geste lui-même est l'acceptation, donc `ConsentChoice.implicit` ; rien n'est
/// envoyé ni collecté avant ce geste.
@MainActor
final class SignUpViewModel: ObservableObject {
    @Published private(set) var state = SignUpUiState()

    private let session: SessionManager
    private let network: NetworkMonitor

    init(session: SessionManager, network: NetworkMonitor) {
        self.session = session
        self.network = network
    }

    /// « Rejoindre ». `password` sert une fois et n'est pas gardé.
    func submitEmail(email: String, password: String) {
        guard !state.isLoading else {
            return
        }
        let cleanEmail = AuthForm.normalizeEmail(email)
        let formOk = AuthForm.isEmailFormatValid(cleanEmail) && AuthForm.isPasswordLongEnough(password)
        if !formOk {
            // Pas de compte, pas de requête. On dit ce qui manque, avec douceur (critères S2, S3).
            state.showFieldErrors = true
            state.message = nil
            return
        }
        guard network.isOnline() else {
            state.message = .network
            return
        }
        state.isLoading = true
        state.message = nil
        Task {
            let failure = await self.session.createAccountWithEmail(
                email: cleanEmail,
                password: password,
                consent: .implicit
            )
            self.state.isLoading = false
            self.state.message = failure?.toMessage().withoutResetAdvice()
        }
    }

    /// « Continuer avec Google » : un geste d'inscription, donc la même acceptation implicite.
    func submitGoogle(source: SocialCredentialSource) {
        submitSocial { await source.requestGoogle() }
    }

    /// « Continuer avec Apple » : idem (la ligne explicative cite aussi ce bouton sur iOS).
    func submitApple(source: SocialCredentialSource) {
        submitSocial { await source.requestApple() }
    }

    private func submitSocial(request: @escaping () async -> SocialTokenResult) {
        guard !state.isLoading else {
            return
        }
        guard network.isOnline() else {
            state.message = .network
            return
        }
        state.isLoading = true
        state.message = nil
        Task {
            let result = await request()
            var message = result.message
            if case .credential(let credential) = result {
                let failure = await self.session.signIn(with: credential, signUpConsent: .implicit)
                message = failure?.toMessage()
            }
            // Fermer la fenêtre : nil, aucun message, les champs restent tels quels.
            self.state.isLoading = false
            self.state.message = message?.withoutResetAdvice()
        }
    }
}

struct SignInUiState: Equatable {
    var isLoading = false
    var showFieldErrors = false
    var message: AuthMessage?
    /// Après un refus du panneau de consentement : une note rassurante (pas une erreur).
    var showAccountNotCreatedNote = false
}

/// Écran de connexion (ticket §3 B).
@MainActor
final class SignInViewModel: ObservableObject {
    /// Une mauvaise réponse n'est jamais montrée plus vite que cela, quelle qu'en soit la raison (adresse inconnue ou
    /// mauvais mot de passe).
    static let minWrongCredentialsMillis = 800

    @Published private(set) var state = SignInUiState()

    private let session: SessionManager
    private let network: NetworkMonitor
    private let clock: AppClock
    private let throttle: LoginThrottle

    init(session: SessionManager, network: NetworkMonitor, clock: AppClock) {
        self.session = session
        self.network = network
        self.clock = clock
        throttle = LoginThrottle(nowMillis: clock.nowMillis)
        switch session.consumeNotice() {
        case .accountNotCreated:
            state.showAccountNotCreatedNote = true
        case .accountUnavailable:
            state.message = .accountUnavailable
        case nil:
            break
        }
    }

    /// « Se connecter ». Le mot de passe sert une fois et n'est pas gardé.
    func submitEmail(email: String, password: String) {
        guard !state.isLoading else {
            return
        }
        let cleanEmail = AuthForm.normalizeEmail(email)
        if !AuthForm.isEmailFormatValid(cleanEmail) || password.isEmpty {
            state.showFieldErrors = true
            state.message = nil
            return
        }
        if let blocked = blockingMessage() {
            state.message = blocked
            state.showAccountNotCreatedNote = false
            return
        }
        state.isLoading = true
        state.message = nil
        state.showAccountNotCreatedNote = false
        Task {
            await self.performEmailSignIn(email: cleanEmail, password: password)
        }
    }

    /// Ce qui empêche d'essayer maintenant : la pause après trop d'échecs, ou l'absence de réseau.
    private func blockingMessage() -> AuthMessage? {
        if throttle.remainingPauseMillis() > 0 {
            return .tooManyAttempts
        }
        return network.isOnline() ? nil : .network
    }

    private func performEmailSignIn(email: String, password: String) async {
        let started = clock.nowMillis()
        let failure = await session.signInWithEmail(email: email, password: password)
        if failure == nil {
            throttle.recordSuccess()
        } else if failure == .invalidCredentials {
            // Mauvais mot de passe et adresse inconnue se ressemblent, durée de la réponse comprise (critères C4 et
            // C6) : un plancher sur la durée de cette seule réponse.
            throttle.recordWrongCredentials()
            let remaining = Self.minWrongCredentialsMillis - (clock.nowMillis() - started)
            if remaining > 0 {
                await clock.pause(remaining)
            }
        }
        let tooMany = failure == .invalidCredentials && throttle.remainingPauseMillis() > 0
        state.isLoading = false
        state.message = tooMany ? .tooManyAttempts : failure?.toMessage()
    }

    /// « Continuer avec Google » sur l'écran de connexion. Une personne nouvelle reçoit ensuite le panneau.
    func submitGoogle(source: SocialCredentialSource) {
        submitSocial { await source.requestGoogle() }
    }

    func submitApple(source: SocialCredentialSource) {
        submitSocial { await source.requestApple() }
    }

    private func submitSocial(request: @escaping () async -> SocialTokenResult) {
        guard !state.isLoading else {
            return
        }
        guard network.isOnline() else {
            state.message = .network
            state.showAccountNotCreatedNote = false
            return
        }
        state.isLoading = true
        state.message = nil
        state.showAccountNotCreatedNote = false
        Task {
            let result = await request()
            var message = result.message
            if case .credential(let credential) = result {
                let failure = await self.session.signIn(with: credential, signUpConsent: nil)
                message = failure?.toMessage()
            }
            self.state.isLoading = false
            self.state.message = message?.withoutResetAdvice()
        }
    }
}

struct ConsentPanelUiState: Equatable {
    var isLoading = false
    var message: AuthMessage?
}

/// Panneau d'une personne nouvelle arrivée par « Continuer avec Google » ou « Continuer avec Apple » sur l'écran de
/// connexion (ticket §7.8). Pas de case : la ligne au-dessus des boutons dit que « Continuer » accepte les conditions, la
/// politique et les statistiques (décision du 2026-10-28). « Continuer » est l'acceptation ; « Annuler » et le geste de
/// retour sont le refus.
@MainActor
final class ConsentPanelViewModel: ObservableObject {
    @Published private(set) var state = ConsentPanelUiState()

    private let session: SessionManager

    init(session: SessionManager) {
        self.session = session
    }

    /// Oublie tout message (appelé quand on quitte le panneau).
    func reset() {
        state = ConsentPanelUiState()
    }

    /// « Continuer » : le geste est l'acceptation de (a) et (b).
    func accept() {
        guard !state.isLoading else {
            return
        }
        _ = session.acceptConsentPanel(.implicit)
    }

    /// « Annuler », geste de retour, fermeture : le compte tout juste créé est supprimé et rien n'est gardé. `source`
    /// ne sert que si Firebase réclame d'abord une connexion récente.
    func refuse(source: SocialCredentialSource) {
        guard !state.isLoading else {
            return
        }
        state.isLoading = true
        state.message = nil
        Task {
            let result = await self.session.refuseConsentPanel(source: source)
            self.state.isLoading = false
            if case .failed(let failure) = result {
                self.state.message = failure.toMessage().withoutResetAdvice()
            }
        }
    }
}
