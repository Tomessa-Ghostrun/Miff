import SwiftUI
import UIKit

// L'onglet Feed (tickets FEED-5 à FEED-7) : l'en-tête qui se masque au défilement, puis la bulle de la Miff, les cartes des membres
// (moi en dernier) et « Ajouter un proche ». Rien ici ne dit « tout va bien » sans le savoir : le squelette, l'état vide, la mention
// hors connexion et les bulles neutres sont calmes et ne sont jamais une phrase de sécurité.

/// Ce que font les gestes du Feed (il n'écrit jamais rien).
struct FeedScreenActions {
    let onOpenProfile: () -> Void
    let onCreate: () -> Void
    let onJoin: () -> Void
    /// « Reprendre le partage » : l'écran de la Miff tient le dialogue et le consentement (rien n'est écrit depuis le Feed).
    let onResumeSharing: (String) -> Void
    let onMessage: (String) -> Void
    let onCall: (String) -> Void
    /// Donne les coordonnées à Plans ; dit ensuite si une application les a prises.
    let onOpenMap: (PositionFix, @escaping (Bool) -> Void) -> Void
    let onSignOut: () -> Void
}

private struct HeaderHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct FeedScreen: View {
    @ObservedObject var model: FeedViewModel
    @ObservedObject var awaiting: AwaitingAgreement
    let isActive: Bool
    let actions: FeedScreenActions

