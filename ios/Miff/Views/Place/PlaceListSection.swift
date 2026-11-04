import SwiftUI

/// Les gestes de la liste des lieux d'une Miff.
struct PlaceListActions {
    let onOpen: (Place) -> Void
    /// « Ajouter un lieu » : `atFreeLimit` dit que la formule gratuite est pleine, donc l'hôte ouvre l'offre plutôt que le formulaire.
    let onAdd: (_ atFreeLimit: Bool) -> Void

    static let none = PlaceListActions(onOpen: { _ in }, onAdd: { _ in })
}

/// Ce que la section des lieux reçoit de l'extérieur : Miff+ ou non (nil tant que ce n'est pas connu : rien n'est alors affirmé sur
/// la formule gratuite), le message calme d'un geste, les gestes, et ce qui se passe quand la personne quitte l'écran.
struct PlaceListContext {
    var premium: Bool?
    var notice: PlaceNotice?
    var actions = PlaceListActions.none
    var onLeave: () -> Void = {}
}

/// Les lignes de « Lieux de la Miff » (maquette 17) : le pictogramme de la catégorie, le nom, l'adresse et une flèche ; une zone de
/// danger porte son étiquette en mots ; le créateur a UNE entrée « Ajouter un lieu » (en pointillés, avec la pastille « Miff+ » à la
/// limite, jamais cachée ni grisée) ; un membre lit seulement, et une phrase dit qui définit les lieux.
struct PlaceRows: View {
    let ui: PlaceListUi
    let actions: PlaceListActions

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            if let notice = ui.notice {
                PlaceNoticeBar(notice: notice)
            }
            if let caption {
                Text(verbatim: caption)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
            }
            if ui.places.isEmpty {
                Text("miff_places_empty")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.sm)
            } else {
                rows
            }
            if ui.isCreator {
                PlaceAddRow(showsPremiumPill: ui.atFreeLimit) {
                    actions.onAdd(ui.atFreeLimit)
                }
            }
        }
    }

    /// Un membre : qui définit les lieux. Un créateur en formule gratuite : « 2 lieux sur 2 avec la formule gratuite ». Un créateur
    /// Miff+, ou dont la formule n'est pas encore connue : rien (rien n'est affirmé sur ce qu'on ne sait pas).
    private var caption: String? {
        if !ui.isCreator {
            return L10n.string("place_list_member_hint")
        }
        if ui.showsFreeCounter {
            return L10n.plural("place_list_counter_free", count: ui.distinctCount)
        }
        return nil
    }

    private var rows: some View {
        VStack(spacing: 0) {
            ForEach(ui.places, id: \.id) { place in
                PlaceRow(place: place) {
                    actions.onOpen(place)
                }
            }
        }
        .background(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorBgSurface.color)
                .miffShadow(shadowDefault)
        )
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
    }
}

/// Une ligne de lieu. Lue ainsi : « Maison de Papa, catégorie Maison, 26 rue du Volnay ».
struct PlaceRow: View {
    let place: Place
    let onOpen: () -> Void

    private var spoken: String {
        let address = place.address ?? ""
        switch (place.category, address.isEmpty) {
        case (.some(let category), false):
            return L10n.format("place_list_row_a11y", place.name, category.label, address)
        case (.some(let category), true):
            return L10n.format("place_list_row_noaddress_a11y", place.name, category.label)
        case (.none, false):
            return L10n.format("place_list_row_nocategory_a11y", place.name, address)
        case (.none, true):
            return L10n.format("place_list_row_nocategory_noaddress_a11y", place.name)
        }
    }

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: Spacing.md) {
                PlaceCategoryIcon(category: place.category, size: 44)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(verbatim: place.name)
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextForm.color)
                        .multilineTextAlignment(.leading)
                    if place.type == .risk {
                        PlaceTypeTag(type: .risk)
                    }
                    if let address = place.address, !address.isEmpty {
                        Text(verbatim: address)
                            .miffTextStyle(textBodyRegular14)
                            .foregroundColor(colorTextDescription.color)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
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

/// « Ajouter un lieu » : une ligne en pointillés bleus ; à la limite de la formule gratuite elle porte la pastille « Miff+ » et
/// ouvre l'offre (jamais masquée ni grisée).
struct PlaceAddRow: View {
    let showsPremiumPill: Bool
    let onAdd: () -> Void

    var body: some View {
        Button(action: onAdd) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .accessibilityHidden(true)
                Text("place_list_add_action")
                    .miffTextStyle(textBodyBold16)
                if showsPremiumPill {
                    PlacePremiumPill()
                }
            }
            .foregroundColor(colorActionPrimary.color)
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget + Spacing.md)
            .padding(.horizontal, Spacing.md)
            .placeDashedBorder()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            Text(verbatim: L10n.string(showsPremiumPill ? "place_list_add_premium_a11y" : "place_list_add_action"))
        )
        .accessibilityAddTraits(.isButton)
    }
}

/// Le message calme qui suit un geste (maquette 04L) : « Le lieu « X » est enregistré. Tes proches le prendront en compte dès qu'ils
/// ouvriront Miff, ou dans les prochaines heures. » ; ou « Ce lieu n'existe plus. ».
struct PlaceNoticeBar: View {
    let notice: PlaceNotice

    private var text: String {
        switch notice {
        case .saved(let name):
            return L10n.format("place_saved_notice", name)
        case .gone:
            return L10n.string("place_gone")
        }
    }

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
