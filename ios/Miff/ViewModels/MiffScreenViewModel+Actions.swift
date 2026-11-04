import Foundation

// SÉCURITÉ, pour ce fichier : mêmes règles que `MiffScreenViewModel.swift`. Le nouveau code rendu par un retrait n'est gardé
// que dans `MiffAction.removedDone`, le temps de la fenêtre qui le montre.

/// MIFF-4 : retirer un proche, renouveler le code, quitter, supprimer (critères N, R, Q, S et D du ticket). Chaque geste est
/// d'abord une QUESTION (rien n'est écrit), puis UN lot confirmé par le serveur : sans réseau la fenêtre le dit avec calme et
/// rien n'est mis en attente (H1). Un double appui ne lance qu'une opération (H2). Miroir d'Android MIFF-2.
extension MiffScreenViewModel {
    // MARK: - Les questions

    /// « ⋯ » > « Retirer de la Miff » sur la ligne de `uid` (le créateur seulement ; jamais sur sa propre ligne).
    func onRemoveRequested(uid: String) {
        let current = state
        guard current.isOwner, current.canWrite, current.action == nil,
              let member = current.members.first(where: { $0.uid == uid }),
              member.uid != current.myUid, member.role != .owner else {
            return
        }
        // Prénom et nom sans rien de visible : jamais « Retirer  de la Miff ? », une formule neutre à la place.
        let name = member.callName.isEmpty ? L10n.string("ios_member_unnamed") : member.callName
        update {
            $0.action = .remove(uid: member.uid, firstName: name)
            $0.message = nil
        }
    }

    /// « Créer un nouveau code » (le créateur seulement).
    func onRenewRequested() {
        let current = state
        guard current.isOwner, current.canWrite, current.action == nil else {
            return
        }
        update {
            $0.action = .renew
            $0.message = nil
            $0.codeRenewed = false
        }
    }

    /// « Quitter la Miff » (un membre seulement : le créateur ne peut que supprimer la Miff, décision QM1).
    func onLeaveRequested() {
        let current = state
        guard !current.isOwner, current.canWrite, current.action == nil else {
            return
        }
        update {
            $0.action = .leave
            $0.message = nil
        }
    }

    /// « Supprimer la Miff » (le créateur seulement).
    func onDeleteRequested() {
        let current = state
        guard current.isOwner, current.canWrite, current.action == nil else {
            return
        }
        update {
            $0.action = .delete(othersCount: current.others.count)
            $0.message = nil
        }
    }

    /// « Annuler », le geste qui ferme la fenêtre, ou « Terminer » après un retrait : ferme sans rien écrire.
    func onActionDismissed() {
        guard !state.actionBusy else {
            return
        }
        update {
            $0.action = nil
            $0.message = nil
        }
    }

    /// « Plus tard » sur l'invitation : elle est fermée et ne revient pas pour ce départ.
    func onInviteLater() {
        update { $0.inviteToRenew = false }
    }

    // MARK: - La confirmation

    func onActionConfirmed() {
        guard let action = state.action, !state.actionBusy else {
            return
        }
        switch action {
        case .removedDone:
            onActionDismissed()
        case .remove(let uid, let firstName):
            confirmRemove(uid: uid, firstName: firstName)
        case .renew:
            confirmRenew()
        case .leave:
            confirmLeave()
        case .delete:
            confirmDelete()
        }
    }

    /// Vrai quand le téléphone a un réseau ; sinon la fenêtre le dit avec calme et rien n'est envoyé (critère H1).
    private func requireNetwork() -> Bool {
        if dependencies.network.isOnline() {
            return true
        }
        update { $0.message = .offline }
        return false
    }

    private func confirmRemove(uid: String, firstName: String) {
        guard requireNetwork() else {
            return
        }
        update {
            $0.actionBusy = true
            $0.message = nil
        }
        // Noté AVANT le lot : l'écoute peut entendre la disparition avant la réponse, et ce n'est pas un « départ ».
        removedByMe.insert(uid)
        Task {
            await self.performRemove(uid: uid, firstName: firstName)
        }
    }

    private func performRemove(uid: String, firstName: String) async {
        switch await dependencies.repository.removeMember(miffId: miffId, targetUid: uid) {
        case .changed(let newCode):
            update {
                $0.actionBusy = false
                $0.action = .removedDone(firstName: firstName, newCode: newCode)
                $0.inviteToRenew = false
            }
        case .failed(let failure):
            removedByMe.remove(uid)
            update {
                $0.actionBusy = false
                $0.message = ActionMessage(failure: failure)
            }
        }
    }

    private func confirmRenew() {
        guard requireNetwork() else {
            return
        }
        update {
            $0.actionBusy = true
            $0.message = nil
        }
        Task {
            await self.performRenew()
        }
    }

    private func performRenew() async {
        switch await dependencies.repository.renewCode(miffId: miffId) {
        case .changed:
            // Le nouveau code arrive à l'écran par la lecture en direct (il est relu, jamais pris d'ici).
            update {
                $0.actionBusy = false
                $0.action = nil
                $0.inviteToRenew = false
                $0.codeRenewed = true
            }
        case .failed(let failure):
            update {
                $0.actionBusy = false
                $0.message = ActionMessage(failure: failure)
            }
        }
    }

    private func confirmLeave() {
        guard requireNetwork(), let uid = dependencies.currentUid() else {
            return
        }
        update {
            $0.actionBusy = true
            $0.message = nil
        }
        leaving = true
        Task {
            await self.performLeave(uid: uid)
        }
    }

    private func performLeave(uid: String) async {
        switch await dependencies.repository.leave(uid: uid, miffId: miffId) {
        case .done:
            // La purge doit aller jusqu'au bout même si l'écran se ferme entre-temps : cette tâche n'est pas rattachée à lui.
            await dependencies.onLeft(miffId)
            update {
                $0.actionBusy = false
                $0.action = nil
                $0.departed = true
            }
        case .failed(let failure):
            leaving = false
            update {
                $0.actionBusy = false
                $0.message = ActionMessage(failure: failure)
                // Un refus entendu pendant le départ a été ignoré : les écoutes sont rattachées de nouveau pour voir où on en est.
                $0.restart += 1
            }
        }
    }

    private func confirmDelete() {
        guard requireNetwork() else {
            return
        }
        // La suppression continue dans l'app (pas dans cet écran) : quitter l'écran ne l'arrête pas. L'état de départ est posé
        // tout de suite pour que la fenêtre ne disparaisse pas un instant entre la question et la progression.
        let started = dependencies.deletions.start(miffId: miffId)
        update {
            $0.action = nil
            $0.message = nil
            $0.inviteToRenew = false
            if let started {
                $0.deletion = started
            }
        }
    }

    /// « Réessayer » après une suppression qui s'est arrêtée.
    func onDeletionRetry() {
        if state.deletion == .stuck {
            dependencies.deletions.retry(miffId: miffId)
        }
    }
}
