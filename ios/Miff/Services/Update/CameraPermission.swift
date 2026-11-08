import AVFoundation
import Foundation
import UIKit

// CONFIDENTIALITÉ, pour ce fichier : il LIT l'état de l'autorisation de la caméra et peut afficher la fenêtre du système APRÈS un toucher
// de la personne sur un bouton qui dit pourquoi (« Continuer » du pop-in d'explication). Il n'ouvre jamais la caméra lui-même et ne lit
// aucune position (critère P1 : jamais de demande à froid, aucun déclenchement à distance).

/// L'autorisation de la caméra et ce que l'écran peut lui demander (un double dans les tests).
protocol CameraPermissionProviding {
    func status() -> CameraAuthorization

    /// La fenêtre du système (affichée UNE seule fois par iOS) ; rend la main quand la personne a répondu. Vrai si la caméra est autorisée.
    func request() async -> Bool

    /// Les Réglages de Miff dans le système (l'autorisation de la caméra s'y change).
    func openSettings()
}

struct SystemCameraPermission: CameraPermissionProviding {
    func status() -> CameraAuthorization {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            return .authorized
        case .notDetermined:
            return .notDetermined
        case .restricted:
            return .restricted
        case .denied:
            return .denied
        @unknown default:
            return .denied
        }
    }

    func request() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .video)
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        DispatchQueue.main.async {
            UIApplication.shared.open(url)
        }
    }
}
