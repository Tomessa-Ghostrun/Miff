import SwiftUI

// Le haut RÉEL de la barre d'onglets, donné à l'Update par la coquille (`MainShellView`), et le réglage qui en tire la marge basse d'une
// vue (voir `UpdateScreenGeometry`). Aucune constante de hauteur : tout vient de la mesure.

/// Le haut de la barre, en coordonnées globales (nil : la barre n'est pas à l'écran ou n'est pas encore mesurée).
private struct TabBarTopKey: EnvironmentKey {
    static let defaultValue: CGFloat? = nil
}

extension EnvironmentValues {
    var tabBarTopY: CGFloat? {
        get { self[TabBarTopKey.self] }
        set { self[TabBarTopKey.self] = newValue }
    }
}

/// Ce que la barre mesure d'elle-même, remonté jusqu'à la coquille.
struct TabBarTopPreference: PreferenceKey {
    static let defaultValue: CGFloat? = nil

    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
        value = nextValue() ?? value
    }
}

/// À poser en `background` de la barre : mesure son cadre réel (hors marges de la zone sûre), en coordonnées globales.
struct TabBarTopReader: View {
    var body: some View {
        GeometryReader { proxy in
            Color.clear.preference(key: TabBarTopPreference.self, value: proxy.frame(in: .global).minY)
        }
    }
}

/// Retire de la vue ce que la barre recouvre : le contenu s'arrête au-dessus de la barre, même si la vue descend jusqu'au bas de l'écran.
private struct AboveTabBar: ViewModifier {
    @Environment(\.tabBarTopY) private var barTopY

    func body(content: Content) -> some View {
        GeometryReader { proxy in
            let bottom = proxy.frame(in: .global).maxY
            let overlap = UpdateScreenGeometry.barOverlap(viewMaxY: bottom, barTopY: barTopY)
            content
                .padding(.bottom, overlap)
                .frame(width: proxy.size.width, height: proxy.size.height)
        }
    }
}

extension View {
    /// Le contenu reste entièrement au-dessus de la barre d'onglets (hauteur mesurée, jamais écrite en dur).
    func aboveTabBar() -> some View {
        modifier(AboveTabBar())
    }
}
