import SwiftUI
import UIKit

/// Ouvrir quelque chose hors de l'app : une page web, l'application Mail, les réglages de l'iPhone. Toujours après un toucher de la
/// personne sur un bouton qui dit ce qui va s'ouvrir.
@MainActor
enum ExternalOpener {
    static func open(_ url: URL?) {
        guard let url else {
            return
        }
        UIApplication.shared.open(url)
    }

    /// Les réglages de l'iPhone pour Miff (c'est là que se changent la localisation et les notifications ; la langue se change dans l'app).
    static func openSystemSettings() {
        open(URL(string: UIApplication.openSettingsURLString))
    }
}

/// Une ligne de la liste des Réglages : une pastille bleu pâle avec l'icône, le titre, sa courte aide, et la flèche (maquettes
/// « réglages »). Toute la ligne est un bouton d'au moins 44 pt, lu comme UN élément par VoiceOver.
struct SettingsRow: View {
    let systemImage: String
    let title: String
    var subtitle: String?
    var hint = ""
    let action: () -> Void

    private var spokenLabel: String {
        [title, subtitle].compactMap { $0 }.joined(separator: ", ")
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                Image(systemName: systemImage)
                    .font(.system(size: Spacing.sectionIcon, weight: .regular))
                    .foregroundColor(colorActionPrimary.color)
                    .frame(width: Spacing.xl + Spacing.sm, height: Spacing.xl + Spacing.sm)
                    .background(
                        RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                            .fill(colorActionPrimaryTint20.color)
                    )
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: title)
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextForm.color)
                    if let subtitle {
                        Text(verbatim: subtitle)
                            .miffTextStyle(textBodyRegular14)
                            .foregroundColor(colorTextDescription.color)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                Image(systemName: "chevron.forward")
                    .foregroundColor(colorTextDescription.color)
                    .accessibilityHidden(true)
            }
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: Spacing.minTouchTarget + Spacing.md)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spokenLabel))
        .accessibilityHint(Text(verbatim: hint))
        .accessibilityAddTraits(.isButton)
    }
}

/// Une information en lecture seule : un intitulé et sa valeur (« Prénom », « Alex »).
struct SettingsValueRow: View {
    let label: String
    let value: String
    var spokenValue: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(verbatim: label)
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
            Text(verbatim: value)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(minHeight: Spacing.minTouchTarget, alignment: .leading)
                .padding(.horizontal, Spacing.md)
                .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
                .overlay(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
                )
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityValue(Text(verbatim: spokenValue ?? value))
    }
}

/// Un lien qui ouvre une page web ou l'application Mail : une ligne soulignée de 44 pt au moins, dont le nom dit où elle mène.
struct SettingsLinkRow: View {
    let title: String
    let spokenHint: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(verbatim: title)
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorActionPrimary.color)
                .underline()
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint(Text(verbatim: spokenHint))
        .accessibilityAddTraits(.isLink)
    }
}

/// Un titre de section dans un écran de Réglages.
struct SettingsSectionTitle: View {
    let key: String

    var body: some View {
        Text(verbatim: L10n.string(key))
            .miffTextStyle(textDisplayTitle)
            .foregroundColor(colorTextForm.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, Spacing.sm)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Un paragraphe d'explication, calme, en gris.
struct SettingsBody: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorTextDescription.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
    }
}
