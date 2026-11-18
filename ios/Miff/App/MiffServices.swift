import Foundation

/// Les services des Miffs (MIFF-3), construits d'un seul tenant. Rien ici n'ouvre Firestore : tout passe par des fonctions
/// appelées à la première requête, depuis des écrans affichés APRÈS le routage de la session (démarrage à froid : aucun accès
/// Firestore avant la session).
@MainActor
struct MiffServices {
    /// Tout ce que font les écrans « Créer », « Rejoindre » et « Miff » avec Firestore.
    let repository: MiffRepository
    /// Les Miffs en mémoire pour la session, vidées à la déconnexion (elles font partie des arrêts de la session).
    let directory: MiffDirectory
    /// Le limiteur d'essais du code (aide de l'interface, critère J5) : gardé pour la durée du processus, pour que quitter
    /// l'écran puis y revenir ne remette pas le compte à zéro.
    let limiter: JoinAttemptLimiter
    /// La suppression d'une Miff, sans écran (MIFF-4) : utilisée par l'écran de la Miff, reprise au lancement, et plus tard par
    /// AUTH-5 (suppression d'un compte) et le ticket Premium (fin d'un abonnement).
    let deletions: MiffDeletionCoordinator
    /// Le renouvellement du consentement des membres qui partagent sous un texte plus ancien (LOC-4a, règle R-LIEU-3).
    let renewals: ConsentRenewals
    /// FEED-5 : les Miffs qui attendent l'accord de la personne (« Pas maintenant » au renouvellement), dites par le Feed.
    let awaiting = AwaitingAgreement()

    static func make(
        gateway: AuthGateway,
        cache: CachePurger,
        network: NetworkMonitor,
        device: DeviceSession,
        onSharingChanged: @escaping () -> Void,
        onSharingResumed: ((String) -> Void)? = nil,
        beforeLeave: @escaping (_ uid: String, _ miffId: String) async -> Void = { _, _ in },
        onAlertGone: @escaping (_ miffId: String) -> Void = { _ in }
    ) -> MiffServices {
        let store = FirestoreMiffStore()
        // SESS-3 : un statut n'est écrit qu'avec l'identifiant de la session CONFIRMÉE de cet appareil ; après un refus, la session
        // est relue auprès du serveur.
        var rules = MiffRepositoryRules.live(
            deviceId: { device.currentDeviceId },
            recheckSession: {
                if let uid = gateway.currentUid {
                    _ = await device.checkAfterRefusal(uid: uid)
                }
            }
        )
        // LOC-4b : un partage qui change (entrer, arrêter, reprendre, quitter, perdre l'accès) fait relire les Miffs au suivi des lieux.
        rules.onSharingChanged = onSharingChanged
        rules.onSharingResumed = onSharingResumed
        // Sans réseau, l'annuaire des Miffs se lit dans le cache du téléphone tout de suite (pas de délais de 20 s mis bout à bout).
        rules.isOnline = { network.isOnline() }
        // LIEU-REQ (critère L1) : « Quitter » reprend d'abord les demandes d'ajout d'un lieu de la personne dans cette Miff.
        rules.beforeLeave = beforeLeave
        // ALERTE « Je suis en danger » : les lots d'arrêt du partage, de départ, de retrait et de suppression suppriment aussi l'alerte de la Miff
        // (seulement quand le build contient la fonction : les règles en production ne connaissent pas encore ce chemin).
        rules.alertsEnabled = BuildFlavor.includesAlertDanger
        rules.onAlertGone = onAlertGone
        let repository = FirestoreMiffRepository(
            store: store,
            observer: FirestoreMiffObserver(),
            location: SystemLocationAccessProbe(),
            cache: cache,
            rules: rules
        )
        let directory = MiffDirectory(
            repository: repository,
            currentUid: { gateway.currentUid },
            activeStore: UserDefaultsActiveMiffStore(),
            cache: cache
        )
        // Avant la première écriture, la suppression prévient l'annuaire (jamais de « tu n'as plus accès » pour la Miff qu'on
        // supprime) ; une fois la Miff disparue, purge du cache et liste relue.
        let hooks = DeletionHooks(
            onStarted: { miffId in directory.expectDeparture(miffId: miffId) },
            onDeleted: { miffId in
                await directory.afterOwnDeparture(miffId: miffId)
                onSharingChanged()
            }
        )
        let deletions = MiffDeletionCoordinator(
            deletion: MiffDeletion(
                store: store, repository: repository, alertsEnabled: BuildFlavor.includesAlertDanger
            ),
            pending: PendingDeletionFileStore(),
            network: network,
            currentUid: { gateway.currentUid },
            hooks: hooks
        )
        deletions.background = UIKitBackgroundExecution()
        return MiffServices(
            repository: repository,
            directory: directory,
            limiter: JoinAttemptLimiter(nowMillis: AppClock.live.nowMillis),
            deletions: deletions,
            renewals: FirestoreConsentRenewals(store: store, onRenewed: onSharingChanged)
        )
    }
}

extension MiffServices {
    /// Fin de session : l'annuaire oublie les Miffs, la suppression en cours est arrêtée, plus personne n'attend d'accord.
    func stop() async {
        await directory.stop()
        await deletions.stop()
        await awaiting.stop()
    }
}
