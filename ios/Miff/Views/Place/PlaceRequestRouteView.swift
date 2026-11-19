import SwiftUI

/// Ce que l'hôte de navigation fait pour les écrans des demandes d'ajout d'un lieu : retour, fin du formulaire, ouverture de l'offre.
struct PlaceRequestRouteActions {
    let onBack: () -> Void
    let onFormEnd: (PlaceFormEnd) -> Void
    /// L'offre doit s'ouvrir. Le formulaire d'une DEMANDE ne la demande jamais (demander une zone de danger est permis à tout membre, décision du
    /// PM du 2026-11-17) : l'hôte garde ce chemin commun avec le formulaire d'un lieu.
    let onPaywall: (PaywallReason) -> Void
}

/// Les deux écrans d'une demande d'ajout d'un lieu (ticket LIEU-REQ), séparés de l'hôte de navigation pour une taille de fichier
/// raisonnable : le formulaire (le même écran que la création d'un lieu, une seule Miff, « Soumettre la demande ») et le détail d'une
/// demande en attente. Seuls des identifiants voyagent dans la destination.
@MainActor
struct PlaceRequestRouteView: View {
    let route: MiffRoute
    let environment: AppEnvironment
    let model: ProfileRequestsViewModel
    let actions: PlaceRequestRouteActions

    var body: some View {
        switch route {
        case .placeRequestForm(let miffId):
            PlaceFormView(
                placeId: nil,
                miffId: miffId,
                dependencies: environment.placeFormDependencies(),
                onBack: actions.onBack,
                exits: PlaceFormExits(
                    onEnd: actions.onFormEnd,
                    onPaywall: { reason, _ in actions.onPaywall(reason) }
                ),
                isRequest: true
            )
        case .placeRequestDetail(let miffId, let requestId):
            PlaceRequestDetailView(
                model: model,
                miffId: miffId,
                requestId: requestId,
                myUid: environment.session.currentUid,
                onClose: actions.onBack
            )
        default:
            EmptyView()
        }
    }
}
