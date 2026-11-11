import Foundation
#if canImport(Combine)
import Combine
#endif

// COÛT et CONFIDENTIALITÉ, pour ce fichier : il ne lit rien lui-même et ne touche à aucune donnée de la personne. Il ne fait que
// décider QUAND montrer la pop-in « Pas de connexion » et QUAND demander à l'écran de relire. Une relecture n'est demandée que si le
// système dit que le réseau est là, au plus une fois toutes les 10 secondes, et seulement pendant que la pop-in est en place (visible
// ou fermée par la personne). Quand le réseau est annoncé mais ne répond rien (réseau lent ou captif), au plus 3 essais automatiques
// se suivent : ensuite seuls « Réessayer » et le retour du réseau relancent une lecture, jamais une boucle de lectures. Tout s'arrête
// dès que l'écran est quitté (la tâche `watch()` est annulée) : l'onglet change, l'app passe en arrière-plan.
// Limite connue et signal : un essai rattache les écoutes de l'écran (quelques dizaines de lectures Firestore au plus) ; si le quota
// de lectures du plan gratuit approche (docs/dev/surveillance-quotas.md), baisser d'abord `maxAutomaticAttemptsOnline`.
// Miroir de `ConnectionGate` d'Android.

/// Ce dont un écran a besoin pour que la pop-in ait un sens.
enum OfflineNeed: Equatable {
    /// Un contenu est affiché (frais ou en cache), ou rien n'est attendu : aucune pop-in.
    case none
    /// Rien n'est encore affiché et une réponse est attendue (squelette, indicateur) : la pop-in vient quand le réseau manque, ou
    /// quand rien n'arrive après un court délai.
    case loading
    /// La lecture a échoué et rien n'est affiché : la pop-in ne vient que si le réseau manque (une autre erreur garde son message).
    case failed
}

/// Les délais de la pop-in, en un seul endroit (réglables ; les tests en passent de courts).
struct OfflinePopinTimings {
    /// Entre deux essais automatiques de reconnexion (10 s, demandé par le PM).
    var retryIntervalMillis = 10_000
    /// Réseau annoncé disponible mais aucune donnée : passé ce délai, la pop-in vient quand même (réseau capté sans accès réel).
    var noDataMillis = 8_000
    /// Sans réseau et rien à montrer : petite grâce avant la pop-in, pour qu'un lancement hors connexion qui trouve vite ses informations
    /// en mémoire du téléphone ne voie jamais la pop-in clignoter (1,5 s, comme Android). Une lecture qui a échoué (`failed`) n'attend
    /// pas : il n'y a rien à attendre.
    var offlineGraceMillis = 1_500
    /// « Réessayer » montre un indicateur au moins ce temps, pour que le toucher se sente pris en compte même sans réseau.
    var retryFeedbackMillis = 800
    /// Réseau annoncé disponible mais qui ne répond rien : au plus ce nombre d'essais automatiques de suite (puis seuls « Réessayer » et
    /// le retour du réseau relancent). Sans réseau, la minuterie tourne sans rien lire : pas de plafond utile.
    var maxAutomaticAttemptsOnline = 3
    /// Le compte à rebours « Nouvel essai dans N s » avance de cette durée à chaque pas.
    var tickMillis = 1_000
}

/// D'où vient une demande de relecture : l'écran peut faire plus pour un geste de la personne que pour un essai automatique.
enum OfflineRetryKind: Equatable {
    /// « Réessayer » : la personne l'a demandé.
    case manual
    /// Le réseau vient de revenir : une seule relecture, tout de suite.
    case networkBack
    /// L'essai de toutes les 10 s : le plus léger possible (les écoutes en cours se rattachent seules au retour du réseau).
    case automatic
}

/// Un moniteur qui dit toujours « en ligne » : pour les écrans qui n'ont pas de moniteur à donner (tests, aperçus).
struct AlwaysOnlineMonitor: NetworkMonitor {
    func isOnline() -> Bool {
        true
    }

    func onlineChanges() -> AsyncStream<Bool> {
        AsyncStream { continuation in
            continuation.yield(true)
            continuation.finish()
        }
    }
}

