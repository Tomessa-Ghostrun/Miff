package com.tomessa.miff.data.notification

import com.tomessa.miff.data.feed.CircleRead
import com.tomessa.miff.data.feed.CircleStatusReader
import com.tomessa.miff.domain.notification.Delivery
import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.NotificationDelivery
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPlanner
import com.tomessa.miff.domain.notification.NotificationPolicy
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.NotifiedKeys
import com.tomessa.miff.domain.notification.PlanInput
import com.tomessa.miff.domain.notification.PlanSnapshot
import com.tomessa.miff.domain.notification.PlannedNotification
import com.tomessa.miff.domain.notification.SnapshotMember
import com.tomessa.miff.domain.tracking.PresenceValue
import java.time.Instant
import java.time.ZoneId

/*
 * NOTIF-4A and NOTIF-5A: the part that APPLIES the plan of [NotificationPlanner] (ticket notifications.md sections 20.3 and 20.5).
 *
 * PRIVACY and COST: the decisions are LOCAL (the snapshot of the Miff the phone already knew). Nothing is read from or written to the server by N1 and N2. Only
 * N3 and N4 read the statuses of the Miff once, from the server, a moment before they are shown ([CircleStatusReader]), at most twice per episode.
 * Nothing here logs, sends an event or keeps a position, a place, an address or a name.
 *
 * `reconcile` is the single entry point that brings the alarms in line with the plan: cheap, idempotent, called at every event that can change it (a status
 * seen by the Feed, my own status written by the detection, an update published, a switch, the permission, a restart, a change of time, the return to the
 * front). Cancelling is as important as planning: a switch turned off, an update published, a sign-out cancel what was waiting, at once.
 */
