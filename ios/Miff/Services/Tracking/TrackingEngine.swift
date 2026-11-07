import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier (critères AUT7, POS1 à POS8, DET1 à DET4, STA1 à STA4, VIE1 à VIE3, HOR1) :
// - RIEN n'est surveillé, lu ni écrit tant que la personne n'est pas connectée, n'a pas « Toujours » ET la position exacte, ET ne
//   partage pas avec une Miff sous le consentement de version 3. Chaque condition est revérifiée à CHAQUE entrée (un retrait est
//   constaté au réveil suivant et tout s'arrête).
// - Une position n'est lue que par `PositionSource` ; elle est gardée dans la copie locale privée (pour choisir les 20 lieux les plus
//   proches), écrite dans Firestore SEULEMENT comme dernière position des Miffs qui en remplissent les conditions (une écriture par
//   Miff, seule), et jamais journalisée, jamais envoyée comme événement, jamais copiée ailleurs. Les événements de zone du système
//   ne donnent pas de position, et on n'en lit pas.
// - Le statut ne porte que des faits ; la couleur n'est jamais écrite. L'heure écrite est l'heure RÉELLE de l'événement.
// - Tout est effacé par `stop()` (déconnexion, suppression du compte, session perdue) et, Miff par Miff, quand le partage ou l'accès
//   est perdu. `stop()` est COLLANT pour la session qui s'est terminée : rien n'est reconstruit tant que la session n'a pas changé.
// - « Un compte, un seul appareil » (SESS-3) : RIEN n'est écrit (statut, accès, position) sans `ActiveDevice.clearance`, vérifié auprès
//   du serveur avant la première écriture d'un passage ; chaque écriture porte l'identifiant de la session ; un téléphone remplacé ne
//   garde que ce qui attend (effacé par `stop()`, que la déconnexion forcée appelle) ; une écriture refusée fait relire la session.
// Aucun journal, aucun événement Analytics : seulement le journal local des builds de test, qui ne peut pas contenir une coordonnée.
// Miroir de `TrackingEngine.kt` d'Android (LOC-2b), avec les différences d'iOS (régions du système, états des régions).

/// Tout ce dont le moteur a besoin. Regroupé pour qu'une construction tienne en un argument.
struct TrackingDependencies {
    let env: TrackingEnvironment
    let store: TrackingStore
    let local: TrackingLocalStore
    let position: PositionSource
    let geofences: GeofenceArming
    let movement: MovementUpdates
    /// Qui tient la session du compte : les écritures ne partent qu'avec son identifiant.
    let device: ActiveDevice
    var journal: DetectionJournal = NoDetectionJournal()
    var timeoutSeconds: TimeInterval = 15
    /// Monte à chaque fin de session (`SessionManager.generation`) : ce que `stop()` a arrêté reste arrêté jusqu'à ce qu'elle change.
    var generation: () -> Int = { 0 }
}

/// La permission d'écrire pendant UN passage (un rafraîchissement, un événement, un retour du réseau) : la session est vérifiée à la
/// PREMIÈRE écriture seulement (un passage qui n'écrit rien ne coûte aucune lecture), la réponse est gardée pour le reste du passage,
/// et un refus qui se révèle être un remplacement la ferme aussitôt.
final class WritePermission {
    private let engine: TrackingEngine
    private let uid: String
    private let born: Int
    private var asked = false
    private var identifier: String?

    init(engine: TrackingEngine, uid: String) {
        self.engine = engine
        self.uid = uid
        born = engine.deps.generation()
    }

    /// Vrai quand la session de ce passage est finie (arrêt demandé, ou nouvelle session commencée) : plus rien ne s'écrit.
    func ended() -> Bool {
        engine.passEnded(born: born)
    }

    /// L'identifiant à écrire, ou nil : rien ne s'écrit maintenant (fini, remplacé, ou le serveur n'a pas pu être interrogé).
    func deviceId() async -> String? {
        if ended() {
            return nil
        }
        if !asked {
            asked = true
            if case .cleared(let deviceId) = await engine.deps.device.clearance(uid: uid) {
                identifier = deviceId
            }
        }
        return identifier
    }

    /// Une écriture a été refusée : est-ce parce que la session n'est plus la nôtre ? Alors rien d'autre ne s'écrit dans ce passage.
    func refused() async {
        if await engine.deps.device.checkAfterRefusal(uid: uid) == .replaced {
            asked = true
            identifier = nil
        }
    }
}

/// Ce qui est lu une fois par passage : les autorisations, la batterie, la permission d'écrire.
struct PassContext {
    let uid: String
    let snapshot: PermissionSnapshot
    let batteryPct: Int?
    let permission: WritePermission

    var access: LocationAccess {
        LocationAccessRule.of(snapshot)
    }
}

/// Le moteur de suivi des lieux : surveille les 20 zones les plus proches, déduit le statut de chaque Miff des arrivées et des départs,
/// et écrit le statut et la dernière position. Les entrées sont SÉRIALISÉES (une seule à la fois, dans l'ordre d'arrivée).
final class TrackingEngine {
    /// Pourquoi le moteur est prié de regarder de nouveau (ticket §6.5 : l'ouverture, le passage en arrière-plan, un déplacement
    /// significatif, une entrée dans une zone ; plus ce que le partage ou les autorisations changent).
    enum Reason: Equatable {
        case opened
        case backgrounded
        case moved
        case zoneEntered
        case rearm
        case sharingChanged
        case networkBack
        case accessChanged
    }

