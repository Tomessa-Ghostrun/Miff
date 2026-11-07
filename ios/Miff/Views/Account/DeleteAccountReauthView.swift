import SwiftUI

/// C. « Une dernière vérification » (et le même écran pour « Dernière vérification avant de terminer », demandée par Firebase tout à la
/// fin ; maquettes 05 et 06). Plein écran, pas une fenêtre : tout reste visible avec le clavier. Seules les méthodes réellement liées au
/// compte sont proposées : le mot de passe, Google, ou Apple. Un compte lié à Apple se ré-authentifie PAR la feuille Apple (seule), dont le
/// code d'autorisation sert aussi à retirer l'autorisation « Se connecter avec Apple ». Rien n'est écrit avant que Firebase accepte.
/// Le mot de passe ne vit que dans le champ de CET écran et part une fois dans `submitPassword`.
struct DeleteAccountReauthView: View {
    @ObservedObject var model: DeleteReauthViewModel
    let onBack: () -> Void
    /// « Mot de passe oublié ? » : seulement sur la première vérification (la suppression n'a pas commencé, rien n'est perdu).
    let onForgotPassword: (() -> Void)?

    @State private var password = ""
    @FocusState private var focus: AuthField?

    private var canSubmit: Bool {
        !password.isEmpty && !model.state.busy
    }

    var body: some View {
        MiffFrame(title: L10n.string("settings_delete_reauth_title"), onBack: onBack, usesCanvas: true) {
            intro
            if let email = model.state.email {
                SettingsBody(text: L10n.format("settings_delete_reauth_account", email))
            }
            if model.state.noMethod {
                DeleteContactSentence(
                    text: L10n.format("settings_delete_reauth_unavailable", AppLinks.supportEmail),
                    systemImage: "info.circle",
                    hint: L10n.string("settings_privacy_open_mail_a11y")
                )
            }
            if model.state.offersApple {
                apple
            }
            if model.state.offersGoogle {
                google
            }
            if model.state.offersPassword {
                passwordSection
            }
            generalMessage
            footer
        }
        .onAppear {
            model.refresh()
        }
        .onDisappear {
            // Le mot de passe ne reste pas en mémoire quand l'écran part.
            password = ""
        }
    }

    // MARK: - Parties

    private var intro: some View {
        HStack(spacing: Spacing.sm + Spacing.xs) {
            DeleteIconTile(systemImage: "lock")
            VStack(alignment: .leading, spacing: Spacing.xs) {
                if model.state.last {
                    Text("settings_delete_reauth_last")
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextForm.color)
                    Text("settings_delete_reauth_last_sub")
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                } else {
                    Text("settings_delete_reauth_body")
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextForm.color)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }

    /// La feuille officielle d'Apple : elle dit elle-même ce qu'elle fait ; la phrase au-dessus dit que, ensuite, Miff n'aura plus
    /// l'autorisation « Se connecter avec Apple ».
    private var apple: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SettingsBody(text: L10n.string("ios_settings_delete_reauth_apple"))
            AppleSignInButton(action: { model.submitApple() })
                .frame(height: 50)
                .allowsHitTesting(!model.state.busy)
                .opacity(model.state.busy ? 0.5 : 1)
        }
    }

    private var google: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("settings_delete_reauth_google")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
            GoogleButton(action: { model.submitGoogle() })
                .allowsHitTesting(!model.state.busy)
                .opacity(model.state.busy ? 0.5 : 1)
            if model.state.offersPassword {
                OrText()
            }
        }
    }

    private var passwordSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            AuthTextField(
                label: "auth_password_label",
                text: $password,
                focus: AuthFieldFocus(field: .password, binding: $focus),
                options: AuthFieldOptions(errorText: passwordError, isPassword: true, submitLabel: .done),
                onSubmit: submit
            )
            if let onForgotPassword {
                FieldLink(key: "auth_signin_forgot_password", action: onForgotPassword)
                    .disabled(model.state.busy)
            }
            Button(action: submit) {
                Text(verbatim: L10n.string(confirmKey))
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!canSubmit)
            Text("settings_delete_reauth_note")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
    }

    /// Le mauvais mot de passe s'affiche sous le champ ; toutes les autres causes (mauvais compte Google, trop d'essais, pas de réseau,
    /// session expirée) au même endroit calme, sans rien de technique.
    private var passwordError: String? {
        model.state.message == .reauthPasswordWrong ? model.state.message?.text : nil
    }

    @ViewBuilder
    private var generalMessage: some View {
        if let message = model.state.message, message != .reauthPasswordWrong {
            MessageText(text: message.text, isError: true)
        }
    }

    private var footer: some View {
        VStack(spacing: Spacing.xs) {
            if model.state.last {
                // Le point de non-retour est derrière nous : pas d'« Annuler », seulement « Plus tard » (la suppression reprend au
                // prochain lancement).
                Text("settings_delete_reauth_later_hint")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            DeleteTextAction(
                key: model.state.last ? "settings_delete_reauth_later" : "settings_delete_cancel",
                action: onBack
            )
            .disabled(model.state.busy)
        }
        .padding(.top, Spacing.sm)
    }

    private var confirmKey: String {
        model.state.busy ? "settings_delete_reauth_checking" : "settings_delete_reauth_confirm"
    }

    private func submit() {
        guard canSubmit else {
            return
        }
        model.submitPassword(password)
    }
}
