import Foundation

// Un compte, un seul appareil (SESS-3) : ce que `SessionManager` fait de la session de CET appareil. Ticket
// `docs/tickets/session-appareil-unique.md`, contrat `backend/data-model.md` (section « Un compte, un seul appareil »).
//
// Règles tenues ici, chacune couverte par un test :
// - à chaque connexion (e-mail, Google, Apple, inscription) la session est écrite ET confirmée par le serveur AVANT que l'app avance ;
//   sans accusé, l'app reste sur « Réessayer » : rien ne démarre, mais l'écoute et le suivi du réseau tournent déjà et reprennent
//   la connexion dès que le serveur répond ;
// - au lancement, la session est vérifiée AVANT toute écriture ; hors ligne personne n'est déconnecté, mais `DeviceSession`
//   n'autorise aucune écriture de statut ni de position tant que le serveur n'a pas confirmé ;
// - l'écoute du document de session ne juge que des instantanés du SERVEUR ; si elle se termine toute seule (erreur), plus rien
//   n'est confirmé, elle est relancée (bornée) et la session est relue ;
// - à CHAQUE retour du réseau la session est relue (une lecture par retour), confirmée ou non ;
// - une déconnexion forcée n'écrit et ne supprime JAMAIS la session ; une déconnexion volontaire ne supprime que la sienne ;
// - une seule vérification à la fois : celles qui arrivent pendant une autre sont rejouées une fois, par une relecture.
extension SessionManager {
    // MARK: - Connexion

    /// Écrit la session de cet appareil (nouvelle connexion, ou « Réessayer » après un accusé manquant). Vrai = confirmée par le
    /// serveur. Faux = rien n'est confirmé : `pendingDeviceBegin` reste vrai (et le candidat est conservé sur disque) ; l'appelant
    /// montre « Réessayer ». L'écoute et le suivi du réseau démarrent dans les deux cas : au retour du réseau la connexion est reprise.
    func beginDeviceSession(uid: String) async -> Bool {
        pendingDeviceBegin = true
        let confirmed = await device.begin(uid: uid)
        guard gateway.currentUid == uid, !ending else {
            return false
        }
        if deviceWatchers.isEmpty {
            startDeviceWatch(uid: uid)
        }
        guard confirmed else {
            return false
        }
        pendingDeviceBegin = false
        // Une nouvelle connexion : l'ancien message « autre téléphone », s'il n'avait pas encore été montré, n'a plus lieu d'être.
        flags.set(false, forKey: Self.keyReplacedNoticePending)
        return true
    }

