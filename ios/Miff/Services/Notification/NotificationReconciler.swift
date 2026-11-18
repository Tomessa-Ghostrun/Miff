import Foundation

// CONFIDENTIALITÉ, pour ce fichier : le réconciliateur lit des faits de couleur (présence, heures) gardés sur l'appareil et programme
// des notifications LOCALES au texte fixe. Il ne lit aucune position, n'écrit rien dans Firestore, n'envoie aucun événement Analytics
// et ne journalise rien (compteurs locaux seulement). Aucun nom de lieu, aucune adresse.

/// Ce dont le réconciliateur a besoin du monde extérieur (des doubles dans les tests).
struct NotificationReconcilerDependencies {
    let scheduler: LocalNotificationScheduling
    let authorization: NotificationAuthorizationReading
    let preferences: NotificationPreferencesService
    let plans: NotificationPlanStoring
    let center: NotificationCenterService
    let currentUid: () -> String?
    /// La Miff active du Feed d'après l'annuaire (nil tant que l'annuaire n'est pas lu : un réveil en arrière-plan, par exemple).
    var activeMiffId: () -> String? = { nil }
    /// Mon accord est attendu dans cette Miff.
    var agreementWaiting: (String) -> Bool = { _ in false }
    var nowMillis: () -> Int = NotificationClock.wallMillis
    var calendar: () -> Calendar = { Calendar.current }
    /// L'app est-elle au premier plan ? (Au premier plan aucune notification du système n'est affichée.)
    var isForeground: () -> Bool = { false }
    /// La langue du texte figé dans une demande : un changement de langue la fait reprogrammer. C'est la langue CHOISIE dans Réglages > Langue
    /// (celle du téléphone par défaut), la même que celle de `text`.
    var language: () -> String = { AppLanguage.code }
    var text: (String) -> String = { L10n.string($0) }
}

/// Fait correspondre les notifications du système à ce que dit le planificateur (ticket §20.3, `reconcile`). Idempotent : appelé à chaque
/// lancement, retour au premier plan, réponse du Feed, changement de réglage, de Miff active ou de langue, il annule ce qui n'est plus
/// valable et programme ce qui manque, sans jamais notifier deux fois la même clé.
@MainActor
final class NotificationReconciler {
    var deps: NotificationReconcilerDependencies
    private var busy = false
    private var again = false

    init(_ deps: NotificationReconcilerDependencies) {
        self.deps = deps
    }

    /// Branche, après coup, ce qui n'existe pas encore à la construction (annuaire des Miffs, accord attendu, état du premier plan).
    func configure(_ change: (inout NotificationReconcilerDependencies) -> Void) {
        change(&deps)
    }

    /// Une réconciliation à la fois : un appel pendant une autre demande simplement qu'elle recommence (aucune course entre deux).
    func reconcile() async {
        if busy {
            again = true
            return
        }
        busy = true
        repeat {
            again = false
            await runOnce()
        } while again
        busy = false
    }

    private func runOnce() async {
        guard let uid = deps.currentUid() else {
            await removeEverything()
            return
        }
        // Un réveil en arrière-plan n'a pas ouvert d'écran : les choix de la personne ET le centre (plafonds du jour) sont lus depuis l'appareil
        // AVANT toute décision, jamais les valeurs par défaut (tout actif).
        loadFromDevice()
        let now = deps.nowMillis()
        let authorization = await deps.authorization.authorization()
        let pending = await deps.scheduler.pendingIdentifiers()
        let delivered = await deps.scheduler.deliveredIdentifiers()
        guard deps.currentUid() == uid else {
            return
        }
        var ledger = deps.plans.loadLedger(uid: uid).pruned(nowMillis: now)
        let snapshot = deps.plans.loadSnapshot(uid: uid)
        forgetOldTravelEnd(uid: uid, now: now)
        ledger = settleFired(ledger, pending: pending, snapshot: snapshot, authorization: authorization, now: now)
        let input = makeInput(uid: uid, now: now, snapshot: snapshot, authorization: authorization, ledger: ledger)
        let plan = NotificationPlanner.plan(input)
        ledger = await removeStale(plan, ledger: ledger, pending: pending)
        await removeObsolete(delivered: delivered, snapshot: snapshot, input: input)
        ledger = await program(plan, ledger: ledger, pending: pending, now: now, uid: uid)
        ledger = await catchUp(plan, ledger: ledger, now: now, uid: uid)
        // La session a pu finir pendant qu'on parlait au système (déconnexion, suppression du compte) : rien n'est réécrit pour un compte parti.
        guard deps.currentUid() == uid else {
            return
        }
        // Pendant qu'on attendait le système, une N6 ou un toucher a pu inscrire une clé : elle est conservée.
        let latest = deps.plans.loadLedger(uid: uid).pruned(nowMillis: now)
        deps.plans.saveLedger(ledger.merging(marksOf: latest), uid: uid)
    }

