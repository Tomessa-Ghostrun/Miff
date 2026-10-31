#if DEBUG || INTERNAL
import SwiftUI

/// Racine des builds Debug et Internal : le diagnostic, avec un lien qui ouvre le vrai parcours de l'app.
@MainActor
struct DiagnosticRootView: View {
    @StateObject private var viewModel: DiagnosticViewModel
    @ObservedObject private var privacy: PrivacySettings
    private let environment: AppEnvironment

    init(environment: AppEnvironment) {
        self.environment = environment
        _viewModel = StateObject(
            wrappedValue: DiagnosticViewModel(
                repository: FirebaseDiagnosticRepository(),
                tracker: environment.analyticsTracker
            )
        )
        _privacy = ObservedObject(wrappedValue: environment.privacySettings)
    }

    var body: some View {
        NavigationStack {
            root
        }
        .modifier(ForcedLayoutDirection(rightToLeft: DiagnosticLaunchOptions.forceRightToLeft))
    }

    @ViewBuilder
    private var root: some View {
        #if DEBUG
        if let preview = DiagnosticLaunchOptions.preview {
            ScreenPreview(name: preview, environment: environment)
        } else if DiagnosticLaunchOptions.showApp {
            AppRootView(environment: environment)
        } else if DiagnosticLaunchOptions.showHome {
            HomeView()
        } else {
            DiagnosticView(viewModel: viewModel, privacy: privacy, environment: environment)
        }
        #else
        DiagnosticView(viewModel: viewModel, privacy: privacy, environment: environment)
        #endif
    }
}

/// Force la mise en page de droite à gauche (captures de la CI : le catalogue n'a pas de langue droite-à-gauche).
private struct ForcedLayoutDirection: ViewModifier {
    let rightToLeft: Bool

    func body(content: Content) -> some View {
        if rightToLeft {
            content.environment(\.layoutDirection, .rightToLeft)
        } else {
            content
        }
    }
}
#endif
