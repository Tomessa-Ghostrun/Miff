import Foundation

// Les contrats de « un compte, un seul appareil » (SESS-3). Du Swift pur : la vraie implémentation Firestore est dans
// `Services/Firebase/FirestoreDeviceSessionRemote.swift`, le stockage de l'identifiant dans `Services/Storage`. Les tests
// utilisent des doubles en mémoire.
//
// SÉCURITÉ : l'identifiant de session n'est JAMAIS journalisé, ni envoyé à Analytics ou à Crashlytics, ni mis dans un message
// d'erreur. Aucun type ci-dessous ne le décrit dans sa représentation texte.

/// Ce que le SERVEUR dit de `users/{uid}/session/active`.
enum RemoteSession: Equatable, CustomStringConvertible {
    /// Le document existe. `deviceId` est l'identifiant qu'il porte ; un document mal formé donne une chaîne vide, qui n'est
    /// jamais celle de cet appareil.
    case active(deviceId: String)
    /// Le serveur a répondu, et il n'y a pas de session.
    case absent

    var description: String {
        switch self {
        case .active:
            return "RemoteSession.active(redacted)"
        case .absent:
            return "RemoteSession.absent"
        }
    }
}

/// Le verdict d'une vérification de la session de cet appareil.
enum SessionCheck: Equatable {
    /// Le serveur confirme que la session est celle de cet appareil : les écritures de statut et de position sont permises.
    case confirmed
    /// Un autre appareil a pris la place, ou la session a disparu alors que cet appareil en avait une : déconnexion locale forcée.
    case replaced
    /// Le serveur n'a pas répondu (hors ligne, lenteur, refus) : personne n'est déconnecté (limite connue, validée par le PM),
    /// mais aucune écriture de statut ni de position ne part tant que le serveur n'a pas confirmé.
    case unverified
}

/// La réponse à « cet appareil peut-il écrire un statut ou une position ? » (LOC-4b).
enum DeviceClearance: Equatable, CustomStringConvertible {
    /// La session est celle de cet appareil, confirmée par le serveur : `deviceId` part dans l'écriture.
    case cleared(deviceId: String)
    /// Un autre appareil tient la session (ou elle a disparu) : cet appareil est déconnecté, rien ne s'écrit.
    case replaced
    /// Le serveur n'a pas répondu : rien ne s'écrit sur une supposition, ce qui attend est gardé.
    case unverified

    var description: String {
        switch self {
        case .cleared:
            return "DeviceClearance.cleared(redacted)"
        case .replaced:
            return "DeviceClearance.replaced"
        case .unverified:
            return "DeviceClearance.unverified"
        }
    }
}

/// Tout ce que l'app demande à Firestore au sujet de la session de l'appareil. Une seule opération par appel, jamais de
/// journal, jamais de message d'erreur remonté (un échec est `false` ou `nil`).
protocol DeviceSessionRemote {
    /// Écrit `{deviceId, startedAt: heure du serveur}` en remplaçant la session précédente. Vrai SEULEMENT après l'accusé du
    /// serveur (transaction : pas de file d'attente hors ligne, donc « pas d'accusé » veut toujours dire « rien d'écrit »).
    func write(uid: String, deviceId: String) async -> Bool

    /// Lit la session auprès du SERVEUR (jamais le cache). nil = pas de réponse du serveur.
    func read(uid: String) async -> RemoteSession?

    /// Supprime la session SEULEMENT si elle porte `deviceId` (lecture puis suppression dans une transaction) : une suppression à
    /// l'aveugle effacerait la session du nouvel appareil, ce que les règles ne peuvent pas empêcher. Vrai si c'est fait ou s'il
    /// n'y avait rien à supprimer pour cet appareil ; faux si le serveur n'a pas répondu (alors RIEN n'est supprimé).
    func deleteIfMine(uid: String, deviceId: String) async -> Bool

    /// Les instantanés CONFIRMÉS PAR LE SERVEUR du document de session, à chaque changement. Jamais un instantané du cache, jamais
    /// une écriture locale encore en attente : hors ligne personne n'est déconnecté. Le flux s'arrête quand on l'abandonne.
    func changes(uid: String) -> AsyncStream<RemoteSession>
}

/// Ce que l'appareil a gardé localement de sa session.
enum StoredDevice: Equatable, CustomStringConvertible {
    /// Aucun identifiant (première installation, réinstallation, ancienne installation, déconnexion).
    case absent
    /// Un identifiant tiré pour une connexion dont le serveur n'a PAS encore confirmé l'écriture. Il n'est jamais jugé comme une
    /// session active ; il est réutilisé par l'essai suivant (une écriture tardive d'un essai précédent écrit alors le même
    /// identifiant, donc ne peut pas écraser une session plus récente de cet appareil).
    case candidate(String)
    /// L'identifiant de la session confirmée par le serveur.
    case confirmed(String)
    /// Le stockage n'a pas pu être lu (erreur d'entrée-sortie, pas « fichier absent ») : état INDÉTERMINÉ. Jamais lu comme
    /// « remplacé » ni comme « aucune session » : rien n'est écrit, personne n'est déconnecté, on réessaiera.
    case unreadable

    var description: String {
        switch self {
        case .absent:
            return "StoredDevice.absent"
        case .candidate:
            return "StoredDevice.candidate(redacted)"
        case .confirmed:
            return "StoredDevice.confirmed(redacted)"
        case .unreadable:
            return "StoredDevice.unreadable"
        }
    }
}

/// L'état local de la session de CET appareil. L'identifiant ne doit PAS survivre à une désinstallation (le Trousseau iOS, lui, y
/// survit : il n'est donc jamais utilisé ici) ni voyager dans une sauvegarde. Le marqueur « révoqué » est persistant : une
/// déconnexion forcée interrompue (processus tué entre l'oubli de l'identifiant et la déconnexion) reprend au lancement suivant au
/// lieu de réécrire une session.
protocol DeviceIdStore {
    func read() -> StoredDevice
    /// Garde l'identifiant comme CANDIDAT (avant l'écriture sur le serveur). Faux s'il est mal formé ou n'a pas pu être gardé :
    /// alors rien n'est écrit sur le serveur.
    func saveCandidate(_ deviceId: String) -> Bool
    /// Le serveur a confirmé : le candidat devient l'identifiant de la session. Faux si cela n'a pas pu être enregistré.
    func confirm() -> Bool
    /// Efface l'identifiant (candidat ou confirmé). Ne touche pas au marqueur « révoqué ».
    func clear()
    func isRevoked() -> Bool
    /// Pose ou retire le marqueur « révoqué ». Faux si cela n'a pas pu être enregistré.
    func setRevoked(_ value: Bool) -> Bool
}
