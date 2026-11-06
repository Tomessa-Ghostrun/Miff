import Foundation

/// Les services du suivi des lieux (LOC-4b), construits d'un seul tenant au démarrage de l'app. Contrairement aux autres services, le
/// gestionnaire de localisation est créé TOUT DE SUITE (et non au premier écran qui en a besoin) : quand le système relance l'app
/// terminée pour un événement de zone ou un déplacement significatif, le gestionnaire et son délégué doivent exister dès le démarrage
/// pour que l'événement en attente soit livré. Rien ici ne lit une position, n'arme une zone ni n'ouvre Firestore : le moteur ne le
/// fait qu'une fois l'accord, « Toujours » et la position exacte vérifiés (critère AUT7).
@MainActor
struct TrackingServices {
    let runtime: TrackingRuntime
    /// Le journal local de la détection : un fichier dans les builds Debug et Internal, rien en publication (critère VIE2).
    let journal: DetectionJournal
    /// Copie de `SessionManager.generation` pour le moteur (un arrêt reste arrêté jusqu'à ce que la session change).
    let generation: GenerationCounter
    /// Gardé ici pour que le gestionnaire de localisation vive aussi longtemps que l'app.
    let location: CoreLocationTracking

    static func make(
        gateway: AuthGateway,
        cache: CachePurger,
        network: NetworkMonitor,
        device: DeviceSession
    ) -> TrackingServices {
        let generation = GenerationCounter()
        let journal = DetectionJournals.make()
        let miffStore = FirestoreMiffStore()
        let permissions = SystemDevicePermissions()
        let location = CoreLocationTracking()
        let dependencies = TrackingDependencies(
            env: SystemTrackingEnvironment(
                currentUid: { gateway.currentUid },
                readPermissions: { await permissions.snapshot() },
                network: network
            ),
            store: FirestoreTrackingStore(base: miffStore, placeStore: FirestorePlaceStore(base: miffStore)),
            local: TrackingLocalFileStore(),
            position: location,
            geofences: location,
            movement: location,
            device: device,
            journal: journal,
            generation: { generation.current }
        )
        let runtime = TrackingRuntime(
            engine: TrackingEngine(dependencies),
            background: UIKitBackgroundExecution(name: "miff-tracking"),
            journal: journal,
            // Une purge du cache laissée par une déconnexion qui avait échoué passe avant tout autre accès à Firestore.
            beforeWork: { await cache.purgeIfPending() }
        )
        location.attach(runtime)
        runtime.start(network: network)
        return TrackingServices(runtime: runtime, journal: journal, generation: generation, location: location)
    }
}
