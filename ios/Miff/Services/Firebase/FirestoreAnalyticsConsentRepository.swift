import FirebaseFirestore
import Foundation

/// Les blocs `analyticsConsent` écrits par Réglages > Confidentialité (les quatre clés toujours présentes, `backend/data-model.md`
/// §2.1). Séparés de la transaction pour être testés sans Firestore.
enum AnalyticsConsentBlocks {
    /// Activer ou réactiver : l'heure du serveur (jamais celle du téléphone) et la version du texte montré.
    static func activation(version: String = ConsentVersions.analytics) -> [String: Any] {
        [
            "enabled": true,
            "consentedAt": FieldValue.serverTimestamp(),
            "consentVersion": version,
            "revokedAt": NSNull()
        ]
    }

    /// Retirer : l'heure du retrait est celle du serveur ; la TRACE du dernier consentement (`consentedAt`, `consentVersion`) est
    /// recopiée telle quelle (les règles exigent qu'elle soit inchangée, à la microseconde près : la valeur lue est réécrite, jamais
    /// recalculée).
    static func withdrawal(previous: [String: Any]?) -> [String: Any] {
        [
            "enabled": false,
            "consentedAt": previous?["consentedAt"] ?? NSNull(),
            "consentVersion": previous?["consentVersion"] ?? NSNull(),
            "revokedAt": FieldValue.serverTimestamp()
        ]
    }
}

/// `AnalyticsConsentRepository` branché sur Firestore : UNE transaction qui lit le bloc actuel, décide (`AnalyticsConsentTransition`) et
/// écrit ce qu'elle décide, avec un délai de 20 s, puis « pas de réseau ». L'instance Firestore est résolue à chaque usage (elle est
/// remplacée après une purge du cache). Ni position, ni donnée personnelle : quatre clés qui décrivent un choix.
final class FirestoreAnalyticsConsentRepository: AnalyticsConsentRepository {
    static let timeoutSeconds: TimeInterval = 20

    private let timeoutSeconds: TimeInterval

    init(timeoutSeconds: TimeInterval = FirestoreAnalyticsConsentRepository.timeoutSeconds) {
        self.timeoutSeconds = timeoutSeconds
    }

    func set(uid: String, enable: Bool) async -> ProfileWriteResult {
        // Aucun chemin n'est construit avec un identifiant qui n'a pas la forme d'un identifiant de compte.
        guard FirebaseBootstrap.isConfigured, !uid.isEmpty, !uid.contains("/") else {
            return .failed
        }
        do {
            let refused = try await timedOperation(seconds: timeoutSeconds) {
                try await Self.commit(uid: uid, enable: enable)
            }
            return refused ? .refused : .done
        } catch is OperationTimedOut {
            return .offline
        } catch {
            return interpretWriteError(error)
        }
    }

    /// Vrai quand la transaction a REFUSÉ d'écrire (un texte plus récent est déjà accepté) : rien n'a été écrit.
    private static func commit(uid: String, enable: Bool) async throws -> Bool {
        let database = Firestore.firestore()
        let reference = database.collection("users").document(uid)
        let outcome = try await database.runTransaction { transaction, errorPointer in
            let snapshot: DocumentSnapshot
            do {
                snapshot = try transaction.getDocument(reference)
            } catch let fetchError as NSError {
                errorPointer?.pointee = fetchError
                return nil
            }
            let previous = snapshot.data()?["analyticsConsent"] as? [String: Any]
            switch AnalyticsConsentTransition.change(previous: previous, enable: enable) {
            case .unchanged:
                return nil
            case .refused:
                return true
            case .activate:
                let block = AnalyticsConsentBlocks.activation()
                transaction.updateData(["analyticsConsent": block], forDocument: reference)
                return nil
            case .withdraw:
                let block = AnalyticsConsentBlocks.withdrawal(previous: previous)
                transaction.updateData(["analyticsConsent": block], forDocument: reference)
                return nil
            }
        }
        return (outcome as? Bool) == true
    }
}
