import FirebaseFirestore
import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : voir `PhotoStore.swift`. La photo (des octets) et la position qui l'accompagne passent
// par ici vers Firestore et nulle part ailleurs ; rien n'est journalisé, aucune erreur de Firestore n'est montrée (elle peut citer un
// chemin ou une valeur). Aucun chemin n'est construit avec une valeur qui n'a pas la forme d'un identifiant.

/// La transaction de l'Update (photo + statut dans le même lot), et la lecture de ma propre photo.
final class FirestorePhotoStore: PhotoStore, OwnPhotoReader {
    private let base = FirestoreMiffStore()

    func commit(
        uid: String,
        miffIds: [String],
        requireConsent: Bool,
        build: @escaping ([String]) -> [DocWrite]
    ) async throws -> PhotoCommit {
        guard miffIds.allSatisfy(MiffId.isValid), !uid.isEmpty, !uid.contains("/") else {
            throw InvalidWriteError()
        }
        let database = try base.firestore()
        let result = try await database.runTransaction { transaction, errorPointer in
            // Toutes les lectures D'ABORD (une transaction ne peut pas lire après avoir écrit), puis le lot.
            var allowed: [String] = []
            var notSharing: [String] = []
            var needsAgreement: [String] = []
            for miffId in miffIds {
                let reference = database.collection("miffs").document(miffId).collection("members").document(uid)
                let snapshot: DocumentSnapshot
                do {
                    snapshot = try transaction.getDocument(reference)
                } catch let fetchError as NSError {
                    errorPointer?.pointee = fetchError
                    return nil
                }
                let sharing = snapshot.data()?["sharing"] as? [String: Any]
                let version = sharing?["consentVersion"] as? String
                if !snapshot.exists || (sharing?["enabled"] as? Bool) != true {
                    notSharing.append(miffId)
                } else if requireConsent && !MiffSharingConsent.coversPosition(version) {
                    needsAgreement.append(miffId)
                } else {
                    allowed.append(miffId)
                }
            }
            if !allowed.isEmpty {
                let writes = build(allowed)
                // Le même garde que les autres sites d'écriture (SESS-3) : un statut sans identifiant de session valide, ou un lot
                // vide (quelque chose n'a pas pu être construit), n'est jamais envoyé.
                guard SessionWriteGuard.isSendable(writes) else {
                    errorPointer?.pointee = NSError(domain: InvalidWriteError.domain, code: 1)
                    return nil
                }
                FirestoreMiffStore.apply(writes, to: transaction, in: database)
            }
            return PhotoCommit(written: allowed, notSharing: notSharing, needsAgreement: needsAgreement)
        }
        guard let commit = result as? PhotoCommit else {
            throw InvalidWriteError()
        }
        return commit
    }

    /// UN `get` de MA PROPRE photo (2 lectures de règles + la lecture), seulement quand la date locale est perdue et que `hasPhoto` est vrai.
    /// Ce qui n'est pas lisible (refus, absence, pas de réseau) ne conclut rien.
    func read(miffId: String, uid: String) async -> OwnPhotoRead {
        guard MiffId.isValid(miffId), !uid.isEmpty, !uid.contains("/"),
              let database = try? base.firestore() else {
            return .unreadable
        }
        let reference = database.collection("miffs").document(miffId).collection("photos").document(uid)
        guard let snapshot = try? await reference.getDocument(), snapshot.exists, let data = snapshot.data(),
              let created = data["createdAt"] as? Timestamp, let expires = data["expiresAt"] as? Timestamp else {
            return .unreadable
        }
        return .found(
            createdAtMillis: Int(created.dateValue().timeIntervalSince1970 * 1000),
            expiresAtMillis: Int(expires.dateValue().timeIntervalSince1970 * 1000)
        )
    }
}
