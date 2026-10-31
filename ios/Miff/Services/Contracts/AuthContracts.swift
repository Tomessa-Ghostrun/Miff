import Foundation

// Les contrats entre la logique de l'app (SessionManager, view models) et le monde extérieur (Firebase, système).
// Du Swift pur : les vraies implémentations sont dans `Services/Firebase`, `Services/Social` et `Services/Storage` ;
// les tests utilisent des doubles en mémoire.

/// Tout ce que l'app demande à Firebase Authentication. Le mot de passe va du formulaire à Firebase et nulle part
/// ailleurs : jamais stocké, jamais journalisé, jamais mis dans un message d'erreur.
protocol AuthGateway {
    var currentUid: String? { get }

    /// Émet l'identifiant utilisateur à chaque changement (nil = déconnecté). Sert à remarquer une session perdue dans
    /// notre dos.
    func uidChanges() -> AsyncStream<String?>

    func signInWithEmail(email: String, password: String) async -> AuthOutcome
    func createAccountWithEmail(email: String, password: String) async -> AuthOutcome
    func signIn(with credential: SocialCredential) async -> AuthOutcome

    /// nil = fait.
    func reauthenticate(with credential: SocialCredential) async -> AuthFailure?

    /// Supprime le compte courant. nil = fait (aussi quand il n'y a plus de compte).
    func deleteCurrentAccount() async -> AuthFailure?

    /// Comment le compte courant se connecte ; nil si personne n'est connecté.
    func signInMethod() -> AnalyticsEvent.Method?

    /// Le nom donné par le fournisseur (Google, Apple), s'il y en a un. Il ne sert qu'à pré-remplir l'étape profil, où la
    /// personne peut le changer : jamais conservé tel quel, jamais journalisé. nil pour un compte e-mail.
    func displayName() -> String?

    func signOut()
}

/// Demande à Firebase d'envoyer l'e-mail « réinitialise ton mot de passe ».
protocol PasswordResetGateway {
    func sendResetEmail(_ email: String) async -> PasswordResetOutcome
}

/// Ouvre la fenêtre système « Continuer avec Google » ou « Continuer avec Apple » et rend le jeton pour Firebase. Lié
/// à un écran (il faut une fenêtre pour présenter la feuille système).
protocol SocialCredentialSource {
    func requestGoogle() async -> SocialTokenResult
    func requestApple() async -> SocialTokenResult
}

/// Y a-t-il un réseau ? Sert à répondre « pas de connexion » tout de suite au lieu d'attendre un long échec.
protocol NetworkMonitor {
    func isOnline() -> Bool
}

enum AccountLoad: Equatable {
    case loaded(AccountSnapshot)
    /// L'état du compte n'a pas pu être lu (hors ligne, refus, lenteur). La session est gardée ; on peut réessayer.
    case unavailable
}

/// Les deux lectures peu coûteuses qui décident où s'ouvre l'app (ticket §5). Lecture seule.
protocol AccountRepository {
    func load(uid: String) async -> AccountLoad
}

/// Purge du cache local de Firestore à la déconnexion, à la suppression du compte ou au refus du panneau de
/// consentement (ticket §7.6, contrat de backend/data-model.md §8 « Purge du cache local »). Rien d'une Miff ne doit
/// rester lisible hors ligne ensuite.
protocol CachePurger {
    /// Laisse partir les écritures en attente AVANT la déconnexion (déconnecté, les règles les refuseraient). Délai court.
    func flushPendingWrites() async

    /// `terminate()` puis `clearPersistence()`, avec un drapeau « purge à faire » autour. Vrai si c'est fait.
    /// En cas d'échec le drapeau reste : `purgeIfPending` réessaie au démarrage suivant, avant tout autre accès Firestore.
    func purge() async -> Bool

    /// Écrit tout de suite le drapeau « purge à faire », au tout début d'une déconnexion, d'un refus ou d'une perte.
    func markPurgePending()

    func purgeIfPending() async
}

/// L'acceptation des conditions (a) et le choix sur les statistiques (b), gardés sur l'appareil jusqu'à la création du
/// profil (ticket §8). Pas un secret (une version et un booléen) mais jamais partagé. Effacé à la déconnexion et une
/// fois le profil créé.
protocol PendingConsentStore {
    func save(_ consent: PendingConsent)
    /// Le consentement en attente de CE compte seulement (jamais celui enregistré pour quelqu'un d'autre).
    func read(uid: String) -> PendingConsent?
    func clear()
}

/// Ce qui doit s'arrêter quand la session se termine (écoutes Firestore, suivi des lieux, écritures de statut, jeton
/// de notification). Rien ne s'enregistre pour l'instant : ces fonctions n'existent pas encore.
typealias SessionTeardown = () async -> Void
