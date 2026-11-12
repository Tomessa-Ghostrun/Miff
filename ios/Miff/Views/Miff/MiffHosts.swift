import SwiftUI

/// Les écrans du flux Miff dans une navigation. Seuls des identifiants voyagent dans une destination (la Miff, le lieu, un mot fixe
/// pour la raison de l'offre) : jamais le code, jamais un nom, une adresse ni une coordonnée.
enum MiffRoute: Hashable {
    case create
    case join
    case miff(String)
    /// Le formulaire d'un lieu : nouveau (`placeId` nil), ou le lieu à modifier, avec la Miff d'où il a été ouvert.
    case placeForm(placeId: String?, miffId: String?)
    case placeDetail(miffId: String, placeId: String)
    case placePaywall(PaywallReason)
    /// Un écran des Réglages (ONB-7) : poussé dans la MÊME pile que les écrans de la Miff, jamais dans une pile imbriquée.
    case settings(SettingsRoute)

    var isSettingsScreen: Bool {
        if case .settings = self {
            return true
        }
        return false
    }

    /// « Mes informations » : le seul écran des Réglages qu'un compte sans Miff n'a pas.
    var isMyInfoScreen: Bool {
        self == .settings(.myInfo)
    }

    /// Cet écran dépend de cette Miff : il part avec elle quand son accès est perdu.
    func belongs(to miffId: String) -> Bool {
        switch self {
        case .miff(let id):
            return id == miffId
        case .placeDetail(let id, _):
            return id == miffId
        case .placeForm(_, let id):
            return id == miffId
        case .create, .join, .placePaywall, .settings:
            return false
        }
    }
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
                onSignOut: { signOut() },
                onOpenSettings: { open(.settings(.settings)) }
            )
            .toolbar(.hidden, for: .navigationBar)
            .swipeBackEnabled()
            .navigationDestination(for: MiffRoute.self) { route in
                destination(route)
                    .toolbar(.hidden, for: .navigationBar)
                    .swipeBackEnabled()
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
        case .settings(let screen):
            // Les Réglages RESTREINTS d'un compte sans Miff (décision du PM du 2026-11-07) : dans cette même pile, sans profil lu
            // (aucune lecture de plus), pour pouvoir supprimer son compte depuis l'app.
            SettingsDestinationView(
                route: screen,
                environment: environment,
                profile: nil,
                restricted: true,
                navigate: { open(.settings($0)) },
                onBack: { pop() }
            )
        case .miff, .placeForm, .placeDetail, .placePaywall:
            // Pas d'écran de Miff ni de lieu avant la fin de l'onboarding : la personne n'en a pas encore.
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
        // Un créateur entre dans un lieu ou dans le formulaire d'un nouveau : les listes de lieux du Profil seront relues à leur retour.
        profilePlaces.routeOpened(route) { isCreator(of: $0) }
        if path.last != route {
            path.append(route)
        }
    }

    private func pop() {
        if !path.isEmpty {
            path.removeLast()
        }
    }

    /// La personne connectée a-t-elle créé cette Miff ? (le champ `ownerUid` de l'annuaire ; inconnu : non.)
    private func isCreator(of miffId: String) -> Bool {
        PlaceViewerRole.isCreator(ownerUid: directory.state.ownerUid(of: miffId), myUid: environment.session.currentUid)
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
    /// Le message calme d'un lieu enregistré, en mémoire seulement (LOC-3).
    @ObservedObject private var placeNotices: PlaceNotices
    @State private var path: [MiffRoute] = []
    /// Le profil de la personne (onglet Profil, Mes informations) : lu une fois par session, gardé ici pour les deux écrans.
    @StateObject private var profile: ProfileTabViewModel
    /// Les membres des cartes de Miff du Profil (photos, nombre) : ce que le Feed a lu pour la Miff affichée, plus une lecture ponctuelle
    /// des autres, une fois par ouverture du Profil. Créé avec la session de l'hôte, il disparaît avec elle.
    @StateObject private var memberPreviews: MiffMemberPreviews
    /// Les lieux sous le carrousel du Profil, pour tous les membres de la Miff regardée (décision du PM, 2026-11-11) : la Miff du Feed
    /// est reprise sans lecture, les autres sont lues UNE fois (cache de 5 minutes), jamais d'écouteur. Créé avec la session de l'hôte.
    @StateObject private var profilePlaces: ProfilePlacesViewModel
    /// MODE VOYAGE (VOY-2I) : la carte du Profil, créée pour la session ; le voyage est écrit par le moteur de suivi, nulle part ailleurs.
    @StateObject private var travel: TravelViewModel
    /// L'Update (UPDATE-3) : l'écran du bouton central, créé pour la session (la photo prise y vit, en mémoire seulement).
    @StateObject private var updateModel: UpdateViewModel
    /// La banderole courte de la suppression (reprise au lancement, fin pendant que la personne est ailleurs).
    @State private var banner: DeletionBanner?

    init(environment: AppEnvironment) {
        self.environment = environment
        _directory = ObservedObject(wrappedValue: environment.miffDirectory)
        _placeNotices = ObservedObject(wrappedValue: environment.placeNotices)
        let session = environment.session
        _profile = StateObject(
            wrappedValue: ProfileTabViewModel(reader: environment.profileReader, currentUid: { session.currentUid })
        )
        _memberPreviews = StateObject(wrappedValue: MiffMemberPreviews(reader: environment.memberPreviewReader))
        _profilePlaces = StateObject(wrappedValue: ProfilePlacesViewModel(reader: environment.placesReader))
        _updateModel = StateObject(wrappedValue: environment.update.makeViewModel())
        let engine = environment.tracking.engine
        _travel = StateObject(wrappedValue: TravelViewModel(control: EngineTravelControl(engine: engine)))
    }

    var body: some View {
        ZStack {
            NavigationStack(path: $path) {
                MainShellView(
                    miffs: directory.state,
                    profile: profile,
                    memberPreviews: memberPreviews,
                    travel: travel,
                    places: ProfilePlacesShell(
                        model: profilePlaces,
                        notices: placeNotices,
                        context: ProfilePlacesContext(
                            myUid: environment.session.currentUid,
                            actions: .make(model: profilePlaces, notices: placeNotices, open: { open($0) })
                        )
                    ),
                    update: UpdateShell(
                        model: updateModel,
                        status: environment.update.myStatus,
                        onReviewAgreement: {
                            // « Revoir et accepter » : le mécanisme du Feed (`AwaitingAgreement`), que la porte des autorisations écoute.
                            environment.awaitingAgreement.requestReview()
                        }
                    ),
                    onOpenMiff: { open(.miff($0)) },
                    onCreateMiff: { createMiffFromProfile() },
                    onOpenSettings: { open(.settings($0)) },
                    feed: { context in feedTab(context) }
                )
                    .toolbar(.hidden, for: .navigationBar)
                    .swipeBackEnabled()
                    .navigationDestination(for: MiffRoute.self) { route in
                        destination(route)
                            .toolbar(.hidden, for: .navigationBar)
                            .swipeBackEnabled()
                    }
            }
            .accessibilityHidden(directory.notices.first != nil)
            if let banner {
                DeletionBannerView(banner: banner)
            }
            if let notice = directory.notices.first {
                AccessLostView(miffName: notice.name, onAcknowledge: { acknowledge(notice) })
            }
        }
        // La dernière Miff a disparu : « Mes informations » empilé part (un compte sans Miff n'a que Mon compte et Confidentialité,
        // décision du PM du 2026-11-07) ; les autres écrans des Réglages restent où ils sont.
        .onChange(of: MainShellRules.hasNoMiff(directory.state)) { noMiff in
            if noMiff {
                path.removeAll { $0.isMyInfoScreen }
            }
        }
        // Mon visage sur les cartes du Profil suit mon profil, SANS lecture : le profil est déjà lu pour l'en-tête (et relu après une modification).
        .onChange(of: profile.state) { state in
            if case .ready(let mine) = state, let uid = environment.session.currentUid {
                memberPreviews.replaceFace(uid: uid, initials: mine.initials, jpeg: mine.avatarJpeg)
            }
        }
        // Une seule lecture des Miffs par session à l'ouverture (pas une à chaque retour sur l'écran) : le contrôle auprès du
        // serveur que l'accès à chaque Miff existe encore, avant d'en montrer quoi que ce soit (ticket §5.7, critère D2).
        .task {
            await directory.refreshIfNeeded()
        }
        // Une suppression non terminée est reprise au lancement (critère S4), et une suppression qui se termine pendant que la
        // personne est ailleurs que sur l'écran de cette Miff est dite avec calme.
        .task {
            await watchDeletions()
        }
        .task(id: banner) {
            guard banner != nil else {
                return
            }
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            banner = nil
        }
    }

    /// Le flux d'annonces est ouvert AVANT la reprise : rien de ce qu'elle annonce n'est perdu.
    private func watchDeletions() async {
        let deletions = environment.miffDeletions
        let notices = deletions.notices()
        deletions.resumePending()
        for await notice in notices {
            switch notice {
            case .resumed:
                banner = .resumed
            case .completed(let miffId):
                // Sur l'écran de cette Miff, c'est l'écran lui-même qui le dit : rien n'est dit deux fois.
                if path.last != .miff(miffId) {
                    banner = .done
                }
            }
        }
    }

    /// L'onglet Feed (FEED-5 à FEED-7) : il remplace l'accueil provisoire dans la coquille.
    private func feedTab(_ context: FeedShellContext) -> some View {
        FeedTab(
            environment: environment,
            context: context,
            memberPreviews: memberPreviews,
            profilePlaces: profilePlaces,
            actions: FeedTabActions(
                onCreate: { open(.create) },
                onJoin: { open(.join) },
                // Sélecteur du Feed, compte gratuit qui a déjà une Miff (décision du PM, 2026-11-08) : l'offre, sans achat.
                onOpenOffer: { open(.placePaywall(.extraMiff)) },
                onOpenMiff: { open(.miff($0)) },
                onSignOut: { signOut() },
                onOpenSettings: { open(.settings(.settings)) }
            )
        )
    }

    /// « Créer une Miff », dernière carte du carrousel du Profil : le même choix que le sélecteur du Feed (décision du PM, 2026-11-08), l'offre
    /// pour un compte gratuit qui a déjà une Miff, le parcours normal sinon (Miff+, formule inconnue : c'est le serveur qui tranche).
    private func createMiffFromProfile() {
        switch ProfileCarouselRules.createOutcome(for: directory.state) {
        case .openPaywall:
            open(.placePaywall(.extraMiff))
        case .openFlow:
            open(.create)
        }
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
                places: placeContext(for: miffId),
                onBack: { pop() }
            )
        case .settings(let screen):
            SettingsDestinationView(
                route: screen,
                environment: environment,
                profile: profile,
                restricted: MainShellRules.hasNoMiff(directory.state),
                navigate: { open(.settings($0)) },
                onBack: { pop() }
            )
        default:
            placeDestination(route)
        }
    }

    // MARK: - Les lieux (LOC-3)

    @ViewBuilder
    private func placeDestination(_ route: MiffRoute) -> some View {
        switch route {
        case .placeForm(let placeId, let miffId):
            PlaceFormView(
                placeId: placeId,
                miffId: miffId,
                dependencies: environment.placeFormDependencies(),
                onBack: { pop() },
                exits: PlaceFormExits(
                    onEnd: { finishPlaceForm($0) },
                    onPaywall: { reason, replacesForm in openPlacePaywall(reason, replacesForm: replacesForm) }
                )
            )
        case .placeDetail(let miffId, let placeId):
            PlaceDetailView(
                miffId: miffId,
                placeId: placeId,
                dependencies: environment.placeDetailDependencies(),
                navigation: PlaceDetailNavigation(
                    onBack: { pop() },
                    onEdit: { open(.placeForm(placeId: placeId, miffId: miffId)) },
                    onGone: { leavePlaceDetail() }
                )
            )
        case .placePaywall(let reason):
            PlacePaywallView(reason: reason, tracker: environment.analyticsTracker, onClose: { pop() })
        case .create, .join, .miff, .settings:
            EmptyView()
        }
    }

    /// Ce que la liste des lieux d'une Miff reçoit : Miff+ ou non (nil tant que ce n'est pas lu auprès du serveur), le message d'un
    /// enregistrement, les gestes.
    private func placeContext(for miffId: String) -> PlaceListContext {
        // Miff+ n'est affirmé (ou nié) que s'il a été LU auprès du serveur : sinon nil, et la liste n'affirme rien de la formule
        // gratuite (ni compteur, ni pastille).
        var premium: Bool?
        if case .ready(let ready) = directory.state, ready.premiumKnown {
            premium = ready.premium
        }
        let notices = placeNotices
        return PlaceListContext(
            premium: premium,
            notice: notices.notice,
            actions: PlaceListActions(
                onOpen: { place in open(.placeDetail(miffId: miffId, placeId: place.id)) },
                // Toujours le formulaire : il relit l'abonnement et les lieux auprès du SERVEUR et ne propose l'offre que si la limite
                // est vraiment atteinte (jamais une décision prise sur l'annuaire, qui peut ignorer un Miff+).
                // Le formulaire coche d'abord CETTE Miff (celle dont la liste est regardée), pas la dernière rejointe.
                onAdd: { _ in open(.placeForm(placeId: nil, miffId: miffId)) }
            ),
            onLeave: { notices.consume() }
        )
    }

    /// Le formulaire est fini. Un lieu supprimé (ou qui n'existe plus) ramène à la liste : le détail d'un lieu qui n'existe plus ne
    /// vaut pas le retour.
    private func finishPlaceForm(_ end: PlaceFormEnd) {
        guard case .placeForm? = path.last else {
            return
        }
        if end == .placeGone {
            placeNotices.gone()
        }
        path.removeLast()
        if end != .saved, case .placeDetail? = path.last {
            path.removeLast()
        }
    }

    /// L'offre s'ouvre ; elle REMPLACE le formulaire quand celui-ci n'a rien à offrir (la limite trouvée à l'ouverture).
    private func openPlacePaywall(_ reason: PaywallReason, replacesForm: Bool) {
        if replacesForm, case .placeForm? = path.last {
            path.removeLast()
        }
        open(.placePaywall(reason))
    }

    /// Le lieu a été supprimé ailleurs pendant que son détail était ouvert : « Ce lieu n'existe plus. », et retour à la liste.
    private func leavePlaceDetail() {
        guard case .placeDetail? = path.last else {
            return
        }
        placeNotices.gone()
        path.removeLast()
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
        // L'écran de cette Miff, et les détails de ses lieux ouverts par-dessus, partent ; une autre Miff derrière reste.
        path.removeAll { $0.belongs(to: notice.miffId) }
    }

    private func open(_ route: MiffRoute) {
        // L'écran d'une Miff s'ouvre : ses membres ont pu changer, la carte du Profil la relira à sa prochaine apparition (voir `invalidate`).
        if case .miff(let miffId) = route {
            memberPreviews.invalidate(miffId: miffId)
        }
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
