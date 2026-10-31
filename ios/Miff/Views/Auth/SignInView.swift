import SwiftUI

/// Écran de connexion (ticket §3 B, critères C1 à C9). Pas de ligne de consentement ici : un compte existant n'a rien à
/// donner, et une personne nouvelle qui arrive par Google ou Apple reçoit le panneau de consentement ensuite (ticket
/// §7.8). « Mot de passe oublié ? » (maquette -8) est sous le champ du mot de passe et ouvre l'écran de réinitialisation
/// avec l'adresse déjà saisie. Le mot de passe n'est jamais sauvegardé.
struct SignInContent: View {
    let state: SignInUiState
    let onBack: (() -> Void)?
    let actions: AuthActions

    @State private var email = ""
    @State private var password = ""
    @State private var emailTouched = false
    @FocusState private var focus: AuthField?

    init(state: SignInUiState, onBack: (() -> Void)?, actions: AuthActions) {
        self.state = state
        self.onBack = onBack
        self.actions = actions
    }

    private var showEmailError: Bool {
        (emailTouched || state.showFieldErrors) && emailFieldHasError(email)
    }

    private var canSubmit: Bool {
        AuthForm.hasBothFields(email: email, password: password) && !state.isLoading
    }

    var body: some View {
        AuthScaffold(paneTitle: "auth_signin_title", onBack: onBack) {
            SocialButtons(isLoading: state.isLoading, actions: actions)
            OrText()
            if state.showAccountNotCreatedNote {
                // Une réassurance, pas une erreur : couleur neutre.
                MessageText(text: L10n.string("auth_panel_refused_notice"), isError: false)
                    .padding(.bottom, Spacing.md)
            }
            fields
            FieldLink(key: "auth_signin_forgot_password") {
                actions.forgotPassword(AuthForm.normalizeEmail(email))
            }
            .disabled(state.isLoading)
            Spacer().frame(height: Spacing.md)
            // Un seul message, sous les champs, pour « adresse inconnue » et « mauvais mot de passe » (critères C4 et C6).
            AuthMessageArea(message: state.message)
            submitButton
            UnderlinedLink(key: "auth_signin_no_account", action: actions.switchScreen)
        }
        .onChange(of: focus) { newFocus in
            if newFocus != .email && !email.isEmpty {
                emailTouched = true
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
                options: AuthFieldOptions(isPassword: true, submitLabel: .done),
                onSubmit: submit
            )
        }
    }

    private var submitButton: some View {
        VStack(spacing: 0) {
            Button("auth_signin_action", action: submit)
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

/// Où mènent les liens de l'écran de connexion.
struct SignInNavigation {
    let onGoToSignUp: () -> Void
    let onForgotPassword: (String) -> Void
}

/// La connexion branchée sur son view model.
@MainActor
struct SignInView: View {
    @StateObject private var viewModel: SignInViewModel
    private let environment: AppEnvironment
    private let onBack: (() -> Void)?
    private let onGoToSignUp: () -> Void
    private let onForgotPassword: (String) -> Void

    init(environment: AppEnvironment, onBack: (() -> Void)?, navigation: SignInNavigation) {
        self.environment = environment
        self.onBack = onBack
        onGoToSignUp = navigation.onGoToSignUp
        onForgotPassword = navigation.onForgotPassword
        _viewModel = StateObject(
            wrappedValue: SignInViewModel(session: environment.session, network: environment.network, clock: .live)
        )
    }

    var body: some View {
        SignInContent(
            state: viewModel.state,
            onBack: onBack,
            actions: AuthActions(
                submitEmail: { viewModel.submitEmail(email: $0, password: $1) },
                apple: { viewModel.submitApple(source: environment.socialSource) },
                google: { viewModel.submitGoogle(source: environment.socialSource) },
                switchScreen: onGoToSignUp,
                forgotPassword: onForgotPassword
            )
        )
    }
}
