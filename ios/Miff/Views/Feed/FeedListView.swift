import SwiftUI

// La liste du Feed : les mentions, la bulle, les cartes des membres (moi en dernier, D43), puis « Ajouter un proche ». Une seule colonne,
// un défilement vertical, aucune liste imbriquée. Le contenu commence SOUS l'en-tête (marge haute = sa hauteur) et passe sous la barre
// d'onglets (la zone sûre qu'elle déclare garde la marge basse). Elle dit à l'écran où est le haut du contenu (pour l'en-tête qui se
// masque) et accepte « tirer pour actualiser ».

/// Ce que font les boutons de la liste. Le Feed n'écrit rien : il confie aux applications du système ou ouvre un autre écran.
struct FeedListActions {
    let card: MemberCardActions
    let bubble: BubbleActions
    let onRetry: () -> Void
    let onReviewAgreement: () -> Void
    let onRefresh: () async -> Void
    /// La carte « deuxième chance » des notifications (NOTIF-1I).
    let onSecondChanceActivate: () -> Void
    let onSecondChanceDismiss: () -> Void
}

/// Où est le haut du contenu sur l'écran (lu par l'écran pour déplacer l'en-tête ; seuls les écarts comptent).
private struct ContentTopKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct FeedListView: View {
    let feed: FeedUiState
    let ready: DirectoryReady
    /// La pop-in « Pas de connexion » du Feed : fermée par la personne, la page dit la même chose à la place du squelette.
    @ObservedObject var offlineRetry: OfflineRetryModel
    let locate: LocateUiState
    let texts: FeedTexts
    /// L'en-tête est dessiné par-dessus la liste : la marge haute du contenu, MESURÉE (`FeedTopInset`), pour que la première carte commence dessous.
    let topInset: CGFloat
    /// Le mouvement de la bulle n'existe que si l'onglet est en usage et l'app au premier plan.
    let isActive: Bool
    let agreementWaiting: Bool
    /// La carte « Un rappel si tu oublies de donner de tes nouvelles ? » est due (une seule fois, après la première update).
    let secondChanceDue: Bool
    let scrollToTop: Int
    let actions: FeedListActions
    let onContentMoved: (CGFloat) -> Void
    /// La distance défilée lue directement sur la vue de défilement (voir `ScrollDistanceReader`).
    let onScrolled: (CGFloat) -> Void
    /// L'origine du contenu de la vue de défilement dans la fenêtre (voir `FeedTopInset`).
    let onOrigin: (CGFloat) -> Void
    /// NOTIF-P1I : la carte de ce proche est amenée à l'écran (un toucher sur une ligne « Depuis ta dernière visite »).
    var focus: FeedFocus?
    var onFocusHandled: (FeedFocus) -> Void = { _ in }
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let topId = "feed-top"
    /// Combien de temps le Feed attend que la Miff d'une ligne touchée soit celle qu'il montre, avant d'oublier la demande (jamais une erreur).
    private static let focusWaitNanos: UInt64 = 10_000_000_000

    private static func cardId(_ uid: String) -> String {
        "feed-card-\(uid)"
    }

    /// Ce qui relance la recherche de la carte : la demande, la Miff montrée et ses cartes.
    private struct FocusKey: Equatable {
        let focus: FeedFocus?
        let miffId: String?
        let cardUids: [String]
    }

    private var focusKey: FocusKey {
        FocusKey(focus: focus, miffId: content?.miffId, cardUids: content?.cards.map { $0.member.uid } ?? [])
    }

    /// Amène la carte du proche à l'écran quand la Miff demandée est celle du Feed ; sinon attend (10 s au plus). La demande est ensuite
    /// oubliée, qu'une carte ait été trouvée ou non (un proche parti de la Miff : le Feed reste tel quel).
    private func bringIntoView(_ proxy: ScrollViewProxy) async {
        guard let focus else {
            return
        }
        guard let content, content.miffId == focus.miffId else {
            try? await Task.sleep(nanoseconds: Self.focusWaitNanos)
            if !Task.isCancelled {
                onFocusHandled(focus)
            }
            return
        }
        if content.cards.contains(where: { $0.member.uid == focus.uid }) {
            // Un instant pour que la liste soit posée (l'onglet vient peut-être d'apparaître).
            try? await Task.sleep(nanoseconds: 150_000_000)
            guard !Task.isCancelled else {
                return
            }
            if reduceMotion {
                proxy.scrollTo(Self.cardId(focus.uid), anchor: .center)
            } else {
                withAnimation { proxy.scrollTo(Self.cardId(focus.uid), anchor: .center) }
            }
        }
        onFocusHandled(focus)
    }

