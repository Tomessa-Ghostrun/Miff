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
    private(set) var writeAttempts = 0
    private(set) var writtenIds: [String] = []
    private(set) var readCalls = 0
    private(set) var deleteCalls = 0
    private(set) var deletedCount = 0
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
        AsyncStream { continuation in
            self.continuation = continuation
        }
    }

    /// Un autre téléphone vient de se connecter avec ce compte : le document change, l'écoute en est avertie.
    func anotherPhoneSignsIn(_ identifier: String = "other-phone-0123456789") {
        serverId = identifier
        continuation?.yield(.active(deviceId: identifier))
    }

    /// Le document disparaît (compte supprimé depuis un autre appareil, par exemple).
    func sessionDisappears() {
        serverId = nil
        continuation?.yield(.absent)
    }
}

final class MemoryDeviceIdStore: DeviceIdStore {
    var stored: String?
    var saveFails = false

    func read() -> String? {
        stored
    }

    func save(_ deviceId: String) -> Bool {
        guard !saveFails, DeviceId.isValid(deviceId) else {
            return false
        }
        stored = deviceId
        return true
    }

    func clear() {
        stored = nil
    }
}
