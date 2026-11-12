import Foundation

/// Annonce un texte au lecteur d'écran SANS déplacer le focus (injectable pour les tests).
@MainActor
protocol PaywallAnnouncing {
    func announce(_ text: String)
}

struct SystemPaywallAnnouncer: PaywallAnnouncing {
    func announce(_ text: String) {
        Announcer.announce(text)
    }
}

/// L'état d'UN affichage de l'écran d'offre : son contenu (`PaywallState`) et la croix différée de 3 secondes
/// (ticket `paywall-v2.md`, §6).
///
/// Un affichage = un view model : l'hôte en crée un nouveau à chaque présentation, donc fermer puis rouvrir repart de zéro et
/// deux affichages ne partagent rien (critère CR4). L'horloge, le délai et l'annonce sont injectés : les tests n'attendent jamais.
@MainActor
final class PaywallViewModel: ObservableObject {
    let reason: PaywallReason
    @Published private(set) var state: PaywallState
    /// « Voir toutes les offres » : la disposition à deux boutons pour une personne éligible à l'essai.
    @Published private(set) var showsAllOffers = false
    @Published private var gate: PaywallCloseGate

    private let clock: any PaywallClock
    private let announcer: any PaywallAnnouncing

    init(
        reason: PaywallReason,
        state: PaywallState = .comingSoon,
        closeDelay: TimeInterval = PaywallTiming.closeDelay,
        clock: any PaywallClock = SystemPaywallClock(),
        announcer: any PaywallAnnouncing
    ) {
        self.reason = reason
        self.state = state
        self.gate = PaywallCloseGate(delay: closeDelay)
        self.clock = clock
        self.announcer = announcer
    }

    /// La croix existe-t-elle ? Absente (ni lue par VoiceOver, ni atteignable au clavier) pendant le délai ; une fois là, elle reste.
    /// Un abonné n'a plus d'offre à lire : « Continuer » est actif tout de suite et la règle ne s'applique pas (Q-PAY1).
    var isCloseAvailable: Bool {
        gate.isRevealed || state == .subscribed
    }

    var layout: PaywallLayout? {
        state.offers.map { PaywallLayout.of($0, showsAllOffers: showsAllOffers) }
    }

    // MARK: - La croix différée

    /// L'écran est affiché : le compte à rebours part (une seule fois, même si l'écran réapparaît).
    func onDisplayed() {
        gate.start(at: clock.now())
    }

    /// Attend l'échéance, qui ne dépend ni du chargement des prix ni de la connexion (critère CR13). Annulée quand l'écran disparaît.
    func runCloseTimer() async {
        onDisplayed()
        while !gate.isRevealed {
            let wait = max(gate.remaining(at: clock.now()), 0.05)
            do {
                try await clock.sleep(for: wait)
            } catch {
                return
            }
            tick()
        }
    }

    /// Relit l'horloge : au retour de l'arrière-plan, si 3 secondes se sont écoulées, la croix est là.
    func tick() {
        if gate.refresh(at: clock.now()) {
            announcer.announce(L10n.string("paywall_close_available_a11y"))
        }
    }

    // MARK: - Le contenu

    func send(_ event: PaywallEvent) {
        state = state.reduced(by: event)
        if state.offers == nil {
            showsAllOffers = false
        }
    }

    func showAllOffers() {
        if state.offers != nil {
            showsAllOffers = true
        }
    }
}
