import SwiftUI

/// Les gestes d'un écran d'inscription ou de connexion.
struct AuthActions {
    var submitEmail: (_ email: String, _ password: String) -> Void
    var apple: () -> Void
    var google: () -> Void
    /// « Déjà un compte ? » sur l'inscription, « Pas encore de compte ? » sur la connexion.
    var switchScreen: () -> Void
    /// « Mot de passe oublié ? » (écran de connexion seulement), avec l'adresse déjà saisie.
    var forgotPassword: (_ email: String) -> Void = { _ in }
}

/// Le message sous les boutons ; « adresse déjà utilisée » propose aussi le chemin vers la connexion (critère S6).
struct AuthMessageArea: View {
    let message: AuthMessage?
    var onGoToSignIn: (() -> Void)?

    var body: some View {
        if let message {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                MessageText(text: message.text, isError: true)
                if message == .emailInUse, let onGoToSignIn {
                    Button(action: onGoToSignIn) {
                        Text("auth_signup_have_account_action")
                            .miffTextStyle(textBodyBold16)
                            .foregroundColor(colorActionPrimary.color)
                            .frame(minHeight: Spacing.minTouchTarget, alignment: .leading)
                    }
                }
            }
            .padding(.bottom, Spacing.sm)
        }
    }
}

/// Les boutons Apple et Google, l'un au-dessus de l'autre (maquette -7), grisés pendant une attente.
struct SocialButtons: View {
    let isLoading: Bool
    let actions: AuthActions

    var body: some View {
        VStack(spacing: Spacing.sm) {
            AppleSignInButton(action: actions.apple)
                .frame(height: 50)
            GoogleButton(action: actions.google)
        }
        .allowsHitTesting(!isLoading)
        .opacity(isLoading ? 0.5 : 1)
    }
}

/// Écran d'inscription (maquette « Welcoming screen -7 », ticket §3 A). Sans état propre : tout vient de
/// `SignUpViewModel` (voir `SignUpView`) ou, pour les captures de la CI, d'un état fixe.
///
/// Aucune case à cocher : la ligne juste au-dessus du bouton principal dit que « Rejoindre » (ou « Continuer avec Apple »
/// ou « Continuer avec Google ») accepte les conditions, la politique de confidentialité et le partage de statistiques
/// anonymes (décision du PM du 2026-10-28). Seul l'e-mail se garde si l'écran est recréé ; le mot de passe n'est jamais
/// sauvegardé nulle part.
struct SignUpContent: View {
    let state: SignUpUiState
    let onBack: (() -> Void)?
    let actions: AuthActions

    @State private var email = ""
    @State private var password = ""
    @State private var emailTouched = false
    @State private var passwordTouched = false
    @FocusState private var focus: AuthField?

    init(state: SignUpUiState, onBack: (() -> Void)?, actions: AuthActions) {
        self.state = state
        self.onBack = onBack
        self.actions = actions
    }

    private var showEmailError: Bool {
        (emailTouched || state.showFieldErrors) && emailFieldHasError(email)
    }

    private var showPasswordError: Bool {
        (passwordTouched || state.showFieldErrors) && !password.isEmpty && !AuthForm.isPasswordLongEnough(password)
    }

    private var canSubmit: Bool {
        AuthForm.hasBothFields(email: email, password: password) && !state.isLoading
    }

    var body: some View {
        AuthScaffold(paneTitle: "auth_signup_title", onBack: onBack) {
            SocialButtons(isLoading: state.isLoading, actions: actions)
            OrText()
            fields
            Spacer().frame(height: Spacing.lg)
            AuthMessageArea(message: state.message, onGoToSignIn: actions.switchScreen)
            // La ligne explicative est JUSTE au-dessus du bouton principal.
            ConsentNotice(sentenceKey: "ios_auth_consent_notice")
            Spacer().frame(height: Spacing.md)
            submitButton
            UnderlinedLink(key: "auth_signup_have_account", action: actions.switchScreen)
        }
        .onChange(of: focus) { newFocus in
            if newFocus != .email && !email.isEmpty {
                emailTouched = true
            }
            if newFocus != .password && !password.isEmpty {
                passwordTouched = true
            }
        }
    }

    private var fields: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            AuthTextField(
                label: "auth_email_label",
                text: emailBinding,
                focus: AuthFieldFocus(field: .email, binding: $focus),
                options: AuthFieldOptions(
                    errorText: showEmailError ? L10n.string("auth_error_email_format") : nil
                ),
                onSubmit: { focus = .password }
            )
            AuthTextField(
                label: "auth_password_label",
                text: passwordBinding,
                focus: AuthFieldFocus(field: .password, binding: $focus),
                options: AuthFieldOptions(
                    errorText: showPasswordError ? L10n.string("auth_error_password_short") : nil,
                    isPassword: true,
                    isNewPassword: true,
                    submitLabel: .done
                ),
                onSubmit: submit
            )
        }
    }

    private var submitButton: some View {
        VStack(spacing: 0) {
            Button("auth_signup_action", action: submit)
                .buttonStyle(.miffPrimaryFullWidth)
                .disabled(!canSubmit)
            if state.isLoading {
                LoadingIndicator()
            }
        }
    }

    private var emailBinding: Binding<String> {
        Binding(get: { email }, set: { email = AuthForm.limitEmailInput($0) })
    }

    private var passwordBinding: Binding<String> {
        Binding(get: { password }, set: { password = AuthForm.limitPasswordInput($0) })
    }

    private func submit() {
        if AuthForm.hasBothFields(email: email, password: password) && !state.isLoading {
            actions.submitEmail(email, password)
        }
    }
}

/// L'inscription branchée sur son view model.
@MainActor
struct SignUpView: View {
    @StateObject private var viewModel: SignUpViewModel
    private let environment: AppEnvironment
    private let onBack: (() -> Void)?
    private let onGoToSignIn: () -> Void

    init(environment: AppEnvironment, onBack: (() -> Void)?, onGoToSignIn: @escaping () -> Void) {
        self.environment = environment
        self.onBack = onBack
        self.onGoToSignIn = onGoToSignIn
        _viewModel = StateObject(
            wrappedValue: SignUpViewModel(session: environment.session, network: environment.network)
        )
    }

    var body: some View {
        SignUpContent(
            state: viewModel.state,
            onBack: onBack,
            actions: AuthActions(
                submitEmail: { viewModel.submitEmail(email: $0, password: $1) },
                apple: { viewModel.submitApple(source: environment.socialSource) },
                google: { viewModel.submitGoogle(source: environment.socialSource) },
                switchScreen: onGoToSignIn
            )
        )
    }
}
