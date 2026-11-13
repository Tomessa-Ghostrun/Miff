import SwiftUI

// Le branchement des lieux de l'onglet Profil à l'hôte de navigation (`MiffHomeHost`), séparé de lui pour une taille de fichier raisonnable.
// Rien ici ne lit ni n'écrit : l'hôte ouvre les écrans, le view model décide des lectures.

extension DirectoryState {
    /// Le créateur de cette Miff tel que l'annuaire le connaît ; nil si la Miff ou son créateur sont inconnus (traité comme un membre).
    func ownerUid(of miffId: String) -> String? {
        if case .ready(let ready) = self {
            return ready.miffs.first { $0.id == miffId }?.ownerUid
        }
        return nil
    }
}

extension ProfilePlacesViewModel {
    /// Un écran de lieu s'ouvre : si c'est un créateur qui y entre (le détail d'un lieu qu'il peut changer, ou le formulaire d'un nouveau),
    /// les listes de lieux sont relues à leur prochaine apparition. Le détail d'un lieu ouvert par un membre ne change rien : aucune
    /// relecture. Vaut aussi pour les lieux ouverts depuis l'écran d'une Miff (même liste de lieux).
    func routeOpened(_ route: MiffRoute, isCreator: (String) -> Bool) {
        switch route {
        case .placeForm:
            // Seul un créateur arrive à un formulaire de lieu.
            onPlacesMayChange()
        case .placeDetail(let miffId, _):
            if isCreator(miffId) {
                onPlacesMayChange()
            }
        case .create, .join, .miff, .settings, .colorsInfo:
            break
        case .placeRequestForm, .placeRequestDetail:
            // Les demandes ont leur propre view model : `ProfileRequestsViewModel.onRequestsMayChange`.
            break
        }
    }
}

extension ProfilePlacesActions {
    /// Les gestes de la section : ouvrir un lieu, « Ajouter un lieu » (le formulaire s'ouvre avec la Miff de la liste cochée en premier),
    /// réessayer, et les deux moments de l'onglet (retour à l'écran, départ).
    @MainActor
    static func make(
        model: ProfilePlacesViewModel,
        notices: PlaceNotices,
        open: @escaping (MiffRoute) -> Void
    ) -> ProfilePlacesActions {
        ProfilePlacesActions(
            onOpenPlace: { miffId, placeId in open(.placeDetail(miffId: miffId, placeId: placeId)) },
            onAddPlace: { miffId in open(.placeForm(placeId: nil, miffId: miffId)) },
            onRetry: { model.onRetry() },
            onLeave: { notices.consume() },
            onAppear: { model.onProfileAppeared() }
        )
    }
}
