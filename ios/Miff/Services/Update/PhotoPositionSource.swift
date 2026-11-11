import Foundation

// CONFIDENTIALITÉ, pour ce fichier : la position qui accompagne une photo (décision du PM du 2026-11-06). Elle est demandée au pont du
// suivi (`PositionSource`, le SEUL qui lit une position : `PermissionPrivacyTests`) ET SEULEMENT après le toucher de « Publier » (décision D33 :
// personne n'est localisé pour une photo jamais publiée). Le `Fix` rendu est gardé en mémoire par l'appelant jusqu'à l'envoi du lot ;
// rien ici ne le journalise, ne l'envoie comme événement ni ne le garde. Ce fichier n'importe pas CoreLocation.

/// Ce que le téléphone a dit quand une position a été demandée pour une photo : une position, ou POURQUOI il n'y en a pas (la phrase
/// calme n'est pas la même).
enum PhotoPosition: Equatable, CustomStringConvertible {
    case found(Fix)
    /// L'autorisation de la position a été retirée, ou la localisation du téléphone est éteinte : seule la
    /// personne peut y changer quelque chose (les Réglages).
    case locationOff
    /// La position est autorisée mais seulement APPROXIMATIVE (« Position précise » éteinte) : les Réglages, « Position précise ».
    case approximate
    /// Le téléphone ne peut pas répondre maintenant (pas de signal, lent) : « réessaie dans un instant ».
    case notFound

    var description: String {
        switch self {
        case .found:
            return "PhotoPosition.found(redacted)"
        case .locationOff:
            return "PhotoPosition.locationOff"
        case .approximate:
            return "PhotoPosition.approximate"
        case .notFound:
            return "PhotoPosition.notFound"
        }
    }
}

/// Où est la personne quand une photo est publiée : UNE mesure, jamais une boucle.
protocol PhotoPositionSource {
    /// UNE position récente (quelques secondes d'attente au plus, `TrackingLimits.photoPositionWaitSeconds` ; un repli sur celle que le
    /// système connaît si elle a moins de 2 minutes, puis sur celle que le suivi a gardée, que l'appelant n'accepte que si elle a moins de
    /// 10 minutes), ou pourquoi il n'y en a pas. LIT la position : n'est appelée qu'une fois la personne ayant touché « Publier ».
    func measure() async -> PhotoPosition

    /// Ce que les AUTORISATIONS empêchent, sans jamais lire de position (nil = rien n'empêche). `needsBackground` : la position doit déjà
    /// être publiée dans la Miff par le suivi, qui ne fonctionne qu'avec « Toujours » ; « Pendant l'utilisation » est alors un obstacle.
    func blocker(needsBackground: Bool) async -> PhotoPosition?
}

/// La mesure d'une photo, faite par le pont du suivi : l'état des autorisations d'abord (pas de mesure sans elles), puis UNE position.
struct TrackingPhotoPositionSource: PhotoPositionSource {
    let position: PositionSource
    let permissions: () async -> PermissionSnapshot
    /// La dernière position que le suivi a gardée sur le téléphone (repli quand la mesure fraîche échoue ; jamais acceptée au-delà de
    /// 10 minutes, `PhotoPlace.from`).
    var keptFix: () -> Fix? = { nil }

    func blocker(needsBackground: Bool) async -> PhotoPosition? {
        let snapshot = await permissions()
        guard snapshot.servicesEnabled else {
            return .locationOff
        }
        switch snapshot.location {
        case .notDetermined, .denied, .restricted:
            return .locationOff
        case .whenInUse:
            if needsBackground {
                return .locationOff
            }
        case .always:
            break
        }
        return snapshot.preciseLocation ? nil : .approximate
    }

    func measure() async -> PhotoPosition {
        if let blocked = await blocker(needsBackground: false) {
            return blocked
        }
        if let fix = await position.current(within: TrackingLimits.photoPositionWaitSeconds) {
            return .found(fix)
        }
        if let kept = keptFix() {
            return .found(kept)
        }
        return .notFound
    }
}
