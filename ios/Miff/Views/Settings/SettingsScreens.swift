import SwiftUI

/// Les gestes de la liste des Réglages.
struct SettingsActions {
    let onBack: () -> Void
    let onOpenMyInfo: () -> Void
    let onOpenAccount: () -> Void
    let onOpenPrivacy: () -> Void
    let onOpenNotifications: () -> Void
}

/// Réglages (maquettes « réglages », ticket Profil / Réglages critère A4) : Mes informations, Mon compte, Notifications (NOTIF-2I, absente
/// des Réglages restreints), Langue, Confidentialité. Rien d'autre : pas d'encart Premium ni d'abonnement (rien ne peut être acheté
/// encore). La ligne « Langue » ouvre les réglages de l'iPhone pour Miff (décision Q4) et dit la langue en cours.
struct SettingsListView: View {
    let actions: SettingsActions
    let build: BuildInfo
    /// Un compte SANS Miff (décision du PM du 2026-11-07) : seulement Mon compte et Confidentialité.
    var restricted = false

    var body: some View {
        MiffFrame(title: L10n.string("settings_title"), onBack: actions.onBack, usesCanvas: true) {
            VStack(spacing: 0) {
                ForEach(SettingsAccess.rows(restricted: restricted), id: \.self) { row in
                    rowView(row)
                }
            }
            Text(verbatim: L10n.format("ios_settings_version", build.version, build.build))
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.md)
        }
    }
}

extension SettingsListRow: Hashable {}

extension SettingsListView {
    @ViewBuilder
    fileprivate func rowView(_ row: SettingsListRow) -> some View {
        switch row {
        case .myInfo:
            SettingsRow(
                systemImage: "pencil",
                title: L10n.string("settings_my_info_title"),
                subtitle: L10n.string("settings_my_info_subtitle"),
                action: actions.onOpenMyInfo
            )
        case .account:
            SettingsRow(
                systemImage: "person",
                title: L10n.string("settings_account_title"),
                subtitle: L10n.string("settings_account_subtitle"),
                action: actions.onOpenAccount
            )
        case .notifications:
            SettingsRow(
                systemImage: "bell",
                title: L10n.string("notif_settings_title"),
                subtitle: L10n.string("notif_settings_subtitle"),
                action: actions.onOpenNotifications
            )
        case .language:
            SettingsRow(
                systemImage: "globe",
                title: L10n.string("settings_language_title"),
                subtitle: L10n.format("ios_settings_language_current", AppLanguageName.current),
                hint: L10n.string("ios_settings_language_hint_a11y"),
                action: { ExternalOpener.openSystemSettings() }
            )
        case .privacy:
            SettingsRow(
                systemImage: "lock",
                title: L10n.string("settings_privacy_title"),
                subtitle: L10n.string("settings_privacy_subtitle"),
                action: actions.onOpenPrivacy
            )
        }
    }
}

/// « Si les détections tardent » : ce qui peut retarder une arrivée ou un départ sur un iPhone. Texte calme, sans rien promettre.
struct DetectionHelpView: View {
    let onBack: () -> Void

    var body: some View {
        MiffFrame(title: L10n.string("settings_detection_help_title"), onBack: onBack, usesCanvas: true) {
            SettingsBody(text: L10n.string("ios_detection_help_intro"))
            SettingsBody(text: L10n.string("ios_detection_help_step_location"))
            SettingsBody(text: L10n.string("ios_detection_help_step_refresh"))
            SettingsBody(text: L10n.string("ios_detection_help_step_saver"))
            SettingsBody(text: L10n.string("ios_detection_help_step_swipe"))
            SettingsBody(text: L10n.string("ios_detection_help_outro"))
            Button {
                ExternalOpener.openSystemSettings()
            } label: {
                Text("settings_privacy_open_phone_settings")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        }
    }
}
