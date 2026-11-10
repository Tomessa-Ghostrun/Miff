import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : la FIN d'un voyage et l'ADOPTION d'un voyage par un nouvel appareil. Voir l'en-tête de
// `TrackingEngine+Travel.swift`. Rien n'est écrit sans l'identifiant de la session confirmée de ce téléphone ; la fin écrit `unknown`
// (neutre) AVANT que le moindre statut de présence reparte, et efface les clés du voyage dans la même écriture. Un nouvel appareil ne
// lit, n'arme et n'écrit RIEN avant d'avoir su s'il y a un voyage : sans cette lecture il écraserait le voyage avec un statut ordinaire.

extension TrackingEngine {
    func doEndTravel() async -> Bool {
        guard let uid = env.uid(), let state = stateFor(uid), state.travel != nil else {
            return false
        }
        await finishTravel(uid, state, now: env.nowMillis())
        return true
    }

    /// Le voyage finit (la personne l'a éteint, ou sa fin est venue) : les proches apprennent l'état neutre « inconnu » (et les clés du voyage
    /// sont effacées dans la MÊME écriture), puis le statut reprend : zones, position et une présence si la personne est dans un lieu.
    /// Une écriture qui ne peut pas partir attend le réseau (l'état local est déjà libre : le suivi n'attend pas).
    func finishTravel(_ uid: String, _ start: LocalState, now: Int) async {
        guard start.travel != nil else {
            return
        }
        let fact = PresenceMachine.unknown(atMillis: now)
        var state = start
        for miffId in start.miffs.keys {
            state.update(miffId) {
                $0.derived = DerivedPresence(presence: fact.presence, placeId: fact.placeId)
                $0.pending = PendingStatus(fact: fact)
                // TOUJOURS, même pour une Miff que le téléphone ne croit pas avoir prévenue (`startedIn`) : si la réponse du début s'est
                // perdue, le serveur porte quand même le voyage, et un statut de fin sans les clés laisserait un faux « Voyage terminé »
                // durable chez les proches (les règles acceptent `null` sur un statut qui n'a pas ces clés).
                $0.clearTravel = true
            }
        }
        state.travel = nil
        // Gardé AVANT d'être envoyé : un processus tué entre-temps reprend (l'état à dire est sur le disque).
        deps.local.save(state)
        let pass = await makePass(uid: uid)
        state = await flushPending(state, pass: pass)
        deps.local.save(state)
        // Zones, position, présence : le recalcul habituel d'un redémarrage.
        await doRefresh(.rearm, supplied: nil)
    }

    /// Un NOUVEL appareil pour ce compte (aucune relecture vérifiée du serveur jusque-là) lit d'abord s'il y a un voyage, AVANT d'armer ou
    /// d'écrire quoi que ce soit, pour l'ADOPTER au lieu de l'écraser. Vrai quand le passage est fini : voyage adopté et traité, ou
    /// impossible à savoir (rien n'est gardé ni armé sur une supposition ; le réveil suivant le redemande).
    func settleNewPhone(_ uid: String, _ state: LocalState, _ reason: Reason, supplied: Fix?) async -> Bool {
        guard let adopted = await adoptTravel(uid, state, now: env.nowMillis()) else {
            return true
        }
        guard adopted.travel != nil else {
            return false
        }
        let paused = await pauseStatuses(adopted)
        deps.local.save(paused)
        await doTravelPass(uid, paused, reason, supplied: supplied)
        return true
    }

    /// MON statut est lu dans chaque Miff où la personne partage (une lecture chacune, auprès du serveur). nil : le serveur n'a pas pu le dire.
    private func adoptTravel(_ uid: String, _ state: LocalState, now: Int) async -> LocalState? {
        let store = deps.store
        var found: TravelRead?
        var reads: [String: TravelPeriod] = [:]
        for miffId in state.miffs.keys.sorted() {
            let read = await guardedCall(timeoutSeconds: deps.timeoutSeconds) {
                try await store.ownTravel(miffId: miffId, uid: uid)
            }
            switch read {
            case .value(let trip):
                guard let trip else {
                    continue
                }
                found = found ?? trip
                reads[miffId] = trip.period
            case .failed(_, let lostAccess):
                if !lostAccess {
                    return nil
                }
            }
        }
        guard let trip = found else {
            return state
        }
        var next = state
        next.travel = LocalTravel(
            untilMillis: trip.period.untilMillis,
            untilDay: trip.period.untilDay,
            sinceMillis: trip.sinceMillis ?? now,
            startedIn: Set(reads.keys),
            upToDateIn: Set(reads.filter { $0.value == trip.period }.keys)
        )
        return next
    }
}
