#if DEBUG
import SwiftUI

/// Les écrans des lieux montrés SEULS avec un état fixe, pour les captures d'écran de la CI
/// (`xcrun simctl launch ... -MiffPreview place-form-filled`). Aucun réseau pour les données, aucune session, aucune écriture : ce sont
/// les vrais écrans, nourris par des états écrits ici (noms et adresses de démonstration, jamais de vraies données). La carte, elle,
/// demande ses tuiles à Apple. Compilé en configuration Debug SEULEMENT, comme `ScreenPreview`.
struct PlaceScreenPreview: View {
    let name: String

    private static let miffA = "AbCdEfGhIjKlMnOpQrSt"
    private static let miffB = "ZyXwVuTsRqPoNmLkJiHg"
    private static let miffC = "QwErTyUiOpAsDfGhJkLz"

    var body: some View {
        switch name {
        case "place-detail", "place-detail-danger", "place-detail-member":
            PlaceDetailContent(state: detailState, onBack: {}, onEdit: {}, onRetry: {})
        case "place-paywall-limit":
            PlacePaywallContent(reason: .limit, onClose: {})
        case "place-paywall-danger":
            PlacePaywallContent(reason: .danger, onClose: {})
        case "place-paywall-miff":
            PlacePaywallContent(reason: .extraMiff, onClose: {})
        case "place-list-free", "place-list-premium", "place-list-member", "place-list-saved":
            MiffScreenContent(state: miffState, actions: miffActions, places: listContext)
        default:
            PlaceFormContent(state: formState, actions: .none)
        }
    }

    // MARK: - Données de démonstration

    private static let home = Place(
        id: "p1",
        miffId: miffA,
        name: "Maison de Papa",
        type: .trusted,
        category: .home,
        address: "26 rue du Volnay, 21000 Dijon, France",
        lat: 47.32207,
        lng: 5.04148,
        radiusM: 150,
        createdBy: "u1"
    )

    private static let site = Place(
        id: "p3",
        miffId: miffA,
        name: "Chantier rue Haute",
        type: .risk,
        category: .other,
        address: "12 rue Haute, 21000 Dijon, France",
        lat: 47.31995,
        lng: 5.03612,
        radiusM: 100,
        createdBy: "u1"
    )

    private static let soccer = Place(
        id: "p2",
        miffId: miffA,
        name: "Foot de Thomas",
        type: .trusted,
        category: .sport,
        address: "Stade des Poussots, 13 Avenue du Tarn, 21000 Dijon",
        lat: 47.30981,
        lng: 5.05233,
        radiusM: 200,
        createdBy: "u1"
    )

    private var owned: [OwnedMiff] {
        [
            OwnedMiff(id: Self.miffA, name: "Famille Moudugenou", joinedAtMillis: 1),
            OwnedMiff(id: Self.miffB, name: "Les copains", joinedAtMillis: 2),
            OwnedMiff(id: Self.miffC, name: "Voisins du 12", joinedAtMillis: 3)
        ]
    }

    private var address: ResolvedAddress {
        ResolvedAddress(label: "17 Avenue d'Italie, 75013 Paris, France", lat: 48.82595, lng: 2.35912)
    }

    // MARK: - Formulaire

    private var formState: PlaceFormState {
        var state = PlaceFormState()
        state.loading = false
        state.owned = owned
        state.selectedMiffIds = [Self.miffA]
        state.lastJoinedId = Self.miffA
        switch name {
        case "place-form-suggestions":
            state.name = "Maison"
            state.addressText = "17 av"
            state.suggestions = .found(suggestions)
        case "place-form-filled":
            fill(&state)
        case "place-form-small-radius":
            fill(&state)
            state.radiusM = 50
        case "place-form-errors":
            fill(&state)
            state.name = "M"
            state.nameTooShort = true
            state.message = .offline
        case "place-form-edit", "place-form-delete":
            fill(&state)
            state.mode = .edit
            state.premium = true
            state.initial = state.snapshot
            state.selectedMiffIds = [Self.miffA, Self.miffB]
            state.deleteDialogOpen = name == "place-form-delete"
        case "place-form-free":
            state.name = "École"
            state.owned = [owned[0]]
        case "place-form-miffs":
            fill(&state)
            state.selectedMiffIds = [Self.miffA, Self.miffB]
            state.miffSheetOpen = true
        default:
            break
        }
        return state
    }

    private func fill(_ state: inout PlaceFormState) {
        state.name = "Maison du père de Nathan"
        state.premium = true
        state.addressText = address.label
        state.chosen = address
        state.radiusM = 150
        state.selectedMiffIds = [Self.miffA, Self.miffB]
    }

    private var suggestions: [AddressSuggestion] {
        [
            AddressSuggestion(id: "s1", title: "17 Avenue d'Italie", subtitle: "75013 Paris, France"),
            AddressSuggestion(id: "s2", title: "17 Avenue de la République", subtitle: "75011 Paris, France"),
            AddressSuggestion(id: "s3", title: "17 Avenue Victor Hugo", subtitle: "92100 Boulogne-Billancourt, France"),
            AddressSuggestion(id: "s4", title: "17 Avenue de Verdun", subtitle: "94200 Ivry-sur-Seine, France")
        ]
    }

    // MARK: - Détail

    private var detailState: PlaceDetailState {
        var state = PlaceDetailState()
        state.status = .ready
        state.place = name == "place-detail-danger" ? Self.site : Self.home
        state.miffName = "Famille Moudugenou"
        state.creatorName = "Emmanuelle Martin"
        state.creatorIsMe = name != "place-detail-member"
        state.canEdit = name != "place-detail-member"
        return state
    }

    // MARK: - Liste (écran d'une Miff)

    private var miffActions: MiffScreenActions {
        let none = {}
        return MiffScreenActions(
            onBack: none,
            onRetry: none,
            onNameChanged: { _ in },
            onSaveName: none,
            onSharingRequested: none,
            onSharingConfirmed: none,
            onSharingDismissed: none,
            onRemoveRequested: { _ in },
            onRenewRequested: none,
            onLeaveRequested: none,
            onDeleteRequested: none,
            onActionConfirmed: none,
            onActionDismissed: none,
            onInviteLater: none,
            onDeletionRetry: none
        )
    }

    private var miffState: MiffScreenState {
        var state = MiffScreenState()
        state.status = .ready
        state.miffId = Self.miffA
        state.name = "Famille Moudugenou"
        state.nameInput = "Famille Moudugenou"
        state.joinCode = "XTZRSP367988"
        state.isOwner = name != "place-list-member"
        state.myUid = state.isOwner ? "u1" : "u2"
        state.members = [
            MiffMember(
                uid: "u1",
                firstName: "Emmanuelle",
                lastName: "Martin",
                avatarJpeg: nil,
                role: .owner,
                sharingEnabled: true
            ),
            MiffMember(
                uid: "u2",
                firstName: "Ryadh",
                lastName: "Martin",
                avatarJpeg: nil,
                role: .member,
                sharingEnabled: true
            )
        ]
        state.places = name == "place-list-premium" ? [Self.home, Self.soccer, Self.site] : [Self.home, Self.soccer]
        state.sharing = true
        return state
    }

    private var listContext: PlaceListContext {
        var context = PlaceListContext()
        context.premium = name == "place-list-premium" ? true : false
        if name == "place-list-saved" {
            context.notice = .saved(name: "Maison de Papa")
        }
        return context
    }
}
#endif
