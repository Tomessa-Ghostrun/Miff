import Foundation

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
        let manager = SessionManager(
            SessionDependencies(
                gateway: FirebaseAuthGateway(),
                accounts: FirestoreAccountRepository(),
                pending: pending,
                flags: flagStore,
                privacy: privacy,
                tracker: tracker,
                cache: FirestoreCachePurger(flags: flagStore),
                // Le brouillon (noms, photo, numéro) part avec la session. Le fil (écoutes), le suivi des lieux et les
                // notifications ajouteront leur propre arrêt ici, pour qu'une déconnexion les arrête (ticket §7.6).
                teardown: [{ drafts.clear() }],
                drafts: drafts
            )
        )
        privacySettings = privacy
        analyticsTracker = tracker
        session = manager
        sessionState = SessionObservable(manager: manager)
        network = NWNetworkMonitor()
        passwordResetGateway = FirebasePasswordResetGateway()
        passwordResetCooldown = ResendCooldown(nowMillis: AppClock.live.nowMillis)
        phoneTools = PhoneNumberKitTools()
        socialSource = SystemSocialCredentialSource()
        onboardingFlags = OnboardingFlags(store: flagStore)
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
}
