import FirebaseFirestore
import Foundation

/// Lire le profil de la personne connectée (onglet Profil, « Mes informations »).
protocol ProfileReading {
    /// nil quand le profil n'a pas pu être lu ou n'est pas utilisable.
    func read(uid: String) async -> ProfileSummary?
}

/// Une lecture de `users/{uid}`, avec le cache du téléphone hors connexion (l'en-tête du Profil reste affichable sans réseau). Une
/// seule lecture par ouverture de l'onglet, aucune écoute en continu (coût : une lecture Firestore par session, plan Spark).
/// Prénom, nom, numéro et photo n'entrent dans aucun journal ni message d'erreur : toute erreur est réduite à « nil ».
struct FirestoreProfileReader: ProfileReading {
    static let timeoutSeconds: TimeInterval = 20

    func read(uid: String) async -> ProfileSummary? {
        guard FirebaseBootstrap.isConfigured else {
            return nil
        }
        do {
            let fields = try await timedOperation(seconds: Self.timeoutSeconds) { () -> [String: Any]? in
                // L'instance est résolue à chaque usage : elle est remplacée après une purge du cache.
                let snapshot = try await Firestore.firestore().collection("users").document(uid).getDocument()
                return snapshot.exists ? snapshot.data() : nil
            }
            return fields.flatMap { ProfileSummary.from(fields: $0) }
        } catch {
            return nil
        }
    }
}
