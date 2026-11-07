import Foundation
#if canImport(Combine)
import Combine
#endif

/// Fait tourner la suppression du compte aussi longtemps que l'app vit (l'écran peut disparaître : un geste = une opération), la met en
/// pause sans réseau et la reprend quand il revient (SC30), s'arrête à la « Dernière vérification » quand Firebase la réclame (SC13) et
/// renonce calmement après trois échecs de même nature (SC23). Modelé sur `MiffDeletionCoordinator` ; miroir d'Android
/// `AccountDeletionController`.
///
/// SÉCURITÉ : le marqueur écrit ici ne contient que l'identifiant du compte. Rien d'autre ne passe par ce fichier : ni nom, ni e-mail, ni
/// jeton, ni code d'autorisation Apple.
@MainActor
final class AccountDeletionController: ObservableObject, AccountDeletionControls {
    static let defaultRetryPauseNanos: UInt64 = 5_000_000_000
    static let maxPauseNanos: UInt64 = 60_000_000_000
    static let maxFailures = 3
    static let maxOnlineFailures = 6

    @Published private(set) var state: AccountDeletionState = .idle

    /// Pause avant un nouvel essai (elle double à chaque échec, jusqu'à une minute). Réglable par les tests.
    var retryPauseNanos = AccountDeletionController.defaultRetryPauseNanos

    private let procedure: AccountDeletionProcedure
    private let marker: AccountDeletionMarker
    private let session: AccountDeletionSession
    private let network: NetworkMonitor
    private let currentUid: () -> String?
    private var running = false
    private var job: Task<Void, Never>?
    /// Ce qui est arrivé à l'autorisation Apple, retenu pour l'écran final (la dernière ré-authentification Apple fait foi).
    private var apple: AppleRevocation = .notNeeded

    init(
        procedure: AccountDeletionProcedure,
        marker: AccountDeletionMarker,
        session: AccountDeletionSession,
        network: NetworkMonitor,
        currentUid: @escaping () -> String?
    ) {
        self.procedure = procedure
        self.marker = marker
        self.session = session
        self.network = network
        self.currentUid = currentUid
    }

    // MARK: - Ce que les écrans demandent

    func start(apple outcome: AppleRevocation) {
        guard let uid = currentUid(), !running else {
            return
        }
        remember(outcome)
        // Sur le disque AVANT le premier changement : une app tuée aussitôt reprend quand même (SC26).
        marker.write(AccountDeletionMark(uid: uid))
        launch(uid: uid, resumed: false, retrying: false)
    }

    func resume() {
        guard let uid = currentUid(), !running, let mark = marker.read(), mark.uid == uid else {
            // Jamais la reprise d'une suppression qui n'est pas celle de ce compte.
            return
        }
        launch(uid: uid, resumed: true, retrying: false)
    }

    func retry() {
        guard let uid = currentUid(), !running else {
            return
        }
        launch(uid: uid, resumed: false, retrying: true)
    }

    func reauthenticated(apple outcome: AppleRevocation) {
        guard let uid = currentUid(), !running else {
            return
        }
        remember(outcome)
        launch(uid: uid, resumed: false, retrying: false)
    }

    func acknowledge() {
        if case .done = state {
            state = .idle
        }
    }

    func signInAgain() {
        Task {
            await self.session.endSessionKeepingDeletionMarker()
        }
    }

    /// Fin de session (déconnexion, session perdue) : plus rien ne tourne ; le marqueur reste.
    func stop() {
        job?.cancel()
        job = nil
        running = false
        state = .idle
    }

    // MARK: - La procédure

    /// Une ré-authentification Apple qui a eu lieu fait foi (la suivante revoit l'autorisation que la feuille vient de redonner) ; sans
    /// Apple, ce qui a été retenu avant reste.
    private func remember(_ outcome: AppleRevocation) {
        if outcome != .notNeeded {
            apple = outcome
        }
    }

    private func launch(uid: String, resumed: Bool, retrying: Bool) {
        running = true
        state = .running(resumed: resumed, retrying: retrying, step: .checking)
        job = Task {
            await self.drive(uid: uid, resumed: resumed, retrying: retrying)
        }
    }

