import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : les lieux (nom, adresse, coordonnées) et la liste des Miffs de la personne passent par ici et
// nulle part ailleurs. Rien n'est journalisé ; les erreurs qui sortent d'ici sont celles de Firestore, que le dépôt réduit à
// une `MiffFailure` avant que rien soit montré. Aucun chemin n'est construit avec une valeur lue sur le réseau sans avoir vérifié
// sa forme.

/// Les opérations Firestore des lieux. Les lectures qui décident d'une limite viennent du SERVEUR (hors ligne, elles lèvent :
/// jamais « gratuit » ni « aucun lieu » par défaut) ; les écritures sont les TRANSACTIONS de `FirestoreMiffStore` (rien n'est
/// mis en attente hors ligne). L'instance Firestore est résolue à CHAQUE usage : elle est remplacée après une purge du cache.
final class FirestorePlaceStore: PlaceStore {
    private let base: MiffStore

    init(base: MiffStore) {
        self.base = base
    }

    private func firestore() throws -> Firestore {
        guard FirebaseBootstrap.isConfigured else {
            // Sans configuration Firebase (poste sans fichier de configuration), rien n'est joignable.
            throw NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        }
        return Firestore.firestore()
    }

    func readEntitlement(uid: String) async throws -> EntitlementRead? {
        // Auprès du SERVEUR, jamais du cache : un « gratuit » périmé ouvrirait l'offre à un compte Miff+, un « Miff+ » périmé
        // laisserait passer une limite jusqu'aux règles. Une lecture qui échoue lève : le dépôt dit « réessaie ».
        let snapshot = try await firestore().collection("entitlements").document(uid).getDocument(source: .server)
        guard snapshot.exists, let data = snapshot.data() else {
            return nil
        }
        let expires = (data["expiresAt"] as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
        return EntitlementRead(active: (data["active"] as? Bool) == true, expiresAtMillis: expires)
    }

    func readMemberships(uid: String) async throws -> [PlaceMembership] {
        let snapshot = try await firestore()
            .collection("users").document(uid).collection("memberships")
            .getDocuments(source: .server)
        return snapshot.documents.compactMap { document -> PlaceMembership? in
            // La Miff est le CHAMP miffId, jamais l'identifiant du document (« free » pour un compte gratuit).
            let data = document.data()
            guard let miffId = data["miffId"] as? String, MiffId.isValid(miffId) else {
                return nil
            }
            let joined = (data["joinedAt"] as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
            return PlaceMembership(miffId: miffId, joinedAtMillis: joined ?? 0)
        }
    }

    func readMiff(miffId: String) async throws -> MiffRead {
        try await base.readMiff(miffId: miffId, fromServer: true)
    }

    func readPlaces(miffId: String) async throws -> [Place] {
        guard MiffId.isValid(miffId) else {
            throw InvalidWriteError()
        }
        let snapshot = try await firestore()
            .collection("miffs").document(miffId).collection("places")
            .getDocuments(source: .server)
        // Nettoyés comme tout texte lu sur le réseau ; un document qui ne peut pas être un lieu de cette app n'est pas montré.
        return snapshot.documents.compactMap { document in
            Place.fromServer(id: document.documentID, miffId: miffId, fields: document.data())
        }
    }

    func commit(_ writes: [DocWrite]) async throws {
        try await base.commit(writes)
    }
}

extension FirestorePlaceRepository {
    /// L'identifiant d'un nouveau lieu (Miff+) vient du SDK : 20 lettres et chiffres, tiré en local, aucun réseau.
    static func newFirestoreId() -> String {
        Firestore.firestore().collection("miffs").document().documentID
    }
}
