import Foundation

// Les contrats de la suppression d'un compte (AUTH-5c, miroir d'Android). Du Swift pur : les vraies implémentations sont dans
// `Services/Firebase` et `Services/Storage`, les tests utilisent des doubles en mémoire.
//
// SÉCURITÉ, pour ce fichier : aucun mot de passe, jeton, code d'autorisation Apple, e-mail ou identifiant de compte ne finit dans un
// journal, un événement ou un message d'erreur. Un échec est une valeur (`DeletionRun`, `MiffFailure`), jamais une erreur lisible.

/// Les méthodes de connexion que Miff connaît, d'après les fournisseurs que Firebase garde pour le compte.
enum SignInProvider: Equatable, Hashable {
    case password
    case google
    case apple
}

/// Ce que Firebase répond quand on lui demande si la session du compte existe encore (un `reload`).
enum AccountSessionCheck: Equatable {
    /// Le compte existe et sa session est valide.
    case valid
    /// Firebase dit que le compte n'existe plus (`userNotFound`) : pour une suppression en cours, c'est un succès.
    case gone
    /// La session ne peut plus servir (jeton expiré ou révoqué) mais le compte existe peut-être : JAMAIS un succès.
    case expired
    /// Pas de réseau, ou une réponse floue : on ne peut rien conclure.
    case unknown
}

/// Le marqueur local « la suppression du compte est en cours » (voir `AccountDeletionMark`). Il a PRIORITÉ sur le routage du
/// lancement : sans lui, un profil déjà supprimé enverrait la personne vers « crée ton profil ».
protocol AccountDeletionMarker {
    func read() -> AccountDeletionMark?
    /// Écrit sur le disque AVANT de rendre la main : une app tuée aussitôt reprend quand même.
    func write(_ mark: AccountDeletionMark)
    func clear()
}

/// Ce que la suppression du compte lit et supprime de `users/{uid}` en plus des Miffs : les index, les appareils, le profil. Derrière un
/// protocole pour que la procédure soit testée avec un double, sans Firestore. Chaque écriture est une TRANSACTION (« pas
/// d'accusé » veut toujours dire « rien d'écrit »).
protocol AccountDataStore {
    /// Combien de documents `users/{uid}/memberships` contient, lu auprès du SERVEUR (échoue hors ligne). CHAQUE document compte, même
    /// un qui n'a pas la forme d'un index : rien ne doit rester derrière un profil supprimé. Un index périmé compte aussi.
    func membershipCount(uid: String) async throws -> Int

    /// Les identifiants de `users/{uid}/devices`, lus auprès du SERVEUR. Vide tant qu'aucune notification n'existe.
    func deviceIds(uid: String) async throws -> [String]

    /// Une transaction confirmée : l'appareil est supprimé (un document déjà absent n'est pas une erreur).
    func deleteDevice(uid: String, deviceId: String) async throws

    /// Une transaction confirmée : `users/{uid}` est supprimé (un profil déjà absent n'est pas une erreur).
    func deleteProfile(uid: String) async throws
}

/// Ce que la suppression du compte demande aux Miffs (un double dans les tests). Chaque appel atteint le SERVEUR et est accusé, ou échoue.
protocol AccountMiffs {
    func inventory(uid: String) async -> MiffsRead

    /// Un index PÉRIMÉ : sa Miff a disparu ou la personne a été retirée. Silencieux, jamais un message.
    func dropStale(uid: String, ref: MembershipRef) async -> MiffStep

    /// Le lot « Quitter » d'une Miff REJOINTE (aucun changement de code : une sortie volontaire, décision du PM).
    func leave(uid: String, ref: MembershipRef) async -> MiffStep

    /// La suppression d'une Miff CRÉÉE : la procédure existante (membres un par un, lieux, dernier lot), idempotente et reprenable.
    func deleteOwned(uid: String, miffId: String) async -> MiffStep
}

/// Rapporte où en est la procédure, pour l'écran de progression. Appelé sur le fil principal.
typealias DeletionStepReporter = @MainActor (DeletionStep) -> Void

