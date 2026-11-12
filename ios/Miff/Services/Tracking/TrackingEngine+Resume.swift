import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : la REPRISE du partage d'une Miff (recette TestFlight, 2026-11-11 : la carte restait grise après
// « arrêter puis reprendre le partage »). Seuls des identifiants de lieux, des heures et la nature de la présence passent ici ; une position
// n'est lue que pour juger de l'appartenance à une zone (comme `recheckStates`), jamais gardée ni journalisée.
//
// Pourquoi : arrêter le partage SUPPRIME le statut (et la photo, et la dernière position) de la Miff sur le serveur ; la reprendre le récrit
// à `unknown` (gris). Le suivi n'écrit un statut que sur un CHANGEMENT de présence : s'il gardait la présence d'avant l'arrêt (ou avait
// retrouvé la Miff « comme neuve » alors que la personne est déjà dans le lieu, donc aucune arrivée à signaler), plus rien ne la réécrivait avant
// la prochaine entrée ou sortie de zone. Elle restait grise.

extension TrackingEngine {
    /// Les Miffs dont le partage vient d'être repris ET que le suivi connaît maintenant : leur détection est remise à zéro (présence, dernière
    /// position écrite, statut en attente, batterie : le serveur n'a plus rien de tout cela) et le drapeau est retiré. Une Miff pas encore
    /// éligible garde son drapeau pour le passage où elle le sera.
    func takeResumed(_ state: inout LocalState) -> [String] {
        guard let flagged = state.resumedMiffs, !flagged.isEmpty else {
            return []
        }
        var taken: [String] = []
        for miffId in flagged.sorted() where state.miffs[miffId] != nil {
            state.update(miffId) { miff in
                // MODE VOYAGE : le statut « inconnu » qui efface les clés d'un voyage terminé (`clearTravel`) attend peut-être encore son envoi ;
                // la remise à zéro ne le supprime pas (la reprise a certes récrit le statut, mais sans compter là-dessus). Le recalcul ci-dessous
                // le remplace s'il a mieux à dire ; l'écriture efface les clés de voyage dans tous les cas.
                let keptPending = miff.clearTravel ? miff.pending : nil
                miff.resetDetection()
                miff.pending = keptPending
            }
            taken.append(miffId)
        }
        let remaining = flagged.subtracting(taken)
        state.resumedMiffs = remaining.isEmpty ? nil : remaining
        return taken
    }

    /// La présence d'une Miff reprise, recalculée « comme si elle n'avait jamais été grise » à partir de ce que le téléphone sait de la
    /// personne MAINTENANT : les zones où la copie locale la croit, et la position de ce passage (si elle a moins de 10 minutes). Dans une
    /// zone de confiance ou de danger : cette présence, à l'heure d'entrée connue (sinon maintenant). Hors de toutes les zones de la Miff, avec
    /// une position pour le dire : `out`, maintenant. Sans position ET hors zone : rien n'est inventé (le statut reste `unknown` jusqu'au
    /// prochain signal du système, qui arrive aussi pour les zones tout juste réarmées). Le statut est écrit par le passage habituel
    /// (`flushPending`), avec toutes ses conditions (« Toujours », consentement v3, session, disjoncteur). Choix de l'heure : l'arrivée recalculée porte l'heure d'entrée connue
    /// (jusqu'à 24 h, sinon maintenant) pour `presenceAt` ET `lastUpdateAt`, comme une vraie arrivée (une zone de confiance est toujours verte,
    /// `StatusColor`) ; une zone de danger ne pose AUCUNE update (rouge tant que la personne n'en fait pas une, comme à l'arrivée) ; un `out`
    /// part de MAINTENANT (le repère de l'orange et du rouge est le moment de la reprise : on ne sait pas depuis quand la personne est partie).
    func presenceAfterResume(_ start: LocalState, miffIds: [String], fix: Fix?, now: Int) -> LocalState {
        var state = start
        for miffId in miffIds {
            let zones = zonesOf(state, miffId)
            guard !zones.isEmpty else {
                continue
            }
            var evidence = state.inside
            if let fix {
                let ownKeys = Set(zones.map { $0.key })
                for zone in allZones(state) where ownKeys.contains(zone.key) && evidence[zone.key] == nil {
                    if ZoneSelection.contains(zone, lat: fix.lat, lng: fix.lng) {
                        evidence[zone.key] = now
                    }
                }
            }
            let derived = PresenceMachine.derive(zones: zones, inside: evidence)
            let fact: StatusFact
            switch derived.presence {
            case .inTrusted, .inRisk:
                let entered = zones.first(where: { $0.placeId == derived.placeId }).flatMap { evidence[$0.key] } ?? now
                fact = StatusFact(
                    presence: derived.presence,
                    placeId: derived.placeId,
                    presenceAtMillis: EventTime.forWrite(eventMillis: entered, nowMillis: now) ?? now,
                    setLastUpdate: derived.presence == .inTrusted
                )
            case .out:
                guard fix != nil else {
                    continue
                }
                fact = StatusFact(presence: .out, placeId: nil, presenceAtMillis: now, setLastUpdate: false)
            default:
                continue
            }
            journal.record(.statusAfterResume, placeId: derived.placeId)
            state.update(miffId) {
                $0.derived = derived
                $0.pending = PendingStatus(fact: fact)
            }
        }
        return state
    }
}
