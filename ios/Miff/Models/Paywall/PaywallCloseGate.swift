import Foundation

/// Les durées de l'écran d'offre, écrites UNE seule fois (ticket `paywall-v2.md`, §6 et §7).
enum PaywallTiming {
    /// Secondes entre l'affichage de l'écran d'offre et l'apparition de la croix de fermeture (décision du PM du 2026-11-12, à
    /// CHAQUE affichage, quel que soit le point d'entrée). 0 supprime la règle (parade au risque de rejet par les stores : une petite
    /// PR, sans refonte). Valeur fixe de l'app : aucun réglage à distance (critère CR12).
    static let closeDelay: TimeInterval = 3
    /// Au-delà, des prix qui n'arrivent pas deviennent « Les offres ne sont pas disponibles pour le moment » (critère PW9).
    static let offersLoadingTimeout: TimeInterval = 10
    /// Durée du fondu de la croix (instantané quand la personne a réduit les animations).
    static let closeFadeDuration: Double = 0.25
}

/// L'horloge de l'écran d'offre, injectable : aucun test n'attend réellement 3 secondes.
protocol PaywallClock {
    /// Secondes écoulées depuis un point fixe, qui ne reculent jamais (changer l'heure du téléphone ne change rien).
    func now() -> TimeInterval
    func sleep(for seconds: TimeInterval) async throws
}

/// L'horloge du téléphone. `ContinuousClock` continue de compter quand l'app passe en arrière-plan ou que l'écran se verrouille :
/// le délai est du temps réel écoulé (ticket §6.1, point 2), pas du temps passé au premier plan.
struct SystemPaywallClock: PaywallClock {
    private let origin = ContinuousClock.now

    func now() -> TimeInterval {
        let elapsed = origin.duration(to: ContinuousClock.now)
        return Double(elapsed.components.seconds) + Double(elapsed.components.attoseconds) / 1e18
    }

    func sleep(for seconds: TimeInterval) async throws {
        try await Task.sleep(nanoseconds: UInt64(max(0, seconds) * 1_000_000_000))
    }
}

/// La règle de la croix différée, sans écran : une valeur par AFFICHAGE de l'offre (jamais partagée entre deux affichages).
///
/// - Avant `start`, ou avant l'échéance, la croix n'existe pas.
/// - `start` n'a d'effet qu'une fois : fermer puis rouvrir crée un NOUVEL affichage, donc une nouvelle valeur.
/// - Une fois révélée, la croix le reste jusqu'à la fermeture : rotation, taille de texte, thème, retour d'arrière-plan ne
///   relancent rien (`refresh` ne revient jamais en arrière).
/// - Un délai de 0 (ou négatif) désactive la règle : la croix est là d'emblée.
struct PaywallCloseGate: Equatable {
    let delay: TimeInterval
    private(set) var shownAt: TimeInterval?
    private(set) var isRevealed: Bool

    init(delay: TimeInterval = PaywallTiming.closeDelay) {
        self.delay = max(0, delay)
        self.shownAt = nil
        self.isRevealed = self.delay == 0
    }

    /// Le compte à rebours part à l'affichage effectif ; un second appel ne le relance pas.
    mutating func start(at now: TimeInterval) {
        guard shownAt == nil else {
            return
        }
        shownAt = now
        refresh(at: now)
    }

    /// Vrai à l'instant précis où la croix vient d'être révélée (pour l'annoncer UNE fois) ; faux sinon.
    @discardableResult
    mutating func refresh(at now: TimeInterval) -> Bool {
        guard !isRevealed, let shownAt, now - shownAt >= delay else {
            return false
        }
        isRevealed = true
        return true
    }

    /// Le temps qu'il reste avant la croix (le délai entier tant que l'écran n'est pas affiché).
    func remaining(at now: TimeInterval) -> TimeInterval {
        if isRevealed {
            return 0
        }
        guard let shownAt else {
            return delay
        }
        return max(0, delay - (now - shownAt))
    }
}
