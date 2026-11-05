import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : l'identifiant de session est aléatoire, mais il n'est JAMAIS journalisé, ni mis dans un événement
// Analytics, un rapport de plantage ou un message d'erreur. Les erreurs de Firestore ne sortent pas d'ici : un échec est `false`
// ou `nil`. Aucun chemin n'est construit avec un identifiant de compte qui n'a pas la forme d'un identifiant de compte Firebase.

/// `DeviceSessionRemote` branché sur `users/{uid}/session/active`. L'instance Firestore est résolue à CHAQUE usage, jamais gardée :
/// elle est remplacée après une purge du cache. Rien ici n'ouvre Firestore avant qu'un écran ne le demande.
final class FirestoreDeviceSessionRemote: DeviceSessionRemote {
    /// Comme Android : 8 secondes. Un serveur qui ne répond pas est « non confirmé », jamais « remplacé ».
    private static let timeoutSeconds: TimeInterval = 8

    func write(uid: String, deviceId: String) async -> Bool {
        guard FirebaseBootstrap.isConfigured, Self.isValidUid(uid), DeviceId.isValid(deviceId) else {
            return false
        }
        do {
            try await timedOperation(seconds: Self.timeoutSeconds) {
                try await Self.commitWrite(uid: uid, deviceId: deviceId)
            }
            return true
        } catch {
            return false
        }
    }

    func read(uid: String) async -> RemoteSession? {
        guard FirebaseBootstrap.isConfigured, Self.isValidUid(uid) else {
            return nil
        }
        do {
            return try await timedOperation(seconds: Self.timeoutSeconds) {
                try await Self.fetch(uid: uid)
            }
        } catch {
            return nil
        }
    }

    func deleteIfMine(uid: String, deviceId: String) async -> Bool {
        guard FirebaseBootstrap.isConfigured, Self.isValidUid(uid), DeviceId.isValid(deviceId) else {
            return false
        }
        do {
            try await timedOperation(seconds: Self.timeoutSeconds) {
                try await Self.commitDeleteIfMine(uid: uid, deviceId: deviceId)
            }
            return true
        } catch {
            return false
        }
    }

    func changes(uid: String) -> AsyncStream<RemoteSession> {
        AsyncStream { continuation in
            guard FirebaseBootstrap.isConfigured, Self.isValidUid(uid) else {
                continuation.finish()
                return
            }
            let reference = Self.documentReference(uid: uid, in: Firestore.firestore())
            let listener = reference.addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                guard error == nil, let snapshot else {
                    // Une écoute qui échoue est terminée par Firestore ; la vérification au retour du réseau prend le relais.
                    continuation.finish()
                    return
                }
                // Hors ligne personne n'est déconnecté : le cache ne dit jamais « remplacée » ni « disparue ». Une écriture locale
                // encore en attente est la nôtre, pas celle d'un autre appareil.
                if snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites {
                    return
                }
                continuation.yield(FirestoreDeviceSessionRemote.remoteSession(of: snapshot))
            }
            continuation.onTermination = { _ in
                listener.remove()
            }
        }
    }

    // MARK: - Intérieur

    /// Un identifiant de compte Firebase : non vide, sans séparateur de chemin.
    private static func isValidUid(_ uid: String) -> Bool {
        !uid.isEmpty && !uid.contains("/")
    }

    private static func documentReference(uid: String, in database: Firestore) -> DocumentReference {
        database.collection("users").document(uid).collection("session").document("active")
    }

    /// Un document sans identifiant bien formé n'est jamais celui de cet appareil : il est lu comme une session d'un autre.
    static func remoteSession(of snapshot: DocumentSnapshot) -> RemoteSession {
        guard snapshot.exists else {
            return .absent
        }
        let identifier = snapshot.data()?[SessionWriteGuard.field] as? String
        if let identifier, DeviceId.isValid(identifier) {
            return .active(deviceId: identifier)
        }
        return .active(deviceId: "")
    }

    private static func commitWrite(uid: String, deviceId: String) async throws {
        let database = Firestore.firestore()
        let reference = documentReference(uid: uid, in: database)
        // Une TRANSACTION, comme les autres écritures de l'app : hors ligne elle échoue au lieu d'attendre en file, donc « pas
        // d'accusé » veut toujours dire « rien d'écrit ». L'heure de début est celle du serveur (les règles refusent le reste).
        _ = try await database.runTransaction { transaction, _ in
            transaction.setData(
                [SessionWriteGuard.field: deviceId, "startedAt": FieldValue.serverTimestamp()],
                forDocument: reference
            )
            return nil
        }
    }

    private static func fetch(uid: String) async throws -> RemoteSession {
        let snapshot = try await documentReference(uid: uid, in: Firestore.firestore()).getDocument(source: .server)
        return remoteSession(of: snapshot)
    }

    private static func commitDeleteIfMine(uid: String, deviceId: String) async throws {
        let database = Firestore.firestore()
        let reference = documentReference(uid: uid, in: database)
        _ = try await database.runTransaction { transaction, errorPointer in
            let snapshot: DocumentSnapshot
            do {
                snapshot = try transaction.getDocument(reference)
            } catch let fetchError as NSError {
                errorPointer?.pointee = fetchError
                return nil
            }
            // Seule SA session est supprimée : celle du nouvel appareil n'est jamais touchée.
            if snapshot.exists, (snapshot.data()?[SessionWriteGuard.field] as? String) == deviceId {
                transaction.deleteDocument(reference)
            }
            return nil
        }
    }
}
