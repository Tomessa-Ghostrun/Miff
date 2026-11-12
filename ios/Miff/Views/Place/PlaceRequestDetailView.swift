import SwiftUI

// SÉCURITÉ, pour ce fichier : la demande (nom, adresse, coordonnées) est lue dans l'état du view model des demandes et dessinée ; rien
// n'est journalisé ni envoyé comme événement.

/// Le détail d'une demande en attente (ticket LIEU-REQ, section 4.2) : le lieu tel qu'il a été demandé (carte, cercle, adresse, rayon,
/// type), un bandeau calme, et les boutons de la personne qui le regarde. Il ne lit rien de lui-même : la demande vient du view model
/// des demandes, qui l'a lue avec la liste.
///
/// Le DEMANDEUR : « En attente de réponse. » et « Retirer ma demande ». Une demande qui n'est plus là (reprise, expirée, traitée sur un
/// autre téléphone) ramène à la liste avec une phrase neutre ; celle que la personne reprend elle-même dit ce qui s'est passé (jamais les
/// deux).
@MainActor
struct PlaceRequestDetailView: View {
    @ObservedObject private var model: ProfileRequestsViewModel
    private let miffId: String
    private let requestId: String
    private let myUid: String?
    private let onClose: () -> Void
    @State private var leaving = false

    init(
        model: ProfileRequestsViewModel,
        miffId: String,
        requestId: String,
        myUid: String?,
        onClose: @escaping () -> Void
    ) {
        _model = ObservedObject(wrappedValue: model)
        self.miffId = miffId
        self.requestId = requestId
        self.myUid = myUid
        self.onClose = onClose
    }

    /// Le rôle vient de la Miff (la personne l'a-t-elle créée ? : l'annuaire le sait), jamais d'un uid déduit de l'identifiant.
    private var isCreator: Bool {
        PlaceRequestRole.isCreator(miffId: miffId, creatorMiffs: model.creatorMiffs)
    }

    /// La demande est la MIENNE : seul son auteur la retire (jamais déduit d'un uid inconnu).
    private var isAuthor: Bool {
        PlaceRequestRole.isAuthor(requestId: requestId, myUid: myUid)
    }

    private var request: PlaceRequest? {
        model.state.request(miffId: miffId, requestId: requestId)
    }

    private var isGone: Bool {
        if case .ready? = model.state.views[miffId] {
            return request == nil
        }
        return false
    }

    var body: some View {
        let current = request
        VStack(spacing: 0) {
            PlaceDetailHeader(
                title: current?.name ?? L10n.string("place_detail_title_loading"),
                onBack: onClose,
                onEdit: nil
            )
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.sm)
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    if let kind = model.notice, case .failed = kind {
                        RequestNoticeBar(text: L10n.string(kind.textKey))
                    }
                    if let current {
                        content(current)
                    } else {
                        LoadingIndicator()
                    }
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
            }
            if let current, !isCreator, isAuthor {
                foot(current)
            }
        }
        .background(colorBgCanvas.color.ignoresSafeArea())
        .hidesInAppSwitcher()
        // Rien n'est connu (l'écran a été recréé) : les demandes de cette Miff sont lues une fois.
        .task {
            if model.state.views[miffId] == nil {
                model.onRequestOpened(miffId: miffId, creator: isCreator)
            }
        }
        // Une demande qui n'est plus là : retour à la liste, avec la phrase neutre (sauf si c'est la personne qui vient de la reprendre).
        .onChange(of: isGone) { gone in
            if gone && !leaving {
                model.showGone()
                onClose()
            }
        }
        .onChange(of: model.notice) { notice in
            guard leaving, let notice else {
                return
            }
            if notice == .withdrawn {
                onClose()
            } else {
                leaving = false
            }
        }
    }

    @ViewBuilder
    private func content(_ current: PlaceRequest) -> some View {
        // Le lieu n'est pas encore dans la Miff : la première chose dite est ce que c'est (l'attente du demandeur), sur le ton calme de l'app.
        MiffNotice(text: L10n.string("place_request_detail_banner_member"))
        PlaceDetailBody(place: current.asPlace, state: detailState(of: current), showsAudience: false)
    }

    private func detailState(of current: PlaceRequest) -> PlaceDetailState {
        var detail = PlaceDetailState()
        detail.status = .ready
        detail.place = current.asPlace
        return detail
    }

    /// « Retirer ma demande » : blanc au trait bleu, jamais un geste rouge (reprendre sa demande n'est pas une suppression grave).
    private func foot(_ current: PlaceRequest) -> some View {
        Button {
            leaving = true
            model.onWithdraw(current)
        } label: {
            Text("place_request_withdraw")
        }
        .buttonStyle(.miffSecondaryFullWidth)
        .disabled(model.state.busyRequestId != nil)
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
        .background(colorBgCanvas.color)
    }
}