    private func drive(uid: String, resumed: Bool, retrying: Bool) async {
        defer {
            running = false
        }
        await session.beginAccountDeletion()
        var pacing = Pacing(pause: retryPauseNanos)
        while !Task.isCancelled {
            let first = retrying && pacing.failures == 0
            state = .running(resumed: resumed, retrying: first, step: .checking)
            let progress = RunProgress()
            let outcome = await procedure.run(uid: uid) { [weak self] step in
                progress.note(step)
                self?.show(step, resumed: resumed, retrying: first)
            }
            if Task.isCancelled {
                return
            }
            // Un tour qui a trouvé MOINS de Miffs à traiter que tous les précédents a avancé (une Miff est partie ou supprimée) : les
            // essais repartent de zéro, pour qu'une longue suppression ralentie çà et là ne soit jamais abandonnée. Strictement
            // décroissant : une étape qui échoue pour de bon s'arrête quand même après trois essais.
            if let total = progress.total, total < pacing.fewestMiffsLeft {
                pacing.restart(at: total)
            }
            guard await handle(outcome, uid: uid, pacing: &pacing) else {
                return
            }
        }
    }

    /// Vrai = un autre tour de la procédure ; faux = elle s'arrête ici (fin, pause longue, arrêt calme, annulation).
    private func handle(_ outcome: DeletionRun, uid: String, pacing: inout Pacing) async -> Bool {
        switch outcome {
        case .completed:
            await finish(uid: uid)
            return false
        case .needsRecentLogin:
            state = .needsRecentLogin
            return false
        case .miffsRemain:
            // Une ANNULATION, jamais une impasse : le marqueur part, l'app est remise sur ses rails et le dit calmement.
            marker.clear()
            state = .idle
            await session.cancelAccountDeletion()
            return false
        case .sessionExpired:
            state = .needsSignIn
            return false
        case .offline:
            // Le téléphone se dit en ligne et rien ne passe : après quelques essais, un arrêt calme et « Réessayer ».
            if network.isOnline() {
                pacing.offlineWhileOnline += 1
                if pacing.offlineWhileOnline >= Self.maxOnlineFailures {
                    state = .stuck
                    return false
                }
            }
            state = .waitingForNetwork
            await waitForNetwork()
            await pacing.wait()
            return true
        case .failed:
            pacing.failures += 1
            if pacing.failures >= Self.maxFailures {
                state = .stuck
                return false
            }
            await pacing.wait()
            return true
        }
    }

    /// La ligne d'étape, seulement tant que ce tour est celui en cours (un état posé par une pause ou un arrêt n'est jamais écrasé).
    private func show(_ step: DeletionStep, resumed: Bool, retrying: Bool) {
        if case .running = state {
            state = .running(resumed: resumed, retrying: retrying, step: step)
        }
    }

    private func finish(uid: String) async {
        // Firebase a confirmé : à partir de maintenant un lancement sans session veut dire « supprimé », pas « déconnecté dans notre dos ».
        if var mark = marker.read() {
            mark.accountDeleted = true
            marker.write(mark)
        }
        await session.eraseAfterAccountDeletion(uid: uid)
        state = .done(appleRevocation: apple)
    }

    private func waitForNetwork() async {
        for await online in network.onlineChanges() where online {
            return
        }
    }
}

/// Le rythme des essais : combien d'échecs de suite, combien de « hors ligne alors que le téléphone se dit en ligne », le plus petit
/// nombre de Miffs trouvé à traiter, et la pause avant le prochain essai (elle double, jusqu'à une minute).
private struct Pacing {
    var failures = 0
    var offlineWhileOnline = 0
    var fewestMiffsLeft = Int.max
    var pause: UInt64

    mutating func restart(at total: Int) {
        fewestMiffsLeft = total
        failures = 0
        offlineWhileOnline = 0
    }

    /// Attend la pause courante puis la double (une minute au plus).
    mutating func wait() async {
        let current = pause
        pause = min(pause * 2, AccountDeletionController.maxPauseNanos)
        try? await Task.sleep(nanoseconds: current)
    }
}

/// Le nombre de Miffs que ce tour a annoncé au début (« Miff 1 sur n »), pour savoir s'il a avancé.
@MainActor
private final class RunProgress {
    private(set) var total: Int?

    func note(_ step: DeletionStep) {
        if case .miff(_, let all, _) = step, total == nil {
            total = all
        }
    }
}
