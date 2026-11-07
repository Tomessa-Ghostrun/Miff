import SwiftUI

// La feuille « Dernière position connue de {Prénom} » (maquette 08, ticket §5.6). Elle montre d'abord la DATE de la mesure, dit que ce
// n'est pas une position en direct, et confie les COORDONNÉES SEULES à Plans : aucune carte n'est dessinée ici, le Feed ne garde rien
// une fois la feuille fermée. Six réponses, toutes calmes (jamais le mot « erreur », jamais « quota ») : récente, ancienne (la date
// seulement, aucune coordonnée, aucun bouton), introuvable, refusée (UN message pour tous les refus : personne n'apprend qui a
// arrêté de partager), hors connexion, indisponible. Les coordonnées ne vivent dans `result` que tant que la feuille est ouverte.

struct LocateSheet: View {
    let firstName: String
    let result: LocateResult
    let texts: FeedTexts
    /// Donne les coordonnées à Plans ; dit ensuite (vrai/faux) si une application les a prises.
    let onOpenMap: (PositionFix, @escaping (Bool) -> Void) -> Void
    let onDismiss: () -> Void
    @State private var mapUnavailable = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                content
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(colorBgSurface.color.ignoresSafeArea())
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder
    private var content: some View {
        switch result {
        case .recent(let fix, let measuredAt, let age):
            title
            Text(verbatim: texts.locateRecent(measuredAt: measuredAt, age: age))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextPrimary.color)
            InfoLine(symbol: "eye", text: L10n.format("feed_locate_trust", firstName))
            if mapUnavailable {
                InfoLine(symbol: "info.circle", text: L10n.string("feed_action_unavailable"))
            }
            Button {
                onOpenMap(fix) { accepted in
                    mapUnavailable = !accepted
                }
            } label: {
                Text("feed_locate_open")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            closeButton
        case .old(let measuredAt):
            title
            Message(symbol: "clock", text: texts.locateOld(measuredAt))
            closeButton
        case .notFound:
            Message(symbol: "mappin.slash", text: L10n.format("feed_locate_not_found", firstName))
            closeButton
        case .denied:
            Message(symbol: "lock", text: L10n.format("feed_locate_denied", firstName))
            closeButton
        case .offline:
            Message(symbol: "wifi.slash", text: L10n.string("feed_locate_offline"))
            closeButton
        case .unavailable:
            Message(symbol: "hourglass", text: L10n.string("feed_locate_unavailable"))
            closeButton
        }
    }

    private var title: some View {
        Text(verbatim: texts.locateTitle(firstName))
            .miffTextStyle(textDisplayTitle)
            .foregroundColor(colorTextPrimary.color)
            .accessibilityAddTraits(.isHeader)
    }

    private var closeButton: some View {
        Button(action: onDismiss) {
            Text("feed_locate_close")
        }
        .buttonStyle(.miffSecondaryFullWidth)
    }
}

/// Un pictogramme dans un disque pâle et une phrase : la façon dont une réponse calme se dit.
private struct Message: View {
    let symbol: String
    let text: String

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: symbol)
                .font(.system(size: 20))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .background(Circle().fill(colorActionPrimaryTint10.color))
                .accessibilityHidden(true)
            Text(verbatim: text)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextPrimary.color)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct InfoLine: View {
    let symbol: String
    let text: String

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: symbol)
                .font(.system(size: 16))
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            Text(verbatim: text)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Spacing.sm + Spacing.xs)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint5.color))
    }
}
