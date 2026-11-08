import Foundation

// Ce qui déclenche une réconciliation, et ce que le système nous dit d'une notification (ticket §20.3). CONFIDENTIALITÉ : seulement des
// identifiants, des heures et des couleurs ; aucune position, aucun nom de lieu.

extension NotificationReconciler {
    /// Le Feed a reçu les statuts DU SERVEUR de la Miff affichée : l'instantané est réécrit (c'est sa seule source hors du premier plan),
    /// puis les rappels sont réconciliés.
    func feedStatusesLoaded(_ event: FeedStatusesEvent) async {
        guard let uid = deps.currentUid(), event.myUid == uid, !event.members.isEmpty else {
            return
        }
        let snapshot = NotificationPlanSnapshot.make(
            miffId: event.miffId,
            myUid: uid,
            members: event.members.map { (uid: $0.uid, sharing: $0.sharing) },
            statuses: event.statuses,
            savedAtMillis: deps.nowMillis()
        )
        deps.plans.saveSnapshot(snapshot, uid: uid)
        await reconcile()
    }

    /// Le suivi des lieux vient de détecter une arrivée ou un départ : MA ligne de l'instantané change (les autres restent), puis les rappels
    /// N1 et N2 sont programmés (ou annulés) tout de suite, même app fermée. Une autre Miff que la Miff active n'a pas de rappel.
    func myPresenceChanged(miffId: String, fact: StatusFact) async {
        guard let uid = deps.currentUid() else {
            return
        }
        if let active = deps.activeMiffId(), active != miffId {
            return
        }
        let existing = deps.plans.loadSnapshot(uid: uid)
        if let existing, existing.miffId != miffId {
            return
        }
        let now = deps.nowMillis()
        let base = existing ?? NotificationPlanSnapshot(miffId: miffId, myUid: uid, members: [], savedAtMillis: now)
        // L'heure de fraîcheur des AUTRES membres ne bouge pas : seule ma ligne a changé.
        let updated = base.withMyFacts(
            presence: fact.presence,
            presenceAtMillis: fact.presenceAtMillis,
            setLastUpdate: fact.setLastUpdate,
            savedAtMillis: base.savedAtMillis
        )
        deps.plans.saveSnapshot(updated, uid: uid)
        await reconcile()
    }

    /// J'ai publié une update : mon repère avance, N1 à N4 sont annulées (et reprogrammées avec le nouveau repère s'il y a lieu) aussitôt.
    /// À appeler par l'écran Update quand la publication est confirmée.
    func myUpdatePublished(atMillis: Int) async {
        guard let uid = deps.currentUid(), let existing = deps.plans.loadSnapshot(uid: uid) else {
            return
        }
        deps.plans.saveSnapshot(
            existing.withMyUpdate(atMillis: atMillis, savedAtMillis: existing.savedAtMillis),
            uid: uid
        )
        await reconcile()
    }

    /// Une de nos notifications arrive pendant que l'app est AU PREMIER PLAN : le système n'affiche rien (la bulle du Feed parle déjà), le
    /// centre garde une entrée silencieuse.
    func foregroundDelivery(identifier: String) {
        guard let uid = deps.currentUid() else {
            return
        }
        var ledger = deps.plans.loadLedger(uid: uid)
        guard let entry = ledger.scheduled[identifier] else {
            return
        }
        deps.center.record(
            NotificationItem(
                id: entry.key,
                type: entry.type,
                miffId: entry.miffId,
                createdAtMillis: entry.fireAtMillis,
                readAtMillis: nil,
                state: .silent
            )
        )
        ledger = ledger.marking(key: entry.key, atMillis: deps.nowMillis()).unscheduling([identifier])
        deps.plans.saveLedger(ledger, uid: uid)
    }

    /// La personne a touché une notification : elle est lue, et l'on dit où aller. nil quand personne n'est connecté (la personne arrive sur
    /// l'écran de connexion, jamais sur une erreur).
    func notificationTapped(type rawType: String?, key: String?, miffId: String?) -> NotificationDestination? {
        guard let uid = deps.currentUid(), let rawType, let type = NotificationType(rawValue: rawType) else {
            return nil
        }
        if let key, let miffId {
            let now = deps.nowMillis()
            var ledger = deps.plans.loadLedger(uid: uid)
            let identifier = NotificationIdentifier.make(type: type, miffId: miffId)
            let fireAt = ledger.scheduled[identifier]?.fireAtMillis ?? now
            deps.center.record(
                NotificationItem(
                    id: key, type: type, miffId: miffId, createdAtMillis: fireAt, readAtMillis: now, state: .shown
                )
            )
            deps.center.markRead(id: key)
            ledger = ledger.marking(key: key, atMillis: now).unscheduling([identifier])
            deps.plans.saveLedger(ledger, uid: uid)
        }
        return type.destination
    }

    /// Les compteurs de recette (locaux, jamais envoyés) : N3 / N4 jugées, dont fausses alertes.
    func followUpCounters() -> (judged: Int, falseAlerts: Int)? {
        guard let uid = deps.currentUid() else {
            return nil
        }
        let ledger = deps.plans.loadLedger(uid: uid)
        return (ledger.judgedFollowUps, ledger.falseAlerts)
    }
}
