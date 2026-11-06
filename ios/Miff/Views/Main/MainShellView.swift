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

/// La COQUILLE de l'app une fois l'onboarding fini (ticket Profil / Réglages, ONB-7) : les trois onglets et leur barre en verre.
///
/// Points d'accroche des chantiers suivants :
/// - le Feed iOS (FEED-5 à 8) remplace le contenu de l'onglet Feed (`feed`) ; la bulle d'incitation (visible sur tous les onglets
///   sauf Update, `design/screens/update.md` §4) sera posée dans la zone du bas, avec la barre, par la même `safeAreaInset` ;
/// - l'Update iOS (UPDATE-3) remplace `UpdateSoonView` et nourrit `central` (la couleur de la pastille) ;
/// - la suppression de compte (AUTH-5c/d) remplace `DeleteAccountSoonView`, atteinte par Mon compte.
///
/// La barre n'est visible que sur les écrans racines : elle disparaît dès qu'un écran est empilé (les Réglages, l'écran d'une Miff, la
/// création, les lieux, qui vivent dans la navigation de l'hôte), avec le clavier, et pour un compte qui n'a aucune Miff (l'écran
/// « Pas encore de Miff » est plein écran, décision Q13 de l'onboarding). Le contenu passe SOUS la barre : chaque défilement garde
/// sa marge basse grâce à la zone sûre que la barre déclare.
struct MainShellView<Feed: View>: View {
    private let environment: AppEnvironment
    private let miffs: DirectoryState
    private let onOpenMiff: (String) -> Void
    private let feed: Feed
    @StateObject private var profile: ProfileTabViewModel
    @State private var selected: MainTab = .feed
    @State private var settingsPath: [SettingsRoute] = []
    @State private var keyboardVisible = false

    init(
        environment: AppEnvironment,
        miffs: DirectoryState,
        onOpenMiff: @escaping (String) -> Void,
        @ViewBuilder feed: () -> Feed
    ) {
        self.environment = environment
        self.miffs = miffs
        self.onOpenMiff = onOpenMiff
        self.feed = feed()
        let session = environment.session
        _profile = StateObject(
            wrappedValue: ProfileTabViewModel(reader: environment.profileReader, currentUid: { session.currentUid })
        )
    }

    /// Un compte sans Miff n'a pas de barre : rien à y faire.
    private var hasNoMiff: Bool {
        if case .ready(let ready) = miffs {
            return ready.miffs.isEmpty
        }
        return false
    }

    private var barVisible: Bool {
        !keyboardVisible && settingsPath.isEmpty && !hasNoMiff
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                TabLayer(isActive: selected == .feed) {
                    feed
                }
                TabLayer(isActive: selected == .profile) {
                    profileStack
                }
                if selected == .update {
                    UpdateSoonView()
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if barVisible {
                    MainTabBar(config: .current, selected: selected, central: .neutral, onSelect: { selected = $0 })
                        .padding(.bottom, TabBarLayout.bottomPadding(safeAreaBottom: proxy.safeAreaInsets.bottom))
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            keyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            keyboardVisible = false
        }
    }

    // MARK: - Profil et Réglages

    /// Le Profil a sa propre pile : les Réglages et leurs écrans s'y empilent, et la barre se cache tant qu'elle n'est pas vide.
    private var profileStack: some View {
        NavigationStack(path: $settingsPath) {
            ProfileTabView(
                state: profile.state,
                miffs: miffs,
                actions: ProfileTabActions(
                    onOpenMyInfo: { push(.myInfo) },
                    onOpenSettings: { push(.settings) },
                    onRetry: { profile.retry() },
                    onOpenMiff: onOpenMiff
                )
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: SettingsRoute.self) { route in
                destination(route)
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
    }

    @ViewBuilder
    private func destination(_ route: SettingsRoute) -> some View {
        switch route {
        case .settings:
            SettingsListView(
                actions: SettingsActions(
                    onBack: { pop() },
                    onOpenMyInfo: { push(.myInfo) },
                    onOpenAccount: { push(.account) },
                    onOpenPrivacy: { push(.privacy) }
                ),
                build: .current
            )
        case .myInfo:
            MyInfoView(
                state: profile.state,
                tools: environment.phoneTools,
                onBack: { pop() },
                onRetry: { profile.retry() }
            )
        case .account:
            AccountView(environment: environment, onBack: { pop() }, onDelete: { push(.deleteAccount) })
        case .deleteAccount:
            DeleteAccountSoonView(onBack: { pop() })
        case .privacy:
            PrivacyView(
                permissions: environment.devicePermissions,
                onBack: { pop() },
                onOpenDetectionHelp: { push(.detectionHelp) }
            )
        case .detectionHelp:
            DetectionHelpView(onBack: { pop() })
        }
    }

    /// Deux touchers rapprochés ouvrent un seul écran, pas deux.
    private func push(_ route: SettingsRoute) {
        if settingsPath.last != route {
            settingsPath.append(route)
        }
    }

    private func pop() {
        if !settingsPath.isEmpty {
            settingsPath.removeLast()
        }
    }
}
