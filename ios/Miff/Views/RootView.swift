import SwiftUI

/// Premier écran de l'app. Builds Debug et Internal : le diagnostic interne, d'où un lien ouvre le vrai parcours de l'app
/// (comme sur Android). Build Release : directement le vrai parcours (inscription, connexion, profil...), sans aucun accès
/// au diagnostic (son code n'est même pas compilé).
struct RootView: View {
    let environment: AppEnvironment

    var body: some View {
        #if DEBUG || INTERNAL
        DiagnosticRootView(environment: environment)
        #else
        AppRootView(environment: environment)
        #endif
    }
}
