import SwiftUI

// La légende de la carte « Répartition de ton temps » : une ligne par tranche, dans le même ordre que l'anneau : pastille de couleur, emoji
// (ou pictogramme neutre), nom, pourcentage. Le NOM de la catégorie est toujours écrit (jamais l'emoji seul) ; l'emoji est celui de la liste
// des lieux (`PlaceCategory.emoji`, table `docs/contracts/place-categories.md`, critère TS-19), décoratif pour le lecteur d'écran.

/// Le repère d'une ligne : l'emoji de la catégorie du lieu, ou un pictogramme neutre (lieu sans catégorie, « {n} autres lieux »,
/// « Ailleurs ») ; l'icône d'avertissement de l'app pour la zone de danger.
struct TimeShareRowGlyph: View {
    let kind: TimeShareRowKind
    let category: PlaceCategory?

    var body: some View {
        glyph
            .frame(minWidth: 24)
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var glyph: some View {
        switch kind {
        case .place:
            if let category {
                Text(verbatim: category.emoji)
                    .miffTextStyle(textBodyRegular)
            } else {
                Image(systemName: "mappin.and.ellipse")
                    .foregroundColor(colorActionPrimary.color)
            }
        case .otherPlaces:
            Image(systemName: "ellipsis.circle")
                .foregroundColor(colorTextDescription.color)
        case .danger:
            Image(systemName: PlaceType.risk.symbol)
                .foregroundColor(colorChartDanger.color)
        case .elsewhere:
            Image(systemName: "mappin.slash")
                .foregroundColor(colorTextDescription.color)
        }
    }
}

/// Une ligne de la légende. `opensDetail` : la ligne de la zone de danger, qui ouvre le détail au toucher (une flèche le montre).
struct TimeShareLegendRow: View {
    let row: TimeShareRow
    let style: TimeShareSliceStyle
    var opensDetail = false

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.sm) {
            TimeShareSwatch(style: style)
            TimeShareRowGlyph(kind: row.kind, category: row.category)
            Text(verbatim: TimeShareTexts.name(of: row))
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextForm.color)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(verbatim: TimeShareTexts.percent(of: row))
                .monospacedDigit()
                .miffTextStyle(textBodyBold14)
                .foregroundColor(colorTextForm.color)
                .fixedSize(horizontal: true, vertical: false)
            if opensDetail {
                Image(systemName: "chevron.forward")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(colorTextDescription.color)
                    .accessibilityHidden(true)
            }
        }
        .frame(minHeight: opensDetail ? Spacing.minTouchTarget : 0)
        .contentShape(Rectangle())
    }
}
