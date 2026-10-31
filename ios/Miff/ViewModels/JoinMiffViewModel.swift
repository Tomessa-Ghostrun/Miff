import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : le code tapé ici est un secret. Il vit en mémoire dans cet état seulement (jamais dans un
// journal, un événement Analytics, un rapport de plantage ou un message d'erreur) ; rien n'est lu dans le presse-papiers
// tant que la personne ne touche pas elle-même « Coller » (bouton système, sans alerte).

enum JoinStep: Equatable {
    case code
    case confirm
}

/// Ce que l'écran de saisie dit sous le champ. Un seul message pour « introuvable » quelle que soit la cause (critère J4).
enum JoinProblem: Equatable {
    /// « On ne trouve pas de Miff avec ce code. Vérifie-le auprès de la personne qui te l'a donné. »
    case notFound
    /// « Ce code ne ressemble pas à un code Miff. » : un message LOCAL, aucune lecture réseau (critère J2).
    case foreignCharacter
    /// « On fait une petite pause d'une minute avant de réessayer. » (critère J5)
    case paused
    case offline
    case generic
    /// Un compte gratuit qui a déjà une Miff (« une seule Miff à la fois ») : le ticket Premium décide de ce qui est proposé.
    case oneMiff
}

struct AlreadyMemberInfo: Equatable, CustomStringConvertible {
    let miffId: String
    let miffName: String

    var description: String {
        "AlreadyMemberInfo(redacted)"
    }
}

struct JoinUiState: CustomStringConvertible {
    var step: JoinStep = .code
    /// Le code normalisé, 12 caractères au plus.
    var code = ""
    var foreignCharacter = false
    var checking = false
    var problem: JoinProblem?
    var alreadyMember: AlreadyMemberInfo?
    /// À l'étape de confirmation : le nom de la Miff que le code ouvre.
    var miffName: String?
    var joining = false
    var confirmMessage: ActionMessage?
    /// La Miff qui vient d'être rejointe : l'écran hôte se referme.
    var joinedMiffId: String?

    var isComplete: Bool {
        JoinCode.analyze(code).isComplete
    }

    /// « Continuer » reste gris tant que 12 caractères valides ne sont pas saisis, et pendant une pause ou une vérification
    /// (critère J2).
    var canContinue: Bool {
        isComplete && !checking && problem != .paused
    }

    var description: String {
        "JoinUiState(redacted)"
    }
}

/// « Rejoindre une Miff » (ticket Miff, écrans D et E, critères J et K). L'ordre des vérifications est celui du ticket §5.4 :
/// format (local), lecture du code, nettoyage d'un index obsolète, « déjà membre ? », puis l'écran du consentement explicite,
/// puis UN lot. Rien n'est écrit avant que la personne touche « Rejoindre et partager » (critères J6 et K3).
@MainActor
final class JoinMiffViewModel: ObservableObject {
    @Published private(set) var state = JoinUiState()

    private let context: MiffFlowContext
    private let limiter: JoinAttemptLimiter
    private let clock: AppClock
    /// Ce que la dernière bonne lecture du code a donné : gardé ici, jamais dans l'état dessiné.
    private var ready: JoinReady?
    private var pauseTask: Task<Void, Never>?

    init(context: MiffFlowContext, limiter: JoinAttemptLimiter, clock: AppClock = .live) {
        self.context = context
        self.limiter = limiter
        self.clock = clock
    }

    /// Tapé, ou collé par la personne (jamais lu dans le presse-papiers tout seul).
    func onCodeChanged(_ raw: String) {
        guard state.step == .code, !state.checking else {
            return
        }
        let input = JoinCode.analyze(raw)
        var next = state
        next.code = input.code
        next.foreignCharacter = input.hasForeignCharacter
        // Un nouveau code laisse l'ancien message derrière lui, mais une pause reste (elle finit toute seule).
        if next.problem != .paused {
            next.problem = nil
        }
        next.alreadyMember = nil
        state = next
    }

    func onContinue() {
        guard state.step == .code, !state.checking else {
            return
        }
        let input = JoinCode.analyze(state.code)
        if input.hasForeignCharacter {
            // Réponse locale : aucune lecture réseau (critère J2).
            state.problem = .foreignCharacter
            return
        }
        guard input.isComplete else {
            return
        }
        if limiter.isPaused() {
            startPause()
            return
        }
        guard context.network.isOnline() else {
            state.problem = .offline
            return
        }
        guard let uid = context.currentUid() else {
            return
        }
        state.checking = true
        state.problem = nil
        state.alreadyMember = nil
        let code = input.code
        Task {
            await self.prepare(uid: uid, code: code)
        }
    }

    private func prepare(uid: String, code: String) async {
        let prepared = await context.repository.prepareJoin(uid: uid, typedCode: code)
        var next = state
        next.checking = false
        switch prepared {
        case .ready(let found):
            limiter.recordSuccess()
            ready = found
            next.step = .confirm
            next.miffName = found.miffName
            next.confirmMessage = nil
            state = next
        case .alreadyMember(let miffId, let miffName):
            limiter.recordSuccess()
            next.alreadyMember = AlreadyMemberInfo(miffId: miffId, miffName: miffName)
            state = next
        case .notFound:
            limiter.recordFailure()
            next.problem = .notFound
            state = next
            if limiter.isPaused() {
                startPause()
            }
        case .needsPremium:
            // Pas une erreur de la personne : cela ne compte pas comme un essai raté.
            next.problem = .oneMiff
            state = next
        case .failed(let failure):
            next.problem = ActionMessage(failure: failure) == .offline ? .offline : .generic
            state = next
        }
    }

    /// « Rejoindre et partager » : le seul geste qui écrit quelque chose.
    func onConfirm() {
        guard state.step == .confirm, !state.joining, let preparation = ready else {
            return
        }
        guard context.network.isOnline() else {
            state.confirmMessage = .offline
            return
        }
        guard let uid = context.currentUid() else {
            return
        }
        state.joining = true
        state.confirmMessage = nil
        Task {
            await self.join(uid: uid, preparation: preparation)
        }
    }

    private func join(uid: String, preparation: JoinReady) async {
        let result = await context.repository.join(uid: uid, preparation: preparation)
        if case .joined(let miffId, let firstMiff) = result {
            context.tracker.track(.miffJoined(isFirstMiff: firstMiff))
            context.onEntered(.joined)
            await context.onDirectoryChanged()
            ready = nil
            var done = state
            done.joining = false
            done.joinedMiffId = miffId
            state = done
            return
        }
        var next = state
        next.joining = false
        switch result {
        case .joined:
            // Déjà traité plus haut.
            state = next
        case .notFound:
            // Renouvelé ou supprimé entre-temps : retour au code, avec le message unique (critère J10).
            ready = nil
            next.step = .code
            next.problem = .notFound
            next.miffName = nil
            state = next
        case .needsPremium:
            next.confirmMessage = .oneMiff
            state = next
        case .failed(let failure):
            next.confirmMessage = ActionMessage(failure: failure)
            state = next
            if failure == .noProfile {
                context.onProfileMissing()
            }
        }
    }

    /// « Annuler » (ou retour) sur la confirmation : rien n'a été écrit, la personne retrouve le code.
    func onCancelConfirm() {
        guard !state.joining else {
            return
        }
        ready = nil
        state.step = .code
        state.miffName = nil
        state.confirmMessage = nil
    }

    private func startPause() {
        state.problem = .paused
        pauseTask?.cancel()
        let waitMillis = limiter.remainingPauseMillis()
        let clock = self.clock
        pauseTask = Task { [weak self] in
            await clock.pause(waitMillis)
            guard !Task.isCancelled else {
                return
            }
            self?.endPause()
        }
    }

    private func endPause() {
        if state.problem == .paused {
            state.problem = nil
        }
    }
}
