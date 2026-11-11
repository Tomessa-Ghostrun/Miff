import SwiftUI

// CONFIDENTIALITÉ et COÛT, pour ce fichier : les photos des membres viennent de `MiffMemberPreviews` (la Miff affichée par le Feed : ce que
// le Feed a déjà lu ; les autres : UNE lecture ponctuelle à l'apparition de la carte, jamais d'écouteur). Rien n'est journalisé.

/// Les gestes du carrousel : ouvrir l'écran d'une Miff, créer une Miff (le choix « offre ou parcours » est fait par l'hôte).
struct ProfileCarouselActions {
    let onOpenMiff: (String) -> Void
    let onCreate: () -> Void
}

/// Ce que le carrousel montre : les Miffs, celle que le Feed affiche, et la carte du départ. Sans `initialPage`, le carrousel s'ouvre sur la
/// Miff que le Feed affiche (la première si elle est inconnue), à chaque ouverture du Profil, comme sur Android ; les écrans de démonstration
/// des captures en imposent une autre.
struct ProfileCarouselContent {
    let miffs: [MiffSummary]
    let activeId: String?
    var initialPage: Int?

    var startPage: Int {
        initialPage ?? CarouselPaging.startPage(miffIds: miffs.map { $0.id }, activeId: activeId)
    }
}

/// Le suivi d'un glissement : où est le doigt (déjà freiné au bout du carrousel) et l'axe décidé au premier mouvement. Un `@GestureState` :
/// il revient SEUL à zéro quand le geste se termine OU est annulé (appel, notification, geste du système), la carte ne reste jamais décalée.
private struct DragTrack {
    var translation: CGFloat = 0
    var horizontal: Bool?
}

/// Ce qui relance la lecture paresseuse : la page montrée, la liste des Miffs, et le Profil à l'écran ou non.
private struct CarouselLoadKey: Equatable {
    let isActive: Bool
    let page: Int
    let miffIds: [String]
}

/// La hauteur d'une carte (la plus grande gagne : toutes les cartes ont la même hauteur, quelle que soit la taille du texte).
private struct CardHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// « Mes Miffs » : un carrousel horizontal, une carte par Miff (nom, nombre de membres, miniatures des membres), et en DERNIER la carte
/// « Créer une Miff » (maquette `design/exports/profil/`). Un pager écrit à la main : iOS 16 n'a pas de défilement par pages en
/// SwiftUI, et `TabView` en pages ne s'adapte pas à la hauteur du texte. Les pastilles de pagination sont AUSSI l'élément que
/// VoiceOver règle d'un glissement vers le haut ou le bas (« Carte 1 sur 3 ») ; seule la carte montrée est lue.
struct ProfileMiffCarousel: View {
    let content: ProfileCarouselContent
    @ObservedObject var previews: MiffMemberPreviews
    /// Vrai quand l'onglet Profil est celui en usage : rien n'est lu avant.
    let isActive: Bool
    let actions: ProfileCarouselActions

    @State private var page: Int
    @GestureState private var track = DragTrack()
    @State private var prefetched: Int?
    /// Le doigt a dépassé le seuil pendant le geste en cours (même s'il est revenu) : ce n'est pas un toucher.
    @State private var exceededSlop = false
    @State private var opened = false
    @State private var cardHeight: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var typeSize

    init(
        content: ProfileCarouselContent,
        previews: MiffMemberPreviews,
        isActive: Bool,
        actions: ProfileCarouselActions
    ) {
        self.content = content
        self.previews = previews
        self.isActive = isActive
        self.actions = actions
        _page = State(initialValue: content.startPage)
    }

    private var miffs: [MiffSummary] {
        content.miffs
    }

    private var activeId: String? {
        content.activeId
    }

    /// L'espace entre deux cartes : plus grand que la marge de l'ombre, pour qu'une voisine ne dépasse jamais dans la marge.
    private let gap: CGFloat = 28
    private let shadowMargin: CGFloat = 14

    private var count: Int {
        CarouselPaging.pageCount(miffs: miffs.count)
    }

    private var current: Int {
        CarouselPaging.clamp(page, count: count)
    }

    var body: some View {
        VStack(spacing: Spacing.sm) {
            pager
            if count > 1 {
                CarouselDots(count: count, current: current, select: go(to:))
            }
        }
        .onPreferenceChange(CardHeightKey.self) { cardHeight = $0 }
        .task(id: CarouselLoadKey(isActive: isActive, page: current, miffIds: miffs.map { $0.id })) {
            await loadVisibleCard()
        }
    }

    // MARK: - Le pager

