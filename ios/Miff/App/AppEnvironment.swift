import Foundation

/// Les services des Miffs (MIFF-3), construits d'un seul tenant. Rien ici n'ouvre Firestore : tout passe par des fonctions
/// appelées à la première requête, depuis des écrans affichés APRÈS le routage de la session (démarrage à froid : aucun accès
/// Firestore avant la session).
@MainActor
struct MiffServices {
    /// Tout ce que font les écrans « Créer », « Rejoindre » et « Miff » avec Firestore.
    let repository: MiffRepository
    /// Les Miffs en mémoire pour la session, vidées à la déconnexion (elles font partie des arrêts de la session).
    let directory: MiffDirectory
    /// Le limiteur d'essais du code (aide de l'interface, critère J5) : gardé pour la durée du processus, pour que quitter
    /// l'écran puis y revenir ne remette pas le compte à zéro.
    let limiter: JoinAttemptLimiter
    /// La suppression d'une Miff, sans écran (MIFF-4) : utilisée par l'écran de la Miff, reprise au lancement, et plus tard par
    /// AUTH-5 (suppression d'un compte) et le ticket Premium (fin d'un abonnement).
    let deletions: MiffDeletionCoordinator
    /// Le renouvellement du consentement des membres qui partagent sous un texte plus ancien (LOC-4a, règle R-LIEU-3).
    let renewals: ConsentRenewals

    static func make(gateway: AuthGateway, cache: CachePurger, network: NetworkMonitor) -> MiffServices {
        let store = FirestoreMiffStore()
        let repository = FirestoreMiffRepository(
            store: store,
            observer: FirestoreMiffObserver(),
            location: SystemLocationAccessProbe(),
            cache: cache,
            rules: MiffRepositoryRules.live()
        )
        let directory = MiffDirectory(
            repository: repository,
            currentUid: { gateway.currentUid },
            activeStore: UserDefaultsActiveMiffStore(),
            cache: cache
        )
        // Avant la première écriture, la suppression prévient l'annuaire (jamais de « tu n'as plus accès » pour la Miff qu'on
        // supprime) ; une fois la Miff disparue, purge du cache et liste relue.
        let hooks = DeletionHooks(
            onStarted: { miffId in directory.expectDeparture(miffId: miffId) },
            onDeleted: { miffId in await directory.afterOwnDeparture(miffId: miffId) }
        )
        let deletions = MiffDeletionCoordinator(
            deletion: MiffDeletion(store: store, repository: repository),
            pending: PendingDeletionFileStore(),
            network: network,
            currentUid: { gateway.currentUid },
            hooks: hooks
        )
        deletions.background = UIKitBackgroundExecution()
        return MiffServices(
            repository: repository,
            directory: directory,
            limiter: JoinAttemptLimiter(nowMillis: AppClock.live.nowMillis),
            deletions: deletions,
            renewals: FirestoreConsentRenewals(store: store)
        )
    }
}

/// Les services des lieux de confiance (LOC-3), construits d'un seul tenant. Rien ici n'ouvre Firestore ni MapKit (MapKit n'est créé qu'au premier usage d'un champ d'adresse) : tout passe par
/// des fonctions appelées par les écrans des lieux, affichés APRÈS le routage de la session. AUCUNE permission de localisation :
/// la recherche d'adresse et la carte de MapKit n'en ont pas besoin.
@MainActor
struct PlaceServices {
    /// Lire ce que la personne détient, écrire les lieux (un document par Miff), supprimer.
    let repository: PlaceRepository
    /// Les suggestions d'adresse et les coordonnées de l'adresse choisie : MapKit, avec un cache gardé pour la session seulement.
    let search: AddressSearch
    /// Le message calme d'un lieu enregistré, en mémoire seulement.
    let notices: PlaceNotices

    static func make() -> PlaceServices {
        let repository = FirestorePlaceRepository(
            store: FirestorePlaceStore(base: FirestoreMiffStore()),
            newId: { FirestorePlaceRepository.newFirestoreId() }
        )
        return PlaceServices(
            repository: repository,
            search: CachedAddressSearch(AppleAddressSearch()),
            notices: PlaceNotices()
        )
    }

    /// Fin de session : le message, ce qui a été tapé dans la recherche d'adresse et ce qu'elle a reçu ne sont pas gardés.
    func stop() async {
        notices.stop()
        search.reset()
    }
}

