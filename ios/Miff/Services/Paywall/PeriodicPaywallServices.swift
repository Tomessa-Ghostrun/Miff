import Combine
import CoreGraphics
import Foundation
import UIKit

// L'offre périodique, construite d'un seul tenant (ticket docs/tickets/paywall-periodique.md, PAY-PER-I). ACTIVE dans TOUS les builds, sans drapeau (décision du PM, P1).
// CONFIDENTIALITÉ : un entier sur le téléphone (voir `PeriodicPaywallFileStore`), aucune écriture sur le serveur, aucune permission, aucun événement qui le porte.

/// Dit à l'écran que quelque chose a changé dans les règles (un toucher relâché, une session, le premier appui, une surcouche) : l'observateur regarde alors de nouveau. Un simple
/// compteur publié, rien de personnel.
@MainActor
final class PeriodicPaywallBeacon: ObservableObject {
    @Published private(set) var tick = 0

    func bump() {
        tick += 1
    }
}

/// Les surfaces des menus du système qui ne disent pas qu'elles sont ouvertes (le menu de « Répartition de ton temps »), en coordonnées de la fenêtre. Sûre entre fils : les
/// écrans l'écrivent, la reconnaissance des touchers la lit.
final class PeriodicMenuAreas: @unchecked Sendable {
    private let lock = NSLock()
    private var areas: [UUID: CGRect] = [:]

    func set(_ id: UUID, frame: CGRect?) {
        lock.lock()
        defer { lock.unlock() }
        areas[id] = frame
    }

    func contains(x: Double, y: Double) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let point = CGPoint(x: x, y: y)
        return areas.values.contains { $0.contains(point) }
    }
}

/// Les points où l'app dit à l'offre périodique ce qu'elle seule sait (l'onglet Update ouvert, le Feed, la pop-in du Profil, les menus). Une classe simple, SANS observation :
/// l'observateur lit ces valeurs au moment de décider.
@MainActor
final class PeriodicPaywallSignals {
    var updateScreenOpen = false
    var feed: @MainActor () -> PeriodicFeedSnapshot = { PeriodicFeedSnapshot() }
    var profileConnectionPopInOpen: @MainActor () -> Bool = { false }
    let menuAreas = PeriodicMenuAreas()
}

/// L'offre périodique pour toute la vie de l'app : les règles, le compteur, ce qui les réveille et ce qui les remet à zéro.
@MainActor
final class PeriodicPaywallServices {
    let rules: PeriodicPaywallRules
    let beacon = PeriodicPaywallBeacon()
    let signals = PeriodicPaywallSignals()
    /// Fin de session (déconnexion, suppression du compte, session perdue) : le compteur et la session sont effacés, la personne suivante repart de zéro (critère PP6).
    let teardowns: [SessionTeardown]
    /// Gardés (jamais retirés : l'offre vit autant que l'app) pour que les observateurs ne soient pas des résultats jetés.
    private var observers: [NSObjectProtocol] = []

    /// `observesLifecycle` : faux dans les tests (ils appellent les règles directement). `center` : le centre de notifications d'où viennent les passages au premier plan.
    init(
        store: PeriodicPaywallCounterStore = PeriodicPaywallFileStore(),
        clock: @escaping () -> Int = PeriodicPaywallClock.monotonicMillis,
        openingsBetween: Int = PeriodicPaywallDefaults.openingsBetween,
        observesLifecycle: Bool = true,
        center: NotificationCenter = .default
    ) {
        let rules = PeriodicPaywallRules(store: store, clock: clock, openingsBetween: openingsBetween)
        self.rules = rules
        teardowns = [{ rules.clear() }]
        let beacon = self.beacon
        rules.onChange = { [weak beacon] in
            Task { @MainActor in
                beacon?.bump()
            }
        }
        if observesLifecycle {
            observeLifecycle(center)
        }
    }

    /// « Ouverture » = l'app DEVIENT ACTIVE devant une personne (démarrage à froid, ou retour après 30 minutes ou plus : la règle mesure l'absence). Un réveil en arrière-plan par la
    /// surveillance des lieux ne rend jamais l'app active : il ne compte pas (critère PP1). Le centre de contrôle ou une alerte du système ne sont qu'« inactive » puis « active »
    /// sans passage par l'arrière-plan : les règles les ignorent. `queue: nil` : le bloc tourne tout de suite sur le fil qui poste (le fil principal pour UIKit) ; les règles sont
    /// sûres entre fils.
    private func observeLifecycle(_ center: NotificationCenter) {
        let rules = self.rules
        let active = center.addObserver(
            forName: UIApplication.didBecomeActiveNotification, object: nil, queue: nil
        ) { _ in
            rules.onForeground()
        }
        let background = center.addObserver(
            forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: nil
        ) { _ in
            rules.onBackground()
        }
        observers = [active, background]
    }
}
