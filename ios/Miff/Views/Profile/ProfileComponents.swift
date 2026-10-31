import SwiftUI
import UIKit

/// Le focus d'un champ à libellé au-dessus : lequel il est, et l'état de focus de l'écran qui le contient.
struct LabeledFieldFocus<Field: Hashable> {
    let field: Field
    let binding: FocusState<Field?>.Binding

    var isFocused: Bool {
        binding.wrappedValue == field
    }
}

/// Réglages d'un champ à libellé au-dessus.
struct LabeledFieldOptions {
    var errorText: String?
    var placeholder: String?
    /// Le « +33 » visuel avant les chiffres (le lecteur d'écran obtient le pays par le sélecteur).
    var prefix: String?
    /// Le petit « 45/50 » montré quand la limite approche, et sa version parlée.
    var counter: String?
    var counterSpoken: String?
    var isEnabled = true
    var submitLabel: SubmitLabel = .next
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?
    var capitalization: TextInputAutocapitalization = .never
}

/// Champ des maquettes « Welcoming screen -1, -2, -6 » : le libellé est AU-DESSUS d'une boîte blanche à bord fin arrondi
/// (bord bleu et plus épais quand le champ est actif, rouge avec un message dessous en cas d'erreur). Libellé et champ ne
/// forment qu'UN élément pour le lecteur d'écran (« Prénom, champ de texte »), l'erreur est annoncée poliment.
struct LabeledTextField<Field: Hashable>: View {
    private let label: LocalizedStringKey
    private let text: Binding<String>
    private let focus: LabeledFieldFocus<Field>
    private let options: LabeledFieldOptions
    private let onSubmit: () -> Void

    init(
        label: LocalizedStringKey,
        text: Binding<String>,
        focus: LabeledFieldFocus<Field>,
        options: LabeledFieldOptions = LabeledFieldOptions(),
        onSubmit: @escaping () -> Void = {}
    ) {
        self.label = label
        self.text = text
        self.focus = focus
        self.options = options
        self.onSubmit = onSubmit
    }

    private var hasError: Bool {
        options.errorText != nil
    }

    private var borderColor: ColorToken {
        if hasError {
            return colorTextError
        }
        return focus.isFocused ? colorBorderAction : colorBorderStrong
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .accessibilityHidden(true)
                .onTapGesture {
                    focus.binding.wrappedValue = focus.field
                }
            box
            if let errorText = options.errorText {
                MessageText(text: errorText, isError: true)
            }
            if let counter = options.counter {
                Text(verbatim: counter)
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextDescription.color)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .accessibilityLabel(Text(verbatim: options.counterSpoken ?? counter))
            }
        }
    }

    private var box: some View {
        HStack(spacing: Spacing.sm) {
            if let prefix = options.prefix {
                Text(verbatim: prefix)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextForm.color)
                    .accessibilityHidden(true)
            }
            TextField(label, text: text, prompt: Text(verbatim: options.placeholder ?? ""))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
                .tint(hasError ? colorTextError.color : colorActionPrimary.color)
                .keyboardType(options.keyboard)
                .textContentType(options.contentType)
                .textInputAutocapitalization(options.capitalization)
                .autocorrectionDisabled(true)
                .submitLabel(options.submitLabel)
                .focused(focus.binding, equals: focus.field)
                .onSubmit(onSubmit)
                .disabled(!options.isEnabled)
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .frame(minHeight: Spacing.minTouchTarget)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(borderColor.color, lineWidth: hasError || focus.isFocused ? strokeAccent : strokeHairline)
        )
    }
}