    private var content: FeedContent? {
        if case .ready(let value) = feed {
            return value
        }
        return nil
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    topAnchor
                    LazyVStack(spacing: 14) {
                        items
                    }
                    .padding(.horizontal, Spacing.md)
                    .padding(.bottom, Spacing.md)
                }
                .background(ScrollDistanceReader(onChange: onScrolled, onOrigin: onOrigin))
            }
            .onPreferenceChange(ContentTopKey.self) { onContentMoved($0) }
            .refreshable { await actions.onRefresh() }
            .task(id: focusKey) { await bringIntoView(proxy) }
            // Retour en haut (le logo) : sans animation avec « Réduire les animations ».
            .onChange(of: scrollToTop) { _ in
                if reduceMotion {
                    proxy.scrollTo(Self.topId, anchor: .top)
                } else {
                    withAnimation { proxy.scrollTo(Self.topId, anchor: .top) }
                }
            }
        }
        .accessibilityAction(named: Text("feed_refresh_a11y")) {
            Task { await actions.onRefresh() }
        }
    }

    /// La marge haute du contenu (l'en-tête mesuré + `FeedTopInset.gapBelowHeader`), tout en haut : son haut est le haut du contenu, sa position
    /// dit où est le contenu, et « retour en haut » (le logo) y ramène la liste AVEC la marge, jamais la première carte sous l'en-tête.
    private var topAnchor: some View {
        Color.clear
            .frame(height: topInset + FeedTopInset.gapBelowHeader)
            .id(Self.topId)
            .background(
                GeometryReader { proxy in
                    Color.clear.preference(key: ContentTopKey.self, value: proxy.frame(in: .global).minY)
                }
            )
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var items: some View {
        if ready.fromCache && content == nil {
            MiffNotice(text: L10n.string("feed_offline_note"))
        }
        if agreementWaiting {
            // « Pas maintenant » a été choisi : une carte permanente dit la vérité (rien n'est partagé), calmement, avec l'unique chemin de retour.
            AgreementCard(
                message: L10n.format("miff_waiting_feed_body", ready.active?.name ?? ""),
                onReview: actions.onReviewAgreement
            )
        }
        if secondChanceDue {
            NotificationSecondChanceCard(
                onActivate: actions.onSecondChanceActivate,
                onDismiss: actions.onSecondChanceDismiss
            )
        }
        switch feed {
        case .loading:
            if offlineRetry.isInlineVisible {
                OfflineInlineNote(model: offlineRetry)
            } else if !agreementWaiting {
                FeedSkeleton()
            }
        case .unavailable:
            if offlineRetry.isInlineVisible {
                OfflineInlineNote(model: offlineRetry)
            } else {
                unavailable
            }
        case .ready(let content):
            readyItems(content)
        }
    }

    private var unavailable: some View {
        VStack(spacing: Spacing.sm) {
            MiffNotice(text: L10n.string("feed_unavailable"))
            Button(action: actions.onRetry) {
                Text("auth_retry_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        }
    }

    @ViewBuilder
    private func readyItems(_ content: FeedContent) -> some View {
        ForEach(Array(content.banners.enumerated()), id: \.offset) { _, banner in
            FeedBannerView(banner: banner, texts: texts)
        }
        BubbleView(
            bubble: content.bubble,
            announceKey: content.announceKey,
            texts: texts,
            isActive: isActive,
            actions: actions.bubble,
            travelNote: content.travelNote
        )
        // COMPTE À REBOURS (COUNTDOWN-I) : juste sous la bulle de la Miff, au-dessus des cartes ; rien quand il n'y a pas lieu de l'afficher.
        CountdownSlot(isActive: isActive)
        ForEach(content.cards, id: \.member.uid) { card in
            MemberCardView(
                card: card,
                texts: texts,
                locating: isLocating(card.member.uid),
                actions: actions.card,
                photoContext: PhotoSlotContext(miffId: content.miffId, offline: content.isOffline, active: isActive)
            )
            .id(Self.cardId(card.member.uid))
        }
        addLovedOne(content)
    }

    private func isLocating(_ uid: String) -> Bool {
        if case .loading(let locatingUid) = locate {
            return locatingUid == uid
        }
        return false
    }

    /// « Ajouter un proche » : la feuille de partage du système (nom, code, lien) ; à 15 membres, la note « bien remplie ».
    @ViewBuilder
    private func addLovedOne(_ content: FeedContent) -> some View {
        if content.isFull {
            FeedFullNote()
        } else {
            ShareLink(item: MiffSharing.message(miffName: content.miffName, code: content.joinCode)) {
                MiffPrimaryLabel(key: "feed_add_loved_one")
            }
            .buttonStyle(.plain)
        }
    }
}