    /// Les choix de la personne et le centre (ses entrées du jour comptent pour les plafonds) sont lus depuis l'appareil, une fois par compte.
    func loadFromDevice() {
        deps.preferences.ensureLoaded()
        deps.center.load()
    }

    // MARK: - L'entrée du planificateur

    private func makeInput(
        uid: String,
        now: Int,
        snapshot: NotificationPlanSnapshot?,
        authorization: NotificationAuthorization,
        ledger: NotificationLedger
    ) -> NotificationPlanInput {
        var input = NotificationPlanInput(
            nowMillis: now,
            snapshot: snapshot,
            // L'annuaire n'est pas toujours lu (réveil en arrière-plan) : l'instantané dit alors la Miff qu'il suivait.
            activeMiffId: deps.activeMiffId() ?? snapshot?.miffId,
            sessionUid: uid
        )
        input.agreementWaiting = snapshot.map { deps.agreementWaiting($0.miffId) } ?? false
        input.preferences = deps.preferences.preferences
        input.authorization = authorization
        input.notifiedKeys = ledger.notifiedKeys
        input.shown = deps.center.history.items
            .filter { $0.state == .shown }
            .map { ShownNotification(type: $0.type, atMillis: $0.createdAtMillis) }
        input.calendar = deps.calendar()
        // MODE VOYAGE : la fin de MON voyage (un compte et un instant, gardés par le suivi) : N7, et la coupure de N1 à N4 pendant le voyage.
        input.myTravelUntilMillis = deps.plans.loadTravelEnd(uid: uid)
        return input
    }

    /// Un voyage dont N7 aurait dû partir il y a plus de 24 h est oublié (la fin n'a plus rien à annoncer).
    private func forgetOldTravelEnd(uid: String, now: Int) {
        guard let until = deps.plans.loadTravelEnd(uid: uid) else {
            return
        }
        let due = TravelEndNotice.dueAt(untilMillis: until, calendar: deps.calendar())
        if now - due > TravelEndNotice.catchUpWindowMillis {
            deps.plans.saveTravelEnd(nil, uid: uid)
        }
    }

    // MARK: - Ce qui a déjà sonné

    /// Une demande qu'on avait programmée, dont l'heure est passée et que le système n'a plus en attente : elle a été livrée (on ne sait
    /// pas si elle a été balayée). Elle entre dans le centre comme « affichée » ; une demande disparue AVANT son heure est simplement oubliée
    /// (le planificateur la reprogrammera si elle est toujours vraie).
    private func settleFired(
        _ start: NotificationLedger,
        pending: Set<String>,
        snapshot: NotificationPlanSnapshot?,
        authorization: NotificationAuthorization,
        now: Int
    ) -> NotificationLedger {
        var ledger = start
        for (identifier, entry) in start.scheduled where !pending.contains(identifier) {
            ledger = ledger.unscheduling([identifier])
            // Permission retirée entre-temps : on ne sait pas si elle a sonné, on ne l'inscrit pas comme « affichée » (le planificateur
            // décidera si elle est toujours à faire quand l'autorisation reviendra).
            guard entry.fireAtMillis <= now, authorization.isAllowed else {
                continue
            }
            ledger = recordFired(entry, ledger: ledger, now: now)
            // Compteur de recette (local, jamais envoyé) : N3 / N4 affichées alors que la couleur était déjà revenue.
            if let snapshot, snapshot.savedAtMillis >= entry.fireAtMillis, entry.type == .n3 || entry.type == .n4 {
                let holds = NotificationPlanner.stillTrue(entry.type, snapshot: snapshot, atMillis: now)
                ledger = ledger.countingFollowUp(falseAlert: !holds)
            }
        }
        return ledger
    }

