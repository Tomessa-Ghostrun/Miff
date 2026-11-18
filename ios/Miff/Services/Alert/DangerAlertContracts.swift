#if ALERT_DANGER
import Foundation

// ALERTE « Je suis en danger » : les contrats entre l'alerte et le monde extérieur (Firestore, le disque). Du Swift pur : les vraies
// implémentations sont `FirestoreDangerAlertStore` (Services/Firebase) et `DangerAlertFileStore`, les tests utilisent des doubles en mémoire.
//
// Tout ce fichier est compilé SEULEMENT quand la condition de compilation `ALERT_DANGER` existe (project.yml : Debug et Internal oui,
// Release non). Miroir d'`AlertStore.kt` et d'`AlertLocalStore.kt` d'Android.

/// Ce que démarrer une alerte a donné.
enum DangerStartResult: Equatable {
    /// Écrite ; `startedAtMillis` est l'horloge du téléphone (la date du serveur est relue par les écouteurs).
    case started(startedAtMillis: Int, expiresAtMillis: Int)
    /// Une alerte de moi est déjà en cours dans cette Miff (un autre téléphone, ou une réponse perdue) : elle est ADOPTÉE, rien n'est écrit.
    case adopted(DangerAlertDoc)
    /// L'alerte précédente de moi dans cette Miff a démarré il y a moins de 60 s : attendre `waitMillis` (les règles refuseraient plus tôt).
    case tooSoon(waitMillis: Int)
    case failed(DangerAlertFailure)
}

enum DangerEndResult: Equatable {
    /// L'alerte est terminée sur le serveur (par cette écriture, ou elle l'était déjà, ou le document a disparu).
    case ended
    case failed(DangerAlertFailure)
}

/// Ce que la lecture de MON alerte d'une Miff, auprès du SERVEUR, a donné.
enum DangerMineRead: Equatable {
    /// `nil` : il n'y a pas de document d'alerte pour moi dans cette Miff.
    case value(DangerAlertDoc?)
    case unavailable
}

/// Les alertes d'UNE Miff telles que l'écouteur du Feed les entend.
struct DangerAlertsHeard: Equatable, CustomStringConvertible {
    let docs: [DangerAlertDoc]
    /// Vrai tant que ce qui est tenu vient du cache du téléphone et que le serveur ne l'a pas confirmé.
    let fromCache: Bool

    var description: String {
        "DangerAlertsHeard(redacted)"
    }
}

/// Ce dont le Feed a besoin des alertes : celles d'UNE Miff, en direct, pendant qu'elle est à l'écran. Froide : l'abandonner retire l'écoute.
protocol DangerAlertFeed {
    /// Le flux se termine quand l'écoute se termine (un refus : les règles ne sont pas à jour, l'accès est perdu) : « pas d'alerte », jamais une
    /// erreur sur le Feed.
    func observe(miffId: String) -> AsyncStream<DangerAlertsHeard>
}

protocol DangerAlertStore: DangerAlertFeed {
    /// Démarre (crée, ou ré-arme après une fin ou une échéance) MON alerte dans `miffId`. Ne lève jamais.
    func start(uid: String, miffId: String, deviceId: String, nowMillis: Int) async -> DangerStartResult

    /// Termine MON alerte dans `miffId`. Ne lève jamais.
    func end(uid: String, miffId: String, deviceId: String, nowMillis: Int) async -> DangerEndResult

    /// UNE lecture, auprès du serveur, de mon alerte de `miffId` (un nouveau téléphone qui adopte une alerte, le retour au premier plan).
    func readMine(uid: String, miffId: String) async -> DangerMineRead
}

/// Où en est le pointeur : l'alerte est en route, ou elle est en cours, ou la personne l'a ABANDONNÉE (elle n'a pas été vue partir) et le serveur n'a pas encore confirmé
/// qu'aucune alerte vivante n'y est.
enum DangerPointerPhase: String, Equatable {
    case sending
    case active
    case abandoned
}

/// Ce que le téléphone retient de MON alerte, et rien d'autre. Un pointeur (l'identifiant de la Miff d'origine, la phase, trois instants) :
/// aucun nom, aucune position, aucun texte. Il laisse l'alerte survivre à un relancement de l'app et laisse l'app écouter la bonne Miff
/// pendant que le Feed en montre une autre (ticket 6).
struct DangerAlertPointer: Equatable, CustomStringConvertible {
    let miffId: String
    let phase: DangerPointerPhase
    /// Quand les 5 s ont été atteintes (horloge du téléphone) : les 15 minutes après lesquelles l'envoi est reconfirmé partent d'ici.
    let armedAtMillis: Int
    var startedAtMillis: Int?
    var expiresAtMillis: Int?
    /// La personne a confirmé une alerte de plus de 15 min : elle peut partir.
    var staleConfirmed = false

    var description: String {
        "DangerAlertPointer(redacted)"
    }
}

protocol DangerAlertLocalStore: AnyObject {
    func pointer() -> DangerAlertPointer?
    func save(_ pointer: DangerAlertPointer)
    func clearPointer()
    /// La bulle bleue de l'onboarding a fait son travail (ticket 7.1, AD8) : l'indicateur concerne l'INSTALLATION, pas le compte.
    func hintDone() -> Bool
    func markHintDone()
    /// Le compte dont les alertes ont été lues UNE fois auprès du serveur à la connexion d'un nouveau téléphone (ticket 6, AD42).
    func adoptedFor() -> String?
    func markAdoptedFor(_ uid: String)
    /// Déconnexion : le pointeur et la note de l'adoption partent ; l'indicateur de la bulle (installation) reste.
    func clearAccount()
}
#endif
