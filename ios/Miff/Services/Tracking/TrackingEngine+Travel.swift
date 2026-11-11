import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier (MODE VOYAGE, docs/tickets/mode-voyage.md, décision du PM du 2026-11-10) : la POSITION reste
// partagée pendant un voyage, avec les mêmes garde-fous et consentements que d'habitude (« Toujours » + position exacte, accord de version 3,
// session de l'appareil, partage actif, fréquence, plafond du jour, une écriture par Miff, seule). Ce qui change : AUCUN statut de présence
// ordinaire, AUCUNE batterie, AUCUN accès n'est écrit pendant le voyage (une présence écrite par-dessus mettrait fin au voyage chez les
// proches ; les règles refusent un lieu ou une batterie à côté d'un voyage). Les seules écritures de statut sont celles qui DISENT le voyage
// (début, nouvelle fin, Miff rejointe) et sa fin. Les zones ne sont pas surveillées pendant le voyage (rien ne suivrait une arrivée : les
// surveiller réveillerait l'app pour rien) ; restent l'ouverture, le passage en arrière-plan et les changements de position significatifs,
// qui gardent la position à jour. Aucune position n'est jamais supprimée. Un nouvel appareil lit son voyage AVANT d'armer quoi que ce soit
// (`TrackingEngine+TravelEnd.swift`). Aucun journal, aucun événement : la date d'un voyage dit une absence prévue.
// Miroir de `TrackingEngine.kt` (VOY-1A) d'Android, sans alarme : sur iOS la fin est traitée au premier réveil ou à la première ouverture
// qui suit la date (la position continue d'être mesurée, donc l'app est réveillée comme d'habitude), jamais à la seconde près.

/// Ce que `setTravel` répond.
enum TravelResult: Equatable {
    /// Le voyage est en place, et chaque Miff où la personne partage le sait (ou elle ne partage nulle part).
    case done
    /// Le voyage est en place ICI ; au moins une Miff l'apprendra dès que le réseau sera revenu (pas de réseau, ou il a échoué).
    case doneOffline
    /// Le serveur a dit non (règles pas encore déployées, session prise) : rien n'a changé.
    case refused
    /// Personne n'est connecté, ou la session est finie.
    case unavailable
}

/// Ce qui est commun à un passage pendant un voyage (une fonction à plus de 5 paramètres est refusée par SwiftLint).
struct TravelPass {
    let uid: String
    let reason: TrackingEngine.Reason
    let supplied: Fix?
    let now: Int
    let permission: WritePermission
}

extension TrackingEngine {
    // MARK: - Commencer, déplacer la fin

    func doSetTravel(_ period: TravelPeriod) async -> TravelResult {
        guard let uid = env.uid(), !deps.device.isRevoked else {
            return .unavailable
        }
        let now = env.nowMillis()
        guard isAcceptableEnd(period, now: now) else {
            return .refused
        }
        var state = stateFor(uid) ?? LocalState(uid: uid)
        let online = env.isOnline()
        // Qui prévenir : les Miffs où la personne partage, lues auprès du serveur quand c'est possible (la liste locale sinon).
        var read: Set<String>?
        if online {
            read = await readEligible(uid)
        }
        let targets = (read ?? Set(state.miffs.keys)).sorted()
        let first = LocalTravel(
            untilMillis: period.untilMillis, untilDay: period.untilDay, sinceMillis: state.travel?.sinceMillis ?? now
        )
        var travel = (state.travel ?? first).moved(to: period)
        if online && !targets.isEmpty {
            // Rien n'a pu être écrit alors que le serveur a dit non : rien ne change, le suivi continue.
            guard let told = await tellMiffs(targets, uid: uid, travel: travel, now: now) else {
                return .refused
            }
            travel.startedIn.formUnion(told)
            travel.upToDateIn = told
        }
        state = keeping(travel, targets: targets, read: read, in: state, now: now)
        // Le statut de présence ne s'arrête que MAINTENANT : le voyage est connu du serveur (ou gardé pour lui être dit). La position reste.
        state = await pauseStatuses(state)
        deps.local.save(state)
        return travelToTell(state) ? .doneOffline : .done
    }

    /// Le serveur accepte une fin à 91 jours au plus ; le sélecteur s'arrête à aujourd'hui + 89 jours : ceci garde tout le reste (une
    /// horloge qui a bougé, un changement d'heure). Une fin déjà passée n'est jamais écrite.
    private func isAcceptableEnd(_ period: TravelPeriod, now: Int) -> Bool {
        !period.isOver(nowMillis: now) && period.untilMillis <= now + TravelLimits.maxEndAheadMillis
    }

    /// La copie locale avec le voyage : la liste des Miffs est celle que le serveur vient de dire (ou, sans réponse, la liste d'avant
    /// complétée).
    private func keeping(
        _ travel: LocalTravel,
        targets: [String],
        read: Set<String>?,
        in start: LocalState,
        now: Int
    ) -> LocalState {
        var state = start
        let listed = Dictionary(uniqueKeysWithValues: targets.map { ($0, start.miffs[$0] ?? LocalMiff()) })
        if read != nil || start.miffs.isEmpty {
            state.miffs = listed
        } else {
            state.miffs = start.miffs.merging(listed) { _, new in new }
        }
        state.travel = travel
        if read != nil {
            state.checkedAtMillis = now
        }
        return state
    }

