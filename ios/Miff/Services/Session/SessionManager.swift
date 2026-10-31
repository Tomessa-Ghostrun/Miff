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
    /// Le brouillon local des étapes du profil. Effacé dès qu'il ne peut plus servir : personne n'est connecté, ou le
    /// profil existe (ceinture et bretelles à côté de l'arrêt à la déconnexion et de l'effacement après l'écriture).
    let drafts: ProfileDraftStore?
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

    let gateway: AuthGateway
    let accounts: AccountRepository
    let pending: PendingConsentStore
    let flags: BooleanStore
    let privacy: CollectionConsentApplying
    let tracker: AnalyticsTracker
    let cache: CachePurger
    let teardown: [SessionTeardown]
    let drafts: ProfileDraftStore?

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
    private(set) var generation = 0

    /// Ce que la dernière lecture du compte a dit (nil avant la première lecture et après une déconnexion).
    private(set) var accountSnapshot: AccountSnapshot?

    /// Vrai pendant que nous mettons nous-mêmes fin à la session : le guetteur de « session perdue » ne doit pas réagir
    /// à notre propre déconnexion.
    var ending = false

    /// Vrai dès qu'une session est connue, jusqu'à sa fin : une perte pendant une transition compte quand même.
    var sessionActive = false

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
        drafts = dependencies.drafts
    }

    /// Le compte connecté (nil = personne). Lu par les étapes de l'Onboarding, qui écrivent le profil.
    var currentUid: String? {
        gateway.currentUid
    }

    /// Nom suggéré par le fournisseur de connexion, pour pré-remplir l'étape profil. Jamais conservé par la session.
    func suggestedDisplayName() -> String? {
        gateway.displayName()
    }

    /// Lit (et oublie) le message unique de l'écran de connexion.
    func consumeNotice() -> SignedOutNotice? {
        let current = notice
        notice = nil
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
        watcher = Task { [weak self] in
            await self?.watchSessionLoss()
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

    private func watchSessionLoss() async {
        for await uid in gateway.uidChanges() where uid == nil && !ending && isSignedInState() {
            await onSessionLost()
        }
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
        ending = true
        cache.markPurgePending()
        state = .loading
        await stopEverything()
        // Déjà déconnecté : les écritures en attente seraient refusées, donc pas de vidage.
        _ = await cache.purge()
        finishSignedOut(destination: .signIn, notice: .accountUnavailable, markReturning: true)
    }

    func stopEverything() async {
        for stop in teardown {
            await stop()
        }
    }

    func finishSignedOut(destination: AppDestination, notice newNotice: SignedOutNotice?, markReturning: Bool) {
        sessionActive = false
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
