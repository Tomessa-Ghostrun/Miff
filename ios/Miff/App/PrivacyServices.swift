import Foundation

/// Les services du consentement « statistiques d'usage et rapports de plantage » (ticket ONB-3b) : les retraits que le compte ne connaît pas
/// encore, gardés sur l'appareil, et ce qui les enregistre dès que le réseau le permet. Rien ici n'ouvre Firestore ni ne collecte quoi que ce
/// soit : l'écriture ne part que sur un geste de la personne (Réglages > Confidentialité) ou pour reprendre un retrait déjà demandé.
@MainActor
struct PrivacyServices {
    /// Les marques de retrait en attente (un fichier privé, exclu des sauvegardes).
    let pending: PendingWithdrawals
    /// Décide de ce qui arrive à un retrait : marquer, enregistrer, reprendre en arrière-plan.
    let withdrawals: ConsentWithdrawals

    static func make(network: NetworkMonitor, currentUid: @escaping () -> String?) -> PrivacyServices {
        // L'heure du téléphone sert seulement à ranger les marques (la plus ancienne est oubliée au-delà de 20 comptes) : jamais envoyée.
        let pending = PendingWithdrawals(
            storage: WithdrawalFileStorage(),
            nowMillis: { Int(Date().timeIntervalSince1970 * 1000) }
        )
        let withdrawals = ConsentWithdrawals(
            pending: pending,
            consents: FirestoreAnalyticsConsentRepository(),
            network: network,
            currentUid: currentUid
        )
        return PrivacyServices(pending: pending, withdrawals: withdrawals)
    }

    /// Le compte vient d'être lu (connexion ou démarrage) : un retrait qui l'attend est enregistré en arrière-plan.
    func resume(uid: String) {
        withdrawals.resume(uid: uid)
    }

    /// Fin de session : la tâche de fond s'arrête ; la marque reste et attend son compte.
    var teardown: SessionTeardown {
        let withdrawals = self.withdrawals
        return { await withdrawals.stop() }
    }
}

extension AppEnvironment {
    /// L'interrupteur de Réglages > Confidentialité : lit et écrit le choix du compte à travers `ConsentWithdrawals`.
    func makePrivacyViewModel() -> PrivacyViewModel {
        let session = self.session
        return PrivacyViewModel(
            dependencies: PrivacyDependencies(
                privacy: privacySettings,
                withdrawals: privacyServices.withdrawals,
                pending: privacyServices.pending,
                network: network,
                currentUid: { session.currentUid }
            )
        )
    }
}
