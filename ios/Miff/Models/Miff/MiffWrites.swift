import Foundation

// SÉCURITÉ, pour ce fichier : les écritures ci-dessous sont le contrat avec backend/firestore.rules. Chaque clé d'un
// document est TOUJOURS écrite (les règles refusent une clé manquante et une clé inconnue) ; les dates sont celles du serveur
// (les règles refusent une date du téléphone) ; aucune coordonnée, aucune adresse, aucun nom de lieu n'est jamais écrit par
// un écran Miff (ticket Miff, critère V1).

enum WriteKind: Equatable {
    case set
    case update
    case delete
}

/// Une écriture d'un lot : le chemin du document, ce qu'il faut faire, les champs (vides pour une suppression). Du Swift
/// pur : l'adaptateur Firestore (`FirestoreMiffStore`) la convertit.
struct DocWrite: Equatable, CustomStringConvertible {
    let path: String
    let kind: WriteKind
    let data: [String: FieldValueSpec]

    init(path: String, kind: WriteKind, data: [String: FieldValueSpec] = [:]) {
        self.path = path
        self.kind = kind
        self.data = data
    }

    /// Le chemin d'un code (`joinCodes/{code}`) est un secret : rien n'en est montré.
    var description: String {
        "DocWrite(\(kind))"
    }
}

/// Les noms, le numéro et la photo recopiés dans la fiche d'un membre (`members/{uid}`), lus dans `users/{uid}` DANS la
/// transaction qui écrit la fiche : les règles exigent que la copie soit égale au profil après le lot, elle est donc lue au
/// moment même où elle est recopiée (backend/data-model.md §2.6).
struct ProfileCopy: Equatable, CustomStringConvertible {
    let firstName: String
    let lastName: String
    let phone: String
    let avatarJpeg: Data?

    var description: String {
        "ProfileCopy(redacted)"
    }

    /// nil quand le profil ne peut pas donner une fiche valide (absent, sans numéro valide) : la personne retourne à l'étape
    /// profil. Une photo vide n'est pas une photo ; une photo de plus de 20 000 octets est écartée (les règles refuseraient la
    /// fiche).
    static func from(fields: [String: Any]?) -> ProfileCopy? {
        guard let fields,
              let first = fields["firstName"] as? String,
              let last = fields["lastName"] as? String,
              let phone = fields["phone"] as? String,
              ProfileDocument.isE164(phone) else {
            return nil
        }
        var avatar = fields["avatar"] as? Data
        if let bytes = avatar, bytes.isEmpty || bytes.count > ProfileDocument.maxAvatarBytes {
            avatar = nil
        }
        return ProfileCopy(firstName: first, lastName: last, phone: phone, avatarJpeg: avatar)
    }
}

/// Qui entre dans quelle Miff, sous quelle clé d'index, avec quelle permission : ce que partagent les lots de création et
/// d'entrée.
struct MiffEntry {
    let uid: String
    let miffId: String
    /// « free », ou l'identifiant de la Miff pour un compte Premium.
    let membershipKey: String
    let profile: ProfileCopy
    let access: LocationAccess
}

/// Les lots du ticket Miff §5.4, §5.7 et §5.13, en données : testés sans Firestore, appliqués chacun par UNE transaction. Un
/// lot vide veut dire « refusé avant envoi » (nom ou code invalide) : l'appelant n'écrit rien.
enum MiffWrites {
    /// `members/{uid}.sharing` d'une personne qui vient d'accepter le texte (consentement explicite, décision QM2).
    private static func sharingOn() -> FieldValueSpec {
        .map([
            "enabled": .bool(true),
            "consentedAt": .serverTimestamp,
            "consentVersion": .string(MiffSharingConsent.version),
            "revokedAt": .null
        ])
    }

    /// Le premier statut, écrit dans le même lot que le consentement : « unknown » (jamais « tout va bien » avant un vrai
    /// signal), la permission du téléphone telle qu'elle est, aucune coordonnée. Sans lui la personne ne pourrait pas lire
    /// les autres (backend/data-model.md §8, réciprocité).
    static func initialStatus(_ access: LocationAccess) -> [String: FieldValueSpec] {
        [
            "presence": .string("unknown"),
            "placeId": .null,
            "presenceAt": .serverTimestamp,
            "lastUpdateAt": .serverTimestamp,
            "updateText": .null,
            "hasPhoto": .bool(false),
            "batteryPct": .null,
            "locationAccess": .string(access.rawValue),
            "escalationDueAt": .null,
            "updatedAt": .serverTimestamp
        ]
    }

    private static func card(role: MiffRole, profile: ProfileCopy, joinCode: String?) -> [String: FieldValueSpec] {
        var fields: [String: FieldValueSpec] = [
            "role": .string(role == .owner ? "owner" : "member"),
            "firstName": .string(profile.firstName),
            "lastName": .string(profile.lastName),
            "phone": .string(profile.phone),
            "joinedAt": .serverTimestamp,
            "sharing": sharingOn()
        ]
        if let avatar = profile.avatarJpeg {
            fields["avatar"] = .bytes(avatar)
        }
        // Seul un membre qui entre avec un code le porte (les règles le lisent comme la preuve d'entrée).
        if let joinCode {
            fields["joinCode"] = .string(joinCode)
        }
        return fields
    }

