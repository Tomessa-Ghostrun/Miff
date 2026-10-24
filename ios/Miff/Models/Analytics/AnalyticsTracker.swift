import Foundation

/// Destination des événements validés (Firebase Analytics en production, un faux dans les tests).
protocol AnalyticsSink {
    func log(eventName: String, params: [String: Any])
}

enum TrackResult: Equatable {
    /// L'événement a passé la liste blanche et a été remis au SDK.
    case sent
    /// L'événement est valide mais l'utilisateur n'a pas activé Analytics : rien n'est envoyé.
    case disabled
    case refused(reason: RefusalReason, detail: String)
}

/// Le seul moyen d'envoyer un événement Analytics. Ordre des contrôles : la liste blanche d'abord (un événement
/// refusé est signalé comme refusé même quand Analytics est éteint), puis le consentement de l'utilisateur.
final class AnalyticsTracker {
    private let sink: AnalyticsSink
    private let isEnabled: () -> Bool

    init(sink: AnalyticsSink, isEnabled: @escaping () -> Bool) {
        self.sink = sink
        self.isEnabled = isEnabled
    }

    /// Point d'entrée typé pour le code applicatif.
    @discardableResult
    func track(_ event: AnalyticsEvent) -> TrackResult {
        track(event.name, params: event.params)
    }

    /// Point d'entrée non typé, validé de la même façon (tests et bouton QA « envoyer une position »).
    @discardableResult
    func track(_ eventName: String, params: [String: Any] = [:]) -> TrackResult {
        switch AnalyticsContract.validate(eventName, params: params) {
        case .refused(let reason, let detail):
            return .refused(reason: reason, detail: detail)
        case .allowed:
            guard isEnabled() else {
                return .disabled
            }
            sink.log(eventName: eventName, params: params)
            return .sent
        }
    }
}
