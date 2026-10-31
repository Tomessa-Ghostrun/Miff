import AuthenticationServices
import SwiftUI
import UIKit

/// Les champs d'un écran d'authentification, pour déplacer le focus de l'un à l'autre.
enum AuthField: Hashable {
    case email
    case password
}

/// Le focus d'un champ : lequel il est, et l'état de focus de l'écran qui le contient.
struct AuthFieldFocus {
    let field: AuthField
    let binding: FocusState<AuthField?>.Binding

    var isFocused: Bool {
        binding.wrappedValue == field
    }
}

/// Réglages d'un champ d'authentification.
struct AuthFieldOptions {
    var errorText: String?
    var isPassword = false
    /// Un mot de passe NOUVEAU (inscription) : le gestionnaire de mots de passe d'iCloud propose d'en créer un fort.
    var isNewPassword = false
    var submitLabel: SubmitLabel = .next
}

/// Champ à soulignement des écrans d'inscription et de connexion (maquettes 04 à 06) : le libellé est à la place du texte
/// indicatif et remonte, en plus petit, quand le champ est rempli ou actif (le lecteur d'écran garde toujours le nom du
/// champ). Le texte commence à la marge gauche de l'écran. L'erreur est un texte sous le champ, annoncé par le lecteur
/// d'écran, jamais la couleur seule.
struct AuthTextField: View {
    private let label: LocalizedStringKey
    private let text: Binding<String>
    private let focus: AuthFieldFocus
    private let options: AuthFieldOptions
    private let onSubmit: () -> Void

    @State private var passwordVisible = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(
        label: LocalizedStringKey,
        text: Binding<String>,
        focus: AuthFieldFocus,
        options: AuthFieldOptions = AuthFieldOptions(),
        onSubmit: @escaping () -> Void = {}
    ) {
        self.label = label
        self.text = text
        self.focus = focus
        self.options = options
        self.onSubmit = onSubmit
    }

    private var isFloating: Bool {
        focus.isFocused || !text.wrappedValue.isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            ZStack(alignment: .leading) {
                floatingLabel
                HStack(spacing: Spacing.sm) {
                    inputField
                    if options.isPassword && !text.wrappedValue.isEmpty {
                        eyeButton
                    }
                }
                .padding(.top, 22)
            }
            Rectangle()
                .fill(lineColor.color)
                .frame(height: options.errorText != nil || focus.isFocused ? strokeAccent : strokeHairline)
            if let errorText = options.errorText {
                MessageText(text: errorText, isError: true)
            }
        }
    }

    private var floatingLabel: some View {
        Text(label)
            .miffTextStyle(textBodyRegular)
            .foregroundColor(labelColor.color)
            .scaleEffect(isFloating ? 0.78 : 1, anchor: .leading)
            .offset(y: isFloating ? -2 : 20)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.15), value: isFloating)
            .accessibilityHidden(true)
            .allowsHitTesting(false)
    }

    @ViewBuilder
    private var inputField: some View {
        if options.isPassword && !passwordVisible {
            SecureField(label, text: text, prompt: Text(verbatim: ""))
                .textContentType(contentType)
                .modifier(commonModifiers)
        } else {
            TextField(label, text: text, prompt: Text(verbatim: ""))
                .textContentType(contentType)
                .keyboardType(options.isPassword ? .default : .emailAddress)
                .modifier(commonModifiers)
        }
    }

    private var contentType: UITextContentType {
        if !options.isPassword {
            return .username
        }
        return options.isNewPassword ? .newPassword : .password
    }

    private var commonModifiers: FieldModifiers {
        FieldModifiers(
            focus: focus,
            submitLabel: options.submitLabel,
            onSubmit: onSubmit,
            hasError: options.errorText != nil
        )
    }

    private var eyeButton: some View {
        let key: LocalizedStringKey = passwordVisible ? "auth_password_hide_a11y" : "auth_password_show_a11y"
        return Button {
            passwordVisible.toggle()
            // Changer de type de champ lui fait perdre le focus : on le lui rend tout de suite.
            focus.binding.wrappedValue = focus.field
        } label: {
            Image(systemName: passwordVisible ? "eye.slash" : "eye")
                .foregroundColor(colorTextForm.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
        }
        .accessibilityLabel(key)
    }

    private var labelColor: ColorToken {
        if options.errorText != nil {
            return colorTextError
        }
        return focus.isFocused ? colorActionPrimary : colorTextDescription
    }

    private var lineColor: ColorToken {
        if options.errorText != nil {
            return colorTextError
        }
        return focus.isFocused ? colorBorderAction : colorBorderStrong
    }
}

