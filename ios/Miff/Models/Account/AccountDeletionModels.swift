import Foundation

// Les valeurs de la suppression d'un compte (AUTH-5c, miroir de `data/account` d'Android). Du Swift pur.
//
// SÉCURITÉ, pour ce fichier : un nom de Miff, un identifiant de Miff ou de compte est une donnée personnelle. Chaque type qui en porte
// cache son contenu dans sa description : rien d'ici ne finit dans un journal, un événement ou un message d'erreur.

/// Une Miff que le compte a créée, telle que l'écran d'information la liste : son nom et le nombre des AUTRES personnes qui y sont.
struct AccountOwnedMiff: Equatable, CustomStringConvertible {
    let miffId: String
    let name: String
    let otherMembers: Int

    var description: String {
        "OwnedMiff(redacted)"
    }
}

/// Ce que l'écran d'information montre des Miffs du compte.
struct AccountMiffsSummary: Equatable, CustomStringConvertible {
    /// Combien de Miffs le compte a REJOINTES (il les quitte).
    let joined: Int
    /// Les Miffs qu'il a CRÉÉES, avec le nombre des autres personnes dans chacune : elles sont supprimées.
    let owned: [AccountOwnedMiff]

    static let none = AccountMiffsSummary(joined: 0, owned: [])

    /// Le compte est dans au moins une Miff, créée ou rejointe (un index périmé ne compte pas : il est nettoyé sans un mot).
    var hasMiffs: Bool {
        joined > 0 || !owned.isEmpty
    }

    var description: String {
        "AccountMiffsSummary(redacted)"
    }
}

/// Ce que le SERVEUR dit que le compte détient et que la suppression doit traiter d'abord : les Miffs.
struct MiffsInventory: Equatable, CustomStringConvertible {
    let joined: [MembershipRef]
    let owned: [AccountOwnedMiff]
    /// Les index PÉRIMÉS (la Miff a été supprimée ou la personne retirée) : seul l'index reste.
    let stale: [MembershipRef]

    var isEmpty: Bool {
        joined.isEmpty && owned.isEmpty && stale.isEmpty
    }

    /// Les Miffs que la personne connaît (les index périmés sont nettoyés sans un mot).
    var visibleCount: Int {
        joined.count + owned.count
    }

    /// Les personnes que la suppression des Miffs créées retire, un lot chacune : décide si l'attente est dite plus longue.
    var removals: Int {
        owned.reduce(0) { $0 + $1.otherMembers }
    }

    var description: String {
        "MiffsInventory(redacted)"
    }
}

enum MiffsRead: Equatable {
    case read(MiffsInventory)
    case failed(MiffFailure)
}

/// Comment une Miff de la suppression s'est terminée : faite (sur le serveur, accusée), ou l'unique raison pour laquelle non.
enum MiffStep: Equatable {
    case done
    case failed(MiffFailure)
}

/// Ce que la lecture du serveur a répondu pour l'écran d'information.
enum Inventory: Equatable {
    /// Lue sur le SERVEUR, complète.
    case loaded(AccountMiffsSummary)
    case offline
    case failed
}

/// Où en est la procédure, pour l'écran de progression : jamais un pourcentage, jamais le détail des lots.
enum DeletionStep: Equatable {
    /// On lit ce que le compte détient : pas encore de ligne.
    case checking
    /// « Miff {index} sur {total} » : les Miffs rejointes d'abord, puis les créées. `longWait` : plus de trois personnes à retirer.
    case miff(index: Int, total: Int, longWait: Bool)
    /// Les Miffs sont faites : les appareils, la session, le profil et le compte.
    case profile
}

/// Comment une exécution de la procédure s'est terminée. La relancer repart toujours de l'état lu sur le serveur.
enum DeletionRun: Equatable {
    /// Le compte d'authentification est SUPPRIMÉ : par cette exécution, ou Firebase dit qu'il n'existe plus (`userNotFound`, le seul
    /// code qui le prouve). Rien d'autre ne compte : ni « personne n'est connecté », ni un jeton expiré ou révoqué.
    case completed
    /// La session ne peut plus servir alors que le compte existe peut-être encore : le marqueur reste, rien ne dit « supprimé », la
    /// personne se reconnecte et la suppression reprend (même compte).
    case sessionExpired
    /// Arrêtée faute de réseau : rien n'est perdu, elle continue au retour du réseau.
    case offline
    /// Firebase veut une connexion plus récente avant de supprimer le compte (« Dernière vérification »).
    case needsRecentLogin
    /// Des Miffs continuent d'apparaître (créées ou rejointes depuis un autre téléphone pendant la suppression) : un profil ou un
    /// compte n'est jamais supprimé avec une Miff derrière lui, la suppression est ANNULÉE (rien du profil n'est supprimé).
    case miffsRemain
    /// Arrêtée pour une autre raison : réessayer est possible.
    case failed
}

