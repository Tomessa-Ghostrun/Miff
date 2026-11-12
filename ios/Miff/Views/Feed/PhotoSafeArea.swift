import SwiftUI
import UIKit

// Les zones sûres (encoche, barre d'accueil) lues pour le plein écran de la photo. Dans un `fullScreenCover`, le `GeometryReader` peut annoncer des
// marges NULLES (ou fausses) sur un vrai appareil alors que le simulateur les donne bien (retour du PM, TestFlight 32 : la croix et l'icône écrasées
// contre l'heure et la batterie, le bouton collé au bord bas). On lit donc AUSSI celles de la fenêtre, et on garde le plus grand des deux ; le
// calcul final (planchers de sécurité et marges) est du Swift pur et testé : `PhotoFullScreenLayout`. Lecture seule : aucune donnée, aucun réseau.
enum PhotoSafeArea {
    /// Les zones sûres à utiliser : le plus grand, côté par côté, de ce que le lecteur de géométrie annonce et de ce que la fenêtre annonce.
    static func read(proxy: EdgeInsets) -> EdgeInsets {
        #if DEBUG
        // Capture de la CI : simule le cas du `fullScreenCover` qui annonce des marges NULLES, pour prouver que les planchers tiennent.
        if DiagnosticLaunchOptions.forceZeroSafeArea {
            return EdgeInsets()
        }
        #endif
        // Horizontal : le lecteur seul (la fenêtre donne gauche/droite, pas début/fin : faux en droite-à-gauche).
        let window = windowInsets()
        return EdgeInsets(
            top: max(proxy.top, window.top),
            leading: proxy.leading,
            bottom: max(proxy.bottom, window.bottom),
            trailing: proxy.trailing
        )
    }

    private static func windowInsets() -> UIEdgeInsets {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
        return (windows.first(where: \.isKeyWindow) ?? windows.first)?.safeAreaInsets ?? .zero
    }
}
