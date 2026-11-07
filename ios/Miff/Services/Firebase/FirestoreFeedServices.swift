import FirebaseFirestore
import Foundation

// COÛT et CONFIDENTIALITÉ, pour ce fichier : le Feed LIT et n'écrit jamais (0 écriture, 0 suppression). Trois lectures, toutes bornées
// pour le plan gratuit de Firestore (Spark) :
// - UNE écoute de `miffs/{id}/status` (la liste : 3 lectures de règles par requête, puis une lecture par document qui change), attachée
//   par le view model seulement tant que le Feed est à l'écran et seulement après la publication de mon propre statut ;
// - UN `get` de `positions/{uid}` par toucher sur « Localiser » (5 lectures de règles + 1 lecture), auprès du SERVEUR, sans écoute ;
// - UNE relecture forcée par « Actualiser » (9 + 2 x membres + lieux lectures), au plus une toutes les 15 s.
// LIMITE CONNUE, acceptée et déjà écrite côté backend (backend/data-model.md, point 27) : le SDK Firestore garde dans son cache
// local les documents qu'il lit, une position comprise, même pour une lecture `source: .server` (qui veut seulement dire « jamais LUE
// dans le cache »). Cette copie reste sur le téléphone jusqu'à la purge du cache (déconnexion, perte d'accès à une Miff). Le Feed,
// lui, n'affiche, ne garde et ne journalise rien d'une position une fois la feuille fermée. Rien de ce qui est lu n'est journalisé.

/// L'écoute des statuts d'une Miff.
final class FirestoreStatusObserver: StatusObserver {
    func observe(miffId: String) -> AsyncStream<StatusFeed> {
        AsyncStream { continuation in
            guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
                continuation.yield(.ended)
                continuation.finish()
                return
            }
            let registration = Firestore.firestore().collection("miffs").document(miffId).collection("status")
                // Les changements de métadonnées sont dits aussi (les mêmes documents, `fromCache` passe à faux) : cela ne coûte aucune
                // lecture et permet au Feed de savoir que ce qu'il montre est le présent et non une copie gardée par le téléphone.
                .addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                    // Un refus est un état normal (réciprocité), pas une erreur ; toute autre fin se dit de la même façon.
                    if error != nil {
                        continuation.yield(.ended)
                        continuation.finish()
                        return
                    }
                    guard let snapshot else {
                        return
                    }
                    continuation.yield(
                        .data(
                            byUid: FeedFirestoreMapping.statuses(of: snapshot),
                            fromCache: snapshot.metadata.isFromCache
                        )
                    )
                }
            continuation.onTermination = { _ in
                registration.remove()
            }
        }
    }
}

/// La lecture unique derrière « Localiser ».
final class FirestorePositionReader: PositionReader {
    /// Hors ligne, une lecture auprès du serveur se termine d'elle-même par « indisponible » ; ceci n'est que le plafond.
    private let timeoutSeconds: TimeInterval

    init(timeoutSeconds: TimeInterval = 15) {
        self.timeoutSeconds = timeoutSeconds
    }

    func read(miffId: String, uid: String) async -> PositionLookup {
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId), FeedFirestoreMapping.isUid(uid) else {
            return .failed
        }
        do {
            let snapshot = try await timedOperation(seconds: timeoutSeconds) {
                try await Firestore.firestore().collection("miffs").document(miffId)
                    .collection("positions").document(uid).getDocument(source: .server)
            }
            guard snapshot.exists else {
                return .notFound
            }
            return FeedFirestoreMapping.position(fields: snapshot.data())
        } catch is OperationTimedOut {
            return .failed
        } catch {
            return FeedFirestoreMapping.lookup(forError: error)
        }
    }
}

/// « Actualiser » : une relecture forcée auprès du SERVEUR.
final class FirestoreFeedRefresher: FeedRefresher {
    func refresh(miffId: String, readStatuses: Bool) async -> ServerRead {
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
            return .unreachable
        }
        let miff = Firestore.firestore().collection("miffs").document(miffId)
        // Au mieux : elles ne font que rafraîchir le cache que lisent les écoutes de la Miff (un échec ici n'est pas la réponse
        // d'« Actualiser »).
        async let miffReached = succeeded { _ = try await miff.getDocument(source: .server) }
        async let membersRead = succeeded { _ = try await miff.collection("members").getDocuments(source: .server) }
        async let placesRead = succeeded { _ = try await miff.collection("places").getDocuments(source: .server) }
        let statuses: ServerRead? = readStatuses ? await statusesRead(miff) : nil
        _ = await membersRead
        _ = await placesRead
        let reached = await miffReached
        // Sans statuts à lire, joindre le serveur est ce qui dit que l'actualisation a abouti : le document de la Miff en est la preuve.
        return statuses ?? (reached ? .fresh(statuses: [:]) : .unreachable)
    }

    private func succeeded(_ operation: () async throws -> Void) async -> Bool {
        do {
            try await operation()
            return true
        } catch {
            return false
        }
    }

    private func statusesRead(_ miff: DocumentReference) async -> ServerRead {
        do {
            let snapshot = try await miff.collection("status").getDocuments(source: .server)
            return .fresh(statuses: FeedFirestoreMapping.statuses(of: snapshot))
        } catch {
            let nsError = error as NSError
            let refused = nsError.domain == FirestoreErrorNumber.domain
                && nsError.code == FirestoreErrorNumber.permissionDenied
            return refused ? .fresh(statuses: nil) : .unreachable
        }
    }
}
