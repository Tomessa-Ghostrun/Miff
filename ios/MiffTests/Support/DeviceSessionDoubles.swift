import Foundation
@testable import Miff

// Doubles en mémoire de « un compte, un seul appareil » (SESS-3). Aucun ne touche au réseau ni à Firebase ; les identifiants sont des
// valeurs de démonstration.

/// Le serveur de la session d'appareil (SESS-3) : un seul document, comme `users/{uid}/session/active`. `reachable` faux = hors
/// ligne (aucune écriture, lecture ni suppression ne répond). Les écritures et suppressions sont notées dans la chronologie
/// partagée (« sessionWrite », « sessionDelete ») pour prouver l'ordre avec la purge et la déconnexion.
final class FakeDeviceSessionRemote: DeviceSessionRemote {
    private let timeline: Timeline
    /// L'identifiant que porte le document sur le serveur (nil = pas de document).
    var serverId: String?
    var reachable = true
    /// Les prochaines écritures qui échouent (réponse perdue), pour prouver le second essai.
    var writesToFail = 0
    /// Une écriture dure ce temps (nanosecondes) : pour que deux `begin` se chevauchent s'ils n'étaient pas sérialisés.
    var writeDelayNanos: UInt64 = 0
    /// Appelé quand une écriture arrive au serveur, avant qu'elle soit appliquée (pour observer l'état local à cet instant).
    var onWrite: () -> Void = {}
    private(set) var writeAttempts = 0
    private(set) var maxConcurrentWrites = 0
    private var activeWrites = 0
    private(set) var writtenIds: [String] = []
    private(set) var readCalls = 0
    private(set) var deleteCalls = 0
    private(set) var deletedCount = 0
    private(set) var changesCalls = 0
    private var continuation: AsyncStream<RemoteSession>.Continuation?

    init(timeline: Timeline = Timeline()) {
        self.timeline = timeline
    }

    private var current: RemoteSession {
        serverId.map { RemoteSession.active(deviceId: $0) } ?? .absent
    }

    /// Tout ce que ce double a reçu du client : rien n'a été écrit ni supprimé quand c'est vide.
    var touched: Bool {
        !writtenIds.isEmpty || deleteCalls > 0
    }

    func write(uid: String, deviceId: String) async -> Bool {
        writeAttempts += 1
        onWrite()
        activeWrites += 1
        maxConcurrentWrites = max(maxConcurrentWrites, activeWrites)
        if writeDelayNanos > 0 {
            try? await Task.sleep(nanoseconds: writeDelayNanos)
        }
        activeWrites -= 1
        guard reachable else {
            return false
        }
        if writesToFail > 0 {
            writesToFail -= 1
            return false
        }
        writtenIds.append(deviceId)
        serverId = deviceId
        timeline.add("sessionWrite")
        continuation?.yield(.active(deviceId: deviceId))
        return true
    }

    func read(uid: String) async -> RemoteSession? {
        readCalls += 1
        return reachable ? current : nil
    }

    func deleteIfMine(uid: String, deviceId: String) async -> Bool {
        deleteCalls += 1
        guard reachable else {
            return false
        }
        if serverId == deviceId {
            serverId = nil
            deletedCount += 1
            timeline.add("sessionDelete")
            continuation?.yield(.absent)
        }
        return true
    }

    func changes(uid: String) -> AsyncStream<RemoteSession> {
        changesCalls += 1
        return AsyncStream { continuation in
            self.continuation = continuation
        }
    }

    /// Un autre téléphone vient de se connecter avec ce compte : le document change, l'écoute en est avertie.
    func anotherPhoneSignsIn(_ identifier: String = "other-phone-0123456789") {
        serverId = identifier
        continuation?.yield(.active(deviceId: identifier))
    }

    /// La suppression « quel que soit le détenteur » de la suppression d'un compte (AUTH-5c).
    private(set) var unconditionalDeletes = 0

    func delete(uid: String) async -> Bool {
        unconditionalDeletes += 1
        guard reachable else {
            return false
        }
        if serverId != nil {
            serverId = nil
            deletedCount += 1
            timeline.add("sessionDelete")
        }
        return true
    }

    /// Le document disparaît (compte supprimé depuis un autre appareil, par exemple).
    func sessionDisappears() {
        serverId = nil
        continuation?.yield(.absent)
    }

    /// L'écoute se termine toute seule (erreur du serveur), sans que le client l'ait abandonnée.
    func endListening() {
        continuation?.finish()
        continuation = nil
    }
}

/// Le stockage local de la session, en mémoire : `stored` est l'identifiant, `isCandidate` dit s'il attend la confirmation du
/// serveur. Chaque panne du disque se simule par un drapeau.
final class MemoryDeviceIdStore: DeviceIdStore {
    var stored: String?
    var isCandidate = false
    var revoked = false
    /// Disque plein : l'identifiant ne peut pas être gardé.
    var saveFails = false
    /// Le candidat ne peut pas être confirmé.
    var confirmFails = false
    /// Erreur de lecture (autre chose que « fichier absent »).
    var unreadable = false
    /// Le marqueur « révoqué » ne peut pas être retiré.
    var revokedClearFails = false

    func read() -> StoredDevice {
        if unreadable {
            return .unreadable
        }
        guard let stored else {
            return .absent
        }
        return isCandidate ? .candidate(stored) : .confirmed(stored)
    }

    func saveCandidate(_ deviceId: String) -> Bool {
        guard !saveFails, DeviceId.isValid(deviceId) else {
            return false
        }
        stored = deviceId
        isCandidate = true
        return true
    }

    func confirm() -> Bool {
        guard stored != nil, !confirmFails else {
            return false
        }
        isCandidate = false
        return true
    }

    func clear() {
        stored = nil
        isCandidate = false
    }

    func isRevoked() -> Bool {
        revoked
    }

    func setRevoked(_ value: Bool) -> Bool {
        if !value && revokedClearFails {
            return false
        }
        revoked = value
        if !value {
            // Comme le fichier réel : la raison part avec le marqueur.
            gone = false
        }
        return true
    }

    /// La raison de la révocation : le document de session a disparu (le compte est supprimé ailleurs).
    var gone = false

    func isGone() -> Bool {
        gone
    }

    func setGone(_ value: Bool) -> Bool {
        gone = value
        return true
    }
}
