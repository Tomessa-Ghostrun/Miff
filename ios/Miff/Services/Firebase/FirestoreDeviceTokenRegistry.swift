import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : le document contient le jeton de notification de CE téléphone, sa plateforme, la version de l'app, le fuseau
// (heures calmes) et un booléen. Les règles ne laissent lire ni écrire le document de personne d'autre, et aucun autre membre ne lit jamais un
// jeton. Aucune position, aucun nom, aucun e-mail, aucun identifiant publicitaire. Le jeton ne passe par aucun journal ni message d'erreur : une
// erreur de Firestore n'est pas remontée, elle est seulement lancée (le registraire l'avale sans la lire). Les écritures sont des TRANSACTIONS
// (comme toutes celles de l'app) : une écriture simple faite sans réseau serait mise en file et partirait PLUS TARD, après une déconnexion
// ou la prise du compte par un autre téléphone ; une transaction n'existe qu'en ligne, donc « pas d'accusé » veut dire « rien d'écrit ». Aucun
// identifiant n'entre dans un chemin sans avoir la forme attendue. L'instance Firestore est résolue à CHAQUE usage (remplacée après une purge).

/// `DeviceTokenRegistry` branché sur `users/{uid}/devices/{deviceId}` (contrat `docs/dev/jetons-appareil-clients.md`).
final class FirestoreDeviceTokenRegistry: DeviceTokenRegistry {
    func upsert(uid: String, deviceId: String, entry: DeviceEntry) async throws {
        guard FirebaseBootstrap.isConfigured, let path = DeviceDocument.path(uid: uid, deviceId: deviceId) else {
            throw InvalidWriteError()
        }
        let database = Firestore.firestore()
        let reference = database.document(path)
        // Contrat §4.1 : un `set` complet sur un document déjà présent porterait un `createdAt` neuf et serait refusé ; un `update` sur un document
        // absent échoue. La transaction lit donc d'abord, puis crée (sept clés) ou met à jour (sans `createdAt` ni `platform`).
        _ = try await database.runTransaction { transaction, errorPointer in
            let snapshot: DocumentSnapshot
            do {
                snapshot = try transaction.getDocument(reference)
            } catch let fetchError as NSError {
                errorPointer?.pointee = fetchError
                return nil
            }
            if snapshot.exists {
                transaction.updateData(
                    FirestoreFieldConversion.convert(DeviceDocument.update(entry)), forDocument: reference
                )
            } else {
                transaction.setData(
                    FirestoreFieldConversion.convert(DeviceDocument.creation(entry)), forDocument: reference
                )
            }
            return nil
        }
    }

    func deleteOthers(uid: String, keepDeviceId: String) async throws {
        guard FirebaseBootstrap.isConfigured, !uid.isEmpty, !uid.contains("/") else {
            throw InvalidWriteError()
        }
        let database = Firestore.firestore()
        // La liste est lue auprès du SERVEUR (1 à 3 documents) : un cache ne dit pas ce qui reste chez les autres téléphones.
        let snapshot = try await database.collection("users").document(uid).collection("devices")
            .getDocuments(source: .server)
        let others = snapshot.documents.map { $0.documentID }.filter { $0 != keepDeviceId }
        // Un ancien document peut avoir un identifiant plus court qu'une session (les règles acceptent 1 à 64 caractères).
        let paths = others
            .filter { PushIdentifier.isValid($0, maxLength: 64) }
            .map { "users/\(uid)/devices/\($0)" }
        guard !paths.isEmpty else {
            return
        }
        // Un seul lot, sans doublon de chemin.
        _ = try await database.runTransaction { transaction, _ in
            for path in Set(paths) {
                transaction.deleteDocument(database.document(path))
            }
            return nil
        }
    }

    func delete(uid: String, deviceId: String) async throws {
        guard FirebaseBootstrap.isConfigured, let path = DeviceDocument.path(uid: uid, deviceId: deviceId) else {
            throw InvalidWriteError()
        }
        let database = Firestore.firestore()
        let reference = database.document(path)
        _ = try await database.runTransaction { transaction, _ in
            transaction.deleteDocument(reference)
            return nil
        }
    }
}
