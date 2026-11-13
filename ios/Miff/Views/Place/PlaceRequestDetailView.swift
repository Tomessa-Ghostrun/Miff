import SwiftUI

// SÉCURITÉ, pour ce fichier : la demande (nom, adresse, coordonnées) et la personne qui l'a faite sont lues dans l'état du view model des
// demandes et dessinées ; rien n'est journalisé ni envoyé comme événement.

/// Le détail d'une demande en attente (ticket LIEU-REQ, section 4.2) : le lieu tel qu'il a été demandé (carte, cercle, adresse, rayon,
/// type), un bandeau calme, et les boutons de la personne qui le regarde. Il ne lit rien de lui-même : la demande vient du view model
/// des demandes, qui l'a lue avec la liste.
///
/// - Le DEMANDEUR (l'uid de l'identifiant de la demande est le sien) : « En attente de réponse. » et « Retirer ma demande ».
/// - Le CRÉATEUR : « Ce lieu n'est pas encore dans la Miff. Il y sera si tu acceptes. », « Demandé par {prénom} » avec la photo ou les
///   initiales, la date, et « Refuser » / « Accepter » (critères C2 à C8). L'offre s'ouvre à la limite de la formule gratuite ; la demande
///   reste en attente.
///
/// Une demande qui n'est plus là (traitée, expirée, reprise sur un autre téléphone) ramène à la liste avec une phrase neutre ; celle que la
/// personne traite ou reprend elle-même dit ce qui s'est passé (jamais les deux).
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
                    if let kind = model.notice, kind.isProblem {
                        RequestNoticeBar(text: kind.text)
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
            if let current {
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
        // Une demande qui n'est plus là : retour à la liste, avec la phrase neutre (sauf si c'est la personne qui vient de la traiter).
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
            if notice.isProblem {
                leaving = false
            } else {
                onClose()
            }
        }
        // L'offre s'ouvre par-dessus : la demande reste en attente, la personne y revient après.
        .onChange(of: model.paywall) { reason in
            if reason != nil {
                leaving = false
            }
        }
    }

    @ViewBuilder
    private func content(_ current: PlaceRequest) -> some View {
        // Le lieu n'est pas encore dans la Miff : la première chose dite est ce que c'est, sur le ton calme de l'app.
        let banner = isCreator ? "place_request_detail_banner_creator" : "place_request_detail_banner_member"
        MiffNotice(text: L10n.string(banner))
        if isCreator {
            requesterCard(current)
        }
        PlaceDetailBody(place: current.asPlace, state: detailState(of: current), showsAudience: false)
    }

    private func detailState(of current: PlaceRequest) -> PlaceDetailState {
        var detail = PlaceDetailState()
        detail.status = .ready
        detail.place = current.asPlace
        return detail
    }

    /// « Demandé par Léa » avec la photo (ou les initiales) et la date : le créateur sait qui demande avant de répondre.
    private func requesterCard(_ current: PlaceRequest) -> some View {
        HStack {
            RequesterLine(requester: model.state.requester(of: current))
            Spacer(minLength: Spacing.sm)
            if let date = dateText(current) {
                Text(verbatim: date)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
            }
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
        .accessibilityElement(children: .combine)
    }

    /// « Le 12 nov. », ou nil quand la demande n'est pas encore datée.
    private func dateText(_ current: PlaceRequest) -> String? {
        guard let millis = current.createdAtMillis else {
            return nil
        }
        let day = Date(timeIntervalSince1970: Double(millis) / 1000).formatted(.dateTime.day().month(.abbreviated))
        return L10n.format("place_request_date", day)
    }

    /// Le demandeur : « Retirer ma demande » (blanc au trait bleu, jamais un geste rouge). Le créateur : « Refuser » et « Accepter ».
    @ViewBuilder
    private func foot(_ current: PlaceRequest) -> some View {
        Group {
            if isCreator {
                AnswerButtons(
                    requestName: current.name,
                    busy: model.state.busyRequestId != nil,
                    onAccept: {
                        leaving = true
                        model.onAccept(current)
                    },
                    onDecline: {
                        leaving = true
                        model.onDecline(current)
                    }
                )
            } else if isAuthor {
                Button {
                    leaving = true
                    model.onWithdraw(current)
                } label: {
                    Text("place_request_withdraw")
                }
                .buttonStyle(.miffSecondaryFullWidth)
                .disabled(model.state.busyRequestId != nil)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
        .background(colorBgCanvas.color)
    }
}
