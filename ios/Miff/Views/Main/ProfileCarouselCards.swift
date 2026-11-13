import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : les photos des membres viennent de `MiffMemberPreviews`. Rien n'est journalisé.

/// La hauteur d'une carte (la plus grande gagne : toutes les cartes ont la même hauteur, quelle que soit la taille du texte).
struct CardHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// La rangée des cartes du carrousel, SANS son décalage : elle ne dépend que de ses données (noms, membres, page, largeur, hauteur) et se
/// compare à elle-même (`Equatable`, posée avec `.equatable()`). Pendant un balayage le carrousel se redessine à chaque image (la position du doigt) :
/// la rangée, elle, n'est PAS réévaluée (ni ses photos, ni ses ombres, ni ses mesures) tant que ses données ne changent pas. Les gestes
/// (fermetures) ne comptent pas dans la comparaison : ils ne dépendent que des identifiants, comparés.
struct CarouselCardsRow: View, Equatable {
    let miffIds: [String]
    let names: [String]
    let members: [MiffCardMembers]
    let count: Int
    let current: Int
    let width: CGFloat
    let gap: CGFloat
    let cardHeight: CGFloat
    let maxVisible: Int
    let onOpenMiff: (String) -> Void
    let onCreate: () -> Void

    static func == (lhs: CarouselCardsRow, rhs: CarouselCardsRow) -> Bool {
        lhs.miffIds == rhs.miffIds && lhs.names == rhs.names && lhs.members == rhs.members && lhs.count == rhs.count
            && lhs.current == rhs.current && lhs.width == rhs.width && lhs.gap == rhs.gap
            && lhs.cardHeight == rhs.cardHeight && lhs.maxVisible == rhs.maxVisible
    }

    var body: some View {
        HStack(alignment: .top, spacing: gap) {
            ForEach(0..<count, id: \.self) { index in
                card(at: index)
                    .frame(width: width)
                    // Seule la carte montrée est lue par VoiceOver ; les autres sont hors de l'écran.
                    .accessibilityHidden(index != current)
            }
        }
    }

    private func card(at index: Int) -> some View {
        // « Carte 2 sur 3 » : la position est dite avec la carte, un lecteur d'écran n'a pas à la deviner.
        let position = L10n.format("profile_tab_carousel_page_a11y", index + 1, count)
        return Group {
            if index < miffIds.count {
                MiffPagerCard(
                    name: names[index],
                    members: members[index],
                    maxVisible: maxVisible,
                    position: position,
                    action: { onOpenMiff(miffIds[index]) }
                )
            } else {
                CreateMiffCard(position: position, action: onCreate)
            }
        }
        // La carte prend sa hauteur naturelle (jamais tronquée), mesurée ; puis toutes prennent la plus grande.
        .fixedSize(horizontal: false, vertical: true)
        .background(
            GeometryReader { proxy in
                Color.clear.preference(key: CardHeightKey.self, value: proxy.size.height)
            }
        )
        .frame(minHeight: cardHeight, alignment: .top)
    }
}

// MARK: - Les cartes

/// Le fond d'une carte : blanc, arrondi, avec l'ombre des cartes du Profil.
struct CardSurface: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: Spacing.minTouchTarget)
            // L'ombre est celle du FOND seulement : posée sur toute la carte, elle doublerait aussi le texte et les miniatures.
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .fill(colorBgSurface.color)
                    .miffShadow(shadowDefault)
            )
            .contentShape(Rectangle())
    }
}

/// La pastille bleue pâle à gauche d'une carte (le pictogramme « proches » de la maquette).
struct CardTile: View {
    let symbol: String
    @ScaledMetric(relativeTo: .body) private var scaled: CGFloat = 40

    /// Grandit avec le texte, jusqu'à 60 pt : la pastille ne mange pas la place du nom.
    private var side: CGFloat {
        min(scaled, 60)
    }

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: side * 0.5, weight: .regular))
            .foregroundColor(colorActionPrimary.color)
            .frame(width: side, height: side)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint20.color)
            )
            .accessibilityHidden(true)
    }
}

/// La carte d'une Miff : son nom, « N membres » (en bleu), les miniatures des membres, la flèche. Ouvre l'écran de la Miff.
struct MiffPagerCard: View {
    let name: String
    let members: MiffCardMembers
    let maxVisible: Int
    let position: String
    let action: () -> Void

    private var memberCount: Int? {
        if case .known(let preview) = members {
            return preview.count
        }
        return nil
    }

    private var spokenLabel: String {
        let open = L10n.format("profile_tab_miff_open_a11y", name)
        guard let memberCount else {
            return "\(open), \(position)"
        }
        return "\(open), \(L10n.plural("profile_tab_miff_members", count: memberCount)), \(position)"
    }

    var body: some View {
        // Aucun `Button` : le toucher est décidé par le geste unique du carrousel (voir `dragGesture`). VoiceOver ouvre la carte par
        // l'action par défaut (double toucher).
        HStack(spacing: Spacing.md) {
            CardTile(symbol: "person.2")
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(verbatim: name)
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextForm.color)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                subtitle
                MemberThumbsRow(members: members, maxVisible: maxVisible)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.forward")
                .foregroundColor(colorTextDescription.color)
                .accessibilityHidden(true)
        }
        .modifier(CardSurface())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spokenLabel))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(.default, action)
    }

    /// « 4 membres » quand on le sait ; sinon (lecture en cours ou impossible) la courte aide d'avant : jamais un message d'erreur.
    @ViewBuilder
    private var subtitle: some View {
        if let memberCount {
            Text(verbatim: L10n.plural("profile_tab_miff_members", count: memberCount))
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorActionPrimary.color)
        } else {
            Text("profile_tab_miff_hint")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
        }
    }
}

/// La dernière carte : « Créer une Miff ». Pour un compte gratuit qui a déjà une Miff, l'hôte ouvre l'offre au lieu du parcours.
struct CreateMiffCard: View {
    let position: String
    let action: () -> Void

    var body: some View {
        // Aucun `Button` (voir `MiffPagerCard`) : jamais d'ouverture au relâchement d'un glissement.
        HStack(spacing: Spacing.md) {
            CardTile(symbol: "plus")
            Text("profile_tab_miff_create")
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextForm.color)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.forward")
                .foregroundColor(colorTextDescription.color)
                .accessibilityHidden(true)
        }
        .modifier(CardSurface())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(L10n.string("profile_tab_miff_create")), \(position)"))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(.default, action)
    }
}
