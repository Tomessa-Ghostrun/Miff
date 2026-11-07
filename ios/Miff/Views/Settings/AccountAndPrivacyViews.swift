import SwiftUI

/// Réglages > Mon compte (ticket Profil / Réglages, critères B1 à B4) : l'adresse e-mail en lecture seule, « Changer mon mot de passe »
/// (compte avec mot de passe seulement), « Se déconnecter » avec confirmation, et l'entrée « Supprimer mon compte ».
struct AccountView: View {
    @StateObject private var model: AccountViewModel
    let onBack: () -> Void
    let onDelete: () -> Void

    init(environment: AppEnvironment, onBack: @escaping () -> Void, onDelete: @escaping () -> Void) {
        let session = environment.session
        _model = StateObject(
            wrappedValue: AccountViewModel(
                email: environment.accountInfo.currentEmail(),
                signIn: AccountSignIn(method: session.gateway.signInMethod()),
                signOut: { await session.signOut() },
                passwordReset: environment.passwordResetGateway,
                network: environment.network,
                cooldown: environment.passwordResetCooldown
            )
        )
        self.onBack = onBack
        self.onDelete = onDelete
    }

    var body: some View {
        MiffFrame(title: L10n.string("settings_account_title"), onBack: onBack, usesCanvas: true) {
            identity
            if model.state.canChangePassword {
                passwordSection
            }
            Button {
                model.requestSignOut()
            } label: {
                Text("settings_account_sign_out_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
            .disabled(model.state.isSigningOut)
            SettingsRow(
                systemImage: "trash",
                title: L10n.string("settings_delete_entry"),
                subtitle: L10n.string("ios_account_delete_subtitle"),
                action: onDelete
            )
        }
        .alert(
            Text("settings_account_sign_out_title"),
            isPresented: Binding(get: { model.state.confirmingSignOut }, set: { if !$0 { model.cancelSignOut() } }),
            actions: {
                Button("settings_account_sign_out_cancel", role: .cancel) {
                    model.cancelSignOut()
                }
                Button("settings_account_sign_out_action") {
                    model.confirmSignOut()
                }
            },
            message: {
                Text("settings_account_sign_out_body")
            }
        )
    }

    @ViewBuilder
    private var identity: some View {
        if let email = model.state.email {
            SettingsValueRow(
                label: L10n.string("settings_account_email_label"),
                value: email,
                spokenValue: L10n.format("settings_account_email_a11y", email)
            )
        }
        switch model.state.signIn {
        case .google:
            SettingsBody(text: L10n.string("settings_account_google"))
        case .apple:
            SettingsBody(text: L10n.string("ios_account_apple"))
        case .password:
            EmptyView()
        }
    }

    @ViewBuilder
    private var passwordSection: some View {
        SettingsBody(text: L10n.string("settings_account_password_help"))
        Button {
            model.changePassword()
        } label: {
            Text(verbatim: passwordButtonTitle)
        }
        .buttonStyle(.miffSecondaryFullWidth)
        .disabled(model.state.passwordStatus == .sending || model.state.passwordLocked)
        if model.state.passwordStatus == .sent {
            MiffNotice(text: L10n.string("settings_account_password_sent"))
        }
        if let message = model.state.passwordMessage {
            MiffNotice(text: message.text, isError: true)
        }
    }

    private var passwordButtonTitle: String {
        model.state.passwordStatus == .sending
            ? L10n.string("settings_account_password_sending")
            : L10n.string("settings_account_password_action")
    }
}

/// Réglages > Confidentialité (ticket Profil / Réglages, critère B5) : ce que Miff fait de la localisation, l'état des autorisations du
/// téléphone (lu ici, jamais demandé) avec un accès aux réglages de l'iPhone, les notifications (« bientôt »), l'aide à la détection,
/// les textes légaux et le contact. L'état est relu à chaque retour dans l'app : la personne change une autorisation dans les
/// réglages de l'iPhone puis revient.
///
/// PAS ENCORE ICI (écart au ticket, listé dans la PR) : l'interrupteur « statistiques et rapports de plantage », qui demande son propre
/// chantier (retrait du consentement du compte, avec reprise hors connexion). En attendant le retrait se demande par e-mail, comme le
/// dit déjà le texte de consentement d'iOS.
struct PrivacyView: View {
    let permissions: DevicePermissions
    let onBack: () -> Void
    let onOpenDetectionHelp: () -> Void

    @State private var permission: PrivacyPermissionState?
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        MiffFrame(title: L10n.string("settings_privacy_title"), onBack: onBack, usesCanvas: true) {
            location
            notifications
            texts
        }
        .task {
            await refresh()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                Task {
                    await refresh()
                }
            }
        }
    }

    private func refresh() async {
        permission = PrivacyPermissionState(snapshot: await permissions.snapshot())
    }

    @ViewBuilder
    private var location: some View {
        SettingsSectionTitle(key: "settings_privacy_location_title")
        SettingsBody(text: L10n.string("settings_privacy_location_body"))
        if let permission {
            MiffNotice(text: L10n.string(permission.locationStatusKey))
        }
        Button {
            ExternalOpener.openSystemSettings()
        } label: {
            Text("settings_privacy_open_phone_settings")
        }
        .buttonStyle(.miffSecondaryFullWidth)
        SettingsLinkRow(
            title: L10n.string("settings_privacy_detection_help_link"),
            spokenHint: "",
            action: onOpenDetectionHelp
        )
    }

    @ViewBuilder
    private var notifications: some View {
        SettingsSectionTitle(key: "settings_privacy_notifications_title")
        SettingsBody(text: L10n.string("settings_privacy_notifications_body"))
        if let permission {
            SettingsBody(text: L10n.string(permission.notificationsStatusKey))
        }
    }

    @ViewBuilder
    private var texts: some View {
        SettingsSectionTitle(key: "settings_privacy_texts_title")
        SettingsLinkRow(
            title: L10n.string("auth_consent_terms_link"),
            spokenHint: L10n.string("settings_privacy_open_browser_a11y"),
            action: { ExternalOpener.open(AppLinks.termsURL()) }
        )
        SettingsLinkRow(
            title: L10n.string("auth_consent_privacy_link"),
            spokenHint: L10n.string("settings_privacy_open_browser_a11y"),
            action: { ExternalOpener.open(AppLinks.privacyURL()) }
        )
        SettingsLinkRow(
            title: L10n.string("settings_privacy_contact"),
            spokenHint: L10n.string("settings_privacy_open_mail_a11y"),
            action: { ExternalOpener.open(ContactLinks.supportMailURL()) }
        )
    }
}
