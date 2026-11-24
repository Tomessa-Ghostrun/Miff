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
            // Discret : un lien de texte SOUS « Se déconnecter », jamais un bouton plein (ticket AUTH-5, §3.2). Il n'ouvre que l'écran
            // d'information : rien n'est écrit d'ici.
            DeleteAccountLink(action: onDelete)
                .disabled(model.state.isSigningOut)
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

/// Réglages > Confidentialité (ticket Profil / Réglages, critères B5 et RG6) : l'interrupteur « statistiques d'usage et rapports de plantage
/// anonymes » (le même choix et les mêmes textes qu'Android), ce que Miff fait de la localisation, l'état des autorisations du
/// téléphone (lu ici, jamais demandé) avec un accès aux réglages de l'iPhone, l'état des notifications, l'aide à la détection
/// et les textes légaux. « Nous contacter » n'est plus ici : il est dans « À propos et aide » (décision du PM du 2026-11-13). L'état est relu à chaque retour dans l'app : la personne change une autorisation dans les
/// réglages de l'iPhone puis revient.
///
/// L'interrupteur agit tout de suite : retirer coupe la collecte sur l'iPhone avant même que le compte l'enregistre (voir
/// `PrivacyViewModel` et `ConsentWithdrawals`).
struct PrivacyView: View {
    let permissions: DevicePermissions
    /// CONS-4-I : les Miffs sous le texte de la version 3 (« Revoir et accepter » de la carte « Une précision », sans pause ni pastille).
    @ObservedObject var awaiting: AwaitingAgreement
    let onBack: () -> Void
    let onOpenDetectionHelp: () -> Void
    /// Efface le journal local des changements de présence (la répartition du temps du Profil repart de zéro). Local : rien n'est envoyé.
    let onEraseTimeHistory: () -> Void

    /// Créé UNE fois par l'écran (autoclosure du `StateObject`) : l'état d'écriture ne repart pas de zéro à chaque redessin du parent.
    @StateObject private var model: PrivacyViewModel
    @State private var permission: PrivacyPermissionState?
    @State private var confirmsErase = false
    @State private var erased = false
    @Environment(\.scenePhase) private var scenePhase

    init(
        permissions: DevicePermissions,
        awaiting: AwaitingAgreement,
        model: @autoclosure @escaping () -> PrivacyViewModel,
        onBack: @escaping () -> Void,
        onOpenDetectionHelp: @escaping () -> Void,
        onEraseTimeHistory: @escaping () -> Void = {}
    ) {
        self.permissions = permissions
        self.awaiting = awaiting
        self.onBack = onBack
        self.onOpenDetectionHelp = onOpenDetectionHelp
        self.onEraseTimeHistory = onEraseTimeHistory
        _model = StateObject(wrappedValue: model())
    }

    var body: some View {
        MiffFrame(title: L10n.string("settings_privacy_title"), onBack: onBack, usesCanvas: true) {
            analytics
            location
            notifications
            timeHistory
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

    /// « Statistiques d'usage et rapports de plantage anonymes » : toute la ligne est l'interrupteur, dessiné d'après l'état du compte (il ne
    /// bouge qu'une fois le choix pris : accepter exige que le compte l'enregistre). Retiré sans réseau : la collecte est coupée quand même
    /// et le message dit calmement que le choix sera enregistré au retour du réseau.
    @ViewBuilder
    private var analytics: some View {
        let enabled = model.state.analyticsEnabled
        MiffSwitchRow(
            title: L10n.string("settings_privacy_analytics_title"),
            description: L10n.string("settings_privacy_analytics_body"),
            isOn: enabled,
            isEnabled: !model.state.isWriting,
            stateText: L10n.string(enabled ? "miff_sharing_state_on" : "miff_sharing_state_off"),
            action: { model.toggleAnalytics() }
        )
        if let message = model.state.message {
            // Des textes déjà calmes (« Pas de connexion pour le moment… ») : sans rouge, rien n'est cassé et rien n'a changé.
            MiffNotice(text: L10n.string(Self.messageKey(message)))
        }
        if model.state.withdrawalPending {
            MiffNotice(text: L10n.string("settings_privacy_withdrawal_not_recorded"))
            Button {
                model.retryWithdrawal()
            } label: {
                Text("settings_privacy_retry_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
            .disabled(model.state.isWriting)
        }
    }

    private static func messageKey(_ message: PrivacyMessage) -> String {
        switch message {
        case .network:
            return "auth_error_network"
        case .saveFailed:
            return "profile_save_failed"
        }
    }

    @ViewBuilder
    private var location: some View {
        SettingsSectionTitle(key: "settings_privacy_location_title")
        SettingsBody(text: L10n.string("settings_privacy_location_body"))
        if let permission {
            MiffNotice(text: L10n.string(permission.locationStatusKey))
        }
        if showsClarify {
            clarify
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

    /// La carte calme peut toujours être relue d'ici (elle vient d'elle-même 3 fois au plus). Mots calmes, aucune pause, aucune pastille : elle
    /// n'apparaît que s'il y a des Miffs à préciser et qu'aucune n'est réellement en pause (CONS-4-I, critère CA-10).
    private var showsClarify: Bool {
        !awaiting.clarifying.isEmpty && awaiting.waiting.isEmpty
    }

    @ViewBuilder
    private var clarify: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("miff_clarify_title")
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextForm.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            SettingsBody(text: L10n.string("settings_privacy_clarify_body"))
        }
        .padding(.top, Spacing.sm)
        Button {
            awaiting.requestReview()
        } label: {
            Text("miff_waiting_review")
        }
        .buttonStyle(.miffSecondaryFullWidth)
    }

    @ViewBuilder
    private var notifications: some View {
        SettingsSectionTitle(key: "settings_privacy_notifications_title")
        // NOTIF-P0 : les rappels existent (l'ancien texte « arrivent bientôt, rien n'est envoyé » est devenu faux). Texte commun avec Android.
        SettingsBody(text: L10n.string("notif_privacy_body"))
        if let permission {
            SettingsBody(text: L10n.string(permission.notificationsStatusKey))
        }
    }

    /// D15 : l'historique de temps gardé sur le téléphone, et le moyen de l'effacer (confirmation calme, puis un message). Ne change rien pour les
    /// proches : le journal ne leur est jamais envoyé.
    @ViewBuilder
    private var timeHistory: some View {
        SettingsSectionTitle(key: "settings_privacy_time_title")
        SettingsBody(text: L10n.string("settings_privacy_time_body"))
        Button {
            confirmsErase = true
        } label: {
            Text("settings_privacy_time_erase")
        }
        .buttonStyle(.miffSecondaryFullWidth)
        .alert(
            Text("settings_privacy_time_confirm_title"),
            isPresented: $confirmsErase,
            actions: {
                Button("settings_privacy_time_confirm_action", role: .destructive) {
                    onEraseTimeHistory()
                    erased = true
                }
                Button("common_cancel", role: .cancel) {}
            },
            message: { Text("settings_privacy_time_confirm_body") }
        )
        // CONS-4-I : dit ce que le bouton efface, et que cela ne change rien pour les proches.
        SettingsBody(text: L10n.string("settings_privacy_time_erase_note"))
        if erased {
            MiffNotice(text: L10n.string("settings_privacy_time_done"))
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
    }
}
