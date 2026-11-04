import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : noms, codes et profils passent par ici et nulle part ailleurs. Rien n'est journalisé ; les
// erreurs qui sortent d'ici sont celles de Firestore, que le dépôt réduit à une `MiffFailure` avant que rien soit montré.
// Aucun chemin n'est construit avec une valeur lue sur le réseau sans avoir vérifié sa forme.

/// Convertit les valeurs d'un document (`FieldValueSpec`, indépendantes de Firestore) en valeurs que le SDK comprend.
enum FirestoreFieldConversion {
    static func convert(_ fields: [String: FieldValueSpec]) -> [String: Any] {
        fields.mapValues { convertValue($0) }
    }

    /// L'heure du serveur (jamais celle du téléphone), « null » explicite, octets de la photo.
    static func convertValue(_ spec: FieldValueSpec) -> Any {
        switch spec {
        case .string(let text):
            return text
        case .bool(let flag):
            return flag
        case .bytes(let bytes):
            return bytes
        case .serverTimestamp:
            return FieldValue.serverTimestamp()
        case .null:
            return NSNull()
        case .map(let nested):
            return convert(nested)
        }
    }
}

extension MiffRepositoryRules {
    /// Les règles de l'app : l'identifiant d'une nouvelle Miff vient du SDK (20 lettres et chiffres, tiré en local, aucun
    /// réseau), le code d'un tirage cryptographique, l'heure de l'horloge du téléphone.
    static func live() -> MiffRepositoryRules {
        MiffRepositoryRules(newMiffId: {
            Firestore.firestore().collection("miffs").document().documentID
        })
    }
}

/// Les opérations Firestore derrière le dépôt des Miffs. Les écritures sont des TRANSACTIONS (voir `MiffStore`) : « pas de
/// confirmation » veut toujours dire « rien d'écrit ». L'instance Firestore est résolue à CHAQUE usage, jamais gardée : elle
/// est remplacée après une purge du cache. Rien ici n'ouvre Firestore avant qu'un écran ne le demande.
final class FirestoreMiffStore: MiffStore {
    private func firestore() throws -> Firestore {
        guard FirebaseBootstrap.isConfigured else {
            // Sans configuration Firebase (poste sans fichier de configuration), rien n'est joignable.
            throw NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        }
        return Firestore.firestore()
    }

    func readJoinCode(_ code: String) async throws -> [String: Any]? {
        // Jamais un chemin construit avec un texte qui n'a pas la forme d'un code.
        guard JoinCode.isValid(code) else {
            return nil
        }
        let snapshot = try await firestore().collection("joinCodes").document(code).getDocument(source: .server)
        return snapshot.exists ? snapshot.data() : nil
    }

    func readMemberships(uid: String, fromServer: Bool) async throws -> [MembershipRef] {
        let source: FirestoreSource = fromServer ? .server : .default
        let snapshot = try await firestore()
            .collection("users").document(uid).collection("memberships")
            .getDocuments(source: source)
        return snapshot.documents.compactMap { document -> MembershipRef? in
            // La Miff est le CHAMP miffId, jamais l'identifiant du document (« free » pour un compte gratuit).
            guard let miffId = document.data()["miffId"] as? String,
                  MiffId.isValid(miffId),
                  Self.isMembershipKey(document.documentID) else {
                return nil
            }
            return MembershipRef(key: document.documentID, miffId: miffId)
        }
    }

    /// « free » ou l'identifiant d'une Miff : les deux seules clés d'index que l'app écrit, donc les seules qu'elle relit.
    private static func isMembershipKey(_ key: String) -> Bool {
        key == FirestoreMiffRepository.freeKey || MiffId.isValid(key)
    }

    func readMiff(miffId: String, fromServer: Bool) async throws -> MiffRead {
        guard MiffId.isValid(miffId) else {
            throw InvalidWriteError()
        }
        let source: FirestoreSource = fromServer ? .server : .default
        let snapshot = try await firestore().collection("miffs").document(miffId).getDocument(source: source)
        guard let details = Self.details(of: snapshot) else {
            return .missing
        }
        return .exists(details)
    }

