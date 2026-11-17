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

/// Réglages > « À propos et aide » (ticket `reglages-a-propos.md`, ABOUT-1I). Retour du PM du 2026-11-13 : EN HAUT et MIS EN AVANT,
/// « Nous contacter » (carte pleine) et « Un retour sur l'app ? » (carte à trait bleu), chacune avec son icône ; PLUS BAS, sous
/// « Informations » et en lignes de liste discrètes : la version (un toucher la copie), les aides, les textes légaux et les licences.
/// Écran calme : aucune donnée lue ni écrite, aucun appel réseau à l'ouverture. Pas de maquette : gabarit des écrans de Réglages.
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

    private var informationRows: [AboutRow] {
        AboutContent.informationRows(restricted: restricted)
    }

    var body: some View {
        MiffFrame(title: L10n.string("about_title"), onBack: actions.onBack, usesCanvas: true) {
            featuredBlock
            informationBlock
        }
    }

    // MARK: - En haut : contact et retour (mis en avant)

    @ViewBuilder
    private var featuredBlock: some View {
        ForEach(AboutContent.featuredRows(feedbackURL: feedbackURL), id: \.self) { row in
            featuredRow(row)
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
    private func featuredRow(_ row: AboutRow) -> some View {
        if row == .contact {
            AboutFeaturedCard(
                systemImage: "envelope",
                title: L10n.string("settings_privacy_contact"),
                hint: L10n.string("settings_privacy_open_mail_a11y"),
                isFilled: true,
                action: openContact
            )
        } else {
            AboutFeaturedCard(
                systemImage: "text.bubble",
                title: L10n.string("about_feedback_title"),
                subtitle: L10n.string("about_feedback_subtitle"),
                hint: L10n.string("about_feedback_hint_a11y"),
                isFilled: false,
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

    private var copiedNote: some View {
        Text("about_copied")
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorTextDescription.color)
    }

    // MARK: - Plus bas : « Informations » (lignes discrètes)

    @ViewBuilder
    private var informationBlock: some View {
        SettingsSectionTitle(key: "about_info_title")
            .padding(.top, Spacing.md)
        VStack(spacing: 0) {
            ForEach(informationRows, id: \.self) { row in
                informationRow(row)
                if row != informationRows.last {
                    Divider()
                }
            }
        }
        if versionCopied {
            copiedNote
        }
    }

    @ViewBuilder
    private func informationRow(_ row: AboutRow) -> some View {
        switch row {
        case .version:
            AboutListRow(
                title: L10n.format("about_version_label", build.version, build.build),
                trailingImage: "doc.on.doc",
                hint: L10n.string("about_version_copy_hint_a11y"),
                action: copyVersion
            )
        case .detectionHelp:
            AboutListRow(
                title: L10n.string("settings_privacy_detection_help_link"),
                action: actions.onOpenDetectionHelp
            )
        case .notificationsHelp:
            AboutListRow(title: L10n.string("notif_settings_help_link"), action: actions.onOpenNotificationsHelp)
        case .terms:
            AboutListRow(
                title: L10n.string("auth_consent_terms_link"),
                trailingImage: "arrow.up.right",
                hint: L10n.string("settings_privacy_open_browser_a11y"),
                action: { ExternalOpener.open(AppLinks.termsURL()) }
            )
        case .privacy:
            AboutListRow(
                title: L10n.string("auth_consent_privacy_link"),
                trailingImage: "arrow.up.right",
                hint: L10n.string("settings_privacy_open_browser_a11y"),
                action: { ExternalOpener.open(AppLinks.privacyURL()) }
            )
        case .licenses:
            AboutListRow(title: L10n.string("about_licenses_title"), action: actions.onOpenLicenses)
        case .contact, .feedback:
            EmptyView()
        }
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
}

/// Une carte mise en avant : une pastille avec l'icône, le titre, sa courte phrase. `isFilled` : fond bleu du design system (le contact) ;
/// sinon fond blanc et trait bleu (le formulaire), comme le bouton secondaire. Toute la carte est UN bouton d'au moins 44 pt, lu d'un
/// trait par VoiceOver ; le texte passe à la ligne aux plus grandes tailles.
private struct AboutFeaturedCard: View {
    let systemImage: String
    let title: String
    var subtitle: String?
    var hint = ""
    let isFilled: Bool
    let action: () -> Void

    private var spokenLabel: String {
        [title, subtitle].compactMap { $0 }.joined(separator: ", ")
    }

    private var textColor: Color {
        (isFilled ? colorTextOnAction : colorActionPrimary).color
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                Image(systemName: systemImage)
                    .font(.system(size: Spacing.sectionIcon, weight: .semibold))
                    .foregroundColor(isFilled ? colorActionPrimary.color : colorTextOnAction.color)
                    .frame(width: Spacing.xl + Spacing.sm, height: Spacing.xl + Spacing.sm)
                    .background(Circle().fill(isFilled ? colorTextOnAction.color : colorActionPrimary.color))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(verbatim: title)
                        .miffTextStyle(textBodyLarge)
                        .foregroundColor(textColor)
                    if let subtitle {
                        Text(verbatim: subtitle)
                            .miffTextStyle(textBodyRegular14)
                            .foregroundColor(isFilled ? colorTextOnAction.color : colorTextDescription.color)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                Image(systemName: "chevron.forward")
                    .foregroundColor(textColor)
                    .accessibilityHidden(true)
            }
            .padding(Spacing.md)
            .frame(minHeight: Spacing.xl * 2)
            .background(cardBackground)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spokenLabel))
        .accessibilityHint(Text(verbatim: hint))
        .accessibilityAddTraits(.isButton)
    }

    @ViewBuilder
    private var cardBackground: some View {
        if isFilled {
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorActionPrimary.color)
                .miffShadow(shadowDefault)
        } else {
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorBgSurface.color)
                .overlay(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .stroke(colorActionPrimary.color, lineWidth: strokeAccent)
                )
        }
    }
}

/// Une ligne de liste standard et discrète : le titre, une petite icône ou une flèche à la fin. Pas de pastille, pas de fond : c'est le
/// rôle des cartes du haut. Un seul bouton d'au moins 44 pt pour VoiceOver.
private struct AboutListRow: View {
    let title: String
    var trailingImage = "chevron.forward"
    var hint = ""
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                Text(verbatim: title)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextForm.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                Image(systemName: trailingImage)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundColor(colorTextDescription.color)
                    .accessibilityHidden(true)
            }
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: Spacing.minTouchTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: title))
        .accessibilityHint(Text(verbatim: hint))
        .accessibilityAddTraits(.isButton)
    }
}
