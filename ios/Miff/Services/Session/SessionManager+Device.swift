import Foundation

// Un compte, un seul appareil (SESS-3) : ce que `SessionManager` fait de la session de CET appareil. Ticket
// `docs/tickets/session-appareil-unique.md`, contrat `backend/data-model.md` (section « Un compte, un seul appareil »).
//
// Règles tenues ici, chacune couverte par un test :
// - à chaque connexion (e-mail, Google, Apple, inscription) la session est écrite ET confirmée par le serveur AVANT que l'app avance ;
//   sans accusé, l'app reste sur « Réessayer » : rien ne démarre ;
// - au lancement, la session est vérifiée AVANT toute écriture ; hors ligne personne n'est déconnecté, mais `DeviceSession`
//   n'autorise aucune écriture de statut ni de position tant que le serveur n'a pas confirmé ;
// - l'écoute du document de session ne juge que des instantanés du SERVEUR ;
// - une déconnexion forcée n'écrit et ne supprime JAMAIS la session ; une déconnexion volontaire ne supprime que la sienne ;
// - une seule vérification à la fois : celles qui arrivent pendant une autre sont rejouées une fois, par une relecture.
extension SessionManager {
    // MARK: - Connexion

    /// Écrit la session de cet appareil (nouvelle connexion, ou « Réessayer » après un accusé manquant). Vrai = confirmée par le
    /// serveur, l'écoute démarre. Faux = rien n'est confirmé : `pendingDeviceBegin` reste vrai et l'appelant montre « Réessayer ».
    func beginDeviceSession(uid: String) async -> Bool {
        pendingDeviceBegin = true
        let confirmed = await device.begin(uid: uid)
        guard confirmed, gateway.currentUid == uid, !ending else {
            return false
        }
        pendingDeviceBegin = false
        // Une nouvelle connexion : l'ancien message « autre téléphone », s'il n'avait pas encore été montré, n'a plus lieu d'être.
        flags.set(false, forKey: Self.keyReplacedNoticePending)
        startDeviceWatch(uid: uid)
        return true
    }

    /// Lancement et « Réessayer » avec un compte connecté : vrai = l'app peut lire le compte. Faux = la suite est déjà réglée (écran
    /// « Réessayer » ou déconnexion forcée).
    func prepareDeviceSession(uid: String) async -> Bool {
        if pendingDeviceBegin {
            if await beginDeviceSession(uid: uid) {
                return true
            }
            if gateway.currentUid == uid, !ending {
                setState(.lookupFailed)
            }
            return false
        }
        // Hors ligne (le système le dit), on n'attend aucun délai : on avance, et la vérification se fait au retour du réseau.
        var verdict = SessionCheck.unverified
        if network.isOnline() {
            verdict = await reviewDeviceSession(uid: uid, incoming: nil)
        }
        guard verdict != .replaced, gateway.currentUid == uid, !ending else {
            return false
        }
        // Injoignable : on avance (hors ligne personne n'est déconnecté), mais aucune écriture ne part sans confirmation.
        if deviceWatchers.isEmpty {
            startDeviceWatch(uid: uid)
        }
        return true
    }

    // MARK: - Écoute

    func startDeviceWatch(uid: String) {
        stopDeviceWatch()
        let changes = device.changes(uid: uid)
        let online = network.onlineChanges()
        let sessionWatch = Task { [weak self] in
            for await remote in changes {
                _ = await self?.reviewDeviceSession(uid: uid, incoming: remote)
            }
        }
        let networkWatch = Task { [weak self] in
            for await isOnline in online where isOnline {
                await self?.recheckDeviceSessionIfUnverified(uid: uid)
            }
        }
        deviceWatchers = [sessionWatch, networkWatch]
    }

    func stopDeviceWatch() {
        for watch in deviceWatchers {
            watch.cancel()
        }
        deviceWatchers = []
    }

    /// Retour du réseau : tant que le serveur n'a pas confirmé la session, on la lit AVANT toute écriture. Une session déjà
    /// confirmée n'est pas relue (l'écoute du document la tient à jour sans lecture de plus).
    private func recheckDeviceSessionIfUnverified(uid: String) async {
        guard device.currentDeviceId == nil else {
            return
        }
        _ = await reviewDeviceSession(uid: uid, incoming: nil)
    }

    // MARK: - Vérification

    private func canReviewDeviceSession(uid: String) -> Bool {
        !ending && sessionActive && gateway.currentUid == uid
    }

    /// Juge la session (un instantané de l'écoute, ou une lecture du serveur si `incoming` est nil) et, si elle est remplacée,
    /// termine la session locale. Une seule vérification à la fois : si une autre arrive pendant celle-ci, elle est rejouée UNE
    /// fois par une lecture du serveur (qui dit la vérité du moment, même si un instantané a été écarté).
    @discardableResult
    func reviewDeviceSession(uid: String, incoming: RemoteSession?) async -> SessionCheck {
        guard canReviewDeviceSession(uid: uid) else {
            return .unverified
        }
        if deviceReviewBusy {
            deviceReviewDirty = true
            return .unverified
        }
        deviceReviewBusy = true
        var verdict = SessionCheck.unverified
        var pendingRead = incoming
        repeat {
            deviceReviewDirty = false
            if let read = pendingRead {
                verdict = await device.judge(read, uid: uid)
            } else {
                verdict = await device.check(uid: uid)
            }
            pendingRead = nil
        } while deviceReviewDirty && verdict != .replaced && canReviewDeviceSession(uid: uid)
        deviceReviewBusy = false
        if verdict == .replaced, canReviewDeviceSession(uid: uid) {
            await endBecauseReplaced()
        }
        return verdict
    }

    /// Le compte est gardé finalement (suppression refusée ou annulée) : la session qui venait d'être supprimée est réécrite. Si le
    /// serveur ne confirme pas, `pendingDeviceBegin` reste vrai : `Réessayer` ou le prochain lancement la réécrit, et aucun statut
    /// ne part d'ici là.
    func restoreDeviceSession() async {
        guard let uid = gateway.currentUid else {
            return
        }
        _ = await beginDeviceSession(uid: uid)
    }

    // MARK: - Fin volontaire

    /// Déconnexion VOLONTAIRE : l'écoute s'arrête D'ABORD (sa propre suppression de session serait lue comme « disparue »), puis
    /// la session est supprimée si c'est la sienne.
    func releaseDeviceSession(uid: String?) async {
        stopDeviceWatch()
        guard let uid else {
            device.forget()
            return
        }
        await device.release(uid: uid, online: network.isOnline())
    }
}