/// La pop-in « Pas de connexion » d'un écran et sa reconnexion automatique. Un modèle par écran ; l'écran :
/// 1. dit ce dont il a besoin (`setNeed`) à chaque changement de son état ;
/// 2. lance `watch()` tant qu'il est visible (une tâche SwiftUI : annulée à la sortie de l'écran ou de l'app) ;
/// 3. donne `onRetry`, la relecture de l'écran (appelée seulement quand le réseau est là).
///
/// La personne peut fermer la pop-in (`dismiss()`) : les essais continuent, et la page de l'écran le dit elle-même
/// (`isInlineVisible` : la phrase et « Réessayer » à la place du squelette).
@MainActor
final class OfflineRetryModel: ObservableObject {
    /// Vrai tant que l'écran est dans cet état « rien à montrer, pas de connexion » (pop-in visible OU fermée par la personne).
    @Published private(set) var isShown = false
    /// Vrai quand la personne a fermé la pop-in : les essais continuent, la page le dit à sa place. Remis à faux dès que l'état s'efface.
    @Published private(set) var isDismissed = false
    /// Vrai le temps d'un « Réessayer » : le bouton montre un indicateur et ne se touche pas deux fois.
    @Published private(set) var isRetrying = false
    /// Le compte à rebours avant le prochain essai automatique (nil : aucun n'est prévu, ou le plafond est atteint).
    @Published private(set) var secondsLeft: Int?
    /// La relecture de l'écran. Jamais appelée sans réseau.
    var onRetry: (OfflineRetryKind) -> Void = { _ in }

    /// La pop-in elle-même est à l'écran.
    var isPopinVisible: Bool {
        isShown && !isDismissed
    }

    /// La pop-in a été fermée : la page de l'écran dit « Pas de connexion » à sa place.
    var isInlineVisible: Bool {
        isShown && isDismissed
    }

    private let network: NetworkMonitor
    private let sleep: (Int) async -> Void
    private let timings: OfflinePopinTimings

    private var need = OfflineNeed.none
    private var isOnline: Bool
    private var waitElapsed = false
    private var watching = false
    private var noDataTask: Task<Void, Never>?
    private var loopTask: Task<Void, Never>?

    init(
        network: NetworkMonitor,
        sleep: @escaping (Int) async -> Void = AppClock.live.pause,
        timings: OfflinePopinTimings = OfflinePopinTimings()
    ) {
        self.network = network
        self.sleep = sleep
        self.timings = timings
        isOnline = network.isOnline()
    }

    /// L'écran dit ce qu'il montre. Sans effet tant que `watch()` ne tourne pas (écran quitté) ; pris en compte à son démarrage.
    func setNeed(_ next: OfflineNeed) {
        guard next != need else {
            return
        }
        need = next
        if next != .loading {
            waitElapsed = false
        } else if isShown {
            // Une relecture commence alors que la pop-in est en place (« Réessayer », essai automatique) : elle ne clignote pas, elle reste.
            waitElapsed = true
        }
        refresh()
    }

    /// Suit le réseau tant que l'appelant reste. À annuler en quittant l'écran : tout est alors remis à zéro, la pop-in disparaît.
    func watch() async {
        watching = true
        isOnline = network.isOnline()
        refresh()
        for await online in network.onlineChanges() {
            let wasOnline = isOnline
            isOnline = online
            if online && !wasOnline {
                // Le réseau revient : la pop-in s'efface d'elle-même (le délai « aucune donnée » repart de zéro) et l'écran relit tout
                // de suite, sans attendre les 10 s.
                waitElapsed = false
                restartWaitTimer()
                let wasShown = isShown
                refresh()
                if wasShown {
                    onRetry(.networkBack)
                }
            } else {
                if !online && wasOnline {
                    // Le réseau part : la petite grâce (plus courte que le délai « aucune donnée ») remplace l'attente en cours.
                    restartWaitTimer()
                }
                refresh()
                if online != wasOnline {
                    // Le plafond d'essais ne vaut que réseau annoncé : le compte repart de zéro.
                    restartRetryLoop()
                }
            }
        }
        stop()
    }

