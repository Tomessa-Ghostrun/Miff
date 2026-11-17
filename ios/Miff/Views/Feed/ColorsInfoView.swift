import SwiftUI

// « Les couleurs de ta Miff » (ticket COULEURS-INFO, COULEURS-2) : l'écran qui explique, calmement, ce que signifient le vert, l'orange, le rouge
// et le gris pour UNE PERSONNE (l'anneau de la photo, le liseré de la carte) et pour la MIFF (la bulle), ce qui change une couleur, et ce
// qu'une couleur ne dit PAS. Il s'ouvre par le petit bouton « i » au bas de la bulle du Feed (`BubbleInfoButton`).
//
// STATIQUE : aucune lecture de données, aucun réseau, aucun événement de statistiques, aucun état ; il se lit en mode avion et ne montre aucune
// donnée personnelle. Les durées viennent de `StatusThresholds` (voir `ColorsInfoContent`). Les illustrations sont dessinées avec les jetons du
// Feed (l'avatar de statut sans pictogramme, la tache de la bulle) : aucune image, aucune valeur de couleur écrite ici. La couleur n'est
// JAMAIS seule : chaque ligne dit son nom. Écran poussé plein écran (sans barre d'onglets), contenu défilant (`MiffFrame`).

struct ColorsInfoView: View {
    let onBack: () -> Void
    var content: ColorsInfoContent = .make()

    var body: some View {
        MiffFrame(title: content.title, onBack: onBack, usesCanvas: true) {
            Text(verbatim: content.intro)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                ForEach(Array(content.items.enumerated()), id: \.offset) { _, item in
                    ColorsInfoRow(item: item)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.bottom, Spacing.lg)
        }
    }
}

private struct ColorsInfoRow: View {
    let item: ColorsInfoItem

    var body: some View {
        switch item {
        case .heading(let text):
            Text(verbatim: text)
                .miffTextStyle(textDisplayLabel)
                .foregroundColor(colorTextForm.color)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, Spacing.md)
                .accessibilityAddTraits(.isHeader)
        case .note(let text):
            Text(verbatim: text)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .fixedSize(horizontal: false, vertical: true)
        case .person(let level, let title, let body):
            ColorLine(title: title, sentence: body) { PersonPicture(level: level) }
        case .miff(let level, let title, let body):
            ColorLine(title: title, sentence: body) { MiffPicture(level: level) }
        case .block(let title, let body):
            NamedText(title: title, sentence: body)
                .padding(.vertical, Spacing.xs)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: "\(title). \(body)"))
        case .line(let text):
            Text(verbatim: text)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextForm.color)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Le nom d'une couleur en gras, puis sa phrase.
private struct NamedText: View {
    let title: String
    let sentence: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(verbatim: title)
                .miffTextStyle(textBodyBold14)
                .foregroundColor(colorTextPrimary.color)
            Text(verbatim: sentence)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextForm.color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// Une ligne de couleur : l'illustration (décorative : VoiceOver ne la lit pas), le nom de la couleur en gras, puis une ou deux phrases. Toute la
/// ligne est lue d'UN bloc (« Vert. Tout va bien. La personne est… »). Aux grandes tailles de texte l'illustration passe AU-DESSUS du texte au
/// lieu de le rétrécir.
private struct ColorLine<Picture: View>: View {
    let title: String
    let sentence: String
    @ViewBuilder let picture: () -> Picture
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Group {
            if dynamicTypeSize >= ColorsInfoLayout.stackedFromSize {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    picture()
                    NamedText(title: title, sentence: sentence)
                }
            } else {
                HStack(alignment: .top, spacing: Spacing.md) {
                    picture()
                    NamedText(title: title, sentence: sentence)
                }
            }
        }
        .padding(.vertical, Spacing.xs)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(title). \(sentence)"))
    }
}

/// À partir de quelle taille de texte l'illustration passe au-dessus du texte, et la taille des illustrations.
enum ColorsInfoLayout {
    static let stackedFromSize: DynamicTypeSize = .xxxLarge
    static let pictureSize: CGFloat = 40
}

/// L'avatar de statut du Feed : un disque neutre et l'anneau du niveau, sans pictogramme ni initiales (décision du PM du 2026-11-06).
private struct PersonPicture: View {
    let level: StatusLevel

    var body: some View {
        FeedAvatar(jpeg: nil, initials: "", level: level, size: ColorsInfoLayout.pictureSize)
    }
}

/// Une petite tache à la forme de la bulle du Feed, de la teinte du niveau : ni texte ni pictogramme.
private struct MiffPicture: View {
    let level: StatusLevel

    var body: some View {
        FeedBubbleShape()
            .fill(level.tint)
            .frame(width: ColorsInfoLayout.pictureSize + Spacing.sm, height: ColorsInfoLayout.pictureSize)
            .accessibilityHidden(true)
    }
}
