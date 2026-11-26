import Combine
import Foundation

/// Branche UN affichage de l'écran d'offre sur le coordinateur de l'achat (SRV-8I). Il ne dessine rien et ne sait rien du store : il demande les offres, lance l'achat ou la
/// restauration, et traduit ce que le coordinateur raconte (`PurchaseSession`) en événements de l'écran (`PaywallEvent`). Un affichage = un pilote : fermer puis rouvrir l'offre
/// repart de zéro (comme la croix différée). Il ne tient aucun prix et n'accorde rien : l'accès vient du serveur (`entitlements/{uid}`), jamais de cet écran.
///
/// Un achat est DEMANDÉ au coordinateur, qui vit autant que l'app : fermer l'offre ne l'annule pas, et son résultat est pris en compte quand même (critère CR11).
/// Une personne qui a déjà Miff+ (une course : un autre téléphone, un achat tout juste confirmé) ne se voit jamais proposer un second abonnement : l'écran montre la
/// confirmation.
@MainActor
final class PaywallStoreDriver: ObservableObject {
    private let model: PaywallViewModel
    private let premium: PremiumCoordinator
    private let offersTimeout: TimeInterval
    private var handledSerial = -1
    private var loadSerial = 0
    private var subscriptions = Set<AnyCancellable>()

    init(
        model: PaywallViewModel,
        premium: PremiumCoordinator,
        offersTimeout: TimeInterval = PaywallTiming.offersLoadingTimeout
    ) {
        self.model = model
        self.premium = premium
        self.offersTimeout = offersTimeout
        // Un résultat fini AVANT l'ouverture de cet écran ne lui est pas raconté (le serveur est relu tout de suite et dit si Miff+ est allumé).
        if case .finished(let serial, _, _) = premium.session {
            handledSerial = serial
        }
        premium.$session
            .sink { [weak self] session in
                self?.onSession(session)
            }
            .store(in: &subscriptions)
        // Un accès confirmé ailleurs pendant que l'offre est ouverte (un « Ask to Buy » approuvé, un achat retrouvé en silence) : la confirmation remplace l'offre.
        premium.accessGranted
            .sink { [weak self] in
                self?.model.send(.purchaseSucceeded)
            }
            .store(in: &subscriptions)
    }

    /// Les gestes de l'écran d'offre.
    var actions: PaywallActions {
        PaywallActions(
            onPurchase: { [weak self] plan in self?.purchase(plan) },
            onRestore: { [weak self] in self?.restore() },
            onRetry: { [weak self] in
                guard let self else {
                    return
                }
                Task {
                    await self.load()
                }
            },
            onManage: { [weak self] in
                guard let self else {
                    return
                }
                Task {
                    await self.premium.openManagement()
                }
            }
        )
    }

    // MARK: - Les offres

    /// Appelé une fois, au début de l'affichage : les prix arrivent.
    func start() async {
        model.beginStore()
        await load()
    }

    /// Demande les offres au store (et au serveur si Miff+ est déjà allumé), au plus `offersTimeout` secondes : puis l'offre, « pas disponible », ou la confirmation.
    func load() async {
        loadSerial += 1
        let serial = loadSerial
        model.send(.offersRequested)
        let premium = self.premium
        let timeout = offersTimeout
        async let status = premium.status()
        let result = (try? await timedOperation(seconds: timeout) { await premium.loadOffers() }) ?? .error
        let resolved = await status
        let already = resolved?.hasAccess == true
        guard serial == loadSerial else {
            return
        }
        if already {
            model.send(.purchaseSucceeded)
            // L'offre a été ouverte par une porte qui ne le savait pas (la formule gardée par l'annuaire a été lue AVANT) : les écrans qui la gardent la relisent.
            premium.noteAccessGranted()
            return
        }
        switch result {
        case .loaded(let offers):
            model.send(.offersLoaded(offers))
        case .notAvailable, .error:
            model.send(.offersFailed)
        }
    }

    // MARK: - Les gestes

    func purchase(_ plan: PaywallPlan) {
        guard !Self.isWorking(premium.session) else {
            return
        }
        model.send(.purchaseStarted(plan))
        guard case .purchasing = model.state else {
            return
        }
        premium.startPurchase(plan: plan)
    }

    func restore() {
        guard !Self.isWorking(premium.session) else {
            return
        }
        model.send(.restoreStarted)
        guard case .restoring = model.state else {
            return
        }
        premium.startRestore()
    }

    private static func isWorking(_ session: PurchaseSession) -> Bool {
        if case .working = session {
            return true
        }
        return false
    }

    // MARK: - Ce que raconte le coordinateur

    private func onSession(_ session: PurchaseSession) {
        guard case .finished(let serial, let outcome, let restore) = session, serial > handledSerial else {
            return
        }
        handledSerial = serial
        if let outcome {
            model.send(Self.event(for: outcome))
        }
        if let restore {
            model.send(Self.event(for: restore))
        }
    }

    static func event(for outcome: PurchaseOutcome) -> PaywallEvent {
        switch outcome {
        case .granted:
            return .purchaseSucceeded
        case .pending:
            return .purchasePending
        case .cancelled:
            return .purchaseCancelled
        case .otherAccount:
            return .purchaseOtherAccount
        case .unavailable:
            return .purchaseUnavailable
        case .onHold:
            return .purchaseOnHold
        case .network, .failed:
            return .purchaseFailed
        }
    }

    static func event(for restore: RestoreOutcome) -> PaywallEvent {
        switch restore {
        case .found:
            return .restoreFound
        case .noneFound:
            return .restoreNothing
        case .otherAccount:
            return .restoreOtherAccount
        case .pending:
            return .restorePending
        case .onHold:
            return .restoreOnHold
        case .cancelled:
            return .restoreCancelled
        case .network, .failed:
            return .restoreFailed
        }
    }
}
