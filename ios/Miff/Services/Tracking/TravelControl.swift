import Foundation

/// Ce que le Profil demande au suivi pour le MODE VOYAGE (VOY-2I), en petit protocole : l'écran est testé avec un double. Un voyage est
/// ouvert à TOUS les comptes : rien ici ne lit ni ne vérifie un abonnement.
protocol TravelControl {
    /// Le voyage en cours sur ce téléphone (lu sur la copie locale), ou nil.
    func current() -> TravelPeriod?

    /// Commence un voyage, ou en déplace la fin quand il y en a un.
    func set(_ period: TravelPeriod) async -> TravelResult

    /// Met fin au voyage maintenant. Faux quand il n'y en avait pas.
    func end() async -> Bool
}

/// `TravelControl` adossé au moteur de suivi.
struct EngineTravelControl: TravelControl {
    let engine: TrackingEngine

    func current() -> TravelPeriod? {
        engine.travelPeriod()
    }

    func set(_ period: TravelPeriod) async -> TravelResult {
        await engine.setTravel(period)
    }

    func end() async -> Bool {
        await engine.endTravel()
    }
}
