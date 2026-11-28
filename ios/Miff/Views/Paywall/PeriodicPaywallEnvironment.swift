import SwiftUI

// Ce que l'hôte de l'offre périodique donne aux écrans qui sont dessous (ticket docs/tickets/paywall-periodique.md, PAY-PER-I). Rien de personnel : des signaux d'état.

private struct OverlayPresenceKey: EnvironmentKey {
    static let defaultValue: OverlayPresence? = nil
}

private struct PeriodicSignalsKey: EnvironmentKey {
    static let defaultValue: PeriodicPaywallSignals? = nil
}

extension EnvironmentValues {
    /// Où les surcouches disent qu'elles sont ouvertes (fourni par `periodicPaywallHost` ; nil, et donc sans effet, dans un aperçu ou un écran qui ne le fournit pas).
    var overlayPresence: OverlayPresence? {
        get { self[OverlayPresenceKey.self] }
        set { self[OverlayPresenceKey.self] = newValue }
    }

    /// Les points où l'app dit à l'offre périodique ce qu'elle seule sait ; nil hors de l'hôte.
    var periodicSignals: PeriodicPaywallSignals? {
        get { self[PeriodicSignalsKey.self] }
        set { self[PeriodicSignalsKey.self] = newValue }
    }
}

/// Dit à l'offre périodique qu'une surcouche (feuille, boîte de dialogue, menu, photo plein écran, fenêtre d'avis) est ouverte tant que `isOpen` est vrai ET que la vue est à
/// l'écran. À poser sur le CONTENU de la surcouche (une feuille, une photo plein écran) ou, pour une boîte de dialogue ou une alerte qui n'a pas de contenu à elle, sur la vue
/// qui la présente, avec la liaison qui l'ouvre. Équivalent SwiftUI d'`OverlayPresent()` d'Android : `PeriodicPaywallGuardTests` échoue si une surcouche des onglets ne l'appelle pas.
private struct OverlayPresentModifier: ViewModifier {
    @Environment(\.overlayPresence) private var presence
    let isOpen: Bool
    @State private var reported = false

    func body(content: Content) -> some View {
        content
            .onAppear { sync() }
            .onChange(of: isOpen) { _ in sync() }
            .onDisappear { release() }
    }

    private func sync() {
        if isOpen && !reported {
            reported = true
            presence?.onOverlayOpened()
        } else if !isOpen && reported {
            reported = false
            presence?.onOverlayClosed()
        }
    }

    private func release() {
        if reported {
            reported = false
            presence?.onOverlayClosed()
        }
    }
}

/// Dit à l'offre périodique où est un menu du système : il ne dit pas lui-même qu'il est ouvert, un appui dessus le fait tenir pour ouvert jusqu'au toucher suivant.
private struct MenuAreaKey: PreferenceKey {
    static let defaultValue: CGRect = .zero

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}

private struct MenuAreaModifier: ViewModifier {
    @Environment(\.periodicSignals) private var signals
    @State private var id = UUID()

    func body(content: Content) -> some View {
        let areas = signals?.menuAreas
        let identifier = id
        return content
            .background(
                GeometryReader { proxy in
                    Color.clear.preference(key: MenuAreaKey.self, value: proxy.frame(in: .global))
                }
            )
            .onPreferenceChange(MenuAreaKey.self) { frame in
                areas?.set(identifier, frame: frame)
            }
            .onDisappear { areas?.set(identifier, frame: nil) }
    }
}

extension View {
    /// Une surcouche qui recouvre les onglets est ouverte (`isOpen`) : l'offre périodique attend 1,5 s APRÈS sa fermeture.
    func overlayPresent(_ isOpen: Bool = true) -> some View {
        modifier(OverlayPresentModifier(isOpen: isOpen))
    }

    /// Cette vue est un menu du système (elle ne dit pas qu'il s'ouvre) : un appui dessus tient la surcouche pour ouverte jusqu'au toucher suivant.
    func reportsMenuArea() -> some View {
        modifier(MenuAreaModifier())
    }
}