    /// Écrit le voyage dans chaque Miff de `targets`, une écriture seule par Miff. Rend les Miffs qui l'ont reçu ; nil quand le serveur a
    /// refusé et que RIEN n'a pu être écrit (rien n'a changé : le suivi continue).
    private func tellMiffs(_ targets: [String], uid: String, travel: LocalTravel, now: Int) async -> Set<String>? {
        let permission = WritePermission(engine: self, uid: uid)
        // Pas d'identifiant de session (remplacé, ou le serveur n'a pas pu être interrogé) : rien n'est écrit et rien ne change.
        guard await permission.deviceId() != nil else {
            return nil
        }
        let presenceAt = EventTime.forWrite(eventMillis: travel.sinceMillis, nowMillis: now) ?? now
        var written = Set<String>()
        var refusals = 0
        for miffId in targets {
            guard let deviceId = await permission.deviceId(), !permission.ended() else {
                break
            }
            let write = travelWrite(miffId, uid: uid, travel: travel, presenceAt: presenceAt, deviceId: deviceId)
            guard let write else {
                continue
            }
            let outcome = await commit(write)
            if case .value = outcome {
                written.insert(miffId)
            } else if case .failed(let failure, _) = outcome, failure == .refused {
                refusals += 1
                await permission.refused()
            } else {
                // Pas de réseau, ou pas de réponse : ce qui reste attend le réveil suivant.
                break
            }
        }
        return written.isEmpty && refusals > 0 ? nil : written
    }

    /// L'écriture du voyage dans UNE Miff : seulement la fin quand la Miff connaît déjà le voyage, sinon le voyage entier.
    private func travelWrite(
        _ miffId: String,
        uid: String,
        travel: LocalTravel,
        presenceAt: Int,
        deviceId: String
    ) -> DocWrite? {
        if travel.startedIn.contains(miffId) {
            return TrackingWrites.travelDate(miffId: miffId, uid: uid, period: travel.period, deviceId: deviceId)
        }
        return TrackingWrites.travelStart(
            miffId: miffId, uid: uid, period: travel.period, presenceAtMillis: presenceAt, deviceId: deviceId
        )
    }

    /// Une Miff de la liste doit encore apprendre le voyage (ou sa nouvelle fin).
    func travelToTell(_ state: LocalState) -> Bool {
        guard let travel = state.travel else {
            return false
        }
        return state.miffs.keys.contains { !travel.upToDateIn.contains($0) }
    }

    /// Les Miffs où la personne partage (partage actif, accord de version 3), SANS leurs lieux. nil quand le serveur n'a pas pu le dire.
    private func readEligible(_ uid: String) async -> Set<String>? {
        let store = deps.store
        let found = await guardedCall(timeoutSeconds: deps.timeoutSeconds) {
            try await store.memberships(uid: uid)
        }
        guard case .value(let refs) = found else {
            return nil
        }
        var seen = Set<String>()
        var eligible = Set<String>()
        for ref in refs where seen.insert(ref.miffId).inserted {
            let miffId = ref.miffId
            let consent = await guardedCall(timeoutSeconds: deps.timeoutSeconds) {
                try await store.consent(miffId: miffId, uid: uid)
            }
            switch consent {
            case .value(let card):
                if let card, card.enabled, MiffSharingConsent.coversPosition(card.version) {
                    eligible.insert(miffId)
                }
            case .failed(_, let lostAccess):
                // Plus membre : pas une Miff à prévenir. Toute autre panne : rien n'en est conclu.
                if !lostAccess {
                    return nil
                }
            }
        }
        return eligible
    }

    // MARK: - Un passage pendant le voyage

    /// Un réveil pendant un voyage. Le voyage est fini : il se termine (le statut reprend). Sinon : les Miffs apparues depuis sont cherchées
    /// (une Miff rejointe pendant un voyage l'apprend aussitôt), ce que les Miffs n'ont pas encore appris est écrit, et la position
    /// continue d'être publiée.
    func doTravelPass(_ uid: String, _ start: LocalState, _ reason: Reason, supplied: Fix?) async {
        guard let travel = start.travel else {
            return
        }
        let now = env.nowMillis()
        if travel.period.isOver(nowMillis: now) {
            await finishTravel(uid, start, now: now)
            return
        }
        let pass = TravelPass(
            uid: uid, reason: reason, supplied: supplied, now: now, permission: WritePermission(engine: self, uid: uid)
        )
        var state = await syncTravelTargets(start, pass)
        state = await flushTravel(state, pass)
        // Le retour du réseau ne mesure rien (comme hors voyage).
        if reason != .networkBack {
            state = await publishPositionDuringTravel(state, pass)
        }
        deps.local.save(state)
    }

