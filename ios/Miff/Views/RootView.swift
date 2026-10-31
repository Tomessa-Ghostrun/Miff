import SwiftUI

/// Premier écran de l'app. Build Debug (simulateur, CI) : le diagnostic interne, d'où un lien ouvre le vrai parcours de l'app.
/// Build Internal (TestFlight du PM) : directement le vrai parcours, le diagnostic restant derrière un appui long discret
/// (`InternalRootView`). Build Release : le vrai parcours, sans aucun accès au diagnostic (son code n'est même pas compilé).
struct RootView: View {
    let environment: AppEnvironment

    var body: some View {
        #if DEBUG
        DiagnosticRootView(environment: environment)
        #elseif INTERNAL
        InternalRootView(environment: environment)
        #else
        AppRootView(environment: environment)
        #endif
    }
}