    /// Inscrit l'événement dans le centre et dans le registre (une clé, une seule fois).
    private func recordFired(_ entry: ScheduledRecord, ledger: NotificationLedger, now: Int) -> NotificationLedger {
        deps.center.record(
            NotificationItem(
                id: entry.key,
                type: entry.type,
                miffId: entry.miffId,
                createdAtMillis: entry.fireAtMillis,
                readAtMillis: nil,
                state: .shown
            )
        )
        return ledger.marking(key: entry.key, atMillis: now)
    }

    // MARK: - Annuler, retirer, programmer

    /// Les demandes en attente que le plan ne veut plus (publication, retour dans un lieu, autre Miff, interrupteur coupé...).
    private func removeStale(
        _ plan: NotificationPlan,
        ledger: NotificationLedger,
        pending: Set<String>
    ) async -> NotificationLedger {
        let wanted = Set(plan.scheduled.map { $0.identifier })
        // N6 est immédiate et ne s'annule pas : sa demande d'une seconde n'est jamais retirée par une réconciliation concurrente.
        let stale = pending.subtracting(wanted).filter { NotificationIdentifier.parse($0)?.type != .n6 }
        guard !stale.isEmpty else {
            return ledger
        }
        await deps.scheduler.removePending(Array(stale))
        return ledger.unscheduling(Array(stale))
    }

    /// « Retrait à la reprise » : une notification déjà affichée qui n'est plus vraie (j'ai publié, la Miff n'est plus de cette couleur,
    /// l'interrupteur est coupé, la Miff n'est plus la Miff active) est retirée de l'écran.
    private func removeObsolete(
        delivered: Set<String>,
        snapshot: NotificationPlanSnapshot?,
        input: NotificationPlanInput
    ) async {
        var obsolete: [String] = []
        for identifier in delivered {
            guard let parts = NotificationIdentifier.parse(identifier) else {
                continue
            }
            if parts.type == .n7 {
                // N7 ne dépend d'aucune Miff ni d'aucune couleur : elle reste à l'écran tant que son interrupteur et l'autorisation tiennent.
                if !NotificationPlanner.travelEndStillWanted(
                    preferences: input.preferences, authorization: input.authorization
                ) {
                    obsolete.append(identifier)
                }
                continue
            }
            let valid = input.preferences.isEnabled(parts.type)
                && input.authorization.isAllowed
                && (parts.type == .n6 || isCurrent(parts, snapshot: snapshot, input: input))
            if !valid {
                obsolete.append(identifier)
            }
        }
        if !obsolete.isEmpty {
            await deps.scheduler.removeDelivered(obsolete)
        }
    }

    /// N1 à N4 : toujours vraie pour la Miff active, d'après l'instantané.
    private func isCurrent(
        _ parts: (type: NotificationType, miffId: String),
        snapshot: NotificationPlanSnapshot?,
        input: NotificationPlanInput
    ) -> Bool {
        snapshot?.miffId == parts.miffId
            && input.activeMiffId == parts.miffId
            && stillTrue(parts.type, snapshot: snapshot, input: input)
    }

    private func stillTrue(
        _ type: NotificationType,
        snapshot: NotificationPlanSnapshot?,
        input: NotificationPlanInput
    ) -> Bool {
        guard let snapshot else {
            return false
        }
        return NotificationPlanner.stillTrue(
            type, snapshot: snapshot, atMillis: input.nowMillis, myTravelUntilMillis: input.myTravelUntilMillis
        )
    }

