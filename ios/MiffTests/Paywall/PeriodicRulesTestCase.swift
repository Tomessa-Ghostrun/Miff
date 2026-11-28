import XCTest
@testable import Miff

/// Le décor commun des tests des règles de l'offre périodique (ticket `docs/tickets/paywall-periodique.md`, critères PP1 à PP17) : une horloge déplacée à la main, un compteur en
/// mémoire, et les gestes d'une personne (démarrage, premier appui, absence). Aucun test n'attend pour de vrai. Miroir de `PeriodicPaywallRulesTest` d'Android.
class PeriodicRulesTestCase: XCTestCase {
    let clock = PeriodicClockBox()
    let store = MemoryPeriodicStore()

    /// Des règles avec un compteur de départ ; le seuil est celui du PM (10) sauf demande.
    func makeRules(openings: Int = PeriodicPaywallDefaults.openingsBetween, counter: Int = 0) -> PeriodicPaywallRules {
        store.value = counter
        let box = clock
        return PeriodicPaywallRules(store: store, clock: { box.now }, openingsBetween: openings)
    }

    /// Un démarrage à froid avec l'app connectée à l'écran.
    func coldStart(_ rules: PeriodicPaywallRules) {
        rules.onForeground()
        rules.onShellShown()
    }

    /// Le premier appui, puis 1,5 s de calme.
    func tapAndSettle(_ rules: PeriodicPaywallRules) {
        rules.onTouch(pressed: true)
        rules.onActivation()
        rules.onTouch(pressed: false)
        clock.advance(PeriodicPaywallDefaults.quietAfterTouchMillis)
    }

    /// L'app part en arrière-plan `millis` millisecondes, puis revient.
    func away(_ rules: PeriodicPaywallRules, millis: Int) {
        rules.onBackground()
        clock.advance(millis)
        rules.onForeground()
    }

    var thirty: Int {
        PeriodicPaywallDefaults.backgroundMillis
    }
}
