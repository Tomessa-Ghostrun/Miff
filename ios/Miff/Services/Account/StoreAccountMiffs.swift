import Foundation

// SÉCURITÉ, pour ce fichier (AUTH-5c) : les noms des Miffs, les identifiants et les membres sont des données personnelles. Ils passent par
// ici et nulle part ailleurs (ni journal, ni événement Analytics, ni message) ; chaque échec sort d'ici sous la forme d'une
// `MiffFailure`. Chaque document touché est la propre fiche, le propre statut, la propre photo, la dernière position et l'index du compte
// (les lots « Quitter »), ou une Miff que le compte a CRÉÉE (la suppression d'une Miff, qui est la procédure existante de MIFF-4).

/// La suppression du compte, côté Miffs (ticket AUTH-5, §5.5 étapes 2 à 4 et §6.1). Les lots sont ceux de `MiffWrites` (4 ou 5
/// écritures pour « Quitter », 4 lectures de règles au plus), et les Miffs créées passent par `MiffDeleter` (MIFF-4, déjà fusionné : 7
/// écritures par retrait, 18 lectures de règles sur 20 au pire ; lieux 5 par lot ; un dernier lot de 8, 13 lectures) : RIEN n'est ajouté
/// à ces lots et aucun retrait n'est mêlé au dernier lot.
///
/// Elle n'utilise PAS `MiffRepository.leave` ni `cleanUpLost` exprès : ils demandent au suivi des lieux de relire les Miffs (la suppression
/// l'a arrêté), et `cleanUpLost` ne dit que oui ou non, alors qu'ici la raison d'un échec décide entre une pause (pas de réseau) et un
/// échec (un refus).
final class StoreAccountMiffs: AccountMiffs {
    private let store: MiffStore
    private let deleter: MiffDeleter
    private let timeoutSeconds: TimeInterval
    /// Appelé avant le lot « Quitter » d'une Miff que le compte a seulement rejointe (LIEU-REQ) : les demandes d'ajout d'un lieu qu'il y a faites
    /// sont reprises (elles portent une adresse et des coordonnées : elles ne restent pas orphelines une fois le compte parti, les règles ne
    /// permettent à leur auteur de les supprimer que tant qu'il est membre). Ne bloque jamais la suppression du compte.
    private let beforeLeave: (_ uid: String, _ miffId: String) async -> Void

    init(
        store: MiffStore,
        deleter: MiffDeleter,
        timeoutSeconds: TimeInterval = 20,
        beforeLeave: @escaping (_ uid: String, _ miffId: String) async -> Void = { _, _ in }
    ) {
        self.store = store
        self.deleter = deleter
        self.timeoutSeconds = timeoutSeconds
        self.beforeLeave = beforeLeave
    }

    func inventory(uid: String) async -> MiffsRead {
        let store = self.store
        let refs: [MembershipRef]
        let memberships = await guardedCall(timeoutSeconds: timeoutSeconds) {
            try await store.readMemberships(uid: uid, fromServer: true)
        }
        switch memberships {
        case .value(let found):
            refs = found
        case .failed(let failure, _):
            return .failed(failure)
        }
        var joined: [MembershipRef] = []
        var owned: [AccountOwnedMiff] = []
        var stale: [MembershipRef] = []
        var reachable: Bool?
        var seen = Set<String>()
        for ref in refs {
            // Deux index de la même Miff (jamais attendu) sont UNE Miff : le second ne trouverait qu'une Miff déjà partie.
            guard seen.insert(ref.miffId).inserted else {
                stale.append(ref)
                continue
            }
            switch await classify(ref, uid: uid, reachable: &reachable) {
            case .joined:
                joined.append(ref)
            case .owned(let miff):
                owned.append(miff)
            case .stale:
                stale.append(ref)
            case .failed(let failure):
                return .failed(failure)
            }
        }
        return .read(MiffsInventory(joined: joined, owned: owned, stale: stale))
    }

    private enum Kind {
        case joined
        case owned(AccountOwnedMiff)
        case stale
        case failed(MiffFailure)
    }

    /// Une Miff de l'index : rejointe, créée (avec le nombre des AUTRES personnes), ou périmée.
    private func classify(_ ref: MembershipRef, uid: String, reachable: inout Bool?) async -> Kind {
        let store = self.store
        let read = await guardedCall(timeoutSeconds: timeoutSeconds) {
            try await store.readMiff(miffId: ref.miffId, fromServer: true)
        }
        switch read {
        case .value(.missing):
            return .stale
        case .value(.exists(let details)):
            guard details.ownerUid == uid else {
                return .joined
            }
            let ids = await guardedCall(timeoutSeconds: timeoutSeconds) {
                try await store.readMemberIds(miffId: ref.miffId)
            }
            switch ids {
            case .value(let members):
                let others = members.filter { $0 != uid }.count
                return .owned(AccountOwnedMiff(miffId: ref.miffId, name: details.name, otherMembers: others))
            case .failed(let failure, _):
                return .failed(failure)
            }
        case .failed(let failure, let lostAccess):
            // Un refus ne veut dire « retiré » que si le serveur répond par ailleurs (un refus d'App Check refuserait tout).
            guard lostAccess else {
                return .failed(failure)
            }
            if reachable == nil {
                // La question elle-même n'est pas passée (pas de réseau) : une pause, pas un refus.
                let probe = await guardedCall(timeoutSeconds: timeoutSeconds) { await store.serverReachable(uid: uid) }
                switch probe {
                case .value(let answer):
                    reachable = answer
                case .failed(let probeFailure, _):
                    return .failed(probeFailure)
                }
            }
            return reachable == true ? .stale : .failed(failure)
        }
    }

    func dropStale(uid: String, ref: MembershipRef) async -> MiffStep {
        await leaveOrDropIndex(uid: uid, ref: ref)
    }

    func leave(uid: String, ref: MembershipRef) async -> MiffStep {
        await beforeLeave(uid, ref.miffId)
        return await leaveOrDropIndex(uid: uid, ref: ref)
    }

    /// Le lot « Quitter » ; si le serveur le refuse (la fiche est déjà partie : le créateur a retiré la personne, ou supprime la Miff),
    /// l'index seul (les règles le permettent une fois la fiche partie). Personne n'en est informé : la personne s'en va.
    private func leaveOrDropIndex(uid: String, ref: MembershipRef) async -> MiffStep {
        let store = self.store
        let leaveWrites = MiffWrites.leave(miffId: ref.miffId, uid: uid, membershipKey: ref.key)
        let leave = await guardedCall(timeoutSeconds: timeoutSeconds) { try await store.commit(leaveWrites) }
        if case .value = leave {
            return .done
        }
        if case .failed(let failure, _) = leave, failure == .offline {
            return .failed(.offline)
        }
        let indexWrites = MiffWrites.dropIndex(miffId: ref.miffId, uid: uid, membershipKey: ref.key)
        let index = await guardedCall(timeoutSeconds: timeoutSeconds) { try await store.commit(indexWrites) }
        switch index {
        case .value:
            return .done
        case .failed(let failure, _):
            return .failed(failure)
        }
    }

    func deleteOwned(uid: String, miffId: String) async -> MiffStep {
        switch await deleter.run(uid: uid, miffId: miffId) {
        case .deleted:
            return .done
        case .notOwner:
            // Personne d'autre ne supprime une Miff à la place de son créateur : si elle n'est plus celle du compte, rien n'est fait et la
            // procédure s'arrête (jamais devinée).
            return .failed(.unknown)
        case .paused(let failure):
            return .failed(failure)
        }
    }
}
