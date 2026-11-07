import SwiftUI

/// La racine du VRAI app : montre ce que dit la SESSION (ticket Authentification §5) : attente, inscription ou connexion,
/// panneau de consentement, ou l'étape suivante du compte. La décision vit dans `SessionManager`, pas ici.
@MainActor
struct AppRootView: View {
    private let environment: AppEnvironment
    @ObservedObject private var sessionState: SessionObservable
    /// Gardé à ce niveau (et non dans la branche du panneau) pour pouvoir être vidé quand on quitte le panneau.
    @StateObject private var panel: ConsentPanelViewModel
    /// La « Dernière vérification » de la suppression du compte (AUTH-5c) : gardée à ce niveau pour survivre aux changements d'écran.
    @StateObject private var lastCheck: DeleteReauthViewModel

    init(environment: AppEnvironment) {
        self.environment = environment
        _sessionState = ObservedObject(wrappedValue: environment.sessionState)
        _panel = StateObject(wrappedValue: ConsentPanelViewModel(session: environment.session))
        _lastCheck = StateObject(wrappedValue: environment.makeDeleteReauthViewModel(last: true))
    }

    var body: some View {
        content
            .onChange(of: sessionState.state) { newState in
                if newState != .ready(.consentPanel) {
                    panel.reset()
                }
            }
            .alert(
                Text("settings_delete_failed_title"),
                isPresented: Binding(
                    get: { sessionState.deletionBlocked },
                    set: { if !$0 { environment.session.dismissDeletionBlockedNotice() } }
                ),
                actions: {
                    Button("miff_lost_ok_action") {
                        environment.session.dismissDeletionBlockedNotice()
                    }
                },
                message: {
                    // Une Miff est apparue pendant la suppression : le compte n'a PAS été supprimé. Une phrase calme, avec la suite.
                    // Les Miffs déjà quittées ou supprimées pendant la tentative le restent (phrase propre à iOS, la clé d'Android ne le dit pas).
                    Text(verbatim: deletionBlockedMessage)
                }
            )
    }

    private var deletionBlockedMessage: String {
        L10n.format("settings_delete_miffs_blocked", AppLinks.supportEmail) + "\n\n"
            + L10n.string("ios_settings_delete_miffs_blocked_more")
    }

    @ViewBuilder
    private var content: some View {
        switch sessionState.state {
        case .loading:
            SessionLoadingView()
        case .lookupFailed:
            AccountLookupFailedView(onRetry: { environment.session.retry() }, onSignOut: signOut)
        case .ready(let destination):
            screen(for: destination)
        }
    }

    @ViewBuilder
    private func screen(for destination: AppDestination) -> some View {
        switch destination {
        case .signUp, .signIn:
            // `id` : une déconnexion arrive sur une navigation neuve (l'écran de connexion), jamais sur une ancienne.
            SignedOutHost(environment: environment, start: destination)
                .id(destination)
        case .consentPanel:
            ConsentPanelContent(
                state: panel.state,
                onContinue: { panel.accept() },
                onRefuse: { panel.refuse(source: environment.socialSource) }
            )
        case .profile:
            ProfileFlowView(environment: environment, mode: .full)
                .id(profileKey(for: .full))
        case .phoneOnly:
            ProfileFlowView(environment: environment, mode: .phoneOnly)
                .id(profileKey(for: .phoneOnly))
        case .miffChoice:
            // L'onboarding n'est complet qu'une fois une Miff créée ou rejointe (CLAUDE.md).
            MiffOnboardingHost(environment: environment)
                .id(sessionKey(for: "choice"))
        case .accountDeletion:
            // AUTH-5c : la suppression du compte a PRIORITÉ sur toute autre destination tant que son marqueur local existe.
            AccountDeletionHostView(
                controller: environment.accountDeletion.controller,
                lastCheck: lastCheck,
                onLeave: { environment.session.leaveAfterAccountDeletion() }
            )
        case .feed:
            // LOC-4a : l'écran des notifications, le renouvellement du consentement et l'écran bloquant de la localisation
            // passent AVANT le Feed ; un hôte neuf par compte et par session, comme le Feed lui-même.
            PermissionGateHost(environment: environment) {
                MiffHomeHost(environment: environment)
            }
            .id(sessionKey(for: "feed"))
        }
    }

    /// Un hôte neuf par compte et par session : la personne suivante ne retrouve jamais une navigation ni un écran de la
    /// précédente.
    private func sessionKey(for name: String) -> String {
        "miff-\(name)-\(environment.session.currentUid ?? "")-\(environment.session.generation)"
    }

    /// Un view model par compte, par mode et par session : après une déconnexion la personne suivante ne retrouve jamais ce
    /// que la précédente avait saisi.
    private func profileKey(for mode: OnboardingMode) -> String {
        let name = mode == .full ? "full" : "phone"
        return "profile-\(name)-\(environment.session.currentUid ?? "")-\(environment.session.generation)"
    }

    private func signOut() {
        Task {
            await environment.session.signOut()
        }
    }
}
