import SwiftUI
import UIKit

/// Un onglet gardé en vie pendant qu'on est ailleurs (sa position de défilement, ce qu'il a lu) : caché, sans toucher possible et sans
/// que VoiceOver le lise.
private struct TabLayer<Content: View>: View {
    let isActive: Bool
    let content: Content

    init(isActive: Bool, @ViewBuilder content: () -> Content) {
        self.isActive = isActive
        self.content = content()
    }

    var body: some View {
        content
            .opacity(isActive ? 1 : 0)
            .allowsHitTesting(isActive)
            .accessibilityHidden(!isActive)
    }
}

/// Ce que la coquille donne à l'onglet Profil pour la liste des lieux sous le carrousel (décision du PM, 2026-11-11) : le view model qui les
/// lit, le message calme d'un geste, et la personne avec ses gestes.
struct ProfilePlacesShell {
    let model: ProfilePlacesViewModel
    let notices: PlaceNotices
    /// Les demandes d'ajout d'un lieu (LIEU-REQ), sous la même liste.
    let requests: ProfileRequestsViewModel
    let context: ProfilePlacesContext
}

/// Ce que la coquille a besoin de l'Update (UPDATE-3) : le view model de l'écran, et MON statut dans la Miff affichée (la couleur du bouton
/// central, la bulle d'incitation, le nettoyage de la photo expirée).
struct UpdateShell {
    let model: UpdateViewModel
    let status: MyStatusStore
    /// « Revoir et accepter » : l'écran de renouvellement de l'accord (la porte des autorisations l'écoute).
    var onReviewAgreement: () -> Void = {}
}

/// Ce qui relance le nettoyage de la photo expirée : l'annuaire lu, ou un changement de ma photo.
private struct CleanupKey: Equatable {
    let ready: Bool
    let facts: MyStatusFacts?
}

/// Ce que la coquille dit au Feed : est-il l'onglet en usage (sinon rien n'est écouté), et comment ouvrir l'onglet Profil (l'avatar de
/// l'en-tête du Feed).
struct FeedShellContext {
    let isActive: Bool
    let openProfile: () -> Void
}

/// La COQUILLE de l'app une fois l'onboarding fini (ticket Profil / Réglages, ONB-7) : les trois onglets et leur barre en verre.
///
/// Points d'accroche des chantiers suivants :
/// - le Feed iOS (FEED-5 à 8) est le contenu de l'onglet Feed (`feed`) ; il est l'UNIQUE écouteur de MON statut (D19) et le donne à
///   `UpdateShell.status` (`MyStatusStore.update`) : couleur du bouton central, bulle, état « partage arrêté », nettoyage de la photo ;
/// - la suppression de compte est faite (AUTH-5c, `DeleteAccountFlowView`) : atteinte par Mon compte.
///
/// L'Update (UPDATE-3) : le bouton central est actif, sa pastille prend la couleur de MON statut, et la bulle d'incitation (visible sur tous
/// les onglets sauf l'Update) est posée dans la zone du bas, AU-DESSUS de la barre, par la même `safeAreaInset` : la marge basse de chaque
/// onglet est donc exactement la hauteur de la barre et de la bulle.
///
/// La barre n'est visible que sur les écrans racines : elle disparaît dès qu'un écran est empilé (les Réglages, l'écran d'une Miff, la
/// création, les lieux : tous poussés dans la pile UNIQUE de l'hôte, qui les dessine par-dessus cette coquille), avec le clavier, et
/// pour un compte qui n'a aucune Miff (l'écran « Pas encore de Miff » est plein écran, décision Q13 de l'onboarding). Le contenu passe
/// SOUS la barre : chaque défilement garde sa marge basse grâce à la zone sûre que la barre déclare.
///
/// Aucune pile de navigation n'est créée ici : une pile imbriquée dans celle de l'hôte (où l'écran de la Miff est empilé) est ce qui
/// faisait planter l'app au toucher d'une carte « Mes Miffs » sur le Profil.
struct MainShellView<Feed: View>: View {
    private let miffs: DirectoryState
    @ObservedObject private var profile: ProfileTabViewModel
    /// Les membres des cartes du Profil : le Profil les observe lui-même (la coquille n'a pas à se redessiner pour eux).
    private let memberPreviews: MiffMemberPreviews
    private let travel: TravelViewModel
    private let places: ProfilePlacesShell
    /// Les demandes d'ajout d'un lieu (LIEU-REQ-C) : le point de l'onglet Profil suit ce que le créateur n'a pas encore vu.
    @ObservedObject private var requests: ProfileRequestsViewModel
    @ObservedObject private var updateModel: UpdateViewModel
    @ObservedObject private var myStatus: MyStatusStore
    private let update: UpdateShell
    private let onOpenMiff: (String) -> Void
    private let onCreateMiff: () -> Void
    private let onOpenSettings: (SettingsRoute) -> Void
    /// Monte chaque fois qu'on demande le Feed (toucher sur la notification « un proche a donné des nouvelles ») : l'onglet Feed est alors montré.
    private let openFeedToken: Int
    private let feed: (FeedShellContext) -> Feed
    /// NOTIF-6I : un toucher sur une ligne du centre de notifications demande un onglet (le Feed, ou l'écran Update).
    @ObservedObject private var tabRequests: TabRequests
    @State private var selected: MainTab = .feed
    @State private var keyboardVisible = false
    @State private var nowMillis = Int(Date().timeIntervalSince1970 * 1000)
    @State private var minuteTick = Timer.publish(every: 60, on: .main, in: .common).autoconnect()
    @State private var confirmation: String?
    @State private var lastPrompt: PromptLevel = .hidden
    /// Le haut RÉEL de la barre (coordonnées globales), mesuré : l'Update s'arrête au-dessus (jamais de hauteur écrite en dur).
    @State private var barTopY: CGFloat?
    @Environment(\.scenePhase) private var scenePhase

