#if ALERT_DANGER
import Foundation

/// Les services de l'alerte « Je suis en danger » (docs/tickets/alerte-danger.md ; AD-I1 et AD-I2), construits d'un seul tenant. Rien ici n'ouvre Firestore avant
/// qu'un écran ne le demande (le Feed écoute les alertes de la Miff affichée, l'envoi ne part qu'aux 5 secondes). Tout ce fichier n'existe que dans les builds
/// avec la condition de compilation `ALERT_DANGER` (Debug et Internal, pas Release).
@MainActor
struct DangerAlertServices {
    let controller: DangerAlertController
    let model: DangerAlertViewModel
    let signal: DangerAlertSignal
    private let local: DangerAlertLocalStore

    static func make(
        gateway: AuthGateway,
        device: DeviceSession,
        network: NetworkMonitor,
        tracking: TrackingServices
    ) -> DangerAlertServices {
        let store = FirestoreDangerAlertStore()
        let local = DangerAlertFileStore()
        let signal = DangerAlertSignal()
        let controller = DangerAlertController(
            store: store,
            device: device,
            network: network,
            local: local,
            currentUid: { gateway.currentUid },
            // UNE mesure de la position à l'envoi, par le suivi existant (ses garde-fous et sa limite de fréquence), jamais attendue.
            onSending: tracking.sharingChanged
        )
        let model = DangerAlertViewModel(
            controller: controller,
            local: local,
            members: FirestoreDangerAlertMembersReader(),
            myUid: { gateway.currentUid },
            region: { Locale.current.region?.identifier },
            signal: signal
        )
        return DangerAlertServices(controller: controller, model: model, signal: signal, local: local)
    }

    /// Fin de session : rien de mon alerte ne reste en mémoire ni sur le téléphone pour le compte suivant (la bulle bleue, qui concerne
    /// l'installation, reste).
    var teardowns: [SessionTeardown] {
        let controller = self.controller
        let local = self.local
        return [
            {
                controller.onSessionEnded()
                local.clearAccount()
            }
        ]
    }

    /// Ce que la suppression d'un compte efface du téléphone pour ce compte.
    var erasers: [LocalDataEraser] {
        let local = self.local
        return [{ _ in local.clearAccount() }]
    }
}
#endif
