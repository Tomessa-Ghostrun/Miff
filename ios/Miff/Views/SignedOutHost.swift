import SwiftUI

/// Les écrans de la personne non connectée.
enum SignedOutRoute: Hashable {
    case signUp
    case signIn
    /// « Mot de passe oublié » (AUTH-4), avec l'adresse déjà saisie sur l'écran de connexion, s'il y en a une.
    case reset(email: String)
}

/// Navigation de la personne non connectée : carrousel de bienvenue (première ouverture seulement), puis inscription et
/// connexion, et « Mot de passe oublié ». Ordre à conserver : splash -> (carrousel si première ouverture) -> inscription /
/// connexion.
///
/// - Le carrousel n'apparaît qu'à la toute première ouverture : aucune session, app jamais utilisée, carrousel jamais
///   validé (`shouldShowWelcomeCarousel`) ; le drapeau est posé à « Commencer » ou « J'ai déjà un compte ». Après une
///   déconnexion : connexion directement (décision Q11). La flèche de retour de l'inscription ramène au carrousel.
/// - La flèche n'existe que s'il y a un écran précédent ; le geste de retour natif (balayage depuis le bord) a le même effet.
@MainActor
struct SignedOutHost: View {
    private let environment: AppEnvironment
    private let start: AppDestination

    @State private var path: [SignedOutRoute] = []
    /// Décidé UNE fois, à l'arrivée sur cet écran : le drapeau change quand on touche « Commencer », et la racine ne doit
    /// pas changer sous la navigation.
    @State private var showCarousel: Bool

    init(environment: AppEnvironment, start: AppDestination) {
        self.environment = environment
        self.start = start
        _showCarousel = State(
            initialValue: shouldShowWelcomeCarousel(
                start: start, carouselSeen: environment.onboardingFlags.carouselSeen)
        )
    }

    /// L'écran racine (sans flèche de retour) quand ce n'est pas le carrousel.
    private var rootRoute: SignedOutRoute? {
        if showCarousel {
            return nil
        }
        return start == .signIn ? .signIn : .signUp
    }

    var body: some View {
        NavigationStack(path: $path) {
            root
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: SignedOutRoute.self) { route in
                    screen(for: route)
                        .toolbar(.hidden, for: .navigationBar)
                }
        }
    }

    @ViewBuilder
    private var root: some View {
        if showCarousel {
            WelcomeCarouselView(
                onStart: {
                    environment.onboardingFlags.markCarouselSeen()
                    path.append(.signUp)
                },
                onHaveAccount: {
                    environment.onboardingFlags.markCarouselSeen()
                    path.append(.signIn)
                }
            )
        } else {
            screen(for: start == .signIn ? .signIn : .signUp, isRoot: true)
        }
    }

    @ViewBuilder
    private func screen(for route: SignedOutRoute, isRoot: Bool = false) -> some View {
        let back: (() -> Void)? = isRoot ? nil : { pop() }
        switch route {
        case .signUp:
            SignUpView(environment: environment, onBack: back, onGoToSignIn: { show(.signIn) })
        case .signIn:
            SignInView(
                environment: environment,
                onBack: back,
                navigation: SignInNavigation(
                    onGoToSignUp: { show(.signUp) },
                    onForgotPassword: { email in openReset(email: email) }
                )
            )
        case .reset(let email):
            PasswordResetView(environment: environment, initialEmail: email, onBack: { pop() })
        }
    }

    /// Un écran de moins, mais seulement s'il y en a un (deux touchers rapprochés ne doivent pas vider la pile).
    private func pop() {
        if !path.isEmpty {
            path.removeLast()
        }
    }

    /// Va à l'écran demandé : revient dessus s'il est déjà dans la pile (ou à la racine), sinon l'ajoute.
    private func show(_ target: SignedOutRoute) {
        if rootRoute == target {
            path.removeAll()
        } else if let index = path.lastIndex(of: target) {
            path.removeSubrange((index + 1)...)
        } else {
            path.append(target)
        }
    }

    /// Deux touchers rapprochés ouvrent un seul écran « Mot de passe oublié », pas deux.
    private func openReset(email: String) {
        if case .reset = path.last {
            return
        }
        path.append(.reset(email: email))
    }
}
