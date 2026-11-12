import SwiftUI

// Le branchement des demandes d'ajout d'un lieu à l'onglet Profil et à l'hôte de navigation (`MiffHomeHost`), séparé des fichiers des lieux du
// Profil pour une taille raisonnable. Rien ici ne lit ni n'écrit : l'hôte ouvre les écrans, le view model des demandes décide des lectures.

/// Ce que la liste des lieux du Profil reçoit des demandes (ticket LIEU-REQ) : celles du membre (B), le message de la demande envoyée, le
/// message calme du dernier geste.
struct ProfileRequestsContent: Equatable, CustomStringConvertible {
    /// Un membre : ses propres demandes en attente, la plus récente d'abord.
    var requests: [PlaceRequest] = []
    var sentNotice: RequestNotice?
    var kind: RequestNoticeKind?
    var busyRequestId: String?
    /// Un créateur : les demandes reçues, la plus ancienne d'abord, avec qui les a faites.
    var received: [ReceivedRequest] = []

    static let none = ProfileRequestsContent()

    var description: String {
        "ProfileRequestsContent(redacted)"
    }

    /// Ce que la liste de `miffId` reçoit. Un créateur ne reçoit jamais « Demande envoyée… » (c'est celui du membre).
    static func make(
        state: ProfileRequestsState,
        miffId: String,
        isCreator: Bool,
        sentNotice: RequestNotice?,
        kind: RequestNoticeKind?
    ) -> ProfileRequestsContent {
        let found = state.views[miffId]?.requests ?? []
        return ProfileRequestsContent(
            requests: isCreator ? [] : found,
            sentNotice: isCreator ? nil : sentNotice,
            kind: kind,
            busyRequestId: state.busyRequestId,
            received: isCreator ? found.map { ReceivedRequest(request: $0, requester: state.requester(of: $0)) } : []
        )
    }
}

/// Ce que l'hôte de navigation fait des événements des demandes (LIEU-REQ-C) : « Accepter » qui doit ouvrir l'offre (formule gratuite pleine,
/// ou zone de danger : la demande reste en attente), et un lieu accepté qui fait relire la liste des lieux du Profil sans attendre les 5 minutes.
struct ProfileRequestsHostEffects: ViewModifier {
    @ObservedObject var requests: ProfileRequestsViewModel
    let places: ProfilePlacesViewModel
    let openPaywall: (PaywallReason) -> Void

    func body(content: Content) -> some View {
        content
            .onChange(of: requests.paywall) { reason in
                if let reason {
                    requests.consumePaywall()
                    openPaywall(reason)
                }
            }
            .onAppear {
                let places = self.places
                requests.onAccepted = { miffId in
                    places.onPlacesMayChange()
                    places.onCardShown(miffId: miffId)
                }
            }
    }
}

extension View {
    /// Les événements des demandes pour l'hôte de navigation (`ProfileRequestsHostEffects`).
    func profileRequestsEffects(
        _ requests: ProfileRequestsViewModel,
        places: ProfilePlacesViewModel,
        openPaywall: @escaping (PaywallReason) -> Void
    ) -> some View {
        modifier(ProfileRequestsHostEffects(requests: requests, places: places, openPaywall: openPaywall))
    }
}

extension AppEnvironment {
    /// Le view model des demandes d'ajout d'un lieu, créé avec la session de l'hôte (un par compte et par session).
    func makeProfileRequestsViewModel() -> ProfileRequestsViewModel {
        let session = self.session
        return ProfileRequestsViewModel(
            repository: placeRequestRepository,
            network: network,
            myUid: { session.currentUid }
        )
    }
}

extension ProfilePlacesPresentation {
    /// La liste des lieux pour `PlaceRows`, avec les demandes : l'onglet Profil les propose (un membre voit les siennes et « Demander l'ajout
    /// d'un lieu »).
    func listUi(
        places: [Place],
        premium: Bool?,
        notice: PlaceNotice?,
        requests: ProfileRequestsContent
    ) -> PlaceListUi {
        var ui = listUi(places: places, premium: premium, notice: notice)
        ui.offersRequests = true
        ui.requests = requests.requests
        ui.requestNotice = requests.sentNotice
        ui.requestKind = requests.kind
        ui.busyRequestId = requests.busyRequestId
        ui.received = requests.received
        return ui
    }
}

extension ProfilePlacesActions {
    /// Les gestes de la section avec ceux des demandes : ouvrir une demande, « Demander l'ajout d'un lieu », et le départ de l'onglet qui
    /// efface aussi le message calme d'un geste sur une demande.
    @MainActor
    func withRequests(
        model: ProfileRequestsViewModel,
        open: @escaping (MiffRoute) -> Void
    ) -> ProfilePlacesActions {
        let base = self
        return ProfilePlacesActions(
            onOpenPlace: base.onOpenPlace,
            onAddPlace: base.onAddPlace,
            onRetry: {
                base.onRetry()
                model.onRetry()
            },
            onLeave: {
                base.onLeave()
                model.consumeNotice()
            },
            onAppear: {
                base.onAppear()
                model.onProfileAppeared()
            },
            onOpenRequest: { miffId, requestId in open(.placeRequestDetail(miffId: miffId, requestId: requestId)) },
            onRequestPlace: { miffId in open(.placeRequestForm(miffId: miffId)) },
            onAcceptRequest: { model.onAccept($0) },
            onDeclineRequest: { model.onDecline($0) },
            onRequestsSeen: { model.onListSeen(miffId: $0) }
        )
    }
}
