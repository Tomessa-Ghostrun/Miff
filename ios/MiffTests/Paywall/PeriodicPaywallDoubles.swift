import Foundation
@testable import Miff

/// L'horloge monotone des tests de l'offre périodique, déplacée à la main : rien n'attend 30 minutes ni 1,5 seconde pour de vrai. Une boîte simple, sans isolation.
final class PeriodicClockBox {
    var now = 1_000_000

    func advance(_ millis: Int) {
        now += millis
    }
}

/// Le compteur en mémoire : ce que l'offre périodique retient, sans fichier.
final class MemoryPeriodicStore: PeriodicPaywallCounterStore {
    var value: Int
    var clears = 0

    init(value: Int = 0) {
        self.value = value
    }

    func read() -> Int {
        value
    }

    func write(_ count: Int) {
        value = count
    }

    func clear() {
        value = 0
        clears += 1
    }
}

/// Une situation calme d'une personne sans Miff+ ; chaque test change ce dont il parle : `calmSituation { $0.keyboardOpen = true }`.
func calmSituation(_ change: (inout PeriodicPaywallSituation) -> Void = { _ in }) -> PeriodicPaywallSituation {
    var situation = PeriodicPaywallSituation(
        miffPlus: false,
        tabsInFront: true,
        updateScreenOpen: false,
        keyboardOpen: false,
        messageOpen: false,
        ownAlertInProgress: false,
        lovedOneAlertUnseen: false,
        offerReachable: true,
        modalPresented: false
    )
    change(&situation)
    return situation
}
