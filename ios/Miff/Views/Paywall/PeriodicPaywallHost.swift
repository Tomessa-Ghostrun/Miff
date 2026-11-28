import SwiftUI
import UIKit

// L'offre périodique posée sur l'app (ticket `docs/tickets/paywall-periodique.md`, PAY-PER-I ; décisions du PM des 2026-11-25 et 2026-11-26). ACTIVE dans TOUS les builds, sans
// drapeau (décision P1). Trois accroches :
// - `periodicPaywallHost` : l'hôte, posé une fois sur l'écran des onglets (`MiffHomeHost`) : l'écoute des appuis, le regard des règles, les surcouches ;
// - `periodicReportsShell` : la coquille dit si l'Update est ouverte et si la pop-in « Pas de connexion » du Profil est là ;
// - `periodicReportsFeed` : le Feed dit s'il porte une alerte, ce que dit ma carte, si la feuille « Localiser » ou sa pop-in est là.
//
// CONFIDENTIALITÉ : rien de la personne n'est lu ici au-delà de ces faits (des booléens et une étiquette d'état) ; aucun événement, aucun journal, aucune permission, aucune écriture
// serveur. L'offre s'ouvre par le point d'entrée UNIQUE de l'hôte (`MiffHomeHost.presentPaywall`, raison `periodic`) : même écran, même croix de 3 secondes, mêmes événements.

extension View {
    /// L'hôte de l'offre périodique. `onTabs` : l'écran des onglets est celui qui est devant (ni un écran empilé, comme les Réglages ou un formulaire, ni l'offre). `open` : ouvre
    /// l'offre (`presentPaywall(.periodic)`).
    @MainActor
    func periodicPaywallHost(
        environment: AppEnvironment,
        directory: MiffDirectory,
        onTabs: Bool,
        deletionBannerOpen: Bool,
        open: @escaping () -> Void
    ) -> some View {
        // L'offre n'a des prix à lire que si l'achat est allumé dans ce build : hors connexion, rien n'est montré par surprise (ticket C6). Sans achat (« Miff+ arrive
        // bientôt »), il n'y a aucun prix à lire : la règle s'applique telle quelle.
        let purchaseIsOn = environment.premium.coordinator != nil
        let network = environment.network
        return modifier(
            PeriodicPaywallHostModifier(
                services: environment.periodicPaywall,
                directory: directory,
                awaiting: environment.awaitingAgreement,
                dangerSignal: environment.dangerShell.signal,
                center: environment.notifications.center,
                beacon: environment.periodicPaywall.beacon,
                offerReachable: { !purchaseIsOn || network.isOnline() },
                onTabs: onTabs,
                deletionBannerOpen: deletionBannerOpen,
                open: open
            )
        )
    }

    /// La coquille des onglets dit à l'offre ce qu'elle seule sait.
    @MainActor
    func periodicReportsShell(updateOpen: Bool, profilePopIn: OfflineRetryModel) -> some View {
        modifier(PeriodicShellReporter(updateOpen: updateOpen, profilePopIn: profilePopIn))
    }

    /// Le Feed dit à l'offre ce qu'il montre.
    @MainActor
    func periodicReportsFeed(model: FeedViewModel) -> some View {
        modifier(PeriodicFeedReporter(model: model))
    }
}

@MainActor
private struct PeriodicShellReporter: ViewModifier {
    @Environment(\.periodicSignals) private var signals
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
private struct PeriodicFeedReporter: ViewModifier {
    @Environment(\.periodicSignals) private var signals
    let model: FeedViewModel

    func body(content: Content) -> some View {
        content.onAppear {
            // Faible : l'offre vit autant que l'app, le Feed non (un Feed recréé remplace cette lecture à son apparition).
            signals?.feed = { [weak model] in
                guard let model else {
                    return PeriodicFeedSnapshot()
                }
                return Self.snapshot(of: model)
            }
        }
    }