    /// « Réessayer » : relit tout de suite si le réseau est là ; sinon rien n'est lu, seul l'indicateur dit que le toucher est pris
    /// en compte. Le compte à rebours et le plafond d'essais repartent de zéro.
    func retryNow() {
        guard isShown, !isRetrying else {
            return
        }
        isRetrying = true
        if network.isOnline() {
            onRetry(.manual)
        }
        restartRetryLoop()
        let sleep = self.sleep
        let feedback = timings.retryFeedbackMillis
        Task { [weak self] in
            await sleep(feedback)
            self?.isRetrying = false
        }
    }

    /// La personne ferme la pop-in (toucher sur le voile, croix, geste « échappe » de VoiceOver) : les essais continuent, la page de
    /// l'écran dit « Pas de connexion » à sa place avec « Réessayer ».
    func dismiss() {
        guard isShown, !isDismissed else {
            return
        }
        isDismissed = true
    }

    // MARK: - Intérieur

    private func stop() {
        watching = false
        waitElapsed = false
        noDataTask?.cancel()
        noDataTask = nil
        loopTask?.cancel()
        loopTask = nil
        isRetrying = false
        isDismissed = false
        secondsLeft = nil
        if isShown {
            isShown = false
        }
    }

    private func refresh() {
        // Rien à montrer et une réponse attendue : un délai court (réseau annoncé disponible : « aucune donnée » ; réseau absent : petite
        // grâce) avant que la pop-in vienne.
        let waitsForData = watching && need == .loading && !waitElapsed
        if waitsForData {
            startNoDataTimerIfNeeded()
        } else {
            noDataTask?.cancel()
            noDataTask = nil
        }
        let show = watching && shouldShow
        if show != isShown {
            isShown = show
        }
        if show {
            startRetryLoopIfNeeded()
        } else {
            loopTask?.cancel()
            loopTask = nil
            secondsLeft = nil
            if isDismissed {
                isDismissed = false
            }
        }
    }

    private var shouldShow: Bool {
        switch need {
        case .none:
            return false
        case .loading:
            return waitElapsed
        case .failed:
            return !isOnline
        }
    }

    private func restartWaitTimer() {
        noDataTask?.cancel()
        noDataTask = nil
    }

    private func startNoDataTimerIfNeeded() {
        guard noDataTask == nil else {
            return
        }
        let sleep = self.sleep
        let delay = isOnline ? timings.noDataMillis : timings.offlineGraceMillis
        noDataTask = Task { [weak self] in
            await sleep(delay)
            guard !Task.isCancelled, let self else {
                return
            }
            noDataTask = nil
            waitElapsed = true
            refresh()
        }
    }

    /// Repart de zéro (compte à rebours et plafond) si la pop-in est en place.
    private func restartRetryLoop() {
        loopTask?.cancel()
        loopTask = nil
        if isShown {
            startRetryLoopIfNeeded()
        }
    }

    /// Un essai toutes les 10 s tant que la pop-in est en place, avec un compte à rebours d'une seconde par pas. Chaque essai ne relit que
    /// si le système dit que le réseau est là. Réseau annoncé au départ : au plus `maxAutomaticAttemptsOnline` essais, puis la boucle
    /// s'arrête (seuls « Réessayer » et le retour du réseau la relancent).
    private func startRetryLoopIfNeeded() {
        guard loopTask == nil else {
            return
        }
        let sleep = self.sleep
        let tick = max(1, timings.tickMillis)
        let steps = max(1, (timings.retryIntervalMillis + tick - 1) / tick)
        let cap = isOnline ? timings.maxAutomaticAttemptsOnline : Int.max
        loopTask = Task { [weak self] in
            var attempts = 0
            while !Task.isCancelled {
                if attempts >= cap {
                    self?.secondsLeft = nil
                    return
                }
                for left in stride(from: steps, through: 1, by: -1) {
                    guard !Task.isCancelled else {
                        return
                    }
                    self?.secondsLeft = left
                    await sleep(tick)
                }
                guard !Task.isCancelled, let self else {
                    return
                }
                if attemptIfOnline() {
                    attempts += 1
                }
            }
        }
    }

    /// Rend vrai quand un essai a eu lieu (réseau là).
    private func attemptIfOnline() -> Bool {
        guard isShown, network.isOnline() else {
            return false
        }
        onRetry(.automatic)
        return true
    }
}
