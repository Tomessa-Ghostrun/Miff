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
/// création, les lieux : tous poussés dans la pile UNIQUE de l'hôte, qui les dessine par-dessus cette coquille), avec le clavier, et
/// pour un compte qui n'a aucune Miff (l'écran « Pas encore de Miff » est plein écran, décision Q13 de l'onboarding). Le contenu passe
/// SOUS la barre : chaque défilement garde sa marge basse grâce à la zone sûre que la barre déclare.
///
/// Aucune pile de navigation n'est créée ici : une pile imbriquée dans celle de l'hôte (où l'écran de la Miff est empilé) est ce qui
/// faisait planter l'app au toucher d'une carte « Mes Miffs » sur le Profil.
struct MainShellView<Feed: View>: View {
    private let miffs: DirectoryState
    @ObservedObject private var profile: ProfileTabViewModel
    private let onOpenMiff: (String) -> Void
    private let onOpenSettings: (SettingsRoute) -> Void
    private let feed: Feed
    @State private var selected: MainTab = .feed
    @State private var keyboardVisible = false

    init(
        miffs: DirectoryState,
        profile: ProfileTabViewModel,
        onOpenMiff: @escaping (String) -> Void,
        onOpenSettings: @escaping (SettingsRoute) -> Void,
        @ViewBuilder feed: () -> Feed
    ) {
        self.miffs = miffs
        _profile = ObservedObject(wrappedValue: profile)
        self.onOpenMiff = onOpenMiff
        self.onOpenSettings = onOpenSettings
        self.feed = feed()
    }

    private var hasNoMiff: Bool {
        MainShellRules.hasNoMiff(miffs)
    }

    /// L'onglet réellement montré : le Feed dès que la dernière Miff a disparu.
    private var shownTab: MainTab {
        MainShellRules.effectiveTab(selected: selected, hasNoMiff: hasNoMiff)
    }

    /// Les écrans empilés recouvrent cette coquille (pile de l'hôte) : `pathIsEmpty` vaut toujours vrai ici.
    private var barVisible: Bool {
        MainShellRules.barVisible(keyboardVisible: keyboardVisible, pathIsEmpty: true, directory: miffs)
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                TabLayer(isActive: shownTab == .feed) {
                    feed
                }
                TabLayer(isActive: shownTab == .profile) {
                    ProfileTabView(
                        state: profile.state,
                        miffs: miffs,
                        actions: ProfileTabActions(
                            onOpenMyInfo: { onOpenSettings(.myInfo) },
                            onOpenSettings: { onOpenSettings(.settings) },
                            onRetry: { profile.retry() },
                            onOpenMiff: onOpenMiff
                        )
                    )
                }
                if shownTab == .update {
                    UpdateSoonView()
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if barVisible {
                    MainTabBar(config: .current, selected: shownTab, central: .neutral, onSelect: { selected = $0 })
                        .padding(.bottom, TabBarLayout.bottomPadding(safeAreaBottom: proxy.safeAreaInsets.bottom))
                }
            }
        }
        // La dernière Miff a disparu : on repart du Feed.
        .onChange(of: hasNoMiff) { noMiff in
            if noMiff {
                selected = .feed
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            keyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            keyboardVisible = false
        }
    }
}
