import Foundation

/// Les services de l'Update (UPDATE-3), construits d'un seul tenant au démarrage. Rien ici n'ouvre la caméra, ne lit une position ni
/// n'ouvre Firestore : tout passe par des fonctions appelées par l'écran Update (la caméra seulement quand il est à l'écran, la position
/// seulement après « Publier ») et par le lancement de l'app (le nettoyage de la photo expirée, sans lecture tant que la date locale existe).
@MainActor
struct UpdateServices {
    /// Ce que le téléphone retient : le garde-fou des publications, la date de la photo envoyée, l'astuce du double toucher.
    let ledger: PublishLedger
    let publisher: UpdatePublisher
    let cleaner: PhotoCleaner
    /// MON statut dans la Miff affichée : la couleur du bouton central, l'anneau de ma photo, la bulle d'incitation.
    let myStatus: MyStatusStore
    let permission: CameraPermissionProviding
    let sharingStopped: () async -> Bool
    /// FEED-8 : les photos lues par le Feed, EN MÉMOIRE seulement ; ma photo publiée y est posée à la publication (ma carte ne la relit pas).
    let photoCache: PhotoCache

    static func make(
        gateway: AuthGateway,
        network: NetworkMonitor,
        device: DeviceSession,
        tracking: TrackingServices,
        miff: MiffServices
    ) -> UpdateServices {
        let directory = miff.directory
        let ledger = FilePublishLedger()
        let photoStore = FirestorePhotoStore()
        let permissions = SystemDevicePermissions()
        let system = SystemTrackingEnvironment(
            currentUid: { gateway.currentUid },
            readPermissions: { await permissions.snapshot() },
            network: network
        )
        let environment = UpdateEnvironment(
            currentUid: { gateway.currentUid },
            miffIds: { Self.miffIds(of: directory.state) },
            battery: { await system.batteryPct() }
        )
        let local = TrackingLocalFileStore()
        // Repli quand la mesure fraîche échoue : la dernière position que le suivi a gardée pour CE compte, de moins de 10 minutes.
        let keptFix: () -> Fix? = {
            Self.keptFix(uid: gateway.currentUid, local: local, nowMillis: Int(Date().timeIntervalSince1970 * 1000))
        }
        let photoPosition = TrackingPhotoPositionSource(
            position: tracking.location, permissions: { await permissions.snapshot() }, keptFix: keptFix
        )
        var publisherDependencies = UpdatePublisherDependencies(
            store: photoStore,
            device: device,
            position: photoPosition,
            network: network,
            ledger: ledger,
            environment: environment
        )
        // Contrat du modèle de données : une photo n'est écrite que là où MA position est publiée (lue sur la copie locale du suivi).
        publisherDependencies.isPositionPublished = { miffId in
            guard let uid = gateway.currentUid, let state = local.load(), state.uid == uid else {
                return false
            }
            return state.miffs[miffId]?.positionAtMillis != nil
        }
        // Aucune Miff n'a encore ma position : la détection est invitée à l'écrire.
        publisherDependencies.onPositionMissing = tracking.sharingChanged
        let photoCache = PhotoCache()
        publisherDependencies.onPhotoPublished = { photoCache.putOwn($0) }
        var cleanerDependencies = PhotoCleanerDependencies(
            store: photoStore, device: device, network: network, ledger: ledger, environment: environment
        )
        cleanerDependencies.reader = photoStore
        let myStatus = MyStatusStore()
        // D36 : « partage arrêté » n'est connu d'avance qu'avec UNE seule Miff ; c'est le Feed qui le dit (aucune lecture de plus).
        let sharingStopped: () async -> Bool = {
            myStatus.sharingStopped && Self.miffIds(of: directory.state).count == 1
        }
        return UpdateServices(
            ledger: ledger,
            publisher: UpdatePublisher(publisherDependencies),
            cleaner: PhotoCleaner(cleanerDependencies),
            myStatus: myStatus,
            permission: SystemCameraPermission(),
            sharingStopped: sharingStopped,
            photoCache: photoCache
        )
    }

    /// La dernière position que le suivi a gardée sur le téléphone, seulement si elle est celle de CE compte et a moins de 10 minutes (la
    /// copie d'un autre compte ou une vieille position ne sert jamais de repli).
    static func keptFix(uid: String?, local: TrackingLocalStore, nowMillis: Int) -> Fix? {
        guard let uid, let state = local.load(), state.uid == uid, let fix = state.fix,
              nowMillis - fix.measuredAtMillis <= TrackingLimits.fixMaxAgeMillis else {
            return nil
        }
        return fix
    }

    /// Les identifiants des Miffs de la personne, tels que l'annuaire les connaît (vide tant qu'il ne les a pas lues).
    static func miffIds(of state: DirectoryState) -> [String] {
        if case .ready(let ready) = state {
            return ready.miffs.map { $0.id }
        }
        return []
    }

    /// Le view model de l'écran Update, créé pour la session.
    func makeViewModel() -> UpdateViewModel {
        UpdateViewModel(
            UpdateViewModelDependencies(
                publisher: publisher,
                cleaner: cleaner,
                ledger: ledger,
                permission: permission,
                sharingStopped: sharingStopped
            )
        )
    }

    /// Fin de session (déconnexion) : le garde-fou et ce qui était suivi partent ; la date de la photo envoyée RESTE (la photo reste en base,
    /// le même compte qui se reconnecte doit pouvoir la nettoyer).
    func stop() {
        ledger.clear()
        myStatus.stop()
        // Les photos lues (et la mienne) ne restent pas en mémoire pour la personne suivante.
        photoCache.clear()
    }

    /// Suppression du compte `uid` (AUTH-5) : la date de la photo de CE compte est effacée avec lui. À appeler par la suppression de compte.
    func eraseAccountData(uid: String) {
        ledger.eraseAccountData(uid: uid)
    }
}
