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
    /// L'offre (« Miff+ arrive bientôt ») : un compte gratuit qui a déjà une Miff a touché « Rejoindre » ou « Créer » dans le sélecteur.
    let onOpenOffer: () -> Void
    /// « Reprendre le partage » : l'écran de la Miff tient le dialogue et le consentement (rien n'est écrit depuis le Feed).
    let onResumeSharing: (String) -> Void
    let onMessage: (String) -> Void
    let onCall: (String) -> Void
    /// Donne les coordonnées à Plans ; dit ensuite si une application les a prises.
    let onOpenMap: (PositionFix, @escaping (Bool) -> Void) -> Void
    let onSignOut: () -> Void
    let onOpenSettings: () -> Void
}

/// La durée du retour animé de l'en-tête (un toucher sur le logo, un verrouillage) : courte.
enum FeedHeaderMotion {
    static let showSeconds: Double = 0.2
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
    /// FEED-8 : la photo ouverte en plein écran. Rien n'en est gardé une fois fermée, ni quand le Feed quitte l'écran.
    @State private var openedPhoto: OpenedPhoto?
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
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
            // Le contenu ne passe JAMAIS sous la barre d'état : une bande fixe de la couleur du fond couvre la zone sûre du haut, même quand
            // l'en-tête est caché (comme Android, qui coupe le contenu à la zone sûre).
            colorBgCanvas.color.ignoresSafeArea(edges: .top).frame(height: 0)
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
        // Sans Miff, la roue crantée des Réglages restreints (Réglages > Mon compte : déconnexion, suppression) est la seule sortie.
        .restrictedSettingsGear(visible: ready?.miffs.isEmpty == true, action: actions.onOpenSettings)
        .onPreferenceChange(HeaderHeightKey.self) { height in
            headerHeight = height
            headerModel.measured(height)
        }
        .onAppear { headerModel.lock(headerLocked) }
        // Verrouillé (chargement, hors connexion, VoiceOver, sélecteur ouvert) : l'en-tête revient avec une courte animation, immédiate avec
        // « Réduire les animations ».
        .onChange(of: headerLocked) { locked in withHeaderAnimation { headerModel.lock(locked) } }
        .onChange(of: isList) { _ in headerModel.resetBaseline() }
        .onChange(of: model.refreshing) { refreshing in announceRefresh(refreshing) }
        // La feuille montre une position : elle est masquée dans la vignette du sélecteur d'applications comme le reste du Feed.
        .sheet(isPresented: locateIsShown) { locateSheet.hidesInAppSwitcher() }
        // La photo en plein écran (masquée elle aussi dans la vignette du sélecteur d'applications) ; elle se ferme quand l'onglet change,
        // que l'app part en arrière-plan ou que la Miff affichée change : aucune photo ne reste ouverte hors de l'écran du Feed.
        .fullScreenCover(item: $openedPhoto) { opened in photoFullScreen(opened) }
        .onChange(of: isActive) { active in
            if !active {
                openedPhoto = nil
            }
        }
        .onChange(of: content?.miffId) { _ in openedPhoto = nil }
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
            onContentMoved: { headerModel.contentMoved(to: $0) },
            onScrolled: { headerModel.scrolled(to: $0) }
        )
    }

    private var listActions: FeedListActions {
        let model = self.model
        let awaiting = self.awaiting
        return FeedListActions(
            card: MemberCardActions(
                onLocate: { model.onLocate(uid: $0) },
                onMessage: actions.onMessage,
                onCall: actions.onCall,
                onOpenPhoto: { openedPhoto = $0 },
                // MODE VOYAGE : « Modifier » sur MA carte ouvre le Profil, où la date de fin se règle.
                onEditTravel: actions.onOpenProfile
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

    /// L'animation courte du retour de l'en-tête ; aucune avec « Réduire les animations ».
    private func withHeaderAnimation(_ change: () -> Void) {
        if reduceMotion {
            change()
        } else {
            withAnimation(.easeOut(duration: FeedHeaderMotion.showSeconds), change)
        }
    }

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
                    withHeaderAnimation { headerModel.show() }
                    scrollToTop += 1
                },
                onSelect: { model.onSelect($0) },
                onCreate: { selectorEntryTapped(open: actions.onCreate) },
                onJoin: { selectorEntryTapped(open: actions.onJoin) },
                selectorOpen: $selectorOpen
            )
        )
        // L'en-tête qui monte est COUPÉ à la zone sûre (il ne passe pas sur l'heure de la barre d'état) ; le masque descend un peu plus bas
        // pour laisser voir son ombre.
        .mask(alignment: .top) { Rectangle().padding(.bottom, -Spacing.md) }
        .accessibilitySortPriority(1)
    }

    /// Le sélecteur s'ouvre pour tout le monde dès qu'il y a une Miff (décision du PM, 2026-11-08).
    private var pillState: FeedPillState? {
        FeedPillState.make(from: ready)
    }

    /// « Rejoindre » ou « Créer » dans le sélecteur : le parcours normal, ou l'offre pour un compte gratuit qui a déjà une Miff.
    /// Une formule inconnue (hors connexion) ouvre le parcours : jamais « gratuit » sur un doute, le serveur tranche.
    private func selectorEntryTapped(open flow: () -> Void) {
        switch FeedSelectorRules.outcome(for: ready) {
        case .openFlow:
            flow()
        case .openPaywall:
            actions.onOpenOffer()
        }
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

    private func photoFullScreen(_ opened: OpenedPhoto) -> some View {
        PhotoFullScreen(
            opened: opened,
            texts: texts,
            nowMillis: model.dependencies.clock(),
            zone: model.dependencies.zone(),
            onOpenMap: actions.onOpenMap,
            onDismiss: { openedPhoto = nil }
        )
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