    init(
        miffs: DirectoryState,
        profile: ProfileTabViewModel,
        memberPreviews: MiffMemberPreviews,
        travel: TravelViewModel,
        places: ProfilePlacesShell,
        update: UpdateShell,
        onOpenMiff: @escaping (String) -> Void,
        onCreateMiff: @escaping () -> Void,
        onOpenSettings: @escaping (SettingsRoute) -> Void,
        tabRequests: TabRequests,
        openFeedToken: Int = 0,
        @ViewBuilder feed: @escaping (FeedShellContext) -> Feed
    ) {
        self.miffs = miffs
        _profile = ObservedObject(wrappedValue: profile)
        self.memberPreviews = memberPreviews
        self.travel = travel
        self.places = places
        _requests = ObservedObject(wrappedValue: places.requests)
        _updateModel = ObservedObject(wrappedValue: update.model)
        _myStatus = ObservedObject(wrappedValue: update.status)
        self.update = update
        self.onOpenMiff = onOpenMiff
        self.onCreateMiff = onCreateMiff
        self.onOpenSettings = onOpenSettings
        _tabRequests = ObservedObject(wrappedValue: tabRequests)
        self.openFeedToken = openFeedToken
        self.feed = feed
    }

    private var hasNoMiff: Bool {
        MainShellRules.hasNoMiff(miffs)
    }

    /// L'onglet réellement montré : le Feed dès que la dernière Miff a disparu.
    private var shownTab: MainTab {
        MainShellRules.effectiveTab(selected: selected, hasNoMiff: hasNoMiff)
    }

    /// L'onglet Profil est regardé : sa pop-in « Pas de connexion » suit le réseau (jamais en arrière-plan).
    private var profileIsWatched: Bool {
        shownTab == .profile && scenePhase != .background
    }

    /// Les écrans empilés recouvrent cette coquille (pile de l'hôte) : `pathIsEmpty` vaut toujours vrai ici.
    private var barVisible: Bool {
        MainShellRules.barVisible(keyboardVisible: keyboardVisible, pathIsEmpty: true, directory: miffs)
    }

    // MARK: - L'Update : la couleur, la bulle

