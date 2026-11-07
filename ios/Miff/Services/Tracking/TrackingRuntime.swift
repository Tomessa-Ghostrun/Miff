import Foundation

// CONFIDENTIALITÉ ET SÉCURITÉ, pour ce fichier : il relie le système (premier plan, arrière-plan, événements de zone, déplacement
// significatif, retour du réseau) au moteur. Il ne lit aucune position lui-même, n'écrit rien dans Firestore, n'envoie aucun événement
// Analytics, ne journalise que par le journal local des builds de test (qui ne peut pas contenir une coordonnée).
//
// Fenêtre de relance : quand le système relance l'app terminée pour un événement de zone ou un déplacement significatif, elle a une
// dizaine de secondes. Chaque travail demande donc du temps d'arrière-plan (`beginBackgroundTask`) et se borne à `wakeBudgetSeconds` :
// ce qui n'est pas fini est déjà gardé dans la copie locale (le statut en attente est écrit sur le disque AVANT l'envoi) et part au
// réveil suivant ; rien n'est perdu.

@MainActor
final class TrackingRuntime: TrackingEventSink {
    let engine: TrackingEngine
    private let background: BackgroundExecution
    private let journal: DetectionJournal
    /// Ce qui passe avant tout accès à Firestore d'un réveil : une purge du cache laissée par une déconnexion qui avait échoué.
    private let beforeWork: () async -> Void
    private var networkTask: Task<Void, Never>?
    /// Le dernier travail lancé : chaque nouveau travail attend la fin du précédent, pour que les événements du système (une sortie,
    /// puis un « dehors ») soient traités DANS L'ORDRE où ils sont arrivés.
    private var lastWork: Task<Void, Never>?
    /// Quand l'app est passée au premier plan pour la dernière fois (le démarrage à froid le signale deux fois).
    private var lastActiveMillis: Int?
    /// Quand le dernier échec de la surveillance de régions a fait tout réenregistrer (millisecondes depuis 1970).
    private var lastRearmMillis: Int?

    init(
        engine: TrackingEngine,
        background: BackgroundExecution,
        journal: DetectionJournal,
        beforeWork: @escaping () async -> Void
    ) {
        self.engine = engine
        self.background = background
        self.journal = journal
        self.beforeWork = beforeWork
    }

    /// Suit le réseau pour la vie du processus : au retour du réseau, ce qui attend d'être écrit l'est (si le processus tourne ; une app
    /// suspendue ou terminée ne peut pas être réveillée par le retour du réseau sur iOS : le réveil suivant, par une zone ou un
    /// déplacement significatif, s'en charge, avec l'heure réelle de l'événement tant qu'elle a moins de 24 h).
    func start(network: NetworkMonitor) {
        guard networkTask == nil else {
            return
        }
        networkTask = Task { [weak self] in
            var wasOnline = true
            for await online in network.onlineChanges() {
                if online && !wasOnline {
                    self?.networkBack()
                }
                wasOnline = online
            }
        }
    }

    /// Du temps de plus (le système l'accorde), le travail, puis le temps est rendu. Borné : jamais plus que la fenêtre de relance.
    /// Strictement ORDONNÉ : le travail commence quand le précédent est fini (ou a épuisé son temps).
    private func run(_ work: @escaping () async -> Void) {
        let token = background.begin()
        let prepare = beforeWork
        let previous = lastWork
        lastWork = Task {
            await previous?.value
            _ = try? await timedOperation(seconds: TrackingLimits.wakeBudgetSeconds) {
                await prepare()
                await work()
            }
            self.background.end(token)
        }
    }

    // MARK: - Ce que l'app signale

    func appBecameActive() {
        // Le démarrage à froid signale deux fois le premier plan : la seconde demande, dans les secondes qui suivent, est ignorée.
        let now = Int(Date().timeIntervalSince1970 * 1000)
        if let last = lastActiveMillis, now - last < TrackingLimits.duplicateOpenMillis {
            return
        }
        lastActiveMillis = now
        let engine = self.engine
        run { await engine.refresh(.opened) }
    }

    func appWentToBackground() {
        let engine = self.engine
        run { await engine.refresh(.backgrounded) }
    }

    /// Le partage a changé (entrer dans une Miff, arrêter ou reprendre, quitter, être retiré, accepter un nouveau texte) ou un lieu a
    /// changé : les Miffs et les lieux sont relus.
    func sharingChanged() {
        let engine = self.engine
        run { await engine.refresh(.sharingChanged) }
    }

    private func networkBack() {
        let engine = self.engine
        run { await engine.onNetworkBack() }
    }

    /// Fin de session : tout s'arrête et est effacé (`TrackingEngine.stop`). Le suivi du réseau continue : il vit autant que le processus
    /// et le moteur ne fait rien sans session.
    func stop() async {
        await engine.stop()
    }

    // MARK: - TrackingEventSink (ce que le système signale)

    func zoneTransition(_ kind: TransitionKind, zoneKey: String) {
        let engine = self.engine
        let at = Int(Date().timeIntervalSince1970 * 1000)
        run { await engine.onTransition(kind, zoneKey: zoneKey, atMillis: at) }
    }

    func zoneStateKnown(zoneKey: String, isInside: Bool) {
        let engine = self.engine
        run { await engine.onZoneState(zoneKey: zoneKey, isInside: isInside) }
    }

    func significantMove(_ fix: Fix) {
        journal.record(.wakeSignificantMove)
        let engine = self.engine
        run { await engine.refresh(.moved, supplied: fix) }
    }

    func accessChanged() {
        let engine = self.engine
        run { await engine.refresh(.accessChanged) }
    }

    /// Le système n'a pas pu surveiller une région (trop de régions, autorisation retirée, service coupé) : tout est réenregistré, mais
    /// au plus une fois toutes les cinq minutes, pour qu'un échec qui se répète ne fasse jamais une boucle.
    func monitoringFailed() {
        journal.record(.monitoringFailed)
        let now = Int(Date().timeIntervalSince1970 * 1000)
        if let last = lastRearmMillis, now - last < TrackingLimits.rearmMinIntervalMillis {
            return
        }
        lastRearmMillis = now
        let engine = self.engine
        run { await engine.refresh(.rearm) }
    }
}