    /// Lancement et « Réessayer » avec un compte connecté : vrai = l'app peut lire le compte. Faux = la suite est déjà réglée (écran
    /// « Réessayer » ou déconnexion forcée).
    func prepareDeviceSession(uid: String) async -> Bool {
        // Une connexion commencée puis interrompue (processus tué, réseau coupé) laisse un candidat sur disque : elle est reprise.
        if device.hasPendingBegin {
            pendingDeviceBegin = true
        }
        if pendingDeviceBegin {
            return await resumeDeviceBegin(uid: uid)
        }
        // Hors ligne (le système le dit), on n'attend aucun délai : on avance, et la vérification se fait au retour du réseau.
        // Un appareil déjà révoqué (déconnexion forcée interrompue) est traité tout de suite, réseau ou pas.
        var verdict = SessionCheck.unverified
        if network.isOnline() || device.isRevoked {
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

    private func resumeDeviceBegin(uid: String) async -> Bool {
        var done = false
        if network.isOnline() {
            done = await beginDeviceSession(uid: uid)
        } else if deviceWatchers.isEmpty {
            startDeviceWatch(uid: uid)
        }
        if !done, gateway.currentUid == uid, !ending {
            setState(.lookupFailed)
        }
        return done
    }

    // MARK: - Écoute

    func startDeviceWatch(uid: String) {
        stopDeviceWatch()
        let changes = device.changes(uid: uid)
        let online = network.onlineChanges()
        let sessionWatch = Task { [weak self] in
            _ = await self?.watchDeviceSession(uid: uid, firstStream: changes)
        }
        let networkWatch = Task { [weak self] in
            var isFirst = true
            for await isOnline in online {
                // La première valeur est l'état du moment, pas un retour du réseau.
                if isFirst {
                    isFirst = false
                    continue
                }
                if isOnline {
                    await self?.onNetworkReturned(uid: uid)
                }
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

    /// Juge les instantanés du serveur. Si l'écoute se termine SANS qu'on l'ait demandée (erreur du serveur), plus rien ne garantit
    /// la session : elle n'est plus confirmée (aucune écriture), l'écoute est relancée après une pause (bornée par
    /// `deviceWatchRestartPauses`) et la session est relue.
    private func watchDeviceSession(uid: String, firstStream: AsyncStream<RemoteSession>) async {
        var stream = firstStream
        var restarts = 0
        while !Task.isCancelled {
            for await remote in stream {
                _ = await reviewDeviceSession(uid: uid, incoming: remote)
            }
            if Task.isCancelled {
                return
            }
            device.markUnverified()
            guard restarts < deviceWatchRestartPauses.count else {
                return
            }
            let pause = deviceWatchRestartPauses[restarts]
            restarts += 1
            try? await Task.sleep(nanoseconds: pause)
            guard !Task.isCancelled, canReviewDeviceSession(uid: uid) else {
                return
            }
            stream = device.changes(uid: uid)
            _ = await reviewDeviceSession(uid: uid, incoming: nil)
        }
    }

    /// Retour du réseau : la session est relue (une lecture par retour), confirmée ou non, AVANT toute écriture. Une connexion qui
    /// n'avait pas été confirmée est reprise ; si l'écran « Réessayer » attendait, l'app avance.
    private func onNetworkReturned(uid: String) async {
        let wasPending = pendingDeviceBegin || device.hasPendingBegin
        _ = await reviewDeviceSession(uid: uid, incoming: nil)
        guard device.currentDeviceId != nil else {
            return
        }
        pendingDeviceBegin = false
        if wasPending, state == .lookupFailed, canReviewDeviceSession(uid: uid) {
            await resolveCurrent()
        }
    }

    // MARK: - Vérification

    func canReviewDeviceSession(uid: String) -> Bool {
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
    /// serveur ne confirme pas, `pendingDeviceBegin` reste vrai et le candidat reste sur disque : l'écoute et le suivi du réseau
    /// tournent déjà, « Réessayer » ou le retour du réseau la réécrit, et aucun statut ne part d'ici là.
    func restoreDeviceSession() async {
        guard let uid = gateway.currentUid else {
            return
        }
        _ = await beginDeviceSession(uid: uid)
    }

    // MARK: - Fin

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

    /// Avant la suppression d'un COMPTE (panneau de consentement refusé, AUTH-5 plus tard) : l'écoute s'arrête, puis la session est
    /// supprimée. Vrai SEULEMENT si le serveur a confirmé (ou s'il n'y avait rien à supprimer) : sinon la suppression du compte NE
    /// DOIT PAS continuer (la session orpheline ne pourrait plus être supprimée une fois le compte parti) ; l'appelant abandonne et
    /// appelle `resumeDeviceWatch` (la procédure reste reprenable). L'état local n'est effacé qu'en cas de succès.
    func deleteDeviceSessionBeforeAccountRemoval(uid: String?) async -> Bool {
        stopDeviceWatch()
        guard let uid else {
            return true
        }
        let deleted = await device.deleteSession(uid: uid, online: network.isOnline())
        if deleted {
            device.clearLocal()
        }
        return deleted
    }

    /// La suppression du compte est abandonnée : la session est toujours celle de cet appareil, l'écoute reprend.
    func resumeDeviceWatch() {
        guard let uid = gateway.currentUid, deviceWatchers.isEmpty else {
            return
        }
        startDeviceWatch(uid: uid)
    }
}
