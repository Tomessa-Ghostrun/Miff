import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : UNE lecture de `entitlements/{uid}` du compte connecté (règle `allow get: if isSelf(uid)`, ni liste ni écriture : l'app n'écrit JAMAIS ce
// document, seul le serveur l'écrit après avoir vérifié l'achat auprès du store). Auprès du SERVEUR et non du cache : une copie restée sur le téléphone pourrait montrer
// un abonnement terminé depuis. Aucun écouteur. L'identifiant ne passe ni dans un journal ni dans un événement.

/// Lit `entitlements/{uid}` pour l'écran de l'abonnement et pour savoir si une offre doit être montrée.
final class FirestoreSubscriptionStore: SubscriptionStore {
    func read(uid: String) async throws -> EntitlementDocument? {
        guard FirebaseBootstrap.isConfigured, !uid.isEmpty, !uid.contains("/") else {
            // Sans configuration Firebase (poste sans fichier de configuration), rien n'est joignable.
            throw NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        }
        let reference = Firestore.firestore().collection("entitlements").document(uid)
        let snapshot = try await reference.getDocument(source: .server)
        guard snapshot.exists, let data = snapshot.data() else {
            return nil
        }
        return Self.document(from: data)
    }

    /// Seuls les types EXACTS comptent : `active` un booléen (le texte « true » n'en est pas un), `expiresAt` un horodatage Firestore (un texte « 2027-01-12 » ou un nombre
    /// tapé à la main dans la console n'en est pas un), `source`, `state` et `plan` des textes, `trial`, `willRenew` et `test` des booléens. Tout le reste se lit comme un
    /// champ absent, jamais comme une erreur ; un champ inconnu en plus est ignoré (le serveur peut en ajouter : un document avec des champs que cette version ne connaît pas
    /// ne casse jamais l'écran, critère AN7).
    static func document(from data: [String: Any]) -> EntitlementDocument {
        let expires = (data["expiresAt"] as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
        return EntitlementDocument(
            active: data["active"] as? Bool,
            expiresAtMillis: expires,
            source: data["source"] as? String,
            state: data["state"] as? String,
            plan: data["plan"] as? String,
            trial: data["trial"] as? Bool,
            willRenew: data["willRenew"] as? Bool,
            test: data["test"] as? Bool
        )
    }
}
