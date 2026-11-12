import SwiftUI

// SÉCURITÉ, pour ce fichier : le nom et l'adresse d'une demande sont dessinés ici et nulle part ailleurs ; ils ne sont jamais journalisés
// et ne sont lus à voix haute que dans la description de la carte pour VoiceOver.

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

/// Une demande en attente dans la liste des lieux (ticket LIEU-REQ, critères M4 et C2). Ce n'est PAS un lieu : un fond atténué et un
/// bord en POINTILLÉS la distinguent des lignes des lieux qui sont dans la Miff (pas de carte pleine), et une pastille dit « En attente »
/// en mots. JAMAIS de rouge ni d'orange (ton calme, T1).
///
/// Le DEMANDEUR lit UN seul bloc : « Ta demande en attente : {lieu} », puis l'adresse, puis « Zone de danger » quand c'en est une.
struct PlaceRequestCard: View {
    let request: PlaceRequest
    let onOpen: () -> Void

    private var spoken: String {
        let named = L10n.format("place_request_a11y_pending_mine", request.name)
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
                    PendingPill(text: L10n.string("place_request_badge_pending"))
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
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint5.color))
        .placeDashedBorder()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spoken))
        .accessibilityAddTraits(.isButton)
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
