import Foundation

// SÉCURITÉ, pour ce fichier : comme `MiffRepository.swift`, codes, noms et identifiants ne sont jamais journalisés, jamais dans
// un événement Analytics, jamais dans un message ; chaque échec sort d'ici sous la forme d'une `MiffFailure`.

/// MIFF-4 dans le dépôt (critères N1, R1 à R7, Q1 à Q4) : renouveler le code, retirer un proche, quitter. Un lot par action,
/// toujours confirmé par le serveur ; un nouveau code valide et différent ; un lot dont la réponse s'est perdue est vérifié
/// auprès du serveur avant d'être appelé un échec. Miroir de `FirestoreMiffRepository` d'Android (MIFF-2).
extension FirestoreMiffRepository {
    func cleanUpLost(uid: String, lost: LostMiff) async -> Bool {
        let store = self.store
        // `LostMiff` n'est construit que par `MiffStateReader`, quand le serveur lui-même refuse la Miff (hors cache, serveur joignable) : le nettoyage n'a donc pas besoin de
        // son propre drapeau « perte confirmée ». L'appelant (l'annuaire) ne le demande qu'une fois la perte CONFIRMÉE par le serveur : retiré par le créateur, ou Miff supprimée par un autre
        // membre. Mon alerte de cette Miff est partie avec elle (le lot ci-dessous, ou la fiche déjà disparue, quelle que soit l'issue du nettoyage) : le contrôleur
        // est prévenu EN PREMIER, pour que la bannière parte et qu'une nouvelle alerte soit possible ailleurs, même si le nettoyage échoue ou ne retire que l'index.
        if rules.alertsEnabled {
            rules.onAlertGone(lost.ref.miffId)
        }
        // Le nettoyage demande le serveur : sans lui rien n'est touché (le prochain démarrage le fera).
        let leaveWrites = MiffWrites.leave(
            miffId: lost.ref.miffId, uid: uid, membershipKey: lost.ref.key, withAlert: rules.alertsEnabled
        )
        var leave = await write { try await store.commit(leaveWrites) }
        // ALERTE : les règles en production peuvent ne pas connaître l'alerte : un lot refusé AVEC sa suppression est réessayé SANS.
        if rules.alertsEnabled, leave == .failed(.refused) {
            let plain = MiffWrites.leave(miffId: lost.ref.miffId, uid: uid, membershipKey: lost.ref.key)
            leave = await write { try await store.commit(plain) }
        }
        if leave == .done {
            rules.onSharingChanged()
            return true
        }
        // Retiré : la fiche a déjà disparu, le lot est refusé. Il ne reste que l'index, que les règles laissent supprimer.
        let dropWrites = MiffWrites.dropIndex(miffId: lost.ref.miffId, uid: uid, membershipKey: lost.ref.key)
        let indexOnly = await write { try await store.commit(dropWrites) }
        if indexOnly == .done {
            rules.onSharingChanged()
        }
        return indexOnly == .done
    }

    func renewCode(miffId: String) async -> CodeChangeResult {
        guard MiffId.isValid(miffId) else {
            return .failed(.unknown)
        }
        return await changeCode(miffId: miffId) { view, newCode in
            MiffWrites.renewCode(
                miffId: miffId,
                name: view.details.name,
                oldCode: view.details.joinCode,
                newCode: newCode,
                oldCodeExists: view.codeDocumentExists
            )
        }
    }

    func removeMember(miffId: String, targetUid: String) async -> CodeChangeResult {
        guard MiffId.isValid(miffId) else {
            return .failed(.unknown)
        }
        // ALERTE : avec la fonction allumée, le lot de retrait supprime AUSSI l'alerte du membre (les règles refusent le retrait tant qu'elle existe ;
        // le créateur ne peut la supprimer que dans ce lot). Les règles en production peuvent ne pas la connaître : un refus est réessayé SANS.
        let first = await removeMemberLot(miffId: miffId, targetUid: targetUid, withAlert: rules.alertsEnabled)
        if rules.alertsEnabled, case .failed(.refused) = first {
            return await removeMemberLot(miffId: miffId, targetUid: targetUid, withAlert: false)
        }
        return first
    }

