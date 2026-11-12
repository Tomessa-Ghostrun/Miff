import SwiftUI

/// Ce qui relance l'écouteur du créateur : la Miff suivie, et l'app qui est devant ou non.
private struct RequestsWatchKey: Equatable {
    let miffId: String?
    let active: Bool
    /// « Réessayer » : un autre numéro, donc un autre écouteur.
    let epoch: Int
}

/// Ce qui dit au view model quelle est la Miff active du Feed et si la personne l'a créée.
private struct ActiveMiffKey: Equatable {
    let miffId: String?
    let ownerUid: String?
}

/// L'écouteur des demandes d'ajout d'un lieu pour le CRÉATEUR (ticket LIEU-REQ-C, §7.1) : un seul, sur une seule Miff (celle de la carte
/// regardée si la personne l'a créée, sinon la Miff active du Feed si elle l'a créée), branché seulement pendant que l'app est devant (il
/// est annulé quand elle passe en arrière-plan : rien n'écoute une Miff que personne ne regarde, et aucun réveil en arrière-plan n'est
/// prévu : le créateur découvre la demande à sa prochaine ouverture de l'app). Il ne dessine rien.
struct ProfileRequestsWatcher: View {
    @ObservedObject var model: ProfileRequestsViewModel
    let miffs: DirectoryState
    let myUid: String?
    @Environment(\.scenePhase) private var scenePhase

    private var activeKey: ActiveMiffKey {
        if case .ready(let ready) = miffs, let id = ready.activeId {
            return ActiveMiffKey(miffId: id, ownerUid: ready.miffs.first { $0.id == id }?.ownerUid)
        }
        return ActiveMiffKey(miffId: nil, ownerUid: nil)
    }

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .accessibilityHidden(true)
            // La Miff active du Feed et si la personne l'a créée : l'annuaire le sait déjà (aucune lecture).
            .task(id: activeKey) {
                let key = activeKey
                model.onActiveMiff(
                    miffId: key.miffId,
                    creator: PlaceViewerRole.isCreator(ownerUid: key.ownerUid, myUid: myUid)
                )
            }
            .task(id: RequestsWatchKey(miffId: model.tracked, active: scenePhase == .active, epoch: model.watchEpoch)) {
                guard scenePhase == .active, let miffId = model.tracked else {
                    return
                }
                await model.watch(miffId: miffId)
            }
    }
}
