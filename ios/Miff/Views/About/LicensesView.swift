import SwiftUI

/// Réglages > À propos et aide > « Licences et attributions » (ticket `reglages-a-propos.md`, §5). Hors connexion : les textes complets
/// sont dans l'app. Toucher une ligne déplie le texte de sa licence (une seule à la fois). Aucune formule « conforme » ni « relu ».
struct LicensesView: View {
    let onBack: () -> Void
    @State private var openID: String?

    /// `initiallyOpen` : seulement pour les aperçus de la CI (une licence dépliée sur la capture).
    init(onBack: @escaping () -> Void, initiallyOpen: String? = nil) {
        self.onBack = onBack
        _openID = State(initialValue: initiallyOpen)
    }

    var body: some View {
        MiffFrame(title: L10n.string("about_licenses_title"), onBack: onBack, usesCanvas: true) {
            SettingsBody(text: L10n.string("about_licenses_intro"))
            group(titleKey: "about_licenses_software_title", entries: LicenseCatalog.software)
            maps
            group(titleKey: "about_licenses_fonts_title", entries: LicenseCatalog.fonts)
        }
    }

    @ViewBuilder
    private func group(titleKey: String, entries: [LicenseEntry]) -> some View {
        SettingsSectionTitle(key: titleKey)
        ForEach(entries) { entry in
            LicenseRow(entry: entry, isOpen: openID == entry.id) {
                openID = openID == entry.id ? nil : entry.id
            }
        }
    }

    /// iPhone : les cartes et les adresses sont celles d'Apple (MapKit), livrées avec le système. Ni MapLibre, ni OpenFreeMap, ni Photon
    /// (ils sont sur Android seulement).
    @ViewBuilder
    private var maps: some View {
        SettingsSectionTitle(key: "about_licenses_maps_title")
        SettingsBody(text: L10n.string("ios_about_licenses_maps_apple"))
        SettingsBody(text: L10n.string("about_licenses_maps_privacy"))
        SettingsLinkRow(
            title: L10n.string("auth_consent_privacy_link"),
            spokenHint: L10n.string("settings_privacy_open_browser_a11y"),
            action: { ExternalOpener.open(AppLinks.privacyURL()) }
        )
    }
}

/// Une ligne de licence : le nom, la version et la licence ; dépliée, le texte complet. Un seul bouton pour VoiceOver (nom, version et
/// licence lus d'un trait), d'au moins 44 pt.
private struct LicenseRow: View {
    let entry: LicenseEntry
    let isOpen: Bool
    let toggle: () -> Void

    private var detail: String {
        [entry.version, entry.licenseName].compactMap { $0 }.joined(separator: " · ")
    }

    private var hintKey: String {
        isOpen ? "about_licenses_close_hint_a11y" : "about_licenses_open_hint_a11y"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Button(action: toggle) {
                HStack(spacing: Spacing.md) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(verbatim: entry.name)
                            .miffTextStyle(textBodyBold16)
                            .foregroundColor(colorTextForm.color)
                        Text(verbatim: detail)
                            .miffTextStyle(textBodyRegular14)
                            .foregroundColor(colorTextDescription.color)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    Image(systemName: isOpen ? "chevron.up" : "chevron.down")
                        .foregroundColor(colorTextDescription.color)
                        .accessibilityHidden(true)
                }
                .frame(minHeight: Spacing.minTouchTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: "\(entry.name), \(detail)"))
            .accessibilityHint(Text(verbatim: L10n.string(hintKey)))
            .accessibilityAddTraits(.isButton)
            if isOpen {
                openedText
            }
        }
    }

    @ViewBuilder
    private var openedText: some View {
        if entry.isBinary {
            SettingsBody(text: L10n.string("ios_about_licenses_binary_note"))
        }
        Text(verbatim: LicenseCatalog.text(for: entry) ?? entry.licenseName)
            .miffTextStyle(textBodyCaption)
            .foregroundColor(colorTextDescription.color)
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.md)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
    }
}