    private func removeMemberLot(miffId: String, targetUid: String, withAlert: Bool) async -> CodeChangeResult {
        await changeCode(miffId: miffId) { view, newCode in
            MiffWrites.removeMember(
                miffId: miffId,
                targetUid: targetUid,
                name: view.details.name,
                oldCode: view.details.joinCode,
                newCode: newCode,
                oldCodeExists: view.codeDocumentExists,
                withAlert: withAlert
            )
        }
    }

    /// UN lot qui change le code. La Miff est lue DANS la transaction (le code et le nom à remplacer sont ceux de ce moment-là ;
    /// si un autre téléphone renouvelle en même temps, Firestore rejoue le lot sur la copie fraîche). Un lot dont la réponse
    /// s'est perdue est vérifié auprès du serveur : si la Miff porte maintenant le nouveau code, il est parti.
    private func changeCode(
        miffId: String,
        writes: @escaping (MiffTxView, String) -> [DocWrite]
    ) async -> CodeChangeResult {
        let newCode: String
        switch await freeCode() {
        case .free(let drawn):
            newCode = drawn
        case .failed(let failure):
            return .failed(failure)
        }
        let store = self.store
        let result = await write {
            try await store.commitWithMiff(miffId: miffId) { view in
                writes(view, newCode)
            }
        }
        switch result {
        case .done:
            return .changed(newCode: newCode)
        case .failed(let failure):
            if failure.mayHaveLanded, await currentCode(of: miffId) == newCode {
                return .changed(newCode: newCode)
            }
            return .failed(failure)
        }
    }

    /// Le code que la Miff porte auprès du SERVEUR, ou nil si on ne peut pas le lire.
    private func currentCode(of miffId: String) async -> String? {
        let store = self.store
        let read = await guarded { try await store.readMiff(miffId: miffId, fromServer: true) }
        if case .value(.exists(let details)) = read {
            return details.joinCode
        }
        return nil
    }

    func leave(uid: String, miffId: String) async -> MiffWriteResult {
        guard MiffId.isValid(miffId) else {
            return .failed(.unknown)
        }
        // Les demandes d'ajout d'un lieu partent AVANT que la personne cesse d'être membre (ensuite les règles refuseraient leur retrait).
        await rules.beforeLeave(uid, miffId)
        let store = self.store
        // La clé de l'index est relue auprès du SERVEUR (« free », ou l'identifiant de la Miff) : on la trouve par son CHAMP
        // miffId, jamais par l'identifiant du document.
        let read = await guarded { try await store.readMemberships(uid: uid, fromServer: true) }
        let key: String?
        switch read {
        case .value(let refs):
            key = refs.first { $0.miffId == miffId }?.key
        case .failed(let failure, _):
            return .failed(failure)
        }
        let writes = MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: key, withAlert: rules.alertsEnabled)
        var result = await write { try await store.commit(writes) }
        // ALERTE : même tolérance que l'arrêt du partage (les règles en production peuvent ne pas connaître l'alerte).
        if rules.alertsEnabled, result == .failed(.refused) {
            let plain = MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: key)
            result = await write { try await store.commit(plain) }
        }
        if result == .done {
            if rules.alertsEnabled {
                rules.onAlertGone(miffId)
            }
            rules.onSharingChanged()
        }
        if case .failed(let failure) = result, failure.mayHaveLanded {
            if await leftAfterLostAnswer(uid: uid, miffId: miffId) {
                // Le lot est bien parti alors que sa réponse s'est perdue : l'alerte de cette Miff est partie avec lui.
                if rules.alertsEnabled {
                    rules.onAlertGone(miffId)
                }
                rules.onSharingChanged()
                return .done
            }
        }
        return result
    }

    /// La réponse du lot s'est perdue : si le serveur refuse maintenant la Miff à cette personne (et qu'il répond par ailleurs),
    /// le lot est bien parti.
    private func leftAfterLostAnswer(uid: String, miffId: String) async -> Bool {
        let store = self.store
        let check = await guarded { try await store.readMiff(miffId: miffId, fromServer: true) }
        guard case .failed(_, let lostAccess) = check, lostAccess else {
            return false
        }
        let reachable = await guarded { await store.serverReachable(uid: uid) }
        if case .value(let isReachable) = reachable {
            return isReachable
        }
        return false
    }
}