/// La flèche ronde des maquettes, en bas à la fin : bleue quand elle peut servir, le même bleu à 30 % sinon (comme le
/// bouton principal). Pendant l'écriture elle montre un indicateur, annoncé par le lecteur d'écran. 56 pt, cible tactile
/// au-delà des 44 pt d'Apple.
struct RoundNextButton: View {
    let enabled: Bool
    let isLoading: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill((enabled || isLoading ? colorActionPrimary : colorActionPrimaryTint30).color)
                if isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(colorTextOnAction.color)
                } else {
                    Image(systemName: "arrow.forward")
                        .font(.system(size: 22, weight: .regular))
                        .foregroundColor(colorTextOnAction.color)
                }
            }
            .frame(width: Self.diameter, height: Self.diameter)
            .modifier(RoundShadow(isActive: enabled && !isLoading))
        }
        .buttonStyle(.plain)
        .disabled(!enabled || isLoading)
        .accessibilityLabel(spokenLabel)
    }

    private static let diameter: CGFloat = 56

    private var spokenLabel: LocalizedStringKey {
        isLoading ? "auth_loading_a11y" : "profile_next_a11y"
    }
}

/// L'ombre du design system, seulement quand le bouton est actif.
private struct RoundShadow: ViewModifier {
    let isActive: Bool

    func body(content: Content) -> some View {
        if isActive {
            content.miffShadow(shadowDefault)
        } else {
            content
        }
    }
}

/// Cadre des étapes du profil (maquettes -1, -2 et -6) : dégradé d'authentification, mot-symbole (avec la flèche de retour
/// à l'étape téléphone seulement), un titre, le contenu, et la flèche RONDE en bas à la fin. Le contenu défile (grande
/// taille de texte, paysage, clavier) ; la flèche reste à portée au-dessus du clavier.
struct ProfileScaffold<Content: View>: View {
    let title: LocalizedStringKey
    let onBack: (() -> Void)?
    let nextButton: RoundNextButton
    let content: Content

    init(
        title: LocalizedStringKey,
        onBack: (() -> Void)?,
        nextButton: RoundNextButton,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.onBack = onBack
        self.nextButton = nextButton
        self.content = content()
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    AuthHeader(onBack: onBack)
                    Spacer().frame(height: Spacing.lg)
                    Text(title)
                        .miffTextStyle(textTitleBold20)
                        .foregroundColor(colorTextForm.color)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, Spacing.lg)
                        .accessibilityAddTraits(.isHeader)
                    content
                    // La flèche ronde ne cache jamais le dernier champ ni le dernier message.
                    Spacer().frame(height: 96)
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
            }
            .scrollDismissesKeyboard(.interactively)
            nextButton
                .padding(.trailing, Spacing.lg)
                .padding(.bottom, Spacing.md)
        }
        .background(gradientAuthBackground.ignoresSafeArea())
    }
}

/// L'appareil photo des maquettes : corps vert de la marque, objectif en anneau blanc. Dessiné pour Miff, décoratif.
struct CameraIcon: View {
    var body: some View {
        ZStack {
            CameraBodyShape()
                .fill(colorBrandPrimary.color)
            Circle()
                .stroke(colorBgSurface.color, lineWidth: 5)
                .frame(width: 18, height: 18)
                .offset(y: 5)
        }
        .frame(width: 64, height: 64)
        .accessibilityHidden(true)
    }
}

/// Le boîtier : un rectangle arrondi et sa petite bosse du dessus, dans un carré de 64 unités.
private struct CameraBodyShape: Shape {
    func path(in rect: CGRect) -> Path {
        let unit = min(rect.width, rect.height) / 64
        var path = Path()
        path.addRoundedRect(
            in: CGRect(x: rect.minX + 6 * unit, y: rect.minY + 20 * unit, width: 52 * unit, height: 34 * unit),
            cornerSize: CGSize(width: 4 * unit, height: 4 * unit)
        )
        path.move(to: CGPoint(x: rect.minX + 18 * unit, y: rect.minY + 21 * unit))
        path.addLine(to: CGPoint(x: rect.minX + 21 * unit, y: rect.minY + 15 * unit))
        path.addLine(to: CGPoint(x: rect.minX + 43 * unit, y: rect.minY + 15 * unit))
        path.addLine(to: CGPoint(x: rect.minX + 46 * unit, y: rect.minY + 21 * unit))
        path.closeSubpath()
        return path
    }
}
