import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : seuls les documents du compte lui-même (`users/{uid}` et ses sous-collections) sont touchés, toujours avec
// l'identifiant du compte connecté ; les règles (`isSelf`) refusent le reste. Les identifiants passent par ici et nulle part ailleurs
// (ni journal, ni événement). Chaque écriture est une TRANSACTION, comme celles du profil et des Miffs : une écriture simple faite sans
// réseau est mise en file et part plus tard toute seule ; une transaction n'existe qu'en ligne, donc « pas d'accusé » veut toujours dire
// « rien d'écrit ». Aucun identifiant n'entre dans un chemin sans avoir la forme d'un identifiant (pas de « / »).

/// Ce que la suppression du compte lit et supprime dans Firestore en plus des Miffs (AUTH-5c) : les index `memberships`, les appareils et
/// le profil.
final class FirestoreAccountDataStore: AccountDataStore {
    func membershipCount(uid: String) async throws -> Int {
        try Self.check(uid)
        let snapshot = try await Self.user(uid).collection("memberships").getDocuments(source: .server)
        return snapshot.documents.count
    }

    func deviceIds(uid: String) async throws -> [String] {
        try Self.check(uid)
        let snapshot = try await Self.user(uid).collection("devices").getDocuments(source: .server)
        return snapshot.documents.map { $0.documentID }
    }

    func deleteDevice(uid: String, deviceId: String) async throws {
        try Self.check(uid)
        try Self.check(deviceId)
        let database = Firestore.firestore()
        let reference = database.collection("users").document(uid).collection("devices").document(deviceId)
        _ = try await database.runTransaction { transaction, _ in
            transaction.deleteDocument(reference)
            return nil
        }
    }

    func deleteProfile(uid: String) async throws {
        try Self.check(uid)
        let database = Firestore.firestore()
        let reference = database.collection("users").document(uid)
        _ = try await database.runTransaction { transaction, _ in
            transaction.deleteDocument(reference)
            return nil
        }
    }

    // MARK: - Intérieur

    /// L'instance Firestore est résolue à CHAQUE usage, jamais gardée : elle est remplacée après une purge du cache.
    private static func user(_ uid: String) -> DocumentReference {
        Firestore.firestore().collection("users").document(uid)
    }

    private static func check(_ identifier: String) throws {
        guard FirebaseBootstrap.isConfigured, !identifier.isEmpty, !identifier.contains("/") else {
            throw InvalidWriteError()
        }
    }
}
