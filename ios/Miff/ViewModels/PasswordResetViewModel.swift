import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ : l'adresse saisie ici n'est pas secrète, mais c'est une donnée personnelle : elle reste en mémoire seulement
// (jamais sauvegardée, jamais journalisée, jamais envoyée à Analytics : cet écran n'a pas accès au traceur, donc aucun
// événement ne peut en partir).

struct PasswordResetUiState: Equatable {
    var isLoading = false
    /// Montrer l'erreur de format du champ (après un premier toucher sur le bouton principal).
    var showFieldError = false
    /// Le serveur trouve l'adresse mal formée alors que le téléphone ne l'a pas vu : mêmes mots que l'erreur locale.
    var emailRejected = false
    /// Réseau, quota ou problème inconnu. Ne dit jamais rien de l'existence d'un compte.
    var message: AuthMessage?
    /// La confirmation d'envoi est montrée. IDENTIQUE que l'adresse ait un compte ou non (critère M2).
    var sent = false
    /// Nombre d'envois acceptés : un nouvel envoi fait relire la confirmation par le lecteur d'écran (critère M2).
    var sendCount = 0
    /// Le bouton d'envoi est en pause (environ 60 s après un envoi vers CETTE adresse, critère M5).
    var resendLocked = false
}

/// Écran « Mot de passe oublié » (ticket §3 C). La personne saisit l'adresse de son compte ; l'app demande à Firebase
/// d'envoyer l'e-mail et montre UNE confirmation, la même pour toutes les adresses (aucune énumération de comptes). Le
/// nouveau mot de passe se choisit sur la page web d'AUTH-W, pas dans l'app (décision Q4).
@MainActor
final class PasswordResetViewModel: ObservableObject {
    /// Une réponse « acceptée » n'est jamais montrée plus vite que cela, quelle que soit l'adresse.
    static let minAnswerMillis = 800

    @Published private(set) var state = PasswordResetUiState()

    private let gateway: PasswordResetGateway
    private let network: NetworkMonitor
    private let cooldown: ResendCooldown
    /// Horloge monotone (temps depuis le démarrage de l'appareil), la même que la pause de `ResendCooldown`.
    private let clock: AppClock

    /// L'adresse telle que la personne l'a dans le champ maintenant (normalisée), et celle à qui l'e-mail a été envoyé.
    private var typed = ""
    private var sentTo: String?
    private var unlockTask: Task<Void, Never>?

    init(
        gateway: PasswordResetGateway,
        network: NetworkMonitor,
        cooldown: ResendCooldown,
        clock: AppClock
    ) {
        self.gateway = gateway
        self.network = network
        self.cooldown = cooldown
        self.clock = clock
    }

    /// Le texte du champ a changé (appelé aussi une fois à l'apparition de l'écran, avec l'adresse pré-remplie).
    func onEmailChanged(_ raw: String) {
        let email = normalise(raw)
        guard email != typed else {
            return
        }
        typed = email
        state.message = nil
        state.emailRejected = false
        // Une confirmation concerne UNE adresse : la modifier la retire (et la retaper la rend).
        state.sent = sentTo != nil && sentTo == typed
        state.resendLocked = cooldown.remainingMillis(for: typed) > 0
        scheduleUnlock()
    }

    /// « Envoyer » / « Renvoyer l'e-mail ».
    func submit(_ raw: String) {
        guard !state.isLoading else {
            return
        }
        // L'écran signale chaque changement ; ceci garde le view model juste même s'il n'a pas encore été prévenu.
        onEmailChanged(raw)
        let email = AuthForm.normalizeEmail(raw)
        guard AuthForm.isEmailFormatValid(email) else {
            // Aucune requête pour une adresse qui ne peut pas exister. On le dit avec douceur (critère M1).
            state.showFieldError = true
            state.message = nil
            return
        }
        if cooldown.remainingMillis(for: email) > 0 {
            state.resendLocked = true
            return
        }
        guard network.isOnline() else {
            // Message neutre, le champ garde ce qui a été saisi (critère X2). Une confirmation qui ne tient plus (ce
            // renvoi n'est pas parti) est retirée.
            sentTo = nil
            state.message = .network
            state.sent = false
            return
        }
        state.isLoading = true
        state.message = nil
        state.emailRejected = false
        Task {
            await self.send(to: email)
        }
    }

    private func send(to email: String) async {
        let started = clock.nowMillis()
        let outcome = await gateway.sendResetEmail(email)
        if outcome == .accepted {
            // « Adresse inconnue » peut répondre plus vite qu'un vrai envoi : un plancher sur la durée de la réponse,
            // pour que le temps qu'elle prend ne dise rien non plus (même idée que l'écran de connexion).
            let remaining = Self.minAnswerMillis - (clock.nowMillis() - started)
            if remaining > 0 {
                await clock.pause(remaining)
            }
        }
        switch outcome {
        case .accepted:
            cooldown.start(for: email)
            sentTo = normalise(email)
            // La personne a pu modifier le champ pendant l'attente : la confirmation concerne l'adresse envoyée.
            state.isLoading = false
            state.showFieldError = false
            state.sent = sentTo == typed
            state.sendCount += 1
            state.resendLocked = cooldown.remainingMillis(for: typed) > 0
            scheduleUnlock()
        case .emailRejected:
            sentTo = nil
            state.isLoading = false
            state.emailRejected = true
            state.sent = false
        case .failed(let failure):
            // Aucune pause n'est lancée : rien n'est parti, réessayer bientôt est très bien.
            sentTo = nil // l'ancienne confirmation ne doit pas côtoyer une erreur
            state.isLoading = false
            state.message = failure.toMessage().withoutResetAdvice()
            state.sent = false
        }
    }

    /// Réactive le bouton quand la pause de l'adresse courante se termine (pas d'horloge qui défile dans l'écran).
    private func scheduleUnlock() {
        unlockTask?.cancel()
        let remaining = cooldown.remainingMillis(for: typed)
        guard remaining > 0 else {
            return
        }
        unlockTask = Task {
            await self.clock.pause(remaining)
            if !Task.isCancelled {
                self.state.resendLocked = self.cooldown.remainingMillis(for: self.typed) > 0
            }
        }
    }

    private func normalise(_ raw: String) -> String {
        AuthForm.normalizeEmail(raw).lowercased()
    }
}
