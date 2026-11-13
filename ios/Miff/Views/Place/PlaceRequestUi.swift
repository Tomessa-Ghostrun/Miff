import SwiftUI

// SÉCURITÉ, pour ce fichier : le nom et l'adresse d'une demande, et le prénom et la photo de la personne qui l'a faite, sont dessinés ici et
// nulle part ailleurs ; ils ne sont jamais journalisés et ne sont lus à voix haute que dans la description de la carte pour VoiceOver.

/// La pastille « En attente » : des mots sur un bleu pâle, de la même famille que les autres pastilles des lieux (jamais une couleur
/// d'alerte).
struct PendingPill: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textCaptionBold12)
            .foregroundColor(colorActionPrimary.color)
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, Spacing.xs)
            .background(Capsule().fill(colorActionPrimaryTint10.color))
    }
}

/// Ce dont la carte d'une demande a besoin quand la personne qui la regarde est le CRÉATEUR de la Miff (ticket LIEU-REQ-C, critères C2 à C4).
struct RequestCreatorUi {
    /// Qui a demandé : nil (pas encore lu) et inconnu se dessinent tous deux sans prénom ; jamais cachée sur un doute.
    let requester: RequesterState?
    /// Une réponse est en cours (les boutons attendent, un second toucher ne fait rien).
    let busy: Bool
    let onAccept: () -> Void
    let onDecline: () -> Void
}

/// Une demande en attente dans la liste des lieux (ticket LIEU-REQ, critères M4 et C2). Ce n'est PAS un lieu : un fond atténué et un bord en
/// POINTILLÉS la distinguent des lignes des lieux qui sont dans la Miff (pas de carte pleine), et une pastille dit « En attente » en mots.
/// JAMAIS de rouge ni d'orange (ton calme, T1).
///
/// Le DEMANDEUR lit UN seul bloc : « Ta demande en attente : {lieu} », puis l'adresse, puis « Zone de danger » quand c'en est une. Le CRÉATEUR
/// (`creator` donné) voit qui a demandé, avec la photo ou les initiales, et les deux réponses « Refuser » et « Accepter » (de même poids :
/// refuser est aussi calme qu'accepter) ; la carte se lit « Demande en attente : {lieu}, demandée par {Prénom} ».
struct PlaceRequestCard: View {
    let request: PlaceRequest
    var creator: RequestCreatorUi?
    let onOpen: () -> Void

    private var firstName: String {
        if case .known(let card)? = creator?.requester {
            return card.firstName
        }
        return ""
    }

    private var named: String {
        guard creator != nil else {
            return L10n.format("place_request_a11y_pending_mine", request.name)
        }
        if firstName.isEmpty {
            return L10n.format("place_request_a11y_pending_card_unknown", request.name)
        }
        return L10n.format("place_request_a11y_pending_card", request.name, firstName)
    }

    private var spoken: String {
        let withAddress: String
        if let address = request.address, !address.isEmpty {
            withAddress = L10n.format("place_list_row_type_a11y", named, address)
        } else {
            withAddress = named
        }
        guard request.type == .risk else {
            return withAddress
        }
        return L10n.format("place_list_row_type_a11y", withAddress, request.type.label)
    }

    var body: some View {
        VStack(spacing: Spacing.xs) {
            openButton
            if let creator {
                AnswerButtons(
                    requestName: request.name,
                    busy: creator.busy,
                    onAccept: creator.onAccept,
                    onDecline: creator.onDecline
                )
                .padding(.horizontal, Spacing.md)
                .padding(.bottom, Spacing.md)
            }
        }
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint5.color))
        .placeDashedBorder()
    }

    private var openButton: some View {
        Button(action: onOpen) {
            HStack(spacing: Spacing.md) {
                // La catégorie choisie (l'épingle quand un document n'en a pas) ; le bord en pointillés, pas une tuile pleine, dit que
                // ce n'est pas encore un lieu de la Miff.
                PlaceCategoryIcon(category: request.category, size: 44)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(verbatim: request.name)
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextForm.color)
                        .multilineTextAlignment(.leading)
                    if request.type == .risk {
                        PlaceTypeTag(type: .risk)
                    }
                    if let address = request.address, !address.isEmpty {
                        Text(verbatim: address)
                            .miffTextStyle(textBodyRegular14)
                            .foregroundColor(colorTextDescription.color)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                    if let creator {
                        RequesterLine(requester: creator.requester)
                    } else {
                        PendingPill(text: L10n.string("place_request_badge_pending"))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .foregroundColor(colorActionPrimary.color)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: Spacing.minTouchTarget + Spacing.lg)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spoken))
        .accessibilityAddTraits(.isButton)
    }
}

/// « Demandé par Léa » avec la photo (ou les initiales) de la personne : le créateur voit QUI demande, dans la liste et dans le détail
/// (critère C2). Une fiche pas encore lue se dessine avec « ? » et « Demandé par un membre ».
struct RequesterLine: View {
    let requester: RequesterState?

    private var card: RequesterCard? {
        if case .known(let found)? = requester {
            return found
        }
        return nil
    }

    var body: some View {
        HStack(spacing: Spacing.xs) {
            MiffAvatar(jpeg: card?.avatarJpeg, initials: card?.initials ?? "?", size: 28)
                .accessibilityHidden(true)
            Text(verbatim: text)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var text: String {
        if let card {
            return L10n.format("place_request_by", card.firstName)
        }
        return L10n.string("place_request_by_unknown")
    }
}

/// « Refuser » et « Accepter » : de même taille, côte à côte (l'un sous l'autre aux grandes tailles de texte) ; « Refuser » n'est JAMAIS
/// rouge (la personne qui a demandé n'en est pas avertie). Chaque bouton nomme le lieu pour VoiceOver.
struct AnswerButtons: View {
    let requestName: String
    let busy: Bool
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Spacing.sm) {
                decline
                accept
            }
            VStack(spacing: Spacing.sm) {
                accept
                decline
            }
        }
    }

    private var decline: some View {
        Button(action: onDecline) {
            Text("place_request_decline")
        }
        .buttonStyle(.miffSecondaryFullWidth)
        .disabled(busy)
        .accessibilityLabel(Text(verbatim: L10n.format("place_request_decline_a11y", requestName)))
    }

    private var accept: some View {
        Button(action: onAccept) {
            Text("place_request_accept")
        }
        .buttonStyle(.miffPrimaryFullWidth)
        .disabled(busy)
        .accessibilityLabel(Text(verbatim: L10n.format("place_request_accept_a11y", requestName)))
    }
}

/// Le message calme qui suit un geste sur une demande (« Demande retirée. », « Cette demande n'est plus en attente. »…), de la famille de
/// celui d'un lieu enregistré : une carte bleu pâle avec une coche, annoncée poliment à VoiceOver.
struct RequestNoticeBar: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "checkmark.circle")
                .font(.system(size: 20, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            Text(verbatim: text)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint5.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeHairline)
        )
        .accessibilityElement(children: .combine)
        .onAppear {
            MiffNoticeAnnouncements.announceOnce(text)
        }
    }
}
