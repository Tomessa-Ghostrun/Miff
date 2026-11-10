import Foundation
#if canImport(Combine)
import Combine
#endif

// MODE VOYAGE, la carte du Profil (VOY-2I, docs/tickets/mode-voyage.md §7). Ouvert à TOUS les comptes (décision du PM du 2026-11-10) :
// aucun abonnement n'est lu, aucune offre n'est montrée. Rien ici n'est journalisé ni envoyé comme événement : le voyage est écrit par le
// moteur de suivi (`TravelControl`) et nulle part ailleurs. Miroir de `TravelViewModel.kt` d'Android (VOY-2A).

/// Pourquoi le sélecteur de date est ouvert : un voyage commence (la personne confirme ensuite), ou la fin d'un voyage en cours bouge
/// (aucune confirmation : réversible).
enum TravelPickerPurpose: Equatable {
    case start
    case change
}

struct TravelPickerState: Equatable {
    let purpose: TravelPickerPurpose
    let initialDay: TravelDate
    let minDay: TravelDate
    let maxDay: TravelDate
}

/// Les phrases calmes sous la carte.
enum TravelMessage: Equatable {
    /// Le statut reprend (« Ton statut reprend. »).
    case resumed
    /// Accepté ici ; les proches le verront une fois en ligne.
    case offline
    /// Le serveur ne l'a pas pris : rien n'a changé.
    case refused
    /// La nouvelle fin d'un voyage en cours n'a pas été prise : la fin qui était là reste.
    case updateRefused
}

struct TravelUiState: Equatable {
    /// Le voyage en cours, ou nil.
    var period: TravelPeriod?
    /// Une opération est en cours : l'interrupteur ne peut pas être touché deux fois.
    var working = false
    var picker: TravelPickerState?
    /// Le voyage qui attend la feuille de confirmation.
    var confirm: TravelPeriod?
    var message: TravelMessage?
}

@MainActor
final class TravelViewModel: ObservableObject {
    @Published private(set) var state = TravelUiState()

    private let control: TravelControl
    private let nowMillis: () -> Int
    private let zone: () -> TimeZone
    private let messageSeconds: Double
    /// Les phrases qui informent seulement s'effacent seules ; celle qui dit qu'un geste n'a pas abouti reste jusqu'au geste suivant.
    private var clearTask: Task<Void, Never>?

    /// Combien de temps une phrase qui informe reste sous la carte.
    static let messageShownSeconds = 6.0

    init(
        control: TravelControl,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        zone: @escaping () -> TimeZone = { TimeZone.current },
        messageSeconds: Double = TravelViewModel.messageShownSeconds
    ) {
        self.control = control
        self.nowMillis = nowMillis
        self.zone = zone
        self.messageSeconds = messageSeconds
        state.period = control.current()
    }

    private var today: TravelDate {
        TravelDate.today(nowMillis: nowMillis(), zone: zone())
    }

    /// Relit le voyage gardé sur le téléphone (l'écran s'ouvre, l'app revient au premier plan, la fin est venue pendant l'absence).
    func reload() {
        let current = control.current()
        if state.period != current {
            state.period = current
        }
    }

    /// La personne a touché l'interrupteur. Éteint vers allumé : le sélecteur de date. Allumé vers éteint : le voyage finit tout de suite
    /// (aucune confirmation, le statut reprend).
    func onToggle() {
        guard !state.working else {
            return
        }
        clearTask?.cancel()
        if state.period == nil {
            openPicker(.start)
            return
        }
        state.working = true
        state.message = nil
        Task { [weak self] in
            guard let self else {
                return
            }
            _ = await control.end()
            state.period = control.current()
            state.working = false
            state.message = .resumed
            clearLater(.resumed)
        }
    }

    /// La ligne « Date de fin » d'un voyage en cours.
    func onChangeDate() {
        guard !state.working, state.period != nil else {
            return
        }
        openPicker(.change)
    }

    private func openPicker(_ purpose: TravelPickerPurpose) {
        let day = today
        let minimum = TravelPlan.minDay(today: day)
        let maximum = TravelPlan.maxDay(today: day)
        var initial = TravelPlan.defaultDay(today: day)
        if let current = state.period.flatMap({ TravelDate.parse($0.untilDay) }) {
            initial = min(max(current, minimum), maximum)
        }
        state.picker = TravelPickerState(purpose: purpose, initialDay: initial, minDay: minimum, maxDay: maximum)
        state.message = nil
    }

    func onPickerDismissed() {
        state.picker = nil
    }

    /// Un jour a été choisi dans le sélecteur.
    func onDayChosen(_ day: TravelDate) {
        guard let picker = state.picker else {
            return
        }
        // Le sélecteur refuse déjà ces jours : un jour hors bornes (une horloge qui a bougé entre-temps) ne change rien.
        guard case .valid(let period) = TravelPlan.choose(day: day, today: today, zone: zone()) else {
            return
        }
        state.picker = nil
        switch picker.purpose {
        case .start:
            state.confirm = period
        case .change:
            apply(period, change: true)
        }
    }

    func onConfirm() {
        guard let period = state.confirm else {
            return
        }
        state.confirm = nil
        apply(period, change: false)
    }

    /// « Annuler » sur la feuille : rien n'a été écrit, rien ne change.
    func onConfirmDismissed() {
        state.confirm = nil
    }

    func onMessageShown() {
        state.message = nil
    }

    private func apply(_ period: TravelPeriod, change: Bool) {
        guard !state.working else {
            return
        }
        state.working = true
        state.message = nil
        clearTask?.cancel()
        Task { [weak self] in
            guard let self else {
                return
            }
            let result = await control.set(period)
            state.period = control.current()
            state.working = false
            let message = Self.message(for: result, change: change)
            state.message = message
            if message == .offline {
                clearLater(.offline)
            }
        }
    }

    /// La phrase calme d'une réponse du moteur ; rien quand tout est en place.
    static func message(for result: TravelResult, change: Bool) -> TravelMessage? {
        switch result {
        case .done:
            return nil
        case .doneOffline:
            return .offline
        case .refused, .unavailable:
            return change ? .updateRefused : .refused
        }
    }

    /// Retire `message` après quelques secondes, s'il est toujours celui qui est montré (un geste plus récent l'a déjà remplacé ou effacé
    /// sinon).
    private func clearLater(_ message: TravelMessage) {
        clearTask?.cancel()
        let seconds = messageSeconds
        clearTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            guard !Task.isCancelled, let self, state.message == message else {
                return
            }
            state.message = nil
        }
    }
}
