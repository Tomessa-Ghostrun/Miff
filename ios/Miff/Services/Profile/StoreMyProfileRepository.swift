import Foundation

// SÉCURITÉ, pour ce fichier : prénom, nom, numéro et photo sont des données personnelles. Ils partent vers Firestore par ce dépôt et
// nulle part ailleurs : jamais journalisés, jamais dans un événement Analytics, jamais dans un message d'erreur (chaque erreur est
// réduite à un `ProfileWriteResult` avant de sortir d'ici). Aucun identifiant n'entre dans un chemin sans avoir la forme d'un
// identifiant (le `uid` sans « / », les Miffs 20 lettres et chiffres).

/// Le résultat d'un appel gardé : sa valeur, ou comment il a échoué (sans l'erreur elle-même).
private enum MyProfileOutcome<Value> {
    case value(Value)
    case failed(ProfileWriteResult)
}

/// L'enregistrement de « Mes informations » sur le profil et sur la fiche de chaque Miff, par le `MiffStore` de l'app (transactions,
/// garde d'écriture et conversion des valeurs déjà en place : aucun nouveau fichier qui écrit dans Firestore).
///
/// Ordre des écritures, et pourquoi (même contrat qu'Android, `FirestoreMyProfileRepository`) :
/// 1. UNE transaction avec le profil et la fiche de chaque Miff, 15 au plus : les règles relisent le profil pour chaque fiche et une
///    requête fait 20 lectures de règles au plus (`backend/data-model.md` §2.6, marge de 5). C'est le contrat du modèle de données : une
///    fiche n'est jamais vue avec un autre numéro que le profil.
/// 2. Si le serveur refuse cette transaction alors qu'il y a des fiches (une Miff qui n'existe plus, une adhésion qui lui a survécu),
///    le profil est écrit SEUL d'abord, puis chaque fiche seule : une Miff périmée n'empêche jamais quelqu'un de changer son propre nom.
///    Une fiche refusée seule est ignorée (la Miff a disparu).
/// 3. Plus de 15 Miffs (Miff+) : les fiches suivantes partent par groupes de 15, le profil est déjà écrit.
/// Seul un réseau absent laisse une fiche « en attente » (`.savedPartial`) : le même enregistrement, renouvelé, finit le travail.
///
/// ÉCART avec Android, voulu : quand un groupe (ou une fiche seule) échoue faute de réseau, les suivants ne sont PAS tentés un par un
/// (Android le fait : jusqu'à 15 attentes de 20 s pour rien) ; ils restent « en attente », comme ils l'auraient été.
final class StoreMyProfileRepository: MyProfileRepository {
    /// `backend/data-model.md` §2.6 : 15 fiches au plus par requête (20 lectures de règles, 5 de marge).
    static let maxCopiesPerRequest = 15
    /// Assez long pour un réseau lent, assez court pour ne pas laisser la personne devant un indicateur qui tourne.
    static let defaultTimeoutSeconds: TimeInterval = 20

    private let store: MiffStore
    private let timeoutSeconds: TimeInterval

    init(store: MiffStore, timeoutSeconds: TimeInterval = StoreMyProfileRepository.defaultTimeoutSeconds) {
        self.store = store
        self.timeoutSeconds = timeoutSeconds
    }

    func save(uid: String, profile: ProfileSummary) async -> ProfileSaveResult {
        guard Self.isWritable(uid: uid, profile: profile) else {
            return .failed
        }
        let data = Self.documentOf(profile)
        // L'index des Miffs est relu auprès du SERVEUR, jamais du cache : une fiche dont la Miff a disparu ferait refuser tout le lot.
        let store = self.store
        let found = await attempt { try await store.readMemberships(uid: uid, fromServer: true) }
        let miffIds: [String]
        switch found {
        case .value(let refs):
            miffIds = Self.distinctMiffIds(refs)
        case .failed(let failure):
            return Self.saveResult(of: failure)
        }

        let firstGroup = Array(miffIds.prefix(Self.maxCopiesPerRequest))
        let first = await commit(uid: uid, data: data, withProfile: true, miffIds: firstGroup)
        if first == .done {
            let pending = await writeRemainingGroups(uid: uid, data: data, miffIds: miffIds)
            return pending ? .savedPartial : .saved
        }
        if first == .offline || firstGroup.isEmpty {
            return Self.saveResult(of: first)
        }
        let profileAlone = await commit(uid: uid, data: data, withProfile: true, miffIds: [])
        guard profileAlone == .done else {
            return Self.saveResult(of: profileAlone)
        }
        let pending = await writeCopiesAlone(uid: uid, data: data, miffIds: miffIds)
        return pending ? .savedPartial : .saved
    }

