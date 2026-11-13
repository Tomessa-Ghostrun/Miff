import SwiftUI
import UIKit

/// Le presse-papiers du système, pour la version et l'adresse de contact : un texte simple, rien d'autre.
struct SystemPasteboard: TextPasteboard {
    func writeText(_ text: String) {
        UIPasteboard.general.string = text
    }
}

/// Les gestes de l'écran « À propos et aide » qui mènent à un autre écran de l'app.
struct AboutActions {
    let onBack: () -> Void
    let onOpenDetectionHelp: () -> Void
    let onOpenNotificationsHelp: () -> Void
    let onOpenLicenses: () -> Void
}

/// Réglages > « À propos et aide » (ticket `reglages-a-propos.md`, ABOUT-1I) : la version (un toucher la copie), l'aide, le contact,
/// le formulaire d'avis, les textes légaux et les licences. Écran calme : aucune donnée lue ni écrite, aucun appel réseau à l'ouverture.
/// Pas de maquette : gabarit des écrans de Réglages (décision Q8 du PM).
struct AboutView: View {
    let actions: AboutActions
    let build: BuildInfo
    /// Un compte SANS Miff : pas d'aide « notifications » (l'écran des notifications ne lui est pas ouvert).
    var restricted = false
    var feedbackURL: URL? = FeedbackForm.url

    @State private var versionCopied = false
    @State private var contactUnavailable = false
    @State private var addressCopied = false
    @State private var formUnavailable = false

    var body: some View {
        MiffFrame(title: L10n.string("about_title"), onBack: actions.onBack, usesCanvas: true) {
            versionBlock
            helpBlock
            textsBlock
            SettingsRow(
                systemImage: "doc.text",
                title: L10n.string("about_licenses_title"),
                action: actions.onOpenLicenses
            )
        }
    }

    // MARK: - Version

    @ViewBuilder
    private var versionBlock: some View {
        Text(verbatim: "Miff")
            .miffTextStyle(textDisplayTitle)
            .foregroundColor(colorTextForm.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
        Button(action: copyVersion) {
            HStack(spacing: Spacing.sm) {
                Text(verbatim: L10n.format("about_version_label", build.version, build.build))
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextForm.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                Image(systemName: "doc.on.doc")
                    .foregroundColor(colorActionPrimary.color)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint(Text(verbatim: L10n.string("about_version_copy_hint_a11y")))
        if versionCopied {
            copiedNote
        }
    }

    private var copiedNote: some View {
        Text("about_copied")
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorTextDescription.color)
    }

    /// Copie « Miff 0.1.0 (12) » et rien d'autre (décision Q7 du PM), annonce « Copié. » et le retire au bout de deux secondes.
    private func copyVersion() {
        AboutVersion.copy(build, to: SystemPasteboard())
        versionCopied = true
        Announcer.announce(L10n.string("about_copied"))
        Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            versionCopied = false
        }
    }

    // MARK: - Aide

    @ViewBuilder
    private var helpBlock: some View {
        SettingsSectionTitle(key: "about_help_title")
        ForEach(AboutContent.helpRows(restricted: restricted, feedbackURL: feedbackURL), id: \.self) { row in
            helpRow(row)
        }
        if contactUnavailable {
            contactFallback
        }
        if formUnavailable {
            MiffNotice(text: L10n.string("review_open_failed"))
        }
        if feedbackURL != nil {
            SettingsBody(text: L10n.string("about_feedback_note"))
        }
    }

    @ViewBuilder
    private func helpRow(_ row: AboutHelpRow) -> some View {
        switch row {
        case .detectionHelp:
            SettingsRow(
                systemImage: "location",
                title: L10n.string("settings_privacy_detection_help_link"),
                action: actions.onOpenDetectionHelp
            )
        case .notificationsHelp:
            SettingsRow(
                systemImage: "bell",
                title: L10n.string("notif_settings_help_link"),
                action: actions.onOpenNotificationsHelp
            )
        case .contact:
            SettingsRow(
                systemImage: "envelope",
                title: L10n.string("settings_privacy_contact"),
                hint: L10n.string("settings_privacy_open_mail_a11y"),
                action: openContact
            )
        case .feedback:
            SettingsRow(
                systemImage: "text.bubble",
                title: L10n.string("about_feedback_title"),
                subtitle: L10n.string("about_feedback_subtitle"),
                hint: L10n.string("about_feedback_hint_a11y"),
                action: openFeedback
            )
        }
    }

    /// L'application e-mail, sans texte pré-rempli. Si rien ne s'ouvre, la ligne n'est pas laissée sans réaction : l'adresse s'affiche,
    /// avec un bouton pour la copier.
    private func openContact() {
        ExternalOpener.open(ContactLinks.supportMailURL()) { opened in
            if !opened {
                contactUnavailable = true
            }
        }
    }

    @ViewBuilder
    private var contactFallback: some View {
        MiffNotice(text: L10n.format("about_contact_unavailable", AppLinks.supportEmail))
        Button {
            SystemPasteboard().writeText(AppLinks.supportEmail)
            addressCopied = true
            Announcer.announce(L10n.string("about_copied"))
        } label: {
            Text("about_contact_copy_action")
        }
        .buttonStyle(.miffSecondaryFullWidth)
        if addressCopied {
            copiedNote
        }
    }

    /// Le formulaire s'ouvre dans le navigateur, à l'adresse EXACTE de `FeedbackForm` : rien n'y est ajouté.
    private func openFeedback() {
        ExternalOpener.open(feedbackURL) { opened in
            formUnavailable = !opened
        }
    }

    // MARK: - Textes

    @ViewBuilder
    private var textsBlock: some View {
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