    private static func snapshot(of model: FeedViewModel) -> PeriodicFeedSnapshot {
        var content: FeedContent?
        if case .ready(let value) = model.uiState {
            content = value
        }
        var snapshot = PeriodicFeedSnapshot.of(content: content)
        snapshot.locateSheetOpen = true
        if case .idle = model.locate {
            snapshot.locateSheetOpen = false
        }
        snapshot.connectionPopInOpen = model.offlineRetry.isPopinVisible
        return snapshot
    }
}

/// Ce qui relance le regard des règles : un changement dans les règles (le signal), ou dans la situation de l'écran.
private struct PeriodicWatchKey: Equatable {
    let tick: Int
    let situation: PeriodicPaywallSituation
}

/// L'hôte : l'écoute des appuis sur la fenêtre, les surcouches qui se déclarent, et le regard des règles à chaque changement.
@MainActor
private struct PeriodicPaywallHostModifier: ViewModifier {
    let services: PeriodicPaywallServices
    @ObservedObject var directory: MiffDirectory
    @ObservedObject var awaiting: AwaitingAgreement
    @ObservedObject var dangerSignal: DangerAlertSignal
    @ObservedObject var center: NotificationCenterService
    @ObservedObject var beacon: PeriodicPaywallBeacon
    let offerReachable: () -> Bool
    let onTabs: Bool
    /// La bannière courte de la suppression d'une Miff est à l'écran.
    let deletionBannerOpen: Bool
    let open: () -> Void

    @Environment(\.scenePhase) private var scenePhase
    @State private var keyboardOpen = false

    func body(content: Content) -> some View {
        content
            .environment(\.overlayPresence, services.rules)
            .environment(\.periodicSignals, services.signals)
            .background(PeriodicTouchProbe(rules: services.rules, menuAreas: services.signals.menuAreas))
            // L'app connectée (onboarding fini, consentements donnés) est à l'écran : l'ouverture de cette session est comptée.
            .onAppear { services.rules.onShellShown() }
            .onDisappear { services.rules.onShellGone() }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
                keyboardOpen = true
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                keyboardOpen = false
            }
            .task(id: PeriodicWatchKey(tick: beacon.tick, situation: situation())) {
                await watch()
            }
    }

    /// Lue quand l'heure vient, pas quand l'événement a eu lieu : le Feed s'est posé, la personne a pu changer d'onglet.
    private func situation() -> PeriodicPaywallSituation {
        let signals = services.signals
        let feed = signals.feed()
        let appMessage = PeriodicPaywallSituation.messageOpen(
            lostAccessNotice: !directory.notices.isEmpty,
            awaitingAgreement: !awaiting.waiting.isEmpty,
            locateSheetOpen: feed.locateSheetOpen,
            myLabel: feed.myLabel,
            connectionPopInOpen: feed.connectionPopInOpen || signals.profileConnectionPopInOpen()
        )
        // La ligne « Alerte terminée… » et la bannière de la suppression d'une Miff sont des messages de l'app, courts mais réels.
        let transient = PeriodicPaywallSituation.transientMessageOpen(
            alertNotice: dangerSignal.noticeShown, deletionBanner: deletionBannerOpen
        )
        let message = appMessage || transient
        let alertLine = PeriodicPaywallSituation.alertLineUnread(in: center.history.items)
        return PeriodicPaywallSituation(
            miffPlus: PeriodicPaywallSituation.miffPlus(of: directory.state),
            tabsInFront: onTabs && scenePhase == .active,
            updateScreenOpen: signals.updateScreenOpen,
            keyboardOpen: keyboardOpen,
            messageOpen: message,
            ownAlertInProgress: PeriodicPaywallSituation.ownAlertHolds(
                inProgress: dangerSignal.inProgress, sentScreenOpen: dangerSignal.sentScreenOpen
            ),
            lovedOneAlertUnseen: feed.alertActive || alertLine,
            offerReachable: offerReachable()
        )
    }

    private func watch() async {
        let watcher = PeriodicPaywallWatcher(
            rules: services.rules,
            situation: { situation() },
            appIsActive: { UIApplication.shared.applicationState == .active },
            modalPresented: { PresentedModalProbe.isModalPresented() },
            sleep: AppClock.live.pause,
            open: open
        )
        await watcher.look()
    }
}
