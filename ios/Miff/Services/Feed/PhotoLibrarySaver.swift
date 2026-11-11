import Foundation
import Photos

// CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier (FEED-8, décision du PM du 2026-11-11 : « Enregistrer cette photo » écrit DIRECTEMENT dans la galerie).
// C'est LE SEUL fichier de l'app qui importe `Photos`, et il ne fait que ceci :
// - AJOUT SEUL : l'autorisation est `.addOnly` (`authorizationStatus(for: .addOnly)`, `requestAuthorization(for: .addOnly)`) ; l'app ne lit JAMAIS la
//   galerie (aucune requête `PHAsset.fetch`, aucun sélecteur, aucune clé `NSPhotoLibraryUsageDescription`) ;
// - AU TOUCHER SEULEMENT : `save` n'est appelé que par `PhotoSaveViewModel.save()`, lui-même lancé par le toucher de l'icône ; aucune écriture
//   automatique, jamais en arrière-plan ;
// - SANS LIEU : l'asset est créé à partir des seuls octets JPEG ré-encodés (`PhotoExport`) ; `location` n'est JAMAIS posé sur la demande
//   (aucune coordonnée dans la galerie). Aucun fichier de l'app, aucun journal, aucun événement.

/// Où en est l'autorisation d'AJOUT à la galerie.
enum PhotoSaveAccess: Equatable {
    case notDetermined
    case allowed
    /// Refusée par la personne : seuls les Réglages y changent quelque chose.
    case denied
    /// Interdite par l'appareil (contrôle parental, gestion d'entreprise) : la personne ne peut pas la changer.
    case restricted
}

@MainActor
protocol PhotoSaving {
    /// L'état actuel de l'autorisation d'ajout, SANS rien demander.
    func access() -> PhotoSaveAccess
    /// La demande du système (une boîte, la première fois seulement).
    func requestAccess() async -> PhotoSaveAccess
    /// Ajoute UNE photo JPEG à la galerie. Faux quand le système n'a pas pu l'écrire.
    func save(jpeg: Data) async -> Bool
}

struct PhotosLibrarySaver: PhotoSaving {
    func access() -> PhotoSaveAccess {
        Self.access(for: PHPhotoLibrary.authorizationStatus(for: .addOnly))
    }

    func requestAccess() async -> PhotoSaveAccess {
        Self.access(for: await PHPhotoLibrary.requestAuthorization(for: .addOnly))
    }

    func save(jpeg: Data) async -> Bool {
        do {
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                request.addResource(with: .photo, data: jpeg, options: nil)
            }
            return true
        } catch {
            return false
        }
    }

    /// `limited` n'existe pas en ajout seul ; s'il arrivait, ajouter reste permis.
    static func access(for status: PHAuthorizationStatus) -> PhotoSaveAccess {
        switch status {
        case .authorized, .limited:
            return .allowed
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
}