    private var pager: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            HStack(alignment: .top, spacing: gap) {
                ForEach(0..<count, id: \.self) { index in
                    card(at: index)
                        .frame(width: width)
                        // Seule la carte montrée est lue par VoiceOver ; les autres sont hors de l'écran.
                        .accessibilityHidden(index != current)
                }
            }
            .offset(x: -CGFloat(current) * (width + gap) + track.translation)
            .frame(width: width, alignment: .leading)
            .contentShape(Rectangle())
            .simultaneousGesture(dragGesture(pageWidth: width))
        }
        // Avant la première mesure : une hauteur de départ (la carte la remplace dès qu'elle est mesurée).
        .frame(height: cardHeight > 0 ? cardHeight : Spacing.minTouchTarget * 2)
        // Les ombres des cartes ne sont pas coupées sur les bords ; les voisines, elles, le sont.
        .clipShape(Rectangle().inset(by: -shadowMargin))
    }

    private func card(at index: Int) -> some View {
        // « Carte 2 sur 3 » : la position est dite avec la carte, un lecteur d'écran n'a pas à la deviner.
        let position = L10n.format("profile_tab_carousel_page_a11y", index + 1, count)
        return Group {
            if index < miffs.count {
                MiffPagerCard(
                    name: miffs[index].name,
                    members: previews.state(for: miffs[index].id),
                    maxVisible: typeSize.isAccessibilitySize ? MemberStack.maxVisibleLargeText : MemberStack.maxVisible,
                    position: position,
                    action: { actions.onOpenMiff(miffs[index].id) }
                )
            } else {
                CreateMiffCard(position: position, action: actions.onCreate)
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

    // MARK: - Le geste

    /// UN seul geste pour toucher ET glisser (`CarouselTouch`) : les cartes n'ont aucun bouton, donc un glissement ne peut jamais ouvrir une
    /// carte. Sous le seuil de 10 pt, le relâchement est un toucher (ouvre la carte montrée) ; au-delà, c'est un glissement (horizontal : change
    /// de carte) ou un défilement de la page (vertical : rien ici). `simultaneousGesture` : le défilement vertical de l'écran continue de marcher.
    private func dragGesture(pageWidth: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($track) { value, state, _ in
                if state.horizontal == nil {
                    switch CarouselTouch.phase(translation: value.translation) {
                    case .horizontal:
                        state.horizontal = true
                    case .vertical:
                        state.horizontal = false
                    case .undecided:
                        break
                    }
                }
                if state.horizontal == true {
                    state.translation = resisted(value.translation.width)
                }
            }
            .onChanged { value in
                // Premier événement d'un nouveau geste (le doigt vient de se poser) : un geste précédent annulé ne laisse rien derrière lui.
                if value.translation == .zero {
                    exceededSlop = false
                }
                let phase = CarouselTouch.phase(translation: value.translation)
                if phase != .undecided {
                    exceededSlop = true
                }
                if phase == .horizontal {
                    prefetchNeighbour(translation: value.translation.width)
                }
            }
            .onEnded { value in
                prefetched = nil
                let moved = exceededSlop
                exceededSlop = false
                if CarouselTouch.isTap(translation: value.translation, exceededSlop: moved) {
                    activateCurrentCard()
                    return
                }
                guard CarouselTouch.phase(translation: value.translation) == .horizontal else {
                    return
                }
                let target = CarouselPaging.settle(
                    current: current,
                    translation: value.translation.width,
                    predictedEnd: value.predictedEndTranslation.width,
                    pageWidth: pageWidth,
                    count: count
                )
                animate {
                    page = target
                }
            }
    }

    /// Le toucher : ouvre la carte montrée (la Miff, ou « Créer une Miff »).
    private func activateCurrentCard() {
        if current < miffs.count {
            actions.onOpenMiff(miffs[current].id)
        } else {
            actions.onCreate()
        }
    }

    /// Au bout du carrousel, la carte résiste (elle suit le doigt à moitié) au lieu de partir dans le vide.
    private func resisted(_ translation: CGFloat) -> CGFloat {
        let blocked = CarouselPaging.neighbour(current: current, translation: translation, count: count) == nil
        return blocked ? translation / 3 : translation
    }

    /// Un glissement vers une carte qui n'a pas encore été lue : la lecture commence maintenant (la carte apparaît), une seule fois.
    private func prefetchNeighbour(translation: CGFloat) {
        let next = CarouselPaging.neighbour(current: current, translation: translation, count: count)
        guard isActive, let neighbour = next, neighbour != prefetched, neighbour < miffs.count else {
            return
        }
        prefetched = neighbour
        let miff = miffs[neighbour]
        let isActiveMiff = miff.id == activeId
        let previews = self.previews
        Task { await previews.loadIfNeeded(miffId: miff.id, isActive: isActiveMiff) }
    }

    private func go(to target: Int) {
        animate {
            page = CarouselPaging.clamp(target, count: count)
        }
    }

    private func animate(_ change: () -> Void) {
        if reduceMotion {
            change()
        } else {
            withAnimation(.easeOut(duration: 0.25), change)
        }
    }

    // MARK: - La lecture paresseuse

    /// À l'ouverture du Profil : une nouvelle « ouverture » (chaque Miff peut être lue une fois), puis la carte montrée est lue si besoin.
    private func loadVisibleCard() async {
        guard isActive else {
            opened = false
            return
        }
        if !opened {
            opened = true
            previews.beginOpening()
            // Le Profil s'ouvre sur la Miff que le Feed affiche (elle a pu changer depuis la dernière ouverture).
            if content.initialPage == nil {
                page = content.startPage
            }
        }
        previews.retain(only: Set(miffs.map { $0.id }))
        guard current < miffs.count else {
            return
        }
        let miff = miffs[current]
        await previews.loadIfNeeded(miffId: miff.id, isActive: miff.id == activeId)
    }
}

// MARK: - Les cartes

/// Le fond d'une carte : blanc, arrondi, avec l'ombre des cartes du Profil.
private struct CardSurface: ViewModifier {
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
private struct CardTile: View {
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
private struct MiffPagerCard: View {
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
private struct CreateMiffCard: View {
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

// MARK: - Les miniatures

/// La rangée de miniatures : la photo de chaque membre (ou ses initiales), « +N » pour les suivants. Décorative : le nombre de membres
/// est dit par le texte de la carte. Pendant la lecture, la place est gardée (des pastilles pâles) pour que la carte ne saute pas.
private struct MemberThumbsRow: View {
    let members: MiffCardMembers
    let maxVisible: Int
    @ScaledMetric(relativeTo: .body) private var side: CGFloat = 28

    private var diameter: CGFloat {
        min(side, 44)
    }

    var body: some View {
        switch members {
        case .known(let preview):
            thumbs(of: preview)
        case .loading:
            HStack(spacing: Spacing.xs) {
                ForEach(0..<3, id: \.self) { _ in
                    Circle()
                        .fill(colorActionPrimaryTint20.color)
                        .frame(width: diameter, height: diameter)
                }
            }
            .accessibilityHidden(true)
        case .unavailable:
            EmptyView()
        }
    }

    private func thumbs(of preview: MemberPreview) -> some View {
        let layout = MemberStack.layout(total: preview.count, maxVisible: maxVisible)
        return HStack(spacing: Spacing.xs) {
            ForEach(Array(preview.thumbs.prefix(layout.visible)), id: \.uid) { thumb in
                MiffAvatar(jpeg: thumb.avatarJpeg, initials: thumb.initials, size: diameter)
                    .overlay(Circle().stroke(colorActionPrimary.color, lineWidth: strokeHairline))
            }
            if layout.extra > 0 {
                Text(verbatim: "+\(layout.extra)")
                    .miffTextStyle(textCaptionBold12)
                    .foregroundColor(colorActionPrimary.color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .frame(width: diameter, height: diameter)
                    .background(Circle().fill(colorActionPrimaryTint20.color))
                    .overlay(Circle().stroke(colorActionPrimary.color, lineWidth: strokeHairline))
            }
        }
        .accessibilityHidden(true)
    }
}

// MARK: - La pagination

/// Les pastilles (la page courante est plus LONGUE : une forme, pas seulement une couleur). C'est l'élément que VoiceOver règle pour changer
/// de carte SANS balayage : « Carte 1 sur 3 », glissement vers le haut ou le bas du doigt.
private struct CarouselDots: View {
    let count: Int
    let current: Int
    let select: (Int) -> Void

    private var spokenPage: String {
        L10n.format("profile_tab_carousel_page_a11y", current + 1, count)
    }

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(0..<count, id: \.self) { position in
                Capsule()
                    .fill((position == current ? colorActionPrimary : colorActionPrimaryTint20).color)
                    .frame(width: position == current ? 22 : 8, height: 8)
            }
        }
        .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spokenPage))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment:
                select(current + 1)
            case .decrement:
                select(current - 1)
            @unknown default:
                break
            }
        }
        .accessibilityAction(named: Text("profile_tab_carousel_previous_a11y")) { select(current - 1) }
        .accessibilityAction(named: Text("profile_tab_carousel_next_a11y")) { select(current + 1) }
    }
}
