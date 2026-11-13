import SwiftUI

/// Réglages > Langue (maquette « reglages-5 », ticket Feed / Profil / Réglages critère A9 ; miroir de `LanguageScreen` d'Android). Trois choix,
/// chacun écrit dans SA langue pour qu'on le retrouve quelle que soit la langue de l'app : « Français », « English » et « Langue du téléphone »
/// (ce dernier suit la langue de l'app). Un toucher applique le choix tout de suite : pas de bouton « Enregistrer » (décision Q4). Un cercle
/// radio (comme Android) marque le choix actif.
///
/// L'écran n'ouvre plus les réglages de l'iPhone (décision du PM du 2026-11-13, qui remplace celle d'ouvrir ces réglages).
struct LanguageView: View {
    @ObservedObject var controller: AppLanguageController
    let onBack: () -> Void

    var body: some View {
        MiffFrame(title: L10n.string("settings_language_title"), onBack: onBack, usesCanvas: true) {
            VStack(spacing: 0) {
                ForEach(LanguageRows.rows(selected: controller.choice), id: \.choice) { row in
                    LanguageRow(
                        label: LanguageLabels.label(for: row.choice),
                        isSelected: row.isSelected,
                        action: { controller.select(row.choice) }
                    )
                }
            }
        }
    }
}

/// Les libellés des trois choix. Les deux langues ne sont JAMAIS traduites (clés `ios_settings_language_french` et `_english`, même texte dans
/// les deux colonnes du catalogue) ; « Langue du téléphone » suit la langue de l'app.
enum LanguageLabels {
    static func label(for choice: AppLanguageChoice) -> String {
        switch choice {
        case .french:
            return L10n.string("ios_settings_language_french")
        case .english:
            return L10n.string("ios_settings_language_english")
        case .system:
            return L10n.string("settings_language_system")
        }
    }
}

/// L'état des trois lignes pour un choix donné : UNE seule est sélectionnée (comme le `selectableGroup` d'Android). Pur, testé.
struct LanguageRowModel: Equatable {
    let choice: AppLanguageChoice
    let isSelected: Bool
}

enum LanguageRows {
    static func rows(selected: AppLanguageChoice) -> [LanguageRowModel] {
        AppLanguageChoice.allCases.map { LanguageRowModel(choice: $0, isSelected: $0 == selected) }
    }

    /// iOS n'a pas de rôle « bouton radio » : la ligne est un bouton, dit « sélectionné » quand elle l'est (VoiceOver lit
    /// « Français, sélectionné, bouton »). Le cercle est décoratif (caché de VoiceOver) : la ligne est le contrôle.
    static func traits(isSelected: Bool) -> AccessibilityTraits {
        isSelected ? [.isButton, .isSelected] : .isButton
    }
}

/// Le cercle d'un bouton radio (miroir du `RadioButton` d'Android) : vide en gris soutenu quand le choix n'est pas actif, bleu de l'app avec
/// un point plein quand il l'est. Dessiné seulement : la ligne qui le porte est le contrôle. Il grandit un peu avec la taille du texte.
struct LanguageRadioCircle: View {
    let isSelected: Bool
    @ScaledMetric(relativeTo: .body) private var size: CGFloat = 20

    var body: some View {
        ZStack {
            Circle()
                .stroke(isSelected ? colorActionPrimary.color : colorBorderStrong.color, lineWidth: strokeAccent)
            if isSelected {
                Circle()
                    .fill(colorActionPrimary.color)
                    .padding(size * 0.25)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Une ligne de choix : le libellé (en gras sur le choix actif) et, à droite comme sur Android, le cercle radio. Toute la ligne est UN
/// bouton d'au moins 44 pt (jamais le seul cercle), lu comme un seul élément par VoiceOver.
struct LanguageRow: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                Text(verbatim: label)
                    .miffTextStyle(isSelected ? textBodyBold16 : textBodyRegular)
                    .foregroundColor(isSelected ? colorTextForm.color : colorTextDescription.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                LanguageRadioCircle(isSelected: isSelected)
            }
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: Spacing.minTouchTarget + Spacing.md)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityAddTraits(LanguageRows.traits(isSelected: isSelected))
    }
}

/// Applique la langue choisie à toute l'app : la locale de l'environnement (les `Text("cle")`, les dates et les nombres des composants du
/// système) suit le choix. Posé une fois, à la racine de l'app.
struct AppLanguageHost<Content: View>: View {
    @ObservedObject var controller: AppLanguageController
    let content: Content

    init(controller: AppLanguageController = .shared, @ViewBuilder content: () -> Content) {
        _controller = ObservedObject(wrappedValue: controller)
        self.content = content()
    }

    var body: some View {
        content.environment(\.locale, controller.state.locale)
    }
}

/// Un écran qui calcule ses textes avec `L10n` (hors de l'environnement SwiftUI) se REFAIT quand la langue effective change : son identité
/// suit le code de la langue. Posé sur les écrans de la coque (onglets) et sur chaque écran empilé, JAMAIS sur la pile elle-même : la
/// pile, l'onglet choisi et les modèles gardés par l'hôte ne bougent pas.
///
/// Changer pour le même code (« Langue du téléphone » alors que le téléphone est déjà en français) ne refait rien.
struct FollowsAppLanguage: ViewModifier {
    @ObservedObject private var controller: AppLanguageController

    init(controller: AppLanguageController = .shared) {
        _controller = ObservedObject(wrappedValue: controller)
    }

    func body(content: Content) -> some View {
        content.id(controller.state.code)
    }
}

extension View {
    func followsAppLanguage() -> some View {
        modifier(FollowsAppLanguage())
    }
}
