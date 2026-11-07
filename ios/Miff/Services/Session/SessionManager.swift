import Foundation

enum SessionState: Equatable {
    /// Quelque chose est en cours de vérification (lecture du compte, déconnexion en cours).
    case loading
    /// Connecté, mais le compte n'a pas pu être lu (hors ligne...). Réessayer ou se déconnecter ; rien d'autre n'est
    /// accessible.
    case lookupFailed
    case ready(AppDestination)
}

/// Message unique montré sur l'écran de connexion après la fin d'une session.
enum SignedOutNotice: Equatable {
    /// Le panneau de consentement a été refusé : le compte tout juste créé a été supprimé (ticket §7.8).
    case accountNotCreated
    /// La session s'est terminée dans notre dos (compte désactivé ou supprimé depuis la console, jeton révoqué).
    case accountUnavailable
    /// Le compte est maintenant utilisé sur un autre téléphone (SESS-3) : déconnexion locale forcée, message calme. Jamais
    /// présenté comme un accès perdu à une Miff.
    case sessionReplaced
}

enum RefuseResult: Equatable {
    case done
    /// La personne a fermé la fenêtre Google ou Apple qu'on lui demandait pour prouver qui elle est : rien ne s'est
    /// passé, aucun message.
    case cancelled
    case failed(AuthFailure)
}

/// Ce que `SessionManager` demande aux réglages de confidentialité (implémenté par `PrivacySettings`).
protocol CollectionConsentApplying {
    /// Démarrage à froid et chaque fois que le compte est (re)lu : ne rien collecter, mais garder le rapport du
    /// plantage précédent sur le disque ; la réponse du compte l'enverra ou l'effacera.
    func pauseCollection()
    /// Applique le choix du COMPTE aux statistiques et aux rapports de plantage à la fois. `false` coupe les deux ET
    /// efface les rapports pas encore envoyés (retrait du consentement, déconnexion, refus).
    func applyAccountConsent(_ enabled: Bool)
}

/// Tout ce dont un `SessionManager` a besoin.
struct SessionDependencies {
    let gateway: AuthGateway
    let accounts: AccountRepository
    let pending: PendingConsentStore
    let flags: BooleanStore
    let privacy: CollectionConsentApplying
    let tracker: AnalyticsTracker
    let cache: CachePurger
    let teardown: [SessionTeardown]
    /// Un compte, un seul appareil (SESS-3) : la session de cet appareil.
    let device: DeviceSession
    /// Le réseau : au retour du réseau, la session est vérifiée AVANT toute écriture.
    let network: NetworkMonitor
    /// Le brouillon local des étapes du profil. Effacé dès qu'il ne peut plus servir : personne n'est connecté, ou le
    /// profil existe (ceinture et bretelles à côté de l'arrêt à la déconnexion et de l'effacement après l'écriture).
    let drafts: ProfileDraftStore?
    /// Copie de `generation` lisible depuis n'importe quel fil (le moteur de suivi des lieux, LOC-4b). Facultative : les tests n'en ont pas besoin.
    var generationCounter: GenerationCounter?
}

/// L'unique responsable de « qui est connecté et où s'ouvre l'app » (tickets Authentification §5, §7.6, §7.8 et §8).
///
/// Règles tenues ici, chacune couverte par un test :
/// - la destination vient de l'état du COMPTE (profil ? Miff ?), jamais de « une connexion vient d'avoir lieu » ;
/// - rien n'est collecté (Analytics, rapports de plantage) tant que le choix propre du compte n'a pas été lu, ou que la
///   personne n'a pas touché « Rejoindre » / « Continuer » (qui accepte les conditions ET les statistiques, décision du
///   PM du 2026-10-28). Tant que le choix n'est pas connu la collecte est seulement EN PAUSE (le rapport du plantage
///   précédent reste sur le disque) ; elle n'est EFFACÉE que sans consentement ;
/// - le profil n'est jamais écrit ici (les étapes de l'Onboarding le font, avec `UserProfileRepository`) ;
/// - mettre fin à une session (déconnexion, refus, perte) arrête les écoutes, purge le cache Firestore, efface le
///   consentement en attente et coupe les collectes.
///
/// Les opérations d'authentification vont jusqu'au bout même si l'écran qui les a lancées disparaît : une requête
/// abandonnée pourrait sinon ouvrir une session que personne ne regarde.
@MainActor
final class SessionManager {
    static let keyReturningUser = "returning_user"
    /// Posé au tout premier lancement d'une INSTALLATION (les réglages de l'app disparaissent avec elle, pas le Trousseau).
    static let keyInstallMarker = "install_marker_v1"
    /// Le message calme « autre téléphone » n'a pas encore été montré (SESS-3) : écrit par la déconnexion forcée, lu au démarrage
    /// suivant. Une déconnexion forcée peut avoir lieu alors que l'app est fermée (réveil par une zone, LOC-4b) : le message attend
    /// l'écran de connexion. Même clé que sur Android.
    static let keyReplacedNoticePending = "session_replaced_notice_pending"

