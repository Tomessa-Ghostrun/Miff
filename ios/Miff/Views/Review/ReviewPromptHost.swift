import SwiftUI
#if REVIEW_PROMPT
import StoreKit
import UIKit
#endif

// La fenêtre d'avis posée sur l'app (ticket `docs/tickets/pop-ins-avis.md`). Trois accroches, toutes SANS effet en publication (le drapeau
// `REVIEW_PROMPT` est absent de la configuration Release : chaque fonction rend alors la vue telle quelle) :
// - `reviewPromptHost` : l'hôte, posé une fois sur l'écran des onglets (`MiffHomeHost`) : la fenêtre par-dessus tout, la décision à chaque
//   retour de l'app au premier plan, la demande du store ;
// - `reviewReportsShell` : la coquille dit si l'Update est ouverte et si la pop-in « Pas de connexion » du Profil est là ;
// - `reviewReportsFeed` : le Feed dit s'il a vu le statut d'un proche, ce que dit ma carte, si la feuille « Localiser » ou sa pop-in est là.
//
// CONFIDENTIALITÉ : rien de la personne n'est lu ici au-delà de ces faits (des booléens et une étiquette d'état) ; aucun événement, aucun
// journal, aucune permission.

extension View {
    /// L'hôte de la fenêtre. `onTabs` : l'écran des onglets est celui qui est devant (ni un écran empilé, comme les Réglages, ni l'offre) : la
    /// fenêtre n'interrompt jamais un parcours, elle attend le retour aux onglets (choix C10 du ticket).
    @MainActor
    func reviewPromptHost(environment: AppEnvironment, directory: MiffDirectory, onTabs: Bool) -> some View {
        #if REVIEW_PROMPT
        return modifier(
            ReviewPromptHostModifier(
                coordinator: environment.reviewPrompt.coordinator,
                directory: directory,
                awaiting: environment.awaitingAgreement,
                onTabs: onTabs
            )
        )
        #else
        return self
        #endif
    }

    /// La coquille des onglets dit à la fenêtre ce qu'elle seule sait.
    @MainActor
    func reviewReportsShell(updateOpen: Bool, profilePopIn: OfflineRetryModel) -> some View {
        #if REVIEW_PROMPT
        return modifier(ReviewShellReporter(updateOpen: updateOpen, profilePopIn: profilePopIn))
        #else
        return self
        #endif
    }

    /// Le Feed dit à la fenêtre ce qu'il montre.
    @MainActor
    func reviewReportsFeed(model: FeedViewModel) -> some View {
        #if REVIEW_PROMPT
        return modifier(ReviewFeedReporter(model: model))
        #else
        return self
        #endif
    }
}

#if REVIEW_PROMPT

private struct ReviewSignalsKey: EnvironmentKey {
    static let defaultValue: ReviewSignals? = nil
}

extension EnvironmentValues {
    /// Les points où l'app dit à la fenêtre d'avis ce qu'elle seule sait ; nil hors de l'hôte.
    var reviewSignals: ReviewSignals? {
        get { self[ReviewSignalsKey.self] }
        set { self[ReviewSignalsKey.self] = newValue }
    }
}

@MainActor
private struct ReviewShellReporter: ViewModifier {
    @Environment(\.reviewSignals) private var signals
    let updateOpen: Bool
    let profilePopIn: OfflineRetryModel

    func body(content: Content) -> some View {
        content
            .onAppear { apply() }
            .onChange(of: updateOpen) { _ in apply() }
    }

    private func apply() {
        signals?.updateScreenOpen = updateOpen
        signals?.profileConnectionPopInOpen = { [weak model = profilePopIn] in model?.isPopinVisible ?? false }
    }
}

@MainActor
private struct ReviewFeedReporter: ViewModifier {
    @Environment(\.reviewSignals) private var signals
    let model: FeedViewModel

    func body(content: Content) -> some View {
        content.onAppear {
            // Faible : la fenêtre vit autant que l'app, le Feed non (un Feed recréé remplace cette lecture à son apparition).
            signals?.feed = { [weak model] in
                guard let model else {
                    return ReviewFeedSnapshot()
                }
                return Self.snapshot(of: model)
            }
        }
    }

    private static func snapshot(of model: FeedViewModel) -> ReviewFeedSnapshot {
        var content: FeedContent?
        if case .ready(let value) = model.uiState {
            content = value
        }
        var locateOpen = true
        if case .idle = model.locate {
            locateOpen = false
        }
        return ReviewFeedSnapshot(
            friendStatusSeen: ReviewSituation.isFriendStatusSeen(in: content),
            myLabel: content?.myLabel,
            locateSheetOpen: locateOpen,
            connectionPopInOpen: model.offlineRetry.isPopinVisible
        )
    }
}

