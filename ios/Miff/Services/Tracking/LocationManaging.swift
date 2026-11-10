import Foundation

// CONFIDENTIALITÉ, pour ce fichier : il décrit ce que le suivi demande au gestionnaire de localisation du système et ce que celui-ci
// lui rend, SANS importer CoreLocation (le seul fichier qui le fait pour lire une position est `SystemLocationManager.swift`). Une
// coordonnée devient tout de suite un `Fix` (dont la `description` ne montre rien). Cette séparation permet de tester toute la
// logique du pont (doublons, état demandé, autorisation, position unique) avec un faux gestionnaire.

/// L'autorisation telle que le système la décrit : sert à ne signaler que les VRAIS changements (le système rappelle aussi au
/// démarrage).
struct AuthorizationState: Equatable {
    let location: LocationAuthorization
    let fullAccuracy: Bool
}

/// Ce que le système rend, une fois dépouillé de tout ce qui n'est pas nécessaire.
enum LocationEvent: Equatable {
    case fix(Fix)
    /// Le système ne peut pas répondre pour de bon (autorisation refusée, erreur de réseau...) : la demande se termine.
    case fixFailed
    /// Le système n'a pas de position POUR L'INSTANT (`kCLErrorLocationUnknown` : GPS à froid, intérieur, signal faible) : l'échec est
    /// passager, la demande peut être refaite tant que quelqu'un attend encore.
    case fixUnavailable
    case region(TransitionKind, String)
    case regionState(String, Bool)
    case monitoringFailed
    case authorization(AuthorizationState)
}

/// Le gestionnaire de localisation du système, réduit à ce que le suivi utilise. Les clés sont celles des zones (`ZoneKey`).
@MainActor
protocol LocationManaging: AnyObject {
    /// Reçoit chaque événement, sur le fil principal.
    var onEvent: (@MainActor (LocationEvent) -> Void)? { get set }

    /// Les clés des régions que le système surveille MAINTENANT pour l'app.
    var heldRegionKeys: Set<String> { get }

    /// Le plus grand rayon que le système accepte pour une région.
    var maximumRegionRadius: Double { get }

    /// La dernière position que le système connaît (sans âge limite : l'appelant juge).
    var systemFix: Fix? { get }

    var regionsAvailable: Bool { get }
    var significantChangesAvailable: Bool { get }

    /// Enregistre (ou remplace) la région de cette zone.
    func watch(_ zone: TrackedZone, radius: Double)
    func unwatch(key: String)

    /// Demande au système de dire si la personne est dans la région (réponse : `LocationEvent.regionState`).
    func askRegionState(key: String)

    /// Demande UNE position (réponse : `LocationEvent.fix` ou `.fixFailed`).
    func askPosition()
    func beginSignificantChanges()
    func endSignificantChanges()
}