    let deps: TrackingDependencies
    private let gate = AsyncGate()
    private let lock = NSLock()
    private var stoppedGeneration: Int?
    /// Le signalement des changements de position significatifs a-t-il été demandé AU SYSTÈME dans CE processus ? Un processus relancé
    /// par le système doit le redemander (le guide d'Apple veut que les services de localisation soient redémarrés après une
    /// relance), même si la copie locale dit que c'était déjà fait.
    private var movementStarted = false

    init(_ deps: TrackingDependencies) {
        self.deps = deps
    }

    var env: TrackingEnvironment {
        deps.env
    }

    var journal: DetectionJournal {
        deps.journal
    }

    // MARK: - Entrées (sérialisées)

    /// L'app est passée au premier plan ou en arrière-plan, le système a signalé un déplacement, le partage ou les autorisations ont changé.
    func refresh(_ reason: Reason, supplied: Fix? = nil) async {
        await serialized {
            if !self.sessionEnded() {
                await self.doRefresh(reason, supplied: supplied)
            }
        }
    }

    /// La surveillance de régions dit : la personne est entrée dans la zone `zoneKey`, ou en est sortie. La position qui accompagne
    /// l'événement n'est JAMAIS lue (le pont du système ne la transmet pas).
    func onTransition(_ kind: TransitionKind, zoneKey: String, atMillis: Int) async {
        await serialized {
            if !self.sessionEnded() {
                await self.doTransition(ZoneTransition(kind: kind, zoneKey: zoneKey, atMillis: atMillis))
            }
        }
    }

    /// Le système a dit, à notre demande, si la personne est dans la zone (à l'armement : DET3, « déjà dedans = arrivée, dehors =
    /// aucun `out` inventé »).
    func onZoneState(zoneKey: String, isInside: Bool) async {
        await serialized {
            if !self.sessionEnded() {
                await self.doZoneState(zoneKey: zoneKey, isInside: isInside)
            }
        }
    }

    /// Le réseau est revenu : ce qui attend est écrit, mais seulement après avoir revérifié les conditions (« Toujours » toujours là,
    /// partage et consentement de version 3 relus du serveur). Ne lit aucune position.
    func onNetworkBack() async {
        await serialized {
            if !self.sessionEnded() {
                await self.doNetworkBack()
            }
        }
    }

    /// Déconnexion, suppression du compte, session perdue : tout s'arrête, est effacé, et reste arrêté pour cette session.
    func stop() async {
        // Marqué AVANT l'attente de la porte : un passage qui n'a pas commencé ne commencera pas ; l'effacement ci-dessous a quand même
        // lieu quand le passage en cours se termine.
        markStopped()
        await serialized {
            await self.doStop()
        }
    }

    private func serialized(_ body: @escaping () async -> Void) async {
        await gate.run(body)
    }

    // MARK: - Session

    private func markStopped() {
        lock.lock()
        stoppedGeneration = deps.generation()
        lock.unlock()
    }

    func isMovementStarted() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return movementStarted
    }

    func setMovementStarted(_ value: Bool) {
        lock.lock()
        movementStarted = value
        lock.unlock()
    }

    /// Retire le signalement des changements significatifs (et se souvient qu'il faudra le redemander).
    func stopMovement() async {
        await deps.movement.stop()
        setMovementStarted(false)
    }

    func sessionEnded() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return stoppedGeneration == deps.generation()
    }

    /// Un passage né dans la génération `born` est fini quand l'arrêt a été demandé pour elle, ou quand une autre a commencé.
    func passEnded(born: Int) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return stoppedGeneration == born || deps.generation() != born
    }

    func makePass(uid: String) async -> PassContext {
        let snapshot = await env.permissions()
        let battery = await env.batteryPct()
        return PassContext(
            uid: uid,
            snapshot: snapshot,
            batteryPct: battery,
            permission: WritePermission(engine: self, uid: uid)
        )
    }

    // MARK: - Arrêt

    func doStop() async {
        await deps.geofences.disarmAll()
        await stopMovement()
        deps.local.clear()
        journal.clear()
    }

    /// La copie locale de CE compte, ou nil. La copie d'un autre compte est effacée sur-le-champ (jamais réutilisée).
    func stateFor(_ uid: String) -> LocalState? {
        guard let loaded = deps.local.load() else {
            return nil
        }
        if loaded.uid == uid {
            return loaded
        }
        deps.local.clear()
        return nil
    }

    /// Plus rien n'est surveillé (l'accès ou le consentement a disparu) : régions et signalements retirés, et tout ce qui dépend de la
    /// position ou du consentement est effacé : où est la personne, sa dernière position, les statuts en attente. Les lieux des Miffs
    /// sont gardés (peu coûteux, aucun secret).
    func disarm(_ state: LocalState) async -> LocalState {
        await deps.geofences.disarmAll()
        await stopMovement()
        journal.record(.disarmed)
        var next = state
        for miffId in state.miffs.keys {
            next.update(miffId) { $0.resetDetection() }
        }
        next.inside = [:]
        next.armed = []
        next.fix = nil
        next.updatesArmed = false
        return next
    }
}
