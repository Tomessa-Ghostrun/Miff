import FirebaseFirestore
import Foundation

// SÉCURITÉ et COÛT, pour ce fichier : les demandes (nom, adresse, coordonnées) passent par ici et nulle part ailleurs ; rien n'est journalisé
// et aucun message d'erreur ne sort (le dépôt réduit chaque échec à une `MiffFailure`). Les identifiants sont vérifiés avant de devenir un
// chemin. COÛT : un membre ne lit QUE ses propres demandes (une requête filtrée sur `requestedBy`, que les règles exigent), 3 documents au
// plus. Ce fichier n'écrit rien lui-même : les écritures passent par `FirestoreMiffStore` (transactions, ou la file du téléphone).

/// Les opérations Firestore des demandes d'ajout d'un lieu. L'instance Firestore est résolue à CHAQUE usage : elle est remplacée après
/// une purge du cache.
final class FirestorePlaceRequestStore: PlaceRequestStore {
    private let base: MiffStore

    init(base: MiffStore) {
        self.base = base
    }

    func firestore() throws -> Firestore {
        guard FirebaseBootstrap.isConfigured else {
            // Sans configuration Firebase (poste sans fichier de configuration), rien n'est joignable.
            throw NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        }
        return Firestore.firestore()
    }

    func requests(miffId: String) throws -> CollectionReference {
        try firestore().collection("miffs").document(miffId).collection("placeRequests")
    }

    func readMine(miffId: String, uid: String, fromServer: Bool) async throws -> [PlaceRequest] {
        guard MiffId.isValid(miffId) else {
            return []
        }
        let source: FirestoreSource = fromServer ? .server : .default
        let snapshot = try await requests(miffId: miffId)
            .whereField("requestedBy", isEqualTo: uid)
            .getDocuments(source: source)
        return snapshot.documents.compactMap { Self.request(from: $0, miffId: miffId) }
    }

    func commit(_ writes: [DocWrite]) async throws {
        try await base.commit(writes)
    }

    func enqueue(_ writes: [DocWrite]) {
        base.enqueue(writes)
    }

    // MARK: - Le créateur

    func observeAll(miffId: String) -> AsyncThrowingStream<RequestsSnapshot, Error> {
        AsyncThrowingStream { continuation in
            guard MiffId.isValid(miffId), let collection = try? self.requests(miffId: miffId) else {
                continuation.finish(throwing: InvalidWriteError())
                return
            }
            // UN écouteur sur UNE Miff (10 documents au plus), détaché dès que le flux est abandonné (l'app quitte le premier plan).
            let registration = collection.addSnapshotListener { snapshot, error in
                if let error {
                    continuation.finish(throwing: error)
                    return
                }
                guard let snapshot else {
                    return
                }
                let parsed = snapshot.documents.compactMap {
                    FirestorePlaceRequestStore.request(from: $0, miffId: miffId)
                }
                continuation.yield(RequestsSnapshot(requests: parsed, fromCache: snapshot.metadata.isFromCache))
            }
            continuation.onTermination = { _ in
                registration.remove()
            }
        }
    }

    func readAll(miffId: String) async throws -> [PlaceRequest] {
        guard MiffId.isValid(miffId) else {
            return []
        }
        let snapshot = try await requests(miffId: miffId).getDocuments()
        return snapshot.documents.compactMap { Self.request(from: $0, miffId: miffId) }
    }

    func readOne(miffId: String, requestId: String) async throws -> PlaceRequest? {
        guard MiffId.isValid(miffId), PlaceRequest.parseId(requestId) != nil else {
            return nil
        }
        let snapshot = try await requests(miffId: miffId).document(requestId).getDocument(source: .server)
        return snapshot.exists ? Self.request(from: snapshot, miffId: miffId) : nil
    }

    func readRequester(miffId: String, uid: String) async throws -> RequesterRead {
        guard MiffId.isValid(miffId), !uid.isEmpty, !uid.contains("/") else {
            throw InvalidWriteError()
        }
        let snapshot = try await firestore()
            .collection("miffs").document(miffId).collection("members").document(uid)
            .getDocument()
        guard snapshot.exists, let data = snapshot.data() else {
            // Une fiche absente de la COPIE DU TÉLÉPHONE ne prouve rien : seul le serveur peut dire que la personne est partie.
            if snapshot.metadata.isFromCache {
                throw NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
            }
            return .notMember
        }
        let first = MiffName.fromServer(data["firstName"] as? String ?? "") ?? ""
        let last = MiffName.fromServer(data["lastName"] as? String ?? "") ?? ""
        return .found(RequesterCard.of(uid: uid, firstName: first, lastName: last, avatarJpeg: data["avatar"] as? Data))
    }

    /// Une demande d'un document, nettoyée ; nil quand il ne peut pas être une demande de cette app.
    static func request(from snapshot: DocumentSnapshot, miffId: String) -> PlaceRequest? {
        let fields = snapshot.data()
        let created = (fields?["createdAt"] as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
        return PlaceRequest.fromServer(
            id: snapshot.documentID,
            miffId: miffId,
            fields: fields,
            createdAtMillis: created
        )
    }
}