    /// La position continue d'être mesurée et publiée pendant un voyage, avec les garde-fous d'un passage ordinaire : rien sans « Toujours » et
    /// la position exacte, sans Miff où la personne partage sous l'accord de version 3, ou sans la session de ce téléphone ; une écriture
    /// par Miff, seule, avec le garde-fou de fréquence et le disjoncteur. Seuls les changements de position significatifs sont gardés (ils
    /// gardent la position à jour app fermée) ; les zones sont retirées.
    private func publishPositionDuringTravel(_ start: LocalState, _ pass: TravelPass) async -> LocalState {
        var state = start
        let snapshot = await env.permissions()
        guard !state.miffs.isEmpty, snapshot.locationComplete else {
            // Personne à prévenir, ou l'accès est retiré (AUT7) : aucune position lue, aucun signalement demandé, la copie de la position
            // effacée.
            await deps.geofences.disarmAll()
            await stopMovement()
            state.armed = []
            state.updatesArmed = false
            state.fix = nil
            return state
        }
        let full: [Reason] = [.opened, .rearm, .sharingChanged]
        let isFull = full.contains(pass.reason)
        if isFull {
            await deps.geofences.disarmAll()
            state.armed = []
        }
        if isFull || !state.updatesArmed || !isMovementStarted() {
            let started = await deps.movement.start()
            state.updatesArmed = started
            setMovementStarted(started)
        }
        let fix = await chooseFix(state, supplied: pass.supplied, reason: pass.reason, now: pass.now)
        if let fix {
            state.fix = fix
            journal.record(.positionRead)
        }
        // Aucune batterie pendant un voyage : le niveau n'est même pas lu pour ce passage.
        let context = PassContext(uid: pass.uid, snapshot: snapshot, batteryPct: nil, permission: pass.permission)
        return await publishPosition(state, fix: fix, pass: context, now: pass.now)
    }

    /// Le statut de présence s'arrête (un voyage commence, ou est adopté) : les zones sont retirées, ce qui en était déduit et les statuts
    /// en attente sont oubliés. La position, son garde-fou de fréquence et les signalements de déplacement restent : elle reste partagée.
    func pauseStatuses(_ start: LocalState) async -> LocalState {
        await deps.geofences.disarmAll()
        var state = start
        for miffId in start.miffs.keys {
            state.update(miffId) {
                $0.derived = nil
                $0.pending = nil
            }
        }
        state.inside = [:]
        state.armed = []
        return state
    }

    /// Les Miffs où la personne partage sont relues quand c'est l'heure (partage changé, redémarrage, retour du réseau, une heure à
    /// l'ouverture) : 1 + n lectures, aucun lieu.
    private func syncTravelTargets(_ state: LocalState, _ pass: TravelPass) async -> LocalState {
        guard let travel = state.travel, isTravelReadDue(state, pass), env.isOnline(),
              let eligible = await readEligible(pass.uid) else {
            return state
        }
        var next = state
        next.miffs = Dictionary(uniqueKeysWithValues: eligible.map { ($0, state.miffs[$0] ?? LocalMiff()) })
        next.checkedAtMillis = pass.now
        var kept = travel
        kept.startedIn.formIntersection(eligible)
        kept.upToDateIn.formIntersection(eligible)
        next.travel = kept
        return next
    }

    private func isTravelReadDue(_ state: LocalState, _ pass: TravelPass) -> Bool {
        switch pass.reason {
        case .sharingChanged, .rearm, .networkBack:
            return true
        case .opened:
            return state.checkedAtMillis == 0 || pass.now - state.checkedAtMillis > TrackingLimits.placesRereadMillis
        case .backgrounded, .moved, .zoneEntered, .accessChanged:
            return false
        }
    }

    /// Dit le voyage (ou sa nouvelle fin) aux Miffs qui ne le savent pas encore : une écriture indépendante par Miff ; un refus retire la Miff
    /// de la liste (elle est regardée de nouveau bientôt).
    private func flushTravel(_ start: LocalState, _ pass: TravelPass) async -> LocalState {
        var state = start
        guard var travel = state.travel, env.isOnline() else {
            return state
        }
        let pending = state.miffs.keys.sorted().filter { !travel.upToDateIn.contains($0) }
        let presenceAt = EventTime.forWrite(eventMillis: travel.sinceMillis, nowMillis: pass.now) ?? pass.now
        for miffId in pending {
            guard let deviceId = await pass.permission.deviceId(), !pass.permission.ended() else {
                break
            }
            let write = travelWrite(miffId, uid: pass.uid, travel: travel, presenceAt: presenceAt, deviceId: deviceId)
            guard let write else {
                continue
            }
            let outcome = await commit(write)
            if case .value = outcome {
                travel.startedIn.insert(miffId)
                travel.upToDateIn.insert(miffId)
            } else if case .failed(let failure, _) = outcome, failure == .refused {
                await pass.permission.refused()
                state.recheckSoon(now: pass.now)
                state.miffs[miffId] = nil
                travel.startedIn.remove(miffId)
                travel.upToDateIn.remove(miffId)
            } else {
                // Pas de réseau, ou pas de réponse : ce qui reste attend le réveil suivant.
                break
            }
        }
        state.travel = travel
        return state
    }
}
