import Foundation

// SÉCURITÉ, pour ce fichier : mêmes règles que `PlaceFormViewModel.swift`. Le nom d'une demande n'est jamais gardé : le message qui suit
// l'envoi ne porte que « hors connexion ou non » (`PlaceNotices.requestSent`).

/// Le formulaire d'une DEMANDE d'ajout d'un lieu (ticket LIEU-REQ-B, critères M3 à M5, M7, M8, M11) : le même écran que la création d'un
/// lieu, une seule Miff (celle de la liste d'où l'on vient), le bouton « Soumettre la demande ». Les limites du membre sont appliquées AVANT
/// d'écrire (les règles ne sont que le filet) ; rien n'est montré comme envoyé avant que le serveur ait confirmé ; sans réseau le téléphone
/// garde la demande et l'envoie au retour du réseau (le message le dit).
extension PlaceFormViewModel {
    /// Qui est la personne (Miff+ ou non), la Miff, les demandes qu'elle a déjà. Ne s'ouvre jamais pour le créateur de la Miff.
    func loadRequest(uid: String) async {
        guard let repository = dependencies.requests, let target = miffId else {
            update {
                $0.loading = false
                $0.loadFailed = .generic
            }
            return
        }
        switch await repository.prepare(uid: uid, miffId: target) {
        case .unavailable:
            update {
                $0.loading = false
                $0.loadFailed = .generic
            }
        case .failed(let failure):
            update {
                $0.loading = false
                $0.loadFailed = PlaceMessage(failure: failure)
            }
        case .loaded(let loaded):
            requestContext = loaded
            let blocked = loaded.mine.count >= PlaceRequestLimits.perMember
            update {
                $0.loading = false
                $0.premium = loaded.premium
                // La Miff est fixe : UNE pastille, pas de sélecteur (le même composant qu'un créateur d'une seule Miff).
                $0.owned = [OwnedMiff(id: target, name: loaded.miffName, joinedAtMillis: 0)]
                $0.selectedMiffIds = [target]
                $0.requestBlocked = blocked
            }
        }
    }

    /// « Soumettre la demande ».
    func submitRequest(_ current: PlaceFormState) {
        guard let chosen = current.chosen, let category = current.category else {
            return
        }
        guard let uid = dependencies.currentUid(), let loaded = requestContext, let target = miffId,
              let repository = dependencies.requests else {
            update { $0.message = .generic }
            return
        }
        let draft = PlaceRequestDraft(
            name: current.name,
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
        let online = dependencies.network.isOnline()
        Task { [weak self] in
            let result = await repository.submit(
                uid: uid,
                miffId: target,
                draft: draft,
                context: loaded,
                online: online
            )
            self?.finishRequest(result)
        }
    }

    private func finishRequest(_ result: RequestSubmitResult) {
        switch result {
        case .done:
            dependencies.notices.requestSent(queued: false)
            update {
                $0.saving = false
                $0.end = .saved
            }
        case .queued:
            dependencies.notices.requestSent(queued: true)
            update {
                $0.saving = false
                $0.end = .saved
            }
        case .needsPremium(let reason):
            update {
                $0.saving = false
                $0.paywall = reason
            }
        case .memberLimit:
            update {
                $0.saving = false
                $0.requestBlocked = true
                $0.message = .requestLimitMember
            }
        case .miffLimit:
            update {
                $0.saving = false
                $0.message = .requestLimitMiff
            }
        case .requestsUnavailable:
            update {
                $0.saving = false
                $0.message = .requestsUnavailable
            }
        case .miffUnavailable:
            update {
                $0.saving = false
                $0.message = .miffGone
            }
        case .failed(let failure):
            update {
                $0.saving = false
                $0.message = PlaceMessage(failure: failure)
            }
        }
    }
}
