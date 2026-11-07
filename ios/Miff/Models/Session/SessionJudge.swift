import Foundation

/// Ce qu'il faut faire de la session de cet appareil une fois le SERVEUR lu (SESS-3, miroir d'Android `SessionJudge`).
enum SessionVerdict: Equatable {
    /// La session est celle de cet appareil.
    case mine
    /// Le compte n'a pas de session et cet appareil n'a pas d'identifiant (connecté avant la fonctionnalité) : il écrit la sienne.
    case writeNew
    /// Un autre téléphone tient la session (ou il y en a une et cet appareil n'a pas d'identifiant) : déconnexion locale, sans
    /// toucher à la session.
    case replaced
    /// Cet appareil A un identifiant mais le DOCUMENT de session a disparu : il n'est supprimé que par la suppression du compte
    /// (qui le supprime en premier) ou depuis la console. Déconnexion locale comme un remplacement, mais JAMAIS le message « ton
    /// compte est utilisé sur un autre téléphone » : personne n'a pris la place, et une suppression peut encore être annulée.
    case sessionGone
}

enum SessionJudge {
    /// La règle du lancement, du retour du réseau et d'une écriture refusée, pour un appareil CONNECTÉ :
    /// - même identifiant que celui du serveur : `mine` ;
    /// - ni session ni identifiant local (ancienne installation) : `writeNew` ;
    /// - un autre identifiant, ou une session sans identifiant local : `replaced` ;
    /// - un identifiant local mais plus de session : `sessionGone`.
    /// La connexion elle-même ne passe jamais ici : elle écrit sa session, quoi qu'il y ait (la dernière connexion gagne).
    static func judge(local: String?, remote: RemoteSession) -> SessionVerdict {
        switch remote {
        case .absent:
            return local == nil ? .writeNew : .sessionGone
        case .active(let identifier):
            if let local, local == identifier {
                return .mine
            }
            return .replaced
        }
    }
}