/// Où en est la suppression du compte, telle que les écrans la disent (toujours calme : jamais un décompte de ce qui reste).
enum AccountDeletionState: Equatable {
    /// Rien en cours (ou pas encore commencé dans ce lancement).
    case idle
    /// En cours. `resumed` : commencée par un lancement précédent. `retrying` : après « Réessayer ».
    case running(resumed: Bool, retrying: Bool, step: DeletionStep)
    /// En pause faute de réseau : elle repart toute seule au retour du réseau.
    case waitingForNetwork
    /// Firebase demande une connexion plus récente : l'écran « Dernière vérification ».
    case needsRecentLogin
    /// Arrêtée après plusieurs essais : le contact et « Réessayer ».
    case stuck
    /// La session ne peut plus servir (fermée dans notre dos, jeton expiré ou révoqué) : RIEN ne dit « supprimé » ; le marqueur reste et se
    /// reconnecter avec le même compte reprend la suppression.
    case needsSignIn
    /// Tout est supprimé et les données locales nettoyées. `appleRevocation` : ce qui s'est passé pour l'autorisation Apple.
    case done(appleRevocation: AppleRevocation)
}

/// Ce qui est arrivé à l'autorisation « Se connecter avec Apple » pendant la suppression (règle 5.1.1(v) de l'App Store).
enum AppleRevocation: String, Equatable {
    /// Le compte n'est pas lié à Apple : rien à retirer.
    case notNeeded
    /// Apple a retiré l'autorisation (appel de révocation accepté).
    case revoked
    /// La révocation a échoué (réseau, code périmé, configuration de la console Firebase manquante) : la suppression a continué, la
    /// personne sait comment retirer l'autorisation elle-même (décision Q8a du 2026-11-05), et la soumission reste bloquée tant que la
    /// révocation n'est pas prouvée sur l'iPhone (SI1).
    case failed
}

/// Le marqueur local « la suppression d'un compte est en cours » : l'identifiant du compte qui l'a lancée, et si Firebase a CONFIRMÉ la
/// suppression du compte d'authentification. Rien d'autre : ni nom, ni e-mail, ni code (ticket §5.6).
struct AccountDeletionMark: Equatable, CustomStringConvertible {
    let uid: String
    /// Firebase a CONFIRMÉ la suppression (écrit avant le nettoyage local). C'est la seule chose qui distingue, à un lancement sans
    /// session, « le compte est supprimé » de « la session a été fermée dans notre dos ».
    var accountDeleted = false

    var description: String {
        "AccountDeletionMark(redacted)"
    }
}

/// Qui la personne peut prévenir avant de supprimer (QM9) : un bouton par Miff nommée (trois au plus), et un de plus, sans nom, pour
/// celles qui ne peuvent pas être nommées.
struct WarnPlan: Equatable, CustomStringConvertible {
    let named: [String]
    let generic: Bool

    /// Le cas courant : UNE Miff à prévenir, le bouton simple « Prévenir mes proches ».
    var single: Bool {
        named.count == 1 && !generic
    }

    var isEmpty: Bool {
        named.isEmpty && !generic
    }

    var description: String {
        "WarnPlan(redacted)"
    }
}

extension AccountMiffsSummary {
    /// Les Miffs que l'écran d'information nomme (les autres sont comptées : « et {k} autres »).
    static let maxListedOwned = 3

    var warnPlan: WarnPlan {
        let listed = owned.prefix(Self.maxListedOwned)
        let named = listed.filter { $0.otherMembers > 0 && !$0.name.isEmpty }.map { $0.name }
        // Une Miff qu'on ne peut pas nommer dans un message (nom vide, ou au-delà des trois listées) est couverte par UN message sans nom.
        let unnamed = listed.contains { $0.otherMembers > 0 && $0.name.isEmpty }
            || owned.dropFirst(Self.maxListedOwned).contains { $0.otherMembers > 0 }
        return WarnPlan(named: named, generic: unnamed)
    }
}
