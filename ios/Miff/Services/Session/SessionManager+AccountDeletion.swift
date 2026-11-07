import Foundation

// La suppression d'un COMPTE vue de la session (AUTH-5c, miroir d'Android `SessionManager`). Ticket `docs/tickets/suppression-compte.md`
// (§5.5, §5.6, §5.10), décision du PM du 2026-11-06 sur l'ordre de la session, `docs/tickets/session-appareil-unique.md` (SES4).
//
// Règles tenues ici, chacune couverte par un test :
// - un marqueur local « suppression en cours » existe AVANT la première écriture de la procédure, et il a PRIORITÉ sur le routage du
//   lancement (un profil déjà supprimé n'envoie jamais la personne vers « crée ton profil ») ;
// - dès le début, l'appareil est FERMÉ (marqueur persistant de `DeviceSession`) : aucune vérification ne réécrit de session, aucun
//   statut ni position ne part, même si un réveil par une zone relance un processus neuf au milieu de la suppression ;
// - tout ce qui tourne s'arrête avant la première écriture (écoutes, suivi des lieux, statuts) : sans cela, les écoutes recevraient des
//   refus au fil des suppressions et annonceraient à tort « accès perdu » ;
// - à la fin : cache purgé, réglages du compte effacés, brouillons, consentement en attente, collecte coupée, régions désarmées (par
//   l'arrêt du suivi), session locale fermée ; le marqueur part EN DERNIER (tuée avant, l'app refait le même nettoyage au lancement) ;
// - seul « utilisateur introuvable » prouve que le compte n'existe plus ; une session fermée, un jeton expiré ou révoqué ne sont jamais
//   pris pour « supprimé ».
extension SessionManager: AccountDeletionSession {
    /// Étape 0 : à partir d'ici l'app montre les écrans de la suppression, et tout ce qui tourne s'arrête, AVANT le premier document.
    /// Idempotent : aussi appelée quand une suppression est reprise.
    func beginAccountDeletion() async {
        // Notre propre fin de session : le guetteur de « session perdue » ne doit pas y réagir.
        ending = true
        sessionActive = true
        // L'appareil est fermé TOUT DE SUITE, marqueur sur le disque : notre propre suppression de la session (étape 2 bis) ne doit jamais
        // ressembler à un remplacement, et rien n'est vérifié ni écrit d'ici là.
        device.closeForAccountDeletion()
        stopDeviceWatch()
        pendingDeviceBegin = false
        setState(.ready(.accountDeletion))
        await stopEverything()
    }

    /// Étape 9 : cache purgé (le drapeau est posé d'abord, pour qu'une app tuée ici purge au lancement suivant), réglages du compte
    /// effacés, brouillon du profil, consentement en attente, collecte coupée et installation oubliée, session locale fermée. Le marqueur
    /// part EN DERNIER. La langue et le fait d'avoir vu le carrousel sont gardés (rien de personnel).
    func eraseAfterAccountDeletion(uid: String) async {
        ending = true
        cache.markPurgePending()
        await stopEverything()
        _ = await cache.purge()
        // Chaque effacement est tenté : un réglage qui ne part pas ne retient jamais les autres.
        for erase in localData {
            await erase(uid)
        }
        drafts?.clear()
        pending.clear()
        privacy.eraseCollectedData()
        setSnapshot(nil)
        bumpGeneration()
        // L'identifiant du compte supprimé est oublié (sa session a été supprimée à l'étape 2 bis) ; l'appareil reste fermé.
        device.forget()
        flags.set(false, forKey: Self.keyReplacedNoticePending)
        flags.set(false, forKey: Self.keyGoneNoticePending)
        gateway.signOut()
        deletionMarker?.clear()
    }

    /// Des Miffs ont continué d'apparaître pendant la suppression (un autre téléphone du compte en créait ou en rejoignait) : elle est
    /// ANNULÉE. Le profil et le compte ne sont jamais supprimés avec une Miff derrière eux. L'app repart
    /// (compte relu, routage habituel, session de l'appareil réécrite, suivi relancé) et le dit UNE fois, calmement.
    func cancelAccountDeletion() async {
        // La session supprimée à l'étape 1 est réécrite : cet appareil redevient l'appareil actif (`begin` lève le marqueur « fermé »).
        // `ending` reste vrai tant que c'est fait : aucune vérification en file ne peut lancer une déconnexion forcée entre-temps.
        await restoreDeviceSession()
        // Le marqueur part APRÈS la réouverture de l'appareil : tué entre les deux, l'app reprend la suppression (qui s'annule de
        // nouveau) au lieu de laisser un appareil fermé sans marqueur de suppression.
        deletionMarker?.clear()
        bumpGeneration()
        ending = false
        sessionActive = true
        showDeletionBlockedNotice()
        await resolveCurrent()
    }

    /// La session ne peut plus servir (fermée dans notre dos, jeton expiré ou révoqué) : cache purgé, retour à l'écran de connexion avec le
    /// marqueur GARDÉ et une phrase calme. Se reconnecter avec le même compte reprend la suppression.
    func endSessionKeepingDeletionMarker() async {
        ending = true
        cache.markPurgePending()
        await stopEverything()
        _ = await cache.purge()
        // Pas « compte indisponible » : l'écran de connexion dit que la suppression sera terminée une fois la personne revenue.
        finishSignedOut(destination: .signIn, notice: .deletionPending, markReturning: true)
    }

    /// « Continuer » sur l'écran de fin (et le cas d'un lancement après coup) : retour au carrousel de bienvenue, puis inscription ou
    /// connexion.
    func leaveAfterAccountDeletion() {
        sessionActive = false
        carouselAfterDeletion = true
        setState(.ready(.signUp))
        ending = false
    }

    /// Lancement avec un marqueur de suppression. Vrai quand il a décidé où s'ouvre l'app.
    /// - même compte que la session : la suppression reprend (les écrans la relancent, sans nouvelle ré-authentification) ;
    /// - plus de session : si Firebase avait CONFIRMÉ la suppression, c'est un SUCCÈS (nettoyage, carrousel, bannière) ; sinon la session a
    ///   été fermée dans notre dos et le marqueur reste (jamais « supprimé » sans preuve) ;
    /// - la session d'UN AUTRE compte : le marqueur n'est pas le sien, il est ignoré et effacé.
    func resolveAccountDeletion(uid: String?) async -> Bool {
        guard let marker = deletionMarker, let mark = marker.read() else {
            return false
        }
        if let uid, uid != mark.uid {
            marker.clear()
            return false
        }
        guard uid != nil else {
            if mark.accountDeleted {
                await finishDeletionBehindTheScreen(uid: mark.uid)
            } else {
                await endSessionKeepingDeletionMarker()
            }
            return true
        }
        sessionActive = true
        await beginAccountDeletion()
        return true
    }

    /// La suppression est finie mais l'écran de fin n'a jamais été vu : ce qui reste est effacé, puis le carrousel et sa bannière.
    private func finishDeletionBehindTheScreen(uid: String) async {
        await eraseAfterAccountDeletion(uid: uid)
        setNotice(.accountDeleted)
        leaveAfterAccountDeletion()
    }
}
