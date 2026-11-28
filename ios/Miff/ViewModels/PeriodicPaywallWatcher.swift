import Foundation

/// Regarde les règles de l'offre périodique et, quand tout est bon, l'ouvre. Ne dessine rien : il ouvre l'écran d'offre EXISTANT (le point d'entrée unique de l'hôte), donc la croix
/// de 3 secondes, les deux événements Analytics et les textes sont ceux de toutes les autres entrées. Miroir de `PeriodicPaywallWatcher` d'Android.
///
/// Tout ce qui touche à l'appareil est injecté (la situation de l'écran, l'état de l'app, la présentation du système, l'attente) : les tests n'attendent jamais pour de vrai.
@MainActor
final class PeriodicPaywallWatcher {
    private let rules: PeriodicPaywallRules
    private let situation: @MainActor () -> PeriodicPaywallSituation
    private let appIsActive: @MainActor () -> Bool
    private let modalPresented: @MainActor () -> Bool
    private let sleep: (Int) async -> Void
    private let open: @MainActor () -> Void

    init(
        rules: PeriodicPaywallRules,
        situation: @escaping @MainActor () -> PeriodicPaywallSituation,
        appIsActive: @escaping @MainActor () -> Bool,
        modalPresented: @escaping @MainActor () -> Bool,
        sleep: @escaping (Int) async -> Void,
        open: @escaping @MainActor () -> Void
    ) {
        self.rules = rules
        self.situation = situation
        self.appIsActive = appIsActive
        self.modalPresented = modalPresented
        self.sleep = sleep
        self.open = open
    }

    /// Décide, attend si les règles le demandent, et recommence ; s'arrête à la première décision définitive. Vrai si l'offre a été ouverte. La situation est RELUE à chaque tour,
    /// et une dernière fois juste avant d'ouvrir (le droit a pu changer pendant l'attente : Miff+ apparu = rien n'est ouvert, critère PP15). Jamais depuis l'arrière-plan : l'app
    /// doit être active (elle l'est de nouveau au prochain changement de phase, qui relance ce regard).
    @discardableResult
    func look() async -> Bool {
        while !Task.isCancelled {
            guard appIsActive() else {
                return false
            }
            var current = situation()
            current.modalPresented = modalPresented()
            switch rules.evaluate(current) {
            case .show:
                rules.onLaunched()
                open()
                return true
            case .wait(let millis):
                await sleep(millis)
            case .no:
                return false
            }
        }
        return false
    }
}
