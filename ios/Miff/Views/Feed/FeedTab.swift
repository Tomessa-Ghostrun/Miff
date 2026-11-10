import SwiftUI

// CONFIDENTIALITÉ et COÛT, pour ce fichier : il crée le view model du Feed, lance son observation SEULEMENT pendant que l'onglet Feed
// est l'onglet en usage ET que l'app n'est pas en arrière-plan (la tâche est annulée dès que l'onglet change ou que l'app part : les
// écoutes sont retirées, la feuille « Localiser » est fermée ; rien n'écoute sur l'onglet Profil), et confie « Écrire à », « Appeler »
// et « Ouvrir dans les cartes » aux applications du SYSTÈME (aucune permission). Il n'envoie aucun événement et ne journalise rien.

/// Ce que le Feed demande à la navigation de l'app (il n'écrit rien lui-même).
struct FeedTabActions {
    let onCreate: () -> Void
    let onJoin: () -> Void
    /// « Reprendre le partage » : ouvre l'écran de cette Miff, qui tient le dialogue et le consentement.
    let onOpenMiff: (String) -> Void
    let onSignOut: () -> Void
    /// L'entrée discrète des Réglages restreints (#113), montrée seulement dans l'état « Pas encore de Miff ».
    let onOpenSettings: () -> Void
}

/// Ce qui relance l'observation : la Miff affichée, l'onglet en usage / l'app au premier plan, « Réessayer ».
private struct ObservationKey: Equatable {
    let miffId: String?
    let running: Bool
    let restarts: Int
}

struct FeedTab: View {
    private let context: FeedShellContext
    private let actions: FeedTabActions
    @StateObject private var model: FeedViewModel
    @ObservedObject private var awaiting: AwaitingAgreement
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @State private var noticeVisible = false

    init(environment: AppEnvironment, context: FeedShellContext, actions: FeedTabActions) {
        self.context = context
        self.actions = actions
        let session = environment.session
        let services = environment.feedServices
        let directory = environment.miffDirectory
        let repository = environment.miffRepository
        let network = environment.network
        let awaitingAgreement = environment.awaitingAgreement
        let myStatus = environment.update.myStatus
        var dependencies = FeedDependencies(
            directory: directory,
            repository: repository,
            services: services,
            network: network,
            awaiting: awaitingAgreement,
            currentUid: { session.currentUid }
        )
        // L'Update ne lit pas ma fiche : le Feed la lui donne (UN seul écouteur, D19).
        dependencies.onMyStatus = { facts, stopped in myStatus.update(facts, sharingStopped: stopped) }
        _model = StateObject(wrappedValue: FeedViewModel(dependencies: dependencies))
        _awaiting = ObservedObject(wrappedValue: awaitingAgreement)
    }

    private var running: Bool {
        context.isActive && scenePhase != .background
    }

    private var activeMiffId: String? {
        if case .ready(let ready) = model.directoryState {
            return ready.activeId
        }
        return nil
    }

    var body: some View {
        let key = ObservationKey(miffId: activeMiffId, running: running, restarts: model.restarts)
        FeedScreen(model: model, awaiting: awaiting, isActive: running, actions: screenActions)
            .task(id: key) {
                if key.running {
                    await model.observe(miffId: key.miffId)
                }
            }
            // La pop-in « Pas de connexion » suit le réseau tant que le Feed est à l'écran : annulée dès que l'onglet change ou que l'app
            // part en arrière-plan (plus de pop-in ni d'essai automatique). Indépendante de « Réessayer » : un essai ne la remet pas à zéro.
            .task(id: key.running) {
                if key.running {
                    await model.watchConnectivity()
                }
            }
            .overlay(alignment: .bottom) {
                if noticeVisible {
                    FeedActionNotice()
                        .padding(.bottom, Spacing.md)
                        .transition(.opacity)
                }
            }
            .task(id: noticeVisible) {
                if noticeVisible {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    noticeVisible = false
                }
            }
            // Le sélecteur d'applications d'iOS garde une vignette de l'écran : ni positions ni photos ne doivent y rester.
            .hidesInAppSwitcher()
    }

    private var screenActions: FeedScreenActions {
        FeedScreenActions(
            onOpenProfile: context.openProfile,
            onCreate: actions.onCreate,
            onJoin: actions.onJoin,
            onResumeSharing: actions.onOpenMiff,
            onMessage: { phone in open(FeedLinks.message(phone: phone)) },
            onCall: { phone in open(FeedLinks.call(phone: phone)) },
            onOpenMap: { fix, done in
                guard let url = FeedLinks.map(fix) else {
                    done(false)
                    return
                }
                openURL(url) { accepted in done(accepted) }
            },
            onSignOut: actions.onSignOut,
            onOpenSettings: actions.onOpenSettings
        )
    }

    /// Confie un lien (SMS, appel) au système. Si aucune application ne répond, un message calme, jamais une erreur technique.
    private func open(_ url: URL?) {
        guard let url else {
            noticeVisible = true
            return
        }
        openURL(url) { accepted in
            if !accepted {
                noticeVisible = true
            }
        }
    }
}
