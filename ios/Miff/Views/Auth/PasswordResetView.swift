import SwiftUI

/// « Mot de passe oublié » (maquette « Welcoming screen -10 », ticket §3 C). Un champ (l'adresse du compte), un bouton ;
/// après l'envoi, UNE confirmation qui dit les mêmes mots pour toute adresse (aucune énumération de comptes, critère M2),
/// puis le bouton se met en pause environ une minute (M5) et devient « Renvoyer l'e-mail ». Le nouveau mot de passe se
/// choisit sur la page web ouverte par l'e-mail, pas ici.
///
/// `initialEmail` est l'adresse déjà saisie sur l'écran de connexion, s'il y en a une (moins de saisie). Aucun mot de
/// passe sur cet écran.
struct PasswordResetContent: View {
    let state: PasswordResetUiState
    let initialEmail: String
    let onBack: () -> Void
    let onEmailChanged: (String) -> Void
    let onSubmit: (String) -> Void

    @State private var email: String
    @State private var emailTouched = false
    @FocusState private var focus: ResetField?

    private enum ResetField: Hashable {
        case email
    }

    init(
        state: PasswordResetUiState,
        initialEmail: String,
        callbacks: PasswordResetCallbacks
    ) {
        self.state = state
        self.initialEmail = initialEmail
        onBack = callbacks.onBack
        onEmailChanged = callbacks.onEmailChanged
        onSubmit = callbacks.onSubmit
        _email = State(initialValue: initialEmail)
    }

    private var showEmailError: Bool {
        ((emailTouched || state.showFieldError) && emailFieldHasError(email)) || state.emailRejected
    }

    private var canSend: Bool {
        !AuthForm.normalizeEmail(email).isEmpty && !state.isLoading && !state.resendLocked
    }

    var body: some View {
        AuthScaffold(paneTitle: "auth_reset_title", onBack: onBack) {
            ScreenTitle(key: "auth_reset_title", style: textTitleBold20)
            Text("auth_reset_intro")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextForm.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            Spacer().frame(height: Spacing.xl)
            emailField
            Spacer().frame(height: Spacing.lg)
            confirmation
            AuthMessageArea(message: state.message)
            sendButton
            if state.sent {
                UnderlinedLink(key: "auth_reset_back_to_signin", action: onBack)
            }
        }
        .onAppear {
            onEmailChanged(email)
        }
        .onChange(of: email) { newValue in
            onEmailChanged(newValue)
        }
        .onChange(of: focus) { newFocus in
            if newFocus == nil && !email.isEmpty {
                emailTouched = true
            }
        }
    }

    private var emailField: some View {
        LabeledTextField(
            label: "auth_email_label",
            text: emailBinding,
            focus: LabeledFieldFocus(field: ResetField.email, binding: $focus),
            options: LabeledFieldOptions(
                errorText: showEmailError ? L10n.string("auth_error_email_format") : nil,
                submitLabel: .send,
                keyboard: .emailAddress,
                contentType: .emailAddress
            ),
            onSubmit: send
        )
    }

    /// La confirmation (et la phrase de la pause) : les mêmes mots que l'adresse ait un compte ou non.
    @ViewBuilder
    private var confirmation: some View {
        if state.sent {
            // `id` : un nouvel envoi crée un NOUVEL élément, pour que le lecteur d'écran redise la confirmation (identique).
            MessageText(text: L10n.string("auth_reset_sent"), isError: false)
                .id(state.sendCount)
                .padding(.bottom, Spacing.sm)
        }
        if state.resendLocked {
            Text("auth_reset_resend_wait")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, Spacing.sm)
        }
    }

    private var sendButton: some View {
        VStack(spacing: 0) {
            Button(action: send) {
                Text(sendLabel)
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!canSend)
            if state.isLoading {
                LoadingIndicator()
            }
        }
    }

    private var sendLabel: LocalizedStringKey {
        state.sent ? "auth_reset_resend_action" : "auth_reset_action"
    }

    private var emailBinding: Binding<String> {
        Binding(get: { email }, set: { email = AuthForm.limitEmailInput($0) })
    }

    private func send() {
        guard canSend else {
            return
        }
        // Cache le clavier pour que la confirmation soit visible.
        focus = nil
        onSubmit(email)
    }
}

/// Les retours de l'écran « Mot de passe oublié ».
struct PasswordResetCallbacks {
    let onBack: () -> Void
    let onEmailChanged: (String) -> Void
    let onSubmit: (String) -> Void
}

/// L'écran branché sur son view model.
@MainActor
struct PasswordResetView: View {
    @StateObject private var viewModel: PasswordResetViewModel
    private let initialEmail: String
    private let onBack: () -> Void

    init(environment: AppEnvironment, initialEmail: String, onBack: @escaping () -> Void) {
        self.initialEmail = initialEmail
        self.onBack = onBack
        _viewModel = StateObject(
            wrappedValue: PasswordResetViewModel(
                gateway: environment.passwordResetGateway,
                network: environment.network,
                cooldown: environment.passwordResetCooldown,
                clock: .live
            )
        )
    }

    var body: some View {
        PasswordResetContent(
            state: viewModel.state,
            initialEmail: initialEmail,
            callbacks: PasswordResetCallbacks(
                onBack: onBack,
                onEmailChanged: { viewModel.onEmailChanged($0) },
                onSubmit: { viewModel.submit($0) }
            )
        )
    }
}
