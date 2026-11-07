import Foundation

/// `AccountSession` branchée sur la session de cet appareil (`DeviceSession`, SESS-3). Elle supprime `users/{uid}/session/active`,
/// QUI QUE SOIT celui qui la tient (le compte disparaît), et ne rend la main qu'avec l'accusé du serveur : sinon elle lève une erreur et
/// la suppression du compte se met en pause (reprenable), elle ne continue jamais sans l'accusé.
///
/// Hors connexion, aucune requête n'est tentée : une transaction a besoin du serveur, et la pause « pas de réseau » est la bonne réponse
/// (l'erreur est celle d'un délai dépassé, que la procédure lit comme un manque de réseau et non comme un échec).
struct DeviceAccountSession: AccountSession {
    let device: DeviceSession
    let network: NetworkMonitor

    func deleteSession(uid: String) async throws {
        guard network.isOnline() else {
            throw OperationTimedOut()
        }
        guard await device.deleteForAccountDeletion(uid: uid) else {
            throw SessionDeletionUnconfirmed()
        }
    }
}