    private var activeMiffId: String? {
        if case .ready(let ready) = miffs {
            return ready.activeId
        }
        return nil
    }

    private var miffCount: Int {
        if case .ready(let ready) = miffs {
            return ready.miffs.count
        }
        return 0
    }

    /// Un statut existe seulement quand je partage (l'arrêt du partage le supprime dans le même lot) : sans statut, neutre.
    private var central: CentralButtonState {
        CentralButtonState(level: UpdatePrompt.level(myStatus.facts, sharing: true, nowMillis: nowMillis))
    }

    private var prompt: PromptLevel {
        UpdatePrompt.prompt(myStatus.facts, sharing: true, nowMillis: nowMillis)
    }

    private var bubbleVisible: Bool {
        MainShellRules.bubbleVisible(prompt: prompt, selected: shownTab, barVisible: barVisible)
    }

    private var cleanupKey: CleanupKey {
        CleanupKey(ready: activeMiffId != nil, facts: myStatus.facts)
    }

    private var updateActions: UpdateActions {
        UpdateActions(
            onClose: { selected = .feed },
            onResumeSharing: {
                selected = .feed
                if let id = activeMiffId {
                    onOpenMiff(id)
                }
            },
            onReviewAgreement: {
                selected = .feed
                update.onReviewAgreement()
            },
            onOpenSettings: { updateModel.openSettings() }
        )
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                TabLayer(isActive: shownTab == .feed) {
                    feed(FeedShellContext(isActive: shownTab == .feed, openProfile: { selected = .profile }))
                }
                TabLayer(isActive: shownTab == .profile) {
                    ProfileTabView(
                        state: profile.state,
                        miffs: miffs,
                        previews: memberPreviews,
                        isActive: shownTab == .profile,
                        travel: travel,
                        offlineRetry: profile.offlineRetry,
                        placesModel: places.model,
                        placeNotices: places.notices,
                        requestsModel: places.requests,
                        placesContext: places.context,
                        actions: ProfileTabActions(
                            onOpenMyInfo: { onOpenSettings(.myInfo) },
                            onOpenSettings: { onOpenSettings(.settings) },
                            onRetry: { profile.retry() },
                            onOpenMiff: onOpenMiff,
                            onCreateMiff: onCreateMiff
                        )
                    )
                    // Profil jamais lu et pas de réseau : la même pop-in « Pas de connexion » que le Feed, seulement tant que l'onglet est
                    // à l'écran (l'essai automatique s'arrête en changeant d'onglet ou en quittant l'app).
                    .overlay { OfflinePopinOverlay(model: profile.offlineRetry) }
                    .task(id: profileIsWatched) {
                        if profileIsWatched {
                            await profile.watchConnectivity()
                        }
                    }
                }
                if shownTab == .update {
                    UpdateTabView(model: updateModel, miffCount: miffCount, actions: updateActions)
                        .environment(\.tabBarTopY, barVisible ? barTopY : nil)
                }
            }
            .background(
                // L'écouteur du créateur sur UNE Miff, branché seulement pendant que l'app est devant (LIEU-REQ-C).
                ProfileRequestsWatcher(model: places.requests, miffs: miffs, myUid: places.context.myUid)
            )
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if barVisible {
                    bottomLayer(screenHeight: Self.fullHeight(of: proxy))
                        .padding(.bottom, TabBarLayout.bottomPadding(safeAreaBottom: proxy.safeAreaInsets.bottom))
                }
            }
        }
        // Changer la langue (Réglages > Langue) refait le contenu des onglets dans la nouvelle langue ; l'onglet choisi, lui, ne bouge pas.
        .followsAppLanguage()
        .onPreferenceChange(TabBarTopPreference.self) { barTopY = $0 }
        // Un toucher sur une notification du serveur : l'onglet Feed.
        .onChange(of: openFeedToken) { _ in
            selected = .feed
        }
        // La dernière Miff a disparu : on repart du Feed.
        .onChange(of: hasNoMiff) { noMiff in
            if noMiff {
                selected = .feed
            }
        }
        // Une ligne du centre de notifications ou une notification a été touchée : l'onglet demandé s'ouvre (une seule fois, même si la
        // demande est arrivée avant que la coquille existe).
        .onChange(of: tabRequests.latest) { _ in
            if let request = tabRequests.takePending() {
                selected = request.tab
            }
        }
        .onAppear {
            if let request = tabRequests.takePending() {
                selected = request.tab
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            keyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            keyboardVisible = false
        }
        .onAppear {
            updateModel.onPublished = { _ in
                publishedArrived()
            }
        }
        .task(id: cleanupKey) {
            cleanUp()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                refreshClock()
                cleanUp()
            }
        }
        .onChange(of: prompt) { current in
            promptChanged(to: current)
        }
        .onReceive(minuteTick) { _ in
            refreshClock()
        }
        .task(id: confirmation) {
            if confirmation != nil {
                try? await Task.sleep(nanoseconds: UInt64(UpdateLayout.confirmationSeconds * 1_000_000_000))
                confirmation = nil
            }
        }
    }

    /// Le message calme, la bulle d'incitation et la barre, dans UNE colonne : ils ne peuvent jamais se chevaucher (le message va AU-DESSUS
    /// de la bulle, dont la queue s'arrête 4 pt au-dessus de la barre).
    private func bottomLayer(screenHeight: CGFloat) -> some View {
        VStack(spacing: 0) {
            if let confirmation {
                UpdateConfirmation(text: confirmation)
                    .padding(.bottom, bubbleVisible ? Spacing.sm : Spacing.md)
            }
            if bubbleVisible {
                IncentiveBubble(level: prompt, maxBodyHeight: screenHeight * UpdateLayout.bubbleMaxScreenShare)
                    .transition(.opacity)
            }
            MainTabBar(
                config: .current,
                selected: shownTab,
                central: central,
                variant: updateModel.stage.glassVariant(updateTabSelected: shownTab == .update),
                profileDot: requests.state.profileDot,
                onSelect: { selected = $0 }
            )
            .background(TabBarTopReader())
        }
    }

    /// La hauteur de l'écran entier (zones sûres comprises) : la bulle prend 40 % de celle-ci au plus.
    private static func fullHeight(of proxy: GeometryProxy) -> CGFloat {
        proxy.size.height + proxy.safeAreaInsets.top + proxy.safeAreaInsets.bottom
    }

    // MARK: - Comportements de l'Update

    /// Une photo est publiée : retour au Feed, où ma carte montrera la photo ; un message calme sans son ni vibration, retiré après 3 secondes.
    private func publishedArrived() {
        // Retour au Feed seulement si la personne est encore sur l'Update ; sinon elle reste où elle est, avec le message calme.
        if shownTab == .update {
            selected = .feed
        }
        let text = L10n.string("update_published")
        confirmation = text
        UpdateAnnouncer.post(text)
    }

    /// La bulle est annoncée au lecteur d'écran une fois à son apparition et au passage de l'orange au rouge (D34), sans son ni vibration.
    private func promptChanged(to current: PromptLevel) {
        let previous = lastPrompt
        lastPrompt = current
        if bubbleVisible && UpdatePrompt.shouldAnnounce(previous: previous, current: current) {
            UpdateAnnouncer.post(L10n.string(current == .red ? "update_bubble_red" : "update_bubble_orange"))
        }
    }

    /// Chaque minute, sans lecture du serveur : le niveau est recalculé avec l'horloge (B3).
    private func refreshClock() {
        nowMillis = Int(Date().timeIntervalSince1970 * 1000)
    }

    /// Au lancement et au retour au premier plan : MA photo expirée est retirée de la base (aucune lecture tant que la date locale existe).
    private func cleanUp() {
        guard cleanupKey.ready else {
            return
        }
        let mine = myStatus.facts.map { MyPhotoFacts(hasPhoto: $0.hasPhoto, lastUpdateAtMillis: $0.lastUpdateAtMillis) }
        updateModel.cleanUpExpiredPhoto(mine)
    }
}
