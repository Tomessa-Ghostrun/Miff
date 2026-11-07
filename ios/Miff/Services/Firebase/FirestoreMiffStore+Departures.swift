import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : comme `FirestoreMiffStore`, rien n'est journalisé ; les erreurs qui sortent d'ici sont celles de
// Firestore, que le dépôt réduit à une `MiffFailure`. Aucun chemin n'est construit avec une valeur lue sur le réseau sans
// avoir vérifié sa forme.

/// Les opérations de MIFF-4 (retirer, renouveler, supprimer) : lire qui est dans une Miff, quels lieux elle a, et la lire
/// DANS la transaction qui change son code.
extension FirestoreMiffStore {
    func readMemberIds(miffId: String) async throws -> [String] {
        guard MiffId.isValid(miffId) else {
            throw InvalidWriteError()
        }
        let snapshot = try await firestore()
            .collection("miffs").document(miffId).collection("members")
            .getDocuments(source: .server)
        return snapshot.documents.map { $0.documentID }
    }

    func readPlaceIds(miffId: String) async throws -> [String] {
        guard MiffId.isValid(miffId) else {
            throw InvalidWriteError()
        }
        let snapshot = try await firestore()
            .collection("miffs").document(miffId).collection("places")
            .getDocuments(source: .server)
        return snapshot.documents.map { $0.documentID }
    }

    func commitWithMiff(miffId: String, build: @escaping (MiffTxView) -> [DocWrite]) async throws {
        guard MiffId.isValid(miffId) else {
            throw InvalidWriteError()
        }
        let database = try firestore()
        let miffReference = database.collection("miffs").document(miffId)
        do {
            _ = try await database.runTransaction { transaction, errorPointer in
                // La Miff est lue D'ABORD, dans la transaction : le code et le nom à remplacer sont ceux de CE moment-là. Si un
                // autre téléphone renouvelle en même temps, Firestore rejoue le lot sur la copie fraîche.
                let miffSnapshot: DocumentSnapshot
                do {
                    miffSnapshot = try transaction.getDocument(miffReference)
                } catch let fetchError as NSError {
                    errorPointer?.pointee = fetchError
                    return nil
                }
                // Le nom est gardé TEL QUE le serveur l'a : les règles exigent que le document du code porte exactement le
                // même (il n'est écrit que dans le lot, jamais montré).
                guard let details = FirestoreMiffStore.details(of: miffSnapshot, cleaningName: false) else {
                    errorPointer?.pointee = NSError(domain: MiffGoneError.domain, code: 1)
                    return nil
                }
                // Le code a la forme d'un code (vérifiée par `details`) avant de devenir un chemin.
                let codeReference = database.collection("joinCodes").document(details.joinCode)
                let codeSnapshot: DocumentSnapshot
                do {
                    codeSnapshot = try transaction.getDocument(codeReference)
                } catch let fetchError as NSError {
                    errorPointer?.pointee = fetchError
                    return nil
                }
                let view = MiffTxView(details: details, codeDocumentExists: codeSnapshot.exists)
                let writes = build(view)
                guard SessionWriteGuard.isSendable(writes) else {
                    errorPointer?.pointee = NSError(domain: InvalidWriteError.domain, code: 1)
                    return nil
                }
                FirestoreMiffStore.apply(writes, to: transaction, in: database)
                return nil
            }
        } catch let error as NSError where error.domain == MiffGoneError.domain {
            throw MiffGoneError()
        }
    }
}
