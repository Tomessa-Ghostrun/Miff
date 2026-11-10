import Foundation

// Les portes du moteur vers l'extérieur (le système, le téléphone, Firestore), en petits protocoles : le moteur lui-même est testé avec
// des doubles, sans rien demander au système. CONFIDENTIALITÉ : un `Fix` ou une `TrackedZone` passe par ici ; aucun n'est jamais
// journalisé (leur `description` ne montre rien). Miroir de `TrackingPorts.kt` d'Android (LOC-2b).

/// Ce que le moteur demande au téléphone, au moment où il le demande.
protocol TrackingEnvironment {
    /// Le compte connecté, ou nil.
    func uid() -> String?

    /// L'état des autorisations (la localisation du téléphone lui-même allumée ou non comprise : `servicesEnabled`).
    func permissions() async -> PermissionSnapshot

    /// Le niveau de la batterie de 0 à 100, ou nil quand le téléphone ne le dit pas.
    func batteryPct() async -> Int?

    func isOnline() -> Bool

    /// L'heure du mur en millisecondes depuis 1970.
    func nowMillis() -> Int

    /// Ce que le système a coupé et qui peut retarder les relances (Actualisation en arrière-plan, mode économie d'énergie) : pour
    /// le journal de la recette seulement, jamais une décision.
    func systemConditions() async -> SystemConditions
}

/// Les réglages du système qui peuvent retarder ou empêcher une relance de l'app en arrière-plan (limites d'iOS à constater sur
/// l'iPhone : la recette les note).
struct SystemConditions: Equatable {
    var backgroundRefreshOff = false
    var lowPowerMode = false
}

/// Qui tient la session du compte (SESS-3) : les écritures n'ont lieu qu'avec son identifiant. `DeviceSession` en est l'implémentation.
protocol ActiveDevice {
    /// Remplacé ou déconnecté, et pas encore reconnecté.
    var isRevoked: Bool { get }

    /// L'identifiant à écrire, ou pourquoi rien ne s'écrit (le serveur est interrogé avant la première écriture d'une série ; un
    /// remplacement lance la déconnexion forcée).
    func clearance(uid: String) async -> DeviceClearance

    /// Après le refus d'une écriture : la session est relue auprès du serveur (un simple refus de consentement n'est pas un remplacement).
    func checkAfterRefusal(uid: String) async -> SessionCheck
}

extension DeviceSession: ActiveDevice {}

/// Ce que rend `PositionSource.currentReusingRecent()` : la position, et si elle vient de la dernière position du système (réutilisée,
/// aucune demande faite) plutôt que d'une mesure demandée. Sert au journal de test (BATT-3) ; `description` ne montre rien.
struct PositionReading: Equatable, CustomStringConvertible {
    let fix: Fix?
    let reused: Bool

    var description: String {
        "PositionReading(fix: \(fix == nil ? "nil" : "redacted"), reused: \(reused))"
    }
}

/// Où est la personne, quand le moteur le demande (un double dans les tests).
protocol PositionSource {
    /// Une position récente (quelques secondes au plus), ou nil quand le système ne peut pas en donner. TOUJOURS une mesure demandée
    /// au système (jamais la dernière position connue, sauf en repli si la demande échoue). LIT la position : n'est appelée qu'une fois
    /// l'accord et « Toujours » vérifiés. C'est ce que demandent l'entrée dans une zone, le déplacement significatif et la photo de
    /// l'Update (dont la position doit avoir moins de 10 minutes selon les règles : mesure fraîche à « Publier »).
    func current() async -> Fix?

    /// Comme `current()`, avec une attente choisie par l'appelant (l'Update attend plus longtemps que le moteur : une personne qui vient de
    /// toucher « Publier » patiente de toute façon). Plusieurs attentes en même temps sont possibles : aucune n'annule les autres.
    func current(within seconds: Double) async -> Fix?

    /// BATT-3, pour l'OUVERTURE de l'app et sa SORTIE seulement : la dernière position du système si elle a moins de
    /// `TrackingLimits.reusableFixMaxAgeMillis` et est assez précise, sans rien demander ; sinon une mesure demandée, comme `current()`.
    /// Une position mesurée avant la dernière déconnexion n'est jamais réutilisée (elle peut être celle d'un autre compte).
    func currentReusingRecent() async -> PositionReading
}

/// La surveillance de régions du système (20 au plus).
protocol GeofenceArming {
    /// Rend la surveillance conforme à `desired` (20 zones au plus) : les régions que le système détient et qui n'y sont plus sont
    /// retirées. `reregister` vrai : TOUT l'ensemble est de nouveau enregistré (opération idempotente : une région de même nom est
    /// remplacée), car la liste gardée sur le téléphone peut se tromper sur ce que le système détient ; faux : seulement ce qui manque
    /// (une zone déjà surveillée n'est pas réarmée). Vrai quand le système a accepté. Pour chaque région enregistrée, le système est
    /// aussi invité à dire si la personne est dedans (`TrackingEventSink.zoneStateKnown`).
    func arm(desired: [TrackedZone], reregister: Bool) async -> Bool

    /// Demande au système de redire si la personne est dans ces zones (réponse : `TrackingEventSink.zoneStateKnown`). Seules les
    /// clés données sont demandées.
    func requestStates(keys: [String]) async

    /// Retire toutes les régions de Miff (déconnexion, plus de partage...).
    func disarmAll() async
}

/// Les signalements de « la personne a changé de lieu de façon significative », rendus à l'app même fermée.
protocol MovementUpdates {
    /// Demande au système de signaler les changements de position significatifs (remplace la demande précédente) ; faux quand il refuse.
    func start() async -> Bool

    func stop() async
}

/// Ce que le moteur lit et écrit dans Firestore. Les écritures sont des TRANSACTIONS (jamais mises en attente hors ligne, voir
/// `MiffStore`) : « pas de réponse » veut toujours dire « rien d'écrit ».
protocol TrackingStore {
    func memberships(uid: String) async throws -> [MembershipRef]

    /// Mon consentement dans une Miff, lu auprès du SERVEUR (nil quand il n'y a pas de fiche).
    func consent(miffId: String, uid: String) async throws -> SharingConsentRead?

    /// Les lieux d'une Miff, lus auprès du SERVEUR : seulement ce qu'il faut pour les surveiller.
    func places(miffId: String) async throws -> [PlaceFact]

    func commit(_ writes: [DocWrite]) async throws
}

/// Ce que le système signale à l'app et que le moteur doit traiter. Rien de ce qui arrive ici ne porte l'heure ou la position d'un
/// ÉVÉNEMENT DE ZONE (le système n'en donne pas, et on ne la lit jamais) : l'heure est celle où il arrive à l'app.
@MainActor
protocol TrackingEventSink {
    func zoneTransition(_ kind: TransitionKind, zoneKey: String)
    func zoneStateKnown(zoneKey: String, isInside: Bool)
    func significantMove(_ fix: Fix)
    func accessChanged()
    func monitoringFailed()
}