    let gateway: AuthGateway
    let accounts: AccountRepository
    let pending: PendingConsentStore
    let flags: BooleanStore
    let privacy: CollectionConsentApplying
    let tracker: AnalyticsTracker
    let cache: CachePurger
    let teardown: [SessionTeardown]
    let device: DeviceSession
    let network: NetworkMonitor
    let drafts: ProfileDraftStore?
    private let generationCounter: GenerationCounter?

    /// Appelé à chaque changement d'état (un seul observateur : l'interface).
    var onStateChange: ((SessionState) -> Void)?

    private(set) var state: SessionState = .loading {
        didSet {
            if oldValue != state {
                onStateChange?(state)
            }
        }
    }

    /// Monte à chaque fin de session (déconnexion, panneau refusé, session perdue). Un écran qui garde des données
    /// personnelles en mémoire (les étapes du profil) s'en sert comme clé : une nouvelle personne ne retrouve jamais ce
    /// que la précédente avait saisi.
    private(set) var generation = 0 {
        didSet {
            generationCounter?.set(generation)
        }
    }

    /// Ce que la dernière lecture du compte a dit (nil avant la première lecture et après une déconnexion).
    private(set) var accountSnapshot: AccountSnapshot?

    /// Vrai pendant que nous mettons nous-mêmes fin à la session : le guetteur de « session perdue » ne doit pas réagir
    /// à notre propre déconnexion.
    var ending = false

    /// Vrai dès qu'une session est connue, jusqu'à sa fin : une perte pendant une transition compte quand même.
    var sessionActive = false

    /// Vrai tant que l'écriture de la session de cet appareil n'a pas été confirmée par le serveur après une connexion : « Réessayer »
    /// la retente au lieu de relire une session qui n'existe pas encore (voir `SessionManager+Device`).
    var pendingDeviceBegin = false

    /// Les deux écoutes de la session de l'appareil (le document de session, le retour du réseau), et la sérialisation de ses
    /// vérifications.
    var deviceWatchers: [Task<Void, Never>] = []
    var deviceReviewBusy = false
    var deviceReviewDirty = false
    /// Quand l'écoute du document se termine sans qu'on l'ait demandé (erreur du serveur), elle est relancée après ces pauses, en
    /// nanosecondes, UNE fois par pause (bornée : au-delà, seul le retour du réseau vérifie encore la session). Réglable par les tests.
    var deviceWatchRestartPauses: [UInt64] = [15_000_000_000, 30_000_000_000, 60_000_000_000]
    /// Une écoute qui a tenu au moins ce temps (secondes) remet le compteur de relances à zéro.
    var deviceWatchHealthySeconds: TimeInterval = 300

    private var started = false
    private var notice: SignedOutNotice?
    private var watcher: Task<Void, Never>?

    init(_ dependencies: SessionDependencies) {
        gateway = dependencies.gateway
        accounts = dependencies.accounts
        pending = dependencies.pending
        flags = dependencies.flags
        privacy = dependencies.privacy
        tracker = dependencies.tracker
        cache = dependencies.cache
        teardown = dependencies.teardown
        device = dependencies.device
        network = dependencies.network
        drafts = dependencies.drafts
        generationCounter = dependencies.generationCounter
        // Un remplacement découvert après un refus d'écriture lance la même déconnexion forcée que l'écoute de la session.
        device.setReplacedHandler { [weak self] in
            Task { @MainActor in
                await self?.endBecauseReplaced()
            }
        }
    }

    /// Le compte connecté (nil = personne). Lu par les étapes de l'Onboarding, qui écrivent le profil.
    var currentUid: String? {
        gateway.currentUid
    }

    /// Nom suggéré par le fournisseur de connexion, pour pré-remplir l'étape profil. Jamais conservé par la session.
    func suggestedDisplayName() -> String? {
        gateway.displayName()
    }

    /// Un message attend-il d'être montré sur l'écran de connexion ?
    var hasNotice: Bool {
        notice != nil
    }

    /// Lit (et oublie) le message unique de l'écran de connexion.
    func consumeNotice() -> SignedOutNotice? {
        let current = notice
        notice = nil
        if current == .sessionReplaced {
            flags.set(false, forKey: Self.keyReplacedNoticePending)
        }
        return current
    }

    func start() {
        Task {
            await self.startAndResolve()
        }
    }

