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
    let locate: LocateUiState
    let texts: FeedTexts
    /// L'en-tête est dessiné par-dessus la liste : sa hauteur est la marge haute du contenu, pour que la première carte commence dessous.
    let topInset: CGFloat
    /// Le mouvement de la bulle n'existe que si l'onglet est en usage et l'app au premier plan.
    let isActive: Bool
    let agreementWaiting: Bool
    let scrollToTop: Int
    let actions: FeedListActions
    let onContentMoved: (CGFloat) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let topId = "feed-top"

    private var content: FeedContent? {
        if case .ready(let value) = feed {
            return value
        }
        return nil
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 14) {
                    topAnchor
                    items
                }
                .padding(.horizontal, Spacing.md)
                .padding(.top, topInset + Spacing.md)
                .padding(.bottom, Spacing.md)
            }
            .onPreferenceChange(ContentTopKey.self) { onContentMoved($0) }
            .refreshable { await actions.onRefresh() }
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

    /// Un point de hauteur nulle tout en haut du contenu : sa position dit où est le contenu.
    private var topAnchor: some View {
        Color.clear
            .frame(height: 0)
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
        switch feed {
        case .loading:
            if !agreementWaiting {
                FeedSkeleton()
            }
        case .unavailable:
            unavailable
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
            actions: actions.bubble
        )
        ForEach(content.cards, id: \.member.uid) { card in
            MemberCardView(
                card: card,
                texts: texts,
                locating: isLocating(card.member.uid),
                actions: actions.card,
                photoContext: PhotoSlotContext(miffId: content.miffId, offline: content.isOffline, active: isActive)
            )
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