/// Réglages communs d'un champ d'authentification : police du design system, pas de majuscule ni de correction
/// automatique (adresse e-mail et mot de passe), touche d'action, focus.
private struct FieldModifiers: ViewModifier {
    let focus: AuthFieldFocus
    let submitLabel: SubmitLabel
    let onSubmit: () -> Void
    let hasError: Bool

    func body(content: Content) -> some View {
        content
            .miffTextStyle(textBodyRegular)
            .foregroundColor(colorTextForm.color)
            .tint(hasError ? colorTextError.color : colorActionPrimary.color)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
            .submitLabel(submitLabel)
            .focused(focus.binding, equals: focus.field)
            .onSubmit(onSubmit)
    }
}

/// La ligne explicative au-dessus du bouton principal (aucune case à cocher) : les conditions d'utilisation et la politique
/// de confidentialité sont des liens dans la phrase (gras, bleus). Pour le lecteur d'écran, les deux liens sont aussi
/// proposés comme actions de la ligne, pour les atteindre sans avoir à viser quelques mots du doigt. `sentenceKey` désigne
/// une phrase à trois valeurs : le nom des conditions, celui de la politique, puis l'adresse où écrire pour retirer le
/// consentement aux statistiques (iOS n'a pas encore d'écran Réglages : voir `ios/README.md`).
struct ConsentNotice: View {
    let sentenceKey: String

    @Environment(\.openURL) private var openURL

    init(sentenceKey: String) {
        self.sentenceKey = sentenceKey
    }

    var body: some View {
        let terms = L10n.string("auth_consent_terms_link")
        let privacy = L10n.string("auth_consent_privacy_link")
        let sentence = L10n.format(sentenceKey, terms, privacy, AppLinks.supportEmail)
        return Text(attributed(sentence, terms: terms, privacy: privacy))
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorTextPrimary.color)
            .tint(colorActionPrimary.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAction(named: Text(verbatim: terms)) {
                open(AppLinks.termsURL())
            }
            .accessibilityAction(named: Text(verbatim: privacy)) {
                open(AppLinks.privacyURL())
            }
    }

    private func attributed(_ sentence: String, terms: String, privacy: String) -> AttributedString {
        var text = AttributedString(sentence)
        style(&text, label: terms, url: AppLinks.termsURL())
        style(&text, label: privacy, url: AppLinks.privacyURL())
        return text
    }

    private func style(_ text: inout AttributedString, label: String, url: URL?) {
        guard let url, let range = text.range(of: label) else {
            return
        }
        text[range].link = url
        text[range].inlinePresentationIntent = .stronglyEmphasized
    }

    private func open(_ url: URL?) {
        if let url {
            openURL(url)
        }
    }
}

/// Le bouton « Continuer avec Apple » officiel du système (libellé, police et forme d'Apple ; le libellé suit la langue du
/// téléphone). Fond noir, comme la maquette.
struct AppleSignInButton: UIViewRepresentable {
    let action: () -> Void

    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let button = ASAuthorizationAppleIDButton(type: .continue, style: .black)
        button.cornerRadius = radiusMd
        button.addTarget(context.coordinator, action: #selector(Coordinator.tapped), for: .touchUpInside)
        return button
    }

    func updateUIView(_ uiView: ASAuthorizationAppleIDButton, context: Context) {
        context.coordinator.action = action
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(action: action)
    }

    final class Coordinator: NSObject {
        var action: () -> Void

        init(action: @escaping () -> Void) {
            self.action = action
        }

        @objc
        func tapped() {
            action()
        }
    }
}
