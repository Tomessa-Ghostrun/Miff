import Foundation

/// La session de CET appareil : « un compte, un seul appareil » (SESS-3, ticket `docs/tickets/session-appareil-unique.md`, contrat
/// de `backend/data-model.md`, section du même nom). Du Swift pur : Firestore est derrière `DeviceSessionRemote`.
///
/// Ce que cette classe garantit :
/// - l'identifiant est généré à CHAQUE connexion, jamais lié à l'appareil physique, et gardé SEULEMENT après l'accusé du serveur ;
/// - `currentDeviceId` ne donne un identifiant que si le serveur a CONFIRMÉ que la session est celle de cet appareil : c'est la
///   seule source d'identifiant pour une écriture de statut ou de position (LOC-4b), donc « pas de confirmation = pas d'écriture » ;
/// - une session différente, ou disparue alors que l'appareil avait un identifiant, est un REMPLACEMENT ; une session qui existe
///   alors que l'appareil n'a PAS d'identifiant (réinstallation, sauvegarde restaurée, autre téléphone) en est un aussi ;
/// - aucune session sur le serveur ET aucun identifiant local (compte connecté avant SESS-3) : l'appareil en écrit une ;
/// - ne JAMAIS réécrire ni supprimer la session d'un appareil remplacé (`forget` n'efface que le local) : pas de boucle. Une
///   fois remplacé (ou déconnecté), l'appareil reste « révoqué » (aucune écriture, aucune vérification qui réécrirait une session)
///   jusqu'à la prochaine connexion (`begin`) ;
/// - la suppression volontaire ne supprime que SA session.
///
/// Les méthodes peuvent être appelées de n'importe quel fil (le dépôt des Miffs lit `currentDeviceId` hors du fil principal) :
/// l'état partagé est protégé par un verrou. La sérialisation des vérifications est assurée par `SessionManager`.
final class DeviceSession {
    private let remote: DeviceSessionRemote
    private let store: DeviceIdStore
    private let makeIdentifier: () -> String?
    private let lock = NSLock()
    private var confirmed: String?
    private var revoked = false
    private var replacedHandler: (() -> Void)?

    init(
        remote: DeviceSessionRemote,
        store: DeviceIdStore,
        makeIdentifier: @escaping () -> String? = { DeviceId.generate(randomBytes: SecureRandom.bytes) }
    ) {
        self.remote = remote
        self.store = store
        self.makeIdentifier = makeIdentifier
    }

    /// L'identifiant à écrire dans un statut ou une position, SEULEMENT si le serveur a confirmé que la session est celle de cet
    /// appareil. nil = n'écris rien (pas encore vérifié, hors ligne depuis le lancement, ou remplacé).
    var currentDeviceId: String? {
        lock.lock()
        defer { lock.unlock() }
        return confirmed
    }

    /// Appelé quand une vérification faite hors du gestionnaire de session (après un refus d'écriture) découvre un remplacement :
    /// le gestionnaire s'en sert pour lancer la déconnexion forcée.
    func setReplacedHandler(_ handler: @escaping () -> Void) {
        lock.lock()
        replacedHandler = handler
        lock.unlock()
    }

    private func setConfirmed(_ identifier: String?) {
        lock.lock()
        confirmed = identifier
        lock.unlock()
    }

    private var isRevoked: Bool {
        lock.lock()
        defer { lock.unlock() }
        return revoked
    }

    private func setRevoked(_ value: Bool) {
        lock.lock()
        revoked = value
        if value {
            confirmed = nil
        }
        lock.unlock()
    }

    // MARK: - Connexion

    /// Nouvelle connexion : un nouvel identifiant, écrit dans la session ; gardé en local SEULEMENT après l'accusé du serveur
    /// (une écriture sans réponse ne doit jamais laisser un identifiant local qui n'est celui d'aucune session). Faux = pas
    /// confirmé : rien ne doit démarrer (surveillance, écritures, entrée dans une Miff).
    func begin(uid: String) async -> Bool {
        setConfirmed(nil)
        store.clear()
        guard let identifier = makeIdentifier(), DeviceId.isValid(identifier) else {
            return false
        }
        // Deux essais avec le MÊME identifiant (comme Android) : une écriture sans réponse qui aboutit quand même est inoffensive.
        var written = false
        for _ in 0..<Self.beginTries where !written {
            written = await remote.write(uid: uid, deviceId: identifier)
        }
        guard written, store.save(identifier) else {
            return false
        }
        lock.lock()
        revoked = false
        confirmed = identifier
        lock.unlock()
        return true
    }

