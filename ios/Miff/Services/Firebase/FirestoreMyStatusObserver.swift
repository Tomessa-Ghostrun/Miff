import FirebaseFirestore
import Foundation

// CONFIDENTIALITÉ, pour ce fichier : MON statut (présence, heures, `hasPhoto`) lu dans `miffs/{id}/status/{moi}`, jamais journalisé. Un
// seul document, un seul écouteur, arrêté quand on abandonne le flux. Aucun chemin n'est construit avec une valeur qui n'a pas la forme
// d'un identifiant.

struct FirestoreMyStatusObserver: MyStatusObserving {
    func observe(miffId: String, uid: String) -> AsyncStream<MyStatusFacts?> {
        AsyncStream { continuation in
            guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId), !uid.isEmpty, !uid.contains("/") else {
                continuation.yield(nil)
                continuation.finish()
                return
            }
            let reference = Firestore.firestore()
                .collection("miffs").document(miffId).collection("status").document(uid)
            let listener = reference.addSnapshotListener { snapshot, error in
                // Une erreur (hors ligne, accès perdu) ne change rien à ce qui est montré : la perte d'accès est traitée par l'annuaire.
                guard error == nil, let snapshot else {
                    return
                }
                continuation.yield(Self.facts(of: snapshot))
            }
            continuation.onTermination = { _ in
                listener.remove()
            }
        }
    }

    /// Le statut tel que le serveur l'a écrit, ou nil s'il n'existe pas. Un champ de la mauvaise forme est ignoré (écrit par quelqu'un
    /// d'autre : rien n'est cru sans vérification).
    static func facts(of snapshot: DocumentSnapshot) -> MyStatusFacts? {
        guard snapshot.exists, let data = snapshot.data() else {
            return nil
        }
        return MyStatusFacts(
            presence: PresenceValue.fromWire(data["presence"] as? String),
            presenceAtMillis: millis(data["presenceAt"]),
            lastUpdateAtMillis: millis(data["lastUpdateAt"]),
            hasPhoto: (data["hasPhoto"] as? Bool) ?? false
        )
    }

    private static func millis(_ value: Any?) -> Int? {
        guard let timestamp = value as? Timestamp else {
            return nil
        }
        return Int(timestamp.dateValue().timeIntervalSince1970 * 1000)
    }
}
