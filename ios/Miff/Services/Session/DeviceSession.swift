import Foundation

/// La session de CET appareil : « un compte, un seul appareil » (SESS-3, ticket `docs/tickets/session-appareil-unique.md`, contrat
/// de `backend/data-model.md`, section du même nom). Du Swift pur : Firestore est derrière `DeviceSessionRemote`.
///
/// Ce que cette classe garantit :
/// - l'identifiant est généré à CHAQUE connexion, jamais lié à l'appareil physique. Il est gardé comme CANDIDAT AVANT l'écriture
///   sur le serveur (un processus tué entre l'écriture et l'enregistrement ne laisse pas une session que l'appareil ne reconnaît
///   pas), puis CONFIRMÉ après l'accusé. Un candidat n'est jamais jugé comme une session active : il est réutilisé par l'essai
///   suivant (une écriture tardive d'un essai précédent, validée par le serveur après coup, écrit alors le MÊME identifiant et ne
///   peut pas écraser une session plus récente). La reprise AUTOMATIQUE d'un candidat (lancement, retour du réseau : `check`)
///   LIT D'ABORD le serveur : session = candidat ou absente, on confirme ou on écrit ; session d'un AUTRE identifiant (la personne
///   s'est connectée ailleurs depuis), l'appareil est remplacé et le candidat abandonné. Seul un appel explicite à `begin`
///   (connexion, « Réessayer ») reprend la place ;
/// - `currentDeviceId` ne donne un identifiant que si le serveur a CONFIRMÉ que la session est celle de cet appareil : c'est la
///   seule source d'identifiant pour une écriture de statut ou de position (LOC-4b), donc « pas de confirmation = pas d'écriture » ;
/// - une session différente, ou disparue alors que l'appareil avait un identifiant, est un REMPLACEMENT ; une session qui existe
///   alors que l'appareil n'a PAS d'identifiant (réinstallation, sauvegarde restaurée, autre téléphone) en est un aussi ;
/// - aucune session sur le serveur ET aucun identifiant local (compte connecté avant SESS-3) : l'appareil en écrit une ;
/// - un stockage local ILLISIBLE est un état indéterminé : ni « remplacé », ni « aucune session » ; rien n'est écrit ni supprimé ;
/// - ne JAMAIS réécrire ni supprimer la session d'un appareil remplacé (`forget` n'efface que le local) : pas de boucle. Une
///   fois remplacé (ou déconnecté), l'appareil reste « révoqué » (marqueur PERSISTANT : aucune écriture, aucune vérification qui
///   réécrirait une session, même après la mort du processus) jusqu'à la prochaine connexion (`begin`) ;
/// - la suppression volontaire ne supprime que sa session ;
/// - une seule opération qui lit ou écrit la session à la fois (`AsyncGate`).
///
/// Les méthodes peuvent être appelées de n'importe quel fil (le dépôt des Miffs lit `currentDeviceId` hors du fil principal) :
/// l'état partagé est protégé par un verrou. La sérialisation des vérifications côté interface est assurée par `SessionManager`.
final class DeviceSession {
    private static let beginTries = 2

    private let remote: DeviceSessionRemote
    private let store: DeviceIdStore
    private let makeIdentifier: () -> String?
    private let gate = AsyncGate()
    private let lock = NSLock()
    private var confirmed: String?
    private var revoked = false
    /// La révocation vient d'un document de session DISPARU (compte supprimé ailleurs), pas d'un autre téléphone.
    private var gone = false
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

