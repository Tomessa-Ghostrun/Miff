import SwiftUI

// Le détail de la zone de danger (ticket §10.2) : un lieu par ligne (emoji de la catégorie, nom, pourcentage), dont la somme égale la ligne
// de la carte. Ouvert au toucher de la ligne « Zone de danger » ; fermé par son bouton ou en tirant la feuille vers le bas (le geste du
// système). Ton calme : aucun fond rouge, aucune couleur d'alerte, aucune phrase d'origine (« qui a défini cette zone » est volontairement
// absente). Les noms de lieux ne sont dits qu'à l'écran et au lecteur d'écran.

struct TimeShareDangerSheet: View {
    let placesCount: Int
    let details: [TimeShareDangerDetail]
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack(alignment: .center, spacing: Spacing.sm) {
                Text(verbatim: L10n.plural("profile_timeshare_danger_zones", count: placesCount))
                    .miffTextStyle(textDisplayTitle)
                    .foregroundColor(colorTextForm.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(colorTextForm.color)
                        .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Text("profile_timeshare_detail_close"))
            }
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    ForEach(details, id: \.placeId) { detail in
                        row(detail)
                    }
                }
            }
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(colorBgCanvas.color.ignoresSafeArea())
    }

    private func row(_ detail: TimeShareDangerDetail) -> some View {
        HStack(alignment: .center, spacing: Spacing.md) {
            PlaceCategoryIcon(category: detail.category, size: 44)
            Text(verbatim: detail.name)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(verbatim: TimeShareTexts.percent(of: detail))
                .monospacedDigit()
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextForm.color)
                .fixedSize(horizontal: true, vertical: false)
        }
        .padding(Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: TimeShareTexts.spokenDetail(detail)))
    }
}
