import SwiftUI

/// Les écrans du flux Miff dans une navigation. Seul l'identifiant de la Miff voyage dans une destination : jamais le code,
/// jamais le nom.
enum MiffRoute: Hashable {
    case create
    case join
    case miff(String)
}

/// L'étape « créer ou rejoindre » de l'onboarding (une personne qui a un profil et pas de Miff, destination `miffChoice`) : le
/// choix, puis la création ou l'entrée. L'onboarding n'est complet que quand une Miff a été créée ou rejointe (CLAUDE.md) :
/// cet hôte n'est quitté que quand la session passe au fil (`continueAfterMiff`), jamais avant.
@MainActor
struct MiffOnboardingHost: View {
    private let environment: AppEnvironment
    @State private var path: [MiffRoute] = []

    init(environment: AppEnvironment) {
        self.environment = environment
    }

    var body: some View {
        NavigationStack(path: $path) {
            MiffChoiceView(
                onCreate: { open(.create) },
                onJoin: { open(.join) },
                onSignOut: { signOut() }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MiffRoute.self) { route in
                destination(route)
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
    }

    @ViewBuilder
    private func destination(_ route: MiffRoute) -> some View {
        switch route {
        case .create:
            CreateMiffView(
                context: environment.miffFlowContext(),
                onBack: { pop() },
                onContinue: { environment.session.continueAfterMiff() }
            )
        case .join:
            JoinMiffView(
                context: environment.miffFlowContext(),
                limiter: environment.joinLimiter,
                navigation: JoinNavigation(
                    onBack: { pop() },
                    onJoined: { environment.session.continueAfterMiff() },
                    onOpenMiff: { _ in openExisting() }
                )
            )
        case .miff:
            // Pas d'écran de Miff avant la fin de l'onboarding : la personne n'en a pas encore.
            EmptyView()
        }
    }

    /// « Ouvrir la Miff » (« tu fais déjà partie de cette Miff ») : la personne est dans cette Miff, donc le compte en a une
    /// (la fin de l'onboarding est comptée) ; l'app passe au fil.
    private func openExisting() {
        environment.session.onMiffEntered(via: .joined)
        Task {
            await environment.miffDirectory.refresh()
        }
        environment.session.continueAfterMiff()
    }

    /// Deux touchers rapprochés ouvrent un seul écran, pas deux.
    private func open(_ route: MiffRoute) {
        if path.last != route {
            path.append(route)
        }
    }

    private func pop() {
        if !path.isEmpty {
            path.removeLast()
        }
    }

    private func signOut() {
        Task {
            await environment.session.signOut()
        }
    }
}

/// L'app une fois l'onboarding fini (une Miff existe) : l'accueil provisoire, et par-dessus lui les écrans du flux Miff (créer,
/// rejoindre, l'écran d'une Miff). Le message unique d'une perte d'accès (décision QM8) couvre tout, pour que rien de la Miff
/// perdue ne reste visible derrière.
@MainActor
struct MiffHomeHost: View {
    private let environment: AppEnvironment
    @ObservedObject private var directory: MiffDirectory
    @State private var path: [MiffRoute] = []

    init(environment: AppEnvironment) {
        self.environment = environment
        _directory = ObservedObject(wrappedValue: environment.miffDirectory)
    }

    var body: some View {
        ZStack {
            NavigationStack(path: $path) {
                MiffHomeContent(state: directory.state, actions: homeActions)
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: MiffRoute.self) { route in
                        destination(route)
                            .toolbar(.hidden, for: .navigationBar)
                    }
            }
            .accessibilityHidden(directory.notices.first != nil)
            if let notice = directory.notices.first {
                AccessLostView(miffName: notice.name, onAcknowledge: { acknowledge(notice) })
            }
        }
        // Une seule lecture des Miffs par session à l'ouverture (pas une à chaque retour sur l'écran) : le contrôle auprès du
        // serveur que l'accès à chaque Miff existe encore, avant d'en montrer quoi que ce soit (ticket §5.7, critère D2).
        .task {
            await directory.refreshIfNeeded()
        }
    }

    private var homeActions: MiffHomeActions {
        MiffHomeActions(
            onRetry: { retry() },
            onSelect: { directory.select(miffId: $0) },
            onCreate: { open(.create) },
            onJoin: { open(.join) },
            onOpenMiff: { open(.miff($0)) },
            onSignOut: { signOut() }
        )
    }

    @ViewBuilder
    private func destination(_ route: MiffRoute) -> some View {
        switch route {
        case .create:
            CreateMiffView(
                context: environment.miffFlowContext(),
                onBack: { pop() },
                onContinue: { pop() }
            )
        case .join:
            JoinMiffView(
                context: environment.miffFlowContext(),
                limiter: environment.joinLimiter,
                navigation: JoinNavigation(
                    onBack: { pop() },
                    onJoined: { pop() },
                    onOpenMiff: { openExisting($0) }
                )
            )
        case .miff(let miffId):
            MiffScreenView(
                miffId: miffId,
                dependencies: environment.miffScreenDependencies(),
                onBack: { pop() }
            )
        }
    }

    /// « Ouvrir la Miff » : l'écran de saisie du code est remplacé par l'écran de cette Miff.
    private func openExisting(_ miffId: String) {
        if !path.isEmpty {
            path.removeLast()
        }
        open(.miff(miffId))
    }

    /// Seul l'écran de CETTE Miff est quitté : une autre Miff ouverte derrière (Premium) reste où elle est.
    private func acknowledge(_ notice: LostNotice) {
        directory.dismissNotice()
        if path.last == .miff(notice.miffId) {
            path.removeLast()
        }
    }

    private func retry() {
        Task {
            await directory.refresh()
        }
    }

    private func open(_ route: MiffRoute) {
        if path.last != route {
            path.append(route)
        }
    }

    private func pop() {
        if !path.isEmpty {
            path.removeLast()
        }
    }

    private func signOut() {
        Task {
            await environment.session.signOut()
        }
    }
}
