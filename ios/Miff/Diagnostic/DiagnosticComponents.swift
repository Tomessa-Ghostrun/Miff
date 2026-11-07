#if DEBUG || INTERNAL
import SwiftUI

// Petits éléments de l'écran de diagnostic (uniquement dans les builds Debug et Internal).

/// Une ligne de texte du diagnostic : normale, en gras, ou « avertissement » (couleur d'erreur accessible).
struct DiagLine: View {
    enum Emphasis {
        case body
        /// Texte d’explication, un peu plus discret (colorTextDescription).
        case description
        case strong
        case warning
    }

    private let text: Text
    private let emphasis: Emphasis

    /// Texte statique : une clé du catalogue de chaînes.
    init(_ key: LocalizedStringKey, _ emphasis: Emphasis = .body) {
        text = Text(key)
        self.emphasis = emphasis
    }

    /// Texte déjà composé (valeurs insérées) : affiché tel quel.
    init(verbatim: String, _ emphasis: Emphasis = .body) {
        text = Text(verbatim: verbatim)
        self.emphasis = emphasis
    }

    private var color: ColorToken {
        switch emphasis {
        case .warning: return colorTextError
        case .description: return colorTextDescription
        case .body, .strong: return colorTextPrimary
        }
    }

    var body: some View {
        text
            .miffTextStyle(emphasis == .strong || emphasis == .warning ? textBodyBold14 : textBodyRegular14)
            .foregroundColor(color.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// Carte d'une section du diagnostic (fond « surface », bordure fine, arrondi radius/md).
struct SectionCard<Content: View>: View {
    private let titleKey: LocalizedStringKey
    private let section: DiagnosticSection
    private let content: Content

    init(_ titleKey: LocalizedStringKey, section: DiagnosticSection, @ViewBuilder content: () -> Content) {
        self.titleKey = titleKey
        self.section = section
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(titleKey)
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextPrimary.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            content
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
        .id(section.rawValue)
    }
}

/// Résultat d'un test : annoncé par VoiceOver quand il change (il apparaît après un appui).
struct LiveResult<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Une ligne avec interrupteur : une seule cible tactile pour toute la ligne, d'au moins 44 pt de haut.
struct SwitchRow: View {
    private let labelKey: LocalizedStringKey
    private let isOn: Binding<Bool>

    init(_ labelKey: LocalizedStringKey, isOn: Binding<Bool>) {
        self.labelKey = labelKey
        self.isOn = isOn
    }

    var body: some View {
        Toggle(isOn: isOn) {
            Text(labelKey)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextPrimary.color)
        }
        .tint(colorActionPrimary.color)
        .frame(minHeight: Spacing.minTouchTarget)
    }
}

/// Style des champs de saisie du diagnostic (e-mail, mot de passe) : fond « surface », bordure forte, arrondi md.
struct DiagFieldStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .miffTextStyle(textBodyRegular)
            .foregroundColor(colorTextForm.color)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderStrong.color, lineWidth: strokeHairline)
            )
    }
}

extension View {
    func diagFieldStyle() -> some View {
        modifier(DiagFieldStyle())
    }
}
#endif