    private func program(
        _ plan: NotificationPlan,
        ledger start: NotificationLedger,
        pending: Set<String>,
        now: Int,
        uid: String
    ) async -> NotificationLedger {
        var ledger = start
        let language = deps.language()
        for planned in plan.scheduled {
            // La session peut finir entre deux demandes : la suivante ne part pas (et le registre ne dira pas qu'elle est programmée).
            guard deps.currentUid() == uid else {
                return ledger
            }
            let identifier = planned.identifier
            let scheduled = ScheduledRecord(
                type: planned.type,
                miffId: planned.miffId,
                key: planned.key,
                fireAtMillis: planned.fireAtMillis,
                quiet: planned.quiet,
                language: language
            )
            if pending.contains(identifier) && ledger.scheduled[identifier] == scheduled {
                continue
            }
            let delay = Double(planned.fireAtMillis - now) / 1000
            await deps.scheduler.add(makeRequest(planned, delaySeconds: delay))
            ledger = ledger.scheduling(scheduled, identifier: identifier)
        }
        return ledger
    }

    private func makeRequest(_ planned: PlannedNotification, delaySeconds: TimeInterval) -> LocalNotificationRequest {
        LocalNotificationRequest(
            identifier: planned.identifier,
            title: deps.text(planned.type.titleKey),
            body: deps.text(planned.type.bodyKey),
            threadIdentifier: planned.miffId,
            categoryIdentifier: NotificationCategory.identifier(for: planned.type),
            // Type et Miff suffisent à ouvrir le bon écran ; la clé de dédoublonnage (qui contient l'identifiant du compte) reste dans le
            // registre local, jamais dans la demande du système.
            userInfo: ["type": planned.type.rawValue, "miffId": planned.miffId],
            delaySeconds: delaySeconds,
            quiet: planned.quiet
        )
    }

    // MARK: - Rattrapage (§20.8)

    /// Un instant déjà passé et toujours vrai : au premier plan, ou plus de 15 minutes après, une entrée de centre SILENCIEUSE ; en arrière-plan
    /// et moins de 15 minutes après, la notification s'affiche aussitôt. Les rattrapages au-delà des plafonds du jour sont silencieux.
    private func catchUp(
        _ plan: NotificationPlan,
        ledger start: NotificationLedger,
        now: Int,
        uid: String
    ) async -> NotificationLedger {
        var ledger = start
        for planned in plan.catchUp {
            guard deps.currentUid() == uid else {
                return ledger
            }
            let late = now - planned.fireAtMillis
            let showsNow = !deps.isForeground() && late <= NotificationPlanner.catchUpWindowMillis
            if showsNow {
                await deps.scheduler.add(makeRequest(planned, delaySeconds: 1))
            }
            ledger = recordCatchUp(planned, state: showsNow ? .shown : .silent, ledger: ledger, now: now)
        }
        for planned in plan.silent {
            ledger = recordCatchUp(planned, state: .silent, ledger: ledger, now: now)
        }
        return ledger
    }

    private func recordCatchUp(
        _ planned: PlannedNotification,
        state: NotificationItemState,
        ledger: NotificationLedger,
        now: Int
    ) -> NotificationLedger {
        deps.center.record(
            NotificationItem(
                id: planned.key,
                type: planned.type,
                miffId: planned.miffId,
                createdAtMillis: planned.fireAtMillis,
                readAtMillis: nil,
                state: state
            )
        )
        return ledger.marking(key: planned.key, atMillis: now)
    }

    // MARK: - Plus de session

    /// Personne n'est connecté : rien ne reste en attente ni à l'écran.
    private func removeEverything() async {
        let pending = await deps.scheduler.pendingIdentifiers()
        let delivered = await deps.scheduler.deliveredIdentifiers()
        await deps.scheduler.removePending(Array(pending))
        await deps.scheduler.removeDelivered(Array(delivered))
    }
}

extension NotificationIdentifier {
    /// Le type et la Miff d'un identifiant de demande (`miff.notif.n1.<miffId>`), nil pour tout autre identifiant.
    static func parse(_ identifier: String) -> (type: NotificationType, miffId: String)? {
        guard identifier.hasPrefix(prefix) else {
            return nil
        }
        let rest = identifier.dropFirst(prefix.count)
        guard let dot = rest.firstIndex(of: "."),
              let type = NotificationType(rawValue: String(rest[rest.startIndex..<dot])) else {
            return nil
        }
        return (type, String(rest[rest.index(after: dot)...]))
    }
}