    private static let beginTries = 2

    // MARK: - Vérification

    /// Lancement et retour du réseau : lit la session auprès du serveur, puis la juge. À faire AVANT toute écriture.
    func check(uid: String) async -> SessionCheck {
        if isRevoked {
            return .replaced
        }
        guard let read = await remote.read(uid: uid) else {
            return .unverified
        }
        return await judge(read, uid: uid)
    }

    /// Juge ce que dit le serveur (lecture explicite ou instantané de l'écoute). Tableau de décision, couvert par des tests :
    ///
    /// | serveur            | identifiant local | verdict                                              |
    /// |--------------------|-------------------|------------------------------------------------------|
    /// | la mienne          | oui, égal         | confirmée                                            |
    /// | une autre          | oui, différent    | remplacée                                            |
    /// | une autre          | non               | remplacée (réinstallation, autre téléphone)          |
    /// | aucune             | oui               | remplacée (session disparue)                         |
    /// | aucune             | non               | j'en écris une (ancienne installation) : confirmée   |
    func judge(_ read: RemoteSession, uid: String) async -> SessionCheck {
        if isRevoked {
            return .replaced
        }
        let local = store.read()
        switch read {
        case .active(let identifier):
            if let local, local == identifier {
                setConfirmed(local)
                return .confirmed
            }
            setRevoked(true)
            return .replaced
        case .absent:
            if local != nil {
                setRevoked(true)
                return .replaced
            }
            let written = await begin(uid: uid)
            return written ? .confirmed : .unverified
        }
    }

    /// Après qu'une écriture a été REFUSÉE : un refus n'est pas à lui seul la preuve d'un remplacement (consentement, partage
    /// arrêté...). La session est relue auprès du serveur : différente ou absente = remplacement, et le gestionnaire est prévenu.
    func checkAfterRefusal(uid: String) async -> SessionCheck {
        let verdict = await check(uid: uid)
        if verdict == .replaced {
            lock.lock()
            let handler = replacedHandler
            lock.unlock()
            handler?()
        }
        return verdict
    }

    /// Avant la PREMIÈRE écriture d'une série de statuts ou de positions, quand rien n'a encore été confirmé dans ce processus
    /// (réveil en arrière-plan par une zone, LOC-4b) : l'identifiant à écrire, ou pourquoi rien ne s'écrit. Une session déjà
    /// confirmée est réutilisée sans lecture de plus ; sinon le serveur est interrogé, et un remplacement lance la déconnexion
    /// forcée.
    func clearance(uid: String) async -> DeviceClearance {
        if isRevoked {
            return .replaced
        }
        if let identifier = currentDeviceId {
            return .cleared(deviceId: identifier)
        }
        switch await checkAfterRefusal(uid: uid) {
        case .confirmed:
            return currentDeviceId.map { DeviceClearance.cleared(deviceId: $0) } ?? .unverified
        case .replaced:
            return .replaced
        case .unverified:
            return .unverified
        }
    }

    func changes(uid: String) -> AsyncStream<RemoteSession> {
        remote.changes(uid: uid)
    }

    // MARK: - Fin

    /// Déconnexion VOLONTAIRE : supprime SA session (jamais celle d'un autre appareil), puis oublie l'identifiant. À appeler APRÈS
    /// l'envoi des écritures en attente et AVANT la purge du cache (`terminate()`) et la déconnexion : après, plus aucune requête
    /// authentifiée n'est possible. Hors ligne : rien n'est supprimé (la prochaine connexion écrasera la session).
    func release(uid: String, online: Bool = true) async {
        let local = store.read()
        setConfirmed(nil)
        // Hors ligne (`online` faux) on n'attend même pas un délai : une transaction a besoin du serveur.
        if let local, online, !isRevoked {
            _ = await remote.deleteIfMine(uid: uid, deviceId: local)
        }
        store.clear()
        setRevoked(false)
    }

    /// Déconnexion FORCÉE ou session perdue : oublie l'identifiant local, ne touche JAMAIS au serveur (la session appartient
    /// peut-être déjà à un autre appareil, et la réécrire ou la supprimer ferait une boucle).
    func forget() {
        setRevoked(true)
        store.clear()
    }
}
