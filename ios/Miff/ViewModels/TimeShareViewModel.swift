import Foundation
#if canImport(Combine)
import Combine
#endif

// CONFIDENTIALITÉ et COÛT, pour ce fichier : tout est calculé SUR LE TÉLÉPHONE, à partir du journal local des changements de présence et de
// la liste des lieux DÉJÀ lue par la section « Lieux de confiance de ... » (aucune lecture Firestore de plus, aucune écriture, aucun
// événement Analytics). Les noms de lieux restent en mémoire, pour l'écran seulement ; rien n'est journalisé (critères TS-1, TS-4).

/// Ce que la carte dessine : son état, et la fenêtre choisie (gardée même quand la carte est masquée).
struct TimeShareDisplay: Equatable, CustomStringConvertible {
    var state: TimeShareCardState = .hidden
    var windowDays = TimeShareLimits.defaultWindowDays
    /// Plus de 20 zones surveillables : une note sous l'anneau dit que la répartition peut être incomplète.
    var overWatchedLimit = false

    var description: String {
        "TimeShareDisplay(redacted)"
    }
}

/// La carte « Répartition de ton temps » de l'onglet Profil : pour la Miff dont le carrousel montre la carte, calcule la répartition de la
/// fenêtre choisie (7, 14 ou 30 jours) d'après le journal local, et dit l'état du suivi. Créé avec la session de l'hôte du Profil.
@MainActor
final class TimeShareViewModel: ObservableObject {
    @Published private(set) var display: TimeShareDisplay
    private let history: PresenceHistory
    private let context: TimeShareContextSource
    private let clock: () -> Int
    private var request: (miffId: String, places: [TimeSharePlace])?
    /// Monte à chaque nouvelle demande : une réponse qui arrive après une demande plus récente est jetée (jamais de clignotement).
    private var generation = 0

    init(
        history: PresenceHistory,
        context: TimeShareContextSource,
        clock: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
    ) {
        self.history = history
        self.context = context
        self.clock = clock
        display = TimeShareDisplay(state: .hidden, windowDays: history.windowDays(), overWatchedLimit: false)
    }

    /// La Miff dont la carte est regardée et ses lieux ACTUELS (nil : pas encore lus, ou aucune carte de Miff montrée : rien n'est dessiné).
    func show(miffId: String?, places: [Place]?) {
        guard let miffId, let places else {
            generation += 1
            request = nil
            if display.state != .hidden {
                display.state = .hidden
            }
            return
        }
        request = (miffId, places.map { TimeSharePlace($0) })
        refresh()
    }

    /// Recalcule (retour sur l'onglet, retour au premier plan, « Réessayer », nouvelle fenêtre). Le journal est relu à chaque fois : le moteur
    /// y écrit même quand l'écran n'est pas là.
    func refresh() {
        generation += 1
        let current = generation
        Task { [weak self] in
            await self?.recompute(generation: current)
        }
    }

    /// Le choix 7 / 14 / 30 jours : gardé sur le téléphone, oublié à la déconnexion.
    func selectWindow(_ days: Int) {
        guard TimeShareLimits.windowsDays.contains(days), days != display.windowDays else {
            return
        }
        history.setWindowDays(days)
        display.windowDays = days
        refresh()
    }

    /// « Effacer mon historique de temps » : la répartition repart de zéro. Aucune écriture serveur.
    func eraseHistory() {
        history.eraseFacts()
        refresh()
    }

    private func recompute(generation current: Int) async {
        guard let request else {
            return
        }
        let conditions = await context.conditions(miffId: request.miffId)
        guard current == generation else {
            return
        }
        let days = TimeShareLimits.validWindow(history.windowDays())
        var readFailed = false
        var outcome: TimeShareOutcome?
        switch history.read(miffId: request.miffId) {
        case .unreadable:
            readFailed = true
        case .facts(let facts):
            outcome = TimeShareCalculator.compute(
                facts: facts, places: request.places, windowDays: days, nowMillis: clock(),
                startedAtMillis: history.startedAt(miffId: request.miffId)
            )
        }
        let state = TimeShareCardState.make(
            places: request.places, readFailed: readFailed, outcome: outcome, conditions: conditions
        )
        let next = TimeShareDisplay(state: state, windowDays: days, overWatchedLimit: conditions.overWatchedLimit)
        if next != display {
            display = next
        }
    }
}