/// L'hôte : la fenêtre par-dessus l'écran, la décision une fois par retour au premier plan, et ce que la fenêtre demande à l'appareil.
@MainActor
private struct ReviewPromptHostModifier: ViewModifier {
    @ObservedObject var coordinator: ReviewPromptCoordinator
    let directory: MiffDirectory
    let awaiting: AwaitingAgreement
    let onTabs: Bool

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.requestReview) private var requestReview
    @State private var keyboardOpen = false
    @State private var openFailed = false

    /// Ce qui relance la décision : un nouveau retour au premier plan, ou le retour aux onglets.
    private struct LookKey: Equatable {
        let tick: Int
        let onTabs: Bool
    }

    private var hasMiff: Bool {
        MainShellRules.hasMiffs(directory.state)
    }

    func body(content: Content) -> some View {
        content
            .environment(\.reviewSignals, coordinator.signals)
            .overlay {
                ReviewPromptOverlay(
                    stage: coordinator.stage,
                    actions: ReviewPromptActions(
                        onLove: { coordinator.onLove() },
                        onBetter: { coordinator.onCanDoBetter() },
                        onClose: { coordinator.onClose() },
                        onWrite: { coordinator.onWrite() },
                        onLater: { coordinator.onLater() }
                    )
                )
            }
            .overlay(alignment: .bottom) {
                if openFailed {
                    ReviewOpenFailedNotice()
                        .padding(.bottom, Spacing.lg)
                        .transition(.opacity)
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
                keyboardOpen = true
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                keyboardOpen = false
            }
            // « Une Miff depuis 7 jours » se compte depuis la première fois que cet appareil montre le Feed avec une Miff.
            .task(id: hasMiff) {
                if hasMiff {
                    coordinator.onMiffSeen()
                }
            }
            // Une fois par retour au premier plan (pas une rotation, pas un retour des Réglages), après un court temps pour que le Feed se pose.
            .task(id: LookKey(tick: coordinator.foregroundTick, onTabs: onTabs)) {
                await look(at: coordinator.foregroundTick)
            }
            // La demande du store, le formulaire ou le courrier : seulement quand l'app est devant, jamais depuis l'arrière-plan.
            .onChange(of: coordinator.pendingEffect) { _ in performPendingEffect() }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    performPendingEffect()
                }
            }
            .onAppear { performPendingEffect() }
            .task(id: openFailed) {
                if openFailed {
                    try? await Task.sleep(nanoseconds: 4_000_000_000)
                    openFailed = false
                }
            }
    }

    private func look(at tick: Int) async {
        if !onTabs || coordinator.hasHandled(tick) {
            return
        }
        try? await Task.sleep(nanoseconds: UInt64(ReviewPromptDefaults.settleMillis) * 1_000_000)
        if Task.isCancelled || UIApplication.shared.applicationState != .active {
            return
        }
        coordinator.onForeground(tick: tick, situation: situation())
    }

    /// Lu quand l'heure vient, pas quand le retour a eu lieu : le Feed s'est posé, la personne a pu changer d'onglet.
    private func situation() -> ReviewSituation {
        let signals = coordinator.signals
        let feed = signals.feed()
        return ReviewSituation(
            hasMiff: hasMiff,
            friendStatusSeen: feed.friendStatusSeen,
            updateScreenOpen: signals.updateScreenOpen,
            keyboardOpen: keyboardOpen,
            blockingMessageOpen: ReviewSituation.messageOpen(
                lostAccessNotice: !directory.notices.isEmpty,
                awaitingAgreement: !awaiting.waiting.isEmpty,
                locateSheetOpen: feed.locateSheetOpen,
                myLabel: feed.myLabel,
                connectionPopInOpen: feed.connectionPopInOpen || signals.profileConnectionPopInOpen()
            )
        )
    }

    private func performPendingEffect() {
        guard UIApplication.shared.applicationState == .active, let effect = coordinator.takeEffect() else {
            return
        }
        let performer = ReviewEffectPerformer(
            opener: SystemReviewLinkOpener(),
            supportMailURL: ContactLinks.supportMailURL,
            requestStoreReview: { requestReview() }
        )
        Task { @MainActor in
            let opened = await performer.perform(effect)
            if !opened {
                openFailed = true
            }
        }
    }
}

#endif
