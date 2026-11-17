import SwiftUI

// L'apparence de l'app (claire imposée : les maquettes n'ont pas de mode sombre) et UNE exception : pendant que le viseur ou l'aperçu de la photo
// sont à l'écran (fonds noir et gris très sombre), la barre d'état doit être EN CLAIR. En apparence claire elle est noire : l'heure, le réseau
// et la batterie disparaissaient sur le noir de la caméra (retour du PM sur l'iPhone 16 : indicateurs « à moitié masqués »).
//
// SwiftUI ne donne le style de la barre d'état que par le schéma de couleurs préféré, et UN SEUL point doit le décider (deux `preferredColorScheme`
// dans la même hiérarchie se disputent, sans ordre sûr) : la racine de l'app (`MiffApp`) lit CET objet. Les écrans sombres le demandent par
// `lightStatusBar()` ; les demandes sont COMPTÉES (le viseur qui part et l'aperçu qui arrive se chevauchent un instant : un simple « vrai /
// faux » dépendrait de l'ordre). Aucune donnée, aucun réseau.

final class AppAppearance: ObservableObject {
    static let shared = AppAppearance()

    /// Combien d'écrans sombres demandent une barre d'état claire en ce moment.
    @Published private(set) var lightStatusBarRequests = 0

    /// Au moins un écran sombre est à l'écran.
    var wantsLightStatusBar: Bool {
        lightStatusBarRequests > 0
    }

    /// Le schéma de couleurs de la racine : sombre (donc barre d'état claire) seulement quand un écran sombre le demande.
    var colorScheme: ColorScheme {
        wantsLightStatusBar ? .dark : .light
    }

    func beginLightStatusBar() {
        lightStatusBarRequests += 1
    }

    func endLightStatusBar() {
        lightStatusBarRequests = max(0, lightStatusBarRequests - 1)
    }
}

/// À la racine de l'app : l'apparence claire imposée, sauf quand un écran sombre demande la barre d'état claire.
struct AppColorSchemeModifier: ViewModifier {
    @ObservedObject private var appearance = AppAppearance.shared

    func body(content: Content) -> some View {
        content.preferredColorScheme(appearance.colorScheme)
    }
}

private struct LightStatusBar: ViewModifier {
    func body(content: Content) -> some View {
        content
            .onAppear { AppAppearance.shared.beginLightStatusBar() }
            .onDisappear { AppAppearance.shared.endLightStatusBar() }
    }
}

extension View {
    /// L'écran est sombre : la barre d'état (heure, réseau, batterie) passe en clair tant qu'il est à l'écran.
    func lightStatusBar() -> some View {
        modifier(LightStatusBar())
    }
}
