import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : les événements de zone et le retour du réseau. La position qui accompagne un événement
// de zone n'est JAMAIS lue (le système n'en donne pas à ce niveau, et le pont n'en transmet aucune). Voir l'en-tête de
// `TrackingEngine.swift`.

extension TrackingEngine {
    /// La surveillance de régions dit : entrée dans une zone, ou sortie. Seuls les identifiants des zones et la nature de l'événement
    /// sont utilisés ; l'heure est celle où l'événement arrive à l'app (le système n'en donne pas d'autre).
    func doTransition(_ transition: ZoneTransition) async {
        guard let uid = env.uid(), !deps.device.isRevoked else {
            await doStop()
            return
        }
        let pass = await makePass(uid: uid)
        let loaded = stateFor(uid)
        guard var state = loaded, !state.miffs.isEmpty, pass.snapshot.locationComplete else {
            // L'accord ou l'accès a disparu : les zones sont retirées maintenant et la copie (position, statuts en attente) effacée ;
            // rien n'est écrit.
            await standDownOnEvent(loaded)
            return
        }
        guard allZones(state).contains(where: { $0.key == transition.zoneKey }) else {
            // Une région que le système garde et que la liste des lieux ne connaît plus : la prochaine mise en conformité la retire.
            return
        }
        let now = env.nowMillis()
        journal.record(
            transition.kind == .enter ? .transitionEnter : .transitionExit,
            placeId: ZoneKey.placeId(transition.zoneKey),
            batteryPct: pass.batteryPct
        )
        state = applyTransition(transition, to: state, now: now)
        // Le statut est gardé AVANT d'être envoyé : un processus tué entre-temps ne le perd pas (c'est le dernier qui compte).
        deps.local.save(state)
        state = await flushPending(state, pass: pass)
        deps.local.save(state)
        // « À chaque entrée détectée dans une zone, la liste est recalculée » (ticket §6.5), et la position est lue de nouveau.
        if transition.kind == .enter {
            await doRefresh(.zoneEntered, supplied: nil)
        }
    }

    private func standDownOnEvent(_ loaded: LocalState?) async {
        journal.record(.notEligible)
        if let loaded {
            deps.local.save(await disarm(loaded))
        } else {
            await deps.geofences.disarmAll()
            await deps.movement.stop()
        }
    }

    /// L'état local « où je suis » après l'événement, et le statut en attente de chaque Miff que cela change.
    private func applyTransition(_ transition: ZoneTransition, to state: LocalState, now: Int) -> LocalState {
        var next = state
        switch transition.kind {
        case .enter:
            if next.inside[transition.zoneKey] == nil {
                next.inside[transition.zoneKey] = min(transition.atMillis, now)
            }
        case .exit:
            next.inside[transition.zoneKey] = nil
        }
        let written = EventTime.forWrite(eventMillis: transition.atMillis, nowMillis: now)
        let event = ZoneTransition(kind: transition.kind, zoneKey: transition.zoneKey, atMillis: written ?? now)
        for (miffId, miff) in state.miffs {
            let change = PresenceMachine.onTransition(
                event,
                zones: zonesOf(state, miffId),
                insideAfter: next.inside,
                last: miff.derived
            )
            guard let change else {
                continue
            }
            if written == nil {
                // Trop vieux pour que les règles l'acceptent : abandonné (pas de boucle), mais l'état local avance.
                journal.record(.statusGivenUp, placeId: change.derived.placeId)
                next.update(miffId) { $0.derived = change.derived }
            } else {
                next.update(miffId) {
                    $0.derived = change.derived
                    $0.pending = PendingStatus(fact: change.fact)
                }
            }
        }
        return next
    }

    /// Le système dit, à notre demande, si la personne est dans la zone. Dedans alors que la copie locale l'ignorait : une arrivée
    /// (DET3, « déjà dans le lieu à l'armement »). Dehors alors que la copie locale la disait dedans : la sortie a été MANQUÉE (app
    /// fermée de force, téléphone éteint) ; son heure est inconnue, donc `unknown` plutôt qu'un `out` inventé.
    func doZoneState(zoneKey: String, isInside: Bool) async {
        guard let uid = env.uid(), !deps.device.isRevoked else {
            await doStop()
            return
        }
        guard let state = stateFor(uid), !state.miffs.isEmpty,
              allZones(state).contains(where: { $0.key == zoneKey }) else {
            return
        }
        let placeId = ZoneKey.placeId(zoneKey)
        let known = state.inside[zoneKey] != nil
        let now = env.nowMillis()
        if isInside {
            journal.record(.regionInside, placeId: placeId)
            if !known {
                await doTransition(ZoneTransition(kind: .enter, zoneKey: zoneKey, atMillis: now))
            }
            return
        }
        journal.record(.regionOutside, placeId: placeId)
        if known {
            await settleMissedExit(uid: uid, state: state, zoneKey: zoneKey, now: now)
        }
    }

    private func settleMissedExit(uid: String, state: LocalState, zoneKey: String, now: Int) async {
        let pass = await makePass(uid: uid)
        guard pass.snapshot.locationComplete else {
            return
        }
        var next = state
        next.inside[zoneKey] = nil
        for (miffId, miff) in state.miffs {
            let change = PresenceMachine.onMissedExit(
                zoneKey: zoneKey,
                zones: zonesOf(state, miffId),
                insideAfter: next.inside,
                last: miff.derived,
                atMillis: now
            )
            guard let change else {
                continue
            }
            next.update(miffId) {
                $0.derived = change.derived
                $0.pending = PendingStatus(fact: change.fact)
            }
        }
        deps.local.save(next)
        next = await flushPending(next, pass: pass)
        deps.local.save(next)
    }

    /// Le réseau est revenu : seulement s'il y a un statut en attente. Les conditions sont revérifiées AVANT d'écrire (revue I3) :
    /// « Toujours » toujours là, et partage + consentement de version 3 relus du serveur. Sans réponse du serveur, rien n'est écrit sur
    /// une supposition (l'état reste en attente) ; « Toujours » retiré : l'état en attente, la position et l'état local sont effacés.
    func doNetworkBack() async {
        guard let uid = env.uid(), !deps.device.isRevoked else {
            await doStop()
            return
        }
        guard let state = stateFor(uid), state.miffs.values.contains(where: { $0.pending != nil }) else {
            return
        }
        journal.record(.wakeNetwork)
        let pass = await makePass(uid: uid)
        guard pass.snapshot.locationComplete else {
            deps.local.save(await disarm(state))
            return
        }
        let synced = await syncMiffs(uid, state, .networkBack, env.nowMillis())
        if synced.state.miffs.isEmpty && synced.verified {
            await deps.geofences.disarmAll()
            await deps.movement.stop()
            deps.local.save(LocalState(uid: uid, checkedAtMillis: synced.state.checkedAtMillis))
            return
        }
        // Pas vérifié (pas de réponse) : cela reste en attente. La session est vérifiée de nouveau avant la première écriture (un
        // téléphone resté hors ligne a pu être remplacé entre-temps : ce qui attend n'est alors jamais écrit).
        var next = synced.state
        if synced.verified {
            next = await flushPending(next, pass: pass)
        }
        deps.local.save(next)
    }
}