/// Conteneur de dépendances écrit à la main (injection par initialiseur, pas de bibliothèque d'injection).
/// Créé une fois par `MiffApp`.
@MainActor
final class AppEnvironment {
    let privacySettings: PrivacySettings
    let analyticsTracker: AnalyticsTracker
    let session: SessionManager
    let sessionState: SessionObservable
    let network: NetworkMonitor
    /// « Mot de passe oublié » (AUTH-4) : l'e-mail part de Firebase ; le nouveau mot de passe se choisit sur la page web
    /// d'AUTH-W.
    let passwordResetGateway: PasswordResetGateway
    /// Pause entre deux envois de l'e-mail de réinitialisation, gardée ici pour que quitter l'écran puis revenir ne donne
    /// pas un renvoi gratuit.
    let passwordResetCooldown: ResendCooldown
    let phoneTools: PhoneNumberTools
    /// Fenêtres système « Continuer avec Google » et « Continuer avec Apple ».
    let socialSource: SocialCredentialSource
    let onboardingFlags: OnboardingFlags
    let miffRepository: MiffRepository
    let miffDirectory: MiffDirectory
    /// Les lieux de confiance (LOC-3) : dépôt, recherche d'adresse (MapKit), message d'enregistrement.
    let placeRepository: PlaceRepository
    let addressSearch: AddressSearch
    let placeNotices: PlaceNotices
    /// La suppression d'une Miff, sans écran (MIFF-4) : reprise au lancement, banderole de fin, écran de la Miff.
    let miffDeletions: MiffDeletionCoordinator
    let joinLimiter: JoinAttemptLimiter
    /// LOC-4a : ce que le téléphone dit des autorisations de Miff (l'ÉTAT seulement, jamais une position), l'écran des
    /// notifications déjà montré, et le renouvellement du consentement.
    let consentRenewals: ConsentRenewals
    let notificationAsks: NotificationAskStore
    /// Créé à la première demande : aucun gestionnaire de localisation n'existe tant qu'aucun écran n'en a besoin.
    lazy var devicePermissions: DevicePermissions = SystemDevicePermissions()

    private let userProfiles: UserProfileRepository
    /// Ce que la personne a saisi dans les étapes du profil : sur l'appareil seulement, effacé une fois le serveur ayant
    /// confirmé (ticket §6.2) et à chaque fin de session.
    private let profileDrafts: ProfileDraftStore
    private let avatarCodec: AvatarCodec
    /// L'acceptation des conditions gardée sur l'appareil entre l'inscription et la création du profil.
    private let pendingConsent: PendingConsentStore

    /// Environnement réel : choix de confidentialité et drapeaux dans UserDefaults, SDK Firebase derrière.
    static func live() -> AppEnvironment {
        AppEnvironment()
    }

    private init() {
        let flagStore = UserDefaultsBooleanStore()
        let privacy = PrivacySettings(
            store: flagStore,
            analytics: FirebaseAnalyticsSwitch(),
            crashReports: FirebaseCrashlyticsSwitch()
        )
        let tracker = AnalyticsTracker(sink: FirebaseAnalyticsSink(), isEnabled: { privacy.analyticsEnabled })
        let drafts = ProfileDraftFileStore()
        let pending = UserDefaultsPendingConsentStore()
        let gateway = FirebaseAuthGateway()
        // Un seul purgeur de cache pour toute l'app : la session, l'annuaire des Miffs et le dépôt purgent le MÊME cache.
        let cache = FirestoreCachePurger(flags: flagStore)
        let networkMonitor = NWNetworkMonitor()
        let miff = MiffServices.make(gateway: gateway, cache: cache, network: networkMonitor)
        let directory = miff.directory
        let deletions = miff.deletions
        let places = PlaceServices.make()
        let manager = SessionManager(
            SessionDependencies(
                gateway: gateway,
                accounts: FirestoreAccountRepository(),
                pending: pending,
                flags: flagStore,
                privacy: privacy,
                tracker: tracker,
                cache: cache,
                // Le brouillon (noms, photo, numéro) et les Miffs en mémoire partent avec la session. Le suivi des lieux et
                // les notifications ajouteront leur propre arrêt ici, pour qu'une déconnexion les arrête (ticket §7.6).
                teardown: [
                    { drafts.clear() },
                    { await directory.stop() },
                    { await deletions.stop() },
                    // Le message d'un lieu et ce qui a été tapé dans la recherche d'adresse partent avec la session.
                    { await places.stop() }
                ],
                drafts: drafts
            )
        )
        privacySettings = privacy
        analyticsTracker = tracker
        session = manager
        sessionState = SessionObservable(manager: manager)
        network = networkMonitor
        passwordResetGateway = FirebasePasswordResetGateway()
        passwordResetCooldown = ResendCooldown(nowMillis: AppClock.live.nowMillis)
        phoneTools = PhoneNumberKitTools()
        socialSource = SystemSocialCredentialSource()
        onboardingFlags = OnboardingFlags(store: flagStore)
        miffRepository = miff.repository
        miffDirectory = directory
        miffDeletions = deletions
        placeRepository = places.repository
        addressSearch = places.search
        placeNotices = places.notices
        joinLimiter = miff.limiter
        consentRenewals = miff.renewals
        notificationAsks = UserDefaultsNotificationAskStore()
        userProfiles = FirestoreUserProfileRepository()
        profileDrafts = drafts
        avatarCodec = ImageAvatarCodec()
        pendingConsent = pending
    }

