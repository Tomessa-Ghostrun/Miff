import SwiftUI

// Les petites pièces des écrans de la suppression du compte (AUTH-5c). Tutoiement, aucun mot alarmiste (critère SC5) : l'action
// destructive est dite par les mots, pas par la seule couleur.

/// La pastille bleu pâle qui porte une icône (cartes d'information, cadenas de la ré-authentification). Décorative : la phrase à côté dit
/// tout.
struct DeleteIconTile: View {
    let systemImage: String

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 18, weight: .regular))
            .foregroundColor(colorActionPrimary.color)
            .frame(width: Spacing.xl + Spacing.sm, height: Spacing.xl + Spacing.sm)
            .background(
                RoundedRectangle(cornerRadius: radiusSm, style: .continuous).fill(colorActionPrimaryTint20.color)
            )
            .accessibilityHidden(true)
    }
}

/// Une ligne d'une carte d'information : une icône et UNE phrase.
struct DeleteInfoRow: Hashable {
    let systemImage: String
    let text: String
}

/// Une carte blanche où chaque ligne a sa pastille et sa phrase. Chaque ligne est lue d'un bloc par VoiceOver.
struct DeleteInfoCard: View {
    let rows: [DeleteInfoRow]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            ForEach(rows, id: \.self) { row in
                HStack(alignment: .top, spacing: Spacing.sm + Spacing.xs) {
                    DeleteIconTile(systemImage: row.systemImage)
                    Text(verbatim: row.text)
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextForm.color)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
    }
}

/// Un encadré d'information (bleu pâle, jamais rouge) : « Ce n'est pas réversible… ». La première phrase est en gras quand `boldLead`.
struct DeleteInfoBox: View {
    let text: String
    let boldLead: Bool

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm + Spacing.xs) {
            Image(systemName: "info.circle")
                .font(.system(size: 20, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            lines
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint10.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorActionPrimaryTint20.color, lineWidth: strokeHairline)
        )
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var lines: some View {
        if boldLead, let end = text.firstIndex(of: ".") {
            let lead = String(text[...end])
            let rest = String(text[text.index(after: end)...])
            (Text(verbatim: lead).bold() + Text(verbatim: rest))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        } else {
            Text(verbatim: text)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Une phrase qui contient l'adresse de contact : toute la ligne ouvre l'application Mail (rien n'est envoyé tout seul). Une seule cible
/// d'au moins 44 pt.
struct DeleteContactSentence: View {
    let text: String
    let systemImage: String
    var hint = ""

    var body: some View {
        Button {
            ExternalOpener.open(ContactLinks.supportMailURL())
        } label: {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: systemImage)
                    .foregroundColor(colorTextForm.color)
                    .padding(.top, 1)
                    .accessibilityHidden(true)
                Text(verbatim: text)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(minHeight: Spacing.minTouchTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint(Text(verbatim: hint))
    }
}

/// Le lien discret de texte « Supprimer mon compte » : jamais un bouton plein, toujours à côté de « Se déconnecter » ou dessous. Il
/// ouvre seulement l'écran d'information (rien n'est écrit d'ici). Apple 5.1.1(v) : la suppression doit pouvoir se lancer dans l'app.
struct DeleteAccountLink: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("settings_delete_entry")
                .miffTextStyle(textBodyLarge)
                .foregroundColor(colorTextError.color)
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
        }
        .accessibilityHint(Text(verbatim: L10n.string("ios_settings_delete_entry_hint")))
    }
}

/// Un lien de texte bleu en gras, de 44 pt au moins (« Annuler », « Plus tard »).
struct DeleteTextAction: View {
    let key: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(key)
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorActionPrimary.color)
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
        }
    }
}
