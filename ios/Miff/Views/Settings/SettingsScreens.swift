import SwiftUI

/// Les gestes de la liste des Réglages.
struct SettingsActions {
    let onBack: () -> Void
    let onOpenMyInfo: () -> Void
    let onOpenAccount: () -> Void
    let onOpenPrivacy: () -> Void
}

/// Réglages (maquettes « réglages », ticket Profil / Réglages critère A4) : Mes informations, Mon compte, Langue, Confidentialité.
/// Rien d'autre : pas de ligne Notifications (rien n'est envoyé encore), pas d'encart Premium ni d'abonnement (rien ne peut être
/// acheté encore). La ligne « Langue » ouvre les réglages de l'iPhone pour Miff (décision Q4) et dit la langue en cours.
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

/// Réglages > Mes informations (ticket Profil / Réglages, critères A5 à A8). PREMIÈRE VERSION : en lecture seule. L'écran montre la photo,
/// le prénom, le nom, le pays et le numéro, et dit honnêtement que les modifier viendra dans une prochaine mise à jour. L'écriture
/// (un lot `users/{uid}` + fiche de chaque Miff, comme sur Android) est le chantier suivant.
struct MyInfoView: View {
    let state: ProfileTabState
    let tools: PhoneNumberTools
    let onBack: () -> Void
    let onRetry: () -> Void

    var body: some View {
        MiffFrame(title: L10n.string("settings_my_info_title"), onBack: onBack, usesCanvas: true) {
            switch state {
            case .loading:
                LoadingIndicator()
            case .unavailable:
                MiffNotice(text: L10n.string("settings_my_info_load_failed"))
                Button(action: onRetry) {
                    Text("auth_retry_action")
                }
                .buttonStyle(.miffSecondaryFullWidth)
            case .ready(let profile):
                content(profile)
            }
        }
    }

    @ViewBuilder
    private func content(_ profile: ProfileSummary) -> some View {
        MiffAvatar(jpeg: profile.avatarJpeg, initials: profile.initials, size: 96)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.sm)
        SettingsValueRow(label: L10n.string("profile_first_name_label"), value: profile.firstName)
        SettingsValueRow(label: L10n.string("profile_last_name_label"), value: profile.lastName)
        if let phone = PhoneDisplay.of(phone: profile.phone, tools: tools, locale: .current) {
            let country = "\(phone.country.flag) \(phone.country.name) \(phone.country.dialCode)"
            SettingsValueRow(
                label: L10n.string("profile_phone_country_label"),
                value: country,
                spokenValue: "\(phone.country.name) \(phone.country.dialCode)"
            )
            SettingsValueRow(label: L10n.string("settings_my_info_phone_label"), value: phone.national)
            SettingsBody(text: L10n.string("profile_phone_visibility"))
        }
        MiffNotice(text: L10n.string("ios_myinfo_edit_soon"))
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