    /// Les dépendances des étapes du profil.
    func profileDependencies() -> ProfileDependencies {
        ProfileDependencies(
            session: session,
            pending: pendingConsent,
            drafts: profileDrafts,
            profiles: userProfiles,
            avatars: avatarCodec,
            network: network,
            phoneTools: phoneTools,
            deviceRegion: { Locale.current.region?.identifier }
        )
    }

    /// Les dépendances des écrans « Créer » et « Rejoindre » (MIFF-3).
    func miffFlowContext() -> MiffFlowContext {
        let session = self.session
        let directory = miffDirectory
        return MiffFlowContext(
            repository: miffRepository,
            network: network,
            currentUid: { session.currentUid },
            tracker: analyticsTracker,
            onEntered: { via in session.onMiffEntered(via: via) },
            onDirectoryChanged: { await directory.refresh() },
            onProfileMissing: { session.retry() }
        )
    }

    /// Les écrans de consentement avant le Feed (LOC-4a) : autorisations, renouvellement du consentement, et « Quitter la Miff »
    /// du flux existant pour la sortie de l'écran de renouvellement. Le blocage de la localisation est l'interrupteur de
    /// COMPILATION (`LocationBlockingSwitch`, bloquant par défaut).
    func makePermissionGate() -> PermissionGateViewModel {
        let session = self.session
        let directory = miffDirectory
        let repository = miffRepository
        return PermissionGateViewModel(
            dependencies: PermissionGateDependencies(
                permissions: devicePermissions,
                asked: notificationAsks,
                renewals: consentRenewals,
                currentUid: { session.currentUid },
                leaver: { uid, miffId in await repository.leave(uid: uid, miffId: miffId) },
                onLeft: { miffId in await directory.afterOwnDeparture(miffId: miffId) }
            ),
            settings: PermissionGateSettings(locationBlocking: LocationBlockingSwitch.current)
        )
    }

    /// Les dépendances du formulaire d'un lieu (LOC-3).
    func placeFormDependencies() -> PlaceFormDependencies {
        let session = self.session
        return PlaceFormDependencies(
            repository: placeRepository,
            search: addressSearch,
            network: network,
            currentUid: { session.currentUid },
            notices: placeNotices
        )
    }

    /// Les dépendances du détail d'un lieu (LOC-3) : la lecture en direct de la Miff, et la perte d'accès confiée à l'annuaire.
    func placeDetailDependencies() -> PlaceDetailDependencies {
        let session = self.session
        let directory = miffDirectory
        return PlaceDetailDependencies(
            repository: miffRepository,
            network: network,
            currentUid: { session.currentUid },
            onAccessLost: { miffId, name in await directory.onAccessLost(miffId: miffId, name: name) }
        )
    }

    /// Les dépendances de l'écran d'une Miff (MIFF-3 et MIFF-4).
    func miffScreenDependencies() -> MiffScreenDependencies {
        let session = self.session
        let directory = miffDirectory
        return MiffScreenDependencies(
            repository: miffRepository,
            network: network,
            currentUid: { session.currentUid },
            onAccessLost: { miffId, name in await directory.onAccessLost(miffId: miffId, name: name) },
            onNameChanged: { await directory.refresh() },
            deletions: miffDeletions,
            onLeft: { miffId in await directory.afterOwnDeparture(miffId: miffId) }
        )
    }
}