    private static func index(_ miffId: String) -> [String: FieldValueSpec] {
        ["miffId": .string(miffId), "joinedAt": .serverTimestamp]
    }

    /// « Créer et partager » : 5 écritures en UN lot (critère C4).
    static func create(entry: MiffEntry, code: String, name: String) -> [DocWrite] {
        let stored = MiffName.stored(name)
        guard JoinCode.isValid(code), MiffName.isValid(stored) else {
            return []
        }
        let miffId = entry.miffId
        let uid = entry.uid
        return [
            DocWrite(
                path: "miffs/\(miffId)",
                kind: .set,
                data: [
                    "ownerUid": .string(uid),
                    "name": .string(stored),
                    "joinCode": .string(code),
                    "createdAt": .serverTimestamp
                ]
            ),
            DocWrite(
                path: "joinCodes/\(code)",
                kind: .set,
                data: ["miffId": .string(miffId), "miffName": .string(stored), "createdAt": .serverTimestamp]
            ),
            DocWrite(
                path: "miffs/\(miffId)/members/\(uid)",
                kind: .set,
                data: card(role: .owner, profile: entry.profile, joinCode: nil)
            ),
            DocWrite(path: "users/\(uid)/memberships/\(entry.membershipKey)", kind: .set, data: index(miffId)),
            DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .set, data: initialStatus(entry.access))
        ]
    }

    /// « Rejoindre et partager » : 3 écritures en UN lot (critère K2).
    static func join(entry: MiffEntry, code: String) -> [DocWrite] {
        guard JoinCode.isValid(code) else {
            return []
        }
        let miffId = entry.miffId
        let uid = entry.uid
        return [
            DocWrite(
                path: "miffs/\(miffId)/members/\(uid)",
                kind: .set,
                data: card(role: .member, profile: entry.profile, joinCode: code)
            ),
            DocWrite(path: "users/\(uid)/memberships/\(entry.membershipKey)", kind: .set, data: index(miffId)),
            DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .set, data: initialStatus(entry.access))
        ]
    }

    /// Renommer : la Miff ET le nom gardé avec le code, dans le même lot (critère G3).
    static func rename(miffId: String, code: String, name: String) -> [DocWrite] {
        let stored = MiffName.stored(name)
        guard JoinCode.isValid(code), MiffName.isValid(stored) else {
            return []
        }
        return [
            DocWrite(path: "miffs/\(miffId)", kind: .update, data: ["name": .string(stored)]),
            DocWrite(path: "joinCodes/\(code)", kind: .update, data: ["miffName": .string(stored)])
        ]
    }

    /// Arrêter le partage (critère K5) : la fiche dit « non » avec la date, et le statut et la photo sont supprimés dans le
    /// même lot (les règles refusent l'arrêt sinon : aucune trace d'une position ne survit à un consentement retiré). Deux
    /// sous-champs seulement changent : la date et la version du premier consentement restent telles quelles.
    static func stopSharing(miffId: String, uid: String) -> [DocWrite] {
        [
            DocWrite(
                path: "miffs/\(miffId)/members/\(uid)",
                kind: .update,
                data: ["sharing.enabled": .bool(false), "sharing.revokedAt": .serverTimestamp]
            ),
            DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .delete),
            DocWrite(path: "miffs/\(miffId)/photos/\(uid)", kind: .delete)
        ]
    }

    /// Reprendre le partage (critère K6) : un nouveau consentement (date et version) et le premier statut de nouveau.
    static func resumeSharing(miffId: String, uid: String, access: LocationAccess) -> [DocWrite] {
        [
            DocWrite(
                path: "miffs/\(miffId)/members/\(uid)",
                kind: .update,
                data: [
                    "sharing.enabled": .bool(true),
                    "sharing.consentedAt": .serverTimestamp,
                    "sharing.consentVersion": .string(MiffSharingConsent.version),
                    "sharing.revokedAt": .null
                ]
            ),
            DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .set, data: initialStatus(access))
        ]
    }

    /// Lot « Quitter » (4 suppressions) : la fiche, le statut, la photo et l'index. Sert au nettoyage après une perte d'accès
    /// (ticket §5.7) ; le bouton « Quitter » de MIFF-4 utilisera le même lot.
    static func leave(miffId: String, uid: String, membershipKey: String) -> [DocWrite] {
        [
            DocWrite(path: "miffs/\(miffId)/members/\(uid)", kind: .delete),
            DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .delete),
            DocWrite(path: "miffs/\(miffId)/photos/\(uid)", kind: .delete),
            DocWrite(path: "users/\(uid)/memberships/\(membershipKey)", kind: .delete)
        ]
    }

    /// Repli du nettoyage : la fiche a déjà disparu (retrait), il ne reste que l'index, que les règles laissent supprimer.
    static func dropIndex(uid: String, membershipKey: String) -> [DocWrite] {
        [DocWrite(path: "users/\(uid)/memberships/\(membershipKey)", kind: .delete)]
    }
}
