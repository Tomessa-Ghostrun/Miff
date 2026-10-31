import FirebaseFirestore
import Foundation

/// Les écritures de `users/{uid}` faites par l'Onboarding, TOUTES par transaction (jamais une écriture simple, voir
/// `UserProfileRepository`). Une transaction n'existe qu'en ligne et ne laisse rien derrière elle en cas d'échec ; elle
/// est entourée d'un délai de 20 s, puis d'un message neutre.
///
/// Limite connue : une transaction qui dépasse le délai peut quand même aboutir plus tard. L'app s'y prépare : si le
/// serveur refuse l'écriture suivante, elle relit le compte et, si le profil est déjà complet, continue (voir
/// `ProfileViewModel`).
final class FirestoreUserProfileRepository: UserProfileRepository {
    /// Assez long pour un réseau lent, assez court pour ne pas laisser la personne devant un indicateur qui tourne.
    static let writeTimeoutSeconds: TimeInterval = 20

    private let timeoutSeconds: TimeInterval

    init(timeoutSeconds: TimeInterval = FirestoreUserProfileRepository.writeTimeoutSeconds) {
        self.timeoutSeconds = timeoutSeconds
    }

    func create(uid: String, profile: NewProfile, consent: PendingConsent) async -> ProfileWriteResult {
        guard consent.uid == uid else {
            return .failed
        }
        let payload: [String: Any]
        do {
            payload = Self.firestoreData(try ProfileDocument.forCreation(profile, consent: consent))
        } catch {
            return .failed
        }
        return await guarded {
            try await Self.transaction(uid: uid) { transaction, reference in
                transaction.setData(payload, forDocument: reference)
            }
        }
    }

    func addPhone(uid: String, phone: String) async -> ProfileWriteResult {
        guard ProfileDocument.isE164(phone) else {
            return .failed
        }
        return await guarded {
            try await Self.transaction(uid: uid) { transaction, reference in
                transaction.updateData(["phone": phone], forDocument: reference)
            }
        }
    }

    func delete(uid: String) async -> ProfileWriteResult {
        await guarded {
            try await Self.transaction(uid: uid) { transaction, reference in
                transaction.deleteDocument(reference)
            }
        }
    }

    func read(uid: String) async -> ProfileSeed? {
        guard FirebaseBootstrap.isConfigured else {
            return nil
        }
        do {
            let fields = try await timedOperation(seconds: timeoutSeconds) { () -> [String: Any]? in
                let snapshot = try await Firestore.firestore().collection("users").document(uid).getDocument()
                return snapshot.exists ? snapshot.data() : nil
            }
            return fields.flatMap { ProfileSeed.from(fields: $0) }
        } catch {
            return nil
        }
    }

    // MARK: - Intérieur

    private func guarded(_ write: @escaping () async throws -> Void) async -> ProfileWriteResult {
        guard FirebaseBootstrap.isConfigured else {
            return .failed
        }
        do {
            try await timedOperation(seconds: timeoutSeconds, write)
            return .done
        } catch is OperationTimedOut {
            return .offline
        } catch {
            return interpretWriteError(error)
        }
    }

    private static func transaction(
        uid: String,
        _ body: @escaping (Transaction, DocumentReference) -> Void
    ) async throws {
        // L'instance est résolue à chaque usage : elle est remplacée après une purge du cache.
        let firestore = Firestore.firestore()
        let reference = firestore.collection("users").document(uid)
        _ = try await firestore.runTransaction { transaction, _ in
            body(transaction, reference)
            return nil
        }
    }

    /// Valeurs du document pour Firestore : heure du serveur, « null » explicite, octets de la photo.
    private static func firestoreData(_ fields: [String: FieldValueSpec]) -> [String: Any] {
        fields.mapValues { firestoreValue($0) }
    }

    private static func firestoreValue(_ spec: FieldValueSpec) -> Any {
        switch spec {
        case .string(let text):
            return text
        case .bool(let flag):
            return flag
        case .bytes(let data):
            return data
        case .serverTimestamp:
            return FieldValue.serverTimestamp()
        case .null:
            return NSNull()
        case .map(let nested):
            return firestoreData(nested)
        }
    }
}
