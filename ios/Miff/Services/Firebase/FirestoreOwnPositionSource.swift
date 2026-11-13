import FirebaseFirestore
import Foundation

// COÛT, CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier (correctif de la phrase « Partage ta position pour voir cette photo. ») : UN `get` de MON propre
// document `miffs/{id}/positions/{moi}`, auprès du SERVEUR, seulement après un refus de photo quand la note locale ne dit pas « publiée » (au plus une fois
// par Miff et par 30 s : `OwnPositionResolver`). Seule l'EXISTENCE du document compte : ses champs (la position) ne sont jamais lus ni gardés. Jamais une
// liste, jamais une écoute, jamais une écriture. Un document que je peux toujours lire (les règles le permettent à son auteur) ; toute erreur est « pas su ».
// 1 lecture facturée.

final class FirestoreOwnPositionSource: OwnPositionServerReading {
    private let myUid: () -> String?
    private let timeoutSeconds: TimeInterval

    init(myUid: @escaping () -> String?, timeoutSeconds: TimeInterval = 15) {
        self.myUid = myUid
        self.timeoutSeconds = timeoutSeconds
    }

    func exists(miffId: String) async -> Bool? {
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
            return nil
        }
        guard let uid = myUid(), FeedFirestoreMapping.isUid(uid) else {
            return nil
        }
        do {
            let snapshot = try await timedOperation(seconds: timeoutSeconds) {
                try await Firestore.firestore().collection("miffs").document(miffId)
                    .collection("positions").document(uid).getDocument(source: .server)
            }
            return snapshot.exists
        } catch {
            return nil
        }
    }
}
