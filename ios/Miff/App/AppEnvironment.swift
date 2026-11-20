import Foundation

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
    /// Les demandes d'ajout d'un lieu (LIEU-REQ) : un membre propose un lieu au créateur de la Miff.
    let requests: PlaceRequestRepository

    static func make(network: NetworkMonitor, onChanged: @escaping () -> Void) -> PlaceServices {
        let repository = FirestorePlaceRepository(
            store: FirestorePlaceStore(base: FirestoreMiffStore()),
            newId: { FirestorePlaceRepository.newFirestoreId() }
        )
        let requests = FirestorePlaceRequestRepository(
            store: FirestorePlaceRequestStore(base: FirestoreMiffStore()),
            places: FirestorePlaceStore(base: FirestoreMiffStore()),
            placeRepository: repository,
            newPlaceId: { FirestorePlaceRepository.newFirestoreId() },
            online: { network.isOnline() },
            // LOC-4b : un lieu accepté fait relire les lieux au suivi, comme un lieu créé par le créateur.
            onPlaceAdded: onChanged
        )
        return PlaceServices(
            // LOC-4b : un lieu créé, modifié ou supprimé fait relire les lieux au suivi (le téléphone du créateur n'attend pas l'heure).
            repository: ChangeNotifyingPlaceRepository(repository, onChanged: onChanged),
            search: CachedAddressSearch(AppleAddressSearch()),
            notices: PlaceNotices(),
            requests: requests
        )
    }

    /// « Quitter une Miff » reprend d'abord les demandes d'ajout d'un lieu de la personne dans cette Miff (LIEU-REQ, critère L1).
    var beforeLeave: (_ uid: String, _ miffId: String) async -> Void {
        { [requests] uid, miffId in await requests.withdrawAllMine(uid: uid, miffId: miffId) }
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
    let passwordResetGateway: PasswordResetGateway = FirebasePasswordResetGateway()
    /// Pause entre deux envois de l'e-mail de réinitialisation, gardée ici pour que quitter l'écran puis revenir ne donne
    /// pas un renvoi gratuit.
    let passwordResetCooldown = ResendCooldown(nowMillis: AppClock.live.nowMillis)
    let phoneTools: PhoneNumberTools = PhoneNumberKitTools()
    /// Fenêtres système « Continuer avec Google » et « Continuer avec Apple ».
    let socialSource: SocialCredentialSource = SystemSocialCredentialSource()
    let onboardingFlags: OnboardingFlags
    let miffRepository: MiffRepository
    let miffDirectory: MiffDirectory
    /// Les lieux de confiance (LOC-3) : dépôt, recherche d'adresse (MapKit), message d'enregistrement.
    let placeRepository: PlaceRepository
    let addressSearch: AddressSearch
    let placeNotices: PlaceNotices
    /// Les demandes d'ajout d'un lieu (LIEU-REQ).
    let placeRequestRepository: PlaceRequestRepository
    /// La suppression d'une Miff, sans écran (MIFF-4) : reprise au lancement, banderole de fin, écran de la Miff.
    let miffDeletions: MiffDeletionCoordinator
    let joinLimiter: JoinAttemptLimiter
    /// La fenêtre d'avis (docs/tickets/pop-ins-avis.md) : vide dans un build sans le drapeau `REVIEW_PROMPT` (Release).
    let reviewPrompt: ReviewPromptServices
    /// LOC-4a : ce que le téléphone dit des autorisations de Miff (l'ÉTAT seulement, jamais une position), l'écran des
    /// notifications déjà montré, et le renouvellement du consentement.
    let consentRenewals: ConsentRenewals
    /// FEED-5 : les Miffs qui attendent l'accord de la personne (« Pas maintenant » au renouvellement), dites par le Feed.
    let awaitingAgreement: AwaitingAgreement
    /// FEED-5 : les lectures du Feed (statuts en direct, position au toucher, actualisation). Elles ne lisent qu'à l'usage.
    let feedServices = FeedServices.live()
    let notificationAsks: NotificationAskStore = UserDefaultsNotificationAskStore()
    /// CONS-4-I : combien de fois la carte calme « Une précision sur ce que tu partages » a été montrée à chaque compte sur ce téléphone
    /// (un compteur dans un fichier exclu des sauvegardes, jamais envoyé, effacé avec le compte).
    let clarifyAsks: ClarifyAskStore = ClarifyAskFileStore()
    /// NOTIF-P0 : le centre de notifications local, les choix de Réglages > Notifications, les demandes d'onglet.
    let notifications: NotificationServices
    /// La carte « deuxième chance » du Feed : créée à la première demande, comme les autorisations qu'elle lit.
    lazy var notificationSecondChance: NotificationSecondChanceViewModel = NotificationSecondChanceViewModel(
        permissions: devicePermissions,
        store: notifications.localData.secondChance,
        currentUid: { [weak self] in self?.session.currentUid }
    )
    /// Le suivi des lieux (LOC-4b) : créé TOUT DE SUITE au démarrage, pour que le système puisse relancer l'app terminée pour un
    /// événement de zone. Il ne fait rien tant que l'accord, « Toujours » et la position exacte ne sont pas là.
    let tracking: TrackingRuntime
    /// Le journal local de la détection (builds Debug et Internal seulement ; en publication il ne garde rien).
    let detectionJournal: DetectionJournal
    /// TIME-3 : le journal local des changements de présence (répartition du temps du Profil, Réglages > Confidentialité).
    let presenceHistory: PresenceHistory
    /// Efface le journal de présence d'une Miff dont la personne n'est plus membre (liste des Miffs confirmée par le serveur).
    private let presenceHistoryPruner: PresenceHistoryPruner
    /// L'Update (UPDATE-3) : la photo du moment, le bouton central coloré, la bulle d'incitation.
    let update: UpdateServices
    /// Les notifications push entre proches (SRV-2I, SRV-4I) : le registre des appareils, l'interrupteur, la réception.
    let push: PushServices
    /// ALERTE « Je suis en danger » : ce que la coquille reçoit de l'alerte (vide dans un build sans la fonction, voir `DangerAlertShell`).
    let dangerShell: DangerAlertShell
    /// La suppression du compte (AUTH-5c) : procédure, contrôleur et retrait de l'autorisation Apple. Construite à la première demande (les
    /// écrans de la suppression), à partir de ce que la session détient déjà ; seul le marqueur local est donné à la session dès le départ.
    lazy var accountDeletion: AccountDeletionServices = AccountDeletionServices.make(self)
    /// Créé à la première demande. Lit seulement l'ÉTAT des autorisations (le gestionnaire qui lit la position est celui du suivi,
    /// `LocationTracking`, créé au démarrage).
    lazy var devicePermissions: DevicePermissions = SystemDevicePermissions()
    /// ONB-7 (coquille de l'app) : le profil de la personne connectée (onglet Profil, Mes informations) et son adresse e-mail (Mon
    /// compte), lus à la demande et gardés seulement par l'écran qui les montre.
    lazy var profileReader: ProfileReading = FirestoreProfileReader()
    lazy var accountInfo: AccountInfoSource = FirebaseAccountInfo()
    /// Les photos des membres sur les cartes de Miff du Profil : UNE lecture ponctuelle par Miff (voir `MiffMemberPreviews`).
    lazy var memberPreviewReader: MemberPreviewReader = FirestoreMemberPreviewReader()
    /// Les lieux de la Miff du carrousel du Profil, visibles par tous ses membres : UNE lecture ponctuelle par Miff (voir `ProfilePlacesViewModel`).
    lazy var placesReader: PlacesReader = FirestorePlacesReader()
    /// Mes informations (ticket `docs/tickets/modifier-infos-perso.md`) : l'enregistrement sur le profil et sur la fiche de chaque Miff, par le magasin des Miffs
    /// (mêmes transactions, même garde d'écriture). Sans état : le SDK Firestore est résolu à chaque usage.
    lazy var myProfileRepository: MyProfileRepository = StoreMyProfileRepository(store: FirestoreMiffStore())

    private let userProfiles: UserProfileRepository = FirestoreUserProfileRepository()
    /// Ce que la personne a saisi dans les étapes du profil : sur l'appareil seulement, effacé une fois le serveur ayant
    /// confirmé (ticket §6.2) et à chaque fin de session.
    private let profileDrafts: ProfileDraftStore
    private let avatarCodec: AvatarCodec = ImageAvatarCodec()
    /// L'acceptation des conditions gardée sur l'appareil entre l'inscription et la création du profil.
    private let pendingConsent: PendingConsentStore

    /// Environnement réel : choix de confidentialité et drapeaux dans UserDefaults, SDK Firebase derrière.
    static func live() -> AppEnvironment {
        AppEnvironment()
    }

    // Le câblage de l'app tient en un seul endroit (chaque service reçoit ce qu'il lui faut des autres) : l'initialiseur dépasse 60 lignes avec les
    // notifications push (SRV-2I). Le découper ferait passer des dizaines de valeurs entre fonctions sans rien clarifier.
    // swiftlint:disable:next function_body_length
    private init() {
        let flagStore = UserDefaultsBooleanStore()
        let privacy = PrivacySettings(
            store: flagStore, analytics: FirebaseAnalyticsSwitch(), crashReports: FirebaseCrashlyticsSwitch()
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
        // NOTIF-P0 : le centre de notifications, les choix de Réglages > Notifications, les demandes d'onglet et les rappels.
        let notifs = NotificationServices.make(currentUid: { gateway.currentUid })
        let trackingServices = TrackingServices.make(
            gateway: gateway, cache: cache, network: networkMonitor, device: device,
            // Un nouveau statut de ma présence détecté par le suivi : les rappels N1 et N2 sont programmés ou annulés aussitôt.
            // MODE VOYAGE : mon voyage commence, change de date ou se termine : N7 est programmée, déplacée ou annulée aussitôt.
            notifications: TrackingNotificationHooks(onPresence: notifs.presenceChanged, onTravel: notifs.travelChanged)
        )
        let places = PlaceServices.make(network: networkMonitor, onChanged: trackingServices.sharingChanged)
        #if ALERT_DANGER
        // ALERTE « Je suis en danger » : seulement dans les builds qui contiennent la fonction (Debug et Internal).
        let dangerServices = DangerAlertServices.make(
            gateway: gateway, device: device, network: networkMonitor, tracking: trackingServices
        )
        let dangerTeardowns = dangerServices.teardowns
        let dangerErasers = dangerServices.erasers
        let dangerController = dangerServices.controller
        let dangerOnGone: (String) -> Void = { miffId in
            Task { @MainActor in dangerController.onAlertRemoved(miffId: miffId) }
        }
        #else
        let dangerTeardowns: [SessionTeardown] = []
        let dangerErasers: [LocalDataEraser] = []
        let dangerOnGone: (String) -> Void = { _ in }
        #endif
        // Un partage ou un lieu qui change : le suivi relit les Miffs et les lieux (depuis n'importe quel fil).
        let miff = MiffServices.make(
            gateway: gateway, cache: cache, network: networkMonitor, device: device,
            onSharingChanged: trackingServices.sharingChanged,
            onSharingResumed: trackingServices.sharingResumed,
            beforeLeave: places.beforeLeave,
            onAlertGone: dangerOnGone
        )
        notifs.connect(directory: miff.directory, awaiting: miff.awaiting)
        let updateServices = UpdateServices.make(
            gateway: gateway, network: networkMonitor, device: device, tracking: trackingServices, miff: miff
        )
        let pushServices = PushServices.make(
            gateway: gateway, device: device, network: networkMonitor, directory: miff.directory
        )
        // La fenêtre d'avis : son état est effacé avec la session (déconnexion, suppression du compte), comme sur Android.
        let review = ReviewPromptServices.make()
        // SRV-2I : la sortie invalide d'abord le jeton de notification (avant tout le reste), puis arrête le reste. Assemblé pas à pas : une longue chaîne de `+`
        // ralentit le compilateur.
        var allTeardowns: [SessionTeardown] = [pushServices.teardown]
        allTeardowns += Self.teardowns(drafts, miff, places, trackingServices, updateServices)
        allTeardowns += [notifs.teardown]
        allTeardowns += review.teardowns
        allTeardowns += dangerTeardowns
        var allErasers: [LocalDataEraser] = pushServices.erasers
        allErasers += dangerErasers
        let manager = SessionManager(
            SessionDependencies(
                gateway: gateway, accounts: FirestoreAccountRepository(), pending: pending, flags: flagStore,
                privacy: privacy, tracker: tracker, cache: cache,
                // Le brouillon (noms, photo, numéro) et les Miffs en mémoire partent avec la session. Le suivi des lieux et
                // les notifications ont leur propre arrêt ici, pour qu'une déconnexion les arrête (ticket §7.6).
                // SRV-2I : la sortie invalide d'abord le jeton de notification (avant tout le reste), puis arrête le reste.
                teardown: allTeardowns,
                device: device,
                network: networkMonitor,
                drafts: drafts, generationCounter: trackingServices.generation,
                deletionMarker: AccountDeletionFileMarker(),
                localData: AccountDeletionServices.localData(
                    updateLedger: updateServices.ledger, presenceHistory: trackingServices.history,
                    extra: allErasers
                )
            )
        )
        // SRV-2I : pendant la fin de la session, le registre des appareils n'écrit rien (voir `PushServices.make`).
        // Un SEUL delegue sur le centre de notifications : celui des messages du serveur ; les rappels locaux (NOTIF-P0) lui sont chaines.
        pushServices.delegate.next = notifs.responseDelegate
        pushServices.sessionProbe.isEnding = { [weak manager] in manager?.ending ?? false }
        #if ALERT_DANGER
        let directory = miff.directory
        // Un refus du serveur sur mon alerte : l'annuaire est relu (une perte confirmée retire l'alerte, voir `DangerAlertController.onAccessDoubt`).
        dangerServices.controller.onAccessDoubt = {
            Task { await directory.refresh() }
        }
        dangerShell = DangerAlertShell(
            signal: dangerServices.signal,
            model: dangerServices.model,
            onSelectMiff: { miffId in directory.select(miffId: miffId) }
        )
        #else
        dangerShell = DangerAlertShell(signal: DangerAlertSignal())
        #endif
        privacySettings = privacy
        analyticsTracker = tracker
        session = manager
        sessionState = SessionObservable(manager: manager)
        network = networkMonitor
        onboardingFlags = OnboardingFlags(store: flagStore)
        miffRepository = miff.repository
        miffDirectory = miff.directory
        miffDeletions = miff.deletions
        placeRepository = places.repository
        addressSearch = places.search
        placeNotices = places.notices
        placeRequestRepository = places.requests
        joinLimiter = miff.limiter
        reviewPrompt = review
        consentRenewals = miff.renewals
        awaitingAgreement = miff.awaiting
        tracking = trackingServices.runtime
        detectionJournal = trackingServices.journal
        presenceHistory = trackingServices.history
        presenceHistoryPruner = PresenceHistoryPruner(history: trackingServices.history, directory: miff.directory)
        update = updateServices
        push = pushServices
        profileDrafts = drafts
        pendingConsent = pending
        notifications = notifs
    }

    /// Ce que la fin de session arrête, dans l'ordre : le brouillon (noms, photo, numéro) et les Miffs en mémoire, les suppressions en
    /// cours, le message d'un lieu et ce qui a été tapé dans la recherche d'adresse, le suivi des lieux (zones retirées, position, lieux
    /// et statuts en attente effacés, borné à 5 s), puis l'Update : le garde-fou des publications et mon statut suivi partent ; la date de
    /// la photo envoyée RESTE (elle est nettoyée par le même compte à sa reconnexion), elle ne part qu'avec la suppression du compte.
    private static func teardowns(
        _ drafts: ProfileDraftFileStore,
        _ miff: MiffServices,
        _ places: PlaceServices,
        _ trackingServices: TrackingServices,
        _ update: UpdateServices
    ) -> [SessionTeardown] {
        [
            { drafts.clear() },
            // L'annuaire des Miffs, la suppression d'une Miff sans écran, et les Miffs qui attendent un accord.
            { await miff.stop() },
            { await places.stop() },
            trackingServices.teardown,
            { update.stop() }
        ]
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
            deviceRegion: { Locale.current.region?.identifier },
            // La photo du compte Google, proposée comme photo de profil préremplie (téléchargée une fois, jamais bloquante).
            googlePhoto: GooglePhotoPrefill(source: GooglePhotoDownloader(), codec: avatarCodec)
        )
    }

    /// L'écran « Mes informations » : un view model pour le profil lu, qui remet la version enregistrée dans l'en-tête du Profil (`current`).
    func makeMyInfoViewModel(current: CurrentProfileHolding, profile: ProfileSummary) -> MyInfoViewModel {
        let session = self.session
        return MyInfoViewModel(
            profile: profile,
            dependencies: MyInfoDependencies(
                repository: myProfileRepository,
                current: current,
                avatars: avatarCodec,
                network: network,
                phoneTools: phoneTools,
                deviceRegion: { Locale.current.region?.identifier },
                currentUid: { session.currentUid }
            )
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
        let push = self.push
        return PermissionGateViewModel(
            dependencies: PermissionGateDependencies(
                permissions: devicePermissions,
                asked: notificationAsks,
                renewals: consentRenewals,
                currentUid: { session.currentUid },
                leaver: { uid, miffId in await repository.leave(uid: uid, miffId: miffId) },
                onLeft: { miffId in await directory.afterOwnDeparture(miffId: miffId) },
                awaiting: awaitingAgreement,
                clarifyAsks: clarifyAsks,
                pushConsent: push.consent,
                // SRV-2I : l'accord vient d'être donné : le registre est synchronisé TOUT DE SUITE (pas au prochain retour au premier plan).
                onNotificationsAnswered: { push.sync() }
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
            notices: placeNotices,
            requests: placeRequestRepository
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
