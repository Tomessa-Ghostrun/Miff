package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue

/*
 * NOTIF-3A: the LOCAL SNAPSHOT of what the phone last knew about the active Miff (ticket section 20.3, "Source des données hors premier plan"). Without
 * it nothing can be rescheduled after a restart or a change of time, nor checked when an alarm rings in a cold process. It holds, per member, the
 * identifier, "does he share", where he is IN THE SENSE OF THE PRESENCE (in a trusted zone, out, in a danger zone, unknown: a keyword, never a place), the
 * time of the presence and of the last update. NO position, NO name of a place, NO address, NO photo, NO name of a person (criterion N-P0-10).
 */

class SnapshotMember(
    val uid: String,
    val sharing: Boolean,
    val presence: PresenceValue?,
    val presenceAtMillis: Long?,
    val lastUpdateAtMillis: Long?,
) {
    fun toFacts(): MemberStatusFacts = MemberStatusFacts(sharing, presence, presenceAtMillis, lastUpdateAtMillis)

    override fun toString(): String = "SnapshotMember(redacted)"
}

class PlanSnapshot(
    val miffId: String,
    val myUid: String,
    val members: List<SnapshotMember>,
    val savedAtMillis: Long,
) {
    /** The facts of the members who have a status. A member with no status is not in the map (the colour of nobody is neutral, never a guess). */
    fun facts(): Map<String, MemberStatusFacts> =
        members.filter { it.presence != null }.associate {
            it.uid to
                it.toFacts()
        }

    /**
     * My own status just changed ON THIS PHONE (a departure or an arrival noticed by the detection, an update published): the snapshot follows without
     * waiting for the Feed. [lastUpdateAtMillis] null keeps the previous value (an arrival in a trusted zone, an update, sets it; a departure does not).
     */
    fun withMyStatus(
        presence: PresenceValue,
        presenceAtMillis: Long,
        lastUpdateAtMillis: Long?,
        nowMillis: Long,
    ): PlanSnapshot {
        val mine = members.firstOrNull { it.uid == myUid }
        val updated =
            SnapshotMember(
                uid = myUid,
                sharing = mine?.sharing ?: true,
                presence = presence,
                presenceAtMillis = presenceAtMillis,
                lastUpdateAtMillis = lastUpdateAtMillis ?: mine?.lastUpdateAtMillis,
            )
        return PlanSnapshot(miffId, myUid, members.filter { it.uid != myUid } + updated, nowMillis)
    }

    /** I published an update: my last update moves, my presence stays. */
    fun withMyUpdate(
        atMillis: Long,
        nowMillis: Long,
    ): PlanSnapshot {
        val mine = members.firstOrNull { it.uid == myUid } ?: return this
        val updated =
            SnapshotMember(
                myUid,
                mine.sharing,
                mine.presence,
                mine.presenceAtMillis,
                maxOf(
                    atMillis,
                    mine.lastUpdateAtMillis ?: atMillis,
                ),
            )
        return PlanSnapshot(miffId, myUid, members.filter { it.uid != myUid } + updated, nowMillis)
    }

    override fun toString(): String = "PlanSnapshot(redacted)"
}
