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

/// Ce qui relance la lecture paresseuse : la page montrée, la liste des Miffs, et le Profil à l'écran ou non.
private struct CarouselLoadKey: Equatable {
    let isActive: Bool
    let page: Int
    let miffIds: [String]
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
    /// Le décalage de la rangée sous le doigt (déjà freiné au bout du carrousel). Un `@State` simple, remis à zéro AVEC le changement de page
    /// dans UNE seule transaction à la fin du geste : la carte ne revient jamais en arrière avant de glisser vers sa nouvelle place.
    @State private var dragX: CGFloat = 0
    /// L'axe décidé au premier mouvement du doigt (il ne change plus jusqu'à la fin du geste).
    @State private var axis = CarouselTouch.Phase.undecided
    /// Vrai pendant qu'un geste est en cours ; son retour à faux SANS fin de geste (appel, notification, geste du système) remet la rangée en place.
    @GestureState private var dragging = false
    @State private var prefetched: Int?
    /// Le doigt a dépassé le seuil pendant le geste en cours (même s'il est revenu) : ce n'est pas un toucher.
    @State private var exceededSlop = false
    @State private var opened = false
    @State private var cardHeight: CGFloat = 0
    /// La taille du texte avec laquelle `cardHeight` a été mesurée (la hauteur ne rétrécit pas tant qu'elle ne change pas).
    @State private var measuredTypeSize: DynamicTypeSize?
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
        .onPreferenceChange(CardHeightKey.self) { measured in
            let next = CarouselCardHeight.next(
                current: cardHeight, measured: measured, sameTextSize: measuredTypeSize == typeSize
            )
            if next != cardHeight {
                cardHeight = next
            }
            if measuredTypeSize != typeSize {
                measuredTypeSize = typeSize
            }
        }
        .task(id: CarouselLoadKey(isActive: isActive, page: current, miffIds: miffs.map { $0.id })) {
            await loadVisibleCard()
        }
        // Un geste interrompu (appel, notification, geste du système) ne passe pas par `onEnded` : la rangée revient en place.
        .onChange(of: dragging) { active in
            if !active {
                axis = .undecided
                settleBack()
            }
        }
    }

    // MARK: - Le pager

    private var pager: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            // La rangée est comparée à elle-même (`.equatable()`) : pendant un balayage, seul le décalage change, jamais les cartes.
            row(width: width)
                .equatable()
                .offset(x: -CGFloat(current) * (width + gap) + dragX)
                .frame(width: width, alignment: .leading)
                .contentShape(Rectangle())
                .simultaneousGesture(dragGesture(pageWidth: width))
        }
        // Avant la première mesure : une hauteur de départ (la carte la remplace dès qu'elle est mesurée).
        .frame(height: cardHeight > 0 ? cardHeight : Spacing.minTouchTarget * 2)
        // Les ombres des cartes ne sont pas coupées sur les bords ; les voisines, elles, le sont.
        .clipShape(Rectangle().inset(by: -shadowMargin))
    }

    private func row(width: CGFloat) -> CarouselCardsRow {
        CarouselCardsRow(
            miffIds: miffs.map { $0.id },
            names: miffs.map { $0.name },
            members: miffs.map { previews.state(for: $0.id) },
            count: count,
            current: current,
            width: width,
            gap: gap,
            cardHeight: cardHeight,
            maxVisible: typeSize.isAccessibilitySize ? MemberStack.maxVisibleLargeText : MemberStack.maxVisible,
            onOpenMiff: actions.onOpenMiff,
            onCreate: actions.onCreate
        )
    }

    // MARK: - Le geste

    /// UN seul geste pour toucher ET glisser (`CarouselTouch`) : les cartes n'ont aucun bouton, donc un glissement ne peut jamais ouvrir une
    /// carte. Sous le seuil de 10 pt, le relâchement est un toucher (ouvre la carte montrée) ; au-delà, c'est un glissement (horizontal : change
    /// de carte) ou un défilement de la page (vertical : rien ici). `simultaneousGesture` : le défilement vertical de l'écran continue de marcher.
    private func dragGesture(pageWidth: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($dragging) { _, state, _ in
                if !state {
                    state = true
                }
            }
            .onChanged { value in
                // Premier événement d'un nouveau geste (le doigt vient de se poser) : un geste précédent annulé ne laisse rien derrière lui.
                if value.translation == .zero {
                    exceededSlop = false
                    axis = .undecided
                }
                let phase = CarouselTouch.phase(translation: value.translation)
                if phase != .undecided && !exceededSlop {
                    exceededSlop = true
                }
                // L'axe est décidé au premier mouvement au-delà du seuil et ne change plus jusqu'à la fin du geste.
                if axis == .undecided && phase != .undecided {
                    axis = phase
                }
                if axis == .horizontal {
                    let next = resisted(value.translation.width)
                    if next != dragX {
                        dragX = next
                    }
                }
                if phase == .horizontal {
                    prefetchNeighbour(translation: value.translation.width)
                }
            }
            .onEnded { value in
                if prefetched != nil {
                    prefetched = nil
                }
                let moved = exceededSlop
                exceededSlop = false
                axis = .undecided
                if CarouselTouch.isTap(translation: value.translation, exceededSlop: moved) {
                    settleBack()
                    activateCurrentCard()
                    return
                }
                guard CarouselTouch.phase(translation: value.translation) == .horizontal else {
                    settleBack()
                    return
                }
                let target = CarouselPaging.settle(
                    current: current,
                    translation: value.translation.width,
                    predictedEnd: value.predictedEndTranslation.width,
                    pageWidth: pageWidth,
                    count: count
                )
                // UNE seule transaction : la nouvelle page ET le retour du décalage à zéro (jamais de retour en arrière avant de glisser).
                animate {
                    page = target
                    dragX = 0
                }
            }
    }

    /// Le décalage revient à zéro (un toucher, ou un geste vertical) ; sans effet s'il est déjà à zéro.
    private func settleBack() {
        if dragX != 0 {
            animate {
                dragX = 0
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