    /// La Miff d'un document, s'il existe et a la forme qu'exigent les règles ; nil sinon. Le nom vient du réseau : il est
    /// nettoyé comme tout nom reçu d'un autre téléphone.
    static func details(of snapshot: DocumentSnapshot) -> MiffDetails? {
        guard snapshot.exists,
              let data = snapshot.data(),
              let name = data["name"] as? String,
              let code = data["joinCode"] as? String,
              JoinCode.isValid(code),
              let owner = data["ownerUid"] as? String else {
            return nil
        }
        return MiffDetails(id: snapshot.documentID, name: MiffName.clean(name), joinCode: code, ownerUid: owner)
    }

    func readEntitlement(uid: String) async throws -> EntitlementRead? {
        let snapshot = try await firestore().collection("entitlements").document(uid).getDocument()
        guard snapshot.exists, let data = snapshot.data() else {
            return nil
        }
        let expires = (data["expiresAt"] as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
        return EntitlementRead(active: (data["active"] as? Bool) == true, expiresAtMillis: expires)
    }

    func readSharing(miffId: String, uid: String) async throws -> Bool? {
        guard MiffId.isValid(miffId) else {
            throw InvalidWriteError()
        }
        let snapshot = try await firestore()
            .collection("miffs").document(miffId).collection("members").document(uid)
            .getDocument(source: .server)
        guard snapshot.exists else {
            return nil
        }
        let sharing = snapshot.data()?["sharing"] as? [String: Any]
        return sharing?["enabled"] as? Bool
    }

    func serverReachable(uid: String) async -> Bool {
        do {
            _ = try await firestore().collection("users").document(uid).getDocument(source: .server)
            return true
        } catch {
            return false
        }
    }

    func commitWithProfile(uid: String, build: @escaping (ProfileCopy) -> [DocWrite]) async throws {
        let database = try firestore()
        let profileReference = database.collection("users").document(uid)
        do {
            _ = try await database.runTransaction { transaction, errorPointer in
                // Le profil est lu D'ABORD : les règles exigent que la copie dans la fiche lui soit égale après le lot.
                let snapshot: DocumentSnapshot
                do {
                    snapshot = try transaction.getDocument(profileReference)
                } catch let fetchError as NSError {
                    errorPointer?.pointee = fetchError
                    return nil
                }
                guard let profile = ProfileCopy.from(fields: snapshot.data()) else {
                    errorPointer?.pointee = NSError(domain: MissingProfileError.domain, code: 1)
                    return nil
                }
                let writes = build(profile)
                guard !writes.isEmpty else {
                    errorPointer?.pointee = NSError(domain: InvalidWriteError.domain, code: 1)
                    return nil
                }
                FirestoreMiffStore.apply(writes, to: transaction, in: database)
                return nil
            }
        } catch let error as NSError where error.domain == MissingProfileError.domain {
            throw MissingProfileError()
        }
    }

    func commit(_ writes: [DocWrite]) async throws {
        guard !writes.isEmpty else {
            throw InvalidWriteError()
        }
        let database = try firestore()
        _ = try await database.runTransaction { transaction, _ in
            FirestoreMiffStore.apply(writes, to: transaction, in: database)
            return nil
        }
    }

    private static func apply(_ writes: [DocWrite], to transaction: Transaction, in database: Firestore) {
        for write in writes {
            let reference = database.document(write.path)
            switch write.kind {
            case .set:
                transaction.setData(FirestoreFieldConversion.convert(write.data), forDocument: reference)
            case .update:
                transaction.updateData(FirestoreFieldConversion.convert(write.data), forDocument: reference)
            case .delete:
                transaction.deleteDocument(reference)
            }
        }
    }
}