    private func locked<Value>(_ body: () -> Value) -> Value {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    /// L'identifiant à écrire dans un statut ou une position, SEULEMENT si le serveur a confirmé que la session est celle de cet
    /// appareil. nil = n'écris rien (pas encore vérifié, hors ligne depuis le lancement, ou remplacé).
    var currentDeviceId: String? {
        locked { confirmed }
    }

    /// Une connexion a commencé et le serveur ne l'a pas encore confirmée (processus tué ou réseau coupé pendant la connexion).
    var hasPendingBegin: Bool {
        if case .candidate = store.read() {
            return true
        }
        return false
    }

    /// Remplacé ou déconnecté, et pas encore reconnecté (marqueur persistant).
    var isRevoked: Bool {
        locked { revoked } || store.isRevoked()
    }

    /// Pourquoi l'appareil est révoqué : le document de session a DISPARU (le compte est supprimé depuis un autre appareil, ou depuis la
    /// console) plutôt qu'un autre téléphone qui prend la place. Ne décide que de la note de l'écran de connexion. Persistant.
    var replacedBecauseGone: Bool {
        locked { gone } || store.isGone()
    }

    /// Appelé quand une vérification faite hors du gestionnaire de session (après un refus d'écriture) découvre un remplacement :
    /// le gestionnaire s'en sert pour lancer la déconnexion forcée.
    func setReplacedHandler(_ handler: @escaping () -> Void) {
        locked { replacedHandler = handler }
    }

    private func notifyReplaced() {
        let handler = locked { replacedHandler }
        handler?()
    }

    private func setConfirmed(_ identifier: String?) {
        locked { confirmed = identifier }
    }

    /// Pose (ou retire) le marqueur « révoqué ». Le poser ne peut pas échouer côté mémoire : même si le disque refuse, l'appareil
    /// reste révoqué tant que le processus vit. Le retirer échoue (faux) si le disque refuse.
    @discardableResult
    private func setRevoked(_ value: Bool) -> Bool {
        if value {
            locked {
                revoked = true
                confirmed = nil
            }
            _ = store.setRevoked(true)
            return true
        }
        guard store.setRevoked(false) else {
            return false
        }
        // Une nouvelle connexion repart de zéro : plus de raison à garder.
        _ = store.setGone(false)
        locked {
            revoked = false
            gone = false
        }
        return true
    }

    /// Révocation parce que le document de session a disparu : la raison est sur le disque AVANT le marqueur (un processus tué entre
    /// les deux ne garde jamais un marqueur sans sa raison).
    private func revokeBecauseGone() {
        locked { gone = true }
        _ = store.setGone(true)
        setRevoked(true)
    }

    // MARK: - Connexion

    /// Nouvelle connexion : un identifiant, gardé comme candidat AVANT l'écriture, écrit dans la session, puis confirmé après l'accusé
    /// du serveur. Faux = pas confirmé : rien ne doit démarrer (surveillance, écritures, entrée dans une Miff) ; le candidat reste
    /// (réutilisé par l'essai suivant), sans jamais être jugé comme une session active.
    func begin(uid: String) async -> Bool {
        await gate.run { await self.beginUnlocked(uid: uid) }
    }

    private func beginUnlocked(uid: String) async -> Bool {
        setConfirmed(nil)
        guard setRevoked(false) else {
            return false
        }
        let identifier: String
        switch store.read() {
        case .unreadable:
            return false
        case .candidate(let existing):
            identifier = existing
        case .absent, .confirmed:
            guard let fresh = makeIdentifier(), DeviceId.isValid(fresh), store.saveCandidate(fresh) else {
                return false
            }
            identifier = fresh
        }
        // Deux essais avec le MÊME identifiant (comme Android) : une écriture sans réponse qui aboutit quand même est inoffensive.
        var written = false
        for _ in 0..<Self.beginTries where !written {
            written = await remote.write(uid: uid, deviceId: identifier)
        }
        guard written, store.confirm() else {
            return false
        }
        setConfirmed(identifier)
        return true
    }

    // MARK: - Vérification

    /// Lancement et retour du réseau : lit la session auprès du serveur, puis la juge. À faire AVANT toute écriture. Une connexion
    /// non confirmée (candidat) est reprise : l'écriture est retentée.
    func check(uid: String) async -> SessionCheck {
        await gate.run { await self.checkUnlocked(uid: uid) }
    }

    private func checkUnlocked(uid: String) async -> SessionCheck {
        if isRevoked {
            return .replaced
        }
        switch store.read() {
        case .unreadable:
            return .unverified
        case .candidate(let candidate):
            return await resumeCandidateUnlocked(candidate, uid: uid)
        case .absent, .confirmed:
            break
        }
        guard let read = await remote.read(uid: uid) else {
            return .unverified
        }
        return await judgeUnlocked(read, uid: uid)
    }

    /// Reprise AUTOMATIQUE d'une connexion non confirmée : le serveur est lu d'abord, pour ne jamais chasser un autre appareil qui
    /// s'est connecté entre-temps (la dernière connexion réelle gagne).
    private func resumeCandidateUnlocked(_ candidate: String, uid: String) async -> SessionCheck {
        guard let read = await remote.read(uid: uid) else {
            return .unverified
        }
        switch read {
        case .active(let identifier):
            guard identifier == candidate else {
                setRevoked(true)
                return .replaced
            }
            // L'écriture avait bien abouti : il ne manque que la confirmation locale.
            guard store.confirm() else {
                return .unverified
            }
            setConfirmed(candidate)
            return .confirmed
        case .absent:
            let written = await beginUnlocked(uid: uid)
            return written ? .confirmed : .unverified
        }
    }

    /// Juge ce que dit le serveur (lecture explicite ou instantané de l'écoute). Tableau de décision, couvert par des tests :
    ///
    /// - la session du serveur est la mienne (identifiant confirmé égal) : confirmée ;
    /// - une autre (identifiant confirmé différent, ou aucun identifiant : réinstallation, autre téléphone) : remplacée ;
    /// - aucune session et un identifiant confirmé (session disparue) : remplacée ;
    /// - aucune session et aucun identifiant (ancienne installation) : j'en écris une, confirmée ;
    /// - un candidat (connexion non confirmée) ou un stockage illisible : non vérifiée, rien n'est écrit, personne n'est déconnecté.
    func judge(_ read: RemoteSession, uid: String) async -> SessionCheck {
        await gate.run { await self.judgeUnlocked(read, uid: uid) }
    }

    private func judgeUnlocked(_ read: RemoteSession, uid: String) async -> SessionCheck {
        if isRevoked {
            return .replaced
        }
        let local: String?
        switch store.read() {
        case .unreadable, .candidate:
            return .unverified
        case .confirmed(let identifier):
            local = identifier
        case .absent:
            local = nil
        }
        switch SessionJudge.judge(local: local, remote: read) {
        case .mine:
            if let local {
                setConfirmed(local)
            }
            return .confirmed
        case .writeNew:
            let written = await beginUnlocked(uid: uid)
            return written ? .confirmed : .unverified
        case .replaced:
            setRevoked(true)
            return .replaced
        case .sessionGone:
            // Même déconnexion locale qu'un remplacement, mais la raison est gardée : la note de l'écran de connexion ne dit pas
            // « autre téléphone » quand personne n'a pris la place.
            revokeBecauseGone()
            return .replaced
        }
    }

    /// Après qu'une écriture a été REFUSÉE : un refus n'est pas à lui seul la preuve d'un remplacement (consentement, partage
    /// arrêté...). La session est relue auprès du serveur : différente ou absente = remplacement, et le gestionnaire est prévenu.
    func checkAfterRefusal(uid: String) async -> SessionCheck {
        let verdict = await check(uid: uid)
        if verdict == .replaced {
            notifyReplaced()
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

    /// L'écoute du document s'est terminée sans que personne l'ait demandé : plus rien ne garantit la session. Les écritures sont
    /// refusées (`currentDeviceId` vide) jusqu'à la prochaine vérification réussie.
    func markUnverified() {
        setConfirmed(nil)
    }

    func changes(uid: String) -> AsyncStream<RemoteSession> {
        remote.changes(uid: uid)
    }

    // MARK: - Fin

    /// Supprime SA session sur le serveur (jamais celle d'un autre appareil), sans toucher à l'état local. Vrai si la session est
    /// supprimée OU s'il n'y avait rien à supprimer pour cet appareil (aucun identifiant, ou déjà remplacé). Faux si le serveur
    /// n'a pas confirmé la suppression (hors ligne, lenteur) ou si le stockage est illisible : la session est peut-être encore là.
    /// Une procédure qui exige que la session ait disparu (suppression d'un compte) s'arrête alors et reste reprenable.
    func deleteSession(uid: String, online: Bool = true) async -> Bool {
        await gate.run { await self.deleteSessionUnlocked(uid: uid, online: online) }
    }

    private func deleteSessionUnlocked(uid: String, online: Bool) async -> Bool {
        switch store.read() {
        case .absent:
            return true
        case .unreadable:
            return false
        case .candidate(let identifier), .confirmed(let identifier):
            if isRevoked {
                return true
            }
            guard online else {
                return false
            }
            return await remote.deleteIfMine(uid: uid, deviceId: identifier)
        }
    }

    /// SUPPRESSION DU COMPTE (AUTH-5c), tout début : l'appareil est FERMÉ, marqueur sur le disque AVANT toute écriture de la procédure.
    /// Aucune vérification ne réécrit plus de session, aucun statut ni position ne part (`currentDeviceId` vide), même si le processus
    /// meurt et qu'un réveil par une zone le relance au milieu de la suppression (sans cela, « aucun identifiant et plus de session »
    /// ressemblerait à une ancienne installation et écrirait une NOUVELLE session). Rouvert par une nouvelle connexion ou par
    /// `restoreDeviceSession` (suppression annulée) : `begin` lève le marqueur. Le marqueur est celui de la déconnexion forcée (la
    /// raison « disparue » n'est pas posée : personne n'a pris la place).
    func closeForAccountDeletion() {
        setRevoked(true)
    }

    /// Suppression du compte, étape « session » : la session est supprimée QUI QUE SOIT celui qui la tient, et l'identifiant local est
    /// oublié SEULEMENT après l'accusé du serveur. Faux (rien n'est supposé supprimé) si le serveur n'a pas confirmé : la suppression
    /// du compte s'arrête et reste reprenable. L'appareil reste fermé dans tous les cas.
    func deleteForAccountDeletion(uid: String) async -> Bool {
        await gate.run { await self.deleteForAccountDeletionUnlocked(uid: uid) }
    }

    private func deleteForAccountDeletionUnlocked(uid: String) async -> Bool {
        setRevoked(true)
        guard await remote.delete(uid: uid) else {
            return false
        }
        store.clear()
        return true
    }

    /// Oublie l'identifiant local SANS marqueur « révoqué » : seulement pour une connexion qui n'a jamais été la nôtre (un candidat
    /// laissé par un autre compte). Après une suppression de session, utiliser `forget`.
    func clearLocal() {
        setConfirmed(nil)
        store.clear()
    }

    /// Déconnexion VOLONTAIRE : supprime SA session si possible, puis oublie l'identifiant. À appeler APRÈS l'envoi des écritures en
    /// attente et AVANT la purge du cache (`terminate()`) et la déconnexion : après, plus aucune requête authentifiée n'est
    /// possible. Hors ligne : rien n'est supprimé (la prochaine connexion écrasera la session). `online` faux : on n'attend même
    /// pas un délai (une transaction a besoin du serveur).
    ///
    /// L'appareil est ensuite FERMÉ (marqueur « révoqué », mémoire et disque) tout de suite, pas seulement à la fin de la
    /// déconnexion : pendant la purge du cache, une vérification déjà en file ne peut pas réécrire une session ni autoriser un
    /// statut. La connexion suivante (`begin`) lève le marqueur.
    func release(uid: String, online: Bool = true) async {
        _ = await deleteSession(uid: uid, online: online)
        forget()
    }

    /// Déconnexion FORCÉE ou session perdue : pose le marqueur « révoqué » (AVANT tout le reste), oublie l'identifiant local, ne
    /// touche JAMAIS au serveur (la session appartient peut-être déjà à un autre appareil, et la réécrire ou la supprimer ferait une
    /// boucle).
    func forget() {
        setRevoked(true)
        store.clear()
    }
}
