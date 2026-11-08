import Foundation

/// Les services de la suppression du compte (AUTH-5c), construits à partir de ce que l'environnement détient déjà. Rien ici n'ouvre
/// Firestore : tout passe par des fonctions appelées à la première requête, depuis des écrans affichés APRÈS le routage de la session.
@MainActor
struct AccountDeletionServices {
    /// La procédure : l'écran d'information la lit (lecture auprès du serveur), le contrôleur la fait tourner.
    let procedure: AccountDeletionProcedure
    /// Fait tourner la procédure aussi longtemps que l'app vit ; reprend au lancement à partir du marqueur local.
    let controller: AccountDeletionController
    /// Retire l'autorisation « Se connecter avec Apple » (règle 5.1.1(v) de l'App Store), via le SDK Firebase, sans serveur à nous.
    let revoker: AppleTokenRevoker

    /// Les réglages gardés pour un compte, effacés avec lui (voir `AccountLocalData`).
    static func localData(updateLedger: PublishLedger) -> [LocalDataEraser] {
        AccountLocalData.erasers(
            AccountLocalStores(
                notificationAsks: UserDefaultsNotificationAskStore(),
                pendingMiffDeletions: PendingDeletionFileStore(),
                activeMiff: UserDefaultsActiveMiffStore(),
                notifications: SystemLocalNotificationsCleaner(),
                trackingLocal: TrackingLocalFileStore(),
                detectionJournal: DetectionJournals.make(),
                updateLedger: updateLedger
            )
        )
    }

    /// Le marqueur est un fichier : la session (qui le lit au lancement) et le contrôleur (qui l'écrit) en ouvrent chacun un, sur le même
    /// fichier.
    static func make(_ environment: AppEnvironment) -> AccountDeletionServices {
        let session = environment.session
        let miffStore = FirestoreMiffStore()
        let procedure = AccountDeletion(
            store: FirestoreAccountDataStore(),
            gateway: session.gateway,
            cache: session.cache,
            session: DeviceAccountSession(device: session.device, network: environment.network),
            miffs: StoreAccountMiffs(
                store: miffStore,
                deleter: MiffDeletion(store: miffStore, repository: environment.miffRepository)
            )
        )
        let controller = AccountDeletionController(
            procedure: procedure,
            marker: AccountDeletionFileMarker(),
            session: session,
            network: environment.network,
            currentUid: { session.currentUid }
        )
        return AccountDeletionServices(
            procedure: procedure, controller: controller, revoker: FirebaseAppleTokenRevoker()
        )
    }
}

extension AppEnvironment {
    /// L'écran d'information (A) : lit les Miffs du compte auprès du serveur.
    func makeDeleteInfoViewModel() -> DeleteAccountInfoViewModel {
        let session = self.session
        return DeleteAccountInfoViewModel(
            procedure: accountDeletion.procedure,
            currentUid: { session.currentUid },
            network: network
        )
    }

    /// La ré-authentification (C), ou « Dernière vérification » (`last`) : à la réussite, la suppression commence (première fois) ou
    /// reprend sa dernière étape (seconde fois).
    func makeDeleteReauthViewModel(last: Bool) -> DeleteReauthViewModel {
        let controller = accountDeletion.controller
        let info = accountInfo
        let dependencies = DeleteReauthDependencies(
            gateway: session.gateway,
            email: { info.currentEmail() },
            network: network,
            social: socialSource,
            revoker: accountDeletion.revoker
        )
        return DeleteReauthViewModel(
            dependencies: dependencies,
            last: last,
            onVerified: { apple in
                last ? controller.reauthenticated(apple: apple) : controller.start(apple: apple)
            }
        )
    }
}
