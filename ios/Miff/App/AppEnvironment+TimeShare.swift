import Foundation

extension AppEnvironment {
    /// Le view model de la carte « Répartition de ton temps » (TIME-4), créé avec la session de l'hôte du Profil. Il lit le journal LOCAL des
    /// changements de présence (la même instance que le moteur de suivi) et ce que le téléphone dit de l'état du suivi : aucune lecture
    /// Firestore, aucune écriture, aucun événement.
    func makeTimeShareViewModel() -> TimeShareViewModel {
        let engine = tracking.engine
        let status = update.myStatus
        let directory = miffDirectory
        let local = TrackingLocalFileStore()
        let context = LiveTimeShareContext(
            permissions: { [unowned self] in self.devicePermissions },
            travelActive: { engine.travelPeriod() != nil },
            // Le Feed ne dit « partage arrêté » que pour la Miff qu'il affiche (la Miff active) : pour une autre Miff, rien n'est affirmé.
            sharingStopped: { miffId in
                guard case .ready(let ready) = directory.state else {
                    return false
                }
                return ready.activeId == miffId && status.sharingStopped
            },
            zoneCount: { LiveTimeShareContext.zoneCount(of: local) }
        )
        return TimeShareViewModel(history: presenceHistory, context: context)
    }
}