    @State private var headerModel = FeedHeaderModel()
    @State private var headerHeight: CGFloat = 0
    @State private var selectorOpen = false
    @State private var scrollToTop = 0
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.accessibilitySwitchControlEnabled) private var switchControl

    private var texts: FeedTexts {
        FeedTexts()
    }

    private var ready: DirectoryReady? {
        if case .ready(let value) = model.directoryState {
            return value
        }
        return nil
    }

    private var content: FeedContent? {
        if case .ready(let value) = model.uiState {
            return value
        }
        return nil
    }

    private var isList: Bool {
        guard let ready else {
            return false
        }
        return !ready.miffs.isEmpty && content != nil
    }

    private var offline: Bool {
        let banner = content?.banners.contains(where: Self.isOfflineBanner) ?? false
        return banner || ready?.fromCache == true
    }

    private static func isOfflineBanner(_ banner: FeedBanner) -> Bool {
        if case .offline = banner {
            return true
        }
        return false
    }

    /// L'en-tête reste visible : pendant le chargement, en erreur, hors connexion, avec un lecteur d'écran (il emporterait le focus avec
    /// lui), et tant que le sélecteur de Miff est ouvert (D41).
    private var headerLocked: Bool {
        CollapsingHeaderState.mustStayVisible(
            isList: isList,
            offline: offline,
            assistiveTechnology: voiceOver || switchControl,
            selectorOpen: selectorOpen
        )
    }

    var body: some View {
        ZStack(alignment: .top) {
            stateContent
            header
            if model.refreshing {
                // Le geste est en haut de la liste, sous l'en-tête : l'indicateur de l'actualisation se montre sous lui.
                ProgressView()
                    .tint(colorActionPrimary.color)
                    .padding(.top, headerHeight + Spacing.sm)
                    .accessibilityHidden(true)
            }
        }
        .background(colorBgCanvas.color.ignoresSafeArea())
        .onPreferenceChange(HeaderHeightKey.self) { height in
            headerHeight = height
            headerModel.measured(height)
        }
        .onAppear { headerModel.lock(headerLocked) }
        .onChange(of: headerLocked) { locked in headerModel.lock(locked) }
        .onChange(of: isList) { _ in headerModel.resetBaseline() }
        .onChange(of: model.refreshing) { refreshing in announceRefresh(refreshing) }
        .sheet(isPresented: locateIsShown) { locateSheet }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("feed_pane_a11y"))
    }

    // MARK: - Les états

    @ViewBuilder
    private var stateContent: some View {
        switch model.directoryState {
        case .loading:
            ScrollView {
                FeedSkeleton()
                    .padding(.horizontal, Spacing.md)
                    .padding(.top, headerHeight + Spacing.md)
            }
        case .unavailable:
            VStack(spacing: Spacing.sm) {
                MiffNotice(text: L10n.string("feed_unavailable"))
                Button {
                    model.retry()
                } label: {
                    Text("auth_retry_action")
                }
                .buttonStyle(.miffSecondaryFullWidth)
            }
            .padding(Spacing.md)
            .padding(.top, headerHeight)
            .frame(maxHeight: .infinity, alignment: .top)
        case .ready(let ready):
            if ready.miffs.isEmpty {
                FeedNoMiff(onCreate: actions.onCreate, onJoin: actions.onJoin, onSignOut: actions.onSignOut)
                    .padding(.top, headerHeight)
                    .frame(maxHeight: .infinity, alignment: .top)
            } else {
                list(ready)
            }
        }
    }

    private func list(_ ready: DirectoryReady) -> some View {
        FeedListView(
            feed: model.uiState,
            ready: ready,
            locate: model.locate,
            texts: texts,
            topInset: headerHeight,
            isActive: isActive,
            agreementWaiting: ready.activeId.map { awaiting.waiting.contains($0) } ?? false,
            scrollToTop: scrollToTop,
            actions: listActions,
            onContentMoved: { headerModel.contentMoved(to: $0) }
        )
    }

    private var listActions: FeedListActions {
        let model = self.model
        let awaiting = self.awaiting
        return FeedListActions(
            card: MemberCardActions(
                onLocate: { model.onLocate(uid: $0) },
                onMessage: actions.onMessage,
                onCall: actions.onCall
            ),
            bubble: BubbleActions(
                onMessage: actions.onMessage,
                onResumeSharing: { resumeSharing() }
            ),
            onRetry: { model.retry() },
            onReviewAgreement: { awaiting.requestReview() },
            onRefresh: { await model.pullToRefresh() }
        )
    }

    private func resumeSharing() {
        if let miffId = content?.miffId {
            actions.onResumeSharing(miffId)
        }
    }

    // MARK: - L'en-tête

    private var header: some View {
        FeedHeaderOverlay(
            model: headerModel,
            content: FeedHeaderContent(
                me: model.me,
                myLevel: content?.myLevel ?? .neutral,
                myStatusDescription: content.map { texts.label($0.myLabel) },
                pill: pillState,
                onOpenProfile: actions.onOpenProfile,
                onLogo: {
                    headerModel.show()
                    scrollToTop += 1
                },
                onSelect: { model.onSelect($0) },
                onCreate: actions.onCreate,
                onJoin: actions.onJoin,
                selectorOpen: $selectorOpen
            )
        )
        .accessibilitySortPriority(1)
    }

    private var pillState: FeedPillState? {
        guard let ready else {
            return nil
        }
        guard let active = ready.active, !ready.miffs.isEmpty else {
            return .noMiff
        }
        // Une formule INCONNUE (abonnement non lu, ou réponse du cache) garde le sélecteur utilisable : jamais « gratuit » sur un doute.
        if !ready.premiumKnown || ready.premium {
            return .selectable(name: active.name, miffs: ready.miffs, activeId: ready.activeId)
        }
        return .plain(name: active.name)
    }

    // MARK: - La feuille « Localiser » et les annonces

    private var locateIsShown: Binding<Bool> {
        Binding(
            get: {
                if case .shown = model.locate {
                    return true
                }
                return false
            },
            set: { shown in
                if !shown {
                    model.onLocateDismissed()
                }
            }
        )
    }

    @ViewBuilder
    private var locateSheet: some View {
        if case .shown(_, let firstName, let result) = model.locate {
            LocateSheet(
                firstName: firstName,
                result: result,
                texts: texts,
                onOpenMap: actions.onOpenMap,
                onDismiss: { model.onLocateDismissed() }
            )
            .presentationDetents([.medium, .large])
        }
    }

    /// « Actualisation en cours » au début et « Informations actualisées » à la fin (rien n'est dit à jour quand le Feed dit « Dernières
    /// informations de… »).
    private func announceRefresh(_ refreshing: Bool) {
        if refreshing {
            UIAccessibility.post(notification: .announcement, argument: L10n.string("feed_refresh_started_a11y"))
        } else if !offline {
            UIAccessibility.post(notification: .announcement, argument: L10n.string("feed_refresh_done_a11y"))
        }
    }
}

/// L'en-tête par-dessus la liste : déplacé par une translation (aucune mise en page ne bouge), mesuré pour que la liste lui laisse sa place.
private struct FeedHeaderOverlay: View {
    @ObservedObject var model: FeedHeaderModel
    let content: FeedHeaderContent

    var body: some View {
        content
            .background(
                GeometryReader { proxy in
                    Color.clear.preference(key: HeaderHeightKey.self, value: proxy.size.height)
                }
            )
            .background(
                colorBgCanvas.color
                    .ignoresSafeArea(edges: .top)
                    .miffShadow(shadowSubtle)
            )
            .offset(y: model.state.offset)
    }
}
