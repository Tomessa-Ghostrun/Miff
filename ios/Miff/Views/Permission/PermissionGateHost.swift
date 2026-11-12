import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : il choisit l'écran et relaie les gestes de la personne ; il ne lit aucune position, n'envoie
// aucun événement et ne journalise rien (décisions (g) et (h) du PM).

/// Ce qui se tient entre la personne et le Feed (LOC-4a) : l'écran des notifications, le renouvellement du consentement et
/// l'écran BLOQUANT de la localisation (voir `resolveGate` pour l'ordre). L'état des autorisations est relu chaque fois que l'app
/// revient au premier plan : une autorisation retirée dans les Réglages fait revenir l'écran bloquant (AUT5). `content` est le
/// Feed, composé seulement quand il ne reste rien à demander.
@MainActor
struct PermissionGateHost<Content: View>: View {
    private let environment: AppEnvironment
    private let content: Content
    @StateObject private var viewModel: PermissionGateViewModel
    @Environment(\.scenePhase) private var scenePhase

    init(environment: AppEnvironment, @ViewBuilder content: () -> Content) {
        self.environment = environment
        self.content = content()
        _viewModel = StateObject(wrappedValue: environment.makePermissionGate())
    }

    var body: some View {
        gate
            .task {
                await viewModel.start()
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    Task {
                        await viewModel.onResumed()
                    }
                }
            }
    }

    @ViewBuilder
    private var gate: some View {
        let state = viewModel.state
        switch state.screen {
        case .none:
            content
        case .checking:
            // Lecture des autorisations ou du consentement (quelques secondes au plus) : ni le Feed ni l'écran « Toujours ».
            SessionLoadingView()
        case .notifications:
            NotificationsPopIn(
                onAllow: {
                    Task {
                        await viewModel.onNotificationsAllowTapped()
                    }
                },
                onLater: {
                    Task {
                        await viewModel.onNotificationsLaterTapped()
                    }
                }
            )
        case .location:
            if let model = state.location {
                LocationPopIn(
                    model: model,
                    onAction: { viewModel.onLocationActionTapped() },
                    onPutOff: state.canPutOffLocation ? { viewModel.onLocationPutOff() } : nil,
                    onSignOut: signOutAction
                )
            } else {
                SessionLoadingView()
            }
        case .renewal:
            if let renewal = state.renewal {
                RenewalScreen(state: renewal, actions: renewalActions)
            } else {
                SessionLoadingView()
            }
        }
    }

    private var signOutAction: (() -> Void)? {
        guard GatePolicy.exitLinksOnBlockingScreens else {
            return nil
        }
        let session = environment.session
        return {
            Task {
                await session.signOut()
            }
        }
    }

    private var renewalActions: RenewalActions {
        RenewalActions(
            onAccept: {
                Task {
                    await viewModel.onRenewalAccepted()
                }
            },
            onLater: { viewModel.onRenewalPutOff() },
            onLeave: { viewModel.onLeaveRequested() },
            onLeaveConfirmed: {
                Task {
                    await viewModel.onLeaveConfirmed()
                }
            },
            onLeaveCancelled: { viewModel.onLeaveCancelled() },
            onSignOut: signOutAction
        )
    }
}
