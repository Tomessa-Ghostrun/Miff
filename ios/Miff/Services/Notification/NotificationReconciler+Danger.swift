import Foundation

// N6 : « Tu es dans une zone de danger de ta Miff » (ticket §20.7). CONFIDENTIALITÉ : le texte est fixe, aucun nom de lieu ni de Miff dans
// la notification ; l'entrée du centre ne garde que le type, l'heure et l'identifiant de la Miff. Aucune lecture ni écriture Firestore,
// aucun événement Analytics : la notification est locale, immédiate, et part AVANT l'écriture du statut sur le serveur.

extension NotificationReconciler {
    /// Le suivi des lieux vient de détecter que j'entre dans une zone de danger d'une de mes Miffs (un statut `in_risk` NOUVEAU : entrer
    /// dans une zone de danger en étant dans une zone de confiance, ou une présence déjà `in_risk`, ne produit rien). Toutes mes Miffs
    /// sont concernées, pas seulement la Miff active.
    func dangerZoneEntered(miffId: String, placeId: String?, enteredAtMillis: Int) async {
        guard let uid = deps.currentUid() else {
            return
        }
        let now = deps.nowMillis()
        let authorization = await deps.authorization.authorization()
        guard deps.currentUid() == uid else {
            return
        }
        let ledger = deps.plans.loadLedger(uid: uid).pruned(nowMillis: now)
        let calendar = deps.calendar()
        let placeKey = NotificationDangerZone.placeKey(miffId: miffId, placeId: placeId)
        var input = DangerZoneInput(
            nowMillis: now,
            sessionUid: uid,
            miffId: miffId,
            placeId: placeId,
            enteredAtMillis: enteredAtMillis
        )
        input.preferences = deps.preferences.preferences
        input.authorization = authorization
        input.notifiedKeys = ledger.notifiedKeys
        input.lastForThisPlaceMillis = ledger.lastDanger[placeKey]
        input.shownToday = shownToday(now: now, calendar: calendar)
        input.isForeground = deps.isForeground()
        input.calendar = calendar
        input.myTravelUntilMillis = deps.plans.loadTravelEnd(uid: uid)
        let decision = NotificationDangerZone.decide(input)
        guard decision != .skip else {
            return
        }
        let eventKey = NotificationDangerZone.key(miffId: miffId, placeId: placeId, enteredAtMillis: enteredAtMillis)
        var state = NotificationItemState.silent
        if case .show(let quiet) = decision {
            await deps.scheduler.add(dangerRequest(miffId: miffId, key: eventKey, quiet: quiet))
            state = .shown
        }
        deps.center.record(
            NotificationItem(
                id: eventKey, type: .n6, miffId: miffId, createdAtMillis: now, readAtMillis: nil, state: state
            )
        )
        // Relu juste avant d'écrire, sans attente entre les deux : une réconciliation en cours n'écrase pas cette marque.
        let latest = deps.plans.loadLedger(uid: uid)
        deps.plans.saveLedger(
            latest.marking(key: eventKey, atMillis: now).markingDanger(placeKey: placeKey, atMillis: now),
            uid: uid
        )
    }

    /// Les N6 déjà AFFICHÉES aujourd'hui (jour local), pour le plafond de 3.
    private func shownToday(now: Int, calendar: Calendar) -> Int {
        let today = calendar.startOfDay(for: Date(timeIntervalSince1970: Double(now) / 1000))
        return deps.center.history.items.filter { item in
            item.type == .n6 && item.state == .shown
                && calendar.startOfDay(for: Date(timeIntervalSince1970: Double(item.createdAtMillis) / 1000)) == today
        }.count
    }

    private func dangerRequest(miffId: String, key: String, quiet: Bool) -> LocalNotificationRequest {
        LocalNotificationRequest(
            identifier: NotificationIdentifier.make(type: .n6, miffId: miffId),
            title: deps.text(NotificationType.n6.titleKey),
            body: deps.text(NotificationType.n6.bodyKey),
            threadIdentifier: miffId,
            categoryIdentifier: NotificationCategory.identifier(for: .n6),
            userInfo: ["type": NotificationType.n6.rawValue, "miffId": miffId, "key": key],
            delaySeconds: 1,
            quiet: quiet
        )
    }
}
