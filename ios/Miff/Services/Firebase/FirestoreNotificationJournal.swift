#if NOTIF_JOURNAL
import FirebaseFirestore
import Foundation

// CNC-I (ticket docs/tickets/centre-notifications-complet.md §8.3 et §8.4, contrat `backend/data-model.md` §2.21) : l'UNIQUE endroit qui parle au journal du
// serveur, `users/{uid}/notifications/{entryId}` (les dossiers des notifications ne touchent jamais Firestore : voir `NotificationPrivacyTests`). Les règles
// du serveur laissent le PROPRIÉTAIRE lire une liste de 100 au plus et supprimer ; personne n'écrit que les fonctions.
//
// SÉCURITÉ et CONFIDENTIALITÉ : rien ici n'est journalisé ni gardé dans le message d'une erreur (il peut citer un chemin avec un identifiant) ; chaque
// document est validé par `NotificationJournal.parse` avant d'être utilisé ; aucun identifiant n'entre dans un chemin sans avoir la forme d'un identifiant.
// COÛT : jamais d'écouteur ; le serveur seulement (`source: .server` : une réponse du cache du téléphone serait une copie de ce qui a déjà été vu) ; 100
// entrées par page, 2 pages au plus ; une suppression est un lot envoyé une fois (les suppressions passent par le point d'écriture commun des Miffs, donc
// le même garde `SessionWriteGuard`).
// Compilé seulement avec la condition `NOTIF_JOURNAL` (project.yml : Debug et Internal oui, Release non, comme `BuildConfig.NOTIF_JOURNAL` d'Android).

final class FirestoreNotificationJournal: NotificationJournalGateway {
    /// Attente maximale d'une lecture : au-delà, rien n'est connu et rien n'est perdu (l'occasion suivante réessaie).
    static let readTimeoutNanos: UInt64 = 20_000_000_000
    static let deleteAllTimeoutNanos: UInt64 = 30_000_000_000
    private static let maxDeleteRounds = 20
    private static let batchSize = 400

    private let writer: MiffStore

    init(writer: MiffStore = FirestoreMiffStore()) {
        self.writer = writer
    }

    func read(uid: String, sinceMillis: Int?) async -> JournalRead {
        guard FirebaseBootstrap.isConfigured, NotificationJournal.isIdentifier(uid) else {
            return .unavailable
        }
        return await TimedAnswer.of(nanoseconds: Self.readTimeoutNanos, fallback: JournalRead.unavailable) {
            await Self.fetch(uid: uid, sinceMillis: sinceMillis)
        }
    }

    func delete(uid: String, entryIds: [String]) {
        guard FirebaseBootstrap.isConfigured, NotificationJournal.isIdentifier(uid) else {
            return
        }
        let valid = entryIds.filter { NotificationJournal.isIdentifier($0) }
        var start = 0
        while start < valid.count {
            let chunk = valid[start..<min(start + Self.batchSize, valid.count)]
            // Un lot confié à la file du téléphone, sans attendre : hors ligne, il part au retour du réseau.
            writer.enqueue(chunk.map { DocWrite(path: Self.path(uid: uid, entryId: $0), kind: .delete) })
            start += Self.batchSize
        }
    }

    func deleteAll(uid: String) async {
        guard FirebaseBootstrap.isConfigured, NotificationJournal.isIdentifier(uid) else {
            return
        }
        let writer = self.writer
        await TimedAnswer.of(nanoseconds: Self.deleteAllTimeoutNanos, fallback: ()) {
            await Self.deleteRounds(uid: uid, writer: writer)
        }
    }

    // MARK: - Intérieur

    /// Une liste de 100, un lot de suppressions, encore, jusqu'à ce que la liste soit vide. Borné. Au mieux : aucune erreur montrée, ce qui reste est
    /// supprimé par la règle d'expiration et n'est lisible de personne d'autre.
    private static func deleteRounds(uid: String, writer: MiffStore) async {
        for _ in 0..<maxDeleteRounds {
            guard let ids = await listIds(uid: uid), !ids.isEmpty else {
                return
            }
            do {
                try await writer.commit(ids.map { DocWrite(path: path(uid: uid, entryId: $0), kind: .delete) })
            } catch {
                return
            }
        }
    }

    private static func path(uid: String, entryId: String) -> String {
        "users/\(uid)/\(NotificationJournal.collection)/\(entryId)"
    }

    private static func collection(uid: String) -> CollectionReference {
        Firestore.firestore().collection("users").document(uid).collection(NotificationJournal.collection)
    }

    private static func fetch(uid: String, sinceMillis: Int?) async -> JournalRead {
        do {
            // Contrat du serveur : depuis un curseur, `createdAt > curseur` CROISSANT (rien n'est sauté, la requête suivante repart de la plus
            // récente vue) ; la PREMIÈRE synchronisation (sans curseur) DÉCROISSANTE, les 100 plus récentes. La `limit` est obligatoire : les
            // règles refusent une liste sans.
            var query: Query
            if let sinceMillis {
                let since = Timestamp(date: Date(timeIntervalSince1970: Double(sinceMillis) / 1000))
                query = collection(uid: uid).whereField("createdAt", isGreaterThan: since).order(by: "createdAt")
            } else {
                query = collection(uid: uid).order(by: "createdAt", descending: true)
            }
            query = query.limit(to: NotificationJournal.pageSize)
            var entries: [JournalEntry] = []
            for page in 1...NotificationJournal.maxPages {
                let snapshot = try await query.getDocuments(source: .server)
                entries += snapshot.documents.compactMap { entry(of: $0) }
                let documents = snapshot.documents
                guard documents.count >= NotificationJournal.pageSize, let last = documents.last else {
                    break
                }
                if page < NotificationJournal.maxPages {
                    query = query.start(afterDocument: last)
                }
            }
            return .loaded(entries)
        } catch {
            let nsError = error as NSError
            if nsError.domain == FirestoreErrorNumber.domain && nsError.code == FirestoreErrorNumber.permissionDenied {
                return .refused
            }
            return .unavailable
        }
    }

    /// Les identifiants d'une page d'entrées du compte (au plus 100), lus auprès du serveur ; nil quand la lecture échoue.
    private static func listIds(uid: String) async -> [String]? {
        do {
            let page = collection(uid: uid).limit(to: NotificationJournal.pageSize)
            let snapshot = try await page.getDocuments(source: .server)
            return snapshot.documents.map { $0.documentID }
        } catch {
            return nil
        }
    }

    /// Un document du journal comme entrée, ou nil quand il faut l'ignorer. PAR document : un champ qui n'a pas le type attendu (un serveur plus
    /// récent peut écrire autre chose) fait ignorer CE document, jamais échouer toute la lecture (et la refaire sans fin).
    private static func entry(of document: QueryDocumentSnapshot) -> JournalEntry? {
        let data = document.data()
        return NotificationJournal.parse(
            docId: document.documentID,
            data: data,
            createdAtMillis: millis(data["createdAt"]),
            expireAtMillis: millis(data["expireAt"])
        )
    }

    private static func millis(_ value: Any?) -> Int? {
        (value as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
    }
}
#endif
