import Combine
import Foundation

// CONFIDENTIALITÉ, pour ce fichier : aucun événement Analytics n'est envoyé par cet écran (rien sur les réglages n'est dans
// `docs/contracts/analytics-events.md`) et aucune position n'est en jeu : le bloc écrit est `analyticsConsent` (quatre clés sur un choix).

enum PrivacyMessage: Equatable {
    /// Pas de réseau : rien n'a changé (accepter exige que le compte l'enregistre).
    case network
    /// Le serveur n'a pas pris le choix. Rien n'a changé.
    case saveFailed
}

struct PrivacyUiState: Equatable {
    /// Les statistiques et les rapports de plantage sont collectés (un seul interrupteur, deux SDK).
    var analyticsEnabled = false
    var isWriting = false
    var message: PrivacyMessage?
    /// La personne a retiré son accord mais le compte ne le détient pas encore (pas de réseau à ce moment-là). La collecte est COUPÉE,
    /// l'appareil se souvient du choix (même après un redémarrage) et l'enregistre tout seul dès que le réseau revient ;
    /// « Enregistrer mon choix » essaie tout de suite.
    var withdrawalPending = false
}

/// Tout ce dont l'interrupteur a besoin.
struct PrivacyDependencies {
    let privacy: PrivacySettings
    let withdrawals: ConsentWithdrawals
    let pending: PendingWithdrawals
    let network: NetworkMonitor
    let currentUid: () -> String?
}

/// Réglages > Confidentialité, l'interrupteur « statistiques d'usage et rapports de plantage anonymes » (ticket ONB-3b, critère RG6 / B5) :
/// UN interrupteur, lu et écrit à travers le consentement du COMPTE (`analyticsConsent`). Même comportement qu'Android (`PrivacyViewModel`).
///
/// - **Accepter** est enregistré D'ABORD (en ligne seulement) ; la collecte ne commence qu'après : rien n'est jamais collecté sans que le
///   compte détienne le consentement. Si un retrait attendait encore, l'acceptation le remplace une fois que le serveur l'a pris.
/// - **Retirer** coupe la collecte TOUT DE SUITE sur ce téléphone (et efface les rapports de plantage pas encore envoyés), marque le
///   choix sur l'appareil, puis l'enregistre (`ConsentWithdrawals`). Sans réseau la marque reste, la collecte reste éteinte à chaque
///   démarrage, et le retrait est enregistré en arrière-plan quand le réseau revient : l'écran ne fait que le dire.
@MainActor
final class PrivacyViewModel: ObservableObject {
    @Published private(set) var state: PrivacyUiState

    private let dependencies: PrivacyDependencies
    private let uid: String?
    private var subscriptions: [AnyCancellable] = []

    init(dependencies: PrivacyDependencies) {
        self.dependencies = dependencies
        let me = dependencies.currentUid()
        uid = me
        state = PrivacyUiState(
            analyticsEnabled: dependencies.privacy.analyticsEnabled,
            withdrawalPending: me.map { dependencies.pending.isPending(uid: $0) } ?? false
        )
        // Les deux se suivent : un retrait enregistré en arrière-plan fait disparaître le message sans que la personne touche rien.
        subscriptions.append(
            dependencies.privacy.$analyticsEnabled.sink { [weak self] enabled in
                self?.state.analyticsEnabled = enabled
            }
        )
        subscriptions.append(
            dependencies.pending.$keys.sink { [weak self] keys in
                guard let self else {
                    return
                }
                self.state.withdrawalPending = self.uid.map { keys.contains(PendingWithdrawals.keyOf($0)) } ?? false
            }
        )
    }

    /// La ligne entière est l'interrupteur : un toucher demande l'état contraire de celui qui est dessiné.
    func toggleAnalytics() {
        setAnalytics(!state.analyticsEnabled)
    }

    func setAnalytics(_ enable: Bool) {
        guard let me = uid, !state.isWriting, dependencies.currentUid() == me else {
            return
        }
        if enable == dependencies.privacy.analyticsEnabled && !dependencies.pending.isPending(uid: me) {
            return
        }
        if enable {
            accept(me)
        } else {
            withdraw(me)
        }
    }

    /// « Enregistrer mon choix » après un retrait qui n'a pas pu être enregistré.
    func retryWithdrawal() {
        guard let me = uid, !state.isWriting, dependencies.currentUid() == me else {
            return
        }
        guard dependencies.pending.isPending(uid: me) else {
            return
        }
        state.isWriting = true
        state.message = nil
        Task { [weak self, withdrawals = dependencies.withdrawals] in
            let result = await withdrawals.retryNow(uid: me)
            self?.finishWriting(message: Self.message(for: result))
        }
    }

    // MARK: - Intérieur

    private func accept(_ me: String) {
        guard dependencies.network.isOnline() else {
            state.message = .network
            return
        }
        state.isWriting = true
        state.message = nil
        // L'écriture est suivie jusqu'au bout quoi qu'il arrive à l'écran : une collecte allumée ou coupée doit correspondre à elle.
        // La tâche tient donc ce qu'il lui faut sans dépendre de l'écran (`self` est seulement prévenu s'il existe encore).
        let privacy = dependencies.privacy
        let withdrawals = dependencies.withdrawals
        let currentUid = dependencies.currentUid
        Task { [weak self] in
            let result = await withdrawals.accept(uid: me)
            if result == .done && currentUid() == me {
                privacy.applyAccountConsent(true)
                self?.finishWriting(message: nil)
            } else {
                self?.finishWriting(message: Self.message(for: result))
            }
        }
    }

    private func withdraw(_ me: String) {
        // Tout de suite, quoi que dise le réseau : la personne a demandé que rien ne soit collecté.
        dependencies.privacy.applyAccountConsent(false)
        state.isWriting = true
        state.message = nil
        Task { [weak self, withdrawals = dependencies.withdrawals] in
            // Marqué sur l'appareil d'abord, puis enregistré ; si le compte est injoignable, la marque fait le reste.
            _ = await withdrawals.withdraw(uid: me)
            self?.state.isWriting = false
        }
    }

    private func finishWriting(message: PrivacyMessage?) {
        state.isWriting = false
        state.message = message
    }

    private static func message(for result: ProfileWriteResult) -> PrivacyMessage? {
        switch result {
        case .done:
            return nil
        case .offline:
            return .network
        case .refused, .failed:
            return .saveFailed
        }
    }
}