    // MARK: - Intérieur

    /// Les fiches au-delà de la première requête, par groupes de 15. Vrai = il en reste à écrire (réseau absent).
    private func writeRemainingGroups(uid: String, data: [String: FieldValueSpec], miffIds: [String]) async -> Bool {
        let rest = Array(miffIds.dropFirst(Self.maxCopiesPerRequest))
        var start = 0
        while start < rest.count {
            let end = min(start + Self.maxCopiesPerRequest, rest.count)
            let group = Array(rest[start..<end])
            start = end
            let result = await commit(uid: uid, data: data, withProfile: false, miffIds: group)
            if result == .done {
                continue
            }
            if result == .offline {
                return true
            }
            if await writeCopiesAlone(uid: uid, data: data, miffIds: group) {
                return true
            }
        }
        return false
    }

    /// Chaque fiche seule. Vrai = au moins une reste à faire (réseau absent) ; une fiche que le serveur refuse est ignorée (la Miff a
    /// disparu).
    private func writeCopiesAlone(uid: String, data: [String: FieldValueSpec], miffIds: [String]) async -> Bool {
        for miffId in miffIds {
            let result = await commit(uid: uid, data: data, withProfile: false, miffIds: [miffId])
            if result == .offline {
                return true
            }
        }
        return false
    }

    /// UNE transaction : `users/{uid}` (quand `withProfile`) et la fiche de chaque Miff de `miffIds`.
    private func commit(
        uid: String,
        data: [String: FieldValueSpec],
        withProfile: Bool,
        miffIds: [String]
    ) async -> ProfileWriteResult {
        var writes: [DocWrite] = []
        if withProfile {
            writes.append(DocWrite(path: "users/\(uid)", kind: .update, data: data))
        }
        for miffId in miffIds {
            writes.append(DocWrite(path: "miffs/\(miffId)/members/\(uid)", kind: .update, data: data))
        }
        let store = self.store
        let batch = writes
        let outcome = await attempt { try await store.commit(batch) }
        switch outcome {
        case .value:
            return .done
        case .failed(let failure):
            return failure
        }
    }

    private func attempt<Value>(_ call: @escaping () async throws -> Value) async -> MyProfileOutcome<Value> {
        do {
            return .value(try await timedOperation(seconds: timeoutSeconds, call))
        } catch is OperationTimedOut {
            return .failed(.offline)
        } catch {
            return .failed(interpretWriteError(error))
        }
    }

    private static func saveResult(of result: ProfileWriteResult) -> ProfileSaveResult {
        switch result {
        case .done:
            return .saved
        case .offline:
            return .offline
        case .refused:
            return .refused
        case .failed:
            return .failed
        }
    }

    /// Les valeurs sont celles que les règles exigent : noms de 1 à 50, numéro E.164, photo de 20 000 octets au plus ; le `uid` entre dans
    /// des chemins, il n'est donc ni vide ni porteur d'un « / ».
    private static func isWritable(uid: String, profile: ProfileSummary) -> Bool {
        guard !uid.isEmpty, !uid.contains("/"),
              let phone = profile.phone, ProfileDocument.isE164(phone),
              ProfileDocument.isNameValid(profile.firstName), ProfileDocument.isNameValid(profile.lastName) else {
            return false
        }
        return (profile.avatarJpeg?.count ?? 0) <= ProfileDocument.maxAvatarBytes
    }

    /// Les quatre clés qui changent, TOUJOURS les quatre : une fiche restée en retard après un échec est réparée par n'importe quel
    /// enregistrement. Sans photo, la clé est retirée (et non mise à « null »), comme sur Android.
    static func documentOf(_ profile: ProfileSummary) -> [String: FieldValueSpec] {
        [
            "firstName": .string(profile.firstName),
            "lastName": .string(profile.lastName),
            "phone": .string(profile.phone ?? ""),
            "avatar": profile.avatarJpeg.map { FieldValueSpec.bytes($0) } ?? .deleteField
        ]
    }

    /// Les Miffs de l'index, sans doublon (deux index de la même Miff ne font qu'une fiche) et seulement celles dont l'identifiant a la
    /// forme que les règles exigent.
    static func distinctMiffIds(_ refs: [MembershipRef]) -> [String] {
        var seen = Set<String>()
        var ids: [String] = []
        for ref in refs where MiffId.isValid(ref.miffId) && seen.insert(ref.miffId).inserted {
            ids.append(ref.miffId)
        }
        return ids
    }
}
