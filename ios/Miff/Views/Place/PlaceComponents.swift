import SwiftUI

// Les briques des écrans des lieux (formulaire, liste, détail, offre), construites avec les tokens du design system. Aucune
// couleur ni aucun style de texte qui ne vienne pas d'eux. Les pictogrammes sont des SYMBOLES SYSTÈME provisoires (l'export Figma
// du PM les remplacera : fiche `icones-categories.md`).

extension PlaceCategory {
    var symbol: String {
        switch self {
        case .home:
            return "house"
        case .work:
            return "briefcase"
        case .school:
            return "graduationcap"
        case .sport:
            return "soccerball"
        case .relative:
            return "person.2"
        case .leisure:
            return "gamecontroller"
        case .other:
            return "ellipsis.circle"
        }
    }
}

extension PlaceType {
    var symbol: String {
        self == .trusted ? "checkmark.shield" : "exclamationmark.triangle"
    }
}

/// Un carré bleu pâle avec le pictogramme d'une catégorie : 28 pt dans le formulaire, 44 pt dans les listes. Un lieu écrit avant
/// que les catégories existent n'en a pas : un simple repère, jamais « Autre » par défaut. Le NOM de la catégorie accompagne
/// toujours le pictogramme (jamais le pictogramme seul) : celui-ci est caché au lecteur d'écran.
struct PlaceCategoryIcon: View {
    let category: PlaceCategory?
    let size: CGFloat

    var body: some View {
        Image(systemName: category?.symbol ?? "mappin.and.ellipse")
            .font(.system(size: size * 0.5, weight: .regular))
            .foregroundColor(colorActionPrimary.color)
            .frame(width: size, height: size)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint20.color)
            )
            .accessibilityHidden(true)
    }
}

/// La pastille « Miff+ » avec un cadenas : ce qui est Miff+ se voit AVANT d'être touché (décision de design 11).
struct PlacePremiumPill: View {
    var body: some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: "lock")
                .font(.system(size: 11, weight: .semibold))
                .accessibilityHidden(true)
            Text("place_premium_pill")
                .miffTextStyle(textCaptionBold12)
        }
        .foregroundColor(colorActionPrimary.color)
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .background(Capsule().fill(colorActionPrimaryTint10.color))
    }
}

/// « Zone de confiance » / « Zone de danger » : la couleur ET le pictogramme ET les mots, jamais la couleur seule.
struct PlaceTypeTag: View {
    let type: PlaceType

    var body: some View {
        let danger = type == .risk
        HStack(spacing: Spacing.xs) {
            Image(systemName: type.symbol)
                .font(.system(size: 13, weight: .regular))
                .accessibilityHidden(true)
            Text(verbatim: type.label)
                .miffTextStyle(textCaptionBold12)
        }
        .foregroundColor((danger ? colorTextError : colorTextForm).color)
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .background(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill((danger ? colorStatusDangerTint : colorStatusSafeTint).color)
        )
    }
}

/// Une phrase discrète avec un « i » (une note, jamais une erreur rouge) : « En dessous de 100 m… », « Choisis ton adresse dans
/// la liste… ».
struct PlaceInfoLine: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "info.circle")
                .font(.system(size: 15, weight: .regular))
                .foregroundColor(colorTextDescription.color)
                .accessibilityHidden(true)
            Text(verbatim: text)
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}

/// Un cadre à bord pointillé (la ligne « Ajouter un lieu » des maquettes).
struct PlaceDashedBorder: ViewModifier {
    func body(content: Content) -> some View {
        content.overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .strokeBorder(colorBorderAction.color, style: StrokeStyle(lineWidth: strokeHairline, dash: [5, 4]))
        )
    }
}

extension View {
    func placeDashedBorder() -> some View {
        modifier(PlaceDashedBorder())
    }
}

/// Le champ de saisie des lieux (nom, adresse) : une boîte blanche à bord fin arrondi, un pictogramme au début, une croix pour
/// recommencer. Le libellé que lit le lecteur d'écran est celui du champ (le titre de la section est au-dessus).
struct PlaceTextField: View {
    @Binding var text: String
    let placeholder: String
    let spokenLabel: String
    var leadingSymbol: String?
    var leadingIsActive = false
    var isEnabled = true
    var isBusy = false
    var capitalization: TextInputAutocapitalization = .sentences
    var submitLabel: SubmitLabel = .done
    var onClear: (() -> Void)?

    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: Spacing.sm) {
            if let leadingSymbol {
                Image(systemName: leadingSymbol)
                    .foregroundColor((leadingIsActive ? colorActionPrimary : colorTextDescription).color)
                    .accessibilityHidden(true)
            }
            TextField(spokenLabel, text: $text, prompt: Text(verbatim: placeholder))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
                .tint(colorActionPrimary.color)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled(true)
                .submitLabel(submitLabel)
                .focused($focused)
                .disabled(!isEnabled)
            trailing
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .frame(minHeight: Spacing.minTouchTarget)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(
                    (focused ? colorBorderAction : colorBorderStrong).color,
                    lineWidth: focused ? strokeAccent : strokeHairline
                )
        )
    }

    @ViewBuilder
    private var trailing: some View {
        if isBusy {
            ProgressView()
                .progressViewStyle(.circular)
                .tint(colorActionPrimary.color)
                .accessibilityLabel(Text(verbatim: L10n.string("place_address_loading_a11y")))
        } else if let onClear, !text.isEmpty {
            Button(action: onClear) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(colorTextForm.color)
                    .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
            }
            .accessibilityLabel(Text(verbatim: L10n.string("place_address_clear_a11y")))
        }
    }
}