class NotificationScheduler(
    private val snapshots: PlanSnapshotStore,
    private val settings: NotificationSettings,
    /** The phone lets Miff notify (the state of the permission is ALLOWED). */
    private val permissionAllowed: () -> Boolean,
    /** Somebody is signed in on this phone with the session of [uid]: checked on the phone, no read of the server. */
    private val sessionValid: (uid: String) -> Boolean,
    private val alarms: NotificationAlarms,
    private val ledger: NotificationLedger,
    private val center: NotificationCenter,
    private val presenter: NotificationPresenter,
    private val appInForeground: () -> Boolean,
    private val reader: CircleStatusReader,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val lock = Any()

    // --- The snapshot: what the phone knows of the active Miff -------------------------------------------------------------------------------

    /** The Feed read the statuses of the Miff shown: the snapshot is written (it is the source of everything below), then the plan is looked at again. */
    fun onStatuses(
        miffId: String,
        myUid: String,
        members: List<SnapshotMember>,
    ) {
        synchronized(lock) {
            // A call queued before the sign-out (or the loss of the session) must not write the snapshot of a person who is gone.
            if (!sessionValid(myUid)) return
            snapshots.save(PlanSnapshot(miffId, myUid, members, nowMillis()))
            reconcile()
        }
    }

    /**
     * My own status changed ON THIS PHONE (a departure or an arrival noticed by the detection): the snapshot follows without waiting for the Feed.
     * [setLastUpdate]: an arrival in a trusted zone is also an update (the reference of the colour moves with it).
     */
    fun onMyPresence(
        miffId: String,
        presence: PresenceValue,
        atMillis: Long,
        setLastUpdate: Boolean,
    ) {
        synchronized(lock) {
            val snapshot = snapshots.load() ?: return
            if (snapshot.miffId != miffId) return
            snapshots.save(
                snapshot.withMyStatus(presence, atMillis, if (setLastUpdate) atMillis else null, nowMillis()),
            )
            reconcile()
        }
    }

    /** I published an update in [miffIds]: it cancels what waited and plans from the new reference. */
    fun onMyUpdate(
        miffIds: List<String>,
        atMillis: Long,
    ) {
        synchronized(lock) {
            val snapshot = snapshots.load() ?: return
            if (snapshot.miffId !in miffIds) return
            snapshots.save(snapshot.withMyUpdate(atMillis, nowMillis()))
            reconcile()
        }
    }

    /** Another Miff was chosen in the selector: nothing of the old one stays; the Feed writes the snapshot of the new one when it has read it. */
    fun onActiveMiffChanged(miffId: String?) {
        synchronized(lock) {
            val snapshot = snapshots.load() ?: return
            if (snapshot.miffId == miffId) return
            snapshots.clear()
            alarms.cancelAll()
        }
    }

    /**
     * The Miffs the person belongs to, as the directory just said (it changes when the person leaves, a Miff is deleted or the access is lost): the reminders of a
     * Miff that is not in the list any more are cancelled and its snapshot is forgotten. Only called for a list the directory really READ (never while it loads or when it failed).
     */
    fun onMiffsKnown(miffIds: Set<String>) {
        synchronized(lock) {
            val snapshot = snapshots.load() ?: return
            if (snapshot.miffId in miffIds) return
            snapshots.clear()
            alarms.cancelAll()
        }
    }

    /** The Miff [miffId] was left or deleted on this phone: what waited for it is cancelled. */
    fun onMiffGone(miffId: String) {
        synchronized(lock) {
            val snapshot = snapshots.load() ?: return
            if (snapshot.miffId != miffId) return
            snapshots.clear()
            alarms.cancelAll()
        }
    }

    /** The sign-out, the loss of the session, the deletion of the account: everything is cancelled and forgotten. */
    fun clearAll() {
        synchronized(lock) {
            alarms.cancelAll()
            snapshots.clear()
            ledger.clear()
        }
    }

    // --- Reconcile ----------------------------------------------------------------------------------------------------------------------

    private fun inputOf(
        snapshot: PlanSnapshot,
        now: Long,
        facts: Map<String, com.tomessa.miff.domain.tracking.MemberStatusFacts> = snapshot.facts(),
    ) = PlanInput(
        nowMillis = now,
        miffId = snapshot.miffId,
        myUid = snapshot.myUid,
        members = facts,
        preferences = settings.current(),
        permissionAllowed = permissionAllowed(),
        sessionValid = sessionValid(snapshot.myUid),
    )

    /**
     * Brings the alarms in line with the plan. [force]: the system lost them (restart of the phone, update of the app, change of the time): they are all asked
     * again whatever was remembered. A notification already notified is never planned again (no double after a restart, a change of time or of zone).
     */
    fun reconcile(force: Boolean = false) {
        synchronized(lock) {
            val snapshot = snapshots.load()
            if (snapshot == null) {
                alarms.cancelAll()
                return
            }
            val now = nowMillis()
            val planned = NotificationPlanner.plan(inputOf(snapshot, now)).associateBy { it.type }
            val scheduled = if (force) emptyMap() else alarms.scheduled()
            val notified = ledger.keys(now)
            for (type in PLANNED_TYPES) {
                val p = planned[type]
                if (p == null || p.key in notified) {
                    alarms.cancel(type)
                    continue
                }
                if (p.dueAtMillis > now) {
                    val known = scheduled[type]
                    if (known == null || known.key != p.key || known.dueAtMillis != p.dueAtMillis) {
                        alarms.schedule(type, ScheduledAlarm(p.key, p.miffId, p.dueAtMillis))
                    }
                } else {
                    caughtUp(p, scheduled[type], now)
                }
            }
        }
    }

    /** A notification whose hour has passed (the app was closed, the phone off, the alarm late): what to do depends on what it needs to be true. */
    private fun caughtUp(
        p: PlannedNotification,
        pending: ScheduledAlarm?,
        now: Long,
    ) {
        when (p.type) {
            NotificationType.N1, NotificationType.N2 -> {
                alarms.cancel(p.type)
                dueNow(p, now)
            }

            NotificationType.N3, NotificationType.N4 -> {
                when {
                    // In front, the facts are the live ones of the Feed: the decision is taken here (and will only write a silent line).
                    appInForeground() -> {
                        alarms.cancel(p.type)
                        dueNow(p, now)
                    }

                    ledger.attempts(p.key) >= MAX_VERIFICATIONS -> {
                        alarms.cancel(p.type)
                        ledger.remember(p.key, now)
                    }

                    // The retry of a verification that could not reach the server is already waiting: leave it.
                    pending != null && pending.key == p.key && pending.dueAtMillis > now -> {
                        Unit
                    }

                    // Verified by the alarm (the network belongs to a receiver with its ten seconds), a moment from now.
                    else -> {
                        alarms.schedule(p.type, ScheduledAlarm(p.key, p.miffId, now + CATCH_UP_DELAY_MILLIS))
                    }
                }
            }

            NotificationType.N6 -> {
                Unit
            }
        }
    }

    // --- N6: I entered a danger zone of my Miff (NOTIF-7A) ------------------------------------------------------------------------------------

    /**
     * The detection validated that I ENTERED a danger zone of [miffId] (a presence `in_risk` that was not already there: entering one while in a trusted zone, and a
     * presence already `in_risk` before a restart, never come here). N6 is shown AT ONCE, before the status is even written: it does not wait for the network, and it
     * reads and writes NOTHING on the server (criterion N-P0-11). Decision: the key `n6|Miff|place|time` is never notified twice; a zone that is left and entered again
     * within 60 minutes (a geofence that oscillates) gives ONE notification; 3 a day, outside the total of 6 (decision Q30); the app in front, the night and the switches
     * are those of [NotificationDelivery]. Without the permission or a valid session: nothing.
     */
    fun onEnteredDanger(
        myUid: String,
        miffId: String,
        placeId: String,
        atMillis: Long,
    ) {
        synchronized(lock) {
            if (!permissionAllowed() || !sessionValid(myUid)) return
            val now = nowMillis()
            val key = "n6|$miffId|$placeId|$atMillis"
            val rearmKey = "n6rearm|$miffId|$placeId"
            val keys = ledger.keys(now)
            val lastForThisZone = keys[rearmKey]
            if (lastForThisZone != null && now - lastForThisZone < NotificationPolicy.N6_REARM_MILLIS) return
            val zone = zone()
            val day =
                Instant
                    .ofEpochMilli(now)
                    .atZone(zone)
                    .toLocalDate()
                    .toString()
            val counters = ledger.counters(day)
            val decision =
                NotificationDelivery.decide(
                    type = NotificationType.N6,
                    preferences = settings.current(),
                    counters = counters,
                    localHour = Instant.ofEpochMilli(now).atZone(zone).hour,
                    appInForeground = appInForeground(),
                    latenessMillis = 0L,
                    alreadyNotified = key in keys,
                )
            when (decision) {
                Delivery.Drop -> {
                    Unit
                }

                Delivery.Silent -> {
                    center.add(NotificationItem(key, NotificationType.N6, miffId, now, state = ItemState.SILENT))
                    ledger.remember(key, now)
                    ledger.remember(rearmKey, now)
                }

                is Delivery.Show -> {
                    center.add(NotificationItem(key, NotificationType.N6, miffId, now, state = ItemState.SHOWN))
                    presenter.show(NotificationType.N6, key, decision.quiet)
                    ledger.remember(key, now)
                    ledger.remember(rearmKey, now)
                    ledger.saveCounters(counters.afterShown(NotificationType.N6))
                }
            }
        }
    }

    // --- An alarm rang -------------------------------------------------------------------------------------------------------------------

    /** The alarm of [type] rang (alarm receiver). N1 and N2 are decided here, locally; N3 and N4 verify the server first ([fireCircle]). */
    fun fire(
        type: NotificationType,
        key: String,
    ) {
        synchronized(lock) {
            alarms.consumed(type)
            val snapshot = snapshots.load() ?: return
            val now = nowMillis()
            val input = inputOf(snapshot, now)
            // The alarm may be stale (an update was published, the switch was turned off, the level moved on): what is planned NOW decides, not what was asked.
            val p = NotificationPlanner.plan(input).firstOrNull { it.type == type && it.key == key }
            if (p != null && NotificationPlanner.stillTrue(type, input)) dueNow(p, now)
            reconcile()
        }
    }

    /**
     * The alarm of N3 or N4 rang (or the catch-up came): the statuses of the Miff are read FROM THE SERVER and the decision is taken on that, never on the copy
     * kept on the phone. Silent in every doubt (decision Q33): offline, then one more try 10 minutes later, then nothing; the rules refuse: nothing.
     */
    suspend fun fireCircle(
        type: NotificationType,
        key: String,
    ) {
        val p: PlannedNotification
        val snapshot: PlanSnapshot
        synchronized(lock) {
            alarms.consumed(type)
            snapshot = snapshots.load() ?: return
            val now = nowMillis()
            p =
                NotificationPlanner.plan(inputOf(snapshot, now)).firstOrNull { it.type == type && it.key == key }
                    ?: run {
                        reconcile()
                        return
                    }
            if (key in ledger.keys(now)) return
            if (appInForeground()) {
                // In front, the facts of the snapshot are the live ones of the Feed: no reading of the server is needed (and none is paid for); only a silent line.
                if (NotificationPlanner.stillTrue(
                        type,
                        inputOf(snapshot, now),
                    )
                ) {
                    dueNow(p, now)
                } else {
                    ledger.remember(key, now)
                }
                reconcile()
                return
            }
            // Counted BEFORE the read: a verification that gets killed halfway still counts, so an episode never costs more than two readings.
            ledger.addAttempt(key)
            // The second try is asked BEFORE the read: if the process is killed during it (a receiver has seconds only), the retry is still waiting.
            if (ledger.attempts(key) < MAX_VERIFICATIONS) {
                alarms.schedule(type, ScheduledAlarm(key, p.miffId, now + RETRY_AFTER_MILLIS))
            }
        }
        val read = reader.read(snapshot.miffId)
        synchronized(lock) {
            val now = nowMillis()
            // The person may have signed out, left the Miff or chosen another one while the server was read: nothing is saved, nothing is shown, nothing is remembered.
            val current = snapshots.load()
            if (!sessionValid(snapshot.myUid) || current == null || current.miffId != snapshot.miffId ||
                current.myUid != snapshot.myUid
            ) {
                return
            }
            when (read) {
                is CircleRead.Fresh -> {
                    // The sharing flags come from the Miff (the snapshot); the presence and the times from the server, which just spoke.
                    val fresh = freshMembers(snapshot, read, now)
                    snapshots.save(fresh)
                    if (NotificationPlanner.stillTrue(type, inputOf(fresh, now))) {
                        dueNow(p, now)
                    } else {
                        // Not true any more (somebody gave news, the Miff changed colour): nothing is shown, and this episode is settled.
                        ledger.remember(key, now)
                    }
                    reconcile()
                }

                CircleRead.Refused -> {
                    ledger.remember(key, now)
                    reconcile()
                }

                CircleRead.Unreachable -> {
                    // The retry (10 minutes later) is already waiting after the first try. After the second: decision Q33, nothing is shown, no "we could not check".
                    if (ledger.attempts(key) >= MAX_VERIFICATIONS) ledger.remember(key, now)
                }
            }
        }
    }

    private fun freshMembers(
        snapshot: PlanSnapshot,
        read: CircleRead.Fresh,
        now: Long,
    ): PlanSnapshot =
        PlanSnapshot(
            miffId = snapshot.miffId,
            myUid = snapshot.myUid,
            members =
                snapshot.members.map { member ->
                    val status = read.statuses[member.uid]
                    if (status == null) {
                        // No status on the server (or not readable): the member has none (the colour of nobody is neutral).
                        SnapshotMember(member.uid, member.sharing, null, null, null)
                    } else {
                        SnapshotMember(
                            member.uid,
                            member.sharing,
                            status.presence,
                            status.presenceAtMillis,
                            status.lastUpdateAtMillis,
                        )
                    }
                },
            savedAtMillis = now,
        )

    // --- Showing ---------------------------------------------------------------------------------------------------------------------------

    /** A notification is due now: decide (caps, foreground, lateness, night, switches), then show it and/or write it to the centre, and remember its key. */
    private fun dueNow(
        p: PlannedNotification,
        now: Long,
    ) {
        val preferences = settings.current()
        val day =
            zone().let {
                Instant
                    .ofEpochMilli(now)
                    .atZone(it)
                    .toLocalDate()
                    .toString()
            }
        val counters = ledger.counters(day)
        val hour = Instant.ofEpochMilli(now).atZone(zone()).hour
        val decision =
            NotificationDelivery.decide(
                type = p.type,
                preferences = preferences,
                counters = counters,
                localHour = hour,
                appInForeground = appInForeground(),
                latenessMillis = maxOf(0L, now - p.dueAtMillis),
                alreadyNotified = NotifiedKeys.contains(ledger.keys(now), p.key, now),
            )
        when (decision) {
            Delivery.Drop -> {
                Unit
            }

            Delivery.Silent -> {
                center.add(
                    NotificationItem(p.key, p.type, p.miffId, minOf(p.dueAtMillis, now), state = ItemState.SILENT),
                )
                ledger.remember(p.key, now)
            }

            is Delivery.Show -> {
                // The line first: if the tray refuses (permission withdrawn a second ago), the centre still tells what happened.
                center.add(NotificationItem(p.key, p.type, p.miffId, now, state = ItemState.SHOWN))
                presenter.show(p.type, p.key, decision.quiet)
                ledger.remember(p.key, now)
                ledger.saveCounters(counters.afterShown(p.type))
            }
        }
    }

    companion object {
        /** The types that wait for an hour. N6 is immediate, it has no alarm. */
        val PLANNED_TYPES = listOf(NotificationType.N1, NotificationType.N2, NotificationType.N3, NotificationType.N4)

        /** At most TWO readings of the server per episode of N3 / N4 (section 20.5). */
        const val MAX_VERIFICATIONS = 2

        /** The second try, when the first could not reach the server (decision Q33): ten minutes later. */
        const val RETRY_AFTER_MILLIS = 10L * 60L * 1000L

        /** A catch-up of N3 / N4 in the background is verified by an alarm a moment from now. */
        const val CATCH_UP_DELAY_MILLIS = 5_000L
    }
}
