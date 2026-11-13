import SwiftUI

// Les trois dernières sections du formulaire d'un lieu : le périmètre (carte et curseur), le type, la catégorie.

/// « Périmètre » : la carte avec le cercle, et un curseur de 50 à 800 m par pas de 50 (100 m au départ). Sous 100 m, une note
/// discrète (jamais en rouge : la valeur reste permise). Le curseur attend le choix d'une adresse.
struct PlaceRadiusSection: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "scope",
                title: L10n.string("place_radius_title"),
                description: L10n.string("place_radius_help")
            )
            PlaceMapBlock(
                chosen: state.chosen,
                radiusM: state.radiusM,
                isDanger: state.type == .risk,
                emoji: state.category?.emoji,
                height: 176
            )
            HStack(spacing: Spacing.md) {
                slider
                valuePill
            }
            HStack {
                Text(verbatim: L10n.format("place_radius_value", PlaceLimits.radiusMin))
                Spacer()
                Text(verbatim: L10n.format("place_radius_value", PlaceLimits.radiusMax))
            }
            .miffTextStyle(textBodyCaption)
            .foregroundColor(colorTextDescription.color)
            .accessibilityHidden(true)
            if PlaceRadius.isBelowRecommended(state.radiusM) {
                PlaceInfoLine(text: L10n.string("place_radius_small_note"))
            }
        }
    }

    private var valuePill: some View {
        Text(verbatim: L10n.format("place_radius_value", state.radiusM))
            .miffTextStyle(textBodyBold16)
            .foregroundColor(colorTextPrimary.color)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .accessibilityHidden(true)
    }

    private var slider: some View {
        Slider(
            value: Binding(get: { Double(state.radiusM) }, set: { actions.onRadiusChanged($0) }),
            in: Double(PlaceLimits.radiusMin)...Double(PlaceLimits.radiusMax),
            step: Double(PlaceLimits.radiusStep)
        )
        .tint(colorActionPrimary.color)
        .disabled(state.chosen == nil || state.busy)
        .accessibilityLabel(Text(verbatim: L10n.string("place_radius_a11y")))
        .accessibilityValue(Text(verbatim: L10n.format("place_radius_state_a11y", state.radiusM)))
    }
}

/// « Type » : « Zone de confiance » (choisie au départ) ou « Zone de danger ». Pour un compte gratuit, la zone de danger porte la
/// pastille « Miff+ » AVANT d'être touchée ; la toucher ouvre l'offre et le choix reste « Zone de confiance » (critère LIM3). Les
/// deux cartes s'empilent aux grandes tailles de texte.
struct PlaceTypeSection: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "eye",
                title: L10n.string("place_type_title"),
                description: L10n.string("place_type_help")
            )
            if dynamicTypeSize.isAccessibilitySize {
                VStack(spacing: Spacing.sm) {
                    cards
                }
            } else {
                HStack(alignment: .top, spacing: Spacing.sm) {
                    cards
                }
            }
        }
    }

    @ViewBuilder
    private var cards: some View {
        PlaceTypeCard(type: .trusted, isSelected: state.type == .trusted, isLocked: false) {
            actions.onTypeSelected(.trusted)
        }
        PlaceTypeCard(type: .risk, isSelected: state.type == .risk, isLocked: !state.premium && state.type != .risk) {
            actions.onTypeSelected(.risk)
        }
    }
}

/// Une des deux cartes de type : le pictogramme, le nom, une courte phrase, une coche quand elle est choisie (jamais la couleur
/// seule).
struct PlaceTypeCard: View {
    let type: PlaceType
    let isSelected: Bool
    let isLocked: Bool
    let onSelect: () -> Void

    private var danger: Bool {
        type == .risk
    }

    private var border: ColorToken {
        if isSelected {
            return danger ? colorTextError : colorBorderAction
        }
        return danger ? colorStatusDanger : colorBorderStrong
    }

    private var fill: ColorToken {
        if !isSelected {
            return colorBgSurface
        }
        return danger ? colorStatusDangerTint : colorStatusSafeTint
    }

    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                HStack(alignment: .top, spacing: Spacing.sm) {
                    Image(systemName: type.symbol)
                        .font(.system(size: 20, weight: .regular))
                        .foregroundColor((danger ? colorTextError : colorTextForm).color)
                        .accessibilityHidden(true)
                    Text(verbatim: type.label)
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor((danger ? colorTextError : colorTextForm).color)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    if isLocked {
                        PlacePremiumPill()
                    }
                }
                Text(verbatim: type.detail)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.leading)
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(colorActionPrimary.color)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .accessibilityHidden(true)
                }
            }
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget, alignment: .topLeading)
            .padding(Spacing.sm + Spacing.xs)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(fill.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(border.color, lineWidth: isSelected ? strokeAccent : strokeHairline)
            )
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: type.label))
        .accessibilityHint(Text(verbatim: type.detail))
        .accessibilityValue(Text(verbatim: spokenValue))
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private var spokenValue: String {
        if isSelected {
            return L10n.string("place_type_selected_a11y")
        }
        return isLocked ? L10n.string("place_type_locked_a11y") : ""
    }
}

/// « Catégorie » : un choix parmi sept, « Maison » au départ ; aucun lieu n'est enregistré sans catégorie. Un lieu écrit avant que
/// les catégories existent n'en montre aucune (rien n'est choisi à la place de la personne).
struct PlaceCategorySection: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            MiffSectionHeader(
                systemImage: "square.grid.2x2",
                title: L10n.string("place_category_title"),
                description: L10n.string("place_category_help")
            )
            ForEach(PlaceCategory.allCases, id: \.self) { category in
                PlaceCategoryRow(category: category, isSelected: state.category == category) {
                    actions.onCategorySelected(category)
                }
            }
        }
    }
}

struct PlaceCategoryRow: View {
    let category: PlaceCategory
    let isSelected: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: Spacing.md) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .font(.system(size: 24))
                    .foregroundColor((isSelected ? colorActionPrimary : colorTextDescription).color)
                    .accessibilityHidden(true)
                PlaceCategoryIcon(category: category, size: 32)
                Text(verbatim: category.label)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextForm.color)
                Spacer(minLength: 0)
            }
            .frame(minHeight: Spacing.minTouchTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: category.label))
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}
