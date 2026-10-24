import SwiftUI

/// Premier écran de l'app. Builds Debug et Internal : le diagnostic interne. Build Release : l'écran d'accueil,
/// sans aucun accès au diagnostic (son code n'est même pas compilé).
struct RootView: View {
    let environment: AppEnvironment

    var body: some View {
        #if DEBUG || INTERNAL
        DiagnosticRootView(environment: environment)
        #else
        HomeView()
        #endif
    }
}