    /// Même chose que `start()`, en attendant la première destination (tests).
    func startAndResolve() async {
        guard !started else {
            return
        }
        started = true
        // Rien n'est collecté dès la première seconde : l'Info.plist dit « éteint », et le choix gardé sur l'appareil n'est
        // PAS cru au démarrage à froid. La collecte ne s'allume qu'une fois le choix du compte lu. PAUSE, pas « aucun
        // consentement » : le rapport du plantage précédent doit survivre jusqu'à la réponse du compte.
        privacy.pauseCollection()
        // Une purge qui avait échoué la dernière fois passe AVANT tout autre accès à Firestore.
        await cache.purgeIfPending()
        // Sur iOS la session Firebase est gardée dans le Trousseau, qui SURVIT à la désinstallation : une app réinstallée
        // s'ouvrirait connectée, sans carrousel ni consentement vu sur cette installation. Au premier lancement d'une
        // installation, une session trouvée est donc fermée avant tout routage (la personne se reconnecte).
        if !flags.value(forKey: Self.keyInstallMarker, default: false) {
            flags.set(true, forKey: Self.keyInstallMarker)
            if gateway.currentUid != nil {
                await signOut()
            }
        }
        // Le flux est ouvert ici, tout de suite (instant d'ouverture déterministe, inoffensif et utile). Ce qui évite la fausse
        // perte de session est la relecture de `gateway.currentUid` dans `handleUidChange`.
        let changes = gateway.uidChanges()
        watcher = Task { [weak self] in
            await self?.watchSessionLoss(changes)
        }
        await resolveCurrent()
    }

    /// « Réessayer » après une lecture du compte qui a échoué.
    func retry() {
        Task {
            await self.resolveCurrent()
        }
    }

    // MARK: - Fin de session

    func setNotice(_ newNotice: SignedOutNotice?) {
        notice = newNotice
    }

    func bumpGeneration() {
        generation += 1
    }

    func setSnapshot(_ snapshot: AccountSnapshot?) {
        accountSnapshot = snapshot
    }

    func setState(_ newState: SessionState) {
        state = newState
    }

    private func watchSessionLoss(_ changes: AsyncStream<String?>) async {
        for await uid in changes {
            await handleUidChange(uid)
        }
    }

    /// Réaction à une annonce « le compte connecté a changé ». Les annonces passent par un flux : elles sont lues un peu
    /// APRÈS l'événement, et la première (l'état au moment où l'écoute s'ouvre, souvent « personne n'est connecté ») peut
    /// n'être lue qu'au milieu d'une connexion. Une session n'est donc déclarée perdue que si, au moment où l'annonce est
    /// lue, plus personne n'est vraiment connecté : une annonce périmée ne doit jamais déconnecter quelqu'un, ni lancer une
    /// seconde fin de session en parallèle d'une autre (course réelle relevée en CI sur le panneau de consentement).
    func handleUidChange(_ uid: String?) async {
        guard uid == nil, !ending, isSignedInState(), gateway.currentUid == nil else {
            return
        }
        await onSessionLost()
    }

    private func isSignedInState() -> Bool {
        switch state {
        case .lookupFailed:
            return true
        case .loading:
            return sessionActive
        case .ready(let destination):
            return destination.requiresSession
        }
    }

    private func onSessionLost() async {
        await endLocally(notice: .accountUnavailable)
    }

    /// Fin de session SANS rien demander au serveur (déjà déconnecté, ou session remplacée par un autre appareil) : plus aucun
    /// suivi ni écriture, cache purgé, écran de connexion avec `notice`. Les écritures en attente seraient refusées, donc pas de
    /// vidage. Ne touche JAMAIS à la session de l'appareil sur le serveur (pas de boucle entre deux téléphones).
    func endLocally(notice newNotice: SignedOutNotice) async {
        ending = true
        stopDeviceWatch()
        device.forget()
        if newNotice == .sessionReplaced {
            flags.set(true, forKey: Self.keyReplacedNoticePending)
        }
        cache.markPurgePending()
        state = .loading
        await stopEverything()
        _ = await cache.purge()
        finishSignedOut(destination: .signIn, notice: newNotice, markReturning: true)
    }

    /// Un autre appareil a pris la place de celui-ci (SESS-3). Une seule fin de session à la fois : sans effet si une autre est déjà
    /// en cours (déconnexion volontaire, session perdue) ou si personne n'est connecté.
    ///
    /// La fin de session tourne dans sa propre tâche : `stopDeviceWatch` annule les tâches de surveillance (dont celle qui appelle),
    /// et l'arrêt de tout (suivi des lieux, LOC-4b) ne doit jamais être coupé net par cette annulation. L'appelant attend la fin.
    func endBecauseReplaced() async {
        guard !ending, sessionActive else {
            return
        }
        ending = true
        await Task { await self.endLocally(notice: .sessionReplaced) }.value
    }

    func stopEverything() async {
        for stop in teardown {
            await stop()
        }
    }

    func finishSignedOut(destination: AppDestination, notice newNotice: SignedOutNotice?, markReturning: Bool) {
        sessionActive = false
        pendingDeviceBegin = false
        stopDeviceWatch()
        device.forget()
        accountSnapshot = nil
        generation += 1
        gateway.signOut()
        if gateway.currentUid != nil {
            // La déconnexion n'a pas abouti (Trousseau illisible) : une seconde tentative, puis les écrans de connexion
            // refusent de continuer tant qu'une session reste ouverte (voir `clearLeftoverSession`).
            gateway.signOut()
        }
        pending.clear()
        privacy.applyAccountConsent(false)
        if markReturning {
            flags.set(true, forKey: Self.keyReturningUser)
        }
        notice = newNotice
        state = .ready(destination)
        ending = false
    }
}
