import Foundation
import UIKit

// CONFIDENTIALITÉ, pour ce fichier : il lit ce que le téléphone dit de LUI-MÊME (le compte connecté, l'état des autorisations, la
// batterie, le réseau, deux réglages du système). Il ne lit AUCUNE position et n'écrit rien.

/// Ce que le moteur demande au téléphone. La batterie est celle de l'appareil, écrite avec le statut (pas d'écriture périodique
/// dédiée, pour limiter le coût) ; les deux réglages du système ne servent qu'au journal de la recette.
struct SystemTrackingEnvironment: TrackingEnvironment {
    let currentUid: () -> String?
    /// L'état des autorisations (`SystemDevicePermissions.snapshot`, qui ne lit aucune position).
    let readPermissions: () async -> PermissionSnapshot
    let network: NetworkMonitor

    func uid() -> String? {
        currentUid()
    }

    func permissions() async -> PermissionSnapshot {
        await readPermissions()
    }

    func batteryPct() async -> Int? {
        await MainActor.run { Self.readBattery() }
    }

    func isOnline() -> Bool {
        network.isOnline()
    }

    func nowMillis() -> Int {
        Int(Date().timeIntervalSince1970 * 1000)
    }

    func systemConditions() async -> SystemConditions {
        let refreshOff = await MainActor.run { UIApplication.shared.backgroundRefreshStatus != .available }
        return SystemConditions(
            backgroundRefreshOff: refreshOff,
            lowPowerMode: ProcessInfo.processInfo.isLowPowerModeEnabled
        )
    }

    /// Le niveau de la batterie de 0 à 100, ou nil quand l'appareil ne le dit pas (simulateur, ou juste après le premier allumage du
    /// suivi : le système met un instant à le donner).
    @MainActor
    private static func readBattery() -> Int? {
        let device = UIDevice.current
        device.isBatteryMonitoringEnabled = true
        let level = device.batteryLevel
        guard level >= 0 else {
            return nil
        }
        return min(100, max(0, Int((level * 100).rounded())))
    }
}
