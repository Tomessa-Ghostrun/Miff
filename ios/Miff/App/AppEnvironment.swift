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

    static func make(
        gateway: AuthGateway,
        cache: CachePurger,
        network: NetworkMonitor,
        device: DeviceSession
    ) -> MiffServices {
        let store = FirestoreMiffStore()
        // SESS-3 : un statut n'est écrit qu'avec l'identifiant de la session CONFIRMÉE de cet appareil ; après un refus, la session
        // est relue auprès du serveur.
        let rules = MiffRepositoryRules.live(
            deviceId: { device.currentDeviceId },
            recheckSession: {
                if let uid = gateway.currentUid {
                    _ = await device.checkAfterRefusal(uid: uid)
                }
            }
        )
        let repository = FirestoreMiffRepository(
            store: store,
            observer: FirestoreMiffObserver(),
            location: SystemLocationAccessProbe(),
            cache: cache,
            rules: rules
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
            deletions: deletions
        )
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
    /// La suppression d'une Miff, sans écran (MIFF-4) : reprise au lancement, banderole de fin, écran de la Miff.
    let miffDeletions: MiffDeletionCoordinator
    let joinLimiter: JoinAttemptLimiter

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
        // Un compte, un seul appareil (SESS-3) : l'identifiant de session vit dans un fichier privé, exclu des sauvegardes et effacé
        // avec l'app (jamais dans le Trousseau, qui survit à la désinstallation).
        let device = DeviceSession(remote: FirestoreDeviceSessionRemote(), store: DeviceIdFileStore())
        let miff = MiffServices.make(gateway: gateway, cache: cache, network: networkMonitor, device: device)
        let directory = miff.directory
        let deletions = miff.deletions
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
                teardown: [{ drafts.clear() }, { await directory.stop() }, { await deletions.stop() }],
                device: device,
                network: networkMonitor,
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
        joinLimiter = miff.limiter
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
