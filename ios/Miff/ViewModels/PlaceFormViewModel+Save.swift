import Foundation

// SÉCURITÉ, pour ce fichier : mêmes règles que `PlaceFormViewModel.swift`. Le nom d'un lieu enregistré n'est gardé que dans
// `PlaceNotices`, en mémoire, le temps du message qui le montre.

/// Enregistrer et supprimer. Un appui = une opération (critère HOR2) ; sans réseau, la réponse est immédiate et rien n'est mis en
/// file (critère PL9) ; un lieu n'est dit « enregistré » qu'après l'accusé du serveur.
extension PlaceFormViewModel {
    // MARK: - Enregistrer

    func onSave() {
        let current = state
        guard current.canSave else {
            return
        }
        // Dès 1 caractère le bouton marche ; sous 2, la phrase du ticket s'affiche et rien n'est écrit (PL2).
        guard PlaceName.isValid(current.name) else {
            update { $0.nameTooShort = true }
            return
        }
        // Rien à tenter sans réseau : la réponse est immédiate et rien n'attend (PL9).
        guard dependencies.network.isOnline() else {
            update { $0.message = .offline }
            return
        }
        guard let chosen = current.chosen, let category = current.category else {
            return
        }
        guard let uid = dependencies.currentUid(), let loaded = context else {
            update { $0.message = .generic }
            return
        }
        let draft = PlaceDraft(
            name: current.name,
            miffIds: current.selectedMiffIds,
            address: chosen.label,
            lat: chosen.lat,
            lng: chosen.lng,
            radiusM: current.radiusM,
            type: current.type,
            category: category
        )
        guard draft.problem == nil else {
            update { $0.message = .generic }
            return
        }
        update {
            $0.saving = true
            $0.message = nil
        }
        let group = original
        Task { [weak self] in
            await self?.write(draft: draft, uid: uid, original: group, loaded: loaded)
        }
    }

    private func write(draft: PlaceDraft, uid: String, original: PlaceGroup?, loaded: PlaceContext) async {
        let repository = dependencies.repository
        let result: PlaceWriteResult
        if let original {
            result = await repository.update(uid: uid, original: original, draft: draft, context: loaded)
        } else {
            result = await repository.create(uid: uid, draft: draft, context: loaded)
        }
        switch result {
        case .done:
            dependencies.notices.saved(name: PlaceName.stored(draft.name))
            update {
                $0.saving = false
                $0.end = .saved
            }
        case .needsPremium(let reason):
            update {
                $0.saving = false
                $0.paywall = reason
            }
        case .miffUnavailable:
            update {
                $0.saving = false
                $0.message = .miffGone
            }
        case .placeGone:
            update {
                $0.saving = false
                $0.end = .placeGone
            }
        case .idTaken:
            update {
                $0.saving = false
                $0.message = .idTaken
            }
        case .failed(let failure):
            update {
                $0.saving = false
                $0.message = PlaceMessage(failure: failure)
            }
        }
    }

    // MARK: - Supprimer

    /// « Supprimer ce lieu » : la question d'abord (rien n'est écrit).
    func onDeleteRequested() {
        let current = state
        guard current.mode == .edit, !current.busy, original != nil else {
            return
        }
        update {
            $0.deleteDialogOpen = true
            $0.deleteMessage = nil
        }
    }

    func onDeleteDismissed() {
        guard !state.deleting else {
            return
        }
        update {
            $0.deleteDialogOpen = false
            $0.deleteMessage = nil
        }
    }

    func onDeleteConfirmed() {
        let current = state
        guard let group = original, !current.deleting, current.deleteDialogOpen else {
            return
        }
        guard dependencies.network.isOnline() else {
            update { $0.deleteMessage = .offline }
            return
        }
        update {
            $0.deleting = true
            $0.deleteMessage = nil
        }
        // La suppression suit les Miffs où le lieu EST maintenant (pas ce qui est coché dans le formulaire).
        Task { [weak self] in
            await self?.runDelete(placeId: group.id, miffIds: group.miffIds)
        }
    }

    private func runDelete(placeId: String, miffIds: [String]) async {
        switch await dependencies.repository.delete(placeId: placeId, miffIds: miffIds) {
        case .done:
            update {
                $0.deleting = false
                $0.deleteDialogOpen = false
                $0.end = .deleted
            }
        case .failed(let failure):
            update {
                $0.deleting = false
                $0.deleteMessage = ActionMessage(failure: failure)
            }
        case .needsPremium, .miffUnavailable, .placeGone, .idTaken:
            update {
                $0.deleting = false
                $0.deleteMessage = .generic
            }
        }
    }
}