/// Ce que le contrôleur demande à la procédure (un double dans les tests).
protocol AccountDeletionProcedure {
    func inventory(uid: String) async -> Inventory
    func run(uid: String, onStep: @escaping DeletionStepReporter) async -> DeletionRun
}

/// La session « un compte, un seul appareil » (SESS-3) du compte qu'on supprime. `deleteSession` supprime
/// `users/{uid}/session/active` et LÈVE une erreur si le serveur n'a pas accusé : la suppression du compte se met alors en pause
/// (reprenable), elle ne continue jamais sans l'accusé.
protocol AccountSession {
    func deleteSession(uid: String) async throws
}

/// Le serveur n'a pas confirmé la suppression de la session (autre chose qu'un réseau absent).
struct SessionDeletionUnconfirmed: Error, Equatable {}

/// Ce que le contrôleur demande à la session de l'app (implémenté par `SessionManager`).
@MainActor
protocol AccountDeletionSession: AnyObject {
    /// Étape 0 : arrête les écoutes, le suivi des lieux et toute écriture de statut ou de position, ferme l'appareil, et met l'app sur
    /// les écrans de la suppression. Rien ne doit ensuite réagir aux documents qui disparaissent (pas de message « accès perdu »,
    /// aucun statut réécrit). Idempotent : aussi appelé quand une suppression est reprise.
    func beginAccountDeletion() async

    /// Étape 9 : le compte a disparu. Purge le cache local, les réglages liés à la personne, la collecte de statistiques, et efface le
    /// marqueur EN DERNIER (une app tuée avant purge de nouveau au lancement suivant).
    func eraseAfterAccountDeletion(uid: String) async

    /// Des Miffs ont continué d'apparaître pendant la suppression : elle est ANNULÉE. La session rouvre l'appareil puis efface le marqueur ; l'app
    /// est remise sur ses rails (compte relu, routage habituel, ce qui s'était arrêté repart) et le dit UNE fois, calmement.
    func cancelAccountDeletion() async

    /// La session ne peut plus servir : cache purgé, retour à l'écran de connexion avec le marqueur GARDÉ et une phrase calme.
    func endSessionKeepingDeletionMarker() async
}

/// Ce que les écrans demandent à la suppression du compte (un double dans les tests).
@MainActor
protocol AccountDeletionControls: AnyObject {
    var state: AccountDeletionState { get }

    /// La personne vient de se ré-authentifier : le marqueur est écrit, l'app arrête ce qui tourne, la suppression continue.
    /// `apple` : ce que la ré-authentification a fait de l'autorisation Apple (retenu pour l'écran final).
    func start(apple: AppleRevocation)

    /// Lancement avec un marqueur et une session valide : la suppression reprend, sans nouvelle ré-authentification.
    func resume()

    /// « Réessayer » sur l'écran d'échec.
    func retry()

    /// La personne s'est ré-authentifiée (« Dernière vérification ») : la dernière étape est retentée. `apple` : voir `start`.
    func reauthenticated(apple: AppleRevocation)

    /// L'écran de fin a été fermé.
    func acknowledge()

    /// « Me reconnecter » : la session est fermée, le marqueur reste.
    func signInAgain()
}

/// Retire l'autorisation « Se connecter avec Apple » du compte (règle 5.1.1(v) de l'App Store et « Sign in with Apple » : un compte
/// supprimé doit aussi retirer le lien avec Apple). Sans serveur à nous : le SDK Firebase appelle lui-même Apple, avec le code
/// d'autorisation que la feuille Apple vient de donner. PRÉREQUIS CÔTÉ PM (`docs/dev/apple-revocation-suppression-compte.md`) : le
/// fournisseur Apple de la console Firebase doit porter l'identifiant de service, l'identifiant d'équipe, l'identifiant de clé et la clé
/// `.p8` (saisie dans la console seulement, jamais dans le dépôt ni dans une conversation). Sinon l'appel échoue : la suppression continue
/// (décision Q8a) et la personne sait retirer l'autorisation elle-même.
protocol AppleTokenRevoker {
    /// Vrai SEULEMENT si Apple a retiré l'autorisation. Le code est à usage unique et de courte durée ; il n'est ni stocké ni journalisé.
    func revoke(authorizationCode: String) async -> Bool
}
