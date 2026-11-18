import FirebaseFirestore
import Foundation

// COÛT, SÉCURITÉ et CONFIDENTIALITÉ, pour ce fichier : UNE écoute d'UN document, `miffs/{id}/status/{moi}` (MON statut), seulement pendant que
// l'écran principal est devant et que le Feed n'écoute pas déjà (Profil, Update). Elle ne lit que ce que mes règles autorisent déjà (mon
// partage actif et mon statut publié : ce sont les conditions de la lecture de `status` du Feed, aucune règle n'est changée). Au plus une
// lecture par changement de MON statut (3 lectures de règles à l'ouverture), jamais de liste, jamais d'écriture. L'identifiant de la Miff et
// celui du compte sont vérifiés avant de construire le chemin. Rien n'est journalisé ; le contenu est nettoyé par le mappage du Feed
// (présence inconnue = neutre).

/// L'écoute de MON statut seul.
final class FirestoreOwnStatusObserver: OwnStatusObserver {
    func observe(miffId: String, uid: String) -> AsyncStream<OwnStatusFeed> {
        AsyncStream { continuation in
            guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId), FeedFirestoreMapping.isUid(uid) else {
                continuation.yield(.ended)
                continuation.finish()
                return
            }
            let registration = Firestore.firestore().collection("miffs").document(miffId).collection("status")
                .document(uid)
                .addSnapshotListener { snapshot, error in
                    // Un refus est un état normal (mon statut n'est pas publié, mon partage est arrêté) ; toute autre fin se dit de même.
                    if error != nil {
                        continuation.yield(.ended)
                        continuation.finish()
                        return
                    }
                    guard let snapshot else {
                        return
                    }
                    guard snapshot.exists, let fields = snapshot.data(with: .estimate) else {
                        continuation.yield(.data(nil))
                        return
                    }
                    continuation.yield(.data(FeedFirestoreMapping.status(fields: fields)))
                }
            continuation.onTermination = { _ in
                registration.remove()
            }
        }
    }
}
