import Foundation

// CONFIDENTIALITÉ, pour ce fichier : la position qui accompagne une photo (décision du PM du 2026-11-06). Elle est demandée au pont du
// suivi (`PositionSource`, le SEUL qui lit une position : `PermissionPrivacyTests`) ET SEULEMENT après le toucher de « Publier » (décision D33 :
// personne n'est localisé pour une photo jamais publiée). Le `Fix` rendu est gardé en mémoire par l'appelant jusqu'à l'envoi du lot ;
// rien ici ne le journalise, ne l'envoie comme événement ni ne le garde. Ce fichier n'importe pas CoreLocation.

/// Ce que le téléphone a dit quand une position a été demandée pour une photo : une position, ou POURQUOI il n'y en a pas (la phrase
/// calme n'est pas la même).
enum PhotoPosition: Equatable, CustomStringConvertible {
    case found(Fix)
    /// L'autorisation de la position a été retirée (ou est devenue approximative), ou la localisation du téléphone est éteinte : seule la
    /// personne peut y changer quelque chose (les Réglages).
    case locationOff
    /// Le téléphone ne peut pas répondre maintenant (pas de signal, lent) : « réessaie dans un instant ».
    case notFound

    var description: String {
        switch self {
        case .found:
            return "PhotoPosition.found(redacted)"
        case .locationOff:
            return "PhotoPosition.locationOff"
        case .notFound:
            return "PhotoPosition.notFound"
        }
    }
}

/// Où est la personne quand une photo est publiée : UNE mesure, jamais une boucle.
protocol PhotoPositionSource {
    /// UNE position récente (au plus quelques secondes d'attente ; un repli sur celle que le système connaît si elle a moins de 2 minutes),
    /// ou pourquoi il n'y en a pas. LIT la position : n'est appelée qu'une fois la personne ayant touché « Publier ».
    func measure() async -> PhotoPosition
}

/// La mesure d'une photo, faite par le pont du suivi : l'état des autorisations d'abord (pas de mesure sans elles), puis UNE position.
struct TrackingPhotoPositionSource: PhotoPositionSource {
    let position: PositionSource
    let permissions: () async -> PermissionSnapshot

    func measure() async -> PhotoPosition {
        let snapshot = await permissions()
        let allowed = snapshot.location == .always || snapshot.location == .whenInUse
        guard allowed, snapshot.servicesEnabled, snapshot.preciseLocation else {
            return .locationOff
        }
        guard let fix = await position.current() else {
            return .notFound
        }
        return .found(fix)
    }
}
