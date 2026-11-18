import SwiftUI
import UIKit

// Ce que la fenêtre de l'app dit de l'écran (sa taille, sa zone sûre du haut), lu pour le viseur et l'aperçu de l'Update. Le lecteur de géométrie de
// SwiftUI peut annoncer des zones sûres nulles ou fausses sur un vrai appareil (voir `PhotoSafeArea`) ; la fenêtre, elle, sait. Lecture seule :
// aucune donnée, aucun réseau. Le calcul (plancher, marges) est du Swift pur et testé : `ViewfinderLayout`.

struct ViewfinderWindowReading {
    /// La taille de la fenêtre (l'écran entier), nil quand aucune fenêtre n'existe encore.
    let size: CGSize?
    /// Les zones sûres à respecter : le plus grand, côté par côté, de celles du lecteur et de celles de la fenêtre.
    let insets: EdgeInsets

    /// Les mêmes zones sûres, pour le calcul pur (`ViewfinderLayout`).
    var layoutInsets: ViewfinderLayout.Insets {
        ViewfinderLayout.Insets(
            top: insets.top, leading: insets.leading, bottom: insets.bottom, trailing: insets.trailing
        )
    }
}

enum ViewfinderWindow {
    /// La lecture de la fenêtre, mêlée à ce que le lecteur de géométrie annonce (`proxy`).
    static func read(proxy: EdgeInsets) -> ViewfinderWindowReading {
        let window = keyWindow()
        #if DEBUG
        // Capture de la CI : simule des zones sûres NULLES (lecteur ET fenêtre) ; la taille de la fenêtre, elle, reste vraie.
        if DiagnosticLaunchOptions.forceZeroSafeArea {
            return ViewfinderWindowReading(size: window?.bounds.size, insets: EdgeInsets())
        }
        #endif
        let safe = window?.safeAreaInsets ?? .zero
        return ViewfinderWindowReading(
            size: window?.bounds.size,
            insets: EdgeInsets(
                top: max(proxy.top, safe.top),
                leading: proxy.leading,
                bottom: max(proxy.bottom, safe.bottom),
                trailing: proxy.trailing
            )
        )
    }

    static func keyWindow() -> UIWindow? {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
        return windows.first(where: \.isKeyWindow) ?? windows.first
    }
}

/// Le contenu reste SOUS la zone sûre du haut (barre d'état, île dynamique, encoche), même si le lecteur de géométrie l'a annoncée nulle : on
/// ajoute seulement ce qui manque (0 dans le cas normal, donc rien ne bouge). Aucune hauteur écrite en dur : c'est la mesure qui décide.
private struct TopSafeAreaFloor: ViewModifier {
    func body(content: Content) -> some View {
        GeometryReader { proxy in
            let reading = ViewfinderWindow.read(proxy: proxy.safeAreaInsets)
            let screen = ViewfinderLayout.screenSize(
                visible: proxy.size, insets: reading.layoutInsets, window: reading.size
            )
            let portrait = ViewfinderLayout.isPortrait(screen)
            let inset = ViewfinderLayout.topInset(readTop: reading.insets.top, isPortrait: portrait)
            let extra = ViewfinderLayout.extraTopPadding(topInset: inset, layoutTop: proxy.safeAreaInsets.top)
            content
                .padding(.top, extra)
                .frame(width: proxy.size.width, height: proxy.size.height)
        }
    }
}

extension View {
    /// Le contenu ne passe jamais sous la barre d'état ni l'île dynamique.
    func topSafeAreaFloor() -> some View {
        modifier(TopSafeAreaFloor())
    }
}
